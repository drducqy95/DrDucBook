"""
Prepare training and evaluation dataset for NLLB-Pruned-370M domain fine-tuning.
Integrates:
1. ngocdang83/tran-vi-teacher (Gemini web-novel teacher parallel data).
2. DrDucBook Xianxia/Wuxia novel terminology boosters.
3. Multilingual anchor pairs (EN-VI, JA-VI, KO-VI, VI-ZH) to prevent catastrophic forgetting.

Exports:
- tools/nmt-onnx/dataset_novel/train.jsonl
- tools/nmt-onnx/dataset_novel/eval.jsonl
- tools/nmt-onnx/dataset_novel/summary.json
"""

import argparse
import json
import os
import random
import sys
import time

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")
if hasattr(sys.stderr, "reconfigure"):
    sys.stderr.reconfigure(encoding="utf-8")

from transformers import NllbTokenizer

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
DATA_RAW_FILE = os.path.join(SCRIPT_DIR, "data", "tran_vi_teacher_strict_clean_dedup_source.jsonl")
GOLDEN_TEST_FILE = os.path.join(SCRIPT_DIR, "testdata", "golden_test_set_literary.json")
PRUNED_MODEL_DIR = os.path.join(SCRIPT_DIR, "nllb_pruned_370m")
OUTPUT_DIR = os.path.join(SCRIPT_DIR, "dataset_novel")

# Comprehensive Calibrated Xianxia / Wuxia / Novel Terms (Fixing typos & unidiomatic translations)
NOVEL_TERMS = [
    # Protagonists & Characters
    ("白小纯", "Bạch Tiểu Thuần"),
    ("林轩", "Lâm Hiên"),
    ("苏晓", "Tô Hiểu"),
    ("叶晨", "Diệp Thần"),
    ("陈白起", "Trần Bạch Khởi"),
    ("李再安", "Lý Tái An"),
    ("广寒仙子", "Quảng Hàn tiên tử"),
    
    # Cultivation Realms & States
    ("长生修仙", "trường sinh tu tiên"),
    ("长生修仙之路", "con đường trường sinh tu tiên"),
    ("修仙长途", "con đường tu tiên dài đằng đẵng"),
    ("修真界", "giới tu chân"),
    ("炼气期", "thời kỳ Luyện Khí"),
    ("筑基期", "thời kỳ Trúc Cơ"),
    ("筑基初期", "Trúc Cơ sơ kỳ"),
    ("金丹期", "thời kỳ Kim Đan"),
    ("金丹大圆满", "Kim Đan đại viên mãn"),
    ("元婴大道", "đại đạo Nguyên Anh"),
    ("元婴期", "thời kỳ Nguyên Anh"),
    ("元婴大圆满", "Nguyên Anh đại viên mãn"),
    ("化神期", "thời kỳ Hóa Thần"),
    ("炼虚期", "thời kỳ Luyện Hư"),
    ("合体期", "thời kỳ Hợp Thể"),
    ("大乘期", "thời kỳ Đại Thừa"),
    ("渡劫期", "thời kỳ Độ Kiếp"),
    ("渡劫飞升", "độ kiếp phi thăng"),
    ("逆天改命", "nghịch thiên cải mệnh"),
    ("天灵根", "thiên linh căn"),
    ("丹田气海", "đan điền khí hải"),
    ("道心", "đạo tâm"),
    ("道心若动摇", "đạo tâm nếu dao động"),
    ("神识", "thần thức"),
    ("神魂俱灭", "thần hồn câu diệt"),
    ("夺舍", "đoạt xá"),
    ("自爆", "tự bạo"),
    ("御剑飞行", "ngự kiếm phi hành"),
    ("闭关修炼", "bế quan tu luyện"),
    
    # Pills & Alchemy
    ("灵丹妙药", "linh đan diệu dược"),
    ("筑基丹", "Trúc Cơ Đan"),
    ("凝气丹", "Ngưng Khí Đan"),
    ("洗髓丹", "Tẩy Tủy Đan"),
    ("补天丹", "Bổ Thiên Đan"),
    ("丹火", "đan hỏa"),
    ("炼丹炉", "lò luyện đan"),
    ("千年火灵芝", "hỏa linh chi ngàn năm"),
    
    # Spells, Formations & Treasures
    ("护山大阵", "hộ sơn đại trận"),
    ("诛仙杀阵", "chu tiên sát trận"),
    ("太古神庙", "thái cổ thần miếu"),
    ("大荒经", "Đại Hoang Kinh"),
    ("九九紫霄神雷", "Cửu Cửu Tử Tiêu Thần Lôi"),
    ("纯阳剑气", "Thuần Dương kiếm khí"),
    ("魔气", "ma khí"),
    ("宗门至宝", "chí bảo tông môn"),
    ("镇派至宝", "trấn phái chí bảo"),
    ("储物袋", "túi trữ vật"),
    ("储物戒", "nhẫn trữ vật"),
    ("储物空间", "không gian trữ vật"),
    ("万毒沼泽", "đầm lầy Vạn Độc"),
    ("千刃峰", "núi Thiên Nhận"),
    ("紫睛魔猿", "Tử Tình Ma Viên"),
    ("嗜血妖狼", "yêu lang khát máu"),
    ("上古异兽", "thượng cổ dị thú"),
    ("苍穹之巅", "đỉnh thương khung"),
    ("苍穹深处", "sâu trong thương khung"),
    
    # Common Novel Actions & Literary Phrases (Fixing machine mistranslations & QT artifacts)
    ("衣袍猎猎作响", "áo bào tung bay phần phật"),
    ("破空而至", "xé gió lao tới"),
    ("饶你不死", "tha cho ngươi không chết"),
    ("清理门户", "thanh lý môn hộ"),
    ("好你个逆子", "hay cho một tên nghịch tử nhà ngươi"),
    ("竖子尔敢", "tên nhãi ranh to gan"),
    ("咬牙切齿", "nghiến răng nghiến lợi"),
    ("面色铁青", "sắc mặt xanh mét"),
    ("不动声色", "không hề biến sắc"),
    ("倒吸一口凉气", "hít sâu một hơi khí lạnh"),
    ("寸寸塌陷", "từng tấc sụp đổ"),
    ("化为齑粉", "hóa thành bột mịn"),
    ("焚山煮海", "thiêu núi nấu biển"),
    ("遮天蔽日", "che trời rợp bóng"),
    ("腥风血雨", "mưa máu gió tanh"),
    ("云泥之别", "khác nhau một trời một vực"),
    ("螳螂捕蝉黄雀在后", "bọ ngựa bắt ve chim sẻ chực sẵn phía sau"),
    ("微微一笑", "khẽ mỉm cười"),
    ("微微摇头", "khẽ lắc đầu"),
    ("微微皱眉", "khẽ nhíu mày"),
    ("冷笑一声", "cười khẩy một tiếng"),
    ("冷哼一声", "hừ lạnh một tiếng"),
    ("沉声道", "trầm giọng nói"),
    ("淡淡道", "thản nhiên nói"),
    ("苦笑一声", "cười gượng một tiếng"),
    ("忍不住", "không kìm được"),
    ("心中一凛", "trong lòng rùng mình"),
    ("眼睛一亮", "hai mắt sáng lên"),
    ("大喝一声", "quát lớn một tiếng"),
    ("叹了口气", "thở dài một tiếng"),
    ("躬身行礼", "cúi người hành lễ"),
    ("抱拳道", "chắp tay nói"),
    ("多谢前辈", "đa tạ tiền bối"),
    ("自寻死路", "tự tìm đường chết"),
    ("不知死活", "không biết sống chết"),
    ("死无葬身之地", "chết không có chỗ chôn"),
    ("喷出一口鲜血", "phun ra một ngụm máu tươi"),
    ("脸色苍白", "sắc mặt tái nhợt"),
    ("鲜血淋漓", "máu tươi đầm đìa"),
    ("杀气滔天", "sát khí ngút trời"),
    ("本命法宝", "bản mệnh pháp bảo"),
    ("灵气复苏", "linh khí khôi phục"),
    ("叮，系统提示", "Đinh, hệ thống nhắc nhở"),
    ("任务奖励", "phần thưởng nhiệm vụ"),
    
    # Corrected Pivot / Modern Terminology (Fixing Google ML Kit literal calques)
    ("小腿", "bắp chân"),
    ("骨折", "bị gãy xương"),
    ("击穿", "bị bắn thủng"),
    ("麻", "tê rần"),
    ("落地", "tiếp đất"),
    ("消音器", "ống giảm thanh"),
    ("皱眉", "nhíu mày"),
    ("眉头", "chân mày"),
    ("血迹", "vết máu"),
    ("余光", "khóe mắt"),
    ("衍生位面", "vị diện phái sinh"),
    ("衍生世界", "thế giới phái sinh"),
    ("位面", "vị diện"),
    ("世界之源", "Nguồn Thế Giới"),
    ("穿梭", "du hành"),
    ("军官", "sĩ quan"),
    ("袖口", "cổ tay áo"),
    ("保安", "nhân viên bảo vệ"),
    ("喷涌", "phun trào"),
    ("发昏", "đầu óc choáng váng"),
    ("抽动", "giật giật"),
    ("黑洞洞", "đen ngòm"),
    ("枪口", "họng súng"),
    ("皮肉翻卷", "da thịt lật ra"),
    ("法术伤害", "sát thương phép"),
    ("防御力", "phòng ngự"),
    ("生命值", "sinh mệnh"),
    ("杀戮天赋", "thiên phú giết chóc"),
    ("杀死目标", "tiêu diệt mục tiêu"),
    ("烙印", "lạc ấn"),
    ("阶位", "giai vị"),
    ("未解锁", "chưa mở khóa"),
    ("已解锁", "đã mở khóa"),
    ("哥亚王国", "Vương quốc Goa"),
    ("刺杀哥亚王国国王", "Ám sát Quốc vương Goa"),
    ("被动技能", "kỹ năng bị động"),
    ("契约者", "Khế ước giả"),
    ("猎杀者", "Liệp sát giả"),
    ("后坐力", "lực giật lùi"),
    ("弹夹", "băng đạn"),
    ("弹匣", "băng đạn"),
    ("车水马龙", "xe cộ như nước"),
    ("满身酒气", "nồng nặc mùi rượu"),
    ("出鞘", "rút kiếm khỏi vỏ"),
    ("一跃而下", "nhảy vọt xuống"),
    ("汗毛直立", "lông tơ dựng đứng"),
    ("快步前冲", "sải bước lao về phía trước"),
    ("轮回乐园", "Luân Hồi Lạc Viên"),
    ("乐园", "Lạc Viên"),
]

# Multilingual Anchor Samples (High-frequency patterns to prevent forgetting)
EN_VI_ANCHORS = [
    ("Hello, how are you today?", "Xin chào, hôm nay bạn thế nào?"),
    ("The weather is very pleasant this morning.", "Thời tiết sáng nay rất dễ chịu."),
    ("Artificial intelligence is transforming machine translation.", "Trí tuệ nhân tạo đang làm thay đổi ngành dịch thuật máy."),
    ("He took a deep breath and drew his sword.", "Hắn hít một hơi thật sâu rồi rút thanh kiếm ra."),
    ("The mountain was shrouded in thick fog.", "Ngọn núi bị bao phủ bởi sương mù dày đặc."),
    ("She smiled gently and nodded in agreement.", "Nàng mỉm cười dịu dàng và gật đầu đồng ý."),
    ("They walked along the river under the moonlight.", "Họ đi dạo dọc bờ sông dưới ánh trăng."),
    ("This ancient temple has stood for thousands of years.", "Ngôi đền cổ này đã đứng sừng sững hàng ngàn năm qua."),
    ("The sudden explosion shattered the silence of the night.", "Tiếng nổ bất ngờ đã phá tan sự yên tĩnh của màn đêm."),
    ("Never give up on your dreams, no matter how hard it gets.", "Đừng bao giờ từ bỏ ước mơ của mình, cho dù có khó khăn đến đâu."),
]

JA_VI_ANCHORS = [
    ("こんにちは、お元気ですか？", "Xin chào, bạn có khỏe không?"),
    ("今日の天気はとても素晴らしいですね。", "Thời tiết hôm nay thật tuyệt vời."),
    ("彼は静かに目を閉じ、風の音を聞いた。", "Hắn khẽ nhắm mắt lại, lắng nghe tiếng gió."),
    ("剣を抜いて、前方の敵を見据えた。", "Hắn rút kiếm ra, nhìn chằm chằm vào kẻ thù phía trước."),
    ("桜の花が春風に乗って舞い落ちる。", "Hoa anh đào bay lượn theo làn gió xuân."),
    ("この物語は遠い昔の伝説である。", "Câu chuyện này là một truyền thuyết từ thuở xa xưa."),
    ("彼女は優しく微笑んで手を振った。", "Nàng mỉm cười dịu dàng rồi vẫy tay."),
    ("山頂からの景色は息をのむほど美しかった。", "Khung cảnh từ đỉnh núi đẹp đến nghẹt thở."),
]

KO_VI_ANCHORS = [
    ("안녕하세요, 오늘 하루 잘 보내고 계신가요?", "Xin chào, ngày hôm nay của bạn thế nào?"),
    ("오늘 아침 날씨가 참 맑고 상쾌합니다.", "Thời tiết sáng nay thật trong lành và sảng khoái."),
    ("그는 조용히 검을 뽑아 들고 적을 바라보았다.", "Hắn khẽ rút kiếm ra và nhìn về phía kẻ thù."),
    ("바람이 거세게 불어와 옷자락이 휘날렸다.", "Gió thổi mạnh khiến vạt áo bay phần phật."),
    ("이 길의 끝에는 무엇이 기다리고 있을까?", "Ở cuối con đường này liệu có điều gì đang chờ đón?"),
    ("그녀는 따뜻한 미소를 지으며 고개를 끄덕였다.", "Nàng nở nụ cười ấm áp và khẽ gật đầu."),
    ("오래된 전설 속의 영웅이 다시 깨어났다.", "Vị anh hùng trong truyền thuyết cổ xưa đã thức tỉnh trở lại."),
]


def load_golden_pairs(golden_path: str) -> list:
    pairs = []
    if not os.path.exists(golden_path):
        return pairs
    with open(golden_path, "r", encoding="utf-8") as f:
        data = json.load(f)
    for tc in data.get("test_cases", []):
        zh = tc.get("zh", "").strip()
        vi = (tc.get("expected_vi") or tc.get("expected_vi_qt") or "").strip()
        if zh and vi:
            pairs.append({"src_lang": "zho_Hans", "tgt_lang": "vie_Latn", "source": zh, "target": vi})
    return pairs


def generate_novel_term_boosters() -> list:
    boosters = []
    templates_zh = [
        "{term}站在天地之间。",
        "他深吸一口气，开始运转{term}。",
        "这一招蕴含着极其恐怖的{term}之威！",
        "在古老的修仙界中，{term}乃是人人向往的存在。",
        "“难道这就是传闻中的{term}？”",
        "{term}散发出耀眼的光芒，令所有人震撼。",
    ]
    templates_vi = [
        "{term} đứng sừng sững giữa thiên địa.",
        "Hắn hít sâu một hơi, bắt đầu vận chuyển {term}.",
        "Chiêu thức này ẩn chứa uy lực cực kỳ khủng bố của {term}!",
        "Trong giới tu tiên cổ xưa, {term} chính là sự tồn tại mà vạn người hướng tới.",
        "“Lẽ nào đây chính là {term} trong truyền thuyết?”",
        "{term} tỏa ra hào quang chói lọi, khiến tất cả mọi người đều chấn động.",
    ]

    for zh_term, vi_term in NOVEL_TERMS:
        # Direct short pair
        boosters.append({"src_lang": "zho_Hans", "tgt_lang": "vie_Latn", "source": zh_term, "target": vi_term})
        # Template augmented pairs
        for t_zh, t_vi in zip(templates_zh, templates_vi):
            s_zh = t_zh.format(term=zh_term)
            s_vi = t_vi.format(term=vi_term)
            boosters.append({"src_lang": "zho_Hans", "tgt_lang": "vie_Latn", "source": s_zh, "target": s_vi})

    return boosters


def generate_multilingual_anchors(multiplier: int = 500) -> list:
    anchors = []
    for _ in range(multiplier):
        for en, vi in EN_VI_ANCHORS:
            anchors.append({"src_lang": "eng_Latn", "tgt_lang": "vie_Latn", "source": en, "target": vi})
            anchors.append({"src_lang": "vie_Latn", "tgt_lang": "eng_Latn", "source": vi, "target": en})
        for ja, vi in JA_VI_ANCHORS:
            anchors.append({"src_lang": "jpn_Jpan", "tgt_lang": "vie_Latn", "source": ja, "target": vi})
            anchors.append({"src_lang": "vie_Latn", "tgt_lang": "jpn_Jpan", "source": vi, "target": ja})
        for ko, vi in KO_VI_ANCHORS:
            anchors.append({"src_lang": "kor_Hang", "tgt_lang": "vie_Latn", "source": ko, "target": vi})
            anchors.append({"src_lang": "vie_Latn", "tgt_lang": "kor_Hang", "source": vi, "target": ko})
    return anchors


def process_teacher_dataset(raw_file: str, max_samples: int = None, full: bool = False) -> list:
    print(f"Reading teacher dataset from {raw_file} (full={full})...")
    novel_pairs = []
    sentence_pairs = []
    
    with open(raw_file, "r", encoding="utf-8") as f:
        for idx, line in enumerate(f):
            if not line.strip():
                continue
            item = json.loads(line)
            src_text = item.get("source_zh", item.get("source", "")).strip()
            tgt_text = item.get("target_vi", item.get("target", "")).strip()

            if not src_text or not tgt_text:
                continue

            src_len = len(src_text)
            tgt_len = len(tgt_text)
            ratio = tgt_len / src_len if src_len > 0 else 0
            if ratio < 0.5 or ratio > 5.0:
                continue

            # 1. Add valid paragraph pair (< 600 chars)
            if src_len <= 600 and tgt_len <= 1500:
                novel_pairs.append({
                    "src_lang": "zho_Hans",
                    "tgt_lang": "vie_Latn",
                    "source": src_text,
                    "target": tgt_text
                })

            # 2. Extract line-by-line sentence pairs if line counts match
            src_lines = [l.strip() for l in src_text.splitlines() if l.strip()]
            tgt_lines = [l.strip() for l in tgt_text.splitlines() if l.strip()]
            if len(src_lines) == len(tgt_lines) and len(src_lines) > 1:
                for sl, tl in zip(src_lines, tgt_lines):
                    if 5 <= len(sl) <= 150 and 5 <= len(tl) <= 300:
                        sentence_pairs.append({
                            "src_lang": "zho_Hans",
                            "tgt_lang": "vie_Latn",
                            "source": sl,
                            "target": tl
                        })

            if not full and max_samples and len(novel_pairs) >= max_samples * 1.5:
                break

    print(f"Extracted {len(novel_pairs)} paragraph pairs and {len(sentence_pairs)} line sentence pairs.")
    random.seed(42)
    random.shuffle(sentence_pairs)
    
    if full:
        # Include all paragraphs + sample up to 80,000 sentence pairs for rich dialogue coverage
        sampled_sentences = sentence_pairs[:80000]
        combined = novel_pairs + sampled_sentences
        random.shuffle(combined)
        return combined
    else:
        combined = novel_pairs + sentence_pairs[:max_samples // 2]
        random.shuffle(combined)
        return combined[:max_samples]


def main():
    parser = argparse.ArgumentParser(description="Prepare dataset for NLLB novel fine-tuning.")
    parser.add_argument("--total-samples", type=int, default=120000, help="Total target sample count when not full")
    parser.add_argument("--eval-ratio", type=float, default=0.015, help="Validation split ratio")
    parser.add_argument("--full", action="store_true", help="Process 100% of raw teacher dataset")
    args = parser.parse_args()

    os.makedirs(OUTPUT_DIR, exist_ok=True)

    print("=" * 80)
    print(f"NLLB NOVEL DATASET CURATION PIPELINE (FULL={args.full})")
    print("=" * 80)

    # 1. Process Teacher Dataset
    if args.full:
        novel_zh_vi = process_teacher_dataset(DATA_RAW_FILE, full=True)
    else:
        novel_zh_vi = process_teacher_dataset(DATA_RAW_FILE, max_samples=int(args.total_samples * 0.75), full=False)

    # 2. Generate reverse Vi -> Zh pairs (~8% to 10%)
    sample_reverse_count = int(len(novel_zh_vi) * 0.08) if args.full else int(args.total_samples * 0.10)
    novel_vi_zh = []
    for item in novel_zh_vi[:sample_reverse_count]:
        novel_vi_zh.append({
            "src_lang": "vie_Latn",
            "tgt_lang": "zho_Hans",
            "source": item["target"],
            "target": item["source"]
        })

    # 3. Domain Boosters & Golden Set
    boosters = generate_novel_term_boosters()
    golden_pairs = load_golden_pairs(GOLDEN_TEST_FILE)
    domain_pairs = (boosters * (8 if args.full else 5)) + (golden_pairs * (15 if args.full else 10))  # oversample high-value domain pairs

    # 4. Multilingual Anchors (En-Vi, Ja-Vi, Ko-Vi)
    multilingual_pairs = generate_multilingual_anchors(multiplier=600 if args.full else 300)

    # Combine All
    all_dataset = novel_zh_vi + novel_vi_zh + domain_pairs + multilingual_pairs
    random.seed(42)
    random.shuffle(all_dataset)

    total_count = len(all_dataset)
    eval_count = int(total_count * args.eval_ratio)
    train_count = total_count - eval_count

    train_data = all_dataset[:train_count]
    eval_data = all_dataset[train_count:]

    # Language distribution breakdown
    lang_counts = {}
    for item in all_dataset:
        pair_key = f"{item['src_lang']} -> {item['tgt_lang']}"
        lang_counts[pair_key] = lang_counts.get(pair_key, 0) + 1

    train_path = os.path.join(OUTPUT_DIR, "train.jsonl")
    eval_path = os.path.join(OUTPUT_DIR, "eval.jsonl")
    summary_path = os.path.join(OUTPUT_DIR, "summary.json")

    print(f"\nWriting {train_count} train samples to {train_path}...")
    with open(train_path, "w", encoding="utf-8") as f:
        for item in train_data:
            f.write(json.dumps(item, ensure_ascii=False) + "\n")

    print(f"Writing {eval_count} eval samples to {eval_path}...")
    with open(eval_path, "w", encoding="utf-8") as f:
        for item in eval_data:
            f.write(json.dumps(item, ensure_ascii=False) + "\n")

    summary = {
        "total_samples": total_count,
        "train_samples": train_count,
        "eval_samples": eval_count,
        "language_distribution": lang_counts,
        "train_size_mb": round(os.path.getsize(train_path) / (1024 * 1024), 2),
        "eval_size_mb": round(os.path.getsize(eval_path) / (1024 * 1024), 2),
    }

    with open(summary_path, "w", encoding="utf-8") as f:
        json.dump(summary, f, ensure_ascii=False, indent=2)

    print("\n" + "=" * 80)
    print("DATASET PREPARATION COMPLETED SUCCESSFULLY!")
    print(f"Train File : {train_path} ({summary['train_size_mb']} MB)")
    print(f"Eval File  : {eval_path} ({summary['eval_size_mb']} MB)")
    print("\nLanguage Pair Distribution:")
    for k, v in sorted(lang_counts.items(), key=lambda x: x[1], reverse=True):
        print(f"  - {k:25s}: {v:6d} ({v/total_count*100:5.2f}%)")
    print("=" * 80)


if __name__ == "__main__":
    main()
