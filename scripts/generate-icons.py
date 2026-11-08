# SPDX-FileCopyrightText: Copyright (C) 2026 The VNCL Contributors
# SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
"""Generate VNCL launcher icons from a single square source image.

Usage:
    python scripts/generate-icons.py <source-image> [resources-dir]

The source is center-cropped slightly, rounded, and written to the asset sizes
used by the launcher, plus a multi-size .ico used for the Windows executable.
"""

from __future__ import annotations

import sys
from pathlib import Path

from PIL import Image, ImageDraw

# Icon variants and their pixel sizes; mirrors assets/img in the HMCL resources.
VARIANTS = {
    "icon.png": 32,
    "icon@2x.png": 64,
    "icon@4x.png": 128,
    "icon@8x.png": 256,
    "icon-title.png": 24,
    "icon-title@2x.png": 48,
    "icon-mac.png": 512,
}

# Small variants use a tighter face crop so the character stays readable at 16-64 px.
FACE_VARIANTS = {"icon.png", "icon@2x.png", "icon-title.png", "icon-title@2x.png"}

ICO_SIZES = (16, 24, 32, 48, 64, 128, 256)

# Fraction of the source kept by the center crop, so the character stays readable at 32 px.
CROP_RATIO = 0.92
# Tighter crop around the face used by small icons and the executable icon.
FACE_RATIO = 0.70
FACE_Y_BIAS = -0.03
# Corner radius as a fraction of the icon size.
CORNER_RATIO = 0.20


def center_crop(image: Image.Image, ratio: float, y_bias: float = 0.0) -> Image.Image:
    """Crop a centered square covering `ratio` of the shorter side."""
    side = int(min(image.size) * ratio)
    center_x = image.width // 2
    center_y = int(image.height / 2 + image.height * y_bias)
    left = center_x - side // 2
    top = center_y - side // 2
    return image.crop((left, top, left + side, top + side))


def rounded(image: Image.Image, size: int) -> Image.Image:
    """Resize `image` to a square and mask it with rounded corners."""
    resized = image.resize((size, size), Image.LANCZOS)
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).rounded_rectangle(
        (0, 0, size - 1, size - 1),
        radius=max(1, int(size * CORNER_RATIO)),
        fill=255,
    )
    result = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    result.paste(resized, (0, 0), mask)
    return result


def main() -> int:
    if len(sys.argv) < 2:
        print(__doc__)
        return 2

    source = Image.open(sys.argv[1]).convert("RGBA")
    resources = Path(sys.argv[2]) if len(sys.argv) > 2 else Path("HMCL/src/main/resources")
    image_dir = resources / "assets" / "img"
    image_dir.mkdir(parents=True, exist_ok=True)

    cropped = center_crop(source, CROP_RATIO)
    face = center_crop(source, FACE_RATIO, FACE_Y_BIAS)

    for name, size in VARIANTS.items():
        source_image = face if name in FACE_VARIANTS else cropped
        rounded(source_image, size).save(image_dir / name, optimize=True)
        print(f"wrote {image_dir / name} ({size}px)")

    ico_path = resources.resolve().parents[3] / "image" / "vncl.ico"
    ico_path.parent.mkdir(parents=True, exist_ok=True)
    rounded(face, 256).save(
        ico_path,
        format="ICO",
        sizes=[(size, size) for size in ICO_SIZES],
    )
    print(f"wrote {ico_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
