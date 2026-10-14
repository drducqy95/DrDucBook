import json
import sys

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")

with open("tools/nmt-onnx/dataset_full_zh_vi/train.jsonl", "r", encoding="utf-8") as f:
    for i in range(15):
        line = f.readline()
        if not line:
            break
        item = json.loads(line)
        print(f"[{i+1}]")
        print(f"  ZH: {item['source']}")
        print(f"  VI: {item['target']}")
        print("-" * 60)
