import os
import onnx
from onnx import helper, TensorProto
import onnxruntime as ort
from onnxruntime_extensions import get_library_path

def create_spm_tokenizer_model(spm_bytes: bytes, output_path: str):
    text_in = helper.make_tensor_value_info("text", TensorProto.STRING, [None])
    ids_out = helper.make_tensor_value_info("input_ids", TensorProto.INT64, [None, None])
    mask_out = helper.make_tensor_value_info("attention_mask", TensorProto.INT64, [None, None])

    node = helper.make_node(
        "SentencepieceTokenizer",
        inputs=["text"],
        outputs=["input_ids", "attention_mask"],
        domain="ai.onnx.contrib",
        model=spm_bytes,
        add_bos=True,
        add_eos=True
    )

    graph = helper.make_graph(
        [node],
        "spm_tokenizer",
        [text_in],
        [ids_out, mask_out]
    )

    model = helper.make_model(graph, producer_name="hachimi_export", opset_imports=[
        helper.make_opsetid("", 14),
        helper.make_opsetid("ai.onnx.contrib", 1)
    ])
    onnx.save(model, output_path)
    print(f"Saved SPM tokenizer to {output_path}")

def create_spm_detokenizer_model(spm_bytes: bytes, output_path: str):
    ids_in = helper.make_tensor_value_info("ids", TensorProto.INT64, [None])
    text_out = helper.make_tensor_value_info("text", TensorProto.STRING, [1])

    node = helper.make_node(
        "SentencepieceDecoder",
        inputs=["ids"],
        outputs=["text"],
        domain="ai.onnx.contrib",
        model=spm_bytes
    )

    graph = helper.make_graph(
        [node],
        "spm_detokenizer",
        [ids_in],
        [text_out]
    )

    model = helper.make_model(graph, producer_name="hachimi_export", opset_imports=[
        helper.make_opsetid("", 14),
        helper.make_opsetid("ai.onnx.contrib", 1)
    ])
    onnx.save(model, output_path)
    print(f"Saved SPM detokenizer to {output_path}")

if __name__ == "__main__":
    print("Helper functions defined successfully")
