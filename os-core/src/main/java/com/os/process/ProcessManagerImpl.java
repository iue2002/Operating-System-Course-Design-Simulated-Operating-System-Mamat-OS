package com.os.process;

import com.os.core.cpu.CpuInterrupt;
import com.os.core.cpu.InterruptType;
import com.os.core.interfaces.DeviceManagerAPI;
import com.os.core.interfaces.ProcessManagerAPI;
import com.os.core.models.Instruction;
import com.os.core.models.PCB;
import com.os.core.models.ProcessTickResult;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 简单的进程管理实现，使用 RR 调度，支持 I/O 阻塞模拟
 */
public class ProcessManagerImpl implements ProcessManagerAPI {
    private static final int MAX_PROCESSES = 10;

    private final RRScheduler scheduler = new RRScheduler();
    private final Map<Integer, PCB> allProcesses = new HashMap<>();
    private final Map<Integer, PCB> terminatedProcesses = new LinkedHashMap<>();
    private final Map<String, Queue<PCB>> blockedQueues = new HashMap<>();
    private final AtomicInteger pidCounter = new AtomicInteger(1);
    private final int maxPid = 1024;
    private final Deque<Integer> recycledPids = new ArrayDeque<>();
    private final Set<Integer> recycledPidSet = new HashSet<>();
    private final java.util.concurrent.atomic.AtomicLong creationCounter = new java.util.concurrent.atomic.AtomicLong(0);
    private PCB running = null;
    private int timeSlice = 5; // 默认时间片（指令数）

    private final DeviceManagerAPI deviceManager; // 设备管理器引用

    // legacy fallback: internal waiters map used when no DeviceManager is injected
    private final Map<String, List<PCB>> deviceWaiters = new HashMap<>();

    public ProcessManagerImpl() {
        this.deviceManager = null; // backward compatibility
    }

    public ProcessManagerImpl(DeviceManagerAPI deviceManager) {
        this.deviceManager = deviceManager;
    }

    @Override
    public synchronized PCB createProcess(String executablePath, Integer priority, List<String> args) {
        if (allProcesses.size() >= MAX_PROCESSES) {
            throw new IllegalStateException("最多同时存在 " + MAX_PROCESSES + " 个进程");
        }
        int pid = allocatePid();
        // remove stale record if pid is recycled
        terminatedProcesses.remove(pid);
        PCB pcb = new PCB(pid, executablePath, executablePath);
        pcb.setPriority(priority == null ? 1 : priority);
        pcb.setTimeSlice(timeSlice);
        // 解析可执行（如果文件路径是内联程序字符串，用简单解析）
        List<Instruction> instrs = Instruction.parseFromStringOrFile(executablePath);
        pcb.setInstructions(instrs);
        pcb.setState(PCB.ProcessState.READY);
        pcb.setStartTime(Math.toIntExact(Math.min(Integer.MAX_VALUE, creationCounter.incrementAndGet())));
        updateCurrentInstructionPointer(pcb);
        allProcesses.put(pid, pcb);
        scheduler.add(pcb);
        return pcb;
    }

    @Override
    public synchronized boolean terminateProcess(int pid, boolean force) {
        PCB p = allProcesses.get(pid);
        if (p == null) {
            // already terminated or not found
            return terminatedProcesses.containsKey(pid);
        }
        recordTermination(p);
        scheduler.remove(pid);
        blockedQueues.values().forEach(q -> q.removeIf(x -> x.getPid() == pid));
        if (running != null && running.getPid() == pid) running = null;
        // 释放该进程占用的设备（若有设备管理器）
        if (deviceManager != null) {
            deviceManager.releaseAllProcessDevices(pid);
        }
        allProcesses.remove(pid);
        releasePid(pid);
        return true;
    }

    @Override
    public synchronized boolean blockProcess(int pid, String reason) {
        PCB p = allProcesses.get(pid);
        if (p == null) return false;
        p.setState(PCB.ProcessState.BLOCKED);
        p.setWaitingFor(reason);
        blockedQueues.computeIfAbsent(reason, k -> new LinkedList<>()).offer(p);
        // remove from ready queue if present
        scheduler.remove(pid);
        if (running != null && running.getPid() == pid) running = null;
        return true;
    }

    @Override
    public synchronized boolean wakeupProcess(int pid) {
        PCB p = allProcesses.get(pid);
        if (p == null) return false;
        // 如果进程已经终止或已经在就绪/运行状态，不再唤醒以避免重复处理
        if (p.getState() == PCB.ProcessState.TERMINATED) return false;
        if (p.getState() == PCB.ProcessState.READY || p.getState() == PCB.ProcessState.RUNNING) return false;
        // remove from blocked queues
        blockedQueues.values().forEach(q -> q.removeIf(x -> x.getPid() == pid));
        p.setState(PCB.ProcessState.READY);
        p.setWaitingFor("");
        scheduler.add(p);
        updateCurrentInstructionPointer(p);
        return true;
    }

    @Override
    public synchronized List<PCB> getAllProcesses() {
        List<PCB> result = new ArrayList<>(allProcesses.values());
        result.addAll(terminatedProcesses.values());
        return result;
    }

    @Override
    public synchronized PCB getProcess(int pid) {
        PCB pcb = allProcesses.get(pid);
        if (pcb != null) return pcb;
        return terminatedProcesses.get(pid);
    }

    @Override
    public synchronized Map<String, Object> getProcessQueues() {
        Map<String, Object> m = new HashMap<>();
        m.put("ready", scheduler.size());
        m.put("readyList", scheduler.snapshot());
        m.put("blocked", blockedQueues.keySet());
        Map<String, java.util.List<PCB>> blockedSnapshots = new HashMap<>();
        for (Map.Entry<String, Queue<PCB>> e : blockedQueues.entrySet()) {
            blockedSnapshots.put(e.getKey(), new ArrayList<>(e.getValue()));
        }
        m.put("blockedList", blockedSnapshots);
        m.put("running", running == null ? null : running.getPid());
        return m;
    }

    @Override
    public synchronized Queue<PCB> getReadyQueue() {
        // not exposing internal queue reference; return snapshot
        Queue<PCB> q = new LinkedList<>();
        // Pull from scheduler by repeatedly calling next then re-adding — but simpler: reflect size only
        return q;
    }

    @Override
    public synchronized Map<String, Queue<PCB>> getBlockedQueue() {
        return blockedQueues;
    }

    @Override
    public synchronized PCB getRunningProcess() {
        return running;
    }

    @Override
    public synchronized PCB schedule() {
        if (running != null && running.getState() == PCB.ProcessState.RUNNING) {
            // if running still has time slice, continue
            return running;
        }
        PCB next = scheduler.next();
        if (next != null) {
            next.setState(PCB.ProcessState.RUNNING);
            updateCurrentInstructionPointer(next);
            running = next;
        }
        return running;
    }

    @Override
    public synchronized ProcessTickResult onTick() {
        List<PCB> changed = new ArrayList<>();
        CpuInterrupt interrupt = CpuInterrupt.none();
        Instruction executedInstruction = null;

        if (running == null) {
            schedule();
        }

        if (running == null) {
            return new ProcessTickResult(changed, null, null,
                    new CpuInterrupt(InterruptType.SCHEDULER_IDLE, null, "无可运行进程"));
        }

        List<Instruction> instrs = running.getInstructions();
        if (instrs == null || instrs.isEmpty() || running.getPc() >= instrs.size()) {
            PCB finished = finishRunningProcess(changed);
            interrupt = new CpuInterrupt(InterruptType.PROGRAM_END,
                    finished == null ? null : finished.getPid(), "程序结束");
            schedule();
            return new ProcessTickResult(changed, running, null, interrupt);
        }

        Instruction ins = instrs.get(running.getPc());
        executedInstruction = ins;
        updateCurrentInstructionPointer(running);
        boolean advancedPc = true;

        if (ins != null) {
            switch (ins.getType()) {
                case ASSIGN:
                    running.setVariable(ins.getVariable(), ins.getValue());
                    break;
                case INCREMENT:
                    running.setVariable(ins.getVariable(), running.getVariable(ins.getVariable()) + 1);
                    break;
                case DECREMENT:
                    running.setVariable(ins.getVariable(), running.getVariable(ins.getVariable()) - 1);
                    break;
                case DEVICE_IO: {
                    PCB current = running;
                    char dev = ins.getDeviceType();
                    int useTime = Math.max(1, ins.getDeviceTime());
                    if (deviceManager != null) {
                        try {
                            deviceManager.requestDevice(current.getPid(), dev, useTime);
                        } catch (Exception ignored) {
                        }
                    } else {
                        String devKey = String.valueOf(dev);
                        List<PCB> list = deviceWaiters.computeIfAbsent(devKey, k -> new ArrayList<>());
                        if (!list.contains(current)) list.add(current);
                    }
                    current.setPc(current.getPc() + 1);
                    current.setRunTime(current.getRunTime() + 1);
                    current.setTimeSlice(Math.max(0, current.getTimeSlice() - 1));
                    updateCurrentInstructionPointer(current);
                    blockProcess(current.getPid(), String.valueOf(dev));
                    changed.add(current);
                    advancedPc = false;
                    interrupt = new CpuInterrupt(InterruptType.IO_REQUEST, current.getPid(),
                            "请求设备 " + dev);
                    break;
                }
                case END: {
                    PCB finished = finishRunningProcess(changed);
                    interrupt = new CpuInterrupt(InterruptType.PROGRAM_END,
                            finished == null ? null : finished.getPid(), "程序结束");
                    advancedPc = false;
                    break;
                }
                default:
                    break;
            }
        }

        if (advancedPc && running != null) {
            running.setPc(running.getPc() + 1);
            running.setRunTime(running.getRunTime() + 1);
            running.setTimeSlice(running.getTimeSlice() - 1);
            updateCurrentInstructionPointer(running);
        }

        if (running != null && (running.getTimeSlice() <= 0 || running.getState() != PCB.ProcessState.RUNNING)) {
            if (running.getState() == PCB.ProcessState.RUNNING) {
                running.setTimeSlice(timeSlice);
                running.setState(PCB.ProcessState.READY);
                scheduler.add(running);
                updateCurrentInstructionPointer(running);
                if (interrupt.isNone()) {
                    interrupt = new CpuInterrupt(InterruptType.TIME_SLICE, running.getPid(), "时间片耗尽");
                }
            }
            running = null;
            schedule();
        }

        if (deviceManager == null && !deviceWaiters.isEmpty()) {
            List<String> toWake = new ArrayList<>();
            for (Map.Entry<String, List<PCB>> e : deviceWaiters.entrySet()) {
                String dev = e.getKey();
                List<PCB> waiters = e.getValue();
                for (PCB p : waiters) {
                    wakeupProcess(p.getPid());
                    changed.add(p);
                    updateCurrentInstructionPointer(p);
                }
                toWake.add(dev);
            }
            for (String d : toWake) deviceWaiters.remove(d);
        }

        PCB snapshot = running;
        return new ProcessTickResult(changed, snapshot, executedInstruction, interrupt);
    }

    @Override
    public synchronized int setTimeSlice(int size) {
        int old = timeSlice;
        timeSlice = size;
        return old;
    }

    @Override
    public synchronized int getTimeSlice() {
        return timeSlice;
    }

    @Override
    public synchronized void saveProcessContext(PCB process) {
        // 模拟：已用字段自动保存
    }

    @Override
    public synchronized void restoreProcessContext(PCB process) {
        // 模拟：已用字段自动恢复
    }

    @Override
    public synchronized void updateProcessRunTime(int pid) {
        PCB p = allProcesses.get(pid);
        if (p != null) p.setRunTime(p.getRunTime() + 1);
    }

    @Override
    public synchronized void setProcessState(int pid, PCB.ProcessState state) {
        PCB p = allProcesses.get(pid);
        if (p != null) p.setState(state);
    }

    @Override
    public synchronized void addToReadyQueue(PCB process) {
        scheduler.add(process);
    }

    @Override
    public synchronized PCB removeFromReadyQueue() {
        return scheduler.next();
    }

    @Override
    public synchronized void addToBlockedQueue(PCB process, String reason) {
        blockedQueues.computeIfAbsent(reason, k -> new LinkedList<>()).offer(process);
    }

    @Override
    public synchronized PCB removeFromBlockedQueue(int pid, String reason) {
        Queue<PCB> q = blockedQueues.get(reason);
        if (q == null) return null;
        for (PCB p : q) {
            if (p.getPid() == pid) {
                q.remove(p);
                return p;
            }
        }
        return null;
    }

    @Override
    public synchronized Map<String, Integer> getProcessCount() {
        Map<String, Integer> m = new HashMap<>();
        int run = running == null ? 0 : 1;
        m.put("running", run);
        m.put("ready", scheduler.size());
        int blocked = blockedQueues.values().stream().mapToInt(Queue::size).sum();
        m.put("blocked", blocked);
        m.put("terminated", terminatedProcesses.size());
        m.put("total", allProcesses.size() + terminatedProcesses.size());
        return m;
    }

    @Override
    public synchronized Map<String, Object> getSystemStatistics() {
        Map<String, Object> s = new HashMap<>();
        s.put("uptime", 0);
        s.put("processCount", getProcessCount());
        return s;
    }

    @Override
    public synchronized Map<String, Object> getProcessHistory(int pid) {
        Map<String, Object> h = new HashMap<>();
        PCB p = allProcesses.get(pid);
        if (p != null) {
            h.put("pid", p.getPid());
            h.put("runTime", p.getRunTime());
            h.put("state", p.getState());
        }
        return h;
    }

    @Override
    public synchronized void resetStatistics() {
        // noop
    }

    @Override
    public synchronized int allocatePid() {
        while (!recycledPids.isEmpty()) {
            int reuse = recycledPids.pollFirst();
            recycledPidSet.remove(reuse);
            if (!allProcesses.containsKey(reuse)) {
                return reuse;
            }
        }
        int start = pidCounter.get();
        for (int i = 0; i < maxPid; i++) {
            int id = pidCounter.getAndIncrement();
            if (id > maxPid) {
                pidCounter.set(1);
                id = pidCounter.getAndIncrement();
            }
            if (!allProcesses.containsKey(id) && !recycledPidSet.contains(id)) {
                return id;
            }
        }
        throw new RuntimeException("PID资源耗尽");
    }

    @Override
    public synchronized void releasePid(int pid) {
        if (pid <= 0 || pid > maxPid) return;
        if (allProcesses.containsKey(pid)) return;
        if (recycledPidSet.add(pid)) {
            recycledPids.addLast(pid);
        }
    }

    @Override
    public synchronized boolean processExists(int pid) {
        return allProcesses.containsKey(pid) || terminatedProcesses.containsKey(pid);
    }

    @Override
    public synchronized int getProcessProgress(int pid) {
        PCB p = allProcesses.get(pid);
        if (p == null) p = terminatedProcesses.get(pid);
        if (p == null) return 0;
        List<Instruction> instrs = p.getInstructions();
        if (instrs == null || instrs.isEmpty()) return 100;
        return Math.min(100, (p.getPc() * 100) / instrs.size());
    }

    private void recordTermination(PCB pcb) {
        if (pcb == null) return;
        pcb.setState(PCB.ProcessState.TERMINATED);
        pcb.setWaitingFor("");
        pcb.setTimeSlice(0);
        terminatedProcesses.put(pcb.getPid(), pcb);
        // keep a reasonable history to avoid unbounded growth
        while (terminatedProcesses.size() > 64) {
            Iterator<Integer> it = terminatedProcesses.keySet().iterator();
            if (!it.hasNext()) break;
            it.next();
            it.remove();
        }
    }

    private void updateCurrentInstructionPointer(PCB pcb) {
        if (pcb == null) return;
        java.util.List<Instruction> instrs = pcb.getInstructions();
        int pc = pcb.getPc();
        pcb.setCurrentInstructionIndex(pc);
        if (instrs != null && !instrs.isEmpty()) {
            if (pc >= 0 && pc < instrs.size()) {
                Instruction target = instrs.get(pc);
                if (target != null) {
                    String text = target.getOriginalText();
                    if (text == null || text.isEmpty()) text = target.toString();
                    pcb.setCurrentInstructionText(text);
                    return;
                }
            }
            Instruction fallback = instrs.get(Math.max(0, Math.min(instrs.size() - 1, pc - 1)));
            if (fallback != null) {
                String text = fallback.getOriginalText();
                if (text == null || text.isEmpty()) text = fallback.toString();
                pcb.setCurrentInstructionText(text);
                return;
            }
        }
        pcb.setCurrentInstructionText("");
    }

    private PCB finishRunningProcess(List<PCB> changed) {
        if (running == null) return null;
        // release devices held by this process
        if (deviceManager != null) {
            deviceManager.releaseAllProcessDevices(running.getPid());
        }
        recordTermination(running);
        changed.add(running);
        int pid = running.getPid();
        PCB finished = running;
        allProcesses.remove(pid);
        releasePid(pid);
        running = null;
        return finished;
    }
}
