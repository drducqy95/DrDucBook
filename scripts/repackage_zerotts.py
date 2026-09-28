#!/usr/bin/env python3
"""
ZeroTTS Repackaging & HuggingFace Upload Tool for Legado.

Downloads model files and precomputed voices from `zeroweight-ai/ZeroTTS`,
converts voice embeddings, packages into Legado format:
  1. Base model package: `legado-tts-zerotts-base.zip` (~903 MB)
     Contains ONNX models, configs, tokenizer, and default voice (0_maichi.bin).
  2. Per-voice addon packages: `legado-tts-zerotts-{key}.zip` (~31 KB each)
     Contains single voice embedding `voices/{id}_{key}.bin`.

Usage:
  uv run --with huggingface_hub,numpy python scripts/repackage_zerotts.py --help
  uv run --with huggingface_hub,numpy python scripts/repackage_zerotts.py --output-dir ./dist/zerotts
  uv run --with huggingface_hub,numpy python scripts/repackage_zerotts.py --upload --hf-token YOUR_TOKEN
"""

import argparse
import hashlib
import json
import os
from pathlib import Path
import sys
import zipfile

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

import numpy as np

SOURCE_REPO = "zeroweight-ai/ZeroTTS"
TARGET_REPO = "Drduc/Legadofork"
TARGET_HF_PREFIX = "packages/tts/zerotts"

VOICES = [
    {"id": 0, "name": "Mai Chi", "key": "maichi", "gender": "nữ", "desc": "trẻ, kể chuyện, nhẹ nhàng"},
    {"id": 1, "name": "Bảo Trang", "key": "baotrang", "gender": "nữ", "desc": "trưởng thành, tin tức, rõ ràng"},
    {"id": 2, "name": "Kim Oanh", "key": "kimoanh", "gender": "nữ", "desc": "trung niên, ấm áp, truyền cảm"},
    {"id": 3, "name": "Gia Huy", "key": "giahuy", "gender": "nam", "desc": "trẻ, trầm ấm, tâm tình"},
    {"id": 4, "name": "Hữu Đức", "key": "huuduc", "gender": "nam", "desc": "lớn tuổi, trầm, điềm đạm"},
    {"id": 5, "name": "Quang Minh", "key": "quangminh", "gender": "nam", "desc": "trẻ, tin tức, dứt khoát"},
    {"id": 6, "name": "Tiến Đạt", "key": "tiendat", "gender": "nam", "desc": "trẻ, bình luận, sôi nổi"},
    {"id": 7, "name": "Hà My", "key": "hamy", "gender": "nữ", "desc": "trẻ, hoạt hình, biểu cảm"},
]

SHARED_ONNX_FILES = [
    ("onnx/text_encoder.onnx", "text_encoder.onnx"),
    ("onnx/prefix_step.onnx", "prefix_step.onnx"),
    ("onnx/local_frame_decode.onnx", "local_frame_decode.onnx"),
    ("onnx/codec/moss_audio_tokenizer_decode_full.onnx", "moss_audio_tokenizer_decode_full.onnx"),
    ("onnx/codec/moss_audio_tokenizer_decode_step.onnx", "moss_audio_tokenizer_decode_step.onnx"),
    ("onnx/codec/moss_audio_tokenizer_decode_shared.data", "moss_audio_tokenizer_decode_shared.data"),
    ("onnx/codec/codec_browser_onnx_meta.json", "codec_browser_onnx_meta.json"),
]

SHARED_CONFIG_FILES = [
    ("config.json", "config.json"),
    ("tokenizer.json", "tokenizer.json"),
    ("null_voice_emb.npy", "null_voice_emb.npy"),
    ("silence_frame.npy", "silence_frame.npy"),
]


def download_source_files(work_dir: Path):
    from huggingface_hub import hf_hub_download

    source_dir = work_dir / "source"
    source_dir.mkdir(parents=True, exist_ok=True)
    print(f"=== Downloading ZeroTTS model files from {SOURCE_REPO} ===")

    for repo_path, _ in SHARED_CONFIG_FILES:
        print(f"  Downloading {repo_path}...")
        hf_hub_download(repo_id=SOURCE_REPO, filename=repo_path, local_dir=source_dir)

    for repo_path, _ in SHARED_ONNX_FILES:
        print(f"  Downloading {repo_path}...")
        hf_hub_download(repo_id=SOURCE_REPO, filename=repo_path, local_dir=source_dir)

    for v in VOICES:
        key = v["key"]
        for fname in ["voice.bin", "voice.npz", "meta.json"]:
            path = f"voices/{key}/{fname}"
            print(f"  Downloading {path}...")
            hf_hub_download(repo_id=SOURCE_REPO, filename=path, local_dir=source_dir)


def extract_voice_latents(source_dir: Path, voice_key: str) -> bytes:
    """Read voice.bin (30720 bytes) or voice.npz -> float32 raw bytes (1, 10, 768)."""
    bin_path = source_dir / f"voices/{voice_key}/voice.bin"
    if bin_path.exists() and bin_path.stat().st_size == 30720:
        return bin_path.read_bytes()

    npz_path = source_dir / f"voices/{voice_key}/voice.npz"
    if npz_path.exists():
        data = np.load(str(npz_path))
        key = list(data.keys())[0]
        arr = data[key].astype(np.float32)
        return arr.tobytes()

    raise FileNotFoundError(f"No valid voice latent file found for {voice_key}")


def generate_voices_catalog() -> str:
    catalog = {
        "engine": "zerotts-onnx-v1",
        "sample_rate": 48000,
        "voices": [
            {
                "id": v["id"],
                "name": v["name"],
                "key": v["key"],
                "file": f"voices/{v['id']}_{v['key']}.bin",
                "gender": v["gender"],
                "description": v["desc"],
            }
            for v in VOICES
        ],
    }
    return json.dumps(catalog, ensure_ascii=False, indent=2)


def sha256_file(path: Path) -> str:
    hasher = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(65536), b""):
            hasher.update(chunk)
    return hasher.hexdigest()


def package_base_model(source_dir: Path, output_dir: Path, quant_dir: Path = None) -> Path:
    zip_name = "legado-tts-zerotts-base.zip"
    zip_path = output_dir / zip_name
    tmp_path = output_dir / f"{zip_name}.tmp"
    print(f"\n=== Packaging Base Model: {zip_name} ===")

    with zipfile.ZipFile(tmp_path, "w", compression=zipfile.ZIP_STORED) as zf:
        for repo_rel, target_name in SHARED_CONFIG_FILES:
            src = source_dir / repo_rel
            zf.write(src, arcname=target_name)
            print(f"  + {target_name} ({src.stat().st_size} bytes)")

        for repo_rel, target_name in SHARED_ONNX_FILES:
            if quant_dir and (quant_dir / target_name).exists():
                src = quant_dir / target_name
                tag = "[INT8 QUANT]" if "moss" not in target_name else "[FP32]"
            else:
                src = source_dir / repo_rel
                tag = "[FP32]"
            zf.write(src, arcname=target_name)
            print(f"  + {target_name} {tag} ({src.stat().st_size / (1024 * 1024):.1f} MB)")

        catalog_json = generate_voices_catalog()
        zf.writestr("zerotts_voices.json", catalog_json.encode("utf-8"))
        print("  + zerotts_voices.json")

        # Include default voice (Mai Chi, id 0)
        default_voice = VOICES[0]
        v_bytes = extract_voice_latents(source_dir, default_voice["key"])
        zf.writestr(f"voices/{default_voice['id']}_{default_voice['key']}.bin", v_bytes)
        print(f"  + voices/{default_voice['id']}_{default_voice['key']}.bin ({len(v_bytes)} bytes)")

    if zip_path.exists():
        zip_path.unlink()
    tmp_path.rename(zip_path)

    size_bytes = zip_path.stat().st_size
    sha = sha256_file(zip_path)
    print(f"  -> Base Model: {size_bytes / (1024*1024):.1f} MB | SHA-256: {sha}")
    return zip_path


def package_voice_addon(source_dir: Path, output_dir: Path, voice: dict) -> Path:
    key = voice["key"]
    vid = voice["id"]
    zip_name = f"legado-tts-zerotts-{key}.zip"
    zip_path = output_dir / zip_name
    tmp_path = output_dir / f"{zip_name}.tmp"

    v_bytes = extract_voice_latents(source_dir, key)
    with zipfile.ZipFile(tmp_path, "w", compression=zipfile.ZIP_STORED) as zf:
        zf.writestr(f"voices/{vid}_{key}.bin", v_bytes)

    if zip_path.exists():
        zip_path.unlink()
    tmp_path.rename(zip_path)

    size_bytes = zip_path.stat().st_size
    sha = sha256_file(zip_path)
    print(f"  -> Voice Addon [{voice['name']}]: {size_bytes} bytes | SHA-256: {sha}")
    return zip_path


def upload_to_huggingface(output_dir: Path, token: str):
    from huggingface_hub import HfApi

    print(f"\n=== Uploading to {TARGET_REPO} ===")
    api = HfApi(token=token)

    for zip_path in sorted(output_dir.glob("legado-tts-zerotts-*.zip")):
        remote_path = f"{TARGET_HF_PREFIX}/{zip_path.name}"
        print(f"  Uploading {zip_path.name} -> {remote_path}...")
        api.upload_file(
            path_or_fileobj=str(zip_path),
            path_in_repo=remote_path,
            repo_id=TARGET_REPO,
            repo_type="dataset",
        )
    print("  [OK] All files uploaded successfully!")


def main():
    parser = argparse.ArgumentParser(description="Repackage ZeroTTS for Legado")
    parser.add_argument("--work-dir", default="./dist/zerotts_work", help="Working directory for downloads")
    parser.add_argument("--output-dir", default="./dist/zerotts", help="Output directory for zip packages")
    parser.add_argument("--skip-download", action="store_true", help="Skip downloading from source repo")
    parser.add_argument("--skip-package", action="store_true", help="Skip packaging if zip files already exist")
    parser.add_argument("--upload", action="store_true", help="Upload resulting packages to HuggingFace")
    parser.add_argument("--hf-token", default=os.getenv("HF_TOKEN"), help="Hugging Face API token with write access")
    parser.add_argument("--quant-dir", default=None, help="Directory containing quantized ONNX models")
    args = parser.parse_args()

    work_dir = Path(args.work_dir).resolve()
    output_dir = Path(args.output_dir).resolve()
    output_dir.mkdir(parents=True, exist_ok=True)
    source_dir = work_dir / "source"

    quant_dir = Path(args.quant_dir).resolve() if args.quant_dir else (work_dir / "quantized_onnx")
    if not quant_dir.exists():
        quant_dir = None
    else:
        print(f"Using quantized ONNX models from: {quant_dir}")

    base_zip = output_dir / "legado-tts-zerotts-base.zip"
    all_zips_exist = (
        base_zip.exists()
        and base_zip.stat().st_size > 300_000_000
        and all((output_dir / f"legado-tts-zerotts-{v['key']}.zip").exists() and (output_dir / f"legado-tts-zerotts-{v['key']}.zip").stat().st_size > 20_000 for v in VOICES)
    )

    if not args.skip_download:
        download_source_files(work_dir)

    if (args.skip_package or args.skip_download) and all_zips_exist and not quant_dir:
        print(f"\n[OK] Found 9 verified packages in {output_dir}, skipping repackaging.")
    else:
        base_zip = package_base_model(source_dir, output_dir, quant_dir=quant_dir)
        voice_zips = []
        print("\n=== Packaging 8 Voice Addon Packages ===")
        for v in VOICES:
            v_zip = package_voice_addon(source_dir, output_dir, v)
            voice_zips.append(v_zip)

        print("\n=======================================================")
        print("SUMMARY FOR ExternalAssetCatalog.kt & hf-artifacts-manifest.json:")
        print("=======================================================")
        print(f"Base Model: {base_zip.name}")
        print(f"  SizeBytes = {base_zip.stat().st_size}L")
        print(f"  Sha256 = \"{sha256_file(base_zip)}\"")
        for v, vz in zip(VOICES, voice_zips):
            print(f"Voice {v['key']}: {vz.name}")
            print(f"  SizeBytes = {vz.stat().st_size}L")
            print(f"  Sha256 = \"{sha256_file(vz)}\"")

    if args.upload:
        if not args.hf_token:
            print("\nError: --hf-token or HF_TOKEN environment variable is required to upload.")
            return
        upload_to_huggingface(output_dir, args.hf_token)


if __name__ == "__main__":
    main()
