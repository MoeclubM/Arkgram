"""Render Play listing artwork in CI from the existing ArkGram boat geometry."""
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

import render_logo


output = Path("play-store-assets")
output.mkdir(exist_ok=True)
icon = Image.new("RGBA", (512, 512), render_logo.CREAM)
icon.alpha_composite(render_logo.render_transparent(512, 512, render_logo.INK, fill_frac=0.52))
icon.convert("RGB").save(output / "icon.png")
graphic = Image.new("RGB", (4096, 2000), render_logo.CREAM[:3])
draw = ImageDraw.Draw(graphic)
render_logo.draw_boat(draw, 720, 1000, 840 / 0.92, render_logo.INK[:3])
fonts = Path(render_logo.REPO) / "TMessagesProj/src/main/assets/fonts"
title = ImageFont.truetype(str(fonts / "rextrabold.ttf"), 400)
caption = ImageFont.truetype(str(fonts / "rmedium.ttf"), 112)
draw.text((1320, 664), "ArkGram", font=title, fill=render_logo.INK[:3])
draw.text((1352, 1168), "Make messaging yours", font=caption, fill=render_logo.INK[:3])
graphic.resize((1024, 500), Image.Resampling.LANCZOS).save(output / "feature-graphic.png")
print("Rendered opaque 512x512 icon and 1024x500 feature graphic")
