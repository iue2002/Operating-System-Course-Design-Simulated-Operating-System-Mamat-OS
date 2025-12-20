package com.os.web;

import com.os.core.impl.OSKernelImpl;
import com.os.core.models.PCB;
import com.os.filesystem.FileSystemImpl;
import com.os.filesystem.Disk;
import com.os.filesystem.FAT;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Lightweight HTTP wrapper exposing kernel commands as JSON REST endpoints.
 * - No external deps (uses com.sun.net.httpserver)
 * - Designed as a development/testing bridge for frontends (Vue or JavaFX WebView)
 *
 * Endpoints (examples) are documented in API_DOCUMENTATION.md in the project root.
 */
public class KernelHttpServer {
    private final OSKernelImpl kernel;
    private final int port;
    private HttpServer server;

    public KernelHttpServer(OSKernelImpl kernel, int port) {
        this.kernel = kernel;
        this.port = port;
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api/fs/create", this::handleCreateFile);
        server.createContext("/api/fs/read", this::handleReadFile);
        server.createContext("/api/fs/delete", this::handleDeleteFile);
        server.createContext("/api/fs/mkdir", this::handleMakeDir);
        server.createContext("/api/fs/list", this::handleListDir);
        server.createContext("/api/process/run", this::handleRunProcess);
        server.createContext("/api/kernel/tick", this::handleTick);
        server.createContext("/api/kernel/info", this::handleInfo);
        server.createContext("/api/memory/status", this::handleMemoryStatus);
        server.createContext("/api/memory/process", this::handleMemoryProcess);
        server.createContext("/api/disk/status", this::handleDiskStatus);
        server.createContext("/api/debug/disk/bitmap", this::handleDebugDiskBitmap);
        server.createContext("/api/debug/fat/bitmap", this::handleDebugFatBitmap);
        server.createContext("/api/process/list", this::handleProcessList);
        server.createContext("/api/process/detail", this::handleProcessDetail);
        server.createContext("/api/process/kill", this::handleProcessKill);
        server.createContext("/api/process/output", this::handleProcessOutput);
        server.createContext("/api/fs/copy", this::handleCopy);
        server.createContext("/api/fs/move", this::handleMove);
        // 新增优雅关机接口
        server.createContext("/api/shutdown", this::handleShutdown);
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        server.start();
        System.out.println("Kernel HTTP server started on port " + port);
    }

    // 优雅关机处理
    private void handleShutdown(HttpExchange ex) throws IOException {
        if (handlePreflight(ex)) return;
        sendJson(ex, 200, "{\"status\":\"shutting down\"}");
        new Thread(() -> {
            try { Thread.sleep(100); } catch (InterruptedException ignored) {}
            System.exit(0);
        }).start();
    }

    public void stop(int delaySeconds) {
        if (server != null) server.stop(delaySeconds);
    }

    // Handle CORS preflight requests
    private boolean handlePreflight(HttpExchange ex) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
            ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS");
            ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
            ex.sendResponseHeaders(204, -1);
            return true;
        }
        return false;
    }

    // ===== Handlers =====

    private void handleCreateFile(HttpExchange ex) throws IOException {
        if (handlePreflight(ex)) return;
        try {
            Map<String,String> q = parseQuery(ex.getRequestURI());
            boolean isExecutable = Boolean.parseBoolean(q.getOrDefault("isExecutable", "false"));
            boolean overwrite = Boolean.parseBoolean(q.getOrDefault("overwrite", "false"));
            String path = q.get("path");
            String content = readRequestBody(ex);
            if (path == null) {
                sendJson(ex, 400, jsonError("missing path parameter"));
                return;
            }
            Map<String,Object> res = kernel.createFileCmd(path, content == null ? "" : content, isExecutable, overwrite);
            sendJson(ex, 200, toJson(res));
        } catch (Exception e) {
            sendJson(ex, 500, jsonError(e.getMessage()));
        }
    }

    private void handleReadFile(HttpExchange ex) throws IOException {
        if (handlePreflight(ex)) return;
        try {
            Map<String,String> q = parseQuery(ex.getRequestURI());
            String path = q.get("path");
            if (path == null) { sendJson(ex, 400, jsonError("missing path")); return; }
            Map<String,Object> res = kernel.readFileCmd(path);
            sendJson(ex, 200, toJson(res));
        } catch (Exception e) { sendJson(ex, 500, jsonError(e.getMessage())); }
    }

    private void handleDeleteFile(HttpExchange ex) throws IOException {
        if (handlePreflight(ex)) return;
        try {
            Map<String,String> q = parseQuery(ex.getRequestURI());
            String path = q.get("path");
            if (path == null) { sendJson(ex, 400, jsonError("missing path")); return; }
            // support deleting directories (recursive flag) as well as files
            boolean recursive = Boolean.parseBoolean(q.getOrDefault("recursive", "false"));
            Map<String,Object> res;
            try {
                // if path is a directory, delegate to deleteDirCmd
                if (kernel.getFileSystem().isDirectory(path)) {
                    res = kernel.deleteDirCmd(path, recursive);
                } else {
                    res = kernel.deleteFileCmd(path);
                }
            } catch (Exception ee) {
                // fallback to file delete if isDirectory check fails
                res = kernel.deleteFileCmd(path);
            }
            sendJson(ex, 200, toJson(res));
        } catch (Exception e) { sendJson(ex, 500, jsonError(e.getMessage())); }
    }

    private void handleMakeDir(HttpExchange ex) throws IOException {
        if (handlePreflight(ex)) return;
        try {
            Map<String,String> q = parseQuery(ex.getRequestURI());
            String path = q.get("path");
            if (path == null) { sendJson(ex, 400, jsonError("missing path")); return; }
            Map<String,Object> res = kernel.makeDirCmd(path);
            sendJson(ex, 200, toJson(res));
        } catch (Exception e) { sendJson(ex, 500, jsonError(e.getMessage())); }
    }

    private void handleListDir(HttpExchange ex) throws IOException {
        if (handlePreflight(ex)) return;
        try {
            Map<String,String> q = parseQuery(ex.getRequestURI());
            String path = q.getOrDefault("path", "/");
            List<Map<String,Object>> entries = kernel.getFileSystem().listDirectory(path);
            sendJson(ex, 200, toJson(entries));
        } catch (Exception e) { sendJson(ex, 500, jsonError(e.getMessage())); }
    }

    private void handleRunProcess(HttpExchange ex) throws IOException {
        if (handlePreflight(ex)) return;
        try {
            Map<String,String> q = parseQuery(ex.getRequestURI());
            String path = q.get("path");
            String body = readRequestBody(ex);
            String input = (path != null) ? path : body;
            if (input == null) { sendJson(ex, 400, jsonError("missing path or program body")); return; }
            Map<String,Object> res = kernel.runProgramCmd(input);
            sendJson(ex, 200, toJson(res));
        } catch (Exception e) { sendJson(ex, 500, jsonError(e.getMessage())); }
    }

    private void handleTick(HttpExchange ex) throws IOException {
        if (handlePreflight(ex)) return;
        try {
            Map<String,Object> res = kernel.systemTick();
            sendJson(ex, 200, toJson(res));
        } catch (Exception e) { sendJson(ex, 500, jsonError(e.getMessage())); }
    }

    private void handleInfo(HttpExchange ex) throws IOException {
        if (handlePreflight(ex)) return;
        try {
            Map<String,Object> info = kernel.getSystemInfo();
            sendJson(ex, 200, toJson(info));
        } catch (Exception e) { sendJson(ex, 500, jsonError(e.getMessage())); }
    }

    private void handleCopy(HttpExchange ex) throws IOException {
        if (handlePreflight(ex)) return;
        try {
            Map<String,String> q = parseQuery(ex.getRequestURI());
            String src = q.get("src");
            String dst = q.get("dst");
            boolean overwrite = Boolean.parseBoolean(q.getOrDefault("overwrite", "true"));
            if (src == null || dst == null) { sendJson(ex, 400, jsonError("missing src or dst")); return; }
            Map<String,Object> res = kernel.copyFileCmd(src, dst, overwrite);
            sendJson(ex, 200, toJson(res));
        } catch (Exception e) { sendJson(ex, 500, jsonError(e.getMessage())); }
    }

    private void handleMove(HttpExchange ex) throws IOException {
        if (handlePreflight(ex)) return;
        try {
            Map<String,String> q = parseQuery(ex.getRequestURI());
            String src = q.get("src");
            String dst = q.get("dst");
            boolean overwrite = Boolean.parseBoolean(q.getOrDefault("overwrite", "true"));
            if (src == null || dst == null) { sendJson(ex, 400, jsonError("missing src or dst")); return; }
            Map<String,Object> res = kernel.moveFileCmd(src, dst, overwrite);
            sendJson(ex, 200, toJson(res));
        } catch (Exception e) { sendJson(ex, 500, jsonError(e.getMessage())); }
    }

    private void handleMemoryStatus(HttpExchange ex) throws IOException {
        if (handlePreflight(ex)) return;
        try {
            Map<String,Object> m = kernel.getMemoryManager().getMemoryStatus();
            sendJson(ex, 200, toJson(m));
        } catch (Exception e) { sendJson(ex, 500, jsonError(e.getMessage())); }
    }

    private void handleMemoryProcess(HttpExchange ex) throws IOException {
        if (handlePreflight(ex)) return;
        try {
            Map<String,String> q = parseQuery(ex.getRequestURI());
            String pidS = q.get("pid");
            if (pidS == null) { sendJson(ex, 400, jsonError("missing pid")); return; }
            int pid = Integer.parseInt(pidS);
            Map<String,Object> m = kernel.getMemoryManager().getProcessMemoryUsage(pid);
            sendJson(ex, 200, toJson(m));
        } catch (NumberFormatException nfe) { sendJson(ex, 400, jsonError("invalid pid")); }
        catch (Exception e) { sendJson(ex, 500, jsonError(e.getMessage())); }
    }

    private void handleDiskStatus(HttpExchange ex) throws IOException {
        if (handlePreflight(ex)) return;
        try {
            Map<String,Object> ds = kernel.getFileSystem().getDiskStatus();
            sendJson(ex, 200, toJson(ds));
        } catch (Exception e) { sendJson(ex, 500, jsonError(e.getMessage())); }
    }

    private void handleDebugDiskBitmap(HttpExchange ex) throws IOException {
        if (handlePreflight(ex)) return;
        try {
            Map<String,Object> out = new HashMap<>();
            Object fs = kernel.getFileSystem();
            if (fs instanceof FileSystemImpl) {
                FileSystemImpl fsi = (FileSystemImpl) fs;
                Disk d = fsi.getDisk();
                boolean[] bm = d.getBitmap();
                List<Integer> bmList = new ArrayList<>();
                for (boolean b : bm) bmList.add(b ? 1 : 0);
                Disk.DiskUsageInfo info = d.getUsageInfo();
                Map<String,Object> usage = new HashMap<>();
                usage.put("totalBlocks", info.totalBlocks);
                usage.put("freeBlocks", info.freeBlocks);
                usage.put("usedBlocks", info.usedBlocks);
                usage.put("usagePercentage", info.usagePercentage);
                out.put("bitmap", bmList);
                out.put("usage", usage);
            } else {
                out.put("error", "filesystem implementation not accessible for debug");
            }
            sendJson(ex, 200, toJson(out));
        } catch (Exception e) { sendJson(ex, 500, jsonError(e.getMessage())); }
    }

    private void handleDebugFatBitmap(HttpExchange ex) throws IOException {
        if (handlePreflight(ex)) return;
        try {
            Map<String,Object> out = new HashMap<>();
            Object fs = kernel.getFileSystem();
            if (fs instanceof FileSystemImpl) {
                FileSystemImpl fsi = (FileSystemImpl) fs;
                FAT fat = fsi.getFAT();
                boolean[] fbm = fat.getBitmap();
                List<Integer> bmList = new ArrayList<>();
                for (boolean b : fbm) bmList.add(b ? 1 : 0);
                FAT.FATInfo fi = fat.getFATInfo();
                Map<String,Object> info = new HashMap<>();
                info.put("totalBlocks", fi.totalBlocks);
                info.put("freeBlocks", fi.freeBlocks);
                info.put("usedBlocks", fi.usedBlocks);
                out.put("bitmap", bmList);
                out.put("info", info);
            } else {
                out.put("error", "filesystem implementation not accessible for debug");
            }
            sendJson(ex, 200, toJson(out));
        } catch (Exception e) { sendJson(ex, 500, jsonError(e.getMessage())); }
    }

    private void handleProcessList(HttpExchange ex) throws IOException {
        if (handlePreflight(ex)) return;
        try {
            List<PCB> procs = kernel.getProcessManager().getAllProcesses();
            List<Map<String,Object>> out = new ArrayList<>();
            for (PCB p : procs) out.add(pcbToMap(p));
            sendJson(ex, 200, toJson(out));
        } catch (Exception e) { sendJson(ex, 500, jsonError(e.getMessage())); }
    }

    private void handleProcessDetail(HttpExchange ex) throws IOException {
        if (handlePreflight(ex)) return;
        try {
            Map<String,String> q = parseQuery(ex.getRequestURI());
            String pidS = q.get("pid");
            if (pidS == null) { sendJson(ex, 400, jsonError("missing pid")); return; }
            int pid = Integer.parseInt(pidS);
            PCB p = kernel.getProcessManager().getProcess(pid);
            if (p == null) { sendJson(ex, 404, jsonError("process not found")); return; }
            Map<String,Object> m = pcbToMap(p);
            // attach memory usage if available
            try { m.put("memory", kernel.getMemoryManager().getProcessMemoryUsage(pid)); } catch (Exception ignored) {}
            sendJson(ex, 200, toJson(m));
        } catch (NumberFormatException nfe) { sendJson(ex, 400, jsonError("invalid pid")); }
        catch (Exception e) { sendJson(ex, 500, jsonError(e.getMessage())); }
    }

    private void handleProcessKill(HttpExchange ex) throws IOException {
        if (handlePreflight(ex)) return;
        try {
            Map<String,String> q = parseQuery(ex.getRequestURI());
            String pidS = q.get("pid");
            if (pidS == null) { sendJson(ex, 400, jsonError("missing pid")); return; }
            int pid = Integer.parseInt(pidS);
            boolean ok = kernel.getProcessManager().terminateProcess(pid, true);
            // Ensure kernel-level cleanup: free page table/pages and release any devices
            if (ok) {
                try { kernel.getMemoryManager().destroyPageTable(pid); } catch (Exception ignored) {}
                try { kernel.getMemoryManager().releaseSystemPage(pid); } catch (Exception ignored) {}
                try { kernel.getDeviceManager().releaseAllProcessDevices(pid); } catch (Exception ignored) {}
                kernel.clearProcessOutput(pid);
                Map<String,Object> ev = new HashMap<>(); ev.put("pid", pid); ev.put("action", "killed");
                kernel.fireEvent("process.killed", ev);
            }
            Map<String,Object> r = new HashMap<>(); r.put("success", ok);
            sendJson(ex, 200, toJson(r));
        } catch (NumberFormatException nfe) { sendJson(ex, 400, jsonError("invalid pid")); }
        catch (Exception e) { sendJson(ex, 500, jsonError(e.getMessage())); }
    }

    private void handleProcessOutput(HttpExchange ex) throws IOException {
        if (handlePreflight(ex)) return;
        try {
            Map<String,String> q = parseQuery(ex.getRequestURI());
            String pidS = q.get("pid");
            if (pidS == null) { sendJson(ex, 400, jsonError("missing pid")); return; }
            int pid = Integer.parseInt(pidS);
            Map<String,Object> res = kernel.getProcessOutputCmd(pid);
            sendJson(ex, 200, toJson(res));
        } catch (NumberFormatException nfe) { sendJson(ex, 400, jsonError("invalid pid")); }
        catch (Exception e) { sendJson(ex, 500, jsonError(e.getMessage())); }
    }

    // ===== utils =====
    private static String jsonError(String msg) {
        return "{\"success\":false,\"error\":\"" + escapeJson(msg == null ? "" : msg) + "\"}";
    }

    private static String readRequestBody(HttpExchange ex) throws IOException {
        InputStream is = ex.getRequestBody();
        if (is == null) return null;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int r;
        while ((r = is.read(buf)) != -1) baos.write(buf, 0, r);
        return new String(baos.toByteArray(), StandardCharsets.UTF_8);
    }

    private static Map<String,String> parseQuery(URI uri) throws UnsupportedEncodingException {
        Map<String,String> out = new HashMap<>();
        String raw = uri.getRawQuery();
        if (raw == null || raw.isEmpty()) return out;
        String[] parts = raw.split("&");
        for (String p : parts) {
            int i = p.indexOf('=');
            if (i < 0) continue;
            String k = URLDecoder.decode(p.substring(0, i), "UTF-8");
            String v = URLDecoder.decode(p.substring(i+1), "UTF-8");
            out.put(k, v);
        }
        return out;
    }

    private static void sendJson(HttpExchange ex, int code, String body) throws IOException {
        byte[] data = body.getBytes(StandardCharsets.UTF_8);
        // Add CORS response headers to allow browser clients
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS");
        ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(code, data.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(data); }
    }

    // Minimal JSON serializer for Maps/Lists/primitives
    private static String toJson(Object o) {
        if (o == null) return "null";
        if (o instanceof Map) {
            StringBuilder sb = new StringBuilder(); sb.append('{');
            boolean first = true;
            for (Object k : ((Map)o).keySet()) {
                if (!first) sb.append(',');
                first = false;
                sb.append('"').append(escapeJson(String.valueOf(k))).append('"').append(':');
                sb.append(toJson(((Map)o).get(k)));
            }
            sb.append('}'); return sb.toString();
        }
        if (o instanceof List) {
            StringBuilder sb = new StringBuilder(); sb.append('[');
            boolean first = true;
            for (Object e : (List)o) {
                if (!first) sb.append(',');
                first = false;
                sb.append(toJson(e));
            }
            sb.append(']'); return sb.toString();
        }
        if (o instanceof String) return '"' + escapeJson((String)o) + '"';
        if (o instanceof Number || o instanceof Boolean) return String.valueOf(o);
        // fallback to string
        return '"' + escapeJson(String.valueOf(o)) + '"';
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    private Map<String,Object> pcbToMap(PCB p) {
        if (p == null) return Collections.emptyMap();
        Map<String,Object> m = new HashMap<>();
        m.put("pid", p.getPid());
        m.put("name", p.getName());
        m.put("state", p.getState() == null ? null : p.getState().toString());
        m.put("pc", p.getPc());
        m.put("timeSlice", p.getTimeSlice());
        m.put("priority", p.getPriority());
        m.put("runTime", p.getRunTime());
        // do not include full instructions by default
        return m;
    }

    public static void main(String[] args) throws Exception {
        int port = 8080;
        if (args != null && args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException ignored) {}
        } else {
            String prop = System.getProperty("os.http.port");
            if (prop != null) {
                try {
                    port = Integer.parseInt(prop.trim());
                } catch (NumberFormatException ignored) {}
            }
        }

        OSKernelImpl kernel = new OSKernelImpl();
        kernel.initialize();
        kernel.start();

        KernelHttpServer srv = new KernelHttpServer(kernel, port);
        srv.start();

        // add shutdown hook to persist disk
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutting down kernel and saving disk image...");
            try { kernel.stop(false); } catch (Exception ignored) {}
            try { srv.stop(1); } catch (Exception ignored) {}
        }));
    }
}