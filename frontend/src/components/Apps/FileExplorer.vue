<template>
  <div class="flex flex-col h-full bg-os-bg border border-os-surface rounded-lg overflow-hidden shadow-lg">
    <!-- Title Bar -->
    <div class="h-10 bg-os-surface border-b border-os-surfaceHighlight flex items-center px-4 justify-between">
      <div class="flex items-center gap-3 text-sm text-os-text pr-4 min-w-0">
        <button @click="goUp" title="返回上级" class="p-2 bg-os-accent text-white rounded-md hover:scale-105 shadow transition-transform flex items-center justify-center">
          <ArrowUp class="w-5 h-5" />
        </button>
        <Folder class="w-5 h-5" />
        <span class="truncate">文件资源管理器</span>
      </div>
      <div class="flex gap-1">
        <!-- 刷新按钮已移除（磁盘为实时） -->
      </div>
    </div>

    <!-- Path Bar -->
    <div class="h-10 border-b border-os-surfaceHighlight bg-os-surface flex items-center gap-2 px-3 text-xs">
      <span class="text-os-muted whitespace-nowrap">位置:</span>
      <div class="flex-1 flex items-center gap-2">
        <input
          ref="addressInputRef"
          v-model="addressInput"
          @focus="selectAddress"
          @keyup.enter="submitAddress"
          class="flex-1 bg-black/10 border border-white/10 rounded px-2 py-1 text-os-text text-xs focus:outline-none focus:ring-1 focus:ring-os-accent placeholder:text-os-muted"
          placeholder="/"
          spellcheck="false"
        />
        <button
          @click="submitAddress"
          class="px-2 py-1 bg-os-accent hover:bg-os-accentHover rounded text-white transition-colors"
        >前往</button>
      </div>
    </div>

    <!-- Toolbar (only shown when viewing files) -->
    <div v-if="currentView === 'files'" class="h-10 border-b border-os-surfaceHighlight bg-os-surface flex items-center px-2 gap-2">
      <button @click="openCreateModal('file')" class="text-xs px-2 py-1 bg-os-accent hover:bg-os-accentHover rounded text-white transition-colors">
        新建文件
      </button>
      <button @click="openCreateModal('executable')" class="text-xs px-2 py-1 bg-os-accent/80 hover:bg-os-accent rounded text-white transition-colors">
        新建可执行文件
      </button>
      <button @click="openCreateModal('folder')" class="text-xs px-2 py-1 bg-os-accent hover:bg-os-accentHover rounded text-white transition-colors">
        新建文件夹
      </button>
      <button @click="deleteSelected" class="text-xs px-2 py-1 bg-red-600 hover:bg-red-700 rounded text-white transition-colors">
        删除
      </button>
    </div>

    <!-- Content Area -->
    <div class="flex-1 overflow-auto">
      <div v-if="isLoading" class="flex items-center justify-center h-full">
        <div class="animate-spin rounded-full h-6 w-6 border-b-2 border-os-accent"></div>
      </div>
      
      <div v-else>
        <!-- Create modal -->
        <div v-if="showCreateModal" class="fixed inset-0 bg-black/70 flex items-center justify-center z-50">
          <div class="bg-os-surface p-4 rounded w-80">
            <div class="text-sm font-medium mb-1">{{ createModalTitle }}</div>
            <p v-if="createHelpText" class="text-xs text-os-muted mb-2">{{ createHelpText }}</p>
            <input v-model="newName" class="w-full p-2 mb-2 bg-white/5 rounded text-sm" :placeholder="createPlaceholder" />
            <div v-if="createErrorMessage" class="text-red-400 text-xs mb-2">{{ createErrorMessage }}</div>
            <div class="flex justify-end gap-2">
              <button @click="resetCreateState" class="px-2 py-1 rounded text-xs">取消</button>
              <button @click="confirmCreate" class="px-2 py-1 bg-os-accent text-white rounded text-xs">{{ pendingOverwrite ? '覆盖' : '创建' }}</button>
            </div>
          </div>
        </div>

        <!-- Delete confirmation modal -->
        <div v-if="showDeleteModal" class="fixed inset-0 bg-black/70 flex items-center justify-center z-50">
          <div class="bg-os-surface p-4 rounded w-80">
            <div class="text-sm font-medium mb-2">确认删除</div>
            <div class="text-xs text-os-text mb-3">确定要删除以下 {{ deleteTargets.length }} 个项目吗？此操作不可恢复。</div>
            <ul class="text-xs mb-3 max-h-24 overflow-auto bg-white/5 p-2 rounded">
              <li v-for="t in deleteTargets" :key="t.name + t.ext">{{ t.name }}{{ t.ext ? '.' + t.ext : '' }}</li>
            </ul>
            <div class="flex justify-end gap-2">
              <button @click="cancelDelete" class="px-2 py-1 rounded text-xs">取消</button>
              <button @click="confirmDelete" class="px-2 py-1 bg-red-600 text-white rounded text-xs">删除</button>
            </div>
          </div>
        </div>

        <!-- Executable action modal -->
        <div v-if="execActionModal.visible" class="fixed inset-0 bg-black/60 flex items-center justify-center z-50">
          <div class="bg-os-surface p-4 rounded w-80 shadow-lg">
            <div class="text-sm font-semibold mb-2">可执行文件操作</div>
            <div class="text-xs text-os-text mb-3">选择要对 {{ execActionModal.item?.name }}{{ execActionModal.item?.ext ? '.' + execActionModal.item.ext : '' }} 执行的操作。</div>
            <div class="flex flex-col gap-2">
              <button
                class="px-3 py-2 text-xs rounded bg-os-accent text-white hover:bg-os-accentHover transition"
                @click="runExecutableFromModal"
                :disabled="execActionLoading"
              >
                {{ execActionLoading ? '运行中…' : '运行程序' }}
              </button>
              <button
                class="px-3 py-2 text-xs rounded bg-os-surfaceHighlight text-os-text hover:bg-os-surfaceHighlight/80 transition"
                @click="editExecutableFromModal"
                :disabled="execActionLoading"
              >
                在编辑器中打开
              </button>
              <button
                class="px-3 py-2 text-xs rounded border border-os-surfaceHighlight hover:bg-white/10 transition"
                @click="closeExecActionModal"
                :disabled="execActionLoading"
              >
                取消
              </button>
            </div>
          </div>
        </div>

        <div v-if="currentView === 'this-pc'" class="p-4">
          <div class="flex items-center gap-4 w-full cursor-pointer" @click="openCDrive">
              <div style="width:48px;flex:0 0 48px;display:flex;align-items:center;justify-content:center;" class="flex-shrink-0">
                <HardDrive class="w-8 h-8 text-blue-500" />
              </div>
              <div class="flex-1 min-w-0">
                <div class="text-sm text-os-text">C: 本地磁盘</div>
                <div class="text-xs text-os-muted">已用: {{ diskInfo ? diskInfo.used + ' / ' + diskInfo.total + ' 块' : '加载中...' }}</div>
                <div class="w-full mt-1">
                  <div
                    class="grid rounded overflow-hidden"
                    :style="{
                      gridTemplateColumns: `repeat(${cols}, minmax(0,1fr))`,
                      gridAutoRows: `${visualSettings.segHeightPx}px`,
                      gap: `${visualSettings.gapPx}px`
                    }">
                    <div
                      v-for="(seg, idx) in segments"
                      :key="'seg-' + idx"
                      :title="seg.title"
                      :style="{ background: seg.color, height: visualSettings.segHeightPx + 'px' }"
                    ></div>
                  </div>
                </div>
                <div class="flex items-center gap-3 text-[11px] text-os-muted mt-2">
                  <div class="flex items-center gap-1">
                    <span class="w-3 h-3 rounded bg-orange-400"></span>
                    <span>系统 {{ diskInfo?.systemBlocks || 0 }} 块</span>
                  </div>
                  <div class="flex items-center gap-1">
                    <span class="w-3 h-3 rounded bg-os-accent"></span>
                    <span>文件 {{ diskInfo?.userBlocks || 0 }} 块</span>
                  </div>
                </div>
              </div>
              <!-- Refresh button removed: disk is realtime -->
            </div>
        </div>

        <div v-else class="grid grid-cols-4 sm:grid-cols-6 md:grid-cols-8 lg:grid-cols-10 xl:grid-cols-12 gap-2 p-2">
          <div 
            v-for="item in items" 
            :key="item.name + item.ext"
            @click.stop="selectItem(item)"
            @dblclick="handleItemDblClick(item)"
            @mousedown.prevent
            @dragstart.prevent
            :class="['flex flex-col items-center p-2 rounded hover:bg-os-hover cursor-pointer group select-none', isSelected(item) ? 'ring-2 ring-os-accent' : '']"
          >
            <div class="relative">
              <Folder v-if="isDir(item)" class="w-10 h-10 text-blue-400" />
              <FileText v-else class="w-10 h-10 text-gray-400" />
              <!-- Selection indicator -->
              <div 
                v-if="isSelected(item)" 
                class="absolute -top-1 -right-1 w-4 h-4 bg-os-accent rounded-full flex items-center justify-center"
              >
                <div class="w-2 h-2 bg-white rounded-full"></div>
              </div>
            </div>
            <div class="flex flex-col items-center mt-1">
              <span class="text-xs text-center text-os-text truncate w-full px-1 select-none">
                {{ item.name }}{{ !isDir(item) && item.ext ? '.' + item.ext : '' }}
              </span>
            </div>
          </div>
        </div>
        
        <!-- Empty State -->
        <div v-if="!isLoading && currentView === 'files' && items.length === 0" class="absolute inset-0 flex flex-col items-center justify-center pointer-events-none">
          <Folder class="w-16 h-16 text-os-surfaceHighlight mb-2 opacity-20" />
          <span class="text-os-muted text-sm opacity-40">此文件夹为空</span>
        </div>
      </div>
    </div>

    <!-- Status Bar -->
    <div class="h-6 border-t border-os-surfaceHighlight bg-os-surface px-3 flex items-center justify-between text-[10px] text-os-muted">
      <span v-if="currentView === 'files'">{{ items.length }} 个项目</span>
      <span v-else>此电脑</span>
      
      <div class="flex gap-2">
        <div class="w-2 h-2 rounded-full bg-green-500/50"></div>
        <span>Online</span>
      </div>
    </div>
    <!-- 全局轻量提示（替代浏览器 alert） -->
    <div v-if="notice" class="fixed bottom-4 left-1/2 transform -translate-x-1/2 z-50 pointer-events-none">
      <div class="bg-black/80 text-white px-3 py-1 rounded text-sm pointer-events-auto">
        {{ notice }}
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, computed, watch, reactive } from 'vue';
import { ArrowUp, Folder, FileText, HardDrive } from 'lucide-vue-next';
import { kernelApi } from '../../api/kernel';
import { useWindowsStore } from '../../stores/windows';
import { useOsStore } from '../../stores/os';
import TextEditor from './TextEditor.vue';
import { markRaw } from 'vue';

const props = defineProps({
  initialPath: { type: String, default: '/' }
});

const currentView = ref('this-pc'); // 'this-pc', 'files'
const currentPath = ref(props.initialPath);
const items = ref([]);
const isLoading = ref(false);
const windowsStore = useWindowsStore();
const selectedItems = ref([]);
const addressInput = ref(currentPath.value || '/');
const addressInputRef = ref(null);

const isDir = (item) => {
  // attrs is a bitmask where bit0 indicates directory
  try {
    return (item.attrs & 0x01) !== 0;
  } catch (e) {
    return item.ext === '';
  }
};

// Check if an item is selected
const isSelected = (item) => {
  return selectedItems.value.some(selected => 
    selected.name === item.name && selected.ext === item.ext);
};

const osStore = useOsStore();

const selectAddress = () => {
  try {
    addressInputRef.value?.select();
  } catch (e) {}
};

const normalizeAddress = (raw) => {
  if (raw == null) return '/';
  let path = String(raw).trim();
  if (!path) return '/';
  if (path === '此电脑' || path.toLowerCase() === 'this-pc') {
    return 'this-pc';
  }
  path = path.replace(/\\+/g, '/');
    path = path.replace(/\/+/g, '/');
  if (!path.startsWith('/')) path = '/' + path;
  if (path.length > 1) {
    path = path.replace(/\/+$/g, '');
  }
  return path || '/';
};

const fetchItems = async () => {
  if (currentView.value !== 'files') return;
  isLoading.value = true;
  try {
    const res = await kernelApi.listDir(currentPath.value);
    if (res.data) {
      items.value = res.data;
      addressInput.value = currentPath.value || '/';
    }
  } catch (e) {
    console.error("Failed to list dir", e);
  } finally {
    isLoading.value = false;
  }
};

const navigateTo = (view) => {
  currentView.value = view;
  if (view === 'files') {
    currentPath.value = '/';
    addressInput.value = '/';
    fetchItems();
  } else if (view === 'this-pc') {
    // refresh system info to show disk/other status
    osStore.fetchInfo();
    addressInput.value = '/';
  }
};

const goUp = () => {
  if (currentView.value === 'files') {
    if (currentPath.value === '/') {
      currentView.value = 'this-pc';
      addressInput.value = '/';
    } else {
      const parts = currentPath.value.split('/').filter(p => p);
      parts.pop();
      currentPath.value = '/' + parts.join('/');
      if (currentPath.value.length > 1 && currentPath.value.endsWith('/')) {
        currentPath.value = currentPath.value.slice(0, -1);
      }
      fetchItems();
    }
  }
};

const refresh = () => {
  if (currentView.value === 'files') fetchItems();
};

// select/deselect on single click
const selectItem = (item) => {
  const index = selectedItems.value.findIndex(selected => 
    selected.name === item.name && selected.ext === item.ext);
  if (index >= 0) {
    // deselect
    selectedItems.value.splice(index, 1);
  } else {
    // single selection for now
    selectedItems.value = [item];
  }
};

// handle double-click (open file or enter directory)
  const handleItemDblClick = async (item) => {
  if (isDir(item)) {
    const newPath = currentPath.value === '/' ? `/${item.name}` : `${currentPath.value}/${item.name}`;
    currentPath.value = newPath;
    fetchItems();
    return;
  }

  const filePath = currentPath.value === '/' ? `/${item.name}.${item.ext}` : `${currentPath.value}/${item.name}.${item.ext}`;
  const isExec = ((item.attrs || 0) & 0x10) !== 0; // ATTR_EXECUTABLE bit
  if (isExec) {
    openExecActionModal(item, filePath);
    return;
  }

  // Not executable -> open in editor
  windowsStore.openWindow(`${item.name}.${item.ext}`, markRaw(TextEditor), { path: filePath });
};

// Create new file
const createNewFile = async () => {
  openCreateModal('file');
};

// Create new folder
const createNewFolder = async () => {
  openCreateModal('folder');
};

// Delete selected items
const notice = ref('');
let noticeTimer = null;
const showNotice = (msg, ms = 2000) => {
  notice.value = msg;
  if (noticeTimer) { clearTimeout(noticeTimer); noticeTimer = null; }
  noticeTimer = setTimeout(() => { notice.value = ''; noticeTimer = null; }, ms);
};

const isMemoryError = (msg) => {
  if (typeof msg !== 'string') return false;
  return msg.includes('内存已满') || msg.includes('内存不足');
};

const submitAddress = async () => {
  const normalized = normalizeAddress(addressInput.value);
  if (normalized === 'this-pc') {
    currentView.value = 'this-pc';
    currentPath.value = '/';
    addressInput.value = '/';
    selectedItems.value = [];
    try { await osStore.fetchInfo(); } catch (e) {}
    return;
  }
  if (!normalized) return;
  const prevPath = currentPath.value;
  isLoading.value = true;
  try {
    const res = await kernelApi.listDir(normalized);
    currentView.value = 'files';
    currentPath.value = normalized;
    items.value = Array.isArray(res.data) ? res.data : [];
    selectedItems.value = [];
    addressInput.value = normalized;
  } catch (e) {
    addressInput.value = prevPath || '/';
    console.error('Failed to navigate to path:', normalized, e);
    showNotice('路径无效或不可访问');
  } finally {
    isLoading.value = false;
  }
};

const deleteSelected = async () => {
  if (selectedItems.value.length === 0) {
    showNotice('请选择要删除的项目');
    return;
  }
  // open in-app delete confirmation modal
  deleteTargets.value = selectedItems.value.slice();
  showDeleteModal.value = true;
};

// actual confirm delete called from modal
const confirmDelete = async () => {
  showDeleteModal.value = false;
  try {
      for (const item of deleteTargets.value) {
      const itemPath = currentPath.value === '/' ? `/${item.name}${item.ext ? '.' + item.ext : ''}` : 
                      `${currentPath.value}/${item.name}${item.ext ? '.' + item.ext : ''}`;
      try {
        const res = isDir(item) ? await kernelApi.deleteFile(itemPath, true) : await kernelApi.deleteFile(itemPath, false);
        if (res && res.data && res.data.disk) {
          try { osStore.applyDiskSnapshot(res.data.disk); } catch (ignored) {}
        }
      } catch (ignored) {}
    }
    selectedItems.value = [];
    deleteTargets.value = [];
    await fetchItems();
    // refresh global info so disk usage updates
    try { await osStore.fetchInfo(); } catch (e) {}
    } catch (e) {
    showNotice('删除失败: ' + (e && e.message ? e.message : e));
  }
};

const cancelDelete = () => {
  showDeleteModal.value = false;
  deleteTargets.value = [];
};

onMounted(() => {
  // Initial fetch for this-pc view
  if (currentView.value === 'this-pc') {
    osStore.fetchInfo();
  }
});

// Auto-refresh This-PC view disk info
let pcRefreshInterval = null;
watch(currentView, (nv) => {
  if (nv === 'this-pc') {
    addressInput.value = '/';
    // fetch immediately and then poll
    osStore.fetchInfo();
    if (pcRefreshInterval) clearInterval(pcRefreshInterval);
    pcRefreshInterval = setInterval(() => {
      osStore.fetchInfo();
    }, 2000);
  } else {
    if (pcRefreshInterval) { clearInterval(pcRefreshInterval); pcRefreshInterval = null; }
    addressInput.value = currentPath.value || '/';
  }
});

watch(currentPath, (path) => {
  if (currentView.value === 'files') {
    addressInput.value = path || '/';
  }
});

onUnmounted(() => {
  if (pcRefreshInterval) { clearInterval(pcRefreshInterval); pcRefreshInterval = null; }
});

// helper to render disk info
const SYSTEM_RESERVED_BLOCKS = 3;

const diskInfo = computed(() => {
  if (!osStore.systemInfo) return null;
  const ds = osStore.systemInfo.disk;
  if (!ds) return null;
  const disk = ds.disk || {};
  const fat = ds.fat || {};
  const total = fat.totalBlocks || (disk.usedBlocks + disk.freeBlocks) || 0;
  const used = disk.usedBlocks || 0;
  const systemBlocks = Math.min(SYSTEM_RESERVED_BLOCKS, total);
  const userBlocks = Math.max(0, used - systemBlocks);
  const systemPct = total > 0 ? (systemBlocks / total) * 100 : 0;
  const userPct = total > 0 ? (userBlocks / total) * 100 : 0;
  const freeBlocks = typeof disk.freeBlocks === 'number' ? disk.freeBlocks : Math.max(0, total - used);
  return {
    total,
    used,
    free: freeBlocks,
    systemBlocks,
    userBlocks,
    systemPct,
    userPct
  };
});

// Segmented bar logic: render up to MAX_SEGMENTS blocks to visualize disk
const MAX_SEGMENTS = 128;
// User-configurable visual settings (can be exposed to UI later)
const visualSettings = {
  rows: 4,            // number of rows to show (changed to 4)
  gapPx: 2,           // gap between blocks in px
  segHeightPx: 8,     // segment height in px
  maxSegments: 128    // cap total segments
};

const totalSegments = computed(() => {
  if (!diskInfo.value || !diskInfo.value.total) return Math.min(32, visualSettings.maxSegments);
  // use disk total blocks as upper bound, but cap to maxSegments
  return Math.min(diskInfo.value.total || 0, visualSettings.maxSegments) || Math.min(32, visualSettings.maxSegments);
});

const rows = computed(() => Math.max(1, visualSettings.rows));

const cols = computed(() => {
  const t = totalSegments.value || 0;
  return Math.max(1, Math.ceil(t / rows.value));
});

const segments = computed(() => {
  const segs = [];
  const di = diskInfo.value;
  const total = di ? di.total : 0;
  const segsCount = totalSegments.value || 0;

  // Prefer detailed per-block usage array if available from backend.
  // Backend `getDiskStatus` returns an object with shape { disk: { blockUsage: [...] , usedBlocks, ... }, fat: { ... } }
  // osStore.fetchInfo merges that into `systemInfo.disk`, so the actual array lives at `systemInfo.disk.disk.blockUsage`.
  const blockUsage = (osStore.systemInfo && osStore.systemInfo.disk && (
    // try common locations for backward compatibility
    osStore.systemInfo.disk.blockUsage || (osStore.systemInfo.disk.disk && osStore.systemInfo.disk.disk.blockUsage)
  )) || null;

  for (let i = 0; i < segsCount; i++) {
    // Map segment to a block index in [0, total)
    const blockIndex = total > 0 ? Math.floor((i * total) / segsCount) : i;
    let occupied = false;
    let title = '空闲';
    if (Array.isArray(blockUsage) && blockIndex < blockUsage.length) {
      const isSystemBlock = di ? (blockIndex < (di.systemBlocks || 0)) : false;
      occupied = !!blockUsage[blockIndex];
      if (isSystemBlock) {
        occupied = true; // system blocks are occupied by definition
        title = `块 ${blockIndex}: 系统保留`;
      } else {
        title = occupied ? `块 ${blockIndex}: 已用（文件）` : `块 ${blockIndex}: 空闲`;
      }
    } else {
      // Fallback: use usedBlocks/user/system heuristics
      const system = di ? di.systemBlocks : 0;
      const user = di ? di.userBlocks : 0;
      if (blockIndex < system) {
        occupied = true;
        title = `块 ${blockIndex}: 系统保留`;
      } else if (blockIndex < system + user) {
        occupied = true;
        title = `块 ${blockIndex}: 已用（文件）`;
      } else {
        occupied = false;
        title = `块 ${blockIndex}: 空闲`;
      }
    }

    let color = '#e5e7eb';
    // system blocks: orange; file-occupied: blue; free: gray
    if (di && blockIndex < (di.systemBlocks || 0)) {
      color = '#fb923c';
    } else if (occupied) {
      color = '#60a5fa';
    }
    segs.push({ color, title });
  }
  return segs;
});

// navigate to C drive (this-pc click)
const openCDrive = () => {
  currentView.value = 'files';
  currentPath.value = '/';
  fetchItems();
};

// create modal state
const showCreateModal = ref(false);
const createType = ref('file');
const newName = ref('');
const pendingOverwrite = ref(null);

// delete modal state
const showDeleteModal = ref(false);
const deleteTargets = ref([]);
// create modal inline error message
const createErrorMessage = ref('');

// executable action modal state
const execActionModal = reactive({ visible: false, item: null, path: '' });
const execActionLoading = ref(false);

const openExecActionModal = (item, path) => {
  execActionModal.visible = true;
  execActionModal.item = item;
  execActionModal.path = path;
  execActionLoading.value = false;
};

const closeExecActionModal = () => {
  execActionModal.visible = false;
  execActionModal.item = null;
  execActionModal.path = '';
  execActionLoading.value = false;
};

const runExecutableFromModal = async () => {
  if (!execActionModal.path || !execActionModal.item) return;
  execActionLoading.value = true;
  try {
    showNotice('正在运行：' + execActionModal.item.name + (execActionModal.item.ext ? '.' + execActionModal.item.ext : ''));
    const res = await kernelApi.runProcess(execActionModal.path);
    if (res && res.data) {
      if (res.data.success === false) {
        const errMsg = res.data.error || '未知错误';
        if (isMemoryError(errMsg)) {
          showNotice('内存已满，进程创建失败');
        } else {
          showNotice('运行失败: ' + errMsg);
        }
      } else {
        const pid = (res.data.pid || res.data.pid === 0) ? res.data.pid : null;
        showNotice('已启动，PID: ' + (pid == null ? '—' : pid));
        try { await osStore.fetchInfo(); } catch (e) {}
      }
    }
    closeExecActionModal();
  } catch (e) {
    showNotice('运行失败: ' + (e && e.message ? e.message : e));
    execActionLoading.value = false;
  }
};

const editExecutableFromModal = () => {
  if (!execActionModal.path || !execActionModal.item) return;
  const name = execActionModal.item.ext ? `${execActionModal.item.name}.${execActionModal.item.ext}` : execActionModal.item.name;
  windowsStore.openWindow(name, markRaw(TextEditor), { path: execActionModal.path });
  closeExecActionModal();
};

const openCreateModal = (type) => {
  createType.value = type;
  newName.value = '';
  createErrorMessage.value = '';
  pendingOverwrite.value = null;
  showCreateModal.value = true;
};

const createModalTitle = computed(() => {
  if (createType.value === 'file') return '新建文件';
  if (createType.value === 'folder') return '新建文件夹';
  return '新建可执行文件';
});

const createPlaceholder = computed(() => {
  if (createType.value === 'folder') return '请输入文件夹名称（最多 3 个字符）';
  if (createType.value === 'executable') return '输入文件名(扩展名会自动添加)';
  return '请输入名称';
});

const createHelpText = computed(() => {
  if (createType.value === 'executable') {
    return '可执行文件将自动使用 .EX 扩展名，并在创建后立即打开编辑器。';
  }
  return '';
});

const resetCreateState = () => {
  showCreateModal.value = false;
  createErrorMessage.value = '';
  pendingOverwrite.value = null;
};
watch(newName, () => {
  createErrorMessage.value = '';
  pendingOverwrite.value = null;
});

const performOverwrite = async () => {
  if (!pendingOverwrite.value) return;
  try {
    const { path, isExecutable, nameToCreate } = pendingOverwrite.value;
    const res = await kernelApi.createFile(path, "", isExecutable, true);
    if (res && res.data) {
      if (res.data.success === false) {
        createErrorMessage.value = res.data.error || '覆盖失败';
        return;
      }
      if (res.data.disk) { try { osStore.applyDiskSnapshot(res.data.disk); } catch (ignored) {} }
    }
    windowsStore.openWindow(nameToCreate, markRaw(TextEditor), { path });
    resetCreateState();
    await fetchItems();
    try { await osStore.fetchInfo(); } catch (e) {}
  } catch (e) {
    showNotice('覆盖失败: ' + (e && e.message ? e.message : e));
  }
};

const confirmCreate = async () => {
  if (pendingOverwrite.value) {
    await performOverwrite();
    return;
  }
  if (!newName.value) {
    createErrorMessage.value = '请输入名称';
    return;
  }
  try {
    let nameToCreate = newName.value.trim();
    if (createType.value === 'file' && nameToCreate && !nameToCreate.includes('.')) {
      nameToCreate += '.tx';
    }
    if (createType.value === 'executable') {
      if (!nameToCreate) {
        createErrorMessage.value = '请输入名称';
        return;
      }
      if (!nameToCreate.toLowerCase().endsWith('.ex')) {
        nameToCreate += '.EX';
      }
    }

    if (createType.value === 'file' || createType.value === 'executable') {
      const dotIndex = nameToCreate.indexOf('.');
      const base = dotIndex >= 0 ? nameToCreate.substring(0, dotIndex) : nameToCreate;
      const ext = dotIndex >= 0 ? nameToCreate.substring(dotIndex + 1) : '';
      if (!base) {
        createErrorMessage.value = '文件名不能为空';
        return;
      }
      if (base.length > 3 || ext.length > 2) {
        createErrorMessage.value = '文件名最多 3 个字符，扩展名最多 2 个字符';
        return;
      }
      if (createType.value === 'executable' && ext.toUpperCase() !== 'EX') {
        createErrorMessage.value = '可执行文件扩展名必须为 .EX';
        return;
      }
    }

    if (createType.value === 'folder') {
      const baseOnly = nameToCreate;
      if (baseOnly.length > 3) {
        createErrorMessage.value = '文件名最多 3 个字符，扩展名最多 2 个字符';
        return;
      }
    }

    const filePath = currentPath.value === '/' ? `/${nameToCreate}` : `${currentPath.value}/${nameToCreate}`;
    const basePart = nameToCreate.split('.')[0];
    const extPart = nameToCreate.includes('.') ? nameToCreate.split('.')[1] : '';
    const duplicate = items.value.find(item => item.name === basePart && (item.ext || '') === extPart);

    if (duplicate) {
      if (createType.value === 'file') {
        const isExecutable = extPart.toLowerCase() === 'ex';
        pendingOverwrite.value = { path: filePath, isExecutable, nameToCreate };
        createErrorMessage.value = '同名文件已存在，点击“覆盖”继续或修改名称。';
        return;
      } else if (createType.value === 'folder') {
        createErrorMessage.value = '同名文件夹已存在';
        return;
      } else if (createType.value === 'executable') {
        pendingOverwrite.value = { path: filePath, isExecutable: true, nameToCreate };
        createErrorMessage.value = '同名可执行文件已存在，点击“覆盖”继续或修改名称。';
        return;
      }
    }

    if (createType.value === 'file' || createType.value === 'executable') {
      const dotIndex2 = nameToCreate.indexOf('.');
      const ext2 = dotIndex2 >= 0 ? nameToCreate.substring(dotIndex2 + 1).toLowerCase() : '';
      const isExecutable = (ext2 === 'ex');
      const initialContent = isExecutable
        ? 'x=0;\nx++;\n!A1;\nend.'
        : "";
      const res = await kernelApi.createFile(filePath, initialContent, isExecutable, false);
      if (res && res.data) {
        if (res.data.success === false) {
          const msg = res.data.error || '创建文件失败';
          if (msg.includes('已存在')) {
            pendingOverwrite.value = { path: filePath, isExecutable, nameToCreate };
            createErrorMessage.value = '同名文件已存在，点击“覆盖”继续或修改名称。';
            return;
          }
          createErrorMessage.value = msg;
          return;
        }
        if (res.data.disk) { try { osStore.applyDiskSnapshot(res.data.disk); } catch (ignored) {} }
      }
      windowsStore.openWindow(nameToCreate, markRaw(TextEditor), { path: filePath });
    } else {
      const res = await kernelApi.createDir(filePath);
      if (res && res.data) {
        if (res.data.success === false) {
          const msg = res.data.error || '创建文件夹失败';
          if (msg.includes('文件名最多')) {
            createErrorMessage.value = '文件名最多 3 个字符，扩展名最多 2 个字符';
          } else {
            createErrorMessage.value = msg;
          }
          return;
        }
        if (res.data.disk) { try { osStore.applyDiskSnapshot(res.data.disk); } catch (ignored) {} }
      }
    }
    resetCreateState();
    await fetchItems();
    try { /* already merged snapshot from response if available */ } catch(e){}
  } catch (e) {
    showNotice('创建失败: ' + (e && e.message ? e.message : e));
  }
};
</script>