"""A fake UCI engine with canned answers, for testing UciEngine's parsing.

Full search: best score 35 for e2e4 (after a lower-bound line that must be ignored).
Restricted search: a fixed score per searched move: e2e4 35, d2d4 15, g2g4 mated in 3,
anything else -5.
"""
import sys

searchmoves = []
for line in sys.stdin:
    cmd = line.strip()
    if cmd == "uci":
        print("id name FakeFish\nuciok", flush=True)
    elif cmd == "isready":
        print("readyok", flush=True)
    elif cmd.startswith("go"):
        parts = cmd.split()
        searchmoves = parts[parts.index("searchmoves") + 1:] if "searchmoves" in parts else []
        if not searchmoves:
            print("info depth 5 seldepth 7 multipv 1 score cp 80 lowerbound nodes 10 pv d2d4")
            print("info depth 5 seldepth 7 multipv 1 score cp 35 nodes 99 pv e2e4 e7e5")
            print("bestmove e2e4", flush=True)
        else:
            for i, m in enumerate(searchmoves):
                score = {"e2e4": "cp 35", "d2d4": "cp 15", "g2g4": "mate -3"}.get(m, "cp -5")
                print(f"info depth 5 multipv {i + 1} score {score} nodes 50 pv {m} e7e5")
            print(f"bestmove {searchmoves[0]}", flush=True)
    elif cmd == "ucinewgame":
        pass
    elif cmd == "quit":
        break
