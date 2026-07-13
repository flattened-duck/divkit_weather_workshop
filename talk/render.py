"""render.py — turn a .pptx into one PNG per slide for visual review.

Pipeline: soffice (headless) pptx -> pdf, then pdftoppm pdf -> slideNN.png.
Uses an isolated LibreOffice user profile so it works even if the desktop app
is open.

Usage:
    python render.py <input.pptx> [out_dir] [dpi]
Default out_dir = <input dir>/render , dpi = 110
"""

import os
import subprocess
import sys

SOFFICE = "/Applications/LibreOffice.app/Contents/MacOS/soffice"


def render(pptx, out_dir=None, dpi=110):
    pptx = os.path.abspath(pptx)
    base = os.path.splitext(os.path.basename(pptx))[0]
    out_dir = out_dir or os.path.join(os.path.dirname(pptx), "render")
    os.makedirs(out_dir, exist_ok=True)
    profile = os.path.join(out_dir, ".lo_profile")

    # 1) pptx -> pdf
    subprocess.run(
        [SOFFICE, "-env:UserInstallation=file://" + profile,
         "--headless", "--convert-to", "pdf", "--outdir", out_dir, pptx],
        check=True, capture_output=True,
    )
    pdf = os.path.join(out_dir, base + ".pdf")
    if not os.path.exists(pdf):
        raise RuntimeError(f"PDF not produced: {pdf}")

    # 2) pdf -> png pages
    for f in os.listdir(out_dir):
        if f.startswith("slide") and f.endswith(".png"):
            os.remove(os.path.join(out_dir, f))
    subprocess.run(
        ["pdftoppm", "-png", "-r", str(dpi), pdf, os.path.join(out_dir, "slide")],
        check=True, capture_output=True,
    )
    pngs = sorted(f for f in os.listdir(out_dir)
                  if f.startswith("slide") and f.endswith(".png"))
    print(f"rendered {len(pngs)} slides -> {out_dir}")
    for p in pngs:
        print("  ", os.path.join(out_dir, p))
    return out_dir


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(1)
    render(sys.argv[1],
           sys.argv[2] if len(sys.argv) > 2 else None,
           int(sys.argv[3]) if len(sys.argv) > 3 else 110)
