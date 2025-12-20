package com.os;

import com.os.core.impl.OSKernelImpl;
import com.os.web.KernelHttpServer;

public class Main {
    public static void main(String[] args) throws Exception {
        // Read port from environment (default 8080)
        String portEnv = System.getenv("OS_HTTP_PORT");
        int port = 8080;
        if (portEnv != null && !portEnv.isBlank()) {
            try { port = Integer.parseInt(portEnv); } catch (NumberFormatException ignored) {}
        }

        // Initialize kernel
        OSKernelImpl kernel = new OSKernelImpl();
        kernel.initialize();
        kernel.start();

        // Start HTTP server exposing kernel APIs
        KernelHttpServer server = new KernelHttpServer(kernel, port);
        server.start();

        System.out.println("Backend started: HTTP server listening on port " + port);

        // Add shutdown hook to persist disk and stop server nicely
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutdown requested: stopping kernel and HTTP server...");
            try { kernel.stop(false); } catch (Exception ignored) {}
            try { server.stop(1); } catch (Exception ignored) {}
            System.out.println("后端已停止");
        }));

        // Keep main thread alive until interrupted
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            // Exit on interrupt
        }
    }
}