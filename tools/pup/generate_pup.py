"""Draws the pixel-art puppy and its little icons, and writes them as Kotlin for the UI.

The pup is a light-brown whelp with a blue collar, 32 × 32 pixels, built from one
symmetric sitting pose (or a lying one) plus face, tail and prop layers per frame.

    python3 tools/pup/generate_pup.py              # writes PupSprites.kt
    python3 tools/pup/generate_pup.py preview.png  # also renders every frame to a PNG
"""
import pathlib
import sys

HERE = pathlib.Path(__file__).parent
OUT = HERE.parent.parent / "ui/src/main/kotlin/com/kregosh/chessopenings/ui/pet/PupSprites.kt"

PALETTE = {
    "K": 0x4A2E1A,  # outline
    "B": 0xC8935A,  # fur
    "L": 0xECCCA0,  # muzzle, belly, paws
    "D": 0x966236,  # ears
    "F": 0xB0683C,  # kibble
    "E": 0x1A1A1A,  # eyes
    "W": 0xFFFFFF,  # eye shine
    "N": 0x281A12,  # nose
    "P": 0xE87A8A,  # tongue, cheeks
    "C": 0x2F6FD0,  # collar, bowl
    "c": 0x78AAF0,  # collar shine
    "b": 0x508CDC,  # bowl rim
    "G": 0xF2C230,  # tag
    "S": 0x96C8F5,  # sweat drop
    "R": 0xE0455A,  # heart
    "r": 0xF59AA8,  # heart shine
    "O": 0xF08A24,  # flame
    "Y": 0xFFD23C,  # flame core
    "w": 0xF6EFE2,  # bone
}

SIZE = 32

# The left half of the sitting pup; the right half is its mirror image.
HALF = [
    "................",
    "................",
    "................",
    ".........KKKKKKK",
    ".......KKBBBBBBB",
    "......KBBBBBBBBB",
    "...KKKBBBBBBBBBB",
    "..KDDDKBBBBBBBBB",
    ".KDDDDDKBBBBBBBB",
    ".KDDDDDKBBBBBBBB",
    ".KDDDDDKBBBBBBBB",
    ".KDDDDDKBBBBBBBB",
    ".KDDDDKBBBBBBLLL",
    "..KDDDKBBBBBLLLL",
    "..KDDKKBBBBLLLLL",
    "...KK.KBBBBLLLLL",
    "......KBBBBBLLLL",
    ".......KKBBBBBBB",
    "........KCCCCCCC",
    "........KCCCCCCC",
    ".......KBBBBBBBB",
    "......KBBBBBBLLL",
    "......KBBBBBLLLL",
    ".....KBBBBBBLLLL",
    "....KBBBKBBBLLLL",
    "....KBBBKBBBLLLL",
    "....KBBBKBBLLLLL",
    "....KBBKLLLLKLLL",
    "....KBBKLLLLKLLL",
    ".....KKKKKKKKKKK",
    "................",
    "................",
]
SITTING = [row + row[::-1] for row in HALF]

LOAF = [
    "      KKKKKKKKKKKKKKKKKKKK      ",
    "    KKBBBBBBBBBBBBBBBBBBBBKK    ",
    "   KBBBBBBBBBBBBBBBBBBBBBBBBK   ",
    "  KBBBBBBBBBBBBBBBBBBBBBBBBBBK  ",
    "  KBBBBBBBBBBBBBBBBBBBBBBBBBBKK ",
    "  KBBBKLLLLKBBBBBBBBKLLLLKBBBKBK",
    "   KKKKLLLLKKKKKKKKKKLLLLKKKKKBK",
    "       KKKK          KKKK    KK ",
]

TAIL_UP = ["    KK ", "   KBBK", "  KBBK ", " KBBK  ", "KBBK   ", "BBK    "]
TAIL_SIDE = ["   KKK ", " KKBBBK", "KBBBKK ", "BBKK   "]
BOWL_FULL = ["  FFKFFFFKFF  ", " KFFFFFFFFFFK ", "KbbbbbbbbbbbbK",
             "KCCCCCCCCCCCCK", " KCCCCCCCCCCK ", "  KKKKKKKKKK  "]
BOWL_EMPTY = ["KKKKKKKKKKKKKK", "KCCCCCCCCCCCCK", " KCCCCCCCCCCK ", "  KKKKKKKKKK  "]
PAW = [" KKKK ", "KLKLKK", "KLLLLK", "KLLLLK", "KLLLLK", " KKKK "]
DROP = [" S ", "SSS", "SSS", " S "]


def paint(grid, rows, x0, y0):
    g = [list(r) for r in grid]
    for dy, row in enumerate(rows):
        for dx, ch in enumerate(row):
            if ch not in " ." and 0 <= y0 + dy < len(g) and 0 <= x0 + dx < len(g[0]):
                g[y0 + dy][x0 + dx] = ch
    return ["".join(r) for r in g]


def dots(grid, points):
    g = [list(r) for r in grid]
    for x, y, ch in points:
        g[y][x] = ch
    return ["".join(r) for r in g]


# Faces: the eyes sit at x 10-11 and 20-21, the nose at y 12.
def eyes_open(g):
    pts = []
    for x in (10, 20):
        pts += [(x + dx, 9 + dy, "E") for dx in range(2) for dy in range(3)] + [(x, 9, "W")]
    return dots(g, pts)


def eyes_closed(g):
    return dots(g, [(x + dx, 11, "K") for x in (10, 20) for dx in (-1, 0, 1, 2)])


def eyes_happy(g):  # ^ ^
    return dots(g, [p for x in (10, 20) for p in ((x - 1, 11, "K"), (x, 10, "K"), (x + 1, 10, "K"), (x + 2, 11, "K"))])


def eyes_sad(g):
    pts = []
    for x in (10, 20):
        pts += [(x + dx, 10 + dy, "E") for dx in range(2) for dy in range(2)] + [(x, 10, "W")]
    pts += [(9, 8, "K"), (10, 8, "K"), (11, 7, "K"), (22, 8, "K"), (21, 8, "K"), (20, 7, "K")]
    return dots(g, pts)


def eyes_lidded(g):
    return dots(g, [p for x in (10, 20) for p in
                    [(x + dx, 10, "K") for dx in (-1, 0, 1, 2)] + [(x, 11, "E"), (x + 1, 11, "E")]])


NOSE = [(14, 12, "N"), (15, 12, "N"), (16, 12, "N"), (17, 12, "N"), (15, 13, "N"), (16, 13, "N")]


def mouth(g, kind):
    pts = list(NOSE)
    if kind == "smile":
        pts += [(15, 14, "K"), (16, 14, "K"), (14, 15, "K"), (17, 15, "K")]
    elif kind == "open":
        pts += [(15, 14, "K"), (16, 14, "K"), (14, 15, "K"), (15, 15, "P"), (16, 15, "P"), (17, 15, "K"),
                (15, 16, "P"), (16, 16, "P")]
    elif kind == "frown":
        pts += [(15, 14, "K"), (16, 14, "K"), (14, 15, "K"), (17, 15, "K"), (13, 16, "K"), (18, 16, "K")]
    elif kind == "flat":
        pts += [(14, 15, "K"), (15, 15, "K"), (16, 15, "K"), (17, 15, "K")]
    return dots(g, pts)


def cheeks(g):
    return dots(g, [(8, 13, "P"), (9, 13, "P"), (22, 13, "P"), (23, 13, "P")])


def collar_tag(g):
    return dots(g, [(15, 19, "G"), (16, 19, "G"), (15, 20, "G"), (16, 20, "G"), (12, 18, "c"), (13, 18, "c")])


def sitting(eyes, mouth_kind, tail=None, blush=True, bowl=None, paws=False, sweat=False):
    g = eyes(SITTING) if eyes else SITTING
    g = mouth(g, mouth_kind)
    if blush:
        g = cheeks(g)
    g = collar_tag(g)
    if tail == "up":
        g = paint(g, TAIL_UP, 24, 16)
    elif tail == "side":
        g = paint(g, TAIL_SIDE, 24, 18)
    if bowl:
        g = paint(g, bowl, 9, 24 if bowl is BOWL_FULL else 26)
    if paws:
        g = paint(paint(g, PAW, 7, 8), PAW, 19, 8)
    if sweat:
        g = paint(g, DROP, 25, 3)
    return g


def lying(eyes, mouth_kind, blush=True, sweat=False):
    face = mouth(eyes(SITTING), mouth_kind)
    if blush:
        face = cheeks(face)
    g = paint(["." * SIZE] * SIZE, LOAF, 0, 23)
    g = paint(g, face[3:18], 0, 9)
    if sweat:
        g = paint(g, DROP, 25, 8)
    return g


FRAMES = {
    "IDLE": sitting(eyes_open, "smile", tail="up"),
    "WAG": sitting(eyes_open, "smile", tail="side"),
    "BLINK": sitting(eyes_closed, "smile", tail="up"),
    "HAPPY": sitting(eyes_happy, "open", tail="side"),
    "HAPPY_UP": sitting(eyes_happy, "open", tail="up"),
    "SAD": sitting(eyes_sad, "frown", blush=False),
    "HUNGRY": sitting(eyes_sad, "open", blush=False, bowl=BOWL_EMPTY),
    "EAT": sitting(eyes_closed, "open", tail="up", bowl=BOWL_FULL),
    "CHEW": sitting(eyes_happy, "smile", tail="side", bowl=BOWL_FULL),
    "COVER": sitting(None, "frown", blush=False, paws=True),
    "WORRIED": sitting(eyes_open, "flat", blush=False, sweat=True),
    "BORED": sitting(eyes_lidded, "flat", blush=False),
    "SLEEP": lying(eyes_closed, "smile"),
    "SICK": lying(eyes_sad, "frown", blush=False, sweat=True),
}

ICONS = {
    "HEART": [".RR.RR.", "RrRRRRR", "RRRRRRR", ".RRRRR.", "..RRR..", "...R..."],
    "BONE": [".KK...KK.", "KwwKKKwwK", ".KwwwwwK.", "KwwKKKwwK", ".KK...KK."],
    "FLAME": ["...O...", "..OO...", "..OOO.O", ".OOYOOO", "OOYYYOO", "OOYYYOO", ".OYYYO.", "..OOO.."],
    "ZZZ": ["KKKK", "..K.", ".K..", "KKKK"],
}


def kotlin():
    lines = [
        "// Generated by tools/pup/generate_pup.py. Do not edit by hand.",
        "package com.kregosh.chessopenings.ui.pet",
        "",
        "/** The sprite colours, by the character the frames use for them ('.' is transparent). */",
        "internal val PUP_PALETTE: Map<Char, Long> = mapOf(",
    ]
    lines += [f"    '{k}' to 0xFF{v:06X}," for k, v in PALETTE.items()]
    lines += [")", "", "/** The pup's frames, 32 × 32 pixels each. */", "internal enum class PupFrame(val rows: List<String>) {"]
    for name, rows in FRAMES.items():
        assert len(rows) == SIZE and all(len(r) == SIZE for r in rows), name
        lines.append(f"    {name}(")
        lines.append("        listOf(")
        lines += [f'            "{r}",' for r in rows]
        lines.append("        ),")
        lines.append("    ),")
    lines[-1] = "    ),"
    lines += ["}", "", "/** Small pixel icons for the pup's card. */", "internal enum class PupIcon(val rows: List<String>) {"]
    for name, rows in ICONS.items():
        lines.append(f"    {name}(listOf(" + ", ".join(f'"{r}"' for r in rows) + ")),")
    lines += ["}", ""]
    return "\n".join(lines)


def preview(path):
    from PIL import Image
    scale = 8
    sprites = list(FRAMES.values()) + list(ICONS.values())
    width = sum((len(s[0]) + 2) * scale for s in sprites)
    img = Image.new("RGB", (width, SIZE * scale), (230, 238, 248))
    x0 = 0
    for rows in sprites:
        for y, row in enumerate(rows):
            for x, ch in enumerate(row):
                if ch in PALETTE:
                    v = PALETTE[ch]
                    img.paste((v >> 16, (v >> 8) & 255, v & 255), (x0 + x * scale, y * scale, x0 + (x + 1) * scale, (y + 1) * scale))
        x0 += (len(rows[0]) + 2) * scale
    img.save(path)


if __name__ == "__main__":
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(kotlin())
    if len(sys.argv) > 1:
        preview(sys.argv[1])
