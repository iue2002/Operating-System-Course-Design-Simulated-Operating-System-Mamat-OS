<template>
  <div class="absolute bottom-16 left-1/2 -translate-x-1/2 w-80 bg-os-surface/80 backdrop-blur-2xl border border-os-border rounded-2xl shadow-2xl p-4 flex flex-col gap-2 z-[10000] animate-slide-up origin-bottom">
    <div class="text-xs font-bold text-os-muted uppercase tracking-wider mb-2 px-2">应用程序</div>
    
    <div class="grid grid-cols-1 gap-1">
      <button 
        v-for="app in apps" 
        :key="app.name"
        class="flex items-center gap-3 p-3 rounded-xl hover:bg-white/10 transition-all duration-200 text-left group active:scale-95"
        @click="$emit('launch', app)"
      >
        <div class="p-2 rounded-lg bg-gradient-to-br from-os-accent to-blue-600 shadow-lg group-hover:shadow-blue-500/30 transition-shadow">
          <component :is="app.icon" class="w-5 h-5 text-white" />
        </div>
        <span class="text-sm font-medium text-os-text group-hover:text-white transition-colors">{{ app.name }}</span>
      </button>
    </div>

    <div class="h-[1px] bg-os-border my-2 mx-2"></div>

    <div class="text-xs font-bold text-os-muted uppercase tracking-wider mb-2 px-2">系统</div>

    <div class="flex gap-2 px-1">
      <button 
        @click="$emit('shutdown')"
        class="flex-1 flex items-center justify-center gap-2 p-3 rounded-xl bg-red-500/10 hover:bg-red-500/20 text-red-400 hover:text-red-300 transition-all active:scale-95 group border border-red-500/10"
      >
        <Power class="w-4 h-4" />
        <span class="text-sm font-medium">关机</span>
      </button>
      
      <button 
        @click="$emit('about')"
        class="flex-1 flex items-center justify-center gap-2 p-3 rounded-xl bg-white/5 hover:bg-white/10 text-os-muted hover:text-white transition-all active:scale-95 border border-white/5"
      >
        <Info class="w-4 h-4" />
        <span class="text-sm font-medium">关于</span>
      </button>
    </div>
  </div>
</template>

<script setup>
import { Power, Info } from 'lucide-vue-next';

defineProps({
  apps: Array
});

defineEmits(['launch', 'shutdown', 'about']);
</script>
