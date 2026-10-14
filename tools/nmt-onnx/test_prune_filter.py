"""
Analyze vocabulary reduction for NLLB-200 with 5 target languages:
Vietnamese (vie_Latn), Chinese (zho_Hans, zho_Hant), English (eng_Latn), Japanese (jpn_Jpan), Korean (kor_Hang).
"""

import os
import sys

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")

from transformers import AutoTokenizer

tokenizer = AutoTokenizer.from_pretrained("facebook/nllb-200-distilled-600M")

# Allowed characters
VI_CHARS = set(
    "aáàảãạăắằẳẵặâấầẩẫậbcdđeéèẻẽẹêếềểễệghiklmnoóòỏõọôốồổỗộơớờởỡợpqrstuúùủũụưứừửữựvxyýỳỷỹỵ"
    "AÁÀẢÃẠĂẮẰẲẴẶÂẤẦẨẪẬBCDĐEÉÈẺẼẸÊẾỀỂỄỆGHIKLMNOÓÒỎÕỌÔỐỒỔỖỘƠỚỜỞỠỢPQRSTUÚÙỦŨỤƯỨỪỬỮỰVXYÝỲỶỸỴ"
)
EN_CHARS = set("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ")
DIGITS_PUNCT = set("0123456789 .,!?:;-\"'()[]{}/@#$%&*+=_~<>，。！？“”‘’；：【】（）《》·…—、\\|`^")

ALLOWED_CHARS = VI_CHARS | EN_CHARS | DIGITS_PUNCT


def is_allowed_char(c: str) -> bool:
    if c in ALLOWED_CHARS:
        return True
    code = ord(c)
    # CJK Unified Ideographs (Chinese & Japanese Kanji)
    if (0x4E00 <= code <= 0x9FFF) or (0x3400 <= code <= 0x4DBF):
        return True
    # Japanese Kana
    if (0x3040 <= code <= 0x30FF) or (0x31F0 <= code <= 0x31FF):
        return True
    # Korean Hangul
    if (0xAC00 <= code <= 0xD7AF) or (0x1100 <= code <= 0x11FF) or (0x3130 <= code <= 0x318F):
        return True
    return False


def keep_piece(piece: str) -> bool:
    clean = piece.replace("\u2581", "")
    if not clean:
        return True
    for c in clean:
        if not is_allowed_char(c):
            return False
    return True


target_langs = {"zho_Hans", "zho_Hant", "vie_Latn", "eng_Latn", "jpn_Jpan", "kor_Hang"}
special_tokens = {"<s>", "<pad>", "</s>", "<unk>", "<mask>"} | target_langs

kept = []
discarded = []
for tok, idx in tokenizer.get_vocab().items():
    if tok in special_tokens:
        kept.append((tok, idx))
    elif keep_piece(tok):
        kept.append((tok, idx))
    else:
        discarded.append((tok, idx))

print(f"Total original vocab: {len(tokenizer.get_vocab())}")
print(f"Kept vocab size: {len(kept)}")
print(f"Discarded: {len(discarded)}")
print(f"Reduction percentage: {len(discarded) / len(tokenizer.get_vocab()) * 100:.2f}%")
