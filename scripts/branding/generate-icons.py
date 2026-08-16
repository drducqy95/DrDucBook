from __future__ import annotations

import argparse
from collections import deque
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageOps


DENSITIES = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192,
}


def square(image: Image.Image) -> Image.Image:
    width, height = image.size
    side = min(width, height)
    left = (width - side) // 2
    top = (height - side) // 2
    return image.crop((left, top, left + side, top + side)).convert("RGBA")


def remove_generated_checkerboard(image: Image.Image) -> Image.Image:
    if image.getchannel("A").getextrema()[0] < 255:
        return image

    rgb = np.asarray(image.convert("RGB"), dtype=np.int16)
    spread = rgb.max(axis=2) - rgb.min(axis=2)
    candidate = (spread < 34) & (rgb.max(axis=2) > 175)
    height, width = candidate.shape
    outside = np.zeros((height, width), dtype=bool)
    queue: deque[tuple[int, int]] = deque()

    for x in range(width):
        if candidate[0, x]:
            queue.append((0, x))
        if candidate[height - 1, x]:
            queue.append((height - 1, x))
    for y in range(height):
        if candidate[y, 0]:
            queue.append((y, 0))
        if candidate[y, width - 1]:
            queue.append((y, width - 1))

    while queue:
        y, x = queue.popleft()
        if outside[y, x] or not candidate[y, x]:
            continue
        outside[y, x] = True
        if y > 0:
            queue.append((y - 1, x))
        if y + 1 < height:
            queue.append((y + 1, x))
        if x > 0:
            queue.append((y, x - 1))
        if x + 1 < width:
            queue.append((y, x + 1))

    mask = Image.fromarray(np.where(outside, 0, 255).astype(np.uint8), mode="L")
    mask = mask.filter(ImageFilter.MinFilter(3)).filter(ImageFilter.GaussianBlur(0.55))
    result = image.copy()
    result.putalpha(mask)
    return result


def save_png(image: Image.Image, path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path, format="PNG", optimize=True)


def make_foreground(master: Image.Image, size: int = 432) -> Image.Image:
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    art_size = 324
    art = master.resize((art_size, art_size), Image.Resampling.LANCZOS)
    offset = (size - art_size) // 2
    canvas.alpha_composite(art, (offset, offset))
    return canvas


def make_monochrome(master: Image.Image, size: int = 432) -> Image.Image:
    art_size = 300
    art = master.resize((art_size, art_size), Image.Resampling.LANCZOS)
    luminance = ImageOps.grayscale(art)
    alpha = luminance.point(lambda value: 255 if value > 30 else 0).filter(
        ImageFilter.GaussianBlur(0.6)
    )
    white = Image.new("RGBA", art.size, (255, 255, 255, 255))
    white.putalpha(alpha)
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    offset = (size - art_size) // 2
    canvas.alpha_composite(white, (offset, offset))
    return canvas


def make_qa_sheet(master: Image.Image) -> Image.Image:
    sheet = Image.new("RGB", (900, 620), "#f4f1ed")
    draw = ImageDraw.Draw(sheet)
    sizes = [48, 96, 192, 384]
    x = 40
    for size in sizes:
        icon = master.resize((size, size), Image.Resampling.LANCZOS)
        sheet.paste(icon, (x, 120), icon)
        draw.text((x, 82), f"{size}px", fill="#241916")
        x += size + 42
    draw.text((40, 35), "DrDucBook icon size QA", fill="#241916")
    return sheet


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("source", type=Path)
    parser.add_argument("root", type=Path)
    args = parser.parse_args()

    root = args.root.resolve()
    with Image.open(args.source) as source:
        clean = remove_generated_checkerboard(square(ImageOps.exif_transpose(source)))
    master = clean.resize((1024, 1024), Image.Resampling.LANCZOS)

    save_png(master, root / "branding/drducbook-icon-master.png")
    save_png(
        make_foreground(master),
        root / "app/src/main/res/drawable-xxxhdpi/drducbook_icon_foreground.png",
    )
    save_png(
        make_monochrome(master),
        root / "app/src/main/res/drawable-xxxhdpi/drducbook_icon_monochrome.png",
    )

    for density, size in DENSITIES.items():
        resized = master.resize((size, size), Image.Resampling.LANCZOS)
        directory = root / f"app/src/main/res/mipmap-{density}"
        directory.mkdir(parents=True, exist_ok=True)
        resized.save(directory / "ic_launcher.webp", "WEBP", lossless=True, method=6)
        resized.save(directory / "ic_launcher_round.webp", "WEBP", lossless=True, method=6)

    drawable = master.resize((512, 512), Image.Resampling.LANCZOS)
    drawable.save(
        root / "app/src/main/res/drawable/ic_launcher.webp",
        "WEBP",
        lossless=True,
        method=6,
    )

    favicon = master.convert("RGBA")
    favicon.save(
        root / "modules/web/public/favicon.ico",
        format="ICO",
        sizes=[(16, 16), (32, 32), (48, 48), (64, 64)],
    )
    save_png(make_qa_sheet(master), root / "artifacts/phase01/icon-qa.png")


if __name__ == "__main__":
    main()
