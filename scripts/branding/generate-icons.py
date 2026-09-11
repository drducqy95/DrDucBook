from __future__ import annotations

import argparse
from pathlib import Path

from PIL import Image, ImageDraw, ImageEnhance, ImageFilter, ImageOps


DENSITIES = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192,
}


def crop_emblem_square(image: Image.Image) -> Image.Image:
    width, height = image.size
    # Center squircle emblem in source
    cx = 656 * width // 1313
    cy = 695 * height // 1391
    side = min(width, int(1280 * height / 1391))
    left = max(0, cx - side // 2)
    top = max(0, cy - side // 2)
    right = min(width, left + side)
    bottom = min(height, top + side)
    cropped = image.crop((left, top, right, bottom)).convert("RGBA")
    return cropped.resize((1024, 1024), Image.Resampling.LANCZOS)


def apply_squircle_mask(image: Image.Image, radius: int = 220) -> Image.Image:
    mask = Image.new("L", image.size, 0)
    draw = ImageDraw.Draw(mask)
    draw.rounded_rectangle((0, 0, image.width, image.height), radius=radius, fill=255)
    # Smooth edges with slight blur
    mask = mask.filter(ImageFilter.GaussianBlur(0.6))
    result = image.copy()
    result.putalpha(mask)
    return result


def apply_circle_mask(image: Image.Image) -> Image.Image:
    mask = Image.new("L", image.size, 0)
    draw = ImageDraw.Draw(mask)
    draw.ellipse((0, 0, image.width, image.height), fill=255)
    mask = mask.filter(ImageFilter.GaussianBlur(0.6))
    result = image.copy()
    result.putalpha(mask)
    return result


def save_png(image: Image.Image, path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    clean = Image.new("RGBA", image.size)
    clean.paste(image, (0, 0))
    clean.save(path, format="PNG", optimize=True)


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


def make_bw_version(master: Image.Image) -> Image.Image:
    gray = ImageOps.grayscale(master)
    gray = ImageEnhance.Contrast(gray).enhance(1.25)
    bw = Image.merge("RGBA", (gray, gray, gray, master.getchannel("A")))
    return bw


def make_qa_sheet(master: Image.Image, master_bw: Image.Image) -> Image.Image:
    sheet = Image.new("RGB", (960, 680), "#f4f1ed")
    draw = ImageDraw.Draw(sheet)
    sizes = [48, 96, 192, 384]
    x = 40
    for size in sizes:
        icon = master.resize((size, size), Image.Resampling.LANCZOS)
        sheet.paste(icon, (x, 100), icon)
        draw.text((x, 70), f"{size}px (Release)", fill="#241916")

        icon_bw = master_bw.resize((size, size), Image.Resampling.LANCZOS)
        sheet.paste(icon_bw, (x, 460), icon_bw)
        draw.text((x, 430), f"{size}px (Debug B&W)", fill="#241916")

        x += size + 36
    draw.text((40, 25), "DrDucBook Icon Transformation QA (Release & Debug B&W)", fill="#241916")
    return sheet


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("source", type=Path)
    parser.add_argument("root", type=Path)
    args = parser.parse_args()

    root = args.root.resolve()
    with Image.open(args.source) as source:
        clean = crop_emblem_square(ImageOps.exif_transpose(source))
    master = apply_squircle_mask(clean, radius=220)
    master_bw = make_bw_version(master)

    # 1. Release Brand Master & Android Assets (src/main)
    save_png(master, root / "branding/drducbook-icon-master.png")
    save_png(
        make_foreground(master),
        root / "app/src/main/res/drawable-xxxhdpi/drducbook_icon_foreground.png",
    )
    save_png(
        make_monochrome(master),
        root / "app/src/main/res/drawable-xxxhdpi/drducbook_icon_monochrome.png",
    )

    master_round = apply_circle_mask(clean)
    master_round_bw = apply_circle_mask(make_bw_version(clean))

    for density, size in DENSITIES.items():
        # Release mipmaps
        dir_main = root / f"app/src/main/res/mipmap-{density}"
        dir_main.mkdir(parents=True, exist_ok=True)
        master.resize((size, size), Image.Resampling.LANCZOS).save(
            dir_main / "ic_launcher.webp", "WEBP", lossless=True, method=6
        )
        master_round.resize((size, size), Image.Resampling.LANCZOS).save(
            dir_main / "ic_launcher_round.webp", "WEBP", lossless=True, method=6
        )

        # Debug B&W mipmaps (src/debug)
        dir_debug = root / f"app/src/debug/res/mipmap-{density}"
        dir_debug.mkdir(parents=True, exist_ok=True)
        master_bw.resize((size, size), Image.Resampling.LANCZOS).save(
            dir_debug / "ic_launcher.webp", "WEBP", lossless=True, method=6
        )
        master_round_bw.resize((size, size), Image.Resampling.LANCZOS).save(
            dir_debug / "ic_launcher_round.webp", "WEBP", lossless=True, method=6
        )

    # Debug B&W adaptive foreground
    save_png(
        make_foreground(master_bw),
        root / "app/src/debug/res/drawable-xxxhdpi/drducbook_icon_foreground.png",
    )

    # Fallback drawable
    drawable = master.resize((512, 512), Image.Resampling.LANCZOS)
    drawable.save(
        root / "app/src/main/res/drawable/ic_launcher.webp",
        "WEBP",
        lossless=True,
        method=6,
    )

    # 2. WebService Favicons & Logos
    favicon = master.convert("RGBA")
    fav_paths = [
        root / "modules/web/public/favicon.ico",
        root / "modules/web/dist/favicon.ico",
        root / "app/src/main/assets/web/favicon.ico",
        root / "app/src/main/assets/web/vue/favicon.ico",
    ]
    for fav_path in fav_paths:
        fav_path.parent.mkdir(parents=True, exist_ok=True)
        favicon.save(
            fav_path,
            format="ICO",
            sizes=[(16, 16), (32, 32), (48, 48), (64, 64)],
        )

    # Wi-Fi upload page logo
    save_png(master.resize((450, 450), Image.Resampling.LANCZOS), root / "app/src/main/assets/web/uploadBook/img/logo.png")

    # QA Sheet
    save_png(make_qa_sheet(master, master_bw), root / "artifacts/phase01/icon-qa.png")
    print("Icon generation completed successfully!")


if __name__ == "__main__":
    main()

