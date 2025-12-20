<template>
  <div class="flex flex-col h-full bg-os-bg text-os-text p-2">
    <div class="mb-3">
      <h2 class="font-bold text-sm uppercase tracking-wide">任务管理器</h2>
    </div>

    <div class="grid gap-3 md:grid-cols-2 mb-3">
      <div class="bg-os-surface border border-os-surfaceHighlight rounded-xl p-3 shadow-sm">
        <div class="flex items-center justify-between text-xs text-os-muted mb-2">
          <span>CPU 调度</span>
          <span v-if="lastTick">Tick #{{ lastTick.uptimeTicks }}</span>
        </div>
        <div class="bg-black/30 rounded-lg p-3 border border-white/5">
          <div class="text-[11px] text-os-muted mb-1 flex justify-between">
            <span>当前运行</span>
            <span v-if="cpuSnapshot" class="text-white/60">PSW {{ cpuSnapshot.psw || '—' }} · ACC {{ cpuSnapshot.acc ?? 0 }}</span>
          </div>
          <div v-if="runningProcess" class="flex items-center justify-between px-3 py-2 rounded-lg border" :class="stateCardClass('RUNNING')">
            <div class="pr-3">
              <div class="text-sm font-semibold text-white">PID {{ runningProcess.pid }}</div>
              <div class="text-[11px] text-os-muted mt-0.5">PC {{ runningProcess.pc }} / {{ runningProcess.instructionCount || '—' }} · 时间片 {{ timeSliceLabel(runningProcess) }}</div>
              <div v-if="runningProcess.currentInstruction" class="text-[11px] text-white/80 mt-1">
                指令：<span class="font-mono">{{ runningProcess.currentInstruction }}</span>
              </div>
            </div>
            <span class="px-2 py-0.5 text-[10px] font-semibold rounded-full" :class="stateBadgeClass('RUNNING')">RUNNING</span>
          </div>
          <div v-else class="text-xs text-os-muted py-4 text-center">当前无运行进程</div>
          <div class="mt-3">
            <div class="text-[11px] text-os-muted mb-1">就绪队列</div>
            <div class="flex flex-col gap-2">
              <div
                v-for="p in readyQueue"
                :key="p.pid"
                class="flex items-center justify-between px-3 py-2 rounded-lg border"
                :class="stateCardClass('READY')"
              >
                <div class="pr-3">
                  <div class="text-sm font-semibold text-white">PID {{ p.pid }}</div>
                  <div class="text-[11px] text-os-muted mt-0.5">PC {{ p.pc }} / {{ p.instructionCount || '—' }} · 时间片 {{ timeSliceLabel(p) }}</div>
                  <div v-if="p.currentInstruction" class="text-[11px] text-white/80 mt-1">
                    指令：<span class="font-mono">{{ p.currentInstruction }}</span>
                  </div>
                </div>
                <span class="px-2 py-0.5 text-[10px] font-semibold rounded-full" :class="stateBadgeClass('READY')">READY</span>
              </div>
              <span v-if="readyQueue.length === 0" class="text-xs text-os-muted">空</span>
            </div>
          </div>
          <div class="mt-3">
            <div class="text-[11px] text-os-muted mb-1">阻塞队列</div>
            <div class="flex flex-col gap-2">
              <div
                v-for="p in blockedQueue"
                :key="p.pid + '-' + p.reason"
                class="flex items-center justify-between px-3 py-2 rounded-lg border"
                :class="stateCardClass('BLOCKED')"
              >
                <div class="pr-3">
                  <div class="text-sm font-semibold text-white">PID {{ p.pid }}</div>
                  <div class="text-[11px] text-os-muted mt-0.5">PC {{ p.pc }} / {{ p.instructionCount || '—' }} · 时间片 {{ timeSliceLabel(p) }}</div>
                  <div class="text-[11px] text-amber-200 mt-1">等待设备 {{ p.reason }}</div>
                  <div v-if="p.currentInstruction" class="text-[11px] text-white/80 mt-1">
                    指令：<span class="font-mono">{{ p.currentInstruction }}</span>
                  </div>
                </div>
                <span class="px-2 py-0.5 text-[10px] font-semibold rounded-full" :class="stateBadgeClass('BLOCKED')">BLOCKED</span>
              </div>
              <span v-if="blockedQueue.length === 0" class="text-xs text-os-muted">空</span>
            </div>
          </div>
        </div>
        <div v-if="lastTick && lastTick.wakeups && lastTick.wakeups.length" class="mt-3 text-[11px] text-os-muted">
          <div class="mb-1">最近唤醒事件</div>
          <ul class="space-y-1">
            <li v-for="w in lastTick.wakeups" :key="w.pid" class="flex items-center gap-2">
              <span class="w-1.5 h-1.5 rounded-full" :class="w.woken ? 'bg-emerald-400' : 'bg-yellow-400'"></span>
              <span>PID {{ w.pid }} · 状态 {{ w.status || (w.woken ? 'completed' : 'pending') }}</span>
            </li>
          </ul>
        </div>
        <div v-if="recentInterrupts.length" class="mt-3 text-[11px] text-os-muted">
          <div class="mb-1">最近中断</div>
          <ul class="space-y-1">
            <li v-for="(intr, idx) in recentInterrupts.slice().reverse()" :key="idx" class="flex items-center gap-2">
              <span class="w-1.5 h-1.5 rounded-full bg-purple-400"></span>
              <span>
                {{ intr.type || 'UNKNOWN' }}
                <template v-if="intr.pid != null"> · PID {{ intr.pid }}</template>
                <template v-if="intr.reason"> · {{ intr.reason }}</template>
              </span>
            </li>
          </ul>
        </div>
      </div>

      <div class="bg-os-surface border border-os-surfaceHighlight rounded-xl p-3 shadow-sm">
        <div class="text-xs text-os-muted mb-2">进程概览</div>
        <div class="grid grid-cols-2 gap-2 text-[11px] text-os-muted">
          <div class="bg-black/30 rounded-lg p-3 border border-white/5">
            <div class="text-os-muted mb-1">总数</div>
            <div class="text-lg font-semibold text-white">{{ activeProcesses.length }}</div>
          </div>
          <div class="bg-black/20 rounded-lg p-3 border border-white/5">
            <div class="text-os-muted mb-1">终止</div>
            <div class="text-lg font-semibold text-white">{{ terminatedCount }}</div>
          </div>
          <div class="bg-black/20 rounded-lg p-3 border border-white/5">
            <div class="text-os-muted mb-1">就绪</div>
            <div class="text-lg font-semibold text-white">{{ readyQueue.length }}</div>
          </div>
          <div class="bg-black/30 rounded-lg p-3 border border-white/5">
            <div class="text-os-muted mb-1">阻塞</div>
            <div class="text-lg font-semibold text-white">{{ blockedQueue.length }}</div>
          </div>
        </div>
      </div>
    </div>
    
    <div class="flex-1 overflow-auto border border-os-surfaceHighlight rounded bg-os-surface">
      <table class="w-full text-left text-sm">
        <thead class="bg-os-surfaceHighlight text-os-muted sticky top-0">
          <tr>
            <th class="p-2">PID</th>
            <th class="p-2">状态</th>
            <th class="p-2">PC</th>
            <th class="p-2">时间</th>
            <th class="p-2">等待</th>
            <th class="p-2">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="proc in activeProcesses" :key="proc.pid + '-' + (proc.order ?? 0)" class="border-b border-os-surfaceHighlight/50 hover:bg-white/5">
            <td class="p-2 font-mono">{{ proc.pid }}</td>
            <td class="p-2">
              <span 
                class="px-1.5 py-0.5 rounded text-[10px] font-bold"
                :class="{
                  'bg-green-500/20 text-green-400': proc.state === 'RUNNING',
                  'bg-yellow-500/20 text-yellow-400': proc.state === 'READY',
                  'bg-red-500/20 text-red-400': proc.state === 'BLOCKED',
                  'bg-gray-500/20 text-gray-400': proc.state === 'TERMINATED'
                }"
              >
                {{ proc.state }}
              </span>
            </td>
            <td class="p-2 font-mono">{{ proc.pc }}</td>
            <td class="p-2">{{ proc.runTime || proc.timeUsed || 0 }}</td>
            <td class="p-2 text-xs text-os-muted">{{ proc.waitingFor || '-' }}</td>
            <td class="p-2">
              <div class="flex gap-2">
                <button
                  @click="kill(proc.pid)"
                  class="text-xs px-2 py-1 rounded text-white bg-red-600 hover:bg-red-700"
                >终止</button>
              </div>
            </td>
          </tr>
          <tr v-if="activeProcesses.length === 0">
            <td colspan="6" class="p-4 text-center text-xs text-os-muted">暂无活动进程</td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- 输出查看已移除 -->
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, computed } from 'vue';
import { useOsStore } from '../../stores/os';
import { kernelApi } from '../../api/kernel';

const osStore = useOsStore();
const processes = computed(() => {
  const list = osStore.systemInfo?.processes || [];
  return [...list].sort((a, b) => {
    const orderA = a.order ?? 0;
    const orderB = b.order ?? 0;
    if (orderA === orderB) return (a.pid || 0) - (b.pid || 0);
    return orderA - orderB;
  });
});
const activeProcesses = computed(() => processes.value.filter(p => p.state !== 'TERMINATED'));
const lastTick = computed(() => osStore.lastTick || null);
const cpuSnapshot = computed(() => (osStore.lastTick && osStore.lastTick.cpu) || null);
const queueInfo = computed(() => osStore.systemInfo?.queues || {});
const recentInterrupts = computed(() => {
  const list = osStore.lastTick?.interrupts || [];
  if (Array.isArray(list)) return list.slice(-5);
  return [];
});
const runningProcess = computed(() => {
  const pid = queueInfo.value?.running;
  if (pid != null) {
    return processes.value.find(p => p.pid === pid) || null;
  }
  return processes.value.find(p => p.state === 'RUNNING') || null;
});
const readyQueue = computed(() => {
  const readyList = queueInfo.value?.readyList || [];
  const mapEntry = (item) => ({
    pid: item.pid,
    state: item.state || 'READY',
    pc: item.pc ?? 0,
    timeSlice: item.timeSlice,
    timeSliceLeft: item.timeSliceLeft,
    instructionCount: item.instructionCount,
    currentInstruction: item.currentInstruction
  });
  if (readyList.length > 0) {
    return readyList.map(mapEntry);
  }
  return processes.value
    .filter(p => p.state === 'READY')
    .map(mapEntry);
});
const blockedQueue = computed(() => {
  const blocked = queueInfo.value?.blockedList || {};
  const arr = [];
  Object.entries(blocked).forEach(([reason, list]) => {
    list.forEach(item => {
      arr.push({
        pid: item.pid,
        reason: (reason || item.waitingFor || '?').toUpperCase(),
        state: item.state || 'BLOCKED',
        pc: item.pc ?? 0,
        timeSlice: item.timeSlice,
        timeSliceLeft: item.timeSliceLeft,
        instructionCount: item.instructionCount,
        currentInstruction: item.currentInstruction
      });
    });
  });
  if (arr.length > 0) return arr;
  return processes.value
    .filter(p => p.state === 'BLOCKED')
    .map(p => ({
      pid: p.pid,
      reason: (p.waitingFor || '?').toUpperCase(),
      state: 'BLOCKED',
      pc: p.pc ?? 0,
      timeSlice: p.timeSlice,
      timeSliceLeft: p.timeSliceLeft,
      instructionCount: p.instructionCount,
      currentInstruction: p.currentInstruction
    }));
});
const terminatedCount = computed(() => processes.value.filter(p => p.state === 'TERMINATED').length);
let autoRefreshTimer = null;

const stateCardClass = (state) => {
  switch (state) {
    case 'RUNNING':
      return 'bg-blue-500/15 border-blue-400/40 text-blue-50 shadow-inner';
    case 'READY':
      return 'bg-emerald-500/15 border-emerald-400/40 text-emerald-50 shadow-inner';
    case 'BLOCKED':
      return 'bg-amber-500/15 border-amber-400/40 text-amber-50 shadow-inner';
    default:
      return 'bg-white/10 border-white/20';
  }
};

const stateBadgeClass = (state) => {
  switch (state) {
    case 'RUNNING':
      return 'bg-blue-500 text-white';
    case 'READY':
      return 'bg-emerald-500 text-emerald-950';
    case 'BLOCKED':
      return 'bg-amber-500 text-amber-950';
    default:
      return 'bg-white/20 text-white';
  }
};

const timeSliceLabel = (proc) => {
  const left = proc.timeSliceLeft ?? proc.timeSlice;
  return left != null ? left : '—';
};

const refresh = async () => {
  await osStore.fetchInfo();
};

const kill = async (pid) => {
  try {
    const res = await kernelApi.killProcess(pid);
    if (res && res.data && res.data.success === false) {
      console.error('kill failed', res.data.error);
    }
  } catch (e) {
    console.error('kill error', e);
  } finally {
    try { await osStore.fetchInfo(); } catch (e) {}
  }
};

// 输出查看功能已移除

onMounted(() => {
  refresh();
  if (!osStore.isRunning) {
    osStore.fetchInfo();
  }
  autoRefreshTimer = setInterval(() => {
    osStore.fetchInfo();
  }, 1000);
});

onUnmounted(() => {
  if (autoRefreshTimer) {
    clearInterval(autoRefreshTimer);
    autoRefreshTimer = null;
  }
});
</script>
