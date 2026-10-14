"""
Test candidate pruned tokenizer (50k tokens) covering:
ZH (Simplified & Traditional), VI, EN, JA, KO.
Validates zero OOV (<unk>) on diverse sentences.
"""

import os
import sys

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")

from transformers import AutoTokenizer

tokenizer = AutoTokenizer.from_pretrained("facebook/nllb-200-distilled-600M")

TRUE_VI_DIACRITICS = set(
    "áàảãạăắằẳẵặâấầẩẫậđéèẻẽẹêếềểễệíìỉĩịóòỏõọôốồổỗộơớờởỡợúùủũụưứừửữựýỳỷỹỵ"
    "ÁÀẢÃẠĂẮẰẲẴẶÂẤẦẨẪẬĐÉÈẺẼẸÊẾỀỂỄỆÍÌỈĨỊÓÒỎÕỌÔỐỒỔỖỘƠỚỜỞỠỢÚÙỦŨỤƯỨỪỬỮỰÝỲỶỸỴ"
)

# 1. Collect tokens by category
special_langs = {"<s>", "<pad>", "</s>", "<unk>", "<mask", "zho_Hans", "zho_Hant", "vie_Latn", "eng_Latn", "jpn_Jpan", "kor_Hang"}
vi_accented = set()
cjk_tokens = set()
kana_tokens = set()
hangul_tokens = set()
digits_punct = set()
ascii_candidates = []

for tok, idx in tokenizer.get_vocab().items():
    if tok in special_langs:
        continue
    clean = tok.replace("\u2581", "")
    if not clean:
        digits_punct.add(tok)
        continue

    # Vietnamese accented
    if any(c in TRUE_VI_DIACRITICS for c in clean):
        vi_accented.add(tok)
    # CJK
    elif any(0x4E00 <= ord(c) <= 0x9FFF or 0x3400 <= ord(c) <= 0x4DBF for c in clean):
        cjk_tokens.add(tok)
    # Kana
    elif any(0x3040 <= ord(c) <= 0x30FF for c in clean):
        kana_tokens.add(tok)
    # Hangul
    elif any(0xAC00 <= ord(c) <= 0xD7AF for c in clean):
        hangul_tokens.add(tok)
    # Digits and punctuation
    elif all(not c.isalpha() for c in clean):
        digits_punct.add(tok)
    # Pure ASCII
    elif all(c.isascii() for c in clean):
        # idx reflects frequency in BPE
        ascii_candidates.append((tok, idx))

# Sort ascii candidates by original token index (frequency) and take top 15,000
ascii_candidates.sort(key=lambda x: x[1])
selected_ascii = {tok for tok, idx in ascii_candidates[:15000]}

# Ensure all single ascii letters are kept
for ch in "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ":
    if ch in tokenizer.get_vocab():
        selected_ascii.add(ch)
    if f"\u2581{ch}" in tokenizer.get_vocab():
        selected_ascii.add(f"\u2581{ch}")

pruned_vocab = (
    special_langs
    | vi_accented
    | cjk_tokens
    | kana_tokens
    | hangul_tokens
    | digits_punct
    | selected_ascii
)

print(f"Original vocabulary size : {len(tokenizer.get_vocab())}")
print(f"Pruned vocabulary size   : {len(pruned_vocab)}")
print(f"  - Special & Lang tokens: {len(special_langs)}")
print(f"  - Vietnamese accented  : {len(vi_accented)}")
print(f"  - CJK (Hanzi / Kanji)  : {len(cjk_tokens)}")
print(f"  - Japanese Kana        : {len(kana_tokens)}")
print(f"  - Korean Hangul        : {len(hangul_tokens)}")
print(f"  - Digits & Punctuation : {len(digits_punct)}")
print(f"  - Selected ASCII Latin : {len(selected_ascii)}")
print(f"Total reduction ratio    : {(1 - len(pruned_vocab) / len(tokenizer.get_vocab())) * 100:.2f}%")

# Test coverage on diverse test sentences
test_sentences = [
    # Tiên hiệp / Kiếm hiệp
    "白小纯站在悬崖边缘，迎着呼啸而来的狂风，衣袍猎猎作响。",
    "漫天魔气与纯阳剑气交织碰撞，方圆百里的虚空寸寸塌陷。",
    "道心若是动摇，哪怕吞服再多灵丹妙药，终其一生也休想触碰到元婴大道的一丝门槛。",
    # Tiếng Việt dịch
    "Bạch Tiểu Thuần đứng bên bờ vực thẳm, đón lấy cuồng phong gào thét thổi tới, áo bào phần phật bay.",
    "Vô số ma khí và Thuần Dương kiếm khí đan xen va chạm, hư không trăm dặm từng tấc sụp đổ.",
    "Đạo tâm nếu như dao động, dù cho có nuốt bao nhiêu linh đan diệu dược cũng đừng mơ chạm tới đại đạo Nguyên Anh.",
    # Tiếng Anh
    "The cultivation path to immortality is fraught with peril and tribulations.",
    "Neural machine translation with pruned vocabulary dramatically reduces on-device memory consumption.",
    # Tiếng Nhật
    "彼は静かに目を閉じ、剣の柄に手をかけた。風が木々を揺らしている。",
    # Tiếng Hàn
    "그는 조용히 눈을 감고 검의 손잡이에 손을 얹었다. 바람이 나무들을 흔들고 있었다."
]

print("\n--- TEST TOKENIZATION COVERAGE ---")
for s in test_sentences:
    orig_tokens = tokenizer.tokenize(s)
    oov_tokens = [t for t in orig_tokens if t not in pruned_vocab]
    print(f"Sentence: {s[:40]}...")
    print(f"  Orig tokens count: {len(orig_tokens)}, OOV tokens in pruned vocab: {len(oov_tokens)}")
    if oov_tokens:
        print(f"  ⚠️ OOV tokens: {oov_tokens}")
