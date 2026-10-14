"""
Package pruned NLLB base model and novel training dataset for cloud training (Colab/Kaggle).
Produces:
- tools/nmt-onnx/dist/nllb_novel_training_bundle.zip (or splits if large)
Optionally pushes directly to Hugging Face Hub if HF_TOKEN is present.
"""

import argparse
import os
import sys
import zipfile
import time

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")
if hasattr(sys.stderr, "reconfigure"):
    sys.stderr.reconfigure(encoding="utf-8")

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
MODEL_DIR = os.path.join(SCRIPT_DIR, "nllb_pruned_370m")
DATA_DIR = os.path.join(SCRIPT_DIR, "dataset_novel")
DIST_DIR = os.path.join(SCRIPT_DIR, "dist")
ZIP_OUT = os.path.join(DIST_DIR, "nllb_novel_training_bundle.zip")


def zip_directories(zip_path, dir_mappings):
    os.makedirs(os.path.dirname(zip_path), exist_ok=True)
    print(f"Creating archive: {zip_path}...")
    start_time = time.time()
    
    with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED) as zf:
        for arc_prefix, src_dir in dir_mappings:
            if not os.path.exists(src_dir):
                print(f"Warning: Directory not found: {src_dir}")
                continue
            print(f"Archiving {src_dir} as '{arc_prefix}'...")
            for root, dirs, files in os.walk(src_dir):
                for file in files:
                    full_path = os.path.join(root, file)
                    rel_path = os.path.relpath(full_path, src_dir)
                    arcname = os.path.join(arc_prefix, rel_path)
                    zf.write(full_path, arcname)

    elapsed = time.time() - start_time
    size_mb = os.path.getsize(zip_path) / (1024 * 1024)
    print(f"Archive created successfully in {elapsed:.2f}s ({size_mb:.2f} MB)")


def main():
    parser = argparse.ArgumentParser(description="Package pruned NLLB model & dataset for cloud training.")
    parser.add_argument("--push-to-hub", type=str, default=None, help="Hugging Face repo id to push to (e.g. username/nllb-pruned-novel)")
    args = parser.parse_args()

    dir_mappings = [
        ("nllb_pruned_370m", MODEL_DIR),
        ("dataset_novel", DATA_DIR),
        ("testdata", os.path.join(SCRIPT_DIR, "testdata")),
    ]

    zip_directories(ZIP_OUT, dir_mappings)

    if args.push_to_hub:
        try:
            from huggingface_hub import HfApi
            api = HfApi()
            print(f"Uploading folder {MODEL_DIR} to Hugging Face: {args.push_to_hub}...")
            api.upload_folder(
                folder_path=MODEL_DIR,
                repo_id=args.push_to_hub,
                repo_type="model",
            )
            print("Upload complete!")
        except Exception as e:
            print(f"Failed to upload to HF Hub: {e}")


if __name__ == "__main__":
    main()
