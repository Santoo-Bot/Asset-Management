"""Label aset ukuran cetak 50 × 20 mm: simbol QR, teks kode QR (1 baris), kategori (maks. 2 baris).

Tata letak sama dengan LabelRenderer di Android. PDF berisi satu label per halaman 50 × 20 mm dengan QR vektor
(tajam saat dicetak); PNG dirender dengan resolusi tinggi untuk printer label.
"""

from __future__ import annotations

import os
from dataclasses import dataclass
from pathlib import Path
from typing import Callable

MM = 72 / 25.4  # point per mm
LABEL_W_MM = 50
LABEL_H_MM = 20
W = LABEL_W_MM * MM
H = LABEL_H_MM * MM
PAD = 1.5 * MM
QR_SIZE = H - 2 * PAD
TEXT_LEFT = PAD + QR_SIZE + 1.5 * MM
TEXT_WIDTH = W - PAD - TEXT_LEFT
CODE_MAX_PT = 8.0
CODE_MIN_PT = 4.0
CAT_PT = 6.5
GAP = 1.5
LINE_FACTOR = 1.17  # tinggi baris ≈ ascent + descent


@dataclass(frozen=True)
class LabelData:
    code: str
    category: str
    qr: str  # isi QR (kode QR khusus atau kode aset); dicetak juga sebagai teks


def qr_matrix(content: str) -> list[list[bool]]:
    """Matriks QR (tanpa margin), koreksi galat tingkat M."""
    from reportlab.graphics.barcode.qrencoder import QRCode, QRErrorCorrectLevel

    q = QRCode(None, QRErrorCorrectLevel.M)
    q.addData(content)
    q.make()
    n = q.getModuleCount()
    return [[bool(q.isDark(r, c)) for c in range(n)] for r in range(n)]


@dataclass
class _Layout:
    code_text: str
    code_pt: float
    cat_lines: list[str]
    code_top: float  # dari atas label, point
    cat_top: float


def _ellipsize(text: str, width: float, measure: Callable[[str], float]) -> str:
    if measure(text) <= width:
        return text
    while text and measure(text + "…") > width:
        text = text[:-1]
    return text + "…"


def _wrap(text: str, width: float, measure: Callable[[str], float], max_lines: int) -> list[str]:
    words = text.split()
    lines: list[str] = []
    cur = ""
    i = 0
    while i < len(words):
        w = words[i]
        trial = f"{cur} {w}".strip()
        if measure(trial) <= width or not cur:
            if not cur and measure(w) > width:
                # Kata terlalu panjang: potong per karakter.
                part = ""
                for ch in w:
                    if measure(part + ch) > width and part:
                        break
                    part += ch
                lines.append(part)
                words[i] = w[len(part):]
                if not words[i]:
                    i += 1
                if len(lines) == max_lines:
                    break
                continue
            cur = trial
            i += 1
        else:
            lines.append(cur)
            cur = ""
            if len(lines) == max_lines:
                break
    if cur and len(lines) < max_lines:
        lines.append(cur)
        cur = ""
    if (i < len(words) or cur) and lines:
        lines[-1] = _ellipsize(lines[-1] + " " + " ".join(words[i:]), width, measure)
    return lines[:max_lines]


def _layout(label: LabelData, code_width: Callable[[str, float], float], cat_width: Callable[[str], float]) -> _Layout:
    size = CODE_MAX_PT
    while size > CODE_MIN_PT and code_width(label.qr, size) > TEXT_WIDTH:
        size -= 0.25
    code = _ellipsize(label.qr, TEXT_WIDTH, lambda t: code_width(t, size))
    cat = _wrap(label.category or "", TEXT_WIDTH, cat_width, 2)
    code_h = size * LINE_FACTOR
    cat_h = len(cat) * CAT_PT * LINE_FACTOR
    block = code_h + (GAP + cat_h if cat else 0)
    top = (H - block) / 2
    return _Layout(code, size, cat, top, top + code_h + GAP)


# ------------------------------------------------------------------ PDF (vektor)
def write_pdf(labels: list[LabelData], path: str | os.PathLike) -> None:
    from reportlab.pdfbase.pdfmetrics import stringWidth
    from reportlab.pdfgen.canvas import Canvas

    c = Canvas(str(path), pagesize=(W, H))
    c.setTitle("Label aset")
    for lb in labels:
        m = qr_matrix(lb.qr)
        n = len(m)
        mod = QR_SIZE / n
        c.setFillColorRGB(0, 0, 0)
        for r, row in enumerate(m):
            x = 0
            while x < n:
                if row[x]:
                    start = x
                    while x < n and row[x]:
                        x += 1
                    # Koordinat PDF dari bawah.
                    c.rect(PAD + start * mod, H - PAD - (r + 1) * mod, (x - start) * mod, mod, stroke=0, fill=1)
                else:
                    x += 1
        lay = _layout(lb, lambda t, s: stringWidth(t, "Helvetica-Bold", s), lambda t: stringWidth(t, "Helvetica", CAT_PT))
        c.setFont("Helvetica-Bold", lay.code_pt)
        c.drawString(TEXT_LEFT, H - lay.code_top - lay.code_pt * 0.93, lay.code_text)
        c.setFillColorRGB(60 / 255, 60 / 255, 60 / 255)
        c.setFont("Helvetica", CAT_PT)
        for i, line in enumerate(lay.cat_lines):
            c.drawString(TEXT_LEFT, H - lay.cat_top - CAT_PT * 0.93 - i * CAT_PT * LINE_FACTOR, line)
        c.showPage()
    c.save()


# ------------------------------------------------------------------ PNG
_FONT_CANDIDATES = {
    True: ["arialbd.ttf", "Arial Bold.ttf", "DejaVuSans-Bold.ttf", "LiberationSans-Bold.ttf"],
    False: ["arial.ttf", "Arial.ttf", "DejaVuSans.ttf", "LiberationSans-Regular.ttf"],
}


def _font(bold: bool, px: float):
    from PIL import ImageFont

    for name in _FONT_CANDIDATES[bold]:
        try:
            return ImageFont.truetype(name, max(1, round(px)))
        except OSError:
            continue
    return ImageFont.load_default(size=max(1, round(px)))


def render_png(label: LabelData, px_per_mm: float = 12.0):
    """Gambar satu label (default 12 px/mm ≈ 305 dpi)."""
    from PIL import Image, ImageDraw

    s = px_per_mm / MM  # px per point
    img = Image.new("RGB", (round(LABEL_W_MM * px_per_mm), round(LABEL_H_MM * px_per_mm)), "white")
    d = ImageDraw.Draw(img)
    m = qr_matrix(label.qr)
    n = len(m)
    mod = QR_SIZE * s / n
    x0 = y0 = PAD * s
    for r, row in enumerate(m):
        for col, dark in enumerate(row):
            if dark:
                d.rectangle([x0 + col * mod, y0 + r * mod, x0 + (col + 1) * mod - 1e-6, y0 + (r + 1) * mod - 1e-6], fill="black")
    cache: dict[float, object] = {}

    def bold(size_pt: float):
        if size_pt not in cache:
            cache[size_pt] = _font(True, size_pt * s)
        return cache[size_pt]

    regular = _font(False, CAT_PT * s)
    lay = _layout(label, lambda t, sz: bold(sz).getlength(t) / s, lambda t: regular.getlength(t) / s)
    d.text((TEXT_LEFT * s, lay.code_top * s), lay.code_text, fill="black", font=bold(lay.code_pt))
    for i, line in enumerate(lay.cat_lines):
        d.text((TEXT_LEFT * s, (lay.cat_top + i * CAT_PT * LINE_FACTOR) * s), line, fill=(60, 60, 60), font=regular)
    return img


def safe_file_name(text: str) -> str:
    return "".join(ch if ch.isalnum() or ch in "-_." else "_" for ch in text)[:60] or "label"


def write_pngs(labels: list[LabelData], folder: str | os.PathLike, stamp: str) -> list[Path]:
    out = []
    for i, lb in enumerate(labels, start=1):
        p = Path(folder) / f"label-{safe_file_name(lb.code)}-{stamp}-{i}.png"
        render_png(lb).save(p, dpi=(305, 305))
        out.append(p)
    return out
