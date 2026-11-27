"""Foto aset di folder `photos` di dalam folder data (path relatif di database, sama dengan Android)."""

from __future__ import annotations

import os
import uuid
from pathlib import Path

DIR = "photos"
MAX_SIDE = 1280
QUALITY = 80


class PhotoStorage:
    def __init__(self, data_dir: str | os.PathLike):
        self.root = Path(data_dir)

    @property
    def photo_dir(self) -> Path:
        d = self.root / DIR
        d.mkdir(parents=True, exist_ok=True)
        return d

    def resolve(self, relative: str | None) -> Path | None:
        """File foto dari path relatif, atau None bila tidak ada / keluar dari folder foto (path dimanipulasi)."""
        if not relative:
            return None
        f = (self.root / relative).resolve()
        base = self.photo_dir.resolve()
        if base not in f.parents or not f.is_file():
            return None
        return f

    def import_compressed(self, source: str | os.PathLike) -> str:
        """Kompres gambar (sisi terpanjang maks. 1280 px, JPEG 80, orientasi EXIF diterapkan). Mengembalikan path relatif."""
        from PIL import Image, ImageOps

        with Image.open(source) as img:
            img = ImageOps.exif_transpose(img)
            if img.mode not in ("RGB", "L"):
                img = img.convert("RGB")
            img.thumbnail((MAX_SIDE, MAX_SIDE))
            name = f"{uuid.uuid4()}.jpg"
            img.save(self.photo_dir / name, "JPEG", quality=QUALITY)
        return f"{DIR}/{name}"

    def delete(self, relative: str | None) -> None:
        f = self.resolve(relative)
        if f:
            try:
                f.unlink()
            except OSError:
                pass

    def delete_unreferenced(self, referenced: set[str]) -> int:
        """Hapus file foto yang tidak lagi dirujuk data mana pun."""
        n = 0
        for f in self.photo_dir.iterdir():
            if f.is_file() and f"{DIR}/{f.name}" not in referenced:
                try:
                    f.unlink()
                    n += 1
                except OSError:
                    pass
        return n


def referenced_photos(db) -> set[str]:
    rows = db.q("SELECT photoPath FROM assets WHERE photoPath IS NOT NULL UNION SELECT photoPath FROM service_records WHERE photoPath IS NOT NULL")
    return {r[0] for r in rows}
