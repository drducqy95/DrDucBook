"""
Test NLLB-200 translation with Dictionary / Terminology integration.
Evaluates Pre-masking, Post-enforcement, and Hybrid pipeline matching DrDucBook.
"""

import os
import re
import sys
import time

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")
if hasattr(sys.stderr, "reconfigure"):
    sys.stderr.reconfigure(encoding="utf-8")

import numpy as np
import onnxruntime as ort
from onnxruntime_extensions import get_library_path

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
NLLB_DIR = os.path.join(SCRIPT_DIR, "dist", "nllb_200_distilled_600m")

NLLB_DECODER_START_TOKEN_ID = 2
NLLB_FORCED_BOS_TOKEN_ID = 256193  # vie_Latn
NLLB_EOS_TOKEN_ID = 2
NLLB_SPECIAL_TOKEN_IDS = {0, 1, 2, 3, 256193, 256200}
NLLB_HEAD_DIM = 64
NLLB_HEADS = 16
NLLB_LAYERS = 12


def load_nllb_sessions():
    so = ort.SessionOptions()
    so.register_custom_ops_library(get_library_path())
    so.graph_optimization_level = ort.GraphOptimizationLevel.ORT_ENABLE_ALL

    return {
        "enc": ort.InferenceSession(os.path.join(NLLB_DIR, "encoder_model.onnx"), so),
        "dec": ort.InferenceSession(os.path.join(NLLB_DIR, "decoder_model_merged.onnx"), so),
        "tok": ort.InferenceSession(os.path.join(NLLB_DIR, "tokenizer.onnx"), so),
        "detok": ort.InferenceSession(os.path.join(NLLB_DIR, "detokenizer.onnx"), so),
    }


def nllb_raw_translate(sessions, text: str, no_repeat_ngram_size: int = 2, max_tokens: int = 256) -> str:
    tok_out = sessions["tok"].run(None, {"text": np.array([text], dtype=object)})
    input_ids = tok_out[0]
    attention_mask = tok_out[1]

    enc_out = sessions["enc"].run(None, {
        "input_ids": input_ids,
        "attention_mask": attention_mask
    })
    last_hidden = enc_out[0]

    generated = [NLLB_DECODER_START_TOKEN_ID]
    past_cache = {}

    for l in range(NLLB_LAYERS):
        for att in ["decoder", "encoder"]:
            for kind in ["key", "value"]:
                past_cache[f"past_key_values.{l}.{att}.{kind}"] = np.zeros(
                    (1, NLLB_HEADS, 0, NLLB_HEAD_DIM), dtype=np.float32
                )

    for step in range(max_tokens):
        current_token = np.array([[generated[-1]]], dtype=np.int64)
        cache_branch = np.array([step > 0], dtype=bool)

        feeds = {
            "input_ids": current_token,
            "encoder_hidden_states": last_hidden,
            "encoder_attention_mask": attention_mask,
            "use_cache_branch": cache_branch,
            **past_cache
        }

        dec_out = sessions["dec"].run(None, feeds)
        logits = dec_out[0].copy()

        if step == 0:
            next_token = NLLB_FORCED_BOS_TOKEN_ID
            output_idx = 1
            for l in range(NLLB_LAYERS):
                for att in ["decoder", "encoder"]:
                    for kind in ["key", "value"]:
                        past_cache[f"past_key_values.{l}.{att}.{kind}"] = dec_out[output_idx]
                        output_idx += 1
        else:
            if no_repeat_ngram_size > 0 and len(generated) >= no_repeat_ngram_size:
                prefix = tuple(generated[-(no_repeat_ngram_size - 1):])
                for i in range(len(generated) - no_repeat_ngram_size + 1):
                    ngram = tuple(generated[i:i + no_repeat_ngram_size])
                    if ngram[:-1] == prefix:
                        logits[0, -1, ngram[-1]] = -1e9

            next_token = int(np.argmax(logits[0, -1, :]))

            output_idx = 1
            for l in range(NLLB_LAYERS):
                past_cache[f"past_key_values.{l}.decoder.key"] = dec_out[output_idx]
                past_cache[f"past_key_values.{l}.decoder.value"] = dec_out[output_idx + 1]
                output_idx += 4

        generated.append(next_token)
        if next_token == NLLB_EOS_TOKEN_ID:
            break

    visible = [t for t in generated if t not in NLLB_SPECIAL_TOKEN_IDS]
    if visible:
        detok_out = sessions["detok"].run(None, {"ids": np.array(visible, dtype=np.int64)})
        return str(detok_out[0][0])
    return ""


# =========================================================================
# Dictionary Strategies
# =========================================================================

# Strategy 1: Pre-masking with DrDucBook placeholder __ENT_id__
def premask_text(src: str, dictionary: list) -> tuple:
    # Sort dictionary by longest original key first
    sorted_dict = sorted(dictionary, key=lambda x: len(x[0]), reverse=True)
    masked = src
    replacements = []
    for i, (orig, trans) in enumerate(sorted_dict):
        if orig in masked:
            placeholder = f"__ENT_{i}__"
            masked = masked.replace(orig, placeholder)
            replacements.append((placeholder, trans, i))
    return masked, replacements


def unmask_text(translated: str, replacements: list) -> str:
    res = translated
    # Match various forms of placeholder output by NMT (case variations, spacing, underscores)
    for placeholder, trans, idx in replacements:
        pattern = re.compile(
            rf"_*__\s*(?:ENT|ent)\s*_?\s*{idx}\s*__*_*|(?<![\w])(?:ENT|ent)\s*[_]?\s*{idx}(?![\d])_*",
            re.IGNORECASE
        )
        res = pattern.sub(trans, res)
    return res


# Strategy 2: Pre-masking with Tag format [T0], [T1]
def premask_tag_format(src: str, dictionary: list) -> tuple:
    sorted_dict = sorted(dictionary, key=lambda x: len(x[0]), reverse=True)
    masked = src
    replacements = []
    for i, (orig, trans) in enumerate(sorted_dict):
        if orig in masked:
            tag = f"[T{i}]"
            masked = masked.replace(orig, tag)
            replacements.append((tag, trans, i))
    return masked, replacements


def unmask_tag_format(translated: str, replacements: list) -> str:
    res = translated
    for tag, trans, idx in replacements:
        pattern = re.compile(rf"\[\s*T{idx}\s*\]|T{idx}", re.IGNORECASE)
        res = pattern.sub(trans, res)
    return res


# Strategy 3: Post-Enforcement (detect literal mistranslations and replace with dictionary target)
def post_enforce(src_cjk: str, translated_vi: str, dictionary: list, literal_map: dict = None) -> str:
    res = translated_vi
    sorted_dict = sorted(dictionary, key=lambda x: len(x[0]), reverse=True)

    for orig, target in sorted_dict:
        if orig in src_cjk:
            # If literal known mistranslation is present, replace it
            if literal_map and orig in literal_map:
                for bad in literal_map[orig]:
                    if bad in res:
                        res = res.replace(bad, target)

            # If CJK remained untranslated in output, replace directly
            if orig in res:
                res = res.replace(orig, target)

    return res


def main():
    sessions = load_nllb_sessions()

    # Dictionary specifically for this scene
    dictionary = [
        ("白小纯", "Bạch Tiểu Thuần"),
        ("狂风", "cuồng phong"),
        ("衣袍猎猎作响", "áo bào tung bay phần phật"),
        ("苍穹深处", "sâu trong thương khung"),
        ("长生修仙之路", "con đường trường sinh tu tiên"),
        ("上古异兽", "thượng cổ dị thú"),
        ("破空而至", "xé gió lao tới"),
        ("宗门至宝", "chí bảo tông môn"),
        ("饶你不死", "tha cho ngươi không chết"),
        ("元婴大道", "đại đạo Nguyên Anh"),
        ("丹田气海", "đan điền khí hải"),
        ("灵丹妙药", "linh đan diệu dược"),
        ("道心", "đạo tâm"),
    ]

    # Common literal translations produced by NLLB when unmasked
    literal_map = {
        "白小纯": ["Cô bé trắng", "Bé chỉ", "cô bé trắng", "bé chỉ"],
        "衣袍猎猎作响": ["áo khoác săn bắn và săn lùng", "áo khoác săn bắn", "săn bắn săn lùng"],
        "苍穹深处": ["sâu vào vịnh", "sâu trong hầm"],
        "长生修仙之路": ["Cái người lớn tuổi có phải là một người khó chịu như thế", "người lớn tuổi"],
        "上古异兽": ["những con vật cổ xưa, loài thú", "loài thú cổ xưa"],
        "破空而至": ["vứt ra", "đột nhiên vứt ra"],
        "宗门至宝": ["tiền vào cửa của mình", "kho báu của môn phái"],
        "饶你不死": ["để ngươi không chết"],
        "元婴大道": ["con đường con cái"],
        "丹田气海": ["Danta Geng Sea", "biển khí Danta"],
        "灵丹妙药": ["thuốc thần kinh"],
        "道心": ["tâm hồn con người"],
    }

    test_sentences = [
        ("白小纯站在悬崖边缘，迎着呼啸而来的狂风，衣袍猎猎作响。", "Nhân vật chính Bạch Tiểu Thuần & áo bào"),
        ("他抬头望向苍穹深处，眼中闪过一丝复杂之色。", "Tâm cảnh & thương khung"),
        ("“长生修仙之路，果真如此残酷吗？”他喃喃自语道。", "Thoại & trường sinh tu tiên"),
        ("四周群山连绵，云雾缭绕，远处隐隐传来上古异兽低沉的咆哮声。", "Thượng cổ dị thú & miêu tả"),
        ("就在此时，一道冰冷的剑光骤然破空而至，快若奔雷，直指他的咽喉！", "Kiếm quang phá không & chiến đấu"),
        ("“交出宗门至宝，饶你不死！”一声厉喝在天地间回荡。", "Chí bảo tông môn & quát tháo"),
        ("道心若是动摇，哪怕吞服再多灵丹妙药，终其一生也休想触碰到元婴大道的一丝门槛。", "Đạo tâm, linh đan & Nguyên Anh"),
        ("好你个逆子，竟敢公然废掉你堂兄的丹田气海！", "Đan điền khí hải"),
    ]

    print("=" * 80)
    print("THỬ NGHIỆM DỊCH NLLB-200 KẾT HỢP TỪ ĐIỂN / THUẬT NGỮ (DICTIONARY PIPELINE)")
    print("=" * 80)

    for idx, (sent, note) in enumerate(test_sentences, 1):
        print(f"\n[Test {idx}] {note}")
        print(f"  🇨🇳 Gốc: {sent}")

        # 1. Raw NLLB
        raw_vi = nllb_raw_translate(sessions, sent, no_repeat_ngram_size=2)
        print(f"  ❌ NLLB Gốc (Không từ điển) : {raw_vi}")

        # 2. Strategy A: Pre-masking với __ENT_id__
        masked_a, reps_a = premask_text(sent, dictionary)
        trans_a = nllb_raw_translate(sessions, masked_a, no_repeat_ngram_size=2)
        unmasked_a = unmask_text(trans_a, reps_a)
        print(f"  🔹 NLLB + Pre-masking [ENT] : {unmasked_a}")

        # 3. Strategy B: Pre-masking với [T_id]
        masked_b, reps_b = premask_tag_format(sent, dictionary)
        trans_b = nllb_raw_translate(sessions, masked_b, no_repeat_ngram_size=2)
        unmasked_b = unmask_tag_format(trans_b, reps_b)
        print(f"  🔸 NLLB + Pre-masking [T_id]: {unmasked_b}")

        # 4. Strategy C: Post-Enforcement (DrDucBook MlKitDictionaryEnforcer mechanism)
        post_enforced = post_enforce(sent, raw_vi, dictionary, literal_map)
        print(f"  ✅ NLLB + Post-Enforcement  : {post_enforced}")


if __name__ == "__main__":
    main()
