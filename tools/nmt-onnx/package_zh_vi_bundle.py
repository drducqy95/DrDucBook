"""
Package dedicated Chinese-Vietnamese Pruned NLLB Model & Full 516k Novel Dataset for Google Colab.
Output: tools/nmt-onnx/dist/nllb_zh_vi_bundle.zip
"""

import os
import sys
import zipfile
import time

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")
if hasattr(sys.stderr, "reconfigure"):
    sys.stderr.reconfigure(encoding="utf-8")

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
MODEL_DIR = os.path.join(SCRIPT_DIR, "nllb_pruned_zh_vi")
DATA_DIR = os.path.join(SCRIPT_DIR, "dataset_full_zh_vi")
TEST_DIR = os.path.join(SCRIPT_DIR, "testdata")
DIST_DIR = os.path.join(SCRIPT_DIR, "dist")
ZIP_OUT = os.path.join(DIST_DIR, "nllb_zh_vi_bundle.zip")


def main():
    os.makedirs(DIST_DIR, exist_ok=True)
    start_time = time.time()
    print("=" * 80)
    print(f"PACKAGING DEDICATED ZH-VI BUNDLE: {ZIP_OUT}")
    print("=" * 80)

    dir_mappings = [
        ("nllb_pruned_zh_vi", MODEL_DIR),
        ("dataset_full_zh_vi", DATA_DIR),
        ("testdata", TEST_DIR),
    ]

    file_mappings = [
        ("train_colab_zh_vi.py", os.path.join(SCRIPT_DIR, "train_colab_zh_vi.py")),
        ("train_nllb_zh_vi_colab.ipynb", os.path.join(SCRIPT_DIR, "train_nllb_zh_vi_colab.ipynb")),
        ("test_model_benchmarks.py", os.path.join(SCRIPT_DIR, "test_model_benchmarks.py")),
    ]

    with zipfile.ZipFile(ZIP_OUT, "w", zipfile.ZIP_DEFLATED) as zf:
        # Directories
        for arc_prefix, src_dir in dir_mappings:
            if not os.path.exists(src_dir):
                print(f"Warning: Directory not found: {src_dir}")
                continue
            print(f"Archiving folder: {src_dir} -> '{arc_prefix}'...")
            for root, dirs, files in os.walk(src_dir):
                for file in files:
                    full_path = os.path.join(root, file)
                    rel_path = os.path.relpath(full_path, src_dir)
                    arcname = os.path.join(arc_prefix, rel_path)
                    zf.write(full_path, arcname)

        # Standalone files
        for arcname, filepath in file_mappings:
            if os.path.exists(filepath):
                print(f"Archiving script: {filepath} -> '{arcname}'...")
                zf.write(filepath, arcname)

    elapsed = time.time() - start_time
    size_mb = os.path.getsize(ZIP_OUT) / (1024 * 1024)
    print("\n" + "=" * 80)
    print(f"🎉 Bundle created successfully in {elapsed:.2f}s!")
    print(f"File: {ZIP_OUT}")
    print(f"Size: {size_mb:.2f} MB")
    print("=" * 80)


if __name__ == "__main__":
    main()
