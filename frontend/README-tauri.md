# Tauri desktop preview (Chromium/WebView2)

This is additive (JavaFX kept). Steps to try the smoother desktop shell:

## Prereqs
- Node.js 18+ (already installed)
- Rust toolchain + MSVC build tools (for Tauri)
- WebView2 runtime (installed by Edge on Windows 10/11; Tauri will prompt if missing)

## Install deps
```powershell
cd frontend
npm install
```

## Run dev (uses Vite dev server in Chromium shell)
```powershell
npm run tauri:dev
```

## Build installer
```powershell
npm run tauri:build
```
Output will be under `frontend/src-tauri/target/release/bundle/` (NSIS installer).

## Backend
Currently this shell only loads the Vue UI. Point your API calls to your existing backend (e.g., localhost:8080). If you want to auto-spawn the Java backend, we can add a Tauri command to start your exe (jlink+jpackage output) and list it under `bundle.externalBin` in `tauri.conf.json`.

## Notes
- Dist is taken from `../dist`; devPath is `http://localhost:5173`.
- If animations are still heavy, open DevTools (Ctrl+Shift+I) in Tauri dev to profile; but Chromium/WebView2 should match browser performance.
