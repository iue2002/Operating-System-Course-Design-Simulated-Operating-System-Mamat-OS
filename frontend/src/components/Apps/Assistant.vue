<template>
  <div class="flex flex-col h-full bg-transparent text-os-text font-sans">
    <!-- Header -->
    <header 
      class="h-12 flex items-center justify-between px-4 border-b border-white/10 bg-white/5 backdrop-blur-md shrink-0 cursor-move select-none"
      @pointerdown="$emit('drag-start', $event)"
    >
      <div class="flex items-center gap-3 pointer-events-none">
        <Sparkles class="w-5 h-5 text-white drop-shadow-[0_0_8px_rgba(255,255,255,0.5)]" />
        <div class="flex flex-col">
          <span class="text-sm font-bold tracking-wide text-white">麦小助</span>
          <span class="text-[10px] text-white/40 font-medium tracking-wider uppercase">Intelligent Assistant</span>
        </div>
      </div>
      <div class="flex items-center gap-2">
        <div class="flex items-center gap-2 bg-black/20 px-2 py-1 rounded-full border border-white/5">
          <div class="w-1.5 h-1.5 rounded-full transition-colors duration-500" :class="status === 'streaming' ? 'bg-green-400 shadow-[0_0_8px_rgba(74,222,128,0.6)]' : 'bg-white/20'"></div>
          <span class="text-[10px] text-white/60 font-medium">{{ connectionLabel }}</span>
        </div>
        <button 
          @click.stop="$emit('minimize')"
          @pointerdown.stop
          class="relative z-[100] w-6 h-6 flex items-center justify-center rounded-full hover:bg-white/10 text-white/40 hover:text-white transition-colors cursor-pointer"
        >
          <Minus class="w-4 h-4" />
        </button>
      </div>
    </header>

    <!-- Chat Area -->
    <main ref="scrollRef" class="flex-1 overflow-y-auto p-4 space-y-6 scrollbar-thin scrollbar-thumb-white/10 scrollbar-track-transparent">
      <transition-group name="assistant-msg" tag="div" class="space-y-6">
        <div v-for="(msg, idx) in messages" :key="idx" :class="['flex gap-4', msg.role === 'user' ? 'flex-row-reverse' : '']">
          
          <!-- Message Bubble -->
          <div class="max-w-[80%] min-w-0">
            <div class="text-[10px] text-white/30 mb-1 px-1" :class="msg.role === 'user' ? 'text-right' : ''">
              {{ msg.role === 'assistant' ? '麦小助' : 'You' }}
            </div>
            <div :class="['rounded-2xl px-4 py-3 text-sm leading-relaxed whitespace-pre-wrap break-words shadow-md transition-all duration-200', bubbleClass(msg.role)]">
              <span v-html="formatMarkdown(msg.content)"></span>
            </div>
          </div>
        </div>
      </transition-group>

      <!-- Loading Indicator -->
      <div v-if="status === 'connecting'" class="flex gap-4">
        <div class="flex items-center gap-1 h-10 px-4 rounded-2xl bg-white/5 border border-white/10">
          <div class="w-1.5 h-1.5 bg-white/40 rounded-full animate-bounce" style="animation-delay: 0ms"></div>
          <div class="w-1.5 h-1.5 bg-white/40 rounded-full animate-bounce" style="animation-delay: 150ms"></div>
          <div class="w-1.5 h-1.5 bg-white/40 rounded-full animate-bounce" style="animation-delay: 300ms"></div>
        </div>
      </div>
    </main>

    <!-- Input Area -->
    <footer class="p-4 bg-gradient-to-t from-black/80 to-transparent">
      <div class="relative group">
        <div class="absolute -inset-0.5 bg-gradient-to-r from-blue-500/20 to-purple-500/20 rounded-2xl blur opacity-0 group-focus-within:opacity-100 transition duration-500"></div>
        <div class="relative flex items-end gap-2 bg-[#1c1c1e] border border-white/10 rounded-2xl p-2 shadow-xl">
          <textarea
            v-model="draft"
            @keydown.enter.prevent="handleEnter"
            placeholder="Ask anything..."
            class="flex-1 bg-transparent border-none text-white placeholder-white/20 text-sm px-3 py-2.5 resize-none focus:ring-0 max-h-32 scrollbar-none"
            rows="1"
            style="min-height: 44px;"
          />
          <button
            class="mb-0.5 p-2 rounded-xl transition-all duration-300 flex items-center justify-center shrink-0"
            :class="sendEnabled 
              ? 'bg-blue-600 text-white shadow-lg shadow-blue-600/30 hover:bg-blue-500 hover:scale-105 active:scale-95' 
              : 'bg-white/5 text-white/20 cursor-not-allowed'"
            :disabled="!sendEnabled"
            @click="send"
          >
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor" class="w-5 h-5">
              <path d="M3.478 2.405a.75.75 0 00-.926.94l2.432 7.905H13.5a.75.75 0 010 1.5H4.984l-2.432 7.905a.75.75 0 00.926.94 60.519 60.519 0 0018.445-8.986.75.75 0 000-1.218A60.517 60.517 0 003.478 2.405z" />
            </svg>
          </button>
        </div>
      </div>
      <div class="text-center mt-2">
        <p class="text-[10px] text-white/20 font-medium tracking-wide">Powered by Wenxin LLM · Real-time Network</p>
      </div>
    </footer>
  </div>
</template>

<script setup>
import { ref, computed, nextTick, onUnmounted } from 'vue';
import { Sparkles, Minus } from 'lucide-vue-next';
import { callAgent } from '../../api/assistant';

const emit = defineEmits(['minimize', 'drag-start']);

const messages = ref([
  {
    role: 'assistant',
    content: '嘿，我是麦小助。随时问我关于麦麦提OS的任何事情，或者直接让我搜搜网络。',
  },
]);

const draft = ref('');
const isStreaming = ref(false);
const status = ref('idle');
const scrollRef = ref(null);
const agentThreadId = ref(null);

const agentOpenIdKey = 'os-agent-open-id';
const agentOpenId = ref('');

if (typeof window !== 'undefined') {
  const existing = window.localStorage.getItem(agentOpenIdKey);
  if (existing) {
    agentOpenId.value = existing;
  } else {
    const generated = typeof window.crypto !== 'undefined' && window.crypto.randomUUID
      ? window.crypto.randomUUID()
      : `uid-${Date.now()}`;
    agentOpenId.value = generated;
    window.localStorage.setItem(agentOpenIdKey, generated);
  }
}

const connectionLabel = computed(() => {
  if (status.value === 'connecting') return '正在召唤麦小助…';
  if (status.value === 'streaming') return '麦小助正在输出…';
  if (status.value === 'error') return '连接失败，请重试';
  return '在线';
});

const sendEnabled = computed(() => draft.value.trim().length > 0 && !isStreaming.value);

const bubbleClass = (role) => (role === 'user'
  ? 'bg-blue-600 text-white shadow-lg shadow-blue-600/20'
  : 'bg-[#2c2c2e] text-gray-100 border border-white/5');

const scrollToBottom = () => {
  nextTick(() => {
    const el = scrollRef.value;
    if (el) el.scrollTop = el.scrollHeight;
  });
};

let typingTimer = null;

const clearTypingTimer = () => {
  if (typingTimer) {
    clearTimeout(typingTimer);
    typingTimer = null;
  }
};

const animateAssistantReply = (text) => new Promise((resolve) => {
  const chunkSize = 3;
  const content = text || '';
  // Push initial empty message
  messages.value.push({ role: 'assistant', content: '' });
  // Get the reactive reference from the array
  const targetMessage = messages.value[messages.value.length - 1];

  const step = (index) => {
    if (index >= content.length) {
      typingTimer = null;
      resolve();
      return;
    }
    targetMessage.content += content.slice(index, index + chunkSize);
    scrollToBottom();
    typingTimer = window.setTimeout(() => step(index + chunkSize), 25);
  };

  step(0);
});

onUnmounted(() => {
  clearTypingTimer();
});

const send = async () => {
  if (!sendEnabled.value) return;
  const content = draft.value.trim();
  draft.value = '';

  messages.value.push({ role: 'user', content });
  scrollToBottom();

  isStreaming.value = true;
  status.value = 'connecting';

  try {
    const result = await callAgent({
      prompt: content,
      threadId: agentThreadId.value,
      openId: agentOpenId.value || 'guest',
    });

    if (result.threadId) {
      agentThreadId.value = result.threadId;
    }

    const reply = result.text?.trim();

    if (reply) {
      status.value = 'streaming';
      await animateAssistantReply(reply);
      status.value = 'idle';
      scrollToBottom();
    } else {
      messages.value.push({
        role: 'assistant',
        content: '（这次没有收到麦小助的回复。）',
      });
      status.value = 'idle';
      scrollToBottom();
    }
  } catch (err) {
    console.error('麦小助接入失败', err);
    status.value = 'error';
    clearTypingTimer();
    messages.value.push({
      role: 'assistant',
      content: err.message || '未能连接麦小助，确认密钥配置后再试一次吧。',
    });
    scrollToBottom();
  }

  clearTypingTimer();
  isStreaming.value = false;
};

const handleEnter = (evt) => {
  if (evt.shiftKey) return;
  send();
};

const formatMarkdown = (text) => {
  return text
    .replace(/\n/g, '<br/>')
    .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
    .replace(/`([^`]+)`/g, '<code class="px-1 bg-black/40 rounded">$1</code>');
};
</script>

<style scoped>
.animate-assistant-enter {
  animation: assistant-pop 0.35s ease-out;
}

@keyframes assistant-pop {
  0% {
    opacity: 0;
    transform: scale(0.88) translateY(16px);
  }
  60% {
    opacity: 1;
    transform: scale(1.03) translateY(-4px);
  }
  100% {
    opacity: 1;
    transform: scale(1) translateY(0);
  }
}

.assistant-msg-enter-active,
.assistant-msg-leave-active {
  transition: all 0.2s ease;
}

.assistant-msg-enter-from {
  opacity: 0;
  transform: translateY(6px);
}

.assistant-msg-leave-to {
  opacity: 0;
  transform: translateY(-6px);
}
</style>
