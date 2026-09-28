"""
End-to-End PyTorch to ONNX Export Pipeline for ngocdang83/HachimiMT-60-QT.
Exports Marian encoder, merged decoder with past KV cache, source/target tokenizers, and detokenizer.
"""

import os
import shutil
import sys

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")
if hasattr(sys.stderr, "reconfigure"):
    sys.stderr.reconfigure(encoding="utf-8")

import onnx
from onnx import helper, TensorProto, numpy_helper
import numpy as np
import torch
from transformers import MarianTokenizer, MarianMTModel
from optimum.exporters.onnx import main_export

MODEL_ID = "ngocdang83/HachimiMT-60-QT"
COMMIT_SHA = "5588b84c0496ea582bb02c13e29af9b368a48150"
OUTPUT_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), "exported_models"))

def create_spm_tokenizer_model(spm_path: str, output_path: str):
    print(f"Building tokenizer graph from {spm_path} -> {output_path}")
    with open(spm_path, "rb") as f:
        spm_bytes = f.read()

    nbest = numpy_helper.from_array(np.array([0], dtype=np.int64), "nbest_size")
    alpha = numpy_helper.from_array(np.array([0.0], dtype=np.float32), "alpha")
    add_bos = numpy_helper.from_array(np.array([False], dtype=bool), "add_bos")
    add_eos = numpy_helper.from_array(np.array([True], dtype=bool), "add_eos")
    reverse = numpy_helper.from_array(np.array([False], dtype=bool), "reverse")
    fairseq = numpy_helper.from_array(np.array([False], dtype=bool), "fairseq")
    unsqueeze_axes = numpy_helper.from_array(np.array([0], dtype=np.int64), "unsqueeze_axes")
    zero_val = numpy_helper.from_array(np.array([0], dtype=np.int64), "zero_val")

    spm_node = helper.make_node(
        "SentencepieceTokenizer",
        inputs=["text", "nbest_size", "alpha", "add_bos", "add_eos", "reverse", "fairseq"],
        outputs=["tokens_i32", "instance_indices", "token_indices"],
        domain="ai.onnx.contrib",
        model=spm_bytes
    )

    cast_node = helper.make_node("Cast", inputs=["tokens_i32"], outputs=["tokens_i64"], to=TensorProto.INT64)
    unsqueeze_ids = helper.make_node("Unsqueeze", inputs=["tokens_i64", "unsqueeze_axes"], outputs=["input_ids"])
    ge_node = helper.make_node("GreaterOrEqual", inputs=["input_ids", "zero_val"], outputs=["mask_bool"])
    cast_mask_node = helper.make_node("Cast", inputs=["mask_bool"], outputs=["attention_mask"], to=TensorProto.INT64)

    text_in = helper.make_tensor_value_info("text", TensorProto.STRING, [None])
    ids_out = helper.make_tensor_value_info("input_ids", TensorProto.INT64, [1, None])
    mask_out = helper.make_tensor_value_info("attention_mask", TensorProto.INT64, [1, None])

    graph = helper.make_graph(
        [spm_node, cast_node, unsqueeze_ids, ge_node, cast_mask_node],
        "spm_tokenizer",
        [text_in],
        [ids_out, mask_out],
        initializer=[nbest, alpha, add_bos, add_eos, reverse, fairseq, unsqueeze_axes, zero_val]
    )

    model = helper.make_model(
        graph,
        producer_name="hachimi_qt_export",
        opset_imports=[
            helper.make_opsetid("", 14),
            helper.make_opsetid("ai.onnx.contrib", 1)
        ]
    )
    onnx.checker.check_model(model)
    onnx.save(model, output_path)
    print(f"✅ Tokenizer exported & verified: {output_path}")

def create_spm_detokenizer_model(spm_path: str, output_path: str):
    print(f"Building detokenizer graph from {spm_path} -> {output_path}")
    with open(spm_path, "rb") as f:
        spm_bytes = f.read()

    ids_in = helper.make_tensor_value_info("ids", TensorProto.INT64, [None])
    text_out = helper.make_tensor_value_info("text", TensorProto.STRING, [1])
    fairseq = numpy_helper.from_array(np.array([False], dtype=bool), "fairseq")

    node = helper.make_node(
        "SentencepieceDecoder",
        inputs=["ids", "fairseq"],
        outputs=["text"],
        domain="ai.onnx.contrib",
        model=spm_bytes
    )

    graph = helper.make_graph(
        [node],
        "spm_detokenizer",
        [ids_in],
        [text_out],
        initializer=[fairseq]
    )

    model = helper.make_model(
        graph,
        producer_name="hachimi_qt_export",
        opset_imports=[
            helper.make_opsetid("", 14),
            helper.make_opsetid("ai.onnx.contrib", 1)
        ]
    )
    onnx.checker.check_model(model)
    onnx.save(model, output_path)
    print(f"✅ Detokenizer exported & verified: {output_path}")

def export_hachimi_qt():
    os.makedirs(OUTPUT_DIR, exist_ok=True)
    temp_optimum_dir = os.path.abspath(os.path.join(os.path.dirname(__file__), "_test_export"))

    if not os.path.exists(os.path.join(temp_optimum_dir, "decoder_model_merged.onnx")):
        print(f"=== STEP 1: DOWNLOADING & EXPORTING MARIAN VIA OPTIMUM MAIN_EXPORT ===")
        print(f"Model: {MODEL_ID} (commit: {COMMIT_SHA})")
        main_export(
            model_name_or_path=MODEL_ID,
            output=temp_optimum_dir,
            task="text2text-generation-with-past",
            revision=COMMIT_SHA
        )

    print(f"\n=== STEP 2: COPYING & VERIFYING ENCODER & DECODER ===")
    src_encoder = os.path.join(temp_optimum_dir, "encoder_model.onnx")
    dst_encoder = os.path.join(OUTPUT_DIR, "encoder_model.onnx")
    shutil.copy2(src_encoder, dst_encoder)
    onnx.checker.check_model(dst_encoder)
    print(f"✅ Encoder model verified: {dst_encoder}")

    src_decoder = os.path.join(temp_optimum_dir, "decoder_model_merged.onnx")
    dst_decoder = os.path.join(OUTPUT_DIR, "decoder_model_merged.onnx")
    shutil.copy2(src_decoder, dst_decoder)
    onnx.checker.check_model(dst_decoder)
    print(f"✅ Decoder merged model verified: {dst_decoder}")

    print(f"\n=== STEP 3: EXPORTING TOKENIZERS & DETOKENIZER FROM SPM ===")
    spm_source = os.path.join(temp_optimum_dir, "source.spm")
    spm_target = os.path.join(temp_optimum_dir, "target.spm")

    dst_tok = os.path.join(OUTPUT_DIR, "tokenizer.onnx")
    create_spm_tokenizer_model(spm_source, dst_tok)

    dst_target_tok = os.path.join(OUTPUT_DIR, "target_tokenizer.onnx")
    create_spm_tokenizer_model(spm_target, dst_target_tok)

    dst_detok = os.path.join(OUTPUT_DIR, "detokenizer.onnx")
    create_spm_detokenizer_model(spm_target, dst_detok)

    print(f"\n🎉 All 5 ONNX models exported successfully to: {OUTPUT_DIR}")
    for fname in os.listdir(OUTPUT_DIR):
        fpath = os.path.join(OUTPUT_DIR, fname)
        if os.path.isfile(fpath):
            size_mb = os.path.getsize(fpath) / (1024 * 1024)
            print(f"  - {fname}: {size_mb:.2f} MB")

if __name__ == "__main__":
    export_hachimi_qt()
