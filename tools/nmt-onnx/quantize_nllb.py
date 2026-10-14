"""
Dynamic INT8 Quantization Script for facebook/nllb-200-distilled-600M ONNX Models.
Quantizes encoder and merged decoder to QInt8, preserving tokenizers.
"""

import os
import shutil
import sys

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")
if hasattr(sys.stderr, "reconfigure"):
    sys.stderr.reconfigure(encoding="utf-8")

import onnx
from onnxruntime.quantization import quantize_dynamic, QuantType
import onnxruntime.quantization.quant_utils as qu
import onnxruntime.quantization.onnx_quantizer as oq
import onnxruntime.quantization.base_quantizer as bq


def quantize_nllb(
    src_dir: str = "exported_models_nllb",
    dst_dir: str = "dist/nllb_200_distilled_600m"
):
    src_dir = os.path.abspath(src_dir)
    dst_dir = os.path.abspath(dst_dir)
    os.makedirs(dst_dir, exist_ok=True)
    print(f"Quantizing NLLB models from {src_dir} to {dst_dir}...")

    # 1. Quantize Encoder
    src_encoder = os.path.join(src_dir, "encoder_model.onnx")
    dst_encoder = os.path.join(dst_dir, "encoder_model.onnx")
    if not os.path.exists(dst_encoder):
        print("Quantizing encoder_model.onnx to INT8...")
        quantize_dynamic(
            model_input=src_encoder,
            model_output=dst_encoder,
            weight_type=QuantType.QInt8,
            op_types_to_quantize=["MatMul", "Gemm", "Gather"]
        )
    onnx.checker.check_model(dst_encoder)
    print(f"✅ Encoder quantized: {os.path.getsize(dst_encoder) / (1024*1024):.2f} MB")

    # 2. Quantize Decoder Merged (subgraph-aware + memory-safe for external data)
    src_decoder = os.path.join(src_dir, "decoder_model_merged.onnx")
    dst_decoder = os.path.join(dst_dir, "decoder_model_merged.onnx")
    if not os.path.exists(dst_decoder) or os.path.getsize(dst_decoder) < 500 * 1024 * 1024:
        print("Quantizing decoder_model_merged.onnx to INT8 with EnableSubgraph...")
        # Patch ORT internals to avoid loading multi-GB external data into single protobuf memory
        oq.save_and_reload_model_with_shape_infer = lambda model: model
        qu.save_and_reload_model_with_shape_infer = lambda model: model
        qu.load_model_with_shape_infer = lambda path: onnx.load(str(path), load_external_data=False)
        orig_to_array = qu.tensor_proto_to_array
        def safe_to_array(init):
            if init.data_type in (onnx.TensorProto.FLOAT, onnx.TensorProto.FLOAT16):
                return onnx.numpy_helper.to_array(init, base_dir=src_dir)
            return orig_to_array(init)
        bq.tensor_proto_to_array = safe_to_array
        qu.tensor_proto_to_array = safe_to_array

        curr_cwd = os.getcwd()
        try:
            os.chdir(src_dir)
            quantize_dynamic(
                model_input=src_decoder,
                model_output=dst_decoder,
                weight_type=QuantType.QInt8,
                op_types_to_quantize=["MatMul", "Gemm", "Gather"],
                extra_options={"EnableSubgraph": True},
                use_external_data_format=False
            )
        finally:
            os.chdir(curr_cwd)
    onnx.checker.check_model(dst_decoder)
    print(f"✅ Decoder quantized: {os.path.getsize(dst_decoder) / (1024*1024):.2f} MB")

    # 3. Copy Tokenizers and Detokenizer as-is
    for fname in ["tokenizer.onnx", "target_tokenizer.onnx", "detokenizer.onnx"]:
        src_file = os.path.join(src_dir, fname)
        dst_file = os.path.join(dst_dir, fname)
        if os.path.exists(src_file):
            shutil.copy2(src_file, dst_file)
            print(f"✅ Copied {fname} ({os.path.getsize(dst_file) / 1024:.1f} KB)")

    print(f"\n🎉 INT8 Quantization complete in: {dst_dir}")
    total_size = sum(os.path.getsize(os.path.join(dst_dir, f)) for f in os.listdir(dst_dir))
    print(f"Total INT8 package size: {total_size / (1024*1024):.2f} MB")


if __name__ == "__main__":
    src = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(__file__), "exported_models_nllb")
    dst = sys.argv[2] if len(sys.argv) > 2 else os.path.join(os.path.dirname(__file__), "dist", "nllb_200_distilled_600m")
    quantize_nllb(src, dst)
