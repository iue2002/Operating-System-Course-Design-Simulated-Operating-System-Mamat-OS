package com.os.tests;

import com.os.core.impl.OSKernelImpl;
import com.os.core.interfaces.OSKernel;

import java.util.Map;

public class IntegrationRunner {
    public static void main(String[] args) throws Exception {
        System.out.println("=== Integration Runner ===");
        OSKernelImpl kernel = new OSKernelImpl();
        kernel.initialize();
        kernel.start();

        System.out.println("\n-- Scenario 1: create multi-level dir, write executable, run by path --");
        Map<String,Object> r1 = kernel.makeDirCmd("/T1", null);
        Map<String,Object> r2 = kernel.makeDirCmd("/T1/D1", null);
        System.out.println("mkdir results: " + r1 + ", " + r2);

        String prog1 = "x=3;\nx++;\n!A1;\nx++;\nend.";
        Map<String,Object> createRes = kernel.createFileCmd("/T1/D1/PRG.TX", prog1, true, true);
        System.out.println("create file result: " + createRes);

        Map<String,Object> runRes = kernel.runProgramCmd("/T1/D1/PRG.TX");
        System.out.println("run result: " + runRes);
        Integer pid = runRes.containsKey("pid") ? (Integer) runRes.get("pid") : null;

        // tick some times to allow I/O and termination
        for (int t=0;t<10;t++) {
            Map<String,Object> tick = kernel.systemTick();
            System.out.println("Tick " + t + " -> deviceEvents=" + tick.get("deviceEvents") + ", processChanges=" + tick.get("processChanges"));
            Thread.sleep(50);
        }

        if (pid != null) {
            String outPath = "out" + pid + ".TX";
            Map<String,Object> readOut = kernel.readFileCmd(outPath);
            System.out.println("Read output file " + outPath + " -> " + readOut);
        }

        System.out.println("\n-- Scenario 2: device blocking/wakeup demonstration --");
        String prog2 = "y=0;\n!A2;\ny++;\nend.";
        Map<String,Object> cr2 = kernel.createFileCmd("/T1/D1/PRG2.TX", prog2, true, true);
        System.out.println("create PRG2 -> " + cr2);
        Map<String,Object> run2 = kernel.runProgramCmd("/T1/D1/PRG2.TX");
        System.out.println("run PRG2 -> " + run2);
        for (int t=0;t<8;t++) {
            Map<String,Object> tick = kernel.systemTick();
            System.out.println("Tick " + t + " -> deviceEvents=" + tick.get("deviceEvents") + ", processChanges=" + tick.get("processChanges"));
            Thread.sleep(50);
        }

        System.out.println("\n-- Scenario 3: memory allocation and release --");
        Map<String,Object> beforeMem = kernel.getMemoryManager().getMemoryStatus();
        System.out.println("Memory before: " + beforeMem);
        Map<String,Object> runInline = kernel.createAndRunProcess("z=0; z++; end.");
        System.out.println("create inline process -> " + runInline);
        Integer pid3 = runInline.containsKey("pid") ? (Integer) runInline.get("pid") : null;
        // allocate some pages for pid3 (best-effort)
        try {
            if (pid3 != null) {
                kernel.getMemoryManager().createPageTable(pid3, 4);
                kernel.getMemoryManager().allocatePages(pid3, 4, null);
            }
        } catch (Exception e) {
            System.out.println("Memory alloc warning: " + e.getMessage());
        }
        Map<String,Object> midMem = kernel.getMemoryManager().getMemoryStatus();
        System.out.println("Memory mid: " + midMem);

        // run a few ticks then ensure termination and cleanup
        for (int t=0;t<6;t++) {
            Map<String,Object> tick = kernel.systemTick();
            System.out.println("Tick " + t + " -> " + tick.get("processChanges"));
            Thread.sleep(50);
        }

        Map<String,Object> afterMem = kernel.getMemoryManager().getMemoryStatus();
        System.out.println("Memory after: " + afterMem);
        try {
            int beforeFree = ((Number) beforeMem.getOrDefault("freePagesCount", 0)).intValue();
            int afterFree = ((Number) afterMem.getOrDefault("freePagesCount", 0)).intValue();
            if (afterFree >= beforeFree) {
                System.out.println("Memory cleanup check: OK (free pages " + afterFree + ")");
            } else {
                System.out.println("Memory cleanup check: FAILED (free pages " + afterFree + " < " + beforeFree + ")");
            }
        } catch (Exception ignore) {}

        System.out.println("\n=== Integration Runner finished ===");
        kernel.stop(false);
    }
}

