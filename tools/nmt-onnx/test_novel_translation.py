"""
Test translation of Chinese novel passages using the exported NLLB-200 ONNX model
and compare side-by-side with HachimiMT-60-QT ONNX.
"""

import os
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
HACHIMI_DIR = os.path.join(SCRIPT_DIR, "dist", "hachimi_mt60_qt_zh_vi")

# NLLB Constants
NLLB_DECODER_START_TOKEN_ID = 2
NLLB_FORCED_BOS_TOKEN_ID = 256193  # vie_Latn
NLLB_EOS_TOKEN_ID = 2
NLLB_SPECIAL_TOKEN_IDS = {0, 1, 2, 3, 256193, 256200}
NLLB_HEAD_DIM = 64
NLLB_HEADS = 16
NLLB_LAYERS = 12

# Hachimi Constants
HACHIMI_START_TOKEN_ID = 1
HACHIMI_EOS_TOKEN_ID = 2
HACHIMI_HEAD_DIM = 72
HACHIMI_HEADS = 8
HACHIMI_LAYERS = 2


def load_nllb_sessions():
    so = ort.SessionOptions()
    so.register_custom_ops_library(get_library_path())
    so.graph_optimization_level = ort.GraphOptimizationLevel.ORT_ENABLE_ALL

    print("Loading NLLB-200 ONNX INT8 sessions...")
    return {
        "enc": ort.InferenceSession(os.path.join(NLLB_DIR, "encoder_model.onnx"), so),
        "dec": ort.InferenceSession(os.path.join(NLLB_DIR, "decoder_model_merged.onnx"), so),
        "tok": ort.InferenceSession(os.path.join(NLLB_DIR, "tokenizer.onnx"), so),
        "detok": ort.InferenceSession(os.path.join(NLLB_DIR, "detokenizer.onnx"), so),
    }


def load_hachimi_sessions():
    so = ort.SessionOptions()
    so.register_custom_ops_library(get_library_path())
    so.graph_optimization_level = ort.GraphOptimizationLevel.ORT_ENABLE_ALL

    print("Loading HachimiMT-60-QT ONNX INT8 sessions...")
    return {
        "enc": ort.InferenceSession(os.path.join(HACHIMI_DIR, "encoder_model.onnx"), so),
        "dec": ort.InferenceSession(os.path.join(HACHIMI_DIR, "decoder_model_merged.onnx"), so),
        "tok": ort.InferenceSession(os.path.join(HACHIMI_DIR, "tokenizer.onnx"), so),
        "detok": ort.InferenceSession(os.path.join(HACHIMI_DIR, "detokenizer.onnx"), so),
    }


def translate_nllb(sessions, text: str, no_repeat_ngram_size: int = 2, max_tokens: int = 256):
    start_time = time.perf_counter()

    # 1. Tokenize
    tok_out = sessions["tok"].run(None, {"text": np.array([text], dtype=object)})
    input_ids = tok_out[0]
    attention_mask = tok_out[1]

    # 2. Encode
    enc_out = sessions["enc"].run(None, {
        "input_ids": input_ids,
        "attention_mask": attention_mask
    })
    last_hidden = enc_out[0]

    # 3. Decode
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
            # Apply no_repeat_ngram_size blocking if > 0
            if no_repeat_ngram_size > 0 and len(generated) >= no_repeat_ngram_size:
                prefix = tuple(generated[-(no_repeat_ngram_size - 1):])
                for i in range(len(generated) - no_repeat_ngram_size + 1):
                    ngram = tuple(generated[i:i + no_repeat_ngram_size])
                    if ngram[:-1] == prefix:
                        blocked_token = ngram[-1]
                        logits[0, -1, blocked_token] = -1e9

            next_token = int(np.argmax(logits[0, -1, :]))

            output_idx = 1
            for l in range(NLLB_LAYERS):
                past_cache[f"past_key_values.{l}.decoder.key"] = dec_out[output_idx]
                past_cache[f"past_key_values.{l}.decoder.value"] = dec_out[output_idx + 1]
                output_idx += 4

        generated.append(next_token)
        if next_token == NLLB_EOS_TOKEN_ID:
            break

    # 4. Detokenize
    visible = [t for t in generated if t not in NLLB_SPECIAL_TOKEN_IDS]
    if visible:
        detok_out = sessions["detok"].run(None, {"ids": np.array(visible, dtype=np.int64)})
        translated = str(detok_out[0][0])
    else:
        translated = ""

    elapsed = time.perf_counter() - start_time
    tokens_generated = len(generated)
    return translated, elapsed, tokens_generated


def translate_hachimi(sessions, text: str, max_tokens: int = 256):
    start_time = time.perf_counter()

    tok_out = sessions["tok"].run(None, {"text": np.array([text], dtype=object)})
    input_ids = tok_out[0]
    attention_mask = tok_out[1]

    enc_out = sessions["enc"].run(None, {
        "input_ids": input_ids,
        "attention_mask": attention_mask
    })
    last_hidden = enc_out[0]

    generated = [HACHIMI_START_TOKEN_ID]
    past_cache = {}

    for l in range(HACHIMI_LAYERS):
        for att in ["decoder", "encoder"]:
            for kind in ["key", "value"]:
                past_cache[f"past_key_values.{l}.{att}.{kind}"] = np.zeros(
                    (1, HACHIMI_HEADS, 0, HACHIMI_HEAD_DIM), dtype=np.float32
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
        logits = dec_out[0]

        next_token = int(np.argmax(logits[0, -1, :]))
        generated.append(next_token)

        output_names = [o.name for o in sessions["dec"].get_outputs()]
        for idx, name in enumerate(output_names):
            if name.startswith("present."):
                if step == 0 or ".decoder." in name:
                    past_name = name.replace("present.", "past_key_values.")
                    past_cache[past_name] = dec_out[idx]

        if next_token == HACHIMI_EOS_TOKEN_ID:
            break

    visible = [t for t in generated[1:] if t not in [0, 1, 2, 3]]
    if visible:
        detok_out = sessions["detok"].run(None, {"ids": np.array(visible, dtype=np.int64)})
        text_val = detok_out[0]
        translated = str(text_val[0]) if isinstance(text_val, np.ndarray) else str(text_val)
    else:
        translated = ""

    elapsed = time.perf_counter() - start_time
    tokens_generated = len(generated)
    return translated, elapsed, tokens_generated


def main():
    novel_passages = [
        {
            "title": "Trích đoạn 1: Khung cảnh tu tiên & tâm cảnh (Nhất Niệm Vĩnh Hằng / Tiên Hiệp)",
            "sentences": [
                "白小纯站在悬崖边缘，迎着呼啸而来的狂风，衣袍猎猎作响。",
                "他抬头望向苍穹深处，眼中闪过一丝复杂之色。",
                "“长生修仙之路，果真如此残酷吗？”他喃喃自语道。",
                "四周群山连绵，云雾缭绕，远处隐隐传来上古异兽低沉的咆哮声。",
                "就在此时，一道冰冷的剑光骤然破空而至，快若奔雷，直指他的咽喉！",
                "“交出宗门至宝，饶你不死！”一声厉喝在天地间回荡。"
            ]
        },
        {
            "title": "Trích đoạn 2: Trận chiến kinh thiên & thuật pháp (Đại chiến huyền huyễn)",
            "sentences": [
                "漫天魔气与纯阳剑气交织碰撞，方圆百里的虚空寸寸塌陷。",
                "无数高耸入云的山峰在狂暴的冲击波下瞬间化为齑粉，大地之上裂开一道道深不见底的狰狞鸿沟。",
                "道心若是动摇，哪怕吞服再多灵丹妙药，终其一生也休想触碰到元婴大道的一丝门槛。"
            ]
        },
        {
            "title": "Trích đoạn 3: Đối thoại & tranh đấu thế gia",
            "sentences": [
                "“楚师弟，你当真以为凭借你手中的那柄残缺灵剑，就能活着从这片万毒沼泽中走出去么？”",
                "黑衣人缓步逼近，嘴角勾起一抹残忍而诡异的弧度。",
                "赵家家主脸色铁青地拍案而起，怒喝道：“好你个逆子，竟敢公然废掉你堂兄的丹田气海！”"
            ]
        }
    ]

    nllb_sess = load_nllb_sessions()
    hachimi_sess = load_hachimi_sessions()

    print("\n" + "="*80)
    print("SO SÁNH THỬ NGHIỆM DỊCH TRUYỆN TRUNG: NLLB-200-600M vs HACHIMI-MT60-QT")
    print("="*80)

    for p_idx, passage in enumerate(novel_passages, 1):
        print(f"\n### {passage['title']}")
        print("-" * 80)

        for s_idx, sent in enumerate(passage["sentences"], 1):
            # Translate with NLLB (ngram=2, standard)
            nllb_res, nllb_time, nllb_tok = translate_nllb(nllb_sess, sent, no_repeat_ngram_size=2)
            # Translate with Hachimi (QT style)
            hachimi_res, hachimi_time, hachimi_tok = translate_hachimi(hachimi_sess, sent)

            print(f"\n[Câu {s_idx}] Gốc (Trung): {sent}")
            print(f"  👉 NLLB-200 (Văn xuôi) : {nllb_res}  ({nllb_time:.2f}s, {nllb_tok} tokens)")
            print(f"  👉 Hachimi (Convert/QT): {hachimi_res}  ({hachimi_time:.2f}s, {hachimi_tok} tokens)")

    # Test full continuous paragraph translation with NLLB
    print("\n" + "="*80)
    print("THỬ NGHIỆM DỊCH CẢ ĐOẠN VĂN LIỀN MẠCH (PARAGRAPH CONTEXT)")
    print("="*80)
    full_paragraph = "白小纯站在悬崖边缘，迎着呼啸而来的狂风，衣袍猎猎作响。他抬头望向苍穹深处，眼中闪过一丝复杂之色。“长生修仙之路，果真如此残酷吗？”他喃喃自语道。"
    print(f"\nĐoạn văn gốc:\n{full_paragraph}\n")
    
    nllb_para_ngram0, t0, _ = translate_nllb(nllb_sess, full_paragraph, no_repeat_ngram_size=0)
    nllb_para_ngram2, t2, _ = translate_nllb(nllb_sess, full_paragraph, no_repeat_ngram_size=2)
    nllb_para_ngram3, t3, _ = translate_nllb(nllb_sess, full_paragraph, no_repeat_ngram_size=3)
    hachimi_para, th, _ = translate_hachimi(hachimi_sess, full_paragraph)

    print(f"NLLB (no_repeat=0): {nllb_para_ngram0} ({t0:.2f}s)")
    print(f"NLLB (no_repeat=2): {nllb_para_ngram2} ({t2:.2f}s)")
    print(f"NLLB (no_repeat=3): {nllb_para_ngram3} ({t3:.2f}s)")
    print(f"Hachimi MT60-QT   : {hachimi_para} ({th:.2f}s)")


if __name__ == "__main__":
    main()
