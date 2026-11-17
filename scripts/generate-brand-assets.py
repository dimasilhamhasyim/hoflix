#!/usr/bin/env python3
"""Rebuild every HOFLIX launcher, splash, wordmark, and preview asset from brand-src/.

Sources:
  brand-src/icon-source.png      red H on black (launcher icon)
  brand-src/wordmark-source.png  red "Hoflix" wordmark on transparent

Run from the repo root: python3 scripts/generate-brand-assets.py
"""
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
SRC_ICON = ROOT / "brand-src" / "icon-source.png"
SRC_WORDMARK = ROOT / "brand-src" / "wordmark-source.png"
RES = ROOT / "composeApp" / "src" / "androidMain" / "res"
COMPOSE_RES = ROOT / "composeApp" / "src" / "commonMain" / "composeResources" / "drawable"

DENSITIES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
FOREGROUND = {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}
VARIANTS = ["", "arctic_blue", "emerald", "rose_gold", "copper", "graphite"]


def load_letter() -> Image.Image:
    """Crop the red H and turn the black field into transparency."""
    rgb = np.asarray(Image.open(SRC_ICON).convert("RGB")).astype(np.float32)
    peak = rgb.max(axis=2)
    ys, xs = np.where(peak > 6)
    y0, y1 = ys.min(), ys.max() + 1
    x0, x1 = xs.min(), xs.max() + 1
    crop = rgb[y0:y1, x0:x1]
    peak = peak[y0:y1, x0:x1]
    alpha = np.clip((peak - 2.0) / 18.0, 0.0, 1.0)
    rgba = np.dstack([crop, alpha * 255.0]).astype(np.uint8)
    return Image.fromarray(rgba, "RGBA")


LETTER = load_letter()


def h_layer(height: int) -> Image.Image:
    ratio = height / LETTER.height
    width = max(1, round(LETTER.width * ratio))
    return LETTER.resize((width, height), Image.Resampling.LANCZOS)


def rounded_mask(size: int, radius_ratio: float) -> Image.Image:
    mask = Image.new("L", (size, size), 0)
    draw = ImageDraw.Draw(mask)
    radius = round(size * radius_ratio)
    draw.rounded_rectangle((0, 0, size - 1, size - 1), radius=radius, fill=255)
    return mask


def circle_mask(size: int) -> Image.Image:
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).ellipse((0, 0, size - 1, size - 1), fill=255)
    return mask


def legacy_icon(size: int, circular: bool = False) -> Image.Image:
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 255))
    h = h_layer(round(size * 0.70))
    canvas.alpha_composite(h, ((size - h.width) // 2, (size - h.height) // 2))
    canvas.putalpha(circle_mask(size) if circular else rounded_mask(size, 0.22))
    return canvas


def foreground(size: int) -> Image.Image:
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    h = h_layer(round(size * 0.54))
    canvas.alpha_composite(h, ((size - h.width) // 2, (size - h.height) // 2))
    return canvas


def monochrome(size: int) -> Image.Image:
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    h = h_layer(round(size * 0.54))
    white = Image.new("RGBA", h.size, (255, 255, 255, 0))
    white.putalpha(h.getchannel("A"))
    canvas.alpha_composite(white, ((size - h.width) // 2, (size - h.height) // 2))
    return canvas


def splash_mark(size: int = 1080) -> Image.Image:
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    h = h_layer(round(size * 0.52))
    canvas.alpha_composite(h, ((size - h.width) // 2, (size - h.height) // 2))
    return canvas


def wordmark() -> Image.Image:
    rgba = np.asarray(Image.open(SRC_WORDMARK).convert("RGBA"))
    alpha = rgba[:, :, 3]
    ys, xs = np.where(alpha > 8)
    crop = Image.fromarray(rgba[ys.min():ys.max() + 1, xs.min():xs.max() + 1], "RGBA")
    box = (1085, 344)
    ratio = min(box[0] / crop.width, box[1] / crop.height)
    resized = crop.resize((round(crop.width * ratio), round(crop.height * ratio)), Image.Resampling.LANCZOS)
    canvas = Image.new("RGBA", box, (0, 0, 0, 0))
    canvas.alpha_composite(resized, ((box[0] - resized.width) // 2, (box[1] - resized.height) // 2))
    return canvas


def save_webp(image: Image.Image, path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path, "WEBP", lossless=True, quality=100, method=6)
    print("wrote", path.relative_to(ROOT))


def save_png(image: Image.Image, path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path, "PNG", optimize=True)
    print("wrote", path.relative_to(ROOT))


def main() -> None:
    for density, size in DENSITIES.items():
        save_webp(legacy_icon(size), RES / f"mipmap-{density}" / "ic_launcher.webp")
        save_webp(legacy_icon(size, circular=True), RES / f"mipmap-{density}" / "ic_launcher_round.webp")
        for variant in VARIANTS:
            suffix = f"_{variant}" if variant else ""
            save_webp(legacy_icon(size), RES / f"mipmap-{density}" / f"ic_launcher{suffix}.webp")
            save_webp(foreground(FOREGROUND[density]), RES / f"mipmap-{density}" / f"ic_launcher{suffix}_foreground.webp")
        save_webp(monochrome(FOREGROUND[density]), RES / f"mipmap-{density}" / "ic_launcher_monochrome.webp")

    for variant in VARIANTS:
        suffix = f"_{variant}" if variant else ""
        save_webp(splash_mark(), RES / "drawable-nodpi" / f"ic_splash_logo{suffix}.webp")

    mark = wordmark()
    for name in ["app_logo_wordmark", "app_logo_wordmark_original", "app_logo_wordmark_gold",
                 "app_logo_wordmark_arctic_blue", "app_logo_wordmark_emerald",
                 "app_logo_wordmark_rose_gold", "app_logo_wordmark_copper",
                 "app_logo_wordmark_graphite"]:
        save_png(mark, COMPOSE_RES / f"{name}.png")

    for name in ["app_icon_original", "app_icon_arctic_blue", "app_icon_emerald",
                 "app_icon_rose_gold", "app_icon_copper", "app_icon_graphite"]:
        save_png(legacy_icon(256), COMPOSE_RES / f"{name}.png")


if __name__ == "__main__":
    main()
