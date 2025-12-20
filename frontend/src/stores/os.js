import { defineStore } from 'pinia';
import { ref } from 'vue';
import { kernelApi } from '../api/kernel';

export const useOsStore = defineStore('os', () => {
    const systemInfo = ref(null);
    const isRunning = ref(false);
    const lastTick = ref(null);
    const isFetching = ref(false);
    const errorCount = ref(0);

    const fetchInfo = async () => {
        if (isFetching.value) return;
        isFetching.value = true;
        try {
            const res = await kernelApi.getInfo();
            systemInfo.value = res.data;
            if (res.data && res.data.lastTick) {
                lastTick.value = res.data.lastTick;
            }
            // also fetch more up-to-date disk snapshot directly and merge
            try {
                const diskRes = await kernelApi.getDiskStatus();
                if (diskRes && diskRes.data) {
                    // ensure systemInfo exists
                    if (!systemInfo.value) systemInfo.value = {};
                    systemInfo.value.disk = diskRes.data;
                }
            } catch (e) {
                // ignore disk fetch errors but keep original info
            }
        } catch (e) {
            console.error("Failed to fetch OS info", e);
        } finally {
            isFetching.value = false;
        }
    };

    // Merge a disk snapshot (from operation response) into systemInfo to keep UI in sync
    const applyDiskSnapshot = (diskSnapshot) => {
        if (!diskSnapshot) return;
        if (!systemInfo.value) systemInfo.value = {};
        systemInfo.value.disk = diskSnapshot;
    };

    const tick = async () => {
        try {
            const res = await kernelApi.tick();
            lastTick.value = res.data;
            // After tick, refresh info
            await fetchInfo();
            errorCount.value = 0;
        } catch (e) {
            // console.error("Tick failed", e);
            errorCount.value++;
            if (errorCount.value >= 3) {
                console.warn("Backend connection failed multiple times. Stopping simulation to prevent log spam.");
                stopSimulation();
            }
        }
    };

    const startSimulation = () => {
        if (isRunning.value) return;
        isRunning.value = true;
        // Poll every 1s or 500ms
        const interval = setInterval(async () => {
            if (!isRunning.value) {
                clearInterval(interval);
                return;
            }
            await tick();
        }, 1000);
    };

    const stopSimulation = () => {
        isRunning.value = false;
    };

    return {
        systemInfo,
        isRunning,
        lastTick,
        isFetching,
        fetchInfo,
        applyDiskSnapshot,
        tick,
        startSimulation,
        stopSimulation
    };
});
