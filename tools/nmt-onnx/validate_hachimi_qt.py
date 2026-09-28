"""
Validation and Parity Evaluation Script for HachimiMT-60-QT ONNX Models.
Tests exported ONNX models against HuggingFace PyTorch model and evaluates on golden test set.
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

def load_golden_test_set(path: str):
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f)

def run_pytorch_baseline(model_id: str, test_set: list):
    import torch
    from transformers import MarianMTModel, MarianTokenizer

    print(f"Loading PyTorch baseline model {model_id}...")
    tokenizer = MarianTokenizer.from_pretrained(model_id)
    model = MarianMTModel.from_pretrained(model_id)
    model.eval()

    results = []
    for item in test_set:
        src = item.get("zh") or item.get("source") or ""
        inputs = tokenizer(src, return_tensors="pt", padding=True, truncation=True)
        with torch.no_grad():
            outputs = model.generate(
                **inputs,
                max_new_tokens=240,
                no_repeat_ngram_size=0,
                repetition_penalty=1.2,
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

    enc_session = ort.InferenceSession(os.path.join(model_dir, "encoder_model.onnx"), so)
    dec_session = ort.InferenceSession(os.path.join(model_dir, "decoder_model_merged.onnx"), so)
    tok_session = ort.InferenceSession(os.path.join(model_dir, "tokenizer.onnx"), so)
    detok_session = ort.InferenceSession(os.path.join(model_dir, "detokenizer.onnx"), so)

    results = []
    for item in test_set:
        src = item.get("zh") or item.get("source") or ""
        # 1. Tokenize
        tok_out = tok_session.run(None, {"text": np.array([src], dtype=object)})
        input_ids = tok_out[0]
        attention_mask = tok_out[1]

        # 2. Encode
        enc_out = enc_session.run(None, {
            "input_ids": input_ids,
            "attention_mask": attention_mask
        })
        last_hidden = enc_out[0]

        # 3. Decode
        generated = [1] # BOS = 1
        past_cache = {}
        head_dim = 72
        heads = 8
        layers = 2

        # Step 0: empty cache
        for l in range(layers):
            for att in ["decoder", "encoder"]:
                for kind in ["key", "value"]:
                    past_cache[f"past_key_values.{l}.{att}.{kind}"] = np.zeros((1, heads, 0, head_dim), dtype=np.float32)

        for step in range(240):
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
            logits = dec_out[0] # [1, 1, 24000]

            # Greedy pick
            next_token = int(np.argmax(logits[0, -1, :]))
            generated.append(next_token)

            # Update cache: encoder cache is frozen after step 0, decoder cache updates every step
            output_names = [o.name for o in dec_session.get_outputs()]
            for idx, name in enumerate(output_names):
                if name.startswith("present."):
                    if step == 0 or ".decoder." in name:
                        past_name = name.replace("present.", "past_key_values.")
                        past_cache[past_name] = dec_out[idx]

            if next_token == 2: # EOS = 2
                break

        # 4. Detokenize
        token_ids = [t for t in generated[1:] if t not in [0, 1, 2, 3]]
        if not token_ids:
            results.append("")
            continue

        detok_out = detok_session.run(None, {"ids": np.array(token_ids, dtype=np.int64)})
        text_val = detok_out[0]
        if isinstance(text_val, np.ndarray):
            text_str = str(text_val[0])
        else:
            text_str = str(text_val)
        results.append(text_str)

    return results

def evaluate_parity(golden_path: str, model_dir: str = "exported_models"):
    data = load_golden_test_set(golden_path)
    test_set = data["test_cases"]

    if not os.path.isabs(model_dir) and not os.path.exists(model_dir):
        alt = os.path.join(os.path.dirname(__file__), model_dir)
        if os.path.exists(alt):
            model_dir = alt

    print(f"Loaded {len(test_set)} test cases from {golden_path}")
    pytorch_trans = run_pytorch_baseline("ngocdang83/HachimiMT-60-QT", test_set)

    if not os.path.exists(os.path.join(model_dir, "decoder_model_merged.onnx")):
        print(f"ONNX model directory {model_dir} not yet generated. Skipping ONNX parity check.")
        return

    onnx_trans = run_onnx_inference(model_dir, test_set)

    chrf = sacrebleu.corpus_chrf(onnx_trans, [[p] for p in pytorch_trans])
    bleu = sacrebleu.corpus_bleu(onnx_trans, [[p] for p in pytorch_trans])

    print(f"\n=== PARITY EVALUATION RESULTS (PyTorch vs ONNX) ===")
    print(f"chrF score: {chrf.score:.2f}%")
    print(f"BLEU score: {bleu.score:.2f}%")

    for i in range(min(5, len(test_set))):
        print(f"\n[Case {i+1}] {test_set[i].get('zh')}")
        print(f"  PyTorch: {pytorch_trans[i]}")
        print(f"  ONNX:    {onnx_trans[i]}")

    assert chrf.score >= 98.0, f"chrF score {chrf.score} is below 98.0% threshold"
    print("\n✅ Parity check PASSED!")

if __name__ == "__main__":
    golden = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(__file__), "testdata/golden_test_set.json")
    evaluate_parity(golden)
