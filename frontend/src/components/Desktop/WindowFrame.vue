<template>
  <!-- Positioning Wrapper (No Animation) -->
  <div
    ref="windowRef"
    class="absolute"
    :class="{ 
      'pointer-events-none': isMinimized,
      'transition-all duration-700 ease-[cubic-bezier(0.25,1,0.5,1)]': !isDragging && !isResizing && !isInertia
    }"
    :style="{
        transform: isMinimized
          ? `translate3d(${position.x}px, ${position.y}px, 0) ${minimizeTransform}`
          : `translate3d(${position.x}px, ${position.y}px, 0)`,
      width: `${width}px`,
      height: `${height}px`,
      zIndex: zIndex,
      opacity: isMinimized ? 0 : 1,
      filter: isMinimized ? 'blur(20px)' : 'blur(0px)',
        clipPath: isMinimized ? 'polygon(0% 0%, 100% 0%, 60% 100%, 40% 100%)' : 'polygon(0% 0%, 100% 0%, 100% 100%, 0% 100%)',
        transformOrigin: 'center center'
    }"
    @mousedown="onFocus"
  >
    <!-- Visual Window (Animated) -->
    <div 
      class="flex flex-col w-full h-full bg-os-surface backdrop-blur-2xl border border-os-border rounded-xl shadow-2xl overflow-hidden animate-pop-in"
      :class="{ 
        'ring-1 ring-os-border shadow-os-accent/20': isActive,
        'pointer-events-none': isDragging || isResizing,
        'transition-all duration-300': !isDragging && !isInertia && !isResizing && !isMinimized
      }"
    >
      <!-- Title Bar -->
      <div
        class="h-10 bg-white/5 flex items-center justify-between px-3 cursor-default select-none border-b border-white/5"
        @mousedown="startDrag"
        @dblclick="toggleMaximize"
      >
        <div class="flex items-center gap-2 pointer-events-none">
          <component :is="icon" v-if="icon" class="w-4 h-4 text-os-muted" />
          <span class="text-sm font-medium text-os-text shadow-sm">{{ title }}</span>
        </div>
        <div class="flex items-center gap-2" @mousedown.stop>
          <button @click.stop="onMinimize" class="p-1.5 hover:bg-white/10 rounded-full transition-colors">
            <Minus class="w-3.5 h-3.5 text-os-text" />
          </button>
          <button @click.stop="onClose" class="p-1.5 hover:bg-red-500 hover:text-white rounded-full transition-colors group">
            <X class="w-3.5 h-3.5 text-os-text group-hover:text-white" />
          </button>
        </div>
      </div>

      <!-- Content -->
      <div class="flex-1 overflow-auto relative bg-os-bg/40 pointer-events-auto">
        <slot></slot>
      </div>
      
      <!-- Resizer -->
      <div class="absolute bottom-0 right-0 w-5 h-5 cursor-se-resize flex items-center justify-center opacity-50 hover:opacity-100" @mousedown.stop="startResize">
        <div class="w-2 h-2 border-r-2 border-b-2 border-os-muted"></div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch, nextTick } from 'vue';
import { X, Minus } from 'lucide-vue-next';

const props = defineProps({
  id: Number,
  title: String,
  icon: { type: [Object, Function], default: null },
  initialX: { type: Number, default: 100 },
  initialY: { type: Number, default: 100 },
  zIndex: Number,
  isActive: Boolean,
  isMinimized: Boolean
});

const emit = defineEmits(['close', 'focus', 'minimize']);

const windowRef = ref(null);

const computeDefaultSize = (title) => {
  if (!title) return { width: 600, height: 400 };
  if (title.includes('任务管理器')) return { width: 780, height: 540 };
  if (title.includes('RAM和CPU监视器')) return { width: 720, height: 520 };
  return { width: 600, height: 400 };
};

const defaultSize = computeDefaultSize(props.title);
const width = ref(defaultSize.width);
const height = ref(defaultSize.height);
const position = reactive({ x: props.initialX, y: props.initialY });
const isDragging = ref(false);
const isResizing = ref(false);
const isInertia = ref(false);
let velocity = { x: 0, y: 0 };
let inertiaFrame = null;
let lastMouseX = 0;
let lastMouseY = 0;
let lastTime = 0;

// minimizeTarget holds the target screen coordinates for the minimize animation (center x,y)
const minimizeTarget = ref(null);
const minimizeTransform = computed(() => {
  if (!props.isMinimized) return '';
  // If a target is present, compute translate to that point then scale down
  const centerX = position.x + width.value / 2;
  const centerY = position.y + height.value / 2;
  if (minimizeTarget.value && minimizeTarget.value.x !== undefined) {
    const dx = minimizeTarget.value.x - centerX;
    const dy = minimizeTarget.value.y - centerY;
    return `translate3d(${dx}px, ${dy}px, 0) scale(0.02, 0.02)`;
  }
  // fallback: shrink in place
  return `scale(0.02, 0.02)`;
});

// When isMinimized prop changes to true, attempt to find the taskbar icon and set minimizeTarget
watch(() => props.isMinimized, async (val) => {
  if (!val) {
    minimizeTarget.value = null;
    return;
  }
  // wait for DOM to settle
  await nextTick();
  try {
    const selector = `[data-win-id=\"${props.id}\"]`;
    const el = document.querySelector(selector);
    if (el) {
      const r = el.getBoundingClientRect();
      // compute center of icon
      minimizeTarget.value = { x: r.left + r.width / 2, y: r.top + r.height / 2 };
      return;
    }
  } catch (e) {
    // ignore
  }
  // default: bottom center of screen near taskbar
  minimizeTarget.value = { x: window.innerWidth / 2, y: window.innerHeight - 24 };
});

const onFocus = () => {
  emit('focus', props.id);
};

const onClose = () => {
  emit('close', props.id);
};

const onMinimize = () => {
  emit('minimize', props.id);
};

// Optimized Drag Logic with Inertia
const startDrag = (e) => {
  if (e.target.closest('button')) return;
  
  // Stop any existing inertia
  if (inertiaFrame) {
    cancelAnimationFrame(inertiaFrame);
    isInertia.value = false;
  }

  isDragging.value = true;
  onFocus();
  
  const startX = e.clientX;
  const startY = e.clientY;
  const initialLeft = position.x;
  const initialTop = position.y;
  
  // Initialize velocity tracking
  lastMouseX = e.clientX;
  lastMouseY = e.clientY;
  lastTime = performance.now();
  velocity = { x: 0, y: 0 };

  const onMouseMove = (e) => {
    const now = performance.now();
    const dt = now - lastTime;
    
    // 1:1 Movement (No lag)
    position.x = initialLeft + (e.clientX - startX);
    position.y = initialTop + (e.clientY - startY);
    
    // Calculate velocity
    if (dt > 0) {
        const vx = e.clientX - lastMouseX;
        const vy = e.clientY - lastMouseY;
        velocity.x = vx;
        velocity.y = vy;
    }
    
    lastMouseX = e.clientX;
    lastMouseY = e.clientY;
    lastTime = now;
  };

  const onMouseUp = () => {
    isDragging.value = false;
    document.removeEventListener('mousemove', onMouseMove);
    document.removeEventListener('mouseup', onMouseUp);
    
    // Start Inertia
    startInertia();
  };

  document.addEventListener('mousemove', onMouseMove);
  document.addEventListener('mouseup', onMouseUp);
};

const startInertia = () => {
  // Reduce fling distance: scale down measured velocity and use stronger friction
  // Keep inertia behavior but make light drags stop quickly.
  // Adjusted to make inertia feel smoother and less "笨重".
  const velocityScale = 0.6; // damp initial fling so release stays controlled
  const friction = 0.86; // stronger decay to shorten glide while keeping it smooth
  const stopThreshold = 0.2; // let tiny drift end sooner without feeling abrupt

  velocity.x *= velocityScale;
  velocity.y *= velocityScale;

  if (Math.abs(velocity.x) < stopThreshold && Math.abs(velocity.y) < stopThreshold) return;

  isInertia.value = true;

  const loop = () => {
    if (Math.abs(velocity.x) < stopThreshold && Math.abs(velocity.y) < stopThreshold) {
      isInertia.value = false;
      return;
    }

    position.x += velocity.x;
    position.y += velocity.y;

    velocity.x *= friction;
    velocity.y *= friction;
        
        // Simple bounds check to prevent losing windows
        const screenW = window.innerWidth;
        const screenH = window.innerHeight;
        
        if (position.x > screenW - 50) { position.x = screenW - 50; velocity.x *= -0.5; }
        if (position.x < -width.value + 50) { position.x = -width.value + 50; velocity.x *= -0.5; }
        if (position.y > screenH - 50) { position.y = screenH - 50; velocity.y *= -0.5; }
        if (position.y < 0) { position.y = 0; velocity.y *= -0.5; }

        inertiaFrame = requestAnimationFrame(loop);
    };
    loop();
};

// Resize Logic
const startResize = (e) => {
  e.preventDefault();
  isResizing.value = true;
  onFocus();
  
  const startX = e.clientX;
  const startY = e.clientY;
  const startWidth = width.value;
  const startHeight = height.value;

  const onMouseMove = (e) => {
    // Direct update, no requestAnimationFrame needed for simple resize if transition is off
    width.value = Math.max(300, startWidth + (e.clientX - startX));
    height.value = Math.max(200, startHeight + (e.clientY - startY));
  };

  const onMouseUp = () => {
    isResizing.value = false;
    document.removeEventListener('mousemove', onMouseMove);
    document.removeEventListener('mouseup', onMouseUp);
  };

  document.addEventListener('mousemove', onMouseMove);
  document.addEventListener('mouseup', onMouseUp);
};
</script>
