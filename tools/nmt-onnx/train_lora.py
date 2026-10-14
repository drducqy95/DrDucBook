"""
Fine-tune NLLB-Pruned-370M using PEFT LoRA on Novel Dataset.
Supports:
- Multi-pair training (ZH-VI 87%, VI-ZH 7%, EN-VI, JA-VI, KO-VI).
- PEFT LoRA (r=16, alpha=32, target_modules=["q_proj", "v_proj", "k_proj", "out_proj"]).
- Gradient accumulation & mixed-precision (FP16/BF16).
- Evaluation & sample inference on golden test cases.
- One-click LoRA merge and unload to produce full fine-tuned safetensors for ONNX export.
"""

import argparse
import json
import os
import sys
import time

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")
if hasattr(sys.stderr, "reconfigure"):
    sys.stderr.reconfigure(encoding="utf-8")

import torch
from datasets import load_dataset
from peft import LoraConfig, PeftModel, TaskType, get_peft_model
from transformers import (
    AutoModelForSeq2SeqLM,
    DataCollatorForSeq2Seq,
    NllbTokenizer,
    Seq2SeqTrainer,
    Seq2SeqTrainingArguments,
)

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
DEFAULT_MODEL_DIR = os.path.join(SCRIPT_DIR, "nllb_pruned_370m")
DEFAULT_DATA_DIR = os.path.join(SCRIPT_DIR, "dataset_novel")
DEFAULT_OUTPUT_DIR = os.path.join(SCRIPT_DIR, "nllb_novel_lora_output")
GOLDEN_TEST_FILE = os.path.join(SCRIPT_DIR, "testdata", "golden_test_set_literary.json")


def parse_args():
    parser = argparse.ArgumentParser(description="Fine-tune Pruned NLLB-200 with LoRA on Novel Domain.")
    parser.add_argument("--model-path", type=str, default=DEFAULT_MODEL_DIR, help="Path to pruned NLLB base model")
    parser.add_argument("--data-dir", type=str, default=DEFAULT_DATA_DIR, help="Path to dataset_novel directory")
    parser.add_argument("--output-dir", type=str, default=DEFAULT_OUTPUT_DIR, help="Path to save checkpoints and LoRA adapter")
    parser.add_argument("--batch-size", type=int, default=8, help="Per-device batch size")
    parser.add_argument("--grad-accum", type=int, default=4, help="Gradient accumulation steps")
    parser.add_argument("--lr", type=float, default=2e-4, help="Learning rate")
    parser.add_argument("--epochs", type=int, default=2, help="Number of training epochs")
    parser.add_argument("--max-length", type=int, default=256, help="Maximum sequence length")
    parser.add_argument("--lora-r", type=int, default=16, help="LoRA rank dimension")
    parser.add_argument("--lora-alpha", type=int, default=32, help="LoRA scaling factor")
    parser.add_argument("--lora-dropout", type=float, default=0.05, help="LoRA dropout rate")
    parser.add_argument("--eval-steps", type=int, default=500, help="Evaluate every N steps")
    parser.add_argument("--save-steps", type=int, default=1000, help="Save checkpoint every N steps")
    parser.add_argument("--max-train-samples", type=int, default=None, help="Cap training samples (for quick testing)")
    parser.add_argument("--merge-and-save", action="store_true", help="Merge LoRA into base weights and export standalone model")
    return parser.parse_args()


def load_tokenized_dataset(data_dir, tokenizer, max_length=256, max_train_samples=None):
    train_file = os.path.join(data_dir, "train.jsonl")
    eval_file = os.path.join(data_dir, "eval.jsonl")

    if not os.path.exists(train_file):
        raise FileNotFoundError(f"Train file not found: {train_file}")

    print(f"Loading raw dataset from {data_dir}...")
    dataset = load_dataset(
        "json",
        data_files={"train": train_file, "validation": eval_file},
    )

    if max_train_samples and max_train_samples < len(dataset["train"]):
        print(f"Subsampling training set to {max_train_samples} samples...")
        dataset["train"] = dataset["train"].select(range(max_train_samples))

    print(f"Dataset split counts: Train={len(dataset['train'])}, Eval={len(dataset['validation'])}")

    def preprocess_batch(batch):
        input_ids_list = []
        labels_list = []
        for src, tgt, src_lang, tgt_lang in zip(
            batch["source"], batch["target"], batch["src_lang"], batch["tgt_lang"]
        ):
            tokenizer.src_lang = src_lang
            tokenizer.tgt_lang = tgt_lang
            inp = tokenizer(src, max_length=max_length, truncation=True)
            out = tokenizer(text_target=tgt, max_length=max_length, truncation=True)
            input_ids_list.append(inp["input_ids"])
            labels_list.append(out["input_ids"])

        return {
            "input_ids": input_ids_list,
            "labels": labels_list,
        }

    print("Tokenizing dataset across all language pairs...")
    tokenized_dataset = dataset.map(
        preprocess_batch,
        batched=True,
        batch_size=1000,
        remove_columns=dataset["train"].column_names,
        desc="Tokenizing",
    )
    return tokenized_dataset


def evaluate_golden_test(model, tokenizer, golden_path, device):
    if not os.path.exists(golden_path):
        print("Golden test file not found. Skipping golden evaluation.")
        return

    with open(golden_path, "r", encoding="utf-8") as f:
        data = json.load(f)

    test_cases = data.get("test_cases", [])[:5]  # Test first 5 cases
    print("\n" + "=" * 80)
    print("GOLDEN NOVEL BENCHMARK (SAMPLE TEST POST-TRAINING)")
    print("=" * 80)

    model.eval()
    tokenizer.src_lang = "zho_Hans"
    target_lang_id = tokenizer.convert_tokens_to_ids("vie_Latn")

    for i, tc in enumerate(test_cases, 1):
        zh_text = tc["zh"]
        expected_vi = tc.get("expected_vi", tc.get("expected_vi_qt", ""))

        inputs = tokenizer(zh_text, return_tensors="pt").to(device)
        with torch.no_grad():
            generated_tokens = model.generate(
                **inputs,
                forced_bos_token_id=target_lang_id,
                max_new_tokens=256,
                num_beams=4,
            )
        output_vi = tokenizer.batch_decode(generated_tokens, skip_special_tokens=True)[0]

        print(f"[{i:02d}] ZH      : {zh_text}")
        print(f"     PRED VI : {output_vi}")
        print(f"     EXP  VI : {expected_vi}")
        print("-" * 80)


def main():
    args = parse_args()
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    print(f"Using device: {device} ({torch.cuda.get_device_name(0) if torch.cuda.is_available() else 'CPU'})")

    # 1. Load Tokenizer & Base Model
    print(f"Loading pruned tokenizer from: {args.model_path}")
    tokenizer = NllbTokenizer.from_pretrained(args.model_path)

    print(f"Loading pruned model from: {args.model_path}")
    model = AutoModelForSeq2SeqLM.from_pretrained(
        args.model_path,
        torch_dtype=torch.float16 if torch.cuda.is_available() else torch.float32,
    )

    # 2. Setup LoRA
    print("\nConfiguring PEFT LoRA...")
    lora_config = LoraConfig(
        task_type=TaskType.SEQ_2_SEQ_LM,
        inference_mode=False,
        r=args.lora_r,
        lora_alpha=args.lora_alpha,
        lora_dropout=args.lora_dropout,
        target_modules=["q_proj", "v_proj", "k_proj", "out_proj"],
        bias="none",
    )
    model = get_peft_model(model, lora_config)
    model.print_trainable_parameters()

    # 3. Load & Tokenize Dataset
    tokenized_dataset = load_tokenized_dataset(
        args.data_dir,
        tokenizer,
        max_length=args.max_length,
        max_train_samples=args.max_train_samples,
    )

    # 4. Data Collator
    data_collator = DataCollatorForSeq2Seq(
        tokenizer,
        model=model,
        pad_to_multiple_of=8 if torch.cuda.is_available() else None,
    )

    # 5. Training Arguments
    os.makedirs(args.output_dir, exist_ok=True)
    training_args = Seq2SeqTrainingArguments(
        output_dir=args.output_dir,
        per_device_train_batch_size=args.batch_size,
        per_device_eval_batch_size=args.batch_size,
        gradient_accumulation_steps=args.grad_accum,
        learning_rate=args.lr,
        warmup_steps=500,
        weight_decay=0.01,
        num_train_epochs=args.epochs,
        logging_steps=50,
        eval_strategy="steps",
        eval_steps=args.eval_steps,
        save_strategy="steps",
        save_steps=args.save_steps,
        save_total_limit=3,
        fp16=torch.cuda.is_available(),
        predict_with_generate=False,
        load_best_model_at_end=True,
        metric_for_best_model="loss",
        greater_is_better=False,
        report_to="none",
        dataloader_num_workers=2 if os.name != "nt" else 0,
    )

    # 6. Trainer (Compatible with Transformers v4 & v5)
    import inspect
    trainer_kwargs = {
        "model": model,
        "args": training_args,
        "train_dataset": tokenized_dataset["train"],
        "eval_dataset": tokenized_dataset["validation"],
        "data_collator": data_collator,
    }
    if "processing_class" in inspect.signature(Seq2SeqTrainer.__init__).parameters:
        trainer_kwargs["processing_class"] = tokenizer
    else:
        trainer_kwargs["tokenizer"] = tokenizer

    trainer = Seq2SeqTrainer(**trainer_kwargs)

    print("\nStarting LoRA Fine-Tuning...")
    trainer.train()

    # 7. Save LoRA Adapter
    adapter_save_dir = os.path.join(args.output_dir, "lora_adapter")
    print(f"\nSaving LoRA adapter to: {adapter_save_dir}")
    model.save_pretrained(adapter_save_dir)
    tokenizer.save_pretrained(adapter_save_dir)

    # 8. Merge & Export Standalone Model (if requested)
    if args.merge_and_save:
        merged_dir = os.path.join(args.output_dir, "merged_nllb_pruned_novel")
        print(f"\nMerging LoRA adapter back into base weights -> {merged_dir}...")
        merged_model = model.merge_and_unload()
        merged_model.save_pretrained(merged_dir)
        tokenizer.save_pretrained(merged_dir)
        print("Merged model saved successfully! Ready for ONNX export & quantization.")

    # 9. Evaluate sample golden cases
    evaluate_golden_test(model, tokenizer, GOLDEN_TEST_FILE, device)


if __name__ == "__main__":
    main()
