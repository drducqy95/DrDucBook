from __future__ import annotations

from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[2]
FORBIDDEN_METADATA = {
    "comment",
    "c2pa",
    "exif",
    "icc_profile",
    "photoshop",
    "xmp",
    "xml",
}


def verify(path: Path, require_transparent_corners: bool = False) -> None:
    with Image.open(path) as image:
        metadata = {key.lower() for key in image.info}
        forbidden = metadata & FORBIDDEN_METADATA
        if forbidden or len(image.getexif()) > 0:
            raise RuntimeError(f"Metadata found in {path}: {sorted(forbidden)}")

        rgba = image.convert("RGBA")
        if require_transparent_corners:
            corners = (
                rgba.getpixel((0, 0))[3],
                rgba.getpixel((rgba.width - 1, 0))[3],
                rgba.getpixel((0, rgba.height - 1))[3],
                rgba.getpixel((rgba.width - 1, rgba.height - 1))[3],
            )
            if corners != (0, 0, 0, 0):
                raise RuntimeError(f"Opaque icon corners in {path}: {corners}")

        print(f"PASS {path.relative_to(ROOT)} {image.mode} {image.size}")


def main() -> None:
    verify(ROOT / "branding/drducbook-icon-master.png", require_transparent_corners=True)
    verify(
        ROOT / "app/src/main/res/drawable-xxxhdpi/drducbook_icon_foreground.png",
        require_transparent_corners=True,
    )
    verify(ROOT / "app/src/main/res/drawable-xxxhdpi/drducbook_icon_monochrome.png")
    for path in sorted((ROOT / "app/src/main/res").glob("mipmap-*/ic_launcher*.webp")):
        verify(path, require_transparent_corners=True)
    verify(ROOT / "modules/web/public/favicon.ico")


if __name__ == "__main__":
    main()
