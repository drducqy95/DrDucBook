"""
End-to-End PyTorch to ONNX Export Pipeline for facebook/nllb-200-distilled-600M.
Exports NLLB encoder, merged decoder with past KV cache, source/target tokenizers, and detokenizer.
"""

import os
import shutil
import sys

# Route Hugging Face cache to D: drive to protect C: system drive space
os.environ["HF_HOME"] = os.path.abspath(os.path.join(os.path.dirname(__file__), ".hf_cache"))

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")
if hasattr(sys.stderr, "reconfigure"):
    sys.stderr.reconfigure(encoding="utf-8")

import onnx
from onnx import helper, TensorProto, numpy_helper
import numpy as np
import torch
from transformers import AutoTokenizer, AutoConfig
from optimum.exporters.onnx import main_export

MODEL_ID = "facebook/nllb-200-distilled-600M"
OUTPUT_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), "exported_models_nllb"))

# Language IDs in NLLB-200
ZHO_HANS_ID = 256200  # Default source: Simplified Chinese
VIE_LATN_ID = 256193  # Target: Vietnamese
EOS_ID = 2            # End-of-sequence token


def create_nllb_tokenizer_model(spm_path: str, output_path: str, src_lang_id: int = ZHO_HANS_ID):
    """
    Builds an ONNX graph for NLLB source text tokenization.
    NLLB maps SentencePiece unk (0) -> 3, and normal tokens (>=1) -> id + 1.
    Then prepends src_lang_id and appends eos_id (2).
    """
    print(f"Building NLLB source tokenizer graph from {spm_path} (src_lang_id={src_lang_id}) -> {output_path}")
    with open(spm_path, "rb") as f:
        spm_bytes = f.read()

    nbest = numpy_helper.from_array(np.array([0], dtype=np.int64), "nbest_size")
    alpha = numpy_helper.from_array(np.array([0.0], dtype=np.float32), "alpha")
    add_bos = numpy_helper.from_array(np.array([False], dtype=bool), "add_bos")
    add_eos = numpy_helper.from_array(np.array([False], dtype=bool), "add_eos")
    reverse = numpy_helper.from_array(np.array([False], dtype=bool), "reverse")
    fairseq = numpy_helper.from_array(np.array([False], dtype=bool), "fairseq")
    zero_const = numpy_helper.from_array(np.array([0], dtype=np.int64), "zero_const")
    one_const = numpy_helper.from_array(np.array([1], dtype=np.int64), "one_const")
    three_const = numpy_helper.from_array(np.array([3], dtype=np.int64), "three_const")
    src_lang_const = numpy_helper.from_array(np.array([src_lang_id], dtype=np.int64), "src_lang_const")
    eos_const = numpy_helper.from_array(np.array([EOS_ID], dtype=np.int64), "eos_const")
    zero_axis = numpy_helper.from_array(np.array([0], dtype=np.int64), "zero_axis")
    zero_val = numpy_helper.from_array(np.array([0], dtype=np.int64), "zero_val")

    spm_node = helper.make_node(
        "SentencepieceTokenizer",
        inputs=["text", "nbest_size", "alpha", "add_bos", "add_eos", "reverse", "fairseq"],
        outputs=["tokens_i32", "inst_idx", "tok_idx"],
        domain="ai.onnx.contrib",
        model=spm_bytes
    )
    cast_node = helper.make_node("Cast", inputs=["tokens_i32"], outputs=["tokens_i64"], to=TensorProto.INT64)
    eq_unk_node = helper.make_node("Equal", inputs=["tokens_i64", "zero_const"], outputs=["is_unk"])
    add_offset_node = helper.make_node("Add", inputs=["tokens_i64", "one_const"], outputs=["shifted_ids"])
    where_node = helper.make_node("Where", inputs=["is_unk", "three_const", "shifted_ids"], outputs=["mapped_ids"])
    concat_node = helper.make_node("Concat", inputs=["src_lang_const", "mapped_ids", "eos_const"], outputs=["full_ids"], axis=0)
    unsqueeze_ids = helper.make_node("Unsqueeze", inputs=["full_ids", "zero_axis"], outputs=["input_ids"])
    ge_node = helper.make_node("GreaterOrEqual", inputs=["input_ids", "zero_val"], outputs=["mask_bool"])
    cast_mask_node = helper.make_node("Cast", inputs=["mask_bool"], outputs=["attention_mask"], to=TensorProto.INT64)

    text_in = helper.make_tensor_value_info("text", TensorProto.STRING, [None])
    ids_out = helper.make_tensor_value_info("input_ids", TensorProto.INT64, [1, None])
    mask_out = helper.make_tensor_value_info("attention_mask", TensorProto.INT64, [1, None])

    graph = helper.make_graph(
        [spm_node, cast_node, eq_unk_node, add_offset_node, where_node, concat_node, unsqueeze_ids, ge_node, cast_mask_node],
        "nllb_tokenizer",
        [text_in],
        [ids_out, mask_out],
        initializer=[nbest, alpha, add_bos, add_eos, reverse, fairseq, zero_const, one_const, three_const, src_lang_const, eos_const, zero_axis, zero_val]
    )

    model = helper.make_model(
        graph,
        producer_name="nllb_tokenizer_export",
        opset_imports=[
            helper.make_opsetid("", 14),
            helper.make_opsetid("ai.onnx.contrib", 1)
        ]
    )
    onnx.checker.check_model(model)
    onnx.save(model, output_path)
    print(f"✅ NLLB source tokenizer exported & verified: {output_path}")


def create_nllb_target_tokenizer_model(spm_path: str, output_path: str):
    """
    Builds an ONNX graph for target lexical constraint tokenization.
    NLLB maps SentencePiece unk (0) -> 3, and normal tokens (>=1) -> id + 1.
    """
    print(f"Building NLLB target constraint tokenizer graph from {spm_path} -> {output_path}")
    with open(spm_path, "rb") as f:
        spm_bytes = f.read()

    nbest = numpy_helper.from_array(np.array([0], dtype=np.int64), "nbest_size")
    alpha = numpy_helper.from_array(np.array([0.0], dtype=np.float32), "alpha")
    add_bos = numpy_helper.from_array(np.array([False], dtype=bool), "add_bos")
    add_eos = numpy_helper.from_array(np.array([False], dtype=bool), "add_eos")
    reverse = numpy_helper.from_array(np.array([False], dtype=bool), "reverse")
    fairseq = numpy_helper.from_array(np.array([False], dtype=bool), "fairseq")
    zero_const = numpy_helper.from_array(np.array([0], dtype=np.int64), "zero_const")
    one_const = numpy_helper.from_array(np.array([1], dtype=np.int64), "one_const")
    three_const = numpy_helper.from_array(np.array([3], dtype=np.int64), "three_const")
    zero_axis = numpy_helper.from_array(np.array([0], dtype=np.int64), "zero_axis")
    zero_val = numpy_helper.from_array(np.array([0], dtype=np.int64), "zero_val")

    spm_node = helper.make_node(
        "SentencepieceTokenizer",
        inputs=["text", "nbest_size", "alpha", "add_bos", "add_eos", "reverse", "fairseq"],
        outputs=["tokens_i32", "inst_idx", "tok_idx"],
        domain="ai.onnx.contrib",
        model=spm_bytes
    )
    cast_node = helper.make_node("Cast", inputs=["tokens_i32"], outputs=["tokens_i64"], to=TensorProto.INT64)
    eq_unk_node = helper.make_node("Equal", inputs=["tokens_i64", "zero_const"], outputs=["is_unk"])
    add_offset_node = helper.make_node("Add", inputs=["tokens_i64", "one_const"], outputs=["shifted_ids"])
    where_node = helper.make_node("Where", inputs=["is_unk", "three_const", "shifted_ids"], outputs=["mapped_ids"])
    unsqueeze_ids = helper.make_node("Unsqueeze", inputs=["mapped_ids", "zero_axis"], outputs=["input_ids"])
    ge_node = helper.make_node("GreaterOrEqual", inputs=["input_ids", "zero_val"], outputs=["mask_bool"])
    cast_mask_node = helper.make_node("Cast", inputs=["mask_bool"], outputs=["attention_mask"], to=TensorProto.INT64)

    text_in = helper.make_tensor_value_info("text", TensorProto.STRING, [None])
    ids_out = helper.make_tensor_value_info("input_ids", TensorProto.INT64, [1, None])
    mask_out = helper.make_tensor_value_info("attention_mask", TensorProto.INT64, [1, None])

    graph = helper.make_graph(
        [spm_node, cast_node, eq_unk_node, add_offset_node, where_node, unsqueeze_ids, ge_node, cast_mask_node],
        "nllb_target_tokenizer",
        [text_in],
        [ids_out, mask_out],
        initializer=[nbest, alpha, add_bos, add_eos, reverse, fairseq, zero_const, one_const, three_const, zero_axis, zero_val]
    )

    model = helper.make_model(
        graph,
        producer_name="nllb_target_tokenizer_export",
        opset_imports=[
            helper.make_opsetid("", 14),
            helper.make_opsetid("ai.onnx.contrib", 1)
        ]
    )
    onnx.checker.check_model(model)
    onnx.save(model, output_path)
    print(f"✅ NLLB target constraint tokenizer exported & verified: {output_path}")


def create_nllb_detokenizer_model(spm_path: str, output_path: str):
    """
    Builds an ONNX graph for NLLB detokenization.
    Maps NLLB unk (3) -> SentencePiece 0, and normal tokens (>=4) -> id - 1.
    """
    print(f"Building NLLB detokenizer graph from {spm_path} -> {output_path}")
    with open(spm_path, "rb") as f:
        spm_bytes = f.read()

    ids_in = helper.make_tensor_value_info("ids", TensorProto.INT64, [None])
    text_out = helper.make_tensor_value_info("text", TensorProto.STRING, [1])
    fairseq = numpy_helper.from_array(np.array([False], dtype=bool), "fairseq")
    zero_const = numpy_helper.from_array(np.array([0], dtype=np.int64), "zero_const")
    one_const = numpy_helper.from_array(np.array([1], dtype=np.int64), "one_const")
    three_const = numpy_helper.from_array(np.array([3], dtype=np.int64), "three_const")

    eq_unk_node = helper.make_node("Equal", inputs=["ids", "three_const"], outputs=["is_unk"])
    sub_node = helper.make_node("Sub", inputs=["ids", "one_const"], outputs=["shifted_ids"])
    where_node = helper.make_node("Where", inputs=["is_unk", "zero_const", "shifted_ids"], outputs=["spm_ids"])
    spm_dec = helper.make_node(
        "SentencepieceDecoder",
        inputs=["spm_ids", "fairseq"],
        outputs=["text"],
        domain="ai.onnx.contrib",
        model=spm_bytes
    )

    graph = helper.make_graph(
        [eq_unk_node, sub_node, where_node, spm_dec],
        "nllb_detokenizer",
        [ids_in],
        [text_out],
        initializer=[fairseq, zero_const, one_const, three_const]
    )

    model = helper.make_model(
        graph,
        producer_name="nllb_detokenizer_export",
        opset_imports=[
            helper.make_opsetid("", 14),
            helper.make_opsetid("ai.onnx.contrib", 1)
        ]
    )
    onnx.checker.check_model(model)
    onnx.save(model, output_path)
    print(f"✅ NLLB detokenizer exported & verified: {output_path}")


def export_nllb():
    os.makedirs(OUTPUT_DIR, exist_ok=True)
    temp_optimum_dir = os.path.abspath(os.path.join(os.path.dirname(__file__), "_test_export_nllb"))

    dst_encoder = os.path.join(OUTPUT_DIR, "encoder_model.onnx")
    dst_decoder = os.path.join(OUTPUT_DIR, "decoder_model_merged.onnx")

    if not os.path.exists(dst_encoder) or not os.path.exists(dst_decoder):
        merged_decoder_path = os.path.join(temp_optimum_dir, "decoder_model_merged.onnx")
        if not os.path.exists(merged_decoder_path):
            print("=== STEP 1: DOWNLOADING & EXPORTING NLLB VIA OPTIMUM MAIN_EXPORT ===")
            print(f"Model: {MODEL_ID}")
            main_export(
                model_name_or_path=MODEL_ID,
                output=temp_optimum_dir,
                task="text2text-generation-with-past",
                opset=18,
                no_post_process=False
            )

        print("\n=== STEP 2: COPYING & VERIFYING ENCODER & DECODER ===")
        src_encoder = os.path.join(temp_optimum_dir, "encoder_model.onnx")
        if os.path.exists(src_encoder) and not os.path.exists(dst_encoder):
            shutil.copy2(src_encoder, dst_encoder)

        src_decoder = os.path.join(temp_optimum_dir, "decoder_model_merged.onnx")
        if os.path.exists(src_decoder) and not os.path.exists(dst_decoder):
            shutil.copy2(src_decoder, dst_decoder)
            src_decoder_data = os.path.join(temp_optimum_dir, "decoder_model_merged.onnx_data")
            if os.path.exists(src_decoder_data):
                shutil.copy2(src_decoder_data, os.path.join(OUTPUT_DIR, "decoder_model_merged.onnx_data"))

    print("\n=== VERIFYING ENCODER & DECODER ===")
    onnx.checker.check_model(dst_encoder)
    print(f"✅ Encoder model verified: {dst_encoder}")
    onnx.checker.check_model(dst_decoder)
    print(f"✅ Decoder merged model verified: {dst_decoder}")

    print("\n=== STEP 3: EXPORTING TOKENIZERS & DETOKENIZER FROM SENTENCEPIECE ===")
    spm_candidate = os.path.join(OUTPUT_DIR, "sentencepiece.bpe.model")
    if not os.path.exists(spm_candidate):
        spm_candidate = os.path.join(temp_optimum_dir, "sentencepiece.bpe.model")
    if not os.path.exists(spm_candidate):
        tok = AutoTokenizer.from_pretrained(MODEL_ID)
        spm_candidate = tok.vocab_file

    dst_tok = os.path.join(OUTPUT_DIR, "tokenizer.onnx")
    if not os.path.exists(dst_tok):
        create_nllb_tokenizer_model(spm_candidate, dst_tok, src_lang_id=ZHO_HANS_ID)

    dst_target_tok = os.path.join(OUTPUT_DIR, "target_tokenizer.onnx")
    if not os.path.exists(dst_target_tok):
        create_nllb_target_tokenizer_model(spm_candidate, dst_target_tok)

    dst_detok = os.path.join(OUTPUT_DIR, "detokenizer.onnx")
    if not os.path.exists(dst_detok):
        create_nllb_detokenizer_model(spm_candidate, dst_detok)

    print(f"\n🎉 All 5 NLLB ONNX models ready in: {OUTPUT_DIR}")
    for fname in sorted(os.listdir(OUTPUT_DIR)):
        fpath = os.path.join(OUTPUT_DIR, fname)
        if os.path.isfile(fpath):
            size_mb = os.path.getsize(fpath) / (1024 * 1024)
            print(f"  - {fname}: {size_mb:.2f} MB")


if __name__ == "__main__":
    export_nllb()
