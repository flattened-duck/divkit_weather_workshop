"""Tile render/slide-*.png into a few labeled contact sheets for fast review."""
import os, glob, re, sys
from PIL import Image, ImageDraw, ImageFont

RENDER = sys.argv[1] if len(sys.argv) > 1 else "/Users/the-leo/divkit-weather-workshop/talk/render"
OUT = sys.argv[2] if len(sys.argv) > 2 else "/Users/the-leo/divkit-weather-workshop/talk"
COLS = 4
ROWS = 4                # 16 per sheet
THUMB_W = 620
PAD = 16
LABEL_H = 30

def num(p):
    m = re.search(r"slide-(\d+)\.png", p)
    return int(m.group(1)) if m else 0

files = sorted(glob.glob(os.path.join(RENDER, "slide-*.png")), key=num)
if not files:
    print("no slides in", RENDER); sys.exit(1)

# thumb height from first image aspect
im0 = Image.open(files[0])
ar = im0.height / im0.width
THUMB_H = int(THUMB_W * ar)
cell_w = THUMB_W + PAD
cell_h = THUMB_H + LABEL_H + PAD

per_sheet = COLS * ROWS
sheets = []
for s in range(0, len(files), per_sheet):
    chunk = files[s:s + per_sheet]
    rows = (len(chunk) + COLS - 1) // COLS
    W = COLS * cell_w + PAD
    H = rows * cell_h + PAD
    sheet = Image.new("RGB", (W, H), "white")
    d = ImageDraw.Draw(sheet)
    try:
        font = ImageFont.truetype("/System/Library/Fonts/Supplemental/Arial.ttf", 22)
    except Exception:
        font = ImageFont.load_default()
    for i, f in enumerate(chunk):
        r, c = divmod(i, COLS)
        x = PAD + c * cell_w
        y = PAD + r * cell_h
        th = Image.open(f).resize((THUMB_W, THUMB_H))
        sheet.paste(th, (x, y + LABEL_H))
        d.rectangle([x, y + LABEL_H, x + THUMB_W, y + LABEL_H + THUMB_H], outline="#CFCDDD", width=1)
        d.text((x + 4, y + 4), f"Слайд {num(f)}", fill="#222A3A", font=font)
    outp = os.path.join(OUT, f"sheet-{len(sheets)+1}.png")
    sheet.save(outp)
    sheets.append(outp)
    print("wrote", outp, f"({len(chunk)} slides)")
print("sheets:", len(sheets))
