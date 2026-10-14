import json
import sys
import urllib.request

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")

# Read README.md
url_readme = "https://huggingface.co/datasets/ngocdang83/tran-vi-teacher/raw/main/README.md"
req_readme = urllib.request.Request(url_readme, headers={"User-Agent": "Mozilla/5.0"})
try:
    with urllib.request.urlopen(req_readme) as resp:
        print("=== README.md ===")
        print(resp.read().decode("utf-8")[:2000])
except Exception as e:
    print("Readme error:", e)

# Check file sizes via HF API
url_tree = "https://huggingface.co/api/datasets/ngocdang83/tran-vi-teacher/tree/main"
req_tree = urllib.request.Request(url_tree, headers={"User-Agent": "Mozilla/5.0"})
try:
    with urllib.request.urlopen(req_tree) as resp:
        tree = json.loads(resp.read().decode("utf-8"))
        print("\n=== File details ===")
        for item in tree:
            size_mb = item.get("size", 0) / (1024 * 1024)
            print(f"- {item.get('path')}: {size_mb:.2f} MB")
except Exception as e:
    print("Tree error:", e)
