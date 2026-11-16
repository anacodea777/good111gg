"""Generates launcher icons and Play Store graphics for good111gg.

Run:  python tools/make_assets.py   (needs Pillow)
"""
import math
import os
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app", "src", "main", "res")
STORE = os.path.join(ROOT, "store")
FONTS = "C:/Windows/Fonts"

BG_TOP = (4, 66, 67)
BG_MID = (0, 53, 47)
BG_BOTTOM = (11, 21, 20)
GOLD = (255, 204, 0)
MINT = (35, 255, 200)


def font(name, size):
    return ImageFont.truetype(os.path.join(FONTS, name), size)


def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def vgradient(w, h):
    img = Image.new("RGB", (w, h))
    px = img.load()
    for y in range(h):
        t = y / max(1, h - 1)
        c = lerp(BG_TOP, BG_MID, t * 2) if t < 0.5 else lerp(BG_MID, BG_BOTTOM, (t - 0.5) * 2)
        for x in range(w):
            px[x, y] = c
    return img


def radial_gold(size):
    """Gold disc with a light source top-left."""
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    px = img.load()
    r = size / 2
    lx, ly = r - 0.35 * r, r - 0.4 * r
    stops = [(0, (255, 246, 196)), (0.35, (255, 211, 26)), (0.72, (240, 180, 0)), (1, (185, 130, 0))]
    for y in range(size):
        for x in range(size):
            if (x - r) ** 2 + (y - r) ** 2 > r * r:
                continue
            d = math.hypot(x - lx, y - ly) / (1.55 * r)
            d = min(d, 1)
            for i in range(len(stops) - 1):
                if stops[i][0] <= d <= stops[i + 1][0]:
                    t = (d - stops[i][0]) / (stops[i + 1][0] - stops[i][0])
                    c = lerp(stops[i][1], stops[i + 1][1], t)
                    break
            px[x, y] = c + (255,)
    return img


def crown_points(cx, cy, r):
    pts = [(-0.50, 0.16), (-0.58, -0.30), (-0.27, -0.04), (0, -0.44), (0.27, -0.04), (0.58, -0.30), (0.50, 0.16)]
    return [(cx + x * r, cy + y * r) for x, y in pts]


def draw_crown(d, cx, cy, r, color):
    d.polygon(crown_points(cx, cy, r), fill=color)
    d.rounded_rectangle([cx - 0.52 * r, cy + 0.22 * r, cx + 0.52 * r, cy + 0.36 * r], radius=0.04 * r, fill=color)
    b = 0.07 * r
    for x, y in [(-0.58, -0.30), (0, -0.44), (0.58, -0.30)]:
        d.ellipse([cx + x * r - b, cy + y * r - b, cx + x * r + b, cy + y * r + b], fill=color)


def coin(size, face="heads"):
    """Render a coin of diameter `size` (RGBA), supersampled."""
    ss = 3
    S = size * ss
    img = radial_gold(S // 2).resize((S, S), Image.BICUBIC)
    mask = Image.new("L", (S, S), 0)
    ImageDraw.Draw(mask).ellipse([0, 0, S - 1, S - 1], fill=255)
    img.putalpha(mask)
    d = ImageDraw.Draw(img)
    r = S / 2
    c = r
    # rim + bevel
    w = int(r * 0.075)
    d.ellipse([c - r * 0.96 - w / 2, c - r * 0.96 - w / 2, c + r * 0.96 + w / 2, c + r * 0.96 + w / 2],
              outline=(212, 160, 0), width=w)
    bw = max(2, int(r * 0.022))
    d.ellipse([c - r * 0.86, c - r * 0.86, c + r * 0.86, c + r * 0.86], outline=(255, 236, 140), width=bw)
    # dots
    dr = r * 0.022
    for i in range(40):
        a = 2 * math.pi * i / 40
        x, y = c + math.cos(a) * r * 0.78, c + math.sin(a) * r * 0.78
        d.ellipse([x - dr, y - dr, x + dr, y + dr], fill=(255, 230, 128))
    off = max(2, r * 0.018)
    if face == "heads":
        draw_crown(d, c + off, c + off, r, (110, 74, 0, 170))
        draw_crown(d, c - off, c - off, r, (255, 246, 194, 204))
        draw_crown(d, c, c, r, (224, 164, 0))
    else:
        f = font("seguibl.ttf", int(r * 0.62))
        for dx, col in [(off, (110, 74, 0, 170)), (-off, (255, 246, 194, 204)), (0, (224, 164, 0))]:
            d.text((c + dx, c + dx), "111", font=f, fill=col, anchor="mm")
    return img.resize((size, size), Image.LANCZOS)


def shadow(w, h, alpha=150):
    """Blurred ellipse of size w x h, returned on a canvas padded by h on each side."""
    pad = h
    img = Image.new("RGBA", (w + 2 * pad, h + 2 * pad), (0, 0, 0, 0))
    ImageDraw.Draw(img).ellipse([pad, pad, pad + w, pad + h], fill=(0, 0, 0, alpha))
    return img.filter(ImageFilter.GaussianBlur(h / 4))


def launcher_icons():
    # Legacy square/round PNG icons (API < 26)
    sizes = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
    for dens, px in sizes.items():
        folder = os.path.join(RES, f"mipmap-{dens}")
        os.makedirs(folder, exist_ok=True)
        base = vgradient(px, px).convert("RGBA")
        cn = coin(int(px * 0.72))
        base.alpha_composite(cn, ((px - cn.width) // 2, (px - cn.height) // 2))
        sq_mask = Image.new("L", (px, px), 0)
        ImageDraw.Draw(sq_mask).rounded_rectangle([0, 0, px - 1, px - 1], radius=px * 0.18, fill=255)
        sq = base.copy()
        sq.putalpha(sq_mask)
        sq.save(os.path.join(folder, "ic_launcher.png"))
        rd_mask = Image.new("L", (px, px), 0)
        ImageDraw.Draw(rd_mask).ellipse([0, 0, px - 1, px - 1], fill=255)
        rd = base.copy()
        rd.putalpha(rd_mask)
        rd.save(os.path.join(folder, "ic_launcher_round.png"))
        # Adaptive foreground: 108dp canvas, art inside the 66dp safe zone
        fg_px = int(px * 108 / 48)
        fg = Image.new("RGBA", (fg_px, fg_px), (0, 0, 0, 0))
        cn = coin(int(fg_px * 0.56))
        fg.alpha_composite(cn, ((fg_px - cn.width) // 2, (fg_px - cn.height) // 2))
        fg.save(os.path.join(folder, "ic_launcher_foreground.png"))


def store_icon():
    px = 512
    base = vgradient(px, px).convert("RGBA")
    cn = coin(int(px * 0.74))
    base.alpha_composite(cn, ((px - cn.width) // 2, (px - cn.height) // 2))
    base.convert("RGB").save(os.path.join(STORE, "icon-512.png"))


def feature_graphic():
    w, h = 1024, 500
    img = vgradient(w, h).convert("RGBA")
    # coins
    sh = shadow(300, 46)
    img.alpha_composite(sh, (650 - 46, 410 - 46))
    c1 = coin(300, "heads")
    img.alpha_composite(c1, (650, 90))
    c2 = coin(170, "tails").rotate(-14, resample=Image.BICUBIC, expand=True)
    img.alpha_composite(c2, (880, 40))
    d = ImageDraw.Draw(img)
    f_big = font("seguibl.ttf", 104)
    d.text((70, 140), "good111", font=f_big, fill=(255, 255, 255))
    wgood = d.textlength("good111", font=f_big)
    d.text((70 + wgood, 140), "gg", font=f_big, fill=GOLD)
    f_sub = font("segoeuib.ttf", 44)
    d.text((74, 280), "Flip a coin. Heads or Tails?", font=f_sub, fill=MINT)
    img.convert("RGB").save(os.path.join(STORE, "feature-graphic-1024x500.png"))


def screenshot(name, face, result_text, color, heads_n, tails_n, flipping=False):
    """Approximation of the app screen at 1080x1920 (for a first listing; replace with
    real device screenshots when you can)."""
    w, h = 1080, 1920
    img = vgradient(w, h).convert("RGBA")
    d = ImageDraw.Draw(img)
    f_title = font("seguibl.ttf", 66)
    tw = d.textlength("good111gg", font=f_title)
    x0 = (w - tw) / 2
    d.text((x0, 120), "good111", font=f_title, fill=(255, 255, 255))
    d.text((x0 + d.textlength("good111", font=f_title), 120), "gg", font=f_title, fill=GOLD)
    # language pill
    d.rounded_rectangle([830, 118, 1040, 218], radius=50, outline=MINT, width=4)
    d.text((935, 168), "বাংলা", font=font("Nirmala.ttc", 38), fill=MINT, anchor="mm")
    # coin
    cd = 600
    cy = 820 if not flipping else 560
    sh_w = 560 if not flipping else 360
    img.alpha_composite(shadow(sh_w, 70), ((w - sh_w) // 2 - 70, 1150 - 70))
    cn = coin(cd, face)
    if flipping:
        cn = cn.resize((cd, int(cd * 0.42)), Image.LANCZOS)
    img.alpha_composite(cn, ((w - cn.width) // 2, cy - cn.height // 2))
    d = ImageDraw.Draw(img)
    if result_text:
        d.text((w / 2, 1340), result_text, font=font("seguibl.ttf", 130), fill=color, anchor="mm")
    d.text((w / 2, 1460), "Flipping…" if flipping else "Tap the coin to flip", font=font("segoeuib.ttf", 46),
           fill=(179, 217, 210), anchor="mm")
    # tally card
    layer = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    ImageDraw.Draw(layer).rounded_rectangle([54, 1580, 1026, 1820], radius=54, fill=(0, 0, 0, 60),
                                            outline=(35, 255, 200, 60), width=3)
    img.alpha_composite(layer)
    d = ImageDraw.Draw(img)
    cols = [(200, str(heads_n), "Heads", GOLD), (430, str(tails_n), "Tails", MINT),
            (660, str(heads_n + tails_n), "Total", (255, 255, 255))]
    for x, n, lab, col in cols:
        d.text((x, 1665), n, font=font("seguibl.ttf", 72), fill=col, anchor="mm")
        d.text((x, 1750), lab, font=font("segoeuib.ttf", 38), fill=(179, 217, 210), anchor="mm")
    d.rounded_rectangle([810, 1650, 990, 1750], radius=50, outline=MINT, width=4)
    d.text((900, 1700), "Reset", font=font("segoeuib.ttf", 38), fill=MINT, anchor="mm")
    img.convert("RGB").save(os.path.join(STORE, name))


if __name__ == "__main__":
    os.makedirs(STORE, exist_ok=True)
    launcher_icons()
    store_icon()
    feature_graphic()
    # Store screenshots are real captures from the app; screenshot() is kept only as a fallback.
    print("assets written")
