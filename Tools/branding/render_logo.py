"""Unified renderer for the ArkGram boat logo.

Single source of truth for the boat shape; regenerates every PNG asset and the
vector drawable, SVG and Lottie artwork from these normalized Bezier paths.
"""
from PIL import Image, ImageDraw, ImageFilter, ImageFont
import json
import math
import os

REPO = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
RES = os.path.join(REPO, "TMessagesProj", "src", "main", "res")
RES_STANDALONE = os.path.join(REPO, "TMessagesProj_AppStandalone", "src", "main", "res")

INK = (47, 42, 36, 255)   # #2F2A24 — same dark warm brown the app already uses
CREAM = (244, 226, 184, 255)  # #F4E2B8 — current launcher background
WHITE = (255, 255, 255, 255)

# Vertices, incoming and outgoing tangents, in Lottie's relative-handle format.
# Curved sails and a shallow, rounded hull retain clear gaps at notification size.
BOAT_PATHS = [
    ("Main sail",
     [(-0.025, -0.425), (-0.025, 0.075), (-0.365, 0.075)],
     [(-0.13, 0.11), (0, 0), (0, 0)],
     [(0, 0), (0, 0), (0.055, -0.19)]),
    ("Jib",
     [(0.04, -0.29), (0.365, 0.075), (0.04, 0.075)],
     [(0, 0), (-0.075, -0.15), (0, 0)],
     [(0.135, 0.085), (0, 0), (0, 0)]),
    ("Hull",
     [(-0.46, 0.135), (0.46, 0.135), (0.29, 0.365),
      (0.205, 0.405), (-0.205, 0.405), (-0.29, 0.365)],
     [(0.04, 0.09), (-0.18, 0.012), (0.045, -0.05),
      (0.036, 0), (0, 0), (0.022, 0.026)],
     [(0.18, 0.012), (-0.04, 0.09), (-0.022, 0.026),
      (0, 0), (-0.036, 0), (-0.045, -0.05)]),
]

DENSITIES = ["mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"]
SCALE = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}


def boat_paths(cx: float, cy: float, s: float, rotation: float = 0):
    angle = math.radians(rotation)
    cos, sin = math.cos(angle), math.sin(angle)
    for name, vertices, incoming, outgoing in BOAT_PATHS:
        path = {"c": True}
        for key, points in (("v", vertices), ("i", incoming), ("o", outgoing)):
            path[key] = [[round(s * (x * cos - y * sin) + (cx if key == "v" else 0), 4),
                          round(s * (x * sin + y * cos) + (cy if key == "v" else 0), 4)]
                         for x, y in points]
        yield name, path


def path_data(path):
    """Serialize the shared cubic paths for both Android and SVG."""
    vertices = path["v"]
    commands = [f"M{vertices[0][0]},{vertices[0][1]}"]
    for i, (x, y) in enumerate(vertices):
        j = (i + 1) % len(vertices)
        end = vertices[j]
        outgoing, incoming = path["o"][i], path["i"][j]
        if outgoing == [0, 0] and incoming == [0, 0]:
            commands.append(f"L{end[0]},{end[1]}")
        else:
            commands.append(f"C{x + outgoing[0]:.4f},{y + outgoing[1]:.4f} "
                            f"{end[0] + incoming[0]:.4f},{end[1] + incoming[1]:.4f} {end[0]},{end[1]}")
    return " ".join(commands) + " Z"


def draw_boat(draw: ImageDraw.ImageDraw, cx: float, cy: float, s: float, color):
    for _, path in boat_paths(cx, cy, s):
        pts = []
        for i, start in enumerate(path["v"]):
            j = (i + 1) % len(path["v"])
            end = path["v"][j]
            outgoing, incoming = path["o"][i], path["i"][j]
            for step in range(32):
                t = step / 32
                pts.append(tuple((1 - t) ** 3 * start[k]
                                 + 3 * (1 - t) ** 2 * t * (start[k] + outgoing[k])
                                 + 3 * (1 - t) * t ** 2 * (end[k] + incoming[k])
                                 + t ** 3 * end[k] for k in (0, 1)))
        draw.polygon(pts, fill=color)


def render_transparent(w: int, h: int, color, fill_frac: float = 0.58) -> Image.Image:
    """Boat in `color` on transparent canvas. `fill_frac` controls the boat's
    width relative to the canvas (so design width 0.92*s ≈ fill_frac*min(w,h))."""
    scale = 4
    img = Image.new("RGBA", (w * scale, h * scale), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    s = min(w, h) * scale * fill_frac / 0.92
    draw_boat(d, w * scale / 2, h * scale / 2, s, color)
    return img.resize((w, h), Image.Resampling.LANCZOS)


def render_background_clip(size: int, *, inset: float = 0.155) -> Image.Image:
    """Adaptive-icon mask overlay: white outside, transparent center.
    Layered above the cream background to preserve Telegram's outer white ring."""
    scale = 4
    big = size * scale
    img = Image.new("RGBA", (big, big), WHITE)
    d = ImageDraw.Draw(img)
    m = int(big * inset)
    d.ellipse([m, m, big - 1 - m, big - 1 - m], fill=(0, 0, 0, 0))
    return img.resize((size, size), Image.Resampling.LANCZOS)


def render_launcher(size: int, *, bg=CREAM, ink=INK, fill_frac: float = 0.52) -> Image.Image:
    """Pre-composited launcher PNG: transparent canvas + white outer circle + cream circle + boat."""
    scale = 4
    big = size * scale
    img = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    shadow = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    shadow_draw = ImageDraw.Draw(shadow)
    outer = int(big * 0.02)
    shadow_draw.ellipse([outer, outer + int(big * 0.02), big - 1 - outer, big - 1 - outer + int(big * 0.02)], fill=(0, 0, 0, 36))
    shadow = shadow.filter(ImageFilter.GaussianBlur(max(1, int(big * 0.018))))
    img.alpha_composite(shadow)
    d = ImageDraw.Draw(img)
    d.ellipse([outer, outer, big - 1 - outer, big - 1 - outer], fill=WHITE)
    ring = int(big * 0.08)
    d.ellipse([ring, ring, big - 1 - ring, big - 1 - ring], fill=bg)
    s = big * fill_frac / 0.92
    draw_boat(d, big / 2, big / 2, s, ink)
    return img.resize((size, size), Image.Resampling.LANCZOS)


def save(img: Image.Image, path: str):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path, optimize=True)


# ---- targeted regenerators ----

def regen_icon_foreground():
    """Adaptive-icon foreground layer: dark boat on transparent, 108dp canvas.
    Densities: mdpi=108, hdpi=162, xhdpi=216, xxhdpi=324, xxxhdpi=432.
    Variants: {,_round,_sa}. Same boat shape for every variant — variants live
    in the background drawables."""
    base = 108
    names = ["icon_foreground", "icon_foreground_round", "icon_foreground_sa",
             "icon_3_foreground", "icon_3_foreground_round", "icon_3_foreground_sa",
             "icon_5_foreground", "icon_5_foreground_round", "icon_5_foreground_sa",
             "icon_6_foreground", "icon_6_foreground_round", "icon_6_foreground_sa"]
    written = 0
    for d in DENSITIES:
        size = int(base * SCALE[d])
        img = render_transparent(size, size, INK, fill_frac=0.35)
        for n in names:
            path = os.path.join(RES, f"mipmap-{d}", f"{n}.png")
            if os.path.exists(path):
                save(img, path)
                written += 1
    return written


def regen_notification():
    """Status-bar notification icon: WHITE silhouette on transparent.
    Densities: mdpi=24, hdpi=36, xhdpi=48, xxhdpi=72, xxxhdpi=96."""
    base = 24
    written = 0
    for d in DENSITIES:
        size = int(base * SCALE[d])
        img = render_transparent(size, size, WHITE, fill_frac=0.80)
        path = os.path.join(RES, f"drawable-{d}", "notification.png")
        if os.path.exists(path):
            save(img, path)
            written += 1
    return written


def regen_launchers():
    """Pre-composited launcher PNGs (Android < 8 fallback):
    white outer ring + cream circle + dark boat.
    Densities: mdpi=48, hdpi=72, xhdpi=96, xxhdpi=144, xxxhdpi=192."""
    base = 48
    written = 0
    targets = [
        ("ic_launcher",     "mipmap"),
        ("ic_launcher_round","mipmap"),
        ("ic_launcher_sa",  "mipmap"),
        ("icon_2_launcher", "mipmap"),
        ("icon_2_launcher_round","mipmap"),
        ("icon_3_launcher", "mipmap"),
        ("icon_3_launcher_round","mipmap"),
        ("icon_4_launcher", "mipmap"),
        ("icon_4_launcher_round","mipmap"),
        ("icon_5_launcher", "mipmap"),
        ("icon_5_launcher_round","mipmap"),
        ("icon_6_launcher", "mipmap"),
        ("icon_6_launcher_round","mipmap"),
        ("ic_launcher_dr",  "drawable"),
    ]
    standalone_names = ["ic_launcher_sa", "icon_2_launcher_sa", "icon_3_launcher_sa",
                        "icon_4_launcher_sa", "icon_5_launcher_sa", "icon_6_launcher_sa"]
    for d in DENSITIES:
        size = int(base * SCALE[d])
        sq = render_launcher(size)
        for name, sub in targets:
            path = os.path.join(RES, f"{sub}-{d}", f"{name}.png")
            if os.path.exists(path):
                save(sq, path)
                written += 1
        for name in standalone_names:
            sa_path = os.path.join(RES_STANDALONE, f"mipmap-{d}", f"{name}.png")
            if os.path.exists(sa_path):
                save(sq, sa_path)
                written += 1
    return written


def regen_background_clips():
    """Adaptive-icon clip overlays: white outer area with transparent center.
    The cream/color background below shows through the center and creates the
    original outer white ring for adaptive launcher icons."""
    base = 108
    written = 0
    for d in DENSITIES:
        size = int(base * SCALE[d])
        for name, inset in (("icon_background_clip", 0.234), ("icon_background_clip_round", 0.206)):
            img = render_background_clip(size, inset=inset)
            path = os.path.join(RES, f"mipmap-{d}", f"{name}.png")
            if os.path.exists(path):
                save(img, path)
                written += 1
    for name, inset in (("icon_background_clip", 0.234), ("icon_background_clip_round", 0.206)):
        path = os.path.join(RES, "drawable", f"{name}.png")
        if os.path.exists(path):
            save(render_background_clip(432, inset=inset), path)
            written += 1
    return written


def regen_intro_plane():
    """Boat texture for the OpenGL intro animation. Loaded with tint #FF2F2A24
    in IntroActivity.java — we just need the alpha silhouette.
    Densities: mdpi=82x74 through xxxhdpi=328x296. The OpenGL mesh keeps
    its original 82x74 logical size and animation."""
    base_w, base_h = 82, 74
    written = 0
    for d in DENSITIES:
        w = int(base_w * SCALE[d])
        h = int(base_h * SCALE[d])
        # Use white because the texture is loaded with an explicit color in code;
        # only alpha matters. White preserves alpha cleanly across resamplers.
        img = render_transparent(w, h, WHITE, fill_frac=0.92)
        path = os.path.join(RES, f"drawable-{d}", "intro_tg_plane.png")
        save(img, path)
        written += 1
    return written


def regen_dialogs_logo():
    """Action-bar ArkGram wordmark used by DialogsActivity.
    Keep the same mdpi logical size as the original bitmap (90x21dp), but write
    density-specific PNGs so high-density screens do not upscale the mdpi asset."""
    font_path = os.path.join(REPO, "TMessagesProj", "src", "main", "assets", "fonts", "rextrabold.ttf")
    base_w, base_h, base_font = 90, 21, 18
    written = 0
    for d in DENSITIES:
        scale = SCALE[d]
        w, h = int(base_w * scale), int(base_h * scale)
        img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        draw = ImageDraw.Draw(img)
        font = ImageFont.truetype(font_path, int(base_font * scale))
        bbox = draw.textbbox((0, 0), "ArkGram", font=font)
        y = int((h - (bbox[3] - bbox[1])) / 2 - bbox[1])
        draw.text((0, y), "ArkGram", font=font, fill=WHITE)
        path = os.path.join(RES, f"drawable-{d}", "telegram_logo_2.png")
        save(img, path)
        written += 1
    save(Image.open(os.path.join(RES, "drawable-mdpi", "telegram_logo_2.png")), os.path.join(RES, "drawable", "telegram_logo_2.png"))
    return written + 1


def regen_logo_middle():
    """Round ArkGram emblem used by TermsOfServiceView."""
    written = 0
    for d in DENSITIES:
        size = int(68 * SCALE[d])
        path = os.path.join(RES, f"drawable-{d}", "logo_middle.png")
        save(render_launcher(size, fill_frac=0.52), path)
        written += 1
    return written


def regen_vectors():
    paths = [path_data(path) for _, path in boat_paths(54, 54, 108 * 0.52 / 0.92)]
    vector = ('<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
              '    android:width="108dp" android:height="108dp"\n'
              '    android:viewportWidth="108" android:viewportHeight="108">\n')
    for path in paths:
        vector += f'    <path android:fillColor="#FF2F2A24" android:pathData="{path}" />\n'
    with open(os.path.join(RES, "drawable", "icon_plane.xml"), "w", encoding="utf-8", newline="\n") as f:
        f.write(vector + "</vector>\n")
    svg = ('<svg xmlns="http://www.w3.org/2000/svg" width="150" height="150" viewBox="0 0 150 150">\n'
           '    <circle fill="#E8C88E" cx="75" cy="75" r="75" />\n')
    for _, path in boat_paths(75, 75, 84 / 0.92):
        svg += f'    <path fill="#FFFFFF" d="{path_data(path)}" />\n'
    with open(os.path.join(RES, "raw", "qr_logo.svg"), "w", encoding="utf-8", newline="\n") as f:
        f.write(svg + "</svg>\n")
    return 2


def regen_animations():
    """Replace only the artwork; preserve original timing, easing and effects."""
    for name in ("plane_logo_plain", "qr_code_logo", "qr_code_logo_2"):
        path = os.path.join(RES, "raw", name + ".json")
        with open(path, encoding="utf-8") as f:
            animation = json.load(f)
        layer = next(layer for layer in animation["layers"] if layer["ind"] == 3)
        color = layer["shapes"][0]["it"][1]["c"]["k"]
        layer["nm"] = "Boat"
        layer["shapes"] = []
        # The original plane rocks around -49 degrees. Counter-rotate its artwork
        # around the existing anchor so the boat stays upright with the same motion.
        for part_name, shape in boat_paths(12.637, 41.19, 220, rotation=49):
            layer["shapes"].append({"ty": "gr", "nm": part_name, "it": [
                {"ty": "sh", "nm": "Path 1", "ks": {"a": 0, "k": shape}},
                {"ty": "fl", "nm": "Fill 1", "c": {"a": 0, "k": color},
                 "o": {"a": 0, "k": 100}, "r": 1},
                {"ty": "tr", "p": {"a": 0, "k": [0, 0]}, "a": {"a": 0, "k": [0, 0]},
                 "s": {"a": 0, "k": [100, 100]}, "r": {"a": 0, "k": 0},
                 "o": {"a": 0, "k": 100}},
            ]})
        with open(path, "w", encoding="utf-8", newline="\n") as f:
            json.dump(animation, f, ensure_ascii=False, separators=(",", ":"))
            f.write("\n")
    return 3


def regen_all():
    n1 = regen_icon_foreground()
    n2 = regen_notification()
    n3 = regen_launchers()
    n4 = regen_intro_plane()
    n5 = regen_background_clips()
    n6 = regen_dialogs_logo()
    n7 = regen_logo_middle()
    n8 = regen_vectors()
    n9 = regen_animations()
    print(f"icon_foreground:  {n1} files")
    print(f"notification:     {n2} files")
    print(f"launchers:        {n3} files")
    print(f"intro plane:      {n4} files")
    print(f"background clips: {n5} files")
    print(f"dialogs logo:     {n6} files")
    print(f"logo middle:      {n7} files")
    print(f"vectors:          {n8} files")
    print(f"animations:       {n9} files")
    print(f"total:            {n1+n2+n3+n4+n5+n6+n7+n8+n9} files")


if __name__ == "__main__":
    regen_all()
