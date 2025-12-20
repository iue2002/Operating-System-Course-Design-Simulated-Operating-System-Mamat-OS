package com.os.core.models;

import com.os.core.cpu.CpuInterrupt;

import java.util.Collections;
import java.util.List;

/**
 * Snapshot describing what happened during a single process manager tick.
 */
public class ProcessTickResult {
    private final List<PCB> changedProcesses;
    private final PCB runningProcess;
    private final Instruction currentInstruction;
    private final CpuInterrupt interrupt;

    public ProcessTickResult(List<PCB> changedProcesses,
                             PCB runningProcess,
                             Instruction currentInstruction,
                             CpuInterrupt interrupt) {
        this.changedProcesses = changedProcesses == null ? Collections.emptyList() : changedProcesses;
        this.runningProcess = runningProcess;
        this.currentInstruction = currentInstruction;
        this.interrupt = interrupt == null ? CpuInterrupt.none() : interrupt;
    }

    public List<PCB> getChangedProcesses() {
        return changedProcesses;
    }

    public PCB getRunningProcess() {
        return runningProcess;
    }

    public Instruction getCurrentInstruction() {
        return currentInstruction;
    }

    public CpuInterrupt getInterrupt() {
        return interrupt;
    }
}
