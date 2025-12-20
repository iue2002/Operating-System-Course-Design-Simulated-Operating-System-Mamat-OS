package com.os.core.cpu;

import com.os.core.models.PCB;
import com.os.core.models.ProcessTickResult;

/**
 * Minimal CPU emulator that tracks registers/PSW for telemetry.
 */
public class CpuEmulator {
    private Integer currentPid;
    private int pc;
    private int accumulator;
    private String psw = "IDLE";

    public CpuTickSnapshot tick(ProcessTickResult tickResult, long timestamp) {
        PCB running = tickResult == null ? null : tickResult.getRunningProcess();
        if (running != null) {
            currentPid = running.getPid();
            pc = running.getPc();
            accumulator = running.getVariable('x');
            psw = running.getState().name();
        } else {
            currentPid = null;
            pc = 0;
            accumulator = 0;
            psw = "IDLE";
        }
        return new CpuTickSnapshot(timestamp, currentPid, pc, psw, accumulator,
                tickResult == null ? CpuInterrupt.none() : tickResult.getInterrupt());
    }

    public Integer getCurrentPid() {
        return currentPid;
    }

    public int getPc() {
        return pc;
    }

    public int getAccumulator() {
        return accumulator;
    }

    public String getPsw() {
        return psw;
    }
}
