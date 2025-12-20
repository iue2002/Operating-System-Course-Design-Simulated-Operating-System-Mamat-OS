<template>
  <div class="w-screen h-screen overflow-hidden relative bg-cover bg-center transition-all duration-1000" style="background-image: url('https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?q=80&w=2564&auto=format&fit=crop');">
    <!-- Overlay for better text contrast -->
    <div class="absolute inset-0 bg-black/20 backdrop-blur-[1px]"></div>

    <!-- Desktop Icons Area -->
    <div class="absolute inset-0 p-4 grid grid-flow-col grid-rows-[repeat(auto-fill,100px)] gap-4 content-start items-start w-fit">
      <DesktopIcon 
        v-for="app in desktopApps" 
        :key="app.name" 
        :label="app.name"
        @open="launchApp(app)"
      >
        <template #icon>
          <component :is="app.icon" class="w-6 h-6 text-white" />
        </template>
      </DesktopIcon>
    </div>

    <!-- Windows Layer -->
    <WindowFrame
      v-for="win in windows"
      :key="win.id"
      :id="win.id"
      :title="win.title"
      :icon="win.props.icon"
      :z-index="win.zIndex"
      :is-active="activeWindowId === win.id"
      :is-minimized="win.isMinimized"
      @focus="store.focusWindow"
      @close="store.closeWindow"
      @minimize="store.minimizeWindow"
    >
      <component :is="win.component" v-bind="win.props" />
    </WindowFrame>

    <!-- Start Menu -->
    <StartMenu 
      v-if="isStartMenuOpen" 
      :apps="allApps" 
      @launch="launchApp"
      @shutdown="$emit('shutdown')"
      @about="showAboutModal = true"
      v-click-outside="closeStartMenu"
    />

    <!-- Taskbar -->
    <Taskbar @toggleStart="toggleStartMenu" />

    <FloatingAssistant />

    <!-- About Modal -->
    <div v-if="showAboutModal" class="fixed inset-0 bg-black/50 backdrop-blur-sm flex items-center justify-center z-[99999]" @click.self="showAboutModal = false">
      <div class="bg-os-surface border border-os-border rounded-2xl p-6 w-96 shadow-2xl animate-pop-in">
        <div class="flex flex-col items-center text-center gap-4">
          <div class="w-16 h-16 bg-gradient-to-br from-blue-500 to-purple-600 rounded-2xl flex items-center justify-center shadow-lg">
            <Cpu class="w-8 h-8 text-white" />
          </div>
          <div>
            <h2 class="text-xl font-bold text-white mb-1">麦麦提OS</h2>
            <p class="text-os-muted text-sm">v1.0.0</p>
          </div>
          <div class="w-full h-[1px] bg-white/10"></div>
          <div class="text-sm text-os-text space-y-1">
            <p class="text-os-muted">开发者</p>
            <p class="font-medium">23软工 麦麦提艾力</p>
            <p class="font-medium">23软工 艾力开木江</p>
            <p class="font-medium">23软工 艾力亚尔</p>
          </div>
          <button 
            @click="showAboutModal = false"
            class="mt-2 px-6 py-2 bg-white/10 hover:bg-white/20 rounded-lg text-sm font-medium transition-colors"
          >
            关闭
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, markRaw } from 'vue';
import { useWindowsStore } from '../stores/windows';
import { storeToRefs } from 'pinia';
import { Folder, Cpu } from 'lucide-vue-next';

// Components
import DesktopIcon from '../components/Desktop/DesktopIcon.vue';
import WindowFrame from '../components/Desktop/WindowFrame.vue';
import Taskbar from '../components/Desktop/Taskbar.vue';
import StartMenu from '../components/Desktop/StartMenu.vue';

// Apps
import FileExplorer from '../components/Apps/FileExplorer.vue';
// TextEditor removed per requirements
import ProcessMonitor from '../components/Apps/ProcessMonitor.vue';
import MemoryMonitor from '../components/Apps/MemoryMonitor.vue';
import DeviceStatus from '../components/Apps/DeviceStatus.vue';
import FloatingAssistant from '../components/Apps/FloatingAssistant.vue';

const store = useWindowsStore();
const { windows, activeWindowId } = storeToRefs(store);

const isStartMenuOpen = ref(false);
const showAboutModal = ref(false);

const allApps = [
  { name: '文件资源管理器', icon: markRaw(Folder), component: markRaw(FileExplorer) },
  { name: '任务管理器', icon: markRaw(Cpu), component: markRaw(ProcessMonitor) },
  { name: 'RAM和CPU监视器', icon: markRaw(Cpu), component: markRaw(MemoryMonitor) },
  { name: '设备状态', icon: markRaw(Cpu), component: markRaw(DeviceStatus) },
];

const desktopApps = allApps;

const launchApp = (app) => {
  store.openWindow(app.name, app.component, { icon: app.icon });
  isStartMenuOpen.value = false;
};

const toggleStartMenu = () => {
  isStartMenuOpen.value = !isStartMenuOpen.value;
};

const closeStartMenu = () => {
  isStartMenuOpen.value = false;
};

// Simple click outside directive implementation if not available
const vClickOutside = {
  mounted(el, binding) {
    el.clickOutsideEvent = function(event) {
      if (!(el === event.target || el.contains(event.target))) {
        binding.value(event, el);
      }
    };
    document.body.addEventListener('click', el.clickOutsideEvent);
  },
  unmounted(el) {
    document.body.removeEventListener('click', el.clickOutsideEvent);
  },
};
</script>
