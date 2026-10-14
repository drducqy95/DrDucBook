import os
import re
import sys

sys.stdout.reconfigure(encoding='utf-8')

output_dir = r"app\test_chapters_1_to_10_output"

print("====================================================================")
print("              DEEP QUALITY AUDIT ACROSS 10 CHAPTERS                 ")
print("====================================================================")

for ch in range(1, 11):
    file_path = os.path.join(output_dir, f"chapter_{ch:02d}_beam3_terms.txt")
    if not os.path.exists(file_path):
        print(f"Chapter {ch:02d}: Not found!")
        continue
    
    with open(file_path, "r", encoding="utf-8") as f:
        text = f.read()
        lines = [line.strip() for line in text.splitlines() if line.strip()]

    # 1. Check for excessive or awkward 'của'
    cua_matches = re.findall(r'(?iu)\bcủa\s+([a-zà-ỹA-ZÀ-Ỹ]+(?:\s+[a-zà-ỹA-ZÀ-Ỹ]+)?)', text)
    cua_awkward = [m for m in cua_matches if any(w in m.lower() for w in [
        'muốn', 'say rượu', 'phát hành', 'sống sót', 'bất hạnh', 'bất công', 'ngột ngạt',
        'đáng ngờ', 'sụp đổ', 'phí sức', 'nổi bật', 'táo bạo', 'yêu thích', 'hi vọng', 'kỳ vọng',
        'chỉ', 'này', 'đó', 'kia', 'trong tay', 'bây giờ', 'rất', 'không'
    ])]

    # 2. Check for double/awkward particles like 'của của', 'là là', 'trong trong', 'ở ở'
    dup_particles = re.findall(r'(?iu)\b(của\s+của|trong\s+trong|ở\s+ở|là\s+là|mà\s+mà|được\s+được|với\s+với)\b', text)

    # 3. Check for awkward inverted possessive/modifier patterns: 'X của Y' where Y is adverb or demonstrative
    awkward_inversions = re.findall(r'(?iu)\b[a-zà-ỹA-ZÀ-Ỹ]+\s+của\s+(?:trong tay|trước đó|bây giờ|này|đó|kia|chỉ|rất|không)\b', text)

    # 4. Check for system brackets 【...】
    system_prompts = [line for line in lines if line.startswith("【") or line.startswith("“【")]

    # 5. Check untranslated / foreign artifacts
    foreign_artifacts = re.findall(r'(?iu)\b[a-zA-Z]{1,3}\b(?!\s*[:\-\/\d])', text)
    # filter out known legitimate roman letters like 'S hình', 'HP', 'MP', 'Lv', 'EXP', 'N P'
    weird_caps = [w for w in foreign_artifacts if w.upper() in ['NP', 'VP', 'PP', 'AP', 'DP', 'CP'] or (w.isupper() and len(w) == 1 and w not in ['S', 'A', 'B', 'C', 'D', 'E', 'F'])]

    # 6. Spacing around punctuation
    spacing_issues = re.findall(r'[a-zà-ỹA-ZÀ-Ỹ]+[，。？！；：][a-zà-ỹA-ZÀ-Ỹ]+', text)

    print(f"\n--- CHƯƠNG {ch:02d} ({len(lines)} đoạn, {len(text)} ký tự) ---")
    print(f"  System/Game Prompts: {len(system_prompts)} prompts")
    print(f"  Awkward 'của' clauses: {len(cua_awkward)} -> {cua_awkward[:6]}")
    if dup_particles:
        print(f"  Duplicate particles: {dup_particles[:4]}")
    if awkward_inversions:
        print(f"  Awkward inversions: {awkward_inversions[:6]}")
    if weird_caps:
        print(f"  Weird POS tags / single caps: {weird_caps[:4]}")
    if spacing_issues:
        print(f"  Spacing issues around CJK punctuation: {len(spacing_issues)} -> {spacing_issues[:4]}")
