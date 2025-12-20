package com.os.core.cpu;

/**
 * Interrupt categories emitted by the CPU emulator / process manager.
 */
public enum InterruptType {
    NONE,
    TIME_SLICE,
    IO_REQUEST,
    IO_COMPLETE,
    PROGRAM_END,
    SCHEDULER_IDLE
}
