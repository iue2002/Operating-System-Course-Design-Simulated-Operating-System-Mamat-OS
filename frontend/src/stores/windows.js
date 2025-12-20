import { defineStore } from 'pinia';
import { ref, computed } from 'vue';

export const useWindowsStore = defineStore('windows', () => {
    const windows = ref([]); // { id, title, component, props, isMinimized, zIndex }
    const nextId = ref(1);
    const activeWindowId = ref(null);
    const highestZIndex = ref(100);

    const openWindow = (title, component, props = {}) => {
        const id = nextId.value++;
        highestZIndex.value++;

        const newWindow = {
            id,
            title,
            component,
            props,
            isMinimized: false,
            zIndex: highestZIndex.value,
            position: { x: 50 + (windows.value.length * 20), y: 50 + (windows.value.length * 20) } // Cascade
        };

        windows.value.push(newWindow);
        activeWindowId.value = id;
        return id;
    };

    const closeWindow = (id) => {
        const index = windows.value.findIndex(w => w.id === id);
        if (index !== -1) {
            windows.value.splice(index, 1);
        }
        if (activeWindowId.value === id) {
            activeWindowId.value = null;
        }
    };

    const focusWindow = (id) => {
        const win = windows.value.find(w => w.id === id);
        if (win) {
            highestZIndex.value++;
            win.zIndex = highestZIndex.value;
            activeWindowId.value = id;
            win.isMinimized = false;
        }
    };

    const minimizeWindow = (id) => {
        const win = windows.value.find(w => w.id === id);
        if (win) {
            win.isMinimized = true;
            activeWindowId.value = null;
        }
    };

    const toggleMinimize = (id) => {
        const win = windows.value.find(w => w.id === id);
        if (win) {
            if (win.isMinimized) {
                focusWindow(id);
            } else {
                if (activeWindowId.value === id) {
                    minimizeWindow(id);
                } else {
                    focusWindow(id);
                }
            }
        }
    };

    const closeAll = () => {
        windows.value = [];
        activeWindowId.value = null;
        nextId.value = 1;
        highestZIndex.value = 100;
    };

    return {
        windows,
        activeWindowId,
        openWindow,
        closeWindow,
        focusWindow,
        minimizeWindow,
        toggleMinimize,
        closeAll
    };
});
