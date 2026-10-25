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
| `ui` (shared Compose screens) | Setup, training and report screens; board with drag and tap |
| `web` (browser app) | Runs: Kotlin/Wasm, Stockfish in a Web Worker; deployed to GitHub Pages from `main` |
| `app` (Android) | Runs: same screens, Stockfish.js in a hidden WebView; debug APK from CI |

## Building and testing

Requires JDK 17+.

```bash
./gradlew test                                  # unit tests for every module
./gradlew -p web wasmJsBrowserDevelopmentRun    # the browser app at http://localhost:8080
./gradlew -p web wasmJsBrowserDistribution      # static site in web/build/dist/wasmJs/productionExecutable
./gradlew -p app assembleDebug                  # Android APK in app/build/outputs/apk/debug (needs the Android SDK)
```

Every CI run also builds the Android app and uploads a debug APK
(`opening-trainer-apk`) that can be installed on a phone.

The browser app needs WebAssembly GC: current Chrome, Firefox, Edge, and Safari 18.2+.

`chess` is tested with perft node counts for the standard test positions, the
Polyglot key test vectors, notation edge cases, and about 6,000 positions from random
games recorded with python-chess (`tools/oracle/generate_fixture.py`).

## Credits

- Opening statistics: [Lichess open database](https://database.lichess.org) (CC0); opening names: [lichess-org/chess-openings](https://github.com/lichess-org/chess-openings) (CC0).
- Engine: [Stockfish](https://stockfishchess.org) via [Stockfish.js](https://github.com/nmrugg/stockfish.js) (GPL-3.0), shipped unmodified as a separate file.
- Pieces: cburnett set by Colin M.L. Burnett, via [lichess-org/lila](https://github.com/lichess-org/lila) (GPLv2+).
