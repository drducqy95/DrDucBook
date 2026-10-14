import sys
import torch
from transformers import AutoModelForSeq2SeqLM, NllbTokenizerFast

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")

model_dir = "tools/nmt-onnx/user_trained/content/nllb_pruned_novel_finetuned"
base_dir = "tools/nmt-onnx/nllb_pruned_370m"

print("Loading model and tokenizer...")
tokenizer = NllbTokenizerFast.from_pretrained(base_dir, local_files_only=True)
model = AutoModelForSeq2SeqLM.from_pretrained(model_dir, local_files_only=True)
model.eval()

tokenizer.src_lang = "zho_Hans"
tgt_id = tokenizer.convert_tokens_to_ids("vie_Latn")

test_sentences = [
    ("他深吸一口气，开始运转长生诀，周身灵气疯狂涌入丹田。", "Hắn hít sâu một hơi, bắt đầu vận chuyển Trường Sinh Quyết, linh khí toàn thân điên cuồng tràn vào đan điền."),
    ("白小纯眨了眨眼，干咳一声，赶紧溜之大吉。", "Bạch Tiểu Thuần chớp chớp mắt, ho khan một tiếng, vội vàng chuồn mất dạng."),
    ("“好你个逆子，竟敢私自盗取宗门至宝筑基丹！”", "“Hay cho một tên nghịch tử nhà ngươi, dám tự ý trộm cắp chí bảo tông môn Trúc Cơ Đan!”"),
    ("衣袍猎猎作响，他拔出长剑，冷冷地看着前方的敌人。", "Áo bào tung bay phần phật, hắn rút thanh trường kiếm ra, lạnh lùng nhìn chằm chằm vào kẻ thù phía trước.")
]

print("=" * 80)
print("TESTING USER DOWNLOADED MODEL TRANSLATIONS:")
print("=" * 80)
for zh, expected in test_sentences:
    inp = tokenizer(zh, return_tensors="pt")
    with torch.no_grad():
        out = model.generate(**inp, forced_bos_token_id=tgt_id, max_new_tokens=100)
    pred_vi = tokenizer.decode(out[0], skip_special_tokens=True)
    print(f"ZH  : {zh}")
    print(f"PRED: {pred_vi}")
    print(f"EXP : {expected}")
    print("-" * 80)
