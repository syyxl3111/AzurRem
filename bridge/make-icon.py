#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""从 image\\长门大头2.png 生成 exe / 窗口用的多尺寸 azurrem.ico。

为什么单独放一个脚本：图标是构建产物，不该手动塞二进制进仓库；
想换图标只要换源图再跑一次这个脚本（build-exe.bat 会用到 bridge\\azurrem.ico）。

用法：
    D:\\Tools\\Python\\python.exe bridge\\make-icon.py
"""

from __future__ import annotations

import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
SOURCE = HERE.parent / "image" / "长门大头2.png"
OUTPUT = HERE / "azurrem.ico"
SIZES = [16, 24, 32, 48, 64, 128, 256]


def main() -> int:
    try:
        from PIL import Image
    except ImportError:
        print("[X] 没装 Pillow，装一个：python -m pip install pillow")
        return 2

    if not SOURCE.is_file():
        print(f"[X] 找不到源图：{SOURCE}")
        return 2

    image = Image.open(SOURCE).convert("RGBA")
    # 居中裁成正方形：非等比缩放会把脸压扁
    side = min(image.size)
    left = (image.width - side) // 2
    top = (image.height - side) // 2
    image = image.crop((left, top, left + side, top + side))

    image.save(OUTPUT, format="ICO", sizes=[(size, size) for size in SIZES])
    print(f"[OK] {OUTPUT}  ({OUTPUT.stat().st_size} bytes, sizes={SIZES})")
    print(f"     源图 {SOURCE.name} {image.size[0]}x{image.size[1]} → 居中裁剪成正方形")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
