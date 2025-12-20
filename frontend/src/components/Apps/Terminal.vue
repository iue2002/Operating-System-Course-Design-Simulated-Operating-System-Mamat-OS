<template>
  <div class="flex flex-col h-full bg-black text-green-400 font-mono text-sm p-2" @click="focusInput">
    <div class="flex-1 overflow-auto whitespace-pre-wrap">
      <div v-for="(line, i) in history" :key="i">{{ line }}</div>
    </div>
    <div class="flex items-center">
      <span class="mr-2">$</span>
      <input 
        ref="inputRef"
        v-model="currentCommand" 
        @keydown.enter="execute"
        class="flex-1 bg-transparent border-none outline-none text-green-400"
        type="text"
        autofocus
      />
    </div>
  </div>
</template>

<script setup>
import { ref, nextTick } from 'vue';
import { kernelApi } from '../../api/kernel';
import { useOsStore } from '../../stores/os';

const history = ref(['欢迎使用 OS 模拟器 v1.0', '输入 "help" 查看命令。']);
const currentCommand = ref('');
const inputRef = ref(null);
const currentPath = ref('/');
const osStore = useOsStore();
const isMemoryError = (msg) => typeof msg === 'string' && (msg.includes('内存已满') || msg.includes('内存不足'));

const focusInput = () => {
  inputRef.value?.focus();
};

const execute = async () => {
  const cmd = currentCommand.value.trim();
  history.value.push(`$ ${cmd}`);
  currentCommand.value = '';

  if (!cmd) return;

  const parts = cmd.split(' ');
  const command = parts[0];
  const args = parts.slice(1);

  try {
    switch (command) {
      case 'help':
        history.value.push('可用命令: ls, cd, cat, run, mkdir, touch, rm, clear, help');
        break;
      case 'clear':
        history.value = [];
        break;
      case 'ls':
        const res = await kernelApi.listDir(currentPath.value);
        const files = res.data.map(f => f.name + (f.ext ? '.' + f.ext : '/')).join('  ');
        history.value.push(files || '(空)');
        break;
      case 'mkdir':
          if (!args[0]) {
          history.value.push('用法: mkdir <目录名>');
        } else {
          const dirPath = currentPath.value === '/' ? `/${args[0]}` : `${currentPath.value}/${args[0]}`;
          const res = await kernelApi.createDir(dirPath);
          if (res && res.data) {
            if (res.data.success === false) {
              history.value.push(`错误: ${res.data.error || '创建目录失败'}`);
            } else {
              if (res.data.disk) {
                try { osStore.applyDiskSnapshot(res.data.disk); } catch (ignored) {}
              }
              history.value.push(`目录 "${args[0]}" 已创建`);
            }
          } else {
            history.value.push(`目录 "${args[0]}" 已创建`);
          }
        }
        break;
      case 'touch':
          if (!args[0]) {
          history.value.push('用法: touch <文件名>');
        } else {
          const filePath = currentPath.value === '/' ? `/${args[0]}` : `${currentPath.value}/${args[0]}`;
          const res = await kernelApi.createFile(filePath, "");
          if (res && res.data) {
            if (res.data.success === false) {
              history.value.push(`错误: ${res.data.error || '创建文件失败'}`);
            } else {
              if (res.data.disk) {
                try { osStore.applyDiskSnapshot(res.data.disk); } catch (ignored) {}
              }
              history.value.push(`文件 "${args[0]}" 已创建`);
            }
          } else {
            history.value.push(`文件 "${args[0]}" 已创建`);
          }
        }
        break;
      case 'rm':
          if (!args[0]) {
          history.value.push('用法: rm <文件名>');
        } else {
          const filePath = currentPath.value === '/' ? `/${args[0]}` : `${currentPath.value}/${args[0]}`;
          const res = await kernelApi.deleteFile(filePath);
          if (res && res.data && res.data.disk) {
            try { osStore.applyDiskSnapshot(res.data.disk); } catch (ignored) {}
          }
          history.value.push(`文件 "${args[0]}" 已删除`);
        }
        break;
      case 'run':
        if (!args[0]) {
          history.value.push('用法: run <路径>');
        } else {
          const runRes = await kernelApi.runProcess(args[0]);
          if (runRes && runRes.data && runRes.data.success) {
            history.value.push(`进程已启动，PID ${runRes.data.pid}`);
          } else {
            const errMsg = runRes && runRes.data ? (runRes.data.error || '失败') : '失败';
            if (isMemoryError(errMsg)) {
              history.value.push('内存已满，进程创建失败');
            } else {
              history.value.push(`错误: ${errMsg}`);
            }
          }
        }
        break;
      default:
        history.value.push(`未知命令: ${command}`);
    }
  } catch (e) {
    history.value.push(`错误: ${e.message}`);
  }

  nextTick(() => {
    // Scroll to bottom
    const container = inputRef.value?.parentElement?.parentElement;
    if (container) container.scrollTop = container.scrollHeight;
  });
};
</script>