<template>
  <div class="relative flex flex-col h-full bg-os-bg">
    <!-- Toolbar -->
    <div class="h-8 bg-os-surface border-b border-os-surfaceHighlight flex items-center px-2 gap-2">
      <button @click="save" class="text-xs px-2 py-1 bg-os-accent hover:bg-os-accentHover rounded text-white transition-colors">
        保存
      </button>
      <span class="text-xs text-os-muted ml-auto">{{ path || '新文件' }}</span>
    </div>
    <div v-if="isExecutableFile" class="px-3 py-2 text-xs bg-amber-50 text-amber-700 border-b border-amber-200 space-y-1">
      <div class="font-medium text-amber-800">可执行文件语法提示：</div>
      <div><code>x=0;</code> / <code>x=9;</code> — 给变量 <code>x</code> 赋值 0-9。</div>
      <div><code>x++;</code> / <code>x--;</code> — 变量自增 / 自减（范围保持 0-9）。</div>
      <div><code>!A3;</code> / <code>!B2;</code> / <code>!C5;</code> — 申请设备 A/B/C，使用 <code>3/2/5</code> 个时间片，期间进程会阻塞。</div>
      <div><code>end.</code> — 程序结束，并将 <code>x</code> 的结果写入 <code>out</code> 文件。</div>
    </div>
    
    <textarea 
      v-model="content" 
      class="flex-1 bg-os-bg text-os-text p-2 font-mono text-sm resize-none focus:outline-none"
      spellcheck="false"
    ></textarea>
    <div class="h-8 bg-os-surface border-t border-os-surfaceHighlight flex items-center px-3 text-xs text-os-muted">
      <div class="ml-auto">字符: {{ charCount }} · 词数: {{ wordCount }}</div>
    </div>
    <!-- Save modal for unnamed files -->
    <div v-if="saveModal" class="fixed inset-0 bg-black/40 flex items-center justify-center z-50">
      <div class="bg-os-surface p-4 rounded w-96">
        <div class="text-sm font-medium mb-2">保存为</div>
        <input v-model="saveName" class="w-full p-2 mb-2 bg-white/5 rounded text-sm" placeholder="请输入路径，例如 /NEWFILE.TX" />
        <div class="flex justify-end gap-2">
          <button @click="cancelSaveModal" class="px-2 py-1 rounded text-xs">取消</button>
          <button @click="confirmSave" class="px-2 py-1 bg-os-accent text-white rounded text-xs">保存</button>
        </div>
      </div>
    </div>
    <!-- Save toast (centered inside window) -->
    <div v-if="savedNotice" class="absolute inset-0 flex items-center justify-center pointer-events-none">
      <div class="bg-green-600 text-white text-xs px-3 py-2 rounded shadow pointer-events-auto">
        已保存
      </div>
    </div>
    <!-- inline notice (errors/info) -->
    <div v-if="notice" class="fixed bottom-4 left-1/2 transform -translate-x-1/2 z-50 pointer-events-none">
      <div class="bg-black/80 text-white px-3 py-1 rounded text-sm pointer-events-auto">
        {{ notice }}
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue';
import { kernelApi } from '../../api/kernel';
import { useOsStore } from '../../stores/os';
import { onBeforeUnmount } from 'vue';

const props = defineProps({
  path: String
});

const content = ref('');
const savedNotice = ref(false);
let savedTimer = null;
const saveModal = ref(false);
const saveName = ref('');
const notice = ref('');
let noticeTimer = null;
const pendingOverwrite = ref(null);
const osStore = useOsStore();
const showNotice = (msg, ms = 2200) => {
  notice.value = msg;
  if (noticeTimer) { clearTimeout(noticeTimer); noticeTimer = null; }
  noticeTimer = setTimeout(() => { notice.value = ''; noticeTimer = null; }, ms);
};

const isExecutableFile = computed(() => {
  if (!props.path) return false;
  const dot = props.path.lastIndexOf('.');
  if (dot < 0) return false;
  return props.path.substring(dot + 1).toLowerCase() === 'ex';
});

const charCount = computed(() => {
  return content.value ? content.value.length : 0;
});

const wordCount = computed(() => {
  if (!content.value) return 0;
  const trimmed = content.value.trim();
  if (trimmed === '') return 0;
  return trimmed.split(/\s+/).filter(Boolean).length;
});

onMounted(async () => {
  if (props.path) {
    try {
      const res = await kernelApi.readFile(props.path);
      if (res.data && res.data.content) {
        content.value = res.data.content;
      }
    } catch (e) {
      console.error("Failed to read file", e);
      content.value = "读取文件错误。";
    }
  }
});

const showSavedToast = () => {
  savedNotice.value = true;
  if (savedTimer) clearTimeout(savedTimer);
  savedTimer = setTimeout(() => { savedNotice.value = false; savedTimer = null; }, 1100);
};

const performSave = async (pathToSave, forceOverwrite, source) => {
  const dot = pathToSave.lastIndexOf('.');
  const ext = dot >= 0 ? pathToSave.substring(dot + 1).toLowerCase() : '';
  const isExecutable = (ext === 'ex');
  const res = await kernelApi.createFile(pathToSave, content.value, isExecutable, forceOverwrite);
  if (res && res.data) {
    if (res.data.success === false) {
      if (savedTimer) { clearTimeout(savedTimer); savedTimer = null; }
      savedNotice.value = false;
      const msg = res.data.error || '保存失败';
      if (!forceOverwrite && msg.includes('已存在')) {
        pendingOverwrite.value = { path: pathToSave, source };
        showNotice('同名文件已存在，再次点击保存将覆盖。');
        return false;
      }
      throw new Error(msg);
    }
    if (res.data.disk) {
      try { osStore.applyDiskSnapshot(res.data.disk); } catch (ignored) {}
    }
  }
  pendingOverwrite.value = null;
  try { await osStore.fetchInfo(); } catch (e) {}
  showSavedToast();
  if (source === 'modal') {
    saveModal.value = false;
  }
  return true;
};

const save = async () => {
  try {
    const pathToSave = props.path;
    if (!pathToSave) {
      pendingOverwrite.value = null;
      saveModal.value = true;
      return;
    }
    const shouldOverwrite = pendingOverwrite.value && pendingOverwrite.value.path === pathToSave && pendingOverwrite.value.source === 'inline';
    await performSave(pathToSave, shouldOverwrite || !pendingOverwrite.value, 'inline');
  } catch (e) {
    showNotice('保存失败: ' + (e && e.message ? e.message : e));
  }
};

const confirmSave = async () => {
  if (!saveName.value) { showNotice('请输入保存路径'); return; }
  try {
    const shouldOverwrite = pendingOverwrite.value && pendingOverwrite.value.path === saveName.value && pendingOverwrite.value.source === 'modal';
    const completed = await performSave(saveName.value, !!shouldOverwrite, 'modal');
    if (!completed) {
      // keep modal open for user confirmation
      return;
    }
  } catch (e) {
    showNotice('保存失败: ' + (e && e.message ? e.message : e));
  }
};

const cancelSaveModal = () => {
  saveModal.value = false;
  pendingOverwrite.value = null;
};

onBeforeUnmount(() => { if (savedTimer) clearTimeout(savedTimer); });
onBeforeUnmount(() => { if (noticeTimer) clearTimeout(noticeTimer); });
</script>