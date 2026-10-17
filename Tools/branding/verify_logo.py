"""CI-only verification of density coverage, reproducibility and rendered motion."""
from pathlib import Path
import subprocess

from PIL import Image

import render_logo


res = Path(render_logo.RES)
standalone = Path(render_logo.RES_STANDALONE)
pngs = [p for root in (res, standalone) for p in root.glob("*/*.png")
        if p.stem in ("intro_tg_plane", "logo_middle", "notification", "ic_launcher_dr")
        or (p.stem.startswith(("icon_", "ic_launcher")) and "background" not in p.stem)]
pixels = {p: (Image.open(p).size, Image.open(p).convert("RGBA").tobytes()) for p in pngs}
vectors = [res / "drawable/icon_plane.xml", res / "raw/qr_logo.svg",
           *(res / f"raw/{name}.json" for name in ("plane_logo_plain", "qr_code_logo", "qr_code_logo_2"))]
sources = {p: p.read_bytes() for p in vectors}
render_logo.regen_all()
for p, (size, before) in pixels.items():
    with Image.open(p) as image:
        assert image.size == size, f"Resource dimensions changed: {p}"
        assert image.convert("RGBA").tobytes() == before, f"Stale generated resource: {p}"
for p, before in sources.items():
    assert p.read_bytes() == before, f"Stale generated vector: {p}"
for density, scale in render_logo.SCALE.items():
    for name, folder, logical_size in (
        ("ic_launcher", "mipmap", (48, 48)),
        ("icon_foreground", "mipmap", (108, 108)),
        ("notification", "drawable", (24, 24)),
        ("intro_tg_plane", "drawable", (82, 74)),
        ("logo_middle", "drawable", (68, 68)),
    ):
        p = res / f"{folder}-{density}/{name}.png"
        with Image.open(p) as image:
            assert image.size == tuple(int(value * scale) for value in logical_size), f"Wrong density: {p}"
            alpha = image.getchannel("A")
            assert alpha.getextrema() == (0, 255), f"Lost transparency or artwork: {p}"
            assert len(alpha.getcolors(256)) > 2, f"Missing antialiasing: {p}"

output = Path("branding-preview")
subprocess.run(["node", str(Path(__file__).with_name("render_animations.cjs")), str(output)], check=True)
for p in output.glob("*.png"):
    with Image.open(p) as image:
        size = 1024 if p.stem.endswith("-1024") else 512
        assert image.size == (size, size), f"Wrong render resolution: {p}"
        alpha = image.getchannel("A")
        assert alpha.getbbox(), f"Empty animation frame: {p}"
        assert len(alpha.getcolors(256)) > 2, f"Missing vector antialiasing: {p}"
frames = [Image.open(output / f"frame-{frame:02}.png").convert("RGBA") for frame in range(54)]
frames[0].save(output / "boat-animation.webp", save_all=True, append_images=frames[1:],
               duration=[round((i + 1) * 1000 / 60) - round(i * 1000 / 60) for i in range(54)],
               lossless=True, loop=0)
print(f"Verified {len(pngs)} PNG assets, {len(vectors)} vectors, all densities and three 60fps animations at 512/1024px.")
