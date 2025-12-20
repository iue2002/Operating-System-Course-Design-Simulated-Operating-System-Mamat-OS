# OS Simulator - API Documentation

This document lists the kernel REST endpoints, their request parameters and response formats, and integration notes for front-end (Vue / JavaFX) integration.

## Overview
The OS kernel (`OSKernelImpl`) exposes a set of wrapper commands for file system, process and system control. A lightweight HTTP server (`KernelHttpServer`) exposes these commands as JSON REST endpoints for front-end integration.

- Server: `KernelHttpServer` (runs on port 8080 by default)
- Entrypoint: `com.os.web.KernelHttpServer.main` (or start `com.os.Main` for demo)
- Disk persistence: `disk.img` in working directory (configurable via code)

---

## Common response shape
All successful responses are returned as JSON. The HTTP status code is `200 OK` for normal responses, `4xx` for client errors and `5xx` for server errors. The kernel methods typically return JSON objects with `success` boolean and additional fields.

Example:
```
{ "success": true, "pid": 1 }
```
or
```
{ "success": false, "error": "createFile failed" }
```

---

## Endpoints

### Create file
- URL: `/api/fs/create`
- Method: POST
- Query parameters:
  - `path` (required) — full path, e.g. `/DIR1/DIR2/PRG.TX`
  - `isExecutable` (optional) — `true` or `false` (default `false`)
  - `overwrite` (optional) — `true` or `false` (default `true`)
- Body: file content (plain text)
- Response: JSON from `kernel.createFileCmd`

Example:
```
POST /api/fs/create?path=/DIR1/DIR2/PRG.TX&isExecutable=true
Body: x=1;\nx++;\nend.
```

### Read file
- URL: `/api/fs/read`
- Method: GET
- Query parameters:
  - `path` (required)
- Response: JSON `{ "success": true, "content": "..." }`

### Delete file
- URL: `/api/fs/delete`
- Method: GET
- Query parameters:
  - `path` (required)
- Response: JSON `{ "success": true/false }`

### Make directory
- URL: `/api/fs/mkdir`
- Method: GET
- Query parameters:
  - `path` (required)
- Response: JSON `{ "success": true/false }`

### List directory
- URL: `/api/fs/list`
- Method: GET
- Query parameters:
  - `path` (optional) default `/`
- Response: JSON array of directory entry objects:
```
[
  { "name": "DIR2", "ext": "", "attrs": 1, "firstBlock": 4, "size": 0 },
  { "name": "PRG", "ext": "TX", "attrs": 3, "firstBlock": 5, "size": 24 }
]
```

### Copy file
- URL: `/api/fs/copy`
- Method: GET
- Query parameters:
  - `src`, `dst`, `overwrite` (optional)
- Response: JSON `{ "success": true/false }`

### Move file
- URL: `/api/fs/move`
- Method: GET
- Query parameters:
  - `src`, `dst`, `overwrite` (optional)
- Response: JSON `{ "success": true/false }`

### Run process (execute file or inline program)
- URL: `/api/process/run`
- Method: POST
- Query parameters:
  - `path` (optional) — if present, kernel loads executable from FS
- Body: if `path` omitted, body content is treated as inline program
- Response: JSON `{ "success": true, "pid": 1 }` or error

### Tick / step the Kernel
- URL: `/api/kernel/tick`
- Method: GET
- Response: JSON with `deviceEvents`, `processChanges`, `wakeups`, etc.

### Kernel info
- URL: `/api/kernel/info`
- Method: GET
- Response: JSON snapshot of `getSystemInfo()` (devices, memory, process count)

---

## Front-end integration notes

### Vue (Web) Integration
- Use `fetch` to call endpoints. Example:
```js
async function readFile(path) {
  const res = await fetch(`/api/fs/read?path=${encodeURIComponent(path)}`);
  return await res.json();
}
```
- The REST API is stateless: kernel state lives on the server. The UI should poll `/api/kernel/tick` or `/api/kernel/info` periodically (e.g. 500ms) for updates, or invoke `/api/kernel/tick` when the user performs actions that should advance the simulation.

### JavaFX (Desktop) Integration
- Use `WebView` to load `frontend/dist/index.html` or a bundled HTML file. Expose Java methods to JS via `JSObject` bridge to call kernel endpoints or call Java methods directly (since desktop launcher will include `os-core` in same JVM, you can call kernel methods without HTTP).

### Persistence & Deployment
- `disk.img` stores the simulated disk and must be placed on a persistent volume when deploying to server or container. For Docker, mount host path to container volume.
- The Kernel saves `disk.img` on shutdown; for safety, consider enabling immediately-save-on-write and atomic write (tmp -> rename) — these are recommended for production.

---

## Example UI workflows
1. Create and run program
   - POST `/api/fs/create?path=/p/PRG.TX&isExecutable=true` with body program
   - POST `/api/process/run?path=/p/PRG.TX`
   - Poll `/api/kernel/tick` until process terminates
   - GET `/api/fs/read?path=out<pid>.TXT` to fetch output

2. File browser
   - GET `/api/fs/list?path=/` to fetch root entries
   - Click folder → GET `/api/fs/list?path=/folder` to fetch children
   - Click file → GET `/api/fs/read?path=/folder/file.TX`

---

## Next steps and optional improvements
- Add HTTP authentication and CORS for web deployment
- Implement atomic disk image save (tmp + rename) and immediate-save-on-modify
- Add REST endpoints for administrative tasks (save/load disk image, backup/restore)
- Add WebSocket push channel for real-time events instead of polling


---

End of document.

