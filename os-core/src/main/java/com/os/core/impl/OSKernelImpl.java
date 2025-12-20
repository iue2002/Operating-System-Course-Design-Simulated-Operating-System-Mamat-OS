package com.os.core.impl;

import com.os.core.cpu.CpuEmulator;
import com.os.core.cpu.CpuInterrupt;
import com.os.core.cpu.CpuTickSnapshot;
import com.os.core.cpu.InterruptType;
import com.os.core.interfaces.DeviceManagerAPI;
import com.os.core.interfaces.FileSystemAPI;
import com.os.core.interfaces.MemoryManagerAPI;
import com.os.core.interfaces.OSKernel;
import com.os.core.interfaces.ProcessManagerAPI;
import com.os.device.DeviceManagerImpl;
import com.os.memory.MemoryManagerImpl;
import com.os.process.ProcessManagerImpl;
import com.os.filesystem.Disk;
import com.os.filesystem.FAT;
import com.os.filesystem.DirectoryEntry;
import com.os.core.models.PCB;
import com.os.core.models.Instruction;
import com.os.core.models.PageTableEntry;
import com.os.core.models.ProcessTickResult;
import com.os.filesystem.FileSystemImpl;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 简单的 OSKernel 实现：组装子系统并负责 systemTick  协调
 */
public class OSKernelImpl implements OSKernel {
    private final DeviceManagerAPI deviceManager;
    private final MemoryManagerAPI memoryManager;
    private final ProcessManagerAPI processManager;
    private final FileSystemAPI fileSystem; // minimal adapter
    private final CpuEmulator cpu = new CpuEmulator();

    private boolean running = false;
    private final Disk disk;
    private final FAT fat;

    // Event listeners and event log for front-end integration
    private final Map<String, List<EventListener>> eventListeners = new HashMap<>();
    private final Deque<Map<String,Object>> eventLog = new ArrayDeque<>();
    private final int EVENT_LOG_MAX = 200;

    // transient in-memory process output storage to avoid filesystem name length issues
    private final Map<Integer, String> processOutputs = new HashMap<>();

    // uptime in ticks
    private long uptimeTicks = 0;
    private ScheduledExecutorService tickExecutor;
    private final long AUTO_TICK_INTERVAL_MS = 5000;
    private volatile Map<String,Object> lastTickSnapshot = Collections.emptyMap();
    private final Object tickLock = new Object();

    public OSKernelImpl() {
        // init components
        this.deviceManager = new DeviceManagerImpl();
        this.memoryManager = new MemoryManagerImpl();
        // ProcessManager needs DeviceManager reference
        this.processManager = new ProcessManagerImpl(this.deviceManager);
        // minimal filesystem uses Disk/FAT
        this.disk = new Disk();
        this.fat = new FAT(disk);
        this.fileSystem = new FileSystemImpl(disk);
    }

    @Override
    public synchronized boolean initialize() {
        memoryManager.initialize();
        deviceManager.initialize();
        // try load disk image if available (persist across runs)
        try {
            disk.loadFromFile(Disk.DEFAULT_DISK_IMAGE);
        } catch (Exception ignored) {
            // fall through and rebuild an empty FAT below
        }
        // Always reload both kernel-level and filesystem-level FAT views so they
        // agree on which data blocks are in use before any reconciliation logic runs.
        reloadFatViews();
        // no explicit init for process manager
        return true;
    }

    /**
     * Reload FAT metadata for both the kernel's diagnostic view and the
     * FileSystemImpl instance so that subsequent operations share the same
     * allocation map.
     */
    private void reloadFatViews() {
        try { fat.loadFAT(); } catch (Exception ignored) {}
        if (fileSystem instanceof FileSystemImpl) {
            FileSystemImpl fsImpl = (FileSystemImpl) fileSystem;
            try { fsImpl.getFAT().loadFAT(); } catch (Exception ignored) {}
        }
    }

    @Override
    public synchronized boolean start() {
        running = true;
        uptimeTicks = 0;
        startAutoTicking();
        return true;
    }

    @Override
    public synchronized boolean stop(boolean force) {
        if (!running) return true;
        running = false;
        stopAutoTicking();

        if (force) {
            try {
                List<PCB> processes = processManager.getAllProcesses();
                if (processes != null) {
                    for (PCB pcb : processes) {
                        if (pcb == null) continue;
                        int pid = pcb.getPid();
                        try {
                            processManager.terminateProcess(pid, true);
                        } catch (Exception ignored) {}
                        try {
                            memoryManager.destroyPageTable(pid);
                        } catch (Exception ignored) {}
                        try {
                            memoryManager.releaseSystemPage(pid);
                        } catch (Exception ignored) {}
                        try {
                            pcb.setSystemPage(-1);
                        } catch (Exception ignored) {}
                        try {
                            deviceManager.releaseAllProcessDevices(pid);
                        } catch (Exception ignored) {}
                    }
                }
            } catch (Exception ignored) {}
            try { processOutputs.clear(); } catch (Exception ignored) {}
            try { deviceManager.reset(); } catch (Exception ignored) {}
        }
        return true;
    }

    @Override
    public synchronized boolean reset(boolean keepFileSystem) {
        boolean wasRunning = running;
        stopAutoTicking();
        deviceManager.reset();
        memoryManager.reset();
        processOutputs.clear();
        lastTickSnapshot = Collections.emptyMap();
        if (!keepFileSystem) {
            fileSystem.formatDisk();
        }
        if (wasRunning) {
            startAutoTicking();
        }
        return true;
    }

    @Override
    public synchronized boolean isRunning() {
        return running;
    }

    @Override
    public synchronized Map<String, Object> systemTick() {
        synchronized (tickLock) {
            uptimeTicks++;
            Map<String,Object> result = new HashMap<>();
            long ts = System.currentTimeMillis();
            result.put("timestamp", ts);
            result.put("uptimeTicks", uptimeTicks);

        List<Map<String,Object>> completed = deviceManager.onTick();
        result.put("deviceEvents", completed);
        List<Map<String,Object>> interrupts = new ArrayList<>();
        // fire device events
        for (Map<String,Object> ev : completed) {
            fireEvent("device.completion", ev);
        }

        List<Map<String,Object>> wakeups = new ArrayList<>();
        // process device completions: wake up pids
        for (Map<String,Object> ev : completed) {
            Object pidObj = ev.get("pid");
            if (!(pidObj instanceof Integer)) continue;
            int pid = (Integer) pidObj;
            String status = ev.get("status") instanceof String ? (String) ev.get("status") : "completed";
            String deviceName = ev.get("device") instanceof String ? (String) ev.get("device") : "";
            Map<String,Object> m = new HashMap<>();
            m.put("pid", pid);
            m.put("status", status);
            if ("completed".equalsIgnoreCase(status)) {
                boolean ok = processManager.wakeupProcess(pid);
                m.put("woken", ok);
                wakeups.add(m);
                fireEvent("process.wakeup", m);
                String reason = deviceName.isEmpty() ? "设备完成" : ("设备 " + deviceName + " 完成");
                interrupts.add(interruptMap(InterruptType.IO_COMPLETE, pid, reason));
            } else {
                m.put("woken", false);
                wakeups.add(m);
                fireEvent("device.allocated", m);
            }
        }
        result.put("wakeups", wakeups);

        ProcessTickResult tickResult = CPU();
        List<PCB> changed = tickResult.getChangedProcesses();
        result.put("processChanges", changed);
        if (!changed.isEmpty()) {
            for (PCB p : changed) {
                Map<String,Object> ev = new HashMap<>();
                ev.put("pid", p.getPid());
                ev.put("state", p.getState());
                fireEvent("process.change", ev);
            }
        }

        // Resource cleanup for terminated processes: free memory and release devices
        for (PCB p : changed) {
            if (p != null && p.getState() == PCB.ProcessState.TERMINATED) {
                int tid = p.getPid();
                try {
                    // cache process output in-memory for API retrieval; no longer persists to disk
                    try {
                        String execPath = p.getExecutablePath() == null ? "" : p.getExecutablePath();
                        int xval = p.getVariable('x');
                        String out = String.format("%s -> x=%d", execPath, xval);
                        processOutputs.put(tid, out);
                        persistProcessOutput(tid, out);
                    } catch (Exception ignored) {}

                    // free memory page table and pages
                    memoryManager.destroyPageTable(tid);
                    memoryManager.releaseSystemPage(tid);
                    p.setSystemPage(-1);
                } catch (Exception ignored) {}
                try {
                    // ensure devices held by this pid are released
                    deviceManager.releaseAllProcessDevices(tid);
                } catch (Exception ignored) {}
                Map<String,Object> cleanupEv = new HashMap<>();
                cleanupEv.put("pid", tid);
                cleanupEv.put("action", "cleanup");
                fireEvent("process.cleanup", cleanupEv);
            }
        }

        CpuTickSnapshot cpuSnapshot = cpu.tick(tickResult, uptimeTicks);
        result.put("cpu", cpuSnapshot.toMap());
        CpuInterrupt cpuInterrupt = cpuSnapshot.getInterrupt();
        if (cpuInterrupt != null && !cpuInterrupt.isNone()) {
            interrupts.add(cpuInterrupt.toMap());
        }
        result.put("interrupts", interrupts);

            lastTickSnapshot = new HashMap<>(result);

            return result;
        }
    }

    // ========== Kernel command wrappers (file/dir/process) ==========
    public synchronized Map<String,Object> createFileCmd(String path, String content, boolean isExecutable, boolean overwrite) {
        Map<String,Object> r = new HashMap<>();
        try {
            boolean ok = fileSystem.createFile(path, content, isExecutable, overwrite);
            r.put("success", ok);
            if (ok) {
                r.put("persistScheduled", true);
                try { r.put("disk", fileSystem.getDiskStatus()); } catch (Exception ignored) {}
            } else {
                boolean exists = false;
                try { exists = fileSystem.pathExists(path); } catch (Exception ignored) {}
                if (exists && !overwrite) {
                    r.put("error", "同名文件已存在");
                } else {
                    r.put("error", "createFile failed");
                }
            }
        } catch (Exception ex) { r.put("success", false); r.put("error", ex.getMessage()); }
        return r;
    }

    public synchronized Map<String,Object> readFileCmd(String path) {
        Map<String,Object> r = new HashMap<>();
        try {
            String c = fileSystem.readFile(path);
            r.put("success", true);
            r.put("content", c == null ? "" : c);
        } catch (Exception ex) { r.put("success", false); r.put("error", ex.getMessage()); }
        return r;
    }

    /**
     * Return process output cached in-memory (if present). This avoids depending on
     * filesystem filenames which are restricted to 3+2 bytes in the simulated FS.
     */
    public synchronized Map<String,Object> getProcessOutputCmd(int pid) {
        Map<String,Object> r = new HashMap<>();
        try {
            if (processOutputs.containsKey(pid)) {
                r.put("success", true);
                r.put("content", processOutputs.get(pid));
            } else {
                r.put("success", false);
                r.put("error", "no output for pid " + pid);
            }
        } catch (Exception ex) { r.put("success", false); r.put("error", ex.getMessage()); }
        return r;
    }

    public synchronized Map<String,Object> deleteFileCmd(String path) {
        Map<String,Object> r = new HashMap<>();
        try {
            boolean ok = fileSystem.deleteFile(path);
            r.put("success", ok);
            if (ok) {
                r.put("persistScheduled", true);
                try { r.put("disk", fileSystem.getDiskStatus()); } catch (Exception ignored) {}
            }
        } catch (Exception ex) { r.put("success", false); r.put("error", ex.getMessage()); }
        return r;
    }

    public synchronized Map<String,Object> copyFileCmd(String src, String dst, boolean overwrite) {
        Map<String,Object> r = new HashMap<>();
        try {
            boolean ok = fileSystem.copyFile(src, dst, overwrite);
            r.put("success", ok);
            if (ok) {
                r.put("persistScheduled", true);
                try { r.put("disk", fileSystem.getDiskStatus()); } catch (Exception ignored) {}
            }
        } catch (Exception ex) { r.put("success", false); r.put("error", ex.getMessage()); }
        return r;
    }

    public synchronized Map<String,Object> moveFileCmd(String src, String dst, boolean overwrite) {
        Map<String,Object> r = new HashMap<>();
        try {
            boolean ok = fileSystem.moveFile(src, dst, overwrite);
            r.put("success", ok);
            if (ok) {
                r.put("persistScheduled", true);
                try { r.put("disk", fileSystem.getDiskStatus()); } catch (Exception ignored) {}
            }
        } catch (Exception ex) { r.put("success", false); r.put("error", ex.getMessage()); }
        return r;
    }

    public synchronized Map<String,Object> makeDirCmd(String path) {
        Map<String,Object> r = new HashMap<>();
        try {
            boolean ok = fileSystem.createDirectory(path);
            r.put("success", ok);
            if (ok) {
                r.put("persistScheduled", true);
                try { r.put("disk", fileSystem.getDiskStatus()); } catch (Exception ignored) {}
            }
        } catch (Exception ex) { r.put("success", false); r.put("error", ex.getMessage()); }
        return r;
    }

    // Backward-compatible overload used by older callers that pass an extra parameter (ignored)
    public synchronized Map<String,Object> makeDirCmd(String path, Object ignored) {
        return makeDirCmd(path);
    }

    public synchronized Map<String,Object> deleteDirCmd(String path, boolean recursive) {
        Map<String,Object> r = new HashMap<>();
        try {
            boolean ok = fileSystem.deleteDirectory(path, recursive);
            r.put("success", ok);
            if (ok) {
                r.put("persistScheduled", true);
                try { r.put("disk", fileSystem.getDiskStatus()); } catch (Exception ignored) {}
            }
        } catch (Exception ex) { r.put("success", false); r.put("error", ex.getMessage()); }
        return r;
    }

    public synchronized Map<String,Object> runProgramCmd(String pathOrProgram) {
        // wrapper for createAndRunProcess
        return createAndRunProcess(pathOrProgram);
    }

    @Override
    public synchronized FileSystemAPI getFileSystem() {
        return fileSystem;
    }

    @Override
    public synchronized ProcessManagerAPI getProcessManager() {
        return processManager;
    }

    @Override
    public synchronized MemoryManagerAPI getMemoryManager() {
        return memoryManager;
    }

    @Override
    public synchronized DeviceManagerAPI getDeviceManager() {
        return deviceManager;
    }

    @Override
    public synchronized Map<String, Object> createAndRunProcess(String executablePath) {
        Map<String,Object> r = new HashMap<>();
        try {
            if (executablePath == null) {
                r.put("success", false);
                r.put("error", "null path");
                return r;
            }

            boolean inlineProgram = executablePath.contains("\n") || executablePath.contains(";") || executablePath.contains("end.");
            String programContent = null;
            if (inlineProgram) {
                programContent = executablePath;
            } else {
                Map<String,Object> exe = fileSystem.loadExecutable(executablePath, false);
                boolean found = exe != null && Boolean.TRUE.equals(exe.get("found"));
                if (!found) {
                    r.put("success", false);
                    r.put("error", "executable not found");
                    return r;
                }
                boolean isExecutable = exe.containsKey("isExecutable") && Boolean.TRUE.equals(exe.get("isExecutable"));
                if (!isExecutable) {
                    r.put("success", false);
                    r.put("error", "target file is not marked executable");
                    return r;
                }
                Object c = exe.get("content");
                if (c instanceof String) {
                    programContent = (String) c;
                }
                if (programContent == null || programContent.isEmpty()) {
                    // fallback to raw read
                    programContent = fileSystem.readFile(executablePath);
                }
            }

            if (programContent == null || programContent.trim().isEmpty()) {
                r.put("success", false);
                r.put("error", "executable content empty");
                return r;
            }

            final int defaultPages = 2;
            if (!memoryManager.hasEnoughMemory(defaultPages)) {
                r.put("success", false);
                r.put("error", "内存已满，进程创建失败");
                return r;
            }
            if (memoryManager.getFreeSystemPageCount() <= 0) {
                r.put("success", false);
                r.put("error", "系统区已满，进程创建失败");
                return r;
            }

            PCB pcb;
            if (inlineProgram) {
                pcb = processManager.createProcess(programContent, 1, null);
                pcb.setExecutablePath("<inline>");
                pcb.setName("inline-" + pcb.getPid());
            } else {
                pcb = processManager.createProcess(programContent, 1, null);
                pcb.setExecutablePath(executablePath);
                pcb.setName(executablePath);
            }

            int systemPageId = memoryManager.allocateSystemPage(pcb.getPid());
            if (systemPageId < 0) {
                try { processManager.terminateProcess(pcb.getPid(), true); } catch (Exception ignored) {}
                processOutputs.remove(pcb.getPid());
                r.put("success", false);
                r.put("error", "系统区已满，进程创建失败");
                return r;
            }
            pcb.setSystemPage(systemPageId);

            boolean pageTableCreated = false;
            Map<String,Object> allocationResult = null;
            try {
                memoryManager.createPageTable(pcb.getPid(), defaultPages);
                pageTableCreated = true;
                allocationResult = memoryManager.allocatePages(pcb.getPid(), defaultPages, null);
            } catch (Exception allocationError) {
                allocationResult = new HashMap<>();
                allocationResult.put("success", false);
                allocationResult.put("error", allocationError.getMessage());
            }
            boolean allocationSucceeded = allocationResult != null && Boolean.TRUE.equals(allocationResult.get("success"));
            if (!allocationSucceeded) {
                if (pageTableCreated) {
                    try { memoryManager.destroyPageTable(pcb.getPid()); } catch (Exception ignored) {}
                }
                try {
                    memoryManager.releaseSystemPage(pcb.getPid());
                    pcb.setSystemPage(-1);
                } catch (Exception ignored) {}
                try { processManager.terminateProcess(pcb.getPid(), true); } catch (Exception ignored) {}
                processOutputs.remove(pcb.getPid());
                r.put("success", false);
                r.put("error", "内存已满，进程创建失败");
                return r;
            }
            processOutputs.remove(pcb.getPid());
            r.put("success", true);
            r.put("pid", pcb.getPid());
            fireEvent("process.created", Collections.singletonMap("pid", pcb.getPid()));
            return r;
        } catch (Exception ex) {
            r.put("success", false);
            r.put("error", ex.getMessage());
            return r;
        }
    }

    // ======= Process control primitives required by course design =======

    /**
     * Primitive wrapper for creating a process from inline program text.
     */
    public synchronized Map<String,Object> createProcessPrimitive(String programBody) {
        return createAndRunProcess(programBody);
    }

    /**
     * Primitive wrapper for terminating a process.
     */
    public synchronized boolean terminateProcessPrimitive(int pid, boolean force) {
        return processManager.terminateProcess(pid, force);
    }

    /**
     * Primitive wrapper for blocking a process (e.g., simulated device wait).
     */
    public synchronized boolean blockProcessPrimitive(int pid, String reason) {
        return processManager.blockProcess(pid, reason);
    }

    /**
     * Primitive wrapper for waking up a blocked process.
     */
    public synchronized boolean wakeupProcessPrimitive(int pid) {
        return processManager.wakeupProcess(pid);
    }

    @Override
    public synchronized Map<String, Object> stepExecution() {
        Map<String,Object> r = systemTick();
        return r;
    }

    @Override
    public synchronized Map<String, Object> executeInstruction(PCB process, Instruction instruction) {
        // For simplicity delegate to process manager tick (no per-instruction API)
        Map<String,Object> r = new HashMap<>();
        r.put("ok", false);
        return r;
    }

    @Override
    public synchronized Map<String, Object> handleProgramEnd(PCB process) {
        Map<String,Object> r = new HashMap<>();
        if (process != null) {
            processManager.terminateProcess(process.getPid(), false);
            // free memory for the process
            memoryManager.destroyPageTable(process.getPid());
            memoryManager.releaseSystemPage(process.getPid());
            process.setSystemPage(-1);
            r.put("terminated", true);
        } else r.put("terminated", false);
        return r;
    }

    public synchronized void clearProcessOutput(int pid) {
        processOutputs.remove(pid);
    }

    /**
     * CPU() - no-arg helper mandated by course requirement to simulate the CPU tick.
     * It invokes the process manager to execute one scheduling quantum and returns
     * the resulting context/interrupt snapshot for other subsystems (memory/device/UI).
     */
    @Override
    public synchronized ProcessTickResult CPU() {
        ProcessTickResult result = processManager.onTick();
        if (result == null) {
            return new ProcessTickResult(Collections.emptyList(), null, null, CpuInterrupt.none());
        }
        return result;
    }

    private Map<String,Object> interruptMap(InterruptType type, Integer pid, String reason) {
        Map<String,Object> map = new HashMap<>();
        map.put("type", type == null ? InterruptType.NONE.name() : type.name());
        map.put("pid", pid);
        map.put("reason", reason);
        return map;
    }

    private void persistProcessOutput(int pid, String line) {
        if (line == null || line.isEmpty()) return;
        try {
            fileSystem.writeOutput(pid, line);
        } catch (Exception ignored) {}
    }

    @Override
    public synchronized void generateInterrupt(String type, Map<String, Object> parameters) {
        // simple stub
        Map<String,Object> ev = new HashMap<>();
        ev.put("type", type);
        ev.put("params", parameters);
        fireEvent("interrupt.generated", ev);
    }

    @Override
    public synchronized Map<String, Object> handleInterrupt(String type, Map<String, Object> parameters) {
        return Collections.emptyMap();
    }

    @Override
    public synchronized void enableInterrupts() {}

    @Override
    public synchronized void disableInterrupts() {}

    @Override
    public synchronized Map<String, Object> getSystemInfo() {
        // richer snapshot for frontend
        Map<String,Object> m = new HashMap<>();
        m.put("running", running);
        m.put("uptimeTicks", uptimeTicks);
        m.put("devices", deviceManager.getAllDeviceStatus());
        m.put("memory", memoryManager.getMemoryStatus());
        m.put("processCount", processManager.getProcessCount());
        // include disk snapshot and simple process list for frontend convenience
        try {
            Map<String,Object> disk = fileSystem.getDiskStatus();
            m.put("disk", disk);
        } catch (Exception ignored) {}
        try {
            List<PCB> procs = processManager.getAllProcesses();
            procs.sort(Comparator.comparingInt(PCB::getStartTime));
            List<Map<String,Object>> procMaps = new ArrayList<>();
            for (PCB p : procs) procMaps.add(simplePcbMap(p));
            m.put("processes", procMaps);
            Map<String,Object> queues = new HashMap<>();
            Map<String,Object> rawQueues = processManager.getProcessQueues();
            if (rawQueues != null) {
                queues.put("readyCount", rawQueues.getOrDefault("ready", 0));
                Object readyListObj = rawQueues.get("readyList");
                if (readyListObj instanceof List) {
                    List<?> readyList = (List<?>) readyListObj;
                    List<Map<String,Object>> readyMaps = new ArrayList<>();
                    for (Object o : readyList) {
                        if (o instanceof PCB) readyMaps.add(simplePcbMap((PCB)o));
                    }
                    queues.put("readyList", readyMaps);
                }
                Object blockedListObj = rawQueues.get("blockedList");
                if (blockedListObj instanceof Map) {
                    Map<?,?> blocked = (Map<?,?>) blockedListObj;
                    Map<String,List<Map<String,Object>>> blockedMaps = new HashMap<>();
                    for (Map.Entry<?,?> e : blocked.entrySet()) {
                        String key = String.valueOf(e.getKey());
                        List<Map<String,Object>> arr = new ArrayList<>();
                        if (e.getValue() instanceof List) {
                            for (Object o : (List<?>) e.getValue()) {
                                if (o instanceof PCB) arr.add(simplePcbMap((PCB) o));
                            }
                        }
                        blockedMaps.put(key, arr);
                    }
                    queues.put("blockedList", blockedMaps);
                }
                queues.put("running", rawQueues.get("running"));
            }
            m.put("queues", queues);
        } catch (Exception ignored) {}
        m.put("lastTick", lastTickSnapshot);
        return m;
    }

    // Helper to convert PCB to JSON-friendly map
    private Map<String,Object> simplePcbMap(PCB p) {
        if (p == null) return Collections.emptyMap();
        Map<String,Object> mm = new HashMap<>();
        mm.put("pid", p.getPid());
        mm.put("name", p.getName());
        mm.put("state", p.getState() == null ? null : p.getState().toString());
        mm.put("pc", p.getPc());
        mm.put("timeSlice", p.getTimeSlice());
        mm.put("priority", p.getPriority());
        mm.put("runTime", p.getRunTime());
        mm.put("order", p.getStartTime());
        mm.put("timeSliceLeft", p.getTimeSlice());
        mm.put("systemPage", p.getSystemPage());
        mm.put("waitingFor", p.getWaitingFor());
        mm.put("currentInstructionIndex", p.getCurrentInstructionIndex());
        String pointer = p.getCurrentInstructionText();
        if (pointer != null && !pointer.isEmpty()) {
            mm.put("currentInstruction", pointer);
        }
        List<Instruction> instructions = p.getInstructions();
        int pc = p.getPc();
        if (instructions != null) {
            mm.put("instructionCount", instructions.size());
            if (!mm.containsKey("currentInstruction") && pc >= 0 && pc < instructions.size()) {
                Instruction current = instructions.get(pc);
                if (current != null) {
                    String text = current.getOriginalText();
                    if (text == null || text.isEmpty()) {
                        text = current.toString();
                    }
                    mm.put("currentInstruction", text);
                }
            }
        }
        return mm;
    }

    @Override
    public synchronized Map<String, Object> getSystemStatistics() {
        Map<String,Object> s = new HashMap<>();
        s.put("deviceStats", deviceManager.getSystemStatistics());
        s.put("memoryStats", memoryManager.getMemoryStatus());
        s.put("processCount", processManager.getProcessCount());
        return s;
    }

    @Override
    public synchronized Map<String, Object> getSystemConfig() { return Collections.emptyMap(); }

    @Override
    public synchronized Map<String, Object> getSystemHealth() { return Collections.emptyMap(); }

    @Override
    public synchronized Map<String, Object> getPerformanceMetrics() { return Collections.emptyMap(); }

    @Override
    public synchronized Map<String, Object> getResourceUsage() { return Collections.emptyMap(); }

    @Override
    public synchronized Map<String, Object> getBottleneckAnalysis() { return Collections.emptyMap(); }

    @Override
    public synchronized void enableDebugMode(int level) {}

    @Override
    public synchronized void disableDebugMode() {}

    @Override
    public synchronized Map<String, Object> getDebugInfo() { return Collections.emptyMap(); }

    @Override
    public synchronized List<Map<String, Object>> getSystemLogs(String level, int limit) {
        List<Map<String,Object>> out = new ArrayList<>();
        synchronized (eventLog) {
            int i=0;
            for (Map<String,Object> e : eventLog) {
                if (limit > 0 && i++ >= limit) break;
                out.add(new HashMap<>(e));
            }
        }
        return out;
    }

    private void startAutoTicking() {
        if (tickExecutor != null && !tickExecutor.isShutdown()) return;
        tickExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "os-kernel-autotick");
            t.setDaemon(true);
            return t;
        });
        tickExecutor.scheduleAtFixedRate(() -> {
            if (!running) return;
            try {
                systemTick();
            } catch (Exception ignored) {}
        }, AUTO_TICK_INTERVAL_MS, AUTO_TICK_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    private void stopAutoTicking() {
        if (tickExecutor != null) {
            tickExecutor.shutdownNow();
            tickExecutor = null;
        }
    }

    @Override
    public synchronized void setLogLevel(String level) {}

    @Override
    public synchronized boolean updateConfig(Map<String, Object> config) { return false; }

    @Override
    public synchronized Object getConfig(String key) { return null; }

    @Override
    public synchronized boolean setConfig(String key, Object value) { return false; }

    @Override
    public synchronized boolean saveConfig(String filePath) { return false; }

    @Override
    public synchronized boolean loadConfig(String filePath) { return false; }

    @Override
    public synchronized void registerEventListener(String eventType, EventListener listener) {
        synchronized (eventListeners) {
            List<EventListener> list = eventListeners.computeIfAbsent(eventType, k -> new ArrayList<>());
            list.add(listener);
        }
    }

    @Override
    public synchronized void unregisterEventListener(String eventType, EventListener listener) {
        synchronized (eventListeners) {
            List<EventListener> list = eventListeners.get(eventType);
            if (list != null) list.remove(listener);
        }
    }

    @Override
    public synchronized void fireEvent(String eventType, Map<String, Object> eventData) {
        Map<String,Object> logEntry = new HashMap<>();
        logEntry.put("time", System.currentTimeMillis());
        logEntry.put("eventType", eventType);
        logEntry.put("data", eventData == null ? Collections.emptyMap() : new HashMap<>(eventData));
        synchronized (eventLog) {
            eventLog.addFirst(logEntry);
            while (eventLog.size() > EVENT_LOG_MAX) eventLog.removeLast();
        }
        // notify listeners (synchronously)
        List<EventListener> listenersCopy;
        synchronized (eventListeners) {
            listenersCopy = new ArrayList<>(eventListeners.getOrDefault(eventType, Collections.emptyList()));
        }
        for (EventListener l : listenersCopy) {
            try {
                l.onEvent(eventType, eventData == null ? Collections.emptyMap() : new HashMap<>(eventData));
            } catch (Exception ex) {
                // swallow listener exception but log
                Map<String,Object> err = new HashMap<>();
                err.put("error", ex.getMessage());
                err.put("eventType", eventType);
                synchronized (eventLog) { eventLog.addFirst(err); }
            }
        }
    }

    @Override
    public synchronized String getErrorMessage(String errorCode) { return ""; }

    @Override
    public synchronized void logError(String errorType, String errorMessage, Exception exception) {}

    @Override
    public synchronized List<Map<String, Object>> getErrorLogs(int limit) { return Collections.emptyList(); }

    @Override
    public synchronized void clearErrorLogs() {}
}
