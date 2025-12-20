package com.os.core.cpu;

import java.util.HashMap;
import java.util.Map;

/**
 * Captures CPU register and interrupt state after executing a tick.
 */
public class CpuTickSnapshot {
    private final long timestamp;
    private final Integer currentPid;
    private final int programCounter;
    private final String psw;
    private final int accumulator;
    private final CpuInterrupt interrupt;

    public CpuTickSnapshot(long timestamp, Integer currentPid, int programCounter,
                           String psw, int accumulator, CpuInterrupt interrupt) {
        this.timestamp = timestamp;
        this.currentPid = currentPid;
        this.programCounter = programCounter;
        this.psw = psw;
        this.accumulator = accumulator;
        this.interrupt = interrupt == null ? CpuInterrupt.none() : interrupt;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public Integer getCurrentPid() {
        return currentPid;
    }

    public int getProgramCounter() {
        return programCounter;
    }

    public String getPsw() {
        return psw;
    }

    public int getAccumulator() {
        return accumulator;
    }

    public CpuInterrupt getInterrupt() {
        return interrupt;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("timestamp", timestamp);
        map.put("pid", currentPid);
        map.put("pc", programCounter);
        map.put("psw", psw);
        map.put("acc", accumulator);
        if (!interrupt.isNone()) {
            map.put("interrupt", interrupt.toMap());
        }
        return map;
    }
}
