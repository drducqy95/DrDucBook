"""
Production Vocabulary Pruning Toolchain for facebook/nllb-200-distilled-600M.
Reduces NLLB-200 vocabulary from 256,206 to ~55,000 tokens for:
- Chinese (zho_Hans, zho_Hant)
- Vietnamese (vie_Latn)
- English (eng_Latn)
- Japanese (jpn_Jpan)
- Korean (kor_Hang)

Shrinks model parameters from ~600M down to ~370M (~38% reduction),
significantly accelerating fine-tuning and mobile ONNX inference.
"""

import argparse
import os
import shutil
import sys
import time

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")
if hasattr(sys.stderr, "reconfigure"):
    sys.stderr.reconfigure(encoding="utf-8")

import sentencepiece.sentencepiece_model_pb2 as model_pb2
import torch
from transformers import AutoModelForSeq2SeqLM, AutoTokenizer, NllbTokenizer

# Allowed Characters & Alphabets
TRUE_VI_DIACRITICS = set(
    "áàảãạăắằẳẵặâấầẩẫậđéèẻẽẹêếềểễệíìỉĩịóòỏõọôốồổỗộơớờởỡợúùủũụưứừửữựýỳỷỹỵ"
    "ÁÀẢÃẠĂẮẰẲẴẶÂẤẦẨẪẬĐÉÈẺẼẸÊẾỀỂỄỆÍÌỈĨỊÓÒỎÕỌÔỐỒỔỖỘƠỚỜỞỠỢÚÙỦŨỤƯỨỪỬỮỰÝỲỶỸỴ"
)

# Common unaccented Vietnamese base syllables to ensure 0% OOV for unaccented words
VI_BASE_SYLLABLES = {
    "an", "anh", "ao", "ap", "at", "ay", "bac", "bai", "ban", "bang", "bao", "bat", "bau", "bay",
    "be", "ben", "beng", "beo", "bi", "bien", "biet", "bim", "bin", "binh", "bip", "bit", "bo",
    "boc", "boi", "bom", "bon", "bong", "bop", "bot", "bu", "bua", "buc", "bui", "bum", "bun",
    "bung", "buoc", "buoi", "buom", "buon", "buot", "bup", "but", "ca", "cac", "cai", "cam", "can",
    "cang", "cao", "cap", "cat", "cau", "cay", "ce", "chai", "cham", "chan", "chang", "chao", "chap",
    "chat", "chau", "chay", "che", "chen", "cheo", "chep", "chet", "chi", "chia", "chiec", "chiem",
    "chien", "chieu", "chim", "chin", "chinh", "chip", "chit", "chiu", "cho", "choa", "choai", "choan",
    "choang", "choc", "choi", "chom", "chon", "chong", "chot", "chu", "chua", "chuan", "chuc", "chui",
    "chum", "chun", "chung", "chuoc", "chuoi", "chuom", "chuon", "chuot", "chup", "chut", "chuyen", "co",
    "coc", "coi", "com", "con", "cong", "cop", "cot", "cu", "cua", "cuc", "cui", "cum", "cun", "cung",
    "cuoc", "cuoi", "cuon", "cuop", "cuot", "cup", "cut", "da", "dac", "dai", "dam", "dan", "dang", "dao",
    "dap", "dat", "dau", "day", "de", "dem", "den", "deo", "dep", "det", "di", "dia", "diec", "diem",
    "dien", "diep", "diet", "dieu", "dim", "din", "dinh", "dip", "dit", "diu", "do", "doa", "doan",
    "doang", "doc", "doi", "dom", "don", "dong", "dop", "dot", "du", "dua", "duc", "due", "dui", "dum",
    "dun", "dung", "duoc", "duoi", "duom", "duong", "duot", "dup", "dut", "duy", "duyen", "duyet", "ga",
    "gac", "gai", "gam", "gan", "gang", "gao", "gap", "gat", "gau", "gay", "ge", "gen", "ghe", "ghem",
    "ghen", "gheo", "ghep", "ghet", "ghi", "ghim", "ghit", "ghu", "gi", "gia", "giac", "giai", "giam",
    "gian", "giang", "giao", "giap", "giat", "giau", "giay", "gie", "giem", "gien", "gieo", "giep",
    "giet", "gieu", "gim", "gin", "gio", "gioa", "gioan", "gioang", "gioc", "gioi", "giom", "gion",
    "giong", "giop", "giot", "giu", "giua", "giuc", "giui", "gium", "giun", "giung", "giup", "giut",
    "go", "goc", "goi", "gom", "gon", "gong", "gop", "got", "gu", "gua", "guc", "gui", "gum", "gun",
    "gung", "guoc", "guoi", "guom", "guong", "guot", "gup", "gut", "ha", "hac", "hai", "ham", "han",
    "hang", "hao", "hap", "hat", "hau", "hay", "he", "hem", "hen", "heo", "hep", "het", "hi", "hia",
    "hiec", "hiem", "hien", "hiep", "hiet", "hieu", "him", "hin", "hinh", "hip", "hit", "hiu", "ho",
    "hoa", "hoac", "hoai", "hoam", "hoan", "hoang", "hoap", "hoat", "hoc", "hoi", "hom", "hon", "hong",
    "hop", "hot", "hu", "hua", "huc", "hue", "huyen", "huyet", "huyen", "huy", "huynh", "huu", "hup",
    "hut", "hung", "hun", "hum", "hui", "ka", "ke", "kem", "ken", "keo", "kep", "ket", "kha", "khac",
    "khai", "kham", "khan", "khang", "khao", "khap", "khat", "khau", "khay", "khe", "khem", "khen", "kheo",
    "khep", "khet", "khi", "khia", "khiec", "khiem", "khien", "khiep", "khiet", "khieu", "khim", "khin",
    "khinh", "khip", "khit", "khiu", "kho", "khoa", "khoac", "khoai", "khoan", "khoang", "khoat", "khoc",
    "khoi", "khom", "khon", "khong", "khop", "khot", "khu", "khua", "khuc", "khue", "khuen", "khui",
    "khum", "khun", "khung", "khuoc", "khuoi", "khuon", "khuop", "khuot", "khup", "khut", "khuy", "khuyen",
    "khuyet", "khuynh", "ki", "kia", "kiec", "kiem", "kien", "kiep", "kiet", "kieu", "kim", "kin", "kinh",
    "kip", "kit", "kiu", "la", "lac", "lai", "lam", "lan", "lang", "lao", "lap", "lat", "lau", "lay", "le",
    "lem", "len", "leo", "lep", "let", "li", "lia", "liec", "liem", "lien", "liep", "liet", "lieu", "lim",
    "lin", "linh", "lip", "lit", "liu", "lo", "loa", "loac", "loai", "loam", "loan", "loang", "loap", "loat",
    "loc", "loi", "lom", "lon", "long", "lop", "lot", "lu", "lua", "luc", "lue", "lui", "lum", "lun", "lung",
    "luoc", "luoi", "luom", "luon", "luop", "luot", "lup", "lut", "luyen", "luyet", "luy", "luyen", "ma",
    "mac", "mai", "mam", "man", "mang", "mao", "map", "mat", "mau", "may", "me", "mem", "men", "meo", "mep",
    "met", "mi", "mia", "miec", "miem", "mien", "miep", "miet", "mieu", "mim", "min", "minh", "mip", "mit",
    "miu", "mo", "moa", "moac", "moan", "moang", "moc", "moi", "mom", "mon", "mong", "mop", "mot", "mu",
    "mua", "muc", "mui", "mum", "mun", "mung", "muoc", "muoi", "muom", "muon", "muop", "muot", "mup", "mut",
    "na", "nac", "nai", "nam", "nan", "nang", "nao", "nap", "nat", "nau", "nay", "ne", "nem", "nen", "neo",
    "nep", "net", "nga", "ngac", "ngai", "ngam", "ngan", "ngang", "ngao", "ngap", "ngat", "ngau", "ngay",
    "nge", "ngem", "ngen", "ngeo", "ngep", "nget", "nghe", "nghem", "nghen", "ngheo", "nghep", "nghet",
    "nghi", "nghia", "nghiec", "nghiem", "nghien", "nghiep", "nghiet", "nghieu", "nghim", "nghin", "nghinh",
    "nghip", "nghit", "nghiu", "ngo", "ngoa", "ngoac", "ngoai", "ngoam", "ngoan", "ngoang", "ngoap", "ngoat",
    "ngoc", "ngoi", "ngom", "ngon", "ngong", "ngop", "ngot", "ngu", "ngua", "nguc", "ngue", "ngui", "ngum",
    "ngun", "ngung", "nguoc", "nguoi", "nguom", "nguon", "nguop", "nguot", "ngup", "ngut", "nguy", "nguyen",
    "nguyet", "nha", "nhac", "nhai", "nham", "nhan", "nhang", "nhao", "nhap", "nhat", "nhau", "nhay", "nhe",
    "nhem", "nhen", "nheo", "nhep", "nhet", "nhi", "nhia", "nhiec", "nhiem", "nhien", "nhiep", "nhiet",
    "nhieu", "nhim", "nhin", "nhinh", "nhip", "nhit", "nhiu", "nho", "nhoa", "nhoac", "nhoai", "nhoam",
    "nhoan", "nhoang", "nhoap", "nhoat", "nhoc", "nhoi", "nhom", "nhon", "nhong", "nhop", "nhot", "nhu",
    "nhua", "nhuc", "nhue", "nhui", "nhum", "nhun", "nhung", "nhuoc", "nhuoi", "nhuom", "nhuon", "nhuop",
    "nhuot", "nhup", "nhut", "nhuy", "nhuyen", "nhuyet", "ni", "nia", "niec", "niem", "nien", "niep", "niet",
    "nieu", "nim", "nin", "ninh", "nip", "nit", "niu", "no", "noa", "noac", "noan", "noang", "noc", "noi",
    "nom", "non", "nong", "nop", "not", "nu", "nua", "nuc", "nui", "num", "nun", "nung", "nuoc", "nuoi",
    "nuom", "nuon", "nuop", "nuot", "nup", "nut", "o", "oc", "oi", "om", "on", "ong", "op", "ot", "pa", "pe",
    "pi", "po", "pu", "qua", "quac", "quai", "quam", "quan", "quang", "quao", "quap", "quat", "quau", "quay",
    "que", "quem", "quen", "queo", "quep", "quet", "qui", "quim", "quin", "quinh", "quip", "quit", "quiu",
    "quo", "quoc", "quon", "quong", "quot", "quu", "quy", "quyen", "quyet", "quynh", "ra", "rac", "rai",
    "ram", "ran", "rang", "rao", "rap", "rat", "rau", "ray", "re", "rem", "ren", "reo", "rep", "ret", "ri",
    "ria", "riec", "riem", "rien", "riep", "riet", "rieu", "rim", "rin", "rinh", "rip", "rit", "riu", "ro",
    "roa", "roac", "roai", "roam", "roan", "roang", "roap", "roat", "roc", "roi", "rom", "ron", "rong",
    "rop", "rot", "ru", "rua", "ruc", "rui", "rum", "run", "rung", "ruoc", "ruoi", "ruom", "ruon", "ruop",
    "ruot", "rup", "rut", "sa", "sac", "sai", "sam", "san", "sang", "sao", "sap", "sat", "sau", "say", "se",
    "sem", "sen", "seo", "sep", "set", "si", "sia", "siec", "siem", "sien", "siep", "siet", "sieu", "sim",
    "sin", "sinh", "sip", "sit", "siu", "so", "soa", "soac", "soai", "soam", "soan", "soang", "soap", "soat",
    "soc", "soi", "som", "son", "song", "sop", "sot", "su", "sua", "suc", "sue", "sui", "sum", "sun", "sung",
    "suoc", "suoi", "suom", "suon", "suop", "suot", "sup", "sut", "suyen", "suyet", "suy", "ta", "tac", "tai",
    "tam", "tan", "tang", "tao", "tap", "tat", "tau", "tay", "te", "tem", "ten", "teo", "tep", "tet", "tha",
    "thac", "thai", "tham", "than", "thang", "thao", "thap", "that", "thau", "thay", "the", "them", "then",
    "theo", "thep", "thet", "thi", "thia", "thiec", "thiem", "thien", "thiep", "thiet", "thieu", "thim",
    "thin", "thinh", "thip", "thit", "thiu", "tho", "thoa", "thoac", "thoai", "thoam", "thoan", "thoang",
    "thoap", "thoat", "thoc", "thoi", "thom", "thon", "thong", "thop", "thot", "thu", "thua", "thuc", "thue",
    "thui", "thum", "thun", "thung", "thuoc", "thuoi", "thuom", "thuon", "thuop", "thuot", "thup", "thut",
    "thuy", "thuyen", "thuyet", "ti", "tia", "tiec", "tiem", "tien", "tiep", "tiet", "tieu", "tim", "tin",
    "tinh", "tip", "tit", "tiu", "to", "toa", "toac", "toai", "toam", "toan", "toang", "toap", "toat", "toc",
    "toi", "tom", "ton", "tong", "top", "tot", "tra", "trac", "trai", "tram", "tran", "trang", "trao", "trap",
    "trat", "trau", "tray", "tre", "trem", "tren", "treo", "trep", "tret", "tri", "tria", "triec", "triem",
    "trien", "triep", "triet", "trieu", "trim", "trin", "trinh", "trip", "trit", "triu", "tro", "troa",
    "troac", "troai", "troam", "troan", "troang", "troap", "troat", "troc", "troi", "trom", "tron", "trong",
    "trop", "trot", "tru", "trua", "truc", "true", "trui", "trum", "trun", "trung", "truoc", "truoi",
    "truom", "truon", "truop", "truot", "trup", "trut", "truy", "truyen", "truyet", "tu", "tua", "tuc",
    "tue", "tui", "tum", "tun", "tung", "tuoc", "tuoi", "tuom", "tuon", "tuop", "tuot", "tup", "tut", "tuyen",
    "tuyet", "tuy", "va", "vac", "vai", "vam", "van", "vang", "vao", "vap", "vat", "vau", "vay", "ve", "vem",
    "ven", "veo", "vep", "vet", "vi", "via", "viec", "viem", "vien", "viep", "viet", "vieu", "vim", "vin",
    "vinh", "vip", "vit", "viu", "vo", "voa", "voac", "voai", "voam", "voan", "voang", "voap", "voat", "voc",
    "voi", "vom", "von", "vong", "vop", "vot", "vu", "vua", "vuc", "vue", "vui", "vum", "vun", "vung",
    "vuoc", "vuoi", "vuom", "vuon", "vuop", "vuot", "vup", "vut", "vuyen", "vuyet", "vuy", "xa", "xac",
    "xai", "xam", "xan", "xang", "xao", "xap", "xat", "xau", "xay", "xe", "xem", "xen", "xeo", "xep",
    "xet", "xi", "xia", "xiec", "xiem", "xien", "xiep", "xiet", "xieu", "xim", "xin", "xinh", "xip", "xit",
    "xiu", "xo", "xoa", "xoac", "xoai", "xoam", "xoan", "xoang", "xoap", "xoat", "xoc", "xoi", "xom", "xon",
    "xong", "xop", "xot", "xu", "xua", "xuc", "xue", "xui", "xum", "xun", "xung", "xuoc", "xuoi", "xuom",
    "xuon", "xuop", "xuot", "xup", "xut", "xuyen", "xuyet", "xuy", "ya", "ye", "yen", "yet", "yeu"
}

TARGET_LANGUAGES = ["zho_Hans", "zho_Hant", "vie_Latn", "eng_Latn", "jpn_Jpan", "kor_Hang"]
SPECIAL_CONTROL_TOKENS = ["<s>", "<pad>", "</s>", "<unk>", "<mask"]


def is_cjk(ch: str) -> bool:
    code = ord(ch)
    return (0x4E00 <= code <= 0x9FFF) or (0x3400 <= code <= 0x4DBF)


def is_kana(ch: str) -> bool:
    code = ord(ch)
    return (0x3040 <= code <= 0x30FF) or (0x31F0 <= code <= 0x31FF)


def is_hangul(ch: str) -> bool:
    code = ord(ch)
    return (0xAC00 <= code <= 0xD7AF) or (0x1100 <= code <= 0x11FF) or (0x3130 <= code <= 0x318F)


def is_digit_or_punct(ch: str) -> bool:
    return ch in (
        "0123456789 .,!?:;-\"'()[]{}/@#$%&*+=_~<>，。！？“”‘’；：【】（）《》·…—、\\|`^"
        "、。「」々ー〜·・"
    )


def build_pruned_vocabulary(
    base_tokenizer: AutoTokenizer,
    max_ascii_latin: int = 25000,
) -> set:
    print("Analyzing and selecting tokens for target languages...")
    special_set = set(SPECIAL_CONTROL_TOKENS) | set(TARGET_LANGUAGES)

    vi_accented = set()
    cjk_tokens = set()
    kana_tokens = set()
    hangul_tokens = set()
    digits_punct = set()
    vi_base_syllables_set = set()
    ascii_candidates = []

    for tok, idx in base_tokenizer.get_vocab().items():
        if tok in special_set:
            continue
        clean = tok.replace("\u2581", "")
        if not clean:
            digits_punct.add(tok)
            continue

        # 1. Vietnamese accented tokens
        if any(c in TRUE_VI_DIACRITICS for c in clean):
            vi_accented.add(tok)
        # 2. CJK Unified Ideographs (Chinese Hanzi & Japanese Kanji)
        elif any(is_cjk(c) for c in clean):
            cjk_tokens.add(tok)
        # 3. Japanese Kana
        elif any(is_kana(c) for c in clean):
            kana_tokens.add(tok)
        # 4. Korean Hangul
        elif any(is_hangul(c) for c in clean):
            hangul_tokens.add(tok)
        # 5. Digits & Punctuation (including Japanese & Chinese iteration marks 々)
        elif all(is_digit_or_punct(c) for c in clean):
            digits_punct.add(tok)
        # 6. Check unaccented Vietnamese syllable match
        elif clean.lower() in VI_BASE_SYLLABLES:
            vi_base_syllables_set.add(tok)
        # 7. Pure ASCII Latin tokens (for English & subwords)
        elif all(c.isascii() for c in clean):
            ascii_candidates.append((tok, idx))

    # Sort ascii candidates by original token index (frequency in SentencePiece)
    ascii_candidates.sort(key=lambda x: x[1])
    selected_ascii = {tok for tok, _ in ascii_candidates[:max_ascii_latin]}

    # Ensure all single ASCII characters exist
    for ch in "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ":
        if ch in base_tokenizer.get_vocab():
            selected_ascii.add(ch)
        if f"\u2581{ch}" in base_tokenizer.get_vocab():
            selected_ascii.add(f"\u2581{ch}")

    final_vocab = (
        special_set
        | vi_accented
        | cjk_tokens
        | kana_tokens
        | hangul_tokens
        | digits_punct
        | vi_base_syllables_set
        | selected_ascii
    )

    print(f"Token Breakdown:")
    print(f"  - Special & Languages : {len(special_set)}")
    print(f"  - Vietnamese accented : {len(vi_accented)}")
    print(f"  - Vietnamese base syl : {len(vi_base_syllables_set)}")
    print(f"  - CJK Hanzi / Kanji   : {len(cjk_tokens)}")
    print(f"  - Japanese Kana       : {len(kana_tokens)}")
    print(f"  - Korean Hangul       : {len(hangul_tokens)}")
    print(f"  - Digits & Punctuation: {len(digits_punct)}")
    print(f"  - Selected ASCII Latin: {len(selected_ascii)}")
    print(f"  =====================================")
    print(f"  Total Pruned Vocab    : {len(final_vocab)} (from {len(base_tokenizer.get_vocab())})")
    print(f"  Reduction Ratio       : {(1 - len(final_vocab) / len(base_tokenizer.get_vocab())) * 100:.2f}%\n")
    return final_vocab


def prune_sentencepiece_proto(
    base_spm_path: str,
    output_spm_path: str,
    kept_vocab_set: set,
) -> None:
    print(f"Reading base SentencePiece model from {base_spm_path}...")
    m = model_pb2.ModelProto()
    with open(base_spm_path, "rb") as f:
        m.ParseFromString(f.read())

    print(f"Original SPM piece count: {len(m.pieces)}")

    # Retain only pieces present in kept_vocab_set (or piece types UNKNOWN / CONTROL)
    new_pieces = []
    for p in m.pieces:
        if p.type in (2, 3):  # <unk>, <s>, </s>
            new_pieces.append(p)
        elif p.piece in kept_vocab_set:
            new_pieces.append(p)

    del m.pieces[:]
    m.pieces.extend(new_pieces)
    print(f"New pruned SPM piece count: {len(m.pieces)}")

    os.makedirs(os.path.dirname(output_spm_path), exist_ok=True)
    with open(output_spm_path, "wb") as f:
        f.write(m.SerializeToString())
    print(f"Saved pruned SentencePiece model to {output_spm_path}\n")


def prune_pytorch_model_and_tokenizer(
    base_model_id: str,
    output_dir: str,
    max_ascii_latin: int = 25000,
) -> None:
    start_time = time.perf_counter()
    os.makedirs(output_dir, exist_ok=True)

    print("=" * 80)
    print(f"PRUNING NLLB-200 VOCABULARY: {base_model_id} -> {output_dir}")
    print("=" * 80)

    # 1. Load base tokenizer
    print(f"Loading base tokenizer from {base_model_id}...")
    base_tok = NllbTokenizer.from_pretrained(base_model_id)
    base_spm_path = getattr(base_tok, "vocab_file", None) or base_tok.init_kwargs.get("vocab_file")
    if not base_spm_path or not os.path.exists(base_spm_path):
        raise FileNotFoundError(f"Could not locate sentencepiece.bpe.model for {base_model_id}")

    # 2. Build kept vocabulary set
    kept_vocab_set = build_pruned_vocabulary(base_tok, max_ascii_latin=max_ascii_latin)

    # 3. Create pruned SentencePiece file
    pruned_spm_path = os.path.join(output_dir, "sentencepiece.bpe.model")
    prune_sentencepiece_proto(base_spm_path, pruned_spm_path, kept_vocab_set)

    # 4. Instantiate pruned tokenizer
    print("Instantiating pruned NllbTokenizer...")
    pruned_tok = NllbTokenizer(
        vocab_file=pruned_spm_path,
        additional_special_tokens=TARGET_LANGUAGES,
        src_lang="zho_Hans",
        tgt_lang="vie_Latn",
    )
    new_vocab_size = len(pruned_tok)
    print(f"New tokenizer vocabulary size: {new_vocab_size}")

    # 5. Load base PyTorch model
    print(f"\nLoading base PyTorch model from {base_model_id} (CPU)...")
    base_model = AutoModelForSeq2SeqLM.from_pretrained(
        base_model_id,
        torch_dtype=torch.float16,
        low_cpu_mem_usage=True,
    )

    old_params = sum(p.numel() for p in base_model.parameters())
    print(f"Base model total parameters: {old_params / 1e6:.2f}M")

    # 6. Remap Embeddings & lm_head
    print("Constructing pruned embedding tensor and remapping weights...")
    old_weight = base_model.model.shared.weight.data
    new_weight = torch.zeros((new_vocab_size, 1024), dtype=old_weight.dtype)

    base_vocab = base_tok.get_vocab()
    remapped_count = 0
    for tok_str, new_id in pruned_tok.get_vocab().items():
        if tok_str in base_vocab:
            old_id = base_tok.convert_tokens_to_ids(tok_str)
            new_weight[new_id] = old_weight[old_id]
            remapped_count += 1

    print(f"Successfully remapped {remapped_count} / {new_vocab_size} tokens ({remapped_count/new_vocab_size*100:.2f}%)")

    new_embed = torch.nn.Embedding(new_vocab_size, 1024, padding_idx=1)
    new_embed.weight.data = new_weight

    base_model.model.shared = new_embed
    base_model.model.encoder.embed_tokens = new_embed
    base_model.model.decoder.embed_tokens = new_embed

    new_lm_head = torch.nn.Linear(1024, new_vocab_size, bias=False)
    new_lm_head.weight = new_embed.weight
    base_model.lm_head = new_lm_head

    base_model.config.vocab_size = new_vocab_size

    new_params = sum(p.numel() for p in base_model.parameters())
    print(f"Pruned model total parameters: {new_params / 1e6:.2f}M")
    print(f"Parameter reduction: {(old_params - new_params) / 1e6:.2f}M ({(1 - new_params / old_params) * 100:.2f}%)\n")

    # 7. Save pruned tokenizer and model
    print(f"Saving pruned model and tokenizer to {output_dir}...")
    pruned_tok.save_pretrained(output_dir)
    base_model.save_pretrained(output_dir, max_shard_size="10GB")

    # 8. Sanity check test
    print("\nRunning verification forward pass on pruned model...")
    test_input = "白小纯站在悬崖边缘，迎着呼啸而来的狂风。"
    inputs = pruned_tok(test_input, return_tensors="pt")
    with torch.no_grad():
        out = base_model(
            **inputs,
            decoder_input_ids=torch.tensor([[2, pruned_tok.convert_tokens_to_ids("vie_Latn")]]),
        )
    print(f"Input text: '{test_input}'")
    print(f"Token IDs : {inputs['input_ids'].tolist()}")
    print(f"Logits shape: {out.logits.shape} (Expected: [1, 2, {new_vocab_size}])")

    elapsed = time.perf_counter() - start_time
    print(f"\n🎉 Vocabulary Pruning completed successfully in {elapsed:.2f}s!")
    print(f"Pruned model location: {os.path.abspath(output_dir)}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Prune NLLB-200 vocabulary for ZH/VI/EN/JA/KO.")
    parser.add_argument(
        "--model-id",
        type=str,
        default="facebook/nllb-200-distilled-600M",
        help="Base HuggingFace model repo or path",
    )
    parser.add_argument(
        "--output-dir",
        type=str,
        default=os.path.join(os.path.dirname(__file__), "nllb_pruned_370m"),
        help="Directory to save the pruned model and tokenizer",
    )
    parser.add_argument(
        "--max-ascii",
        type=int,
        default=25000,
        help="Maximum pure ASCII Latin tokens to retain for English/unaccented Vietnamese",
    )
    args = parser.parse_args()

    # Route HF cache to Drive D
    os.environ["HF_HOME"] = os.path.abspath(os.path.join(os.path.dirname(__file__), ".hf_cache"))

    prune_pytorch_model_and_tokenizer(
        base_model_id=args.model_id,
        output_dir=args.output_dir,
        max_ascii_latin=args.max_ascii,
    )
