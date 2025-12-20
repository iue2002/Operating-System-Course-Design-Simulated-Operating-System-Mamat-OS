# OS Simulator Frontend (Vue 3 + Vite)

This is a minimal demo frontend that talks to the backend Kernel HTTP server.

Prerequisites:
- Node.js 18+ and npm installed

Quick start (development):
```bash
cd frontend
npm install
npm run dev
```

This will run a dev server (Vite) on port 5173 and proxy `/api` to `http://localhost:8080` by default.

Build for production:
```bash
npm run build
npm run preview
```

The frontend contains a simple File Browser, Editor and System panel to demonstrate calling the REST APIs.

