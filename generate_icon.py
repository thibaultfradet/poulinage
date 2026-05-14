#!/usr/bin/env python3
"""Génère les icônes jument 🐴 pour Android (PNG + adaptive icon foreground)."""
from PIL import Image, ImageDraw, ImageFont
import os

BASE = "app/src/main/res"
EMOJI = "🐴"

# Icônes standard (48dp) pour les fallbacks Android < 8
standard_sizes = {
    "mipmap-mdpi":    48,
    "mipmap-hdpi":    72,
    "mipmap-xhdpi":   96,
    "mipmap-xxhdpi":  144,
    "mipmap-xxxhdpi": 192,
}

# Icônes foreground adaptive (108dp) pour Android 8+
# Le safe zone est le centre 72dp (66.7%), on dessine l'emoji dedans
adaptive_sizes = {
    "mipmap-mdpi":    108,
    "mipmap-hdpi":    162,
    "mipmap-xhdpi":   216,
    "mipmap-xxhdpi":  324,
    "mipmap-xxxhdpi": 432,
}

FONT_PATHS = [
    "/System/Library/Fonts/Apple Color Emoji.ttc",
    "/System/Library/Fonts/AppleColorEmoji.ttf",
]

# Pillow / Apple Color Emoji ne rend pas les emojis couleur au-delà de ~72px.
# On rend à cette taille de base puis on upscale avec LANCZOS.
MAX_RENDER_PX = 72


def get_font(size):
    for path in FONT_PATHS:
        if os.path.exists(path):
            try:
                return ImageFont.truetype(path, size)
            except Exception:
                pass
    return ImageFont.load_default()


def render_emoji(canvas_size, bg_color=(255, 255, 255, 255), emoji_size_ratio=0.75):
    """Rend l'emoji centré, puis upscale si nécessaire."""
    render_size = min(canvas_size, MAX_RENDER_PX)
    img = Image.new("RGBA", (render_size, render_size), bg_color)
    draw = ImageDraw.Draw(img)
    font = get_font(int(render_size * emoji_size_ratio))
    bbox = draw.textbbox((0, 0), EMOJI, font=font, embedded_color=True)
    w, h = bbox[2] - bbox[0], bbox[3] - bbox[1]
    x = (render_size - w) // 2 - bbox[0]
    y = (render_size - h) // 2 - bbox[1]
    draw.text((x, y), EMOJI, font=font, embedded_color=True)
    if canvas_size != render_size:
        img = img.resize((canvas_size, canvas_size), Image.LANCZOS)
    return img


print("Génération des icônes standard...")
for folder, size in standard_sizes.items():
    img = render_emoji(size)
    dest = os.path.join(BASE, folder)
    os.makedirs(dest, exist_ok=True)
    img.save(os.path.join(dest, "ic_launcher.png"))
    img.save(os.path.join(dest, "ic_launcher_round.png"))
    print(f"  {folder}/ic_launcher.png ({size}px) ✓")

print("\nGénération des foregrounds adaptatifs (108dp)...")
for folder, size in adaptive_sizes.items():
    # Pour l'adaptive icon, l'emoji doit être dans le safe zone (center 72/108)
    # On dessine l'emoji à 66% de la taille totale pour rester dans la safe zone
    img = render_emoji(size, emoji_size_ratio=0.6)
    dest = os.path.join(BASE, folder)
    os.makedirs(dest, exist_ok=True)
    img.save(os.path.join(dest, "ic_launcher_fg.png"))
    print(f"  {folder}/ic_launcher_fg.png ({size}px) ✓")

print("\nIcônes générées avec succès.")
