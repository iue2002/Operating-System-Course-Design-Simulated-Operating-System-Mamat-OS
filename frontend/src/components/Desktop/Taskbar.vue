<template>
  <div class="h-16 flex items-end justify-center absolute bottom-2 w-full z-[9999] pointer-events-none">
    <!-- Dock Container -->
    <div class="h-14 bg-os-surface/70 backdrop-blur-2xl border border-white/10 rounded-2xl flex items-center px-3 gap-3 shadow-2xl pointer-events-auto transition-all hover:bg-os-surface/80 hover:scale-[1.01]">
      
      <!-- Start Button -->
      <button 
        class="p-2 rounded-xl hover:bg-white/10 transition-all active:scale-90 flex items-center justify-center group relative"
        @click.stop="$emit('toggleStart')"
      >
        <div class="absolute inset-0 bg-os-accent/20 rounded-xl blur-md opacity-0 group-hover:opacity-100 transition-opacity"></div>
        <LayoutGrid class="w-6 h-6 text-os-accent relative z-10" />
      </button>

      <div class="w-[1px] h-8 bg-white/10 mx-1"></div>

      <!-- Running Apps -->
      <div class="flex items-center gap-2">
        <button
          v-for="win in windows"
          :key="win.id"
          :data-win-id="win.id"
          class="group relative p-2 rounded-xl transition-all duration-300 hover:bg-white/10 active:scale-90"
          :class="activeWindowId === win.id && !win.isMinimized ? 'bg-white/10' : ''"
          @click="handleTaskClick(win)"
        >
          <!-- App Icon -->
          <component :is="win.props.icon || AppWindow" class="w-6 h-6 text-white relative z-10 transition-transform group-hover:-translate-y-1" />
          
          <!-- Active Dot -->
          <div 
            class="absolute -bottom-1 left-1/2 -translate-x-1/2 w-1 h-1 rounded-full bg-os-accent transition-all duration-300"
            :class="!win.isMinimized ? 'opacity-100 scale-100' : 'opacity-0 scale-0'"
          ></div>
          
          <!-- Tooltip -->
          <div class="absolute -top-10 left-1/2 -translate-x-1/2 bg-os-surface/90 backdrop-blur px-2 py-1 rounded text-[10px] text-white opacity-0 group-hover:opacity-100 transition-opacity pointer-events-none whitespace-nowrap border border-white/10">
            {{ win.title }}
          </div>
        </button>
      </div>
    </div>

    <!-- System Tray (Absolute Right) -->
    <div class="absolute right-4 bottom-4 h-10 bg-os-surface/70 backdrop-blur-xl border border-white/10 rounded-xl flex items-center px-4 gap-4 pointer-events-auto shadow-lg">
      <div class="flex flex-col items-end">
        <span class="text-sm font-medium text-white leading-none">{{ time }}</span>
        <span class="text-[10px] text-os-muted leading-none mt-1">{{ date }}</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue';
import { LayoutGrid, AppWindow } from 'lucide-vue-next';
import { useWindowsStore } from '../../stores/windows';
import { storeToRefs } from 'pinia';

const store = useWindowsStore();
const { windows, activeWindowId } = storeToRefs(store);

const handleTaskClick = (win) => {
  store.toggleMinimize(win.id);
};

// Clock
const time = ref('');
const date = ref('');
let timer;

const updateTime = () => {
  const now = new Date();
  time.value = now.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  date.value = now.toLocaleDateString();
};

onMounted(() => {
  updateTime();
  timer = setInterval(updateTime, 1000);
});

onUnmounted(() => {
  clearInterval(timer);
});
</script>
