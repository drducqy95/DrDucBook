"""
Package the Pruned NLLB-370M Base Model, the 100% Full-Scale Chinese -> Vietnamese Novel Dataset
(with 600+ Calibrated Proper Names, Anime/Manhwa/Game entities), and Golden Test Sets into
dist/nllb_full_zh_vi_bundle.zip for Google Colab / Kaggle cloud training.
"""

import os
import sys
import time
import zipfile

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")
if hasattr(sys.stderr, "reconfigure"):
    sys.stderr.reconfigure(encoding="utf-8")

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
MODEL_DIR = os.path.join(SCRIPT_DIR, "nllb_pruned_370m")
DATA_DIR = os.path.join(SCRIPT_DIR, "dataset_full_zh_vi")
TEST_DIR = os.path.join(SCRIPT_DIR, "testdata")
DIST_DIR = os.path.join(SCRIPT_DIR, "dist")
ZIP_OUT = os.path.join(DIST_DIR, "nllb_full_zh_vi_bundle.zip")


def main():
    os.makedirs(DIST_DIR, exist_ok=True)
    print("=" * 80)
    print("PACKAGING FULL CHINESE -> VIETNAMESE TRAINING BUNDLE (WITH PROPER NAMES)")
    print("=" * 80)
    print(f"Output: {ZIP_OUT}")

    dir_mappings = [
        ("nllb_pruned_370m", MODEL_DIR),
        ("dataset_full_zh_vi", DATA_DIR),
        ("testdata", TEST_DIR),
    ]

    t0 = time.time()
    total_files = 0

    with zipfile.ZipFile(ZIP_OUT, "w", zipfile.ZIP_DEFLATED) as zf:
        for arc_prefix, src_dir in dir_mappings:
            if not os.path.exists(src_dir):
                print(f"WARNING: Directory not found: {src_dir}")
                continue
            print(f"Archiving folder '{src_dir}' as '{arc_prefix}'...")
            for root, dirs, files in os.walk(src_dir):
                for file in files:
                    full_path = os.path.join(root, file)
                    rel_path = os.path.relpath(full_path, src_dir)
                    arcname = os.path.join(arc_prefix, rel_path)
                    zf.write(full_path, arcname)
                    total_files += 1

    elapsed = time.time() - t0
    size_mb = os.path.getsize(ZIP_OUT) / (1024 * 1024)
    print(f"Bundle created successfully in {elapsed:.2f}s!")
    print(f"Total files archived: {total_files}")
    print(f"Zip file size       : {size_mb:.2f} MB ({os.path.getsize(ZIP_OUT):,} bytes)")
    print("=" * 80)


if __name__ == "__main__":
    main()
