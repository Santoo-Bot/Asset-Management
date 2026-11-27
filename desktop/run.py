"""Jalankan Aset GA Desktop dari kode sumber: `python run.py`. Juga titik masuk build PyInstaller."""

import sys

from asetga.app import main

if __name__ == "__main__":
    sys.exit(main())
