package com.os.tests;

import com.os.core.cpu.InterruptType;
import com.os.core.interfaces.DeviceManagerAPI;
import com.os.core.models.PCB;
import com.os.core.models.ProcessTickResult;
import com.os.device.DeviceManagerImpl;
import com.os.memory.MemoryManagerImpl;
import com.os.process.ProcessManagerImpl;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Requirement-driven regression tests that cover the course-mandated
 * process management and paged memory behaviors.
 */
class ProcessMemoryComplianceTest {

    @Test
    void systemZoneStatisticsReflectSplit() {
        MemoryManagerImpl memory = new MemoryManagerImpl();

        Map<String, Object> status = memory.getMemoryStatus();
        assertEquals(32, asInt(status, "totalPages"));
        assertEquals(10, asInt(status, "systemPageCount"));
        assertEquals(10, asInt(status, "systemFreePagesCount"));
        assertEquals(22, asInt(status, "userTotalPages"));

        int sysPage = memory.allocateSystemPage(1);
        assertTrue(sysPage >= 0, "First system page allocation should succeed");
        memory.createPageTable(1, 2);
        memory.allocatePages(1, 2, null);

        Map<String, Object> after = memory.getMemoryStatus();
        assertEquals(9, asInt(after, "systemFreePagesCount"));
        assertEquals(1, asInt(after, "systemUsedPagesCount"));
        assertEquals(2, asInt(after, "userUsedPagesCount"));
        assertEquals(20, asInt(after, "freePagesCount"));

        memory.releaseSystemPage(1);
        memory.destroyPageTable(1);
        Map<String, Object> finalStatus = memory.getMemoryStatus();
        assertEquals(10, asInt(finalStatus, "systemFreePagesCount"));
        assertEquals(0, asInt(finalStatus, "systemUsedPagesCount"));
        assertEquals(22, asInt(finalStatus, "freePagesCount"));
    }

    @Test
    void systemPageLimitIsEnforced() {
        MemoryManagerImpl memory = new MemoryManagerImpl();
        for (int pid = 1; pid <= 10; pid++) {
            int page = memory.allocateSystemPage(pid);
            assertTrue(page >= 0, "System page should be reserved for pid " + pid);
        }
        assertEquals(0, memory.getFreeSystemPageCount(), "All 10 PCB slots must be occupied");
        assertEquals(-1, memory.allocateSystemPage(99), "11th process must be rejected");

        assertTrue(memory.releaseSystemPage(5));
        assertEquals(1, memory.getFreeSystemPageCount());
        assertTrue(memory.allocateSystemPage(11) >= 0, "Freed slot should be reusable");
    }

    @Test
    void roundRobinTimeSlicePreemptionOccurs() {
        DeviceManagerAPI deviceManager = new DeviceManagerImpl();
        ProcessManagerImpl pm = new ProcessManagerImpl(deviceManager);
        PCB p1 = pm.createProcess(cpuBoundProgram(10), 1, null);
        PCB p2 = pm.createProcess(cpuBoundProgram(10), 1, null);
        assertNotEquals(p1.getPid(), p2.getPid());

        AtomicBoolean timeSliceInterruptSeen = new AtomicBoolean(false);
        for (int tick = 0; tick < 40; tick++) {
            ProcessTickResult result = pm.onTick();
            if (result != null && result.getInterrupt() != null &&
                    result.getInterrupt().getType() == InterruptType.TIME_SLICE) {
                timeSliceInterruptSeen.set(true);
            }
            if (pm.getProcessCount().getOrDefault("terminated", 0) >= 2) {
                break;
            }
        }

        assertTrue(timeSliceInterruptSeen.get(), "RR scheduler must emit TIME_SLICE interrupts");
        assertEquals(2, pm.getProcessCount().getOrDefault("terminated", 0));
    }

    @Test
    void deviceIoBlocksAndWakesProcesses() {
        DeviceManagerImpl deviceManager = new DeviceManagerImpl();
        ProcessManagerImpl pm = new ProcessManagerImpl(deviceManager);
        PCB pcb = pm.createProcess("x=0;\n!A2;\nx++;\nend.", 1, null);

        pm.onTick(); // execute x=0
        pm.onTick(); // hit !A2 and block
        assertEquals(PCB.ProcessState.BLOCKED, pcb.getState(), "Process must be blocked waiting for device A");

        // simulate hardware ticks and manual wake-ups like OSKernelImpl.systemTick()
        for (int tick = 0; tick < 3; tick++) {
            List<Map<String, Object>> events = deviceManager.onTick();
            for (Map<String, Object> event : events) {
                if ("completed".equals(event.get("status"))) {
                    pm.wakeupProcess((Integer) event.get("pid"));
                }
            }
        }

        assertEquals(PCB.ProcessState.READY, pcb.getState(), "Device completion should wake the process");

        for (int i = 0; i < 10 && pcb.getState() != PCB.ProcessState.TERMINATED; i++) {
            pm.onTick();
        }
        assertEquals(PCB.ProcessState.TERMINATED, pcb.getState(), "Process should finish after device I/O");
    }

    private static int asInt(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        fail("Missing integer key: " + key);
        return -1;
    }

    private static String cpuBoundProgram(int increments) {
        StringBuilder sb = new StringBuilder("x=0;\n");
        for (int i = 0; i < increments; i++) {
            sb.append("x++;\n");
        }
        sb.append("end.");
        return sb.toString();
    }
}
