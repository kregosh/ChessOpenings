// The app is mostly WebAssembly (Compose's Skia and our code) and can't get under webpack's
// 244 KiB "recommended size", so its size hints are noise.
config.performance = { hints: false };
