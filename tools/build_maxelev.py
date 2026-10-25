#!/usr/bin/env python3
"""
国土数値情報「標高・傾斜度5次メッシュ（G04-d）」から最高標高だけを抜き出し、
NavHUD 用のコンパクトなバイナリ（maxelev.bin）を作る。

使い方:
    pip install pyshp
    python build_maxelev.py <G04-dのZIPを置いたフォルダ> maxelev.bin

フォルダ内の G04-d-*_GML.zip をすべて読み込む（ZIPは展開不要）。

出力形式（すべてリトルエンディアン）:
    ヘッダ   : magic "MXEL"(4) / version u16 (=1) / ブロック数 N u16 / 刻み STEP u16 [m]
    索引     : N 個 × { 1次メッシュコード u16, オフセット u32, 長さ u32 }
               （コード昇順。オフセットはファイル先頭からのバイト位置）
    ブロック : 1次メッシュ1つ分を zlib 圧縮したもの
               展開すると 320×320 の int16（行は南→北、列は西→東）
               値 × STEP = その5次メッシュ（約250m）の最高標高 [m]（STEP 単位に切り上げ）
               データなし（海など）= 0、元データが "unknown" = -32768
"""
import io, math, struct, sys, zipfile, zlib
from pathlib import Path

import shapefile  # pyshp

N = 320            # 1次メッシュ1辺あたりの5次メッシュ数
UNKNOWN = -32768
STEP = 10          # 標高の刻み [m]。1 にすると精度は上がるが容量は約2倍
FIELD_CODE = "G04d_001"  # 5次メッシュコード
FIELD_MAX = "G04d_003"   # 最高標高（002=平均、004=最低）


def rowcol(code: str):
    """5次メッシュコード(10桁) → 1次メッシュ内の (行, 列)。行は南から、列は西から。"""
    r2, c2 = int(code[4]), int(code[5])   # 2次: 8分割
    r3, c3 = int(code[6]), int(code[7])   # 3次: 10分割
    q4, q5 = int(code[8]) - 1, int(code[9]) - 1  # 4次・5次: 1=南西 2=南東 3=北西 4=北東
    if not (r2 < 8 and c2 < 8 and 0 <= q4 < 4 and 0 <= q5 < 4):
        raise ValueError(f"メッシュコードが不正: {code}")
    row = r2 * 40 + r3 * 4 + (q4 // 2) * 2 + (q5 // 2)
    col = c2 * 40 + c3 * 4 + (q4 % 2) * 2 + (q5 % 2)
    return row, col


def read_zip(path: Path):
    with zipfile.ZipFile(path) as z:
        names = z.namelist()
        base = next(n[:-4] for n in names if n.endswith("FifthMesh.shp"))
        files = {ext: io.BytesIO(z.read(base + ext)) for ext in (".shp", ".shx", ".dbf")}
    reader = shapefile.Reader(shp=files[".shp"], shx=files[".shx"], dbf=files[".dbf"], encoding="cp932")
    names = [f[0] for f in reader.fields[1:]]          # 先頭の DeletionFlag を除く
    i_code, i_max = names.index(FIELD_CODE), names.index(FIELD_MAX)
    grid = [0] * (N * N)
    code1 = None
    count = unknown = 0
    for rec in reader.iterRecords():
        code, v = rec[i_code], rec[i_max]
        code1 = code1 or code[:4]
        if code[:4] != code1:
            raise ValueError(f"{path.name}: 別の1次メッシュのレコードが混ざっている ({code})")
        r, c = rowcol(code)
        if v == "unknown":
            grid[r * N + c] = UNKNOWN
            unknown += 1
        else:
            q = math.ceil(float(v) / STEP)
            if not (-32767 <= q <= 32767):
                raise ValueError(f"{path.name}: 標高が範囲外 ({code}: {v})")
            grid[r * N + c] = q
        count += 1
    return int(code1), grid, count, unknown


def main(src_dir: str, out_path: str):
    blocks = []
    for zp in sorted(Path(src_dir).glob("G04-d-*_GML.zip")):
        code1, grid, count, unknown = read_zip(zp)
        data = zlib.compress(struct.pack(f"<{N*N}h", *grid), 9)
        blocks.append((code1, data))
        print(f"{zp.name}: 1次メッシュ {code1}, {count} メッシュ（unknown {unknown}）, 圧縮後 {len(data)/1024:.0f} KB")
    blocks.sort()
    codes = [c for c, _ in blocks]
    dup = sorted({c for c in codes if codes.count(c) > 1})
    if dup:
        sys.exit(f"同じ1次メッシュの ZIP が2つ以上ある: {dup}")
    header = struct.pack("<4sHHH", b"MXEL", 1, len(blocks), STEP)
    offset = len(header) + 10 * len(blocks)
    index, body = b"", b""
    for code1, data in blocks:
        index += struct.pack("<HII", code1, offset + len(body), len(data))
        body += data
    Path(out_path).write_bytes(header + index + body)
    print(f"{out_path}: {len(blocks)} ブロック, {Path(out_path).stat().st_size/1024/1024:.2f} MB")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    main(sys.argv[1], sys.argv[2])
