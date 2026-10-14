"""
Benchmark and evaluate a fine-tuned or ONNX model against the 2 Gold-Standard Test Sets:
1. golden_test_set_literary.json (Xianxia / Wuxia / Cultivation Literary prose)
2. golden_test_set_foreign_entities.json (Western / Anime / Manhwa / Game stats)
"""

import argparse
import json
import os
import sys
import torch
from transformers import AutoModelForSeq2SeqLM, AutoTokenizer

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")
if hasattr(sys.stderr, "reconfigure"):
    sys.stderr.reconfigure(encoding="utf-8")

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
TEST_DIR = os.path.join(SCRIPT_DIR, "testdata")


def run_benchmark(
    model_dir: str,
    tokenizer_dir: str,
    benchmark_file: str,
    max_samples: int = 15,
    num_beams: int = 4,
    no_repeat_ngram_size: int = 3,
    repetition_penalty: float = 1.15,
):
    print("=" * 80)
    print(f"RUNNING BENCHMARK: {os.path.basename(benchmark_file)}")
    print(f"Model: {model_dir}")
    print(f"Decoding: {'Beam Search (beams=' + str(num_beams) + ')' if num_beams > 1 else 'Greedy Search'}")
    print("=" * 80)

    with open(benchmark_file, "r", encoding="utf-8") as f:
        data = json.load(f)
    cases = data.get("test_cases", [])[:max_samples]

    tokenizer = AutoTokenizer.from_pretrained(tokenizer_dir, local_files_only=True)
    model = AutoModelForSeq2SeqLM.from_pretrained(model_dir, local_files_only=True)
    model.eval()

    tokenizer.src_lang = "zho_Hans"
    tgt_id = tokenizer.convert_tokens_to_ids("vie_Latn")

    for idx, tc in enumerate(cases, 1):
        zh = tc.get("zh", "").strip()
        expected = tc.get("expected_vi", "").strip()
        category = tc.get("category", "")

        inp = tokenizer(zh, return_tensors="pt")
        with torch.no_grad():
            gen_kwargs = {
                "forced_bos_token_id": tgt_id,
                "max_new_tokens": 128,
            }
            if num_beams > 1:
                gen_kwargs["num_beams"] = num_beams
                gen_kwargs["no_repeat_ngram_size"] = no_repeat_ngram_size
                gen_kwargs["repetition_penalty"] = repetition_penalty
            out = model.generate(**inp, **gen_kwargs)
        pred = tokenizer.decode(out[0], skip_special_tokens=True)

        print(f"[{idx}/{len(cases)}] Category: {category}")
        print(f"ZH  : {zh}")
        print(f"PRED: {pred}")
        print(f"EXP : {expected}")
        print("-" * 80)


def main():
    parser = argparse.ArgumentParser(description="Evaluate model on novel benchmarks.")
    parser.add_argument("--model-dir", type=str, default="tools/nmt-onnx/nllb_pruned_370m")
    parser.add_argument("--tokenizer-dir", type=str, default="tools/nmt-onnx/nllb_pruned_370m")
    parser.add_argument("--benchmark", type=str, default="foreign", choices=["literary", "foreign", "both"])
    parser.add_argument("--num-beams", type=int, default=4, help="Beam size (1 for greedy, 4 for high-quality)")
    parser.add_argument("--no-repeat-ngram-size", type=int, default=3)
    parser.add_argument("--repetition-penalty", type=float, default=1.15)
    args = parser.parse_args()

    bench_kwargs = {
        "num_beams": args.num_beams,
        "no_repeat_ngram_size": args.no_repeat_ngram_size,
        "repetition_penalty": args.repetition_penalty,
    }

    if args.benchmark in ["literary", "both"]:
        lit_file = os.path.join(TEST_DIR, "golden_test_set_literary.json")
        run_benchmark(args.model_dir, args.tokenizer_dir, lit_file, **bench_kwargs)

    if args.benchmark in ["foreign", "both"]:
        for_file = os.path.join(TEST_DIR, "golden_test_set_foreign_entities.json")
        run_benchmark(args.model_dir, args.tokenizer_dir, for_file, **bench_kwargs)


if __name__ == "__main__":
    main()
