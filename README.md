# Chess Opening Trainer

A free app for beginners and low-intermediate players to learn how to continue an
opening against the replies they will actually meet. Android app and web app from
one Kotlin codebase.

Design: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Status

| Module | State |
|---|---|
| `chess` (rules engine) | Done: move generation, FEN, SAN, UCI, Polyglot keys |
| `core` | Done: book format, training session logic (bot, grading, feedback, undo), setup catalog |
| `tools/bookgen` (opening book from Lichess games) | Done, first real book pending (GitHub Actions) |
| `ui`, `app`, `web` | Not started |

## Building and testing

Requires JDK 17+.

```bash
./gradlew test        # unit tests for every module
```

`chess` is tested with perft node counts for the standard test positions, the
Polyglot key test vectors, notation edge cases, and about 6,000 positions from random
games recorded with python-chess (`tools/oracle/generate_fixture.py`).
