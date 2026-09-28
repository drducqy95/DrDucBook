#!/usr/bin/env python3
"""
ZeroTTS ONNX Quantization & Mobile Optimization Pipeline
Applies Mixed-Precision Dynamic INT8 quantization to Transformer models (MatMul/Gemm only)
while preserving FP32 precision for codec/vocoder to prevent audio artifacts.
"""

import argparse
import os
import shutil
import sys
import time
from pathlib import Path

import onnx
import onnxruntime as ort
from onnxruntime.quantization import quantize_dynamic, QuantType

TRANSFORMER_MODELS = [
    "text_encoder.onnx",
    "prefix_step.onnx",
    "local_frame_decode.onnx",
]

CODEC_FILES = [
    "codec/moss_audio_tokenizer_decode_full.onnx",
    "codec/moss_audio_tokenizer_decode_step.onnx",
    "codec/moss_audio_tokenizer_decode_shared.data",
    "codec/codec_browser_onnx_meta.json",
]


def format_size(bytes_val: int) -> str:
    for unit in ["B", "KB", "MB", "GB"]:
        if bytes_val < 1024.0:
            return f"{bytes_val:.2f} {unit}"
        bytes_val /= 1024.0
    return f"{bytes_val:.2f} TB"


def quantize_transformer_model(
    input_path: Path,
    output_path: Path,
) -> None:
    print(f"\n[*] Quantizing {input_path.name}...")
    start_time = time.time()
    orig_size = input_path.stat().st_size
    print(f"    Original FP32 size: {format_size(orig_size)}")

    output_path.parent.mkdir(parents=True, exist_ok=True)

    quantize_dynamic(
        model_input=str(input_path),
        model_output=str(output_path),
        weight_type=QuantType.QInt8,  # Signed INT8 for ARM NEON SDOT/i8mm
        op_types_to_quantize=["MatMul", "Gemm"],  # Quantize only linear/attention projections
        per_channel=True,  # Per-channel quantization for high dynamic range fidelity
        reduce_range=False,  # False for ARM64 native signed dot product instructions
    )

    elapsed = time.time() - start_time
    new_size = output_path.stat().st_size
    reduction = (1.0 - new_size / orig_size) * 100
    print(f"    Quantized INT8 size: {format_size(new_size)} (-{reduction:.1f}%) in {elapsed:.2f}s")

    # Validate ONNX graph structure
    print(f"    Validating ONNX graph...")
    loaded_model = onnx.load(str(output_path))
    onnx.checker.check_model(loaded_model)

    # Validate ONNX Runtime session load
    opts = ort.SessionOptions()
    opts.log_severity_level = 3  # Error only
    session = ort.InferenceSession(str(output_path), opts, providers=["CPUExecutionProvider"])
    print(f"    Session load OK! Inputs: {len(session.get_inputs())}, Outputs: {len(session.get_outputs())}")


def convert_to_ort(onnx_path: Path, ort_path: Path) -> None:
    print(f"[*] Converting {onnx_path.name} to FlatBuffers (.ort)...")
    try:
        from onnxruntime.tools.convert_onnx_models_to_ort import convert_onnx_models_to_ort
        convert_onnx_models_to_ort(
            model_path_or_dir=str(onnx_path),
            output_dir=str(ort_path.parent),
            optimization_styles=["Basic"],
            target_platform="arm",
        )
        if ort_path.exists():
            print(f"    Generated {ort_path.name} ({format_size(ort_path.stat().st_size)})")
    except Exception as ex:
        print(f"    Warning: .ort conversion skipped or failed: {ex}")


def main():
    parser = argparse.ArgumentParser(description="ZeroTTS ONNX Quantization Tool")
    parser.add_argument(
        "--source-dir",
        type=Path,
        default=Path("dist/zerotts_work/source/onnx"),
        help="Source directory containing FP32 ONNX files",
    )
    parser.add_argument(
        "--output-dir",
        type=Path,
        default=Path("dist/zerotts_work/quantized_onnx"),
        help="Output directory for quantized INT8 models",
    )
    parser.add_argument(
        "--convert-ort",
        action="store_true",
        help="Also convert models to .ort (FlatBuffers) format",
    )
    args = parser.parse_args()

    source_dir: Path = args.source_dir.resolve()
    output_dir: Path = args.output_dir.resolve()

    if not source_dir.exists():
        print(f"Error: Source directory {source_dir} does not exist", file=sys.stderr)
        sys.exit(1)

    output_dir.mkdir(parents=True, exist_ok=True)
    print(f"=== ZeroTTS Quantization Pipeline ===")
    print(f"Source dir: {source_dir}")
    print(f"Output dir: {output_dir}")

    total_orig = 0
    total_quant = 0

    # 1. Quantize 3 transformer models
    for model_name in TRANSFORMER_MODELS:
        src = source_dir / model_name
        if not src.exists():
            print(f"Error: Required model {src} not found!", file=sys.stderr)
            sys.exit(1)

        total_orig += src.stat().st_size

        # Output both as .int8.onnx and .onnx in target directory
        out_int8 = output_dir / model_name.replace(".onnx", ".int8.onnx")
        out_onnx = output_dir / model_name

        quantize_transformer_model(src, out_int8)
        # Also copy/link as standard .onnx so standard package loaders pick it up directly
        shutil.copy2(str(out_int8), str(out_onnx))

        total_quant += out_int8.stat().st_size

        if args.convert_ort:
            out_ort = output_dir / model_name.replace(".onnx", ".ort")
            convert_to_ort(out_int8, out_ort)

    # 2. Copy codec/vocoder files intact (FP32)
    codec_out_dir = output_dir / "codec"
    codec_out_dir.mkdir(parents=True, exist_ok=True)

    print(f"\n[*] Copying codec / vocoder files in full FP32 precision...")
    for rel_path in CODEC_FILES:
        src = source_dir / rel_path
        dst = output_dir / rel_path
        if src.exists():
            dst.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(str(src), str(dst))
            size = src.stat().st_size
            total_orig += size
            total_quant += size
            print(f"    Copied {src.name} ({format_size(size)}) [FP32 UNTOUCHED]")
            # Also copy to root level of output_dir if needed
            root_dst = output_dir / src.name
            shutil.copy2(str(src), str(root_dst))
        else:
            print(f"    Warning: Optional codec file {src} not found")

    print("\n==================================================")
    print(f"Original Total Size:  {format_size(total_orig)}")
    print(f"Quantized Total Size: {format_size(total_quant)}")
    print(f"Overall Reduction:    {(1.0 - total_quant / total_orig) * 100:.1f}%")
    print("==================================================")
    print("[+] Quantization completed successfully!")


if __name__ == "__main__":
    main()
