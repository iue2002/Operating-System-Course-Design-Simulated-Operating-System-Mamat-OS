package com.os.process;

import com.os.core.models.PCB;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

/**
 * 简单的 Round-Robin 调度器（FIFO就绪队列）
 */
public class RRScheduler implements Scheduler {
    private final Queue<PCB> readyQueue = new LinkedList<>();

    @Override
    public synchronized void add(PCB pcb) {
        if (pcb != null) {
            pcb.setState(PCB.ProcessState.READY);
            readyQueue.offer(pcb);
        }
    }

    @Override
    public synchronized PCB next() {
        return readyQueue.poll();
    }

    @Override
    public synchronized void remove(int pid) {
        readyQueue.removeIf(p -> p.getPid() == pid);
    }

    @Override
    public synchronized void clear() {
        readyQueue.clear();
    }

    @Override
    public synchronized int size() {
        return readyQueue.size();
    }

    @Override
    public synchronized List<PCB> snapshot() {
        return new ArrayList<>(readyQueue);
    }
}

