package com.os.core.cpu;

import java.util.HashMap;
import java.util.Map;

/**
 * Simple immutable interrupt descriptor used for telemetry and UI.
 */
public class CpuInterrupt {
    private final InterruptType type;
    private final Integer pid;
    private final String reason;

    public CpuInterrupt(InterruptType type, Integer pid, String reason) {
        this.type = type == null ? InterruptType.NONE : type;
        this.pid = pid;
        this.reason = reason;
    }

    public InterruptType getType() {
        return type;
    }

    public Integer getPid() {
        return pid;
    }

    public String getReason() {
        return reason;
    }

    public boolean isNone() {
        return type == InterruptType.NONE;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("type", type.name());
        map.put("pid", pid);
        map.put("reason", reason);
        return map;
    }

    public static CpuInterrupt none() {
        return new CpuInterrupt(InterruptType.NONE, null, null);
    }
}
