<template>
  <div v-if="powerState === 'off'" class="w-screen h-screen bg-black flex items-center justify-center">
    <button 
      @click="powerOn" 
      class="group relative flex flex-col items-center gap-4 transition-all duration-500 hover:scale-110"
    >
      <div class="w-24 h-24 rounded-full border-4 border-white/20 flex items-center justify-center group-hover:border-white/60 group-hover:shadow-[0_0_50px_rgba(255,255,255,0.3)] transition-all duration-500 bg-white/5">
        <svg xmlns="http://www.w3.org/2000/svg" class="w-10 h-10 text-white/50 group-hover:text-white transition-colors duration-500" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <path d="M18.36 6.64a9 9 0 1 1-12.73 0"></path>
          <line x1="12" y1="2" x2="12" y2="12"></line>
        </svg>
      </div>
      <span class="text-white/40 font-light tracking-[0.2em] text-sm group-hover:text-white/80 transition-colors">POWER ON</span>
    </button>
  </div>
  
  <BootScreen v-else-if="powerState === 'booting'" @finish="onBootFinish" />
  
  <ShutdownScreen v-else-if="powerState === 'shutting-down'" @finish="onShutdownFinish" />
  
  <Desktop v-else @shutdown="onShutdown" />
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { useWindowsStore } from './stores/windows';
import { useOsStore } from './stores/os';
import Desktop from './views/Desktop.vue';
import BootScreen from './components/System/BootScreen.vue';
import ShutdownScreen from './components/System/ShutdownScreen.vue';

let tauriInvoke = null;
let tauriWindow = null;

const loadTauriApis = async () => {
  if (!window.__TAURI__?.invoke) return false;
  try {
    const [{ invoke }, { appWindow }] = await Promise.all([
      import('@tauri-apps/api/tauri'),
      import('@tauri-apps/api/window')
    ]);
    tauriInvoke = invoke;
    tauriWindow = appWindow;
    console.log('[tauri] API 加载成功');
    return true;
  } catch (err) {
    console.error('加载 Tauri API 失败，尝试使用全局 __TAURI__ 兜底', err);
    // 兜底：直接用全局 __TAURI__ 上的 invoke / window
    if (window.__TAURI__?.invoke) tauriInvoke = window.__TAURI__.invoke;
    if (window.__TAURI__?.window?.getCurrent) tauriWindow = window.__TAURI__.window.getCurrent();
    return Boolean(tauriInvoke);
  }
};

const SESSION_KEY = 'os-power-state';

// 'off' | 'booting' | 'on' | 'shutting-down'
const savedState = typeof window !== 'undefined' ? sessionStorage.getItem(SESSION_KEY) : null;
const powerState = ref(savedState === 'on' || savedState === 'booting' ? 'on' : 'off');
const windowsStore = useWindowsStore();
const osStore = useOsStore();

const ensureBackend = async () => {
  // 在 Tauri 环境下拉起后端，浏览器模式下跳过
  if (!tauriInvoke) return;
  try {
    await tauriInvoke('ensure_backend');
    console.log('[tauri] ensure_backend 调用完成');
  } catch (err) {
    console.error('启动后端失败，请确认已在 IDEA 里打包 os-core 或设置 OS_BACKEND_PATH', err);
  }
};

const requestShutdown = async () => {
  // 同时请求后端退出 + 关闭当前窗口，确保关机后立即退出
  if (tauriInvoke) {
    try {
      await tauriInvoke('shutdown_all');
      console.log('[tauri] shutdown_all 调用完成');
    } catch (err) {
      console.error('关闭应用时停止后端失败', err);
    }
  }

  // 双保险关闭窗口（Tauri 首选，其次浏览器）
  if (tauriWindow?.close) {
    try { await tauriWindow.close(); } catch (_) {}
  } else if (window.__TAURI__?.window?.getCurrent) {
    try { await window.__TAURI__.window.getCurrent().close(); } catch (_) {}
  } else {
    try { window.close(); } catch (_) {}
  }
};

const powerOn = () => {
  windowsStore.closeAll(); // 确保开机是干净的窗口状态
  powerState.value = 'booting';
  sessionStorage.setItem(SESSION_KEY, 'booting');
};

const onBootFinish = () => {
  powerState.value = 'on';
  sessionStorage.setItem(SESSION_KEY, 'on');
  osStore.startSimulation();
  osStore.fetchInfo();
};

const onShutdown = async () => {
  powerState.value = 'shutting-down';
  sessionStorage.setItem(SESSION_KEY, 'shutting-down');
  windowsStore.closeAll();
  osStore.stopSimulation();

  // 发送后端优雅关机请求（前端发 HTTP）
  try {
    const kernel = await import('./api/kernel');
    await kernel.kernelApi.shutdown();
    console.log('[frontend] 已发送后端关闭请求');
  } catch (e) {
    console.error('[frontend] 发送后端关闭请求失败', e);
  }

  // 等待 300ms 后再关闭窗口
  await new Promise((r) => setTimeout(r, 300));

  // 尝试通过 Tauri API 关闭窗口（多种兜底）
  if (tauriInvoke) {
    try {
      await tauriInvoke('force_close');
      return;
    } catch (e) {
      console.error('tauri force_close 失败', e);
    }
  }

  if (tauriWindow?.close) {
    try { await tauriWindow.close(); return; } catch (e) { console.error('tauriWindow.close 失败', e); }
  }

  if (window.__TAURI__?.window?.getCurrent) {
    try { await window.__TAURI__.window.getCurrent().close(); return; } catch (e) { console.error('__TAURI__.window.getCurrent.close 失败', e); }
  }

  try { window.close(); } catch (e) { console.error('window.close 失败', e); }
};

const onShutdownFinish = async () => {
  powerState.value = 'off';
  sessionStorage.removeItem(SESSION_KEY);
  await requestShutdown();
};

onMounted(() => {
  // 应用冷启动展示开机页，刷新时保持已开机状态并补上后端/定时器
  loadTauriApis().then(() => {
    ensureBackend();
  });
  if (powerState.value === 'on') {
    osStore.startSimulation();
    osStore.fetchInfo();
  }
});
</script>

<style>
/* Global unselectable text */
body {
  user-select: none;
  -webkit-user-select: none;
}

/* Allow selection in inputs */
input, textarea, [contenteditable] {
  user-select: text;
  -webkit-user-select: text;
}
</style>

