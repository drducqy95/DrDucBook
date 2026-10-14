"""
Modular & Fault-Tolerant LoRA Fine-Tuning Script for NLLB-Pruned Chinese-Vietnamese (zh_vi).
Optimized for Google Colab (Free T4 GPU / 16GB VRAM) and spotty networks.

Key Features:
- Chunked training (e.g. 500 - 1,000 steps per stage) with explicit max_steps.
- Auto-sync checkpoints to Google Drive (/content/drive/MyDrive/nllb_zh_vi_checkpoints).
- Auto-resume from the latest checkpoint on Google Drive upon restart or disconnection.
- 100% compatible with Transformers v4.40 - v5.x (dynamic argument inspection).
- In-flight benchmark generation on Golden Test sets at the end of each stage.
"""

import argparse
import inspect
import json
import os
import shutil
import sys
import time
import torch

# ---------------------------------------------------------------------------
# Hotfix: Neutralize Colab's pre-installed torchao 0.10.0 bug in PEFT
# In PEFT, is_torchao_available() incorrectly raises ImportError instead of
# returning False when torchao < 0.16.0 is installed in Python environment.
# ---------------------------------------------------------------------------
import sys
sys.modules["torchao"] = None

try:
    import peft.import_utils
    peft.import_utils.is_torchao_available = lambda: False
except Exception:
    pass
try:
    import peft.tuners.lora.torchao as _torchao_dispatch
    _torchao_dispatch.is_torchao_available = lambda: False
except Exception:
    pass

from datasets import load_dataset
from peft import LoraConfig, TaskType, get_peft_model, PeftModel
from transformers import (
    AutoModelForSeq2SeqLM,
    AutoTokenizer,
    DataCollatorForSeq2Seq,
    Seq2SeqTrainer,
    Seq2SeqTrainingArguments,
)

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")
if hasattr(sys.stderr, "reconfigure"):
    sys.stderr.reconfigure(encoding="utf-8")


def sync_to_drive(local_dir: str, drive_dir: str):
    if not os.path.exists(drive_dir):
        os.makedirs(drive_dir, exist_ok=True)
    print(f"\n[Drive Sync] Syncing checkpoints from {local_dir} -> {drive_dir}...")
    for item in os.listdir(local_dir):
        s_path = os.path.join(local_dir, item)
        d_path = os.path.join(drive_dir, item)
        if os.path.isdir(s_path):
            if os.path.exists(d_path):
                shutil.rmtree(d_path, ignore_errors=True)
            shutil.copytree(s_path, d_path)
        else:
            shutil.copy2(s_path, d_path)
    print("[Drive Sync] Sync completed successfully!\n")


def find_latest_checkpoint(checkpoint_dir: str) -> str | None:
    if not os.path.exists(checkpoint_dir):
        return None
    ckpts = [
        os.path.join(checkpoint_dir, d)
        for d in os.listdir(checkpoint_dir)
        if d.startswith("checkpoint-") and os.path.isdir(os.path.join(checkpoint_dir, d))
    ]
    if not ckpts:
        return None
    # Sort by step number
    ckpts.sort(key=lambda x: int(x.split("-")[-1]))
    return ckpts[-1]


def evaluate_sample_sentences(model, tokenizer, test_sentences, src_lang="zho_Hans", tgt_lang="vie_Latn"):
    tokenizer.src_lang = src_lang
    tgt_id = tokenizer.convert_tokens_to_ids(tgt_lang)
    print("\n" + "=" * 80)
    print("IN-FLIGHT BENCHMARK EVALUATION (Beam Search num_beams=4)")
    print("=" * 80)
    model.eval()
    for zh, desc in test_sentences:
        inp = tokenizer(zh, return_tensors="pt").to(model.device)
        with torch.no_grad():
            out = model.generate(
                **inp,
                forced_bos_token_id=tgt_id,
                max_new_tokens=64,
                num_beams=4,
                no_repeat_ngram_size=3,
                repetition_penalty=1.15,
            )
        pred = tokenizer.decode(out[0], skip_special_tokens=True)
        print(f"[{desc}]")
        print(f"  ZH  : {zh}")
        print(f"  PRED: {pred}")
        print("-" * 80)


def main():
    parser = argparse.ArgumentParser(description="Fault-tolerant LoRA training for NLLB zh_vi.")
    parser.add_argument("--model-path", type=str, default="/content/nllb_pruned_zh_vi")
    parser.add_argument("--train-file", type=str, default="/content/dataset_full_zh_vi/train.jsonl")
    parser.add_argument("--eval-file", type=str, default="/content/dataset_full_zh_vi/eval.jsonl")
    parser.add_argument("--output-dir", type=str, default="/content/nllb_zh_vi_checkpoints")
    parser.add_argument("--drive-dir", type=str, default="/content/drive/MyDrive/nllb_zh_vi_checkpoints")
    parser.add_argument("--max-steps", type=int, default=500, help="Steps for this training chunk")
    parser.add_argument("--batch-size", type=int, default=16, help="Per device batch size")
    parser.add_argument("--accum-steps", type=int, default=2, help="Gradient accumulation steps")
    parser.add_argument("--lr", type=float, default=2e-4)
    parser.add_argument("--save-steps", type=int, default=250)
    parser.add_argument("--lora-r", type=int, default=32)
    parser.add_argument("--lora-alpha", type=int, default=64)
    parser.add_argument("--target-modules", nargs="+", default=["q_proj", "k_proj", "v_proj", "out_proj", "fc1", "fc2"])
    parser.add_argument("--max-length", type=int, default=256)
    args = parser.parse_args()

    device = "cuda" if torch.cuda.is_available() else "cpu"
    print(f"Training Device: {device.upper()}")

    # 1. Check if Google Drive has prior checkpoints to resume
    resume_checkpoint = None
    if os.path.exists(args.drive_dir):
        drive_latest = find_latest_checkpoint(args.drive_dir)
        if drive_latest:
            print(f"Found existing checkpoint on Drive: {drive_latest}")
            os.makedirs(args.output_dir, exist_ok=True)
            local_target = os.path.join(args.output_dir, os.path.basename(drive_latest))
            if not os.path.exists(local_target):
                print(f"Restoring checkpoint from Drive to local disk...")
                shutil.copytree(drive_latest, local_target)
            resume_checkpoint = local_target
            print(f"Resume target ready: {resume_checkpoint}")

    if not resume_checkpoint:
        resume_checkpoint = find_latest_checkpoint(args.output_dir)

    # 2. Fast Local Cache for Model Path (if loaded from Google Drive)
    model_path = args.model_path
    if model_path.startswith("/content/drive/") and os.path.exists(model_path):
        local_base = os.path.join("/content", os.path.basename(model_path.rstrip("/")))
        if not os.path.exists(local_base):
            print(f"Copying base model from Drive to local SSD for fast training: {local_base}...")
            shutil.copytree(model_path, local_base)
        model_path = local_base

    # 3. Load Tokenizer & Model
    print(f"Loading tokenizer from {model_path}...")
    tokenizer = AutoTokenizer.from_pretrained(model_path)
    print(f"Tokenizer vocab size: {len(tokenizer):,}")

    print(f"Loading base pruned model in FP16 from {model_path}...")
    base_model = AutoModelForSeq2SeqLM.from_pretrained(
        model_path,
        torch_dtype=torch.float16 if device == "cuda" else torch.float32,
        low_cpu_mem_usage=True,
    )

    if device == "cuda":
        base_model = base_model.to("cuda")

    # 4. Configure LoRA
    if resume_checkpoint and os.path.exists(os.path.join(resume_checkpoint, "adapter_config.json")):
        print(f"Loading LoRA adapter weights from checkpoint: {resume_checkpoint}...")
        model = PeftModel.from_pretrained(base_model, resume_checkpoint, is_trainable=True)
    else:
        print(f"Configuring new LoRA adapter (r={args.lora_r}, alpha={args.lora_alpha}, modules={args.target_modules})...")
        lora_config = LoraConfig(
            task_type=TaskType.SEQ_2_SEQ_LM,
            inference_mode=False,
            r=args.lora_r,
            lora_alpha=args.lora_alpha,
            lora_dropout=0.05,
            target_modules=args.target_modules,
            bias="none",
        )
        model = get_peft_model(base_model, lora_config)

    model.print_trainable_parameters()

    # 4. Load & Preprocess Dataset
    print(f"Loading dataset JSONL from {args.train_file}...")
    dataset = load_dataset(
        "json",
        data_files={"train": args.train_file, "validation": args.eval_file},
    )
    print(f"Train samples: {len(dataset['train']):,}, Eval samples: {len(dataset['validation']):,}")

    max_len = args.max_length

    def preprocess_batch(batch):
        inp_ids, lbl_ids = [], []
        tokenizer.src_lang = "zho_Hans"
        tokenizer.tgt_lang = "vie_Latn"
        for src, tgt in zip(batch["source"], batch["target"]):
            inp = tokenizer(src, max_length=max_len, truncation=True)
            out = tokenizer(text_target=tgt, max_length=max_len, truncation=True)
            inp_ids.append(inp["input_ids"])
            lbl_ids.append(out["input_ids"])
        return {"input_ids": inp_ids, "labels": lbl_ids}

    print("Tokenizing train and eval datasets...")
    tokenized_dataset = dataset.map(
        preprocess_batch,
        batched=True,
        batch_size=2000,
        remove_columns=dataset["train"].column_names,
        desc="Tokenizing",
    )

    data_collator = DataCollatorForSeq2Seq(
        tokenizer,
        model=model,
        pad_to_multiple_of=8 if device == "cuda" else None,
    )

    # 5. Build Training Arguments with Dynamic Compatibility
    current_step = 0
    if resume_checkpoint:
        try:
            current_step = int(os.path.basename(resume_checkpoint).split("-")[-1])
        except Exception:
            current_step = 0

    target_max_steps = args.max_steps
    if target_max_steps <= current_step:
        target_max_steps = current_step + args.max_steps
        print("\n" + "=" * 80)
        print(f"[Smart Step Calculation] Checkpoint hiện tại: {current_step} steps.")
        print(f"[Smart Step Calculation] Tự động cộng dồn: Huấn luyện thêm +{args.max_steps} steps.")
        print(f"[Smart Step Calculation] Tổng mục tiêu mới: {target_max_steps} steps.")
        print("=" * 80 + "\n")
    else:
        print("\n" + "=" * 80)
        print(f"[Smart Step Calculation] Checkpoint hiện tại: {current_step} steps.")
        print(f"[Smart Step Calculation] Mục tiêu tổng thể: {target_max_steps} steps (sẽ chạy thêm +{target_max_steps - current_step} steps).")
        print("=" * 80 + "\n")

    sig_args = inspect.signature(Seq2SeqTrainingArguments.__init__)
    args_kwargs = {
        "output_dir": args.output_dir,
        "per_device_train_batch_size": args.batch_size,
        "per_device_eval_batch_size": args.batch_size,
        "gradient_accumulation_steps": args.accum_steps,
        "learning_rate": args.lr,
        "fp16": (device == "cuda"),
        "logging_steps": 50,
        "save_steps": args.save_steps,
        "save_total_limit": 2,
        "max_steps": target_max_steps,
        "report_to": "none",
    }
    if "predict_with_generate" in sig_args.parameters:
        args_kwargs["predict_with_generate"] = False
    if "warmup_steps" in sig_args.parameters:
        args_kwargs["warmup_steps"] = 100

    training_args = Seq2SeqTrainingArguments(**args_kwargs)

    # 6. Build Trainer with Dynamic Compatibility
    trainer_kwargs = {
        "model": model,
        "args": training_args,
        "train_dataset": tokenized_dataset["train"],
        "eval_dataset": tokenized_dataset["validation"],
        "data_collator": data_collator,
    }
    sig_trainer = inspect.signature(Seq2SeqTrainer.__init__)
    if "processing_class" in sig_trainer.parameters:
        trainer_kwargs["processing_class"] = tokenizer
    else:
        trainer_kwargs["tokenizer"] = tokenizer

    trainer = Seq2SeqTrainer(**trainer_kwargs)

    # 7. Start Training
    print(f"\nStarting training chunk (Target: {target_max_steps} total steps)...")
    if resume_checkpoint:
        print(f"Resuming training from checkpoint: {resume_checkpoint}")
        trainer.train(resume_from_checkpoint=resume_checkpoint)
    else:
        trainer.train()

    # 8. Save Final Model & Adapters
    print(f"\nSaving adapter weights to {args.output_dir}...")
    model.save_pretrained(args.output_dir)
    tokenizer.save_pretrained(args.output_dir)

    # 9. Sync to Google Drive
    if os.path.exists("/content/drive/MyDrive"):
        sync_to_drive(args.output_dir, args.drive_dir)

    # 10. Run In-Flight Benchmark Test
    test_samples = [
        ("白小纯深吸一口气，开始运转长生功。", "Xianxia Cultivation"),
        ("克莱恩·莫雷蒂站在贝克兰德的雨夜街头，轻轻按住头顶的黑色礼帽。", "Lord of the Mysteries (Western Name)"),
        ("【系统提示】：玩家HP恢复100点，获得稀有BOSS宝箱！", "Game Stats (HP / BOSS)"),
        ("蒙奇·D·路飞咧嘴大笑，振臂高呼：“我是要成为海贼王的男人！”", "Anime / Manga"),
        ("筑基初期与金丹大圆满，犹如云泥之别。", "Literary Cultivation Realms"),
    ]
    evaluate_sample_sentences(model, tokenizer, test_samples)

    print("\n🎉 Training chunk finished successfully!")


if __name__ == "__main__":
    main()
