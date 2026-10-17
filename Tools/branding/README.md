# ArkGram logo resources

`render_logo.py` defines the three closed Bezier paths shared by the launcher,
notification, intro texture, terms emblem, Android monochrome drawable, QR SVG
and all three Lottie logo animations. Keep artwork changes in `BOAT_PATHS` and
regenerate the assets with Python and Pillow 12.3.0:

```sh
python Tools/branding/render_logo.py
```

PNG resources keep their Android logical dimensions and cover mdpi through
xxxhdpi. Rasterization uses 4x supersampling and Lanczos downsampling, including
transparent foregrounds and notification silhouettes. Colors, launcher ring and
launcher mark widths are preserved.

Lottie resources remain entirely vector based at 512x512, 60fps, frames 20..74.
Only logo artwork is replaced. Original layer transforms, keyframes, easing,
background and effect layers remain intact. Artwork is counter-rotated by 49
degrees around the original anchor so the boat is upright during the existing
rocking motion. The intro keeps its existing OpenGL mesh and animation.

The **ArkGram Logo Assets** GitHub Actions workflow checks regeneration against
committed assets, density dimensions, transparency and antialiasing. It renders
all three animations with Lottie at 512px and 1024px and checks for changing,
nonempty frames. Android's AAPT2 compiler also compiles every checked logo resource
from the app and standalone builds. Full application builds use the existing beta
workflow. Its `boat-logo-preview` artifact includes screenshots and a
lossless animated WebP with the original 60fps timeline. Verification runs in CI;
do not run `verify_logo.py`, browser checks, Android builds or tests locally.
