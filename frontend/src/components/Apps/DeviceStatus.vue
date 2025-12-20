<template>
  <div class="flex flex-col h-full bg-os-bg p-2">
    <div class="mb-2">
      <h2 class="font-bold">设备状态</h2>
    </div>
    <div class="flex-1 overflow-auto border border-os-surfaceHighlight rounded bg-os-surface p-2">
      <div v-if="!devices || devices.length === 0" class="text-xs text-os-muted">无设备信息</div>
      <div v-else class="grid gap-2">
        <div v-for="d in devices" :key="d.id" class="p-2 bg-os-bg/50 rounded flex items-center justify-between">
          <div>
            <div class="text-sm font-medium">设备 {{ d.id }}</div>
            <div class="text-xs text-os-muted">状态: {{ d.status }} · 所有者 PID: {{ d.ownerPid || '-' }}</div>
            <div class="text-xs text-os-muted">等待队列: {{ d.waitQueueLength || 0 }} · 进度: {{ d.progress || 0 }}%</div>
          </div>
          <div>
            <div :class="['px-2 py-1 rounded text-xs font-bold', statusClass(d.status)]">{{ d.status }}</div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted } from 'vue';
import { useOsStore } from '../../stores/os';

const osStore = useOsStore();

const devices = computed(() => osStore.systemInfo?.devices || []);

onMounted(() => {
  osStore.fetchInfo();
});

const statusClass = (s) => {
  if (!s) return 'bg-gray-500/20 text-gray-200';
  if (s === 'FREE') return 'bg-green-500/20 text-green-400';
  if (s === 'BUSY' || s === 'IN_USE') return 'bg-yellow-500/20 text-yellow-400';
  if (s === 'ERROR') return 'bg-red-500/20 text-red-400';
  return 'bg-gray-500/20 text-gray-200';
};
</script>

<style scoped>
.device-status { padding: 8px; }
.device-status .header { display:flex; justify-content:space-between; align-items:center }
.device-status .status { margin-left:8px; padding:2px 6px; border-radius:6px }
.device-status .status.free { background: #e0f7fa; color:#00796b }
.device-status .status.busy { background: #fff3e0; color:#ef6c00 }
.device-status .status.unknown { background:#eee }
</style>
