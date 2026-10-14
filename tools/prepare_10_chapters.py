import os
import shutil
import glob
import sys

sys.stdout.reconfigure(encoding='utf-8')

src_dir = r"test_data\luan_hoi_nhac_vien\轮回乐园24389258f3f881c9"
dst_dir = r"app\test_chapters_1_to_10"
os.makedirs(dst_dir, exist_ok=True)

print(f"Scanning {src_dir}...")
for i in range(10):
    prefix = f"{i:05d}"
    candidates = glob.glob(os.path.join(src_dir, f"{prefix}*.nb"))
    raw_files = [f for f in candidates if ".vi." not in f]
    if raw_files:
        src_file = raw_files[0]
        dst_file = os.path.join(dst_dir, f"chapter_{i+1:02d}.txt")
        shutil.copyfile(src_file, dst_file)
        with open(dst_file, "r", encoding="utf-8") as f:
            lines = [line.strip() for line in f if line.strip()]
            chars = sum(len(line) for line in lines)
            first_line = lines[0] if lines else ""
        print(f"Chapter {i+1:02d}: {os.path.basename(src_file)} -> {dst_file} ({len(lines)} paras, {chars} chars) | Title/Lead: {first_line[:50]}")
    else:
        print(f"Chapter {i+1:02d}: NOT FOUND!")
