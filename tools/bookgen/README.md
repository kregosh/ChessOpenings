# bookgen

Builds the opening book (`data/book.bin`) from Lichess game data. See
[docs/ARCHITECTURE.md](../../docs/ARCHITECTURE.md), section 4.

```bash
./gradlew :bookgen:installDist
B=tools/bookgen/build/install/bookgen/bin/bookgen

# 1. Count moves per position (streams; stops after --max-games)
curl -sSL https://database.lichess.org/standard/lichess_db_standard_rated_2026-08.pgn.zst \
  | zstd -dc | $B count --out counts.txt --max-games 3000000

# 2. Apply thresholds, evaluate with Stockfish, write the book
$B build --counts counts.txt --openings data/openings --out data/book.bin \
  --stockfish /usr/games/stockfish --depth 16

# 3. Look at the result
$B dump --book data/book.bin --line "e4 c5"
```

`$B --help` lists every option.

For big runs the engine work can be split: `$B evaluate --shard I --shards N --out evals-I.txt`
(same counts and thresholds in every shard), then `$B build --evals evals-0.txt,…` instead
of `--stockfish`.

The **Build opening book** GitHub Actions workflow does exactly that: one counting job per
month, then N parallel evaluation jobs, then a build job that pushes the book to a
`book/<run>` branch.

## What goes in

| Band | Games | Decides |
|---|---|---|
| strong | both players 2000+ | user book moves: ≥5% of a position's strong games (100+ games), losing ≤40 cp |
| club | average 1200–1800 | bot candidates: ≥3% of a position's club games (200+ games), losing ≤80 cp |

Bullet (under 3 minutes), unrated, variant and custom-position games are skipped.

## Notes

- Counting is lossy past `--max-positions` (2M): rare positions are dropped, popular
  ones keep exact counts. About 2,000 games per second on one core.
- Losses come from one Stockfish search per position that scores the engine's best move
  together with the candidate moves; separate searches disagree by search noise.
- Counts files from several runs can be merged: `--counts a.txt,b.txt`.
