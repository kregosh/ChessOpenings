"""Generates chess/src/test/resources/random-games.txt with python-chess as the oracle.

Each line is one position of a random game:
    FEN | Polyglot key (hex) | sorted SAN of every legal move | UCI of the move played next

Run with python-chess 1.11 on the path:
    python3 tools/oracle/generate_fixture.py
"""
import random
import chess
import chess.polyglot

GAMES = 40
MAX_PLIES = 160
random.seed(20261011)

lines = []
for _ in range(GAMES):
    board = chess.Board()
    while not board.is_game_over(claim_draw=False) and board.ply() < MAX_PLIES:
        moves = list(board.legal_moves)
        # Prefer captures, promotions and castling a little so the games reach special moves.
        special = [m for m in moves if board.is_capture(m) or m.promotion or board.is_castling(m)]
        move = random.choice(special if special and random.random() < 0.4 else moves)
        sans = sorted(board.san(m) for m in moves)
        lines.append(f"{board.fen(en_passant='fen')}|{chess.polyglot.zobrist_hash(board):016x}|{' '.join(sans)}|{move.uci()}")
        board.push(move)
    sans = sorted(board.san(m) for m in board.legal_moves)
    lines.append(f"{board.fen(en_passant='fen')}|{chess.polyglot.zobrist_hash(board):016x}|{' '.join(sans)}|-")

with open("chess/src/test/resources/random-games.txt", "w") as f:
    f.write("\n".join(lines) + "\n")
print(len(lines), "positions")
