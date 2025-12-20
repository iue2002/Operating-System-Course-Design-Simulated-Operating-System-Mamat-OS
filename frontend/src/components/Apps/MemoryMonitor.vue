<template>
  <div class="flex flex-col h-full bg-os-bg">
    <!-- Title Bar -->
    <div class="h-8 bg-os-surface border-b border-os-surfaceHighlight flex items-center px-2">
      <Cpu class="w-4 h-4 mr-2" />
      <span class="text-xs text-os-text">RAM和CPU监视器</span>
    </div>
    
    <!-- Content -->
    <div class="flex-1 overflow-auto p-4">
      <div class="grid gap-4 md:grid-cols-2 xl:grid-cols-2 items-stretch auto-rows-fr">
        <div class="h-full flex flex-col">
          <h3 class="text-sm font-medium text-os-text mb-2">内存分布</h3>
          <div class="bg-os-surface rounded-xl p-4 shadow-sm border border-os-surfaceHighlight/60 h-full flex flex-col">
            <div class="flex items-center justify-between text-xs text-os-muted mb-3 flex-wrap gap-2">
              <span>物理页 {{ memoryInfo?.totalPages || 0 }} = 系统 {{ memoryInfo?.systemPageCount || 0 }} + 用户 {{ memoryInfo?.userTotalPages || 0 }}</span>
              <span>用户区 {{ Math.round(memoryInfo?.userUsagePercentage || 0) }}% 使用率</span>
            </div>
            <div class="flex items-center justify-between text-[11px] text-os-muted mb-3 flex-wrap gap-2">
              <span>系统区 {{ memoryInfo?.systemUsedPagesCount || 0 }}/{{ memoryInfo?.systemPageCount || 0 }} 页</span>
              <span>剩余 {{ memoryInfo?.systemFreePagesCount || 0 }} 页 · {{ Math.round(memoryInfo?.systemUsagePercentage || 0) }}%</span>
            </div>
            <div class="grid grid-cols-8 gap-2">
              <div
                v-for="tile in pageTiles"
                :key="tile.index"
                :class="[
                  'relative h-9 rounded-lg flex items-center justify-center text-[10px] font-mono transition-colors border',
                  tile.state === 'system'
                    ? 'bg-indigo-500/40 text-white border-indigo-400/60 shadow-inner'
                    : tile.state === 'free'
                      ? 'bg-emerald-500/20 text-emerald-100 border-emerald-400/30'
                      : tile.state === 'running'
                        ? 'bg-red-500/80 text-white border-red-400/80 shadow-inner'
                        : 'bg-os-accent/70 text-white border-os-accent/80 shadow-inner'
                ]"
              >
                <span>p{{ tile.index }}</span>
                <div
                  v-if="tile.state !== 'free'"
                  class="absolute inset-0 rounded-lg bg-black/10"
                ></div>
              </div>
            </div>
            <div class="flex items-center gap-4 text-[11px] text-os-muted mt-3">
              <div class="flex items-center gap-1">
                <span class="w-3 h-3 rounded bg-indigo-500"></span>
                <span>系统区</span>
              </div>
              <div class="flex items-center gap-1">
                <span class="w-3 h-3 rounded bg-red-500"></span>
                <span>运行中</span>
              </div>
              <div class="flex items-center gap-1">
                <span class="w-3 h-3 rounded bg-os-accent/80"></span>
                <span>正在占用</span>
              </div>
              <div class="flex items-center gap-1">
                <span class="w-3 h-3 rounded bg-emerald-500/30"></span>
                <span>空闲页</span>
              </div>
            </div>
          </div>
        </div>

        <div class="h-full flex flex-col">
          <h3 class="text-sm font-medium text-os-text mb-2">CPU 监视器</h3>
          <div class="bg-os-surface rounded-xl p-4 shadow-sm border border-os-surfaceHighlight/60 h-full flex flex-col">
            <div class="text-xs text-os-muted mb-3">当前运行进程</div>
            <div class="flex-1 flex flex-col">
              <template v-if="runningProcessInfo">
                <div class="flex items-center justify-between bg-blue-500/10 border border-blue-400/30 rounded-lg px-3 py-2">
                  <div>
                    <div class="text-sm font-semibold text-white">PID {{ runningProcessInfo.pid }}</div>
                    <div class="text-[11px] text-os-muted mt-0.5">
                      PC {{ runningProcessInfo.pc ?? '—' }} / {{ runningProcessInfo.instructionCount ?? '—' }} · 时间片 {{ timeSliceLabel(runningProcessInfo) }}
                    </div>
                  </div>
                  <span class="px-2 py-0.5 text-[10px] rounded-full bg-blue-500 text-white font-semibold">RUNNING</span>
                </div>
                <div class="mt-3">
                  <div class="text-[11px] text-os-muted mb-1">当前指令</div>
                  <div class="bg-black/40 border border-white/10 rounded-lg text-xs text-white/80 font-mono px-3 py-2 whitespace-pre-wrap min-h-[60px] flex items-center">
                    {{ runningInstruction || '—' }}
                  </div>
                </div>
              </template>
              <template v-else>
                <div class="flex-1 rounded-lg border border-white/10 bg-black/30 flex items-center justify-center text-xs text-os-muted">
                  当前无运行进程
                </div>
              </template>
            </div>
          </div>
        </div>

      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, computed, watch } from 'vue';
import { Cpu } from 'lucide-vue-next';
import { kernelApi } from '../../api/kernel';
import { useOsStore } from '../../stores/os';

const memoryInfo = ref(null);
const pageOwners = ref({});
const systemPages = ref([]);
const runningPid = ref(null);
const osStore = useOsStore();
const runningProcessInfo = computed(() => {
  const info = osStore.systemInfo || {};
  const processes = info.processes || [];
  const running = info.queues && info.queues.running != null ? info.queues.running : null;
  if (running != null) {
    const found = processes.find(p => p.pid === running);
    if (found) return found;
  }
  return processes.find(p => p.state === 'RUNNING') || null;
});
const runningInstruction = computed(() => runningProcessInfo.value?.currentInstruction || '');
const pageTiles = computed(() => {
  if (!memoryInfo.value || !memoryInfo.value.totalPages) return [];
  const total = memoryInfo.value.totalPages;
  const free = new Set(memoryInfo.value.freePages || []);
  const systemSet = new Set(systemPages.value || []);
  const owners = pageOwners.value || {};
  const tiles = [];
  for (let i = 0; i < total; i++) {
    const state = systemSet.has(i)
      ? 'system'
      : free.has(i)
        ? 'free'
        : owners[i] != null && owners[i] === runningPid.value
          ? 'running'
          : 'used';
    tiles.push({ index: i, state, owner: owners[i] ?? null });
  }
  return tiles;
});

let refreshInterval = null;
let refreshInFlight = false;
let refreshQueued = false;

const refreshData = async () => {
  if (refreshInFlight) {
    refreshQueued = true;
    return;
  }
  refreshInFlight = true;
  try {
    // 获取内存状态（返回 totalPages, pageSize, freePagesCount, allocations）
    const memoryRes = await kernelApi.getMemoryStatus();
    if (memoryRes.data) {
      const m = memoryRes.data;
      const total = m.totalPages || 0;
      const free = m.freePagesCount || 0;
      const used = total - free;
      const pct = total > 0 ? (used / total) * 100 : 0;
      const systemCount = m.systemPageCount || 0;
      const reportedSystemUsed = m.systemUsedPagesCount;
      const reportedSystemFree = m.systemFreePagesCount;
      const systemUsed = reportedSystemUsed != null
        ? reportedSystemUsed
        : (reportedSystemFree != null ? Math.max(0, systemCount - reportedSystemFree) : 0);
      const systemFree = reportedSystemFree != null
        ? reportedSystemFree
        : Math.max(0, systemCount - systemUsed);
      const systemPct = m.systemUsagePercentage ?? (systemCount > 0 ? (systemUsed / systemCount) * 100 : 0);
      const info = {
        totalPages: total,
        pageSize: m.pageSize || 0,
        freePagesCount: free,
        usedPagesCount: m.usedPagesCount ?? used,
        usagePercentage: m.usagePercentage ?? pct,
        allocations: m.allocations || {},
        freePages: m.freePages || [],
        pageOwners: m.pageOwners || {},
        systemPageCount: m.systemPageCount || 0,
        systemFreePagesCount: systemFree,
        systemUsedPagesCount: systemUsed,
        systemUsagePercentage: systemPct,
        systemPages: m.systemPages || [],
        systemPageOwners: m.systemPageOwners || {},
        userTotalPages: m.userTotalPages ?? (total - (m.systemPageCount || 0)),
        userUsedPagesCount: m.userUsedPagesCount ?? (used - (m.systemPageCount || 0)),
        userUsagePercentage: m.userUsagePercentage ?? pct
      };
      memoryInfo.value = info;
      pageOwners.value = info.pageOwners || {};
      systemPages.value = info.systemPages || [];
    }

    const infoRes = await kernelApi.getInfo();
    let running = null;
    if (infoRes.data && infoRes.data.queues && infoRes.data.queues.running != null) {
      running = infoRes.data.queues.running;
    }
    if (running == null && Array.isArray(infoRes.data?.processes)) {
      const rp = infoRes.data.processes.find(p => p.state === 'RUNNING');
      if (rp) running = rp.pid;
    }
    runningPid.value = running;
  } catch (e) {
    console.error('获取内存/磁盘信息失败:', e);
  }
  finally {
    refreshInFlight = false;
    if (refreshQueued) {
      refreshQueued = false;
      refreshData();
    }
  }
};

onMounted(() => {
  refreshData();
  // 每1秒刷新一次数据，作为兜底
  refreshInterval = setInterval(refreshData, 1000);
});

onUnmounted(() => {
  if (refreshInterval) {
    clearInterval(refreshInterval);
  }
});

watch(() => osStore.lastTick, (tick) => {
  if (tick != null) refreshData();
});

watch(() => osStore.systemInfo, (info) => {
  if (info) refreshData();
});

const timeSliceLabel = (proc) => {
  const left = proc?.timeSliceLeft ?? proc?.timeSlice;
  return left != null ? left : '—';
};
</script>