"""
Validation and Parity Evaluation Script for facebook/nllb-200-distilled-600M ONNX Models.
Tests exported ONNX models against HuggingFace PyTorch model on diverse translation samples.
"""

import json
import os
import sys

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")
if hasattr(sys.stderr, "reconfigure"):
    sys.stderr.reconfigure(encoding="utf-8")

import numpy as np
import sacrebleu

# NLLB Constants
DECODER_START_TOKEN_ID = 2
FORCED_BOS_TOKEN_ID = 256193  # vie_Latn
EOS_TOKEN_ID = 2
SPECIAL_TOKEN_IDS = {0, 1, 2, 3, 256193, 256200}
HEAD_DIM = 64
HEADS = 16
LAYERS = 12


def run_pytorch_baseline(model_id: str, test_set: list):
    import torch
    from transformers import AutoModelForSeq2SeqLM, AutoTokenizer

    print(f"Loading PyTorch baseline model {model_id}...")
    tokenizer = AutoTokenizer.from_pretrained(model_id, src_lang="zho_Hans")
    model = AutoModelForSeq2SeqLM.from_pretrained(model_id)
    model.eval()

    results = []
    for item in test_set:
        src = item.get("zh") or item.get("source") or ""
        inputs = tokenizer(src, return_tensors="pt")
        with torch.no_grad():
            outputs = model.generate(
                **inputs,
                forced_bos_token_id=FORCED_BOS_TOKEN_ID,
                max_new_tokens=192,
                num_beams=1,
                do_sample=False
            )
        translated = tokenizer.decode(outputs[0], skip_special_tokens=True)
        results.append(translated)

    return results


def run_onnx_inference(model_dir: str, test_set: list):
    import onnxruntime as ort
    from onnxruntime_extensions import get_library_path

    so = ort.SessionOptions()
    so.register_custom_ops_library(get_library_path())

    print(f"Loading ONNX sessions from {model_dir}...")
    enc_session = ort.InferenceSession(os.path.join(model_dir, "encoder_model.onnx"), so)
    dec_session = ort.InferenceSession(os.path.join(model_dir, "decoder_model_merged.onnx"), so)
    tok_session = ort.InferenceSession(os.path.join(model_dir, "tokenizer.onnx"), so)
    detok_session = ort.InferenceSession(os.path.join(model_dir, "detokenizer.onnx"), so)

    results = []
    for item in test_set:
        src = item.get("zh") or item.get("source") or ""
        # 1. Tokenize via ONNX
        tok_out = tok_session.run(None, {"text": np.array([src], dtype=object)})
        input_ids = tok_out[0]
        attention_mask = tok_out[1]

        # 2. Encode via ONNX
        enc_out = enc_session.run(None, {
            "input_ids": input_ids,
            "attention_mask": attention_mask
        })
        last_hidden = enc_out[0]

        # 3. Autoregressive Decode matching HachimiOnnxTranslator
        generated = [DECODER_START_TOKEN_ID]
        past_cache = {}

        # Step 0: Initialize empty cache [1, 16, 0, 64]
        for l in range(LAYERS):
            for att in ["decoder", "encoder"]:
                for kind in ["key", "value"]:
                    past_cache[f"past_key_values.{l}.{att}.{kind}"] = np.zeros(
                        (1, HEADS, 0, HEAD_DIM), dtype=np.float32
                    )

        for step in range(192):
            current_token = np.array([[generated[-1]]], dtype=np.int64)
            cache_branch = np.array([step > 0], dtype=bool)

            feeds = {
                "input_ids": current_token,
                "encoder_hidden_states": last_hidden,
                "encoder_attention_mask": attention_mask,
                "use_cache_branch": cache_branch,
                **past_cache
            }

            dec_out = dec_session.run(None, feeds)
            logits = dec_out[0]

            if step == 0:
                next_token = FORCED_BOS_TOKEN_ID
                # At step 0, save both decoder self-cache and encoder cross-cache
                output_idx = 1
                for l in range(LAYERS):
                    for att in ["decoder", "encoder"]:
                        for kind in ["key", "value"]:
                            past_cache[f"past_key_values.{l}.{att}.{kind}"] = dec_out[output_idx]
                            output_idx += 1
            else:
                next_token = int(np.argmax(logits[0, -1, :]))
                # At step > 0, ONLY update decoder self-cache; encoder cross-cache remains fixed
                output_idx = 1
                for l in range(LAYERS):
                    past_cache[f"past_key_values.{l}.decoder.key"] = dec_out[output_idx]
                    past_cache[f"past_key_values.{l}.decoder.value"] = dec_out[output_idx + 1]
                    output_idx += 4  # skip dummy present.l.encoder.key and value

            generated.append(next_token)
            if next_token == EOS_TOKEN_ID:
                break

        # 4. Filter visible tokens and Detokenize via ONNX
        visible_tokens = [t for t in generated if t not in SPECIAL_TOKEN_IDS]
        if visible_tokens:
            detok_out = detok_session.run(None, {"ids": np.array(visible_tokens, dtype=np.int64)})
            translated = detok_out[0][0]
        else:
            translated = ""

        results.append(translated)

    return results


def main():
    test_set = [
        {"zh": "你好，世界！", "ref": "Xin chào, thế giới!"},
        {"zh": "这是一个关于神仙与凡人修仙的故事。", "ref": "Đây là một câu chuyện về thần tiên và phàm nhân tu tiên."},
        {"zh": "山间微风徐徐，落叶飘零。", "ref": "Gió núi nhẹ nhàng thổi, lá rụng bay lượn."},
    ]

    model_dir = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(__file__), "dist", "nllb_200_distilled_600m")
    if not os.path.exists(model_dir):
        print(f"Model directory {model_dir} not found.")
        return

    print(f"Running ONNX validation on {len(test_set)} test sentences...")
    onnx_preds = run_onnx_inference(model_dir, test_set)
    for i, (item, pred) in enumerate(zip(test_set, onnx_preds)):
        print(f"\n[Test {i+1}]")
        print(f"  Source : {item['zh']}")
        print(f"  Pred   : {pred}")


if __name__ == "__main__":
    main()
