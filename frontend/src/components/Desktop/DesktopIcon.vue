<template>
  <div 
    class="flex flex-col items-center gap-1 p-2 rounded hover:bg-white/10 cursor-pointer w-24 group transition-colors"
    @dblclick="$emit('open')"
  >
    <div class="w-12 h-12 bg-gradient-to-br from-os-accent to-blue-600 rounded-xl shadow-lg flex items-center justify-center group-hover:scale-105 transition-transform">
      <slot name="icon">
        <File class="w-6 h-6 text-white" />
      </slot>
    </div>
    <span class="text-xs text-center text-white drop-shadow-md select-none break-words w-full">{{ label }}</span>
  </div>
</template>

<script setup>
import { File } from 'lucide-vue-next';

defineProps({
  label: String
});
</script>
