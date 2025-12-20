<template>
  <!-- Droplets Layer (Fixed Fullscreen, Behind Orb) -->
  <div class="fixed inset-0 pointer-events-none z-[119999] overflow-hidden">
    <div 
      v-for="drop in droplets" 
      :key="drop.id"
      class="absolute pointer-events-none"
      :style="{
        left: `${drop.absoluteX}px`,
        top: `${drop.absoluteY}px`,
        width: `${drop.size}px`,
        height: `${drop.size}px`,
        '--fall-dist': `${drop.fallDistance}px`,
        animation: `drop-fall-y ${drop.duration}s cubic-bezier(0.6, 0.05, 0.9, 0.2) forwards`
      }"
    >
      <div 
        class="w-full h-full rounded-full backdrop-blur-[2px] border border-white/20"
        :style="{
          background: 'radial-gradient(130% 130% at 30% 20%, rgba(255,255,255,0.95) 0%, rgba(255,255,255,0.3) 20%, rgba(147,51,234,0.15) 100%)',
          boxShadow: 'inset 2px 2px 4px rgba(255,255,255,0.5), inset -2px -2px 4px rgba(0,0,0,0.1), 0 4px 8px rgba(0,0,0,0.15)',
          animation: `drop-shape ${drop.duration}s linear forwards`
        }"
      ></div>
    </div>
    
    <!-- Splash Particles -->
    <div 
      v-for="splash in splashes" 
      :key="splash.id"
      class="absolute rounded-full bg-white/80 shadow-[0_0_4px_rgba(255,255,255,0.8)]"
      :style="{
        left: `${splash.x}px`,
        top: `${splash.y}px`,
        width: `${splash.size}px`,
        height: `${splash.size}px`,
        '--tx': `${splash.tx}px`,
        '--ty': `${splash.ty}px`,
        animation: `splash-fly 0.8s linear forwards`
      }"
    ></div>
  </div>

  <div 
    ref="wrapperRef"
    class="fixed top-0 left-0 z-[120000] pointer-events-none will-change-transform"
    :class="{ 'transition-all duration-300 ease-out': !isDragging }"
  >
    <div ref="recoilWrapper" class="pointer-events-auto relative will-change-transform">
      <!-- Tension Blob (Hidden extension of the orb) -->
      <div 
        ref="tensionRef"
        class="absolute left-[calc(75%)] bottom-[10px] -translate-x-1/2 w-8 h-8 rounded-full opacity-0 pointer-events-none -z-10"
        style="
          background: radial-gradient(circle at center, var(--tw-gradient-stops)); 
          --tw-gradient-from: #2563eb; 
          --tw-gradient-to: #ec4899; 
          --tw-gradient-stops: var(--tw-gradient-from), #9333ea, var(--tw-gradient-to);
          filter: brightness(0.9);
        "
      ></div>

      <!-- Main Morphing Container -->
      <div 
        ref="morphContainer"
        class="relative z-10 overflow-hidden shadow-[0_0_50px_rgba(0,0,0,0.5)] transition-all duration-300 ease-out will-change-[width,height,border-radius,transform]"
        :class="{ 
          'animate-liquid-morph': !expanded,
          'rounded-2xl': expanded
        }"
        :style="{
          width: `${currentSize.width}px`,
          height: `${currentSize.height}px`,
          borderRadius: expanded ? '16px' : '50%',
          transform: expanded ? 'none' : dragDeformation
        }"
        @pointerdown="startDrag"
        @click="handleContainerClick"
        @mouseenter="resetIdleTimer"
        @mouseleave="startIdleTimer"
      >
        <!-- Liquid Background (Always Visible) -->
        <div class="absolute inset-0 scale-110 bg-[radial-gradient(circle_at_center,_var(--tw-gradient-stops))] from-blue-600 via-purple-600 to-pink-500 opacity-80 blur-sm transition-all duration-500"></div>
        <div class="absolute inset-0 bg-black/40 backdrop-blur-sm transition-all duration-500" :class="{ 'bg-black/80': expanded }"></div>

        <!-- Bubble Content (Particles & Icon) -->
        <div 
          class="absolute inset-0 transition-opacity duration-300 flex items-center justify-center"
          :class="{ 'opacity-0 pointer-events-none': expanded, 'opacity-100': !expanded }"
        >
          <canvas ref="particleCanvas" width="64" height="64" class="w-full h-full opacity-80 absolute inset-0"></canvas>
          <Sparkles class="w-6 h-6 text-white animate-pulse drop-shadow-[0_0_8px_rgba(255,255,255,0.5)] relative z-10" />
        </div>

        <!-- Window Content (Chat App) -->
        <div 
          class="absolute inset-0 transition-opacity duration-300 flex flex-col z-50"
          :class="{ 'opacity-0 pointer-events-none': !expanded, 'opacity-100': expanded }"
        >
          <AssistantApp 
            class="flex-1" 
            @minimize="close" 
            @drag-start="startDrag"
          />
        </div>
      </div>

      <!-- Idle Message Bubble (Outside Container) -->
      <transition name="fade-slide">
        <div 
          v-if="!expanded && idleMessage"
          class="absolute right-full mr-3 top-1/2 -translate-y-1/2 whitespace-nowrap px-3 py-1.5 bg-black/80 backdrop-blur-md border border-white/10 rounded-xl text-xs text-white shadow-lg pointer-events-none"
        >
          {{ idleMessage }}
          <div class="absolute top-1/2 -right-1 -translate-y-1/2 w-2 h-2 bg-black/80 border-t border-r border-white/10 rotate-45"></div>
        </div>
      </transition>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch, nextTick } from 'vue';
import { Sparkles } from 'lucide-vue-next';
import AssistantApp from './Assistant.vue';

const PANEL = { width: 360, height: 520 };
const BUBBLE = { width: 64, height: 64 };
const EDGE_GAP = 24;

const expanded = ref(false);
const position = ref({ x: EDGE_GAP, y: EDGE_GAP });
const viewport = ref({ width: 0, height: 0 });
const dragOffset = ref({ x: 0, y: 0 });
const isDragging = ref(false);
const hasDragged = ref(false);
const dragStartPos = ref({ x: 0, y: 0 });
const savedPosition = ref(null);
const userMovedWindow = ref(false);
const dragDeformation = ref('');
let physicsRaf = null;

// Physics State
const physics = {
  lagX: 0, lagY: 0, velX: 0, velY: 0, lastAngle: 0
};

// Idle & Particles
const wrapperRef = ref(null);
const morphContainer = ref(null);
const recoilWrapper = ref(null);
const tensionRef = ref(null);
const particleCanvas = ref(null);
const idleMessage = ref('');
const droplets = ref([]);
const splashes = ref([]);
let idleTimer = null;
let messageTimer = null;
let animationFrame = null;
let dripInterval = null;
let dropletIdCounter = 0;
let splashIdCounter = 0;

const IDLE_MESSAGES = [
  '好无聊...', '主人在吗？', '发个呆 ( ఠ ͟ʖ ఠ )', 
  '内存好香...', '整理一下硬盘...', 'Zzz...', 
  '听见风扇的声音了吗', '想吃显卡...'
];

const currentSize = computed(() => (expanded.value ? PANEL : BUBBLE));

// Removed wrapperStyle computed to prevent Vue from overwriting manual DOM updates
// const wrapperStyle = computed(() => ({
//   transform: `translate(${position.value.x}px, ${position.value.y}px)`,
// }));

const clamp = (value, min, max) => Math.min(Math.max(value, min), max);

const updateViewport = () => {
  viewport.value = {
    width: window.innerWidth,
    height: window.innerHeight,
  };
};

const ensureInBounds = () => {
  const { width, height } = currentSize.value;
  const maxX = Math.max(EDGE_GAP, viewport.value.width - width - EDGE_GAP);
  const maxY = Math.max(EDGE_GAP, viewport.value.height - height - EDGE_GAP);
  position.value = {
    x: clamp(position.value.x, EDGE_GAP, maxX),
    y: clamp(position.value.y, EDGE_GAP, maxY),
  };
};

// Non-reactive state for drag/physics to bypass Vue overhead
const dragTargetPos = { x: 0, y: 0 };

const updatePhysics = () => {
  // Use dragTargetPos if dragging, otherwise reactive position
  const targetX = isDragging.value ? dragTargetPos.x : position.value.x;
  const targetY = isDragging.value ? dragTargetPos.y : position.value.y;
  const tension = 0.02; 
  const damping = 0.9;

  const dx = targetX - physics.lagX;
  const dy = targetY - physics.lagY;

  physics.velX = (physics.velX + dx * tension) * damping;
  physics.velY = (physics.velY + dy * tension) * damping;

  physics.lagX += physics.velX;
  physics.lagY += physics.velY;

  const lagDx = targetX - physics.lagX;
  const lagDy = targetY - physics.lagY;
  
  // Stop loop if settled
  if (Math.abs(lagDx) < 0.1 && Math.abs(lagDy) < 0.1 && Math.abs(physics.velX) < 0.1 && Math.abs(physics.velY) < 0.1 && !isDragging.value) {
    if (morphContainer.value) {
       morphContainer.value.style.transform = 'none';
    }
    physicsRaf = null;
    physics.lagX = targetX;
    physics.lagY = targetY;
    return;
  }

  const k = 0.005;
  const maxS = 0.4;
  let sx = Math.min(Math.abs(lagDx) * k, maxS);
  let sy = Math.min(Math.abs(lagDy) * k, maxS);
  
  const scaleX = 1 + sx - (sy * 0.5);
  const scaleY = 1 + sy - (sx * 0.5);

  // Direct DOM update for performance
  if (morphContainer.value && !expanded.value) {
    morphContainer.value.style.transform = `scale(${scaleX}, ${scaleY})`;
  }

  physicsRaf = requestAnimationFrame(updatePhysics);
};

const onPointerMove = (event) => {
  if (!isDragging.value) return;
  const { width, height } = currentSize.value;
  const maxX = Math.max(EDGE_GAP, viewport.value.width - width - EDGE_GAP);
  const maxY = Math.max(EDGE_GAP, viewport.value.height - height - EDGE_GAP);
  
  // Update non-reactive target
  dragTargetPos.x = clamp(event.clientX - dragOffset.value.x, EDGE_GAP, maxX);
  dragTargetPos.y = clamp(event.clientY - dragOffset.value.y, EDGE_GAP, maxY);

  // Direct DOM update for wrapper position
  if (wrapperRef.value) {
    wrapperRef.value.style.transform = `translate(${dragTargetPos.x}px, ${dragTargetPos.y}px)`;
  }

  const dx = Math.abs(event.clientX - dragStartPos.value.x);
  const dy = Math.abs(event.clientY - dragStartPos.value.y);
  if (dx > 3 || dy > 3) {
    hasDragged.value = true;
  }
};

const stopDrag = () => {
  if (!isDragging.value) return;
  isDragging.value = false;
  
  // Sync final position back to Vue state
  position.value = { ...dragTargetPos };
  
  window.removeEventListener('pointermove', onPointerMove);
  window.removeEventListener('pointerup', stopDrag);
};

const startDrag = (event) => {
  if (event.button !== 0) return;
  isDragging.value = true;
  hasDragged.value = false;
  userMovedWindow.value = true;
  dragStartPos.value = { x: event.clientX, y: event.clientY };
  
  // Initialize non-reactive target
  dragTargetPos.x = position.value.x;
  dragTargetPos.y = position.value.y;

  if (!physicsRaf) {
    physics.lagX = position.value.x;
    physics.lagY = position.value.y;
    physics.velX = 0;
    physics.velY = 0;
    updatePhysics();
  }

  dragOffset.value = {
    x: event.clientX - position.value.x,
    y: event.clientY - position.value.y,
  };
  
  window.addEventListener('pointermove', onPointerMove);
  window.addEventListener('pointerup', stopDrag);
};

const open = async () => {
  savedPosition.value = { ...position.value };
  
  // Calculate centered position for expansion
  const centerX = position.value.x + BUBBLE.width / 2;
  const centerY = position.value.y + BUBBLE.height / 2;
  
  const newX = centerX - PANEL.width / 2;
  const newY = centerY - PANEL.height / 2;
  
  // Update position to center the panel
  position.value = { x: newX, y: newY };
  ensureInBounds(); // Keep within screen

  userMovedWindow.value = false;
  // Ensure transition is enabled before state change
  isDragging.value = false;
  await nextTick();
  expanded.value = true;
};

const close = () => {
  expanded.value = false;
  // Restore original position
  if (savedPosition.value) {
    position.value = { ...savedPosition.value };
  }
};

const handleContainerClick = () => {
  if (hasDragged.value) return;
  if (expanded.value) return; // Don't close on click if already open (let minimize button do it)
  open();
};

// --- Particle System ---
class Particle {
  constructor(canvas) {
    this.canvas = canvas;
    this.x = Math.random() * canvas.width;
    this.y = Math.random() * canvas.height;
    this.vx = (Math.random() - 0.5) * 0.5;
    this.vy = (Math.random() - 0.5) * 0.5;
    this.size = Math.random() * 2 + 0.5;
    this.alpha = Math.random() * 0.5 + 0.2;
  }

  update() {
    this.x += this.vx;
    this.y += this.vy;
    if (this.x < 0 || this.x > this.canvas.width) this.vx *= -1;
    if (this.y < 0 || this.y > this.canvas.height) this.vy *= -1;
  }

  draw(ctx) {
    ctx.beginPath();
    ctx.arc(this.x, this.y, this.size, 0, Math.PI * 2);
    ctx.fillStyle = `rgba(255, 255, 255, ${this.alpha})`;
    ctx.fill();
  }
}

const initParticles = () => {
  if (!particleCanvas.value) return;
  const canvas = particleCanvas.value;
  const ctx = canvas.getContext('2d');
  const particles = Array.from({ length: 15 }, () => new Particle(canvas));

  const animate = () => {
    // Always run loop, but only draw if canvas exists and visible
    if (!particleCanvas.value) return; 
    
    // Optimization: Skip rendering if expanded (hidden)
    if (expanded.value) {
      animationFrame = requestAnimationFrame(animate);
      return;
    }

    ctx.clearRect(0, 0, canvas.width, canvas.height);
    
    particles.forEach(p => {
      p.update();
      p.draw(ctx);
    });

    particles.forEach((a, i) => {
      particles.slice(i + 1).forEach(b => {
        const dx = a.x - b.x;
        const dy = a.y - b.y;
        const dist = Math.sqrt(dx * dx + dy * dy);
        if (dist < 20) {
          ctx.beginPath();
          ctx.moveTo(a.x, a.y);
          ctx.lineTo(b.x, b.y);
          ctx.strokeStyle = `rgba(255, 255, 255, ${0.2 * (1 - dist / 20)})`;
          ctx.stroke();
        }
      });
    });
    animationFrame = requestAnimationFrame(animate);
  };
  animate();
};

// --- Idle Logic ---
const showRandomMessage = () => {
  const msg = IDLE_MESSAGES[Math.floor(Math.random() * IDLE_MESSAGES.length)];
  idleMessage.value = msg;
  if (messageTimer) clearTimeout(messageTimer);
  messageTimer = setTimeout(() => {
    idleMessage.value = '';
    startIdleTimer();
  }, 3000);
};

const startIdleTimer = () => {
  if (idleTimer) clearTimeout(idleTimer);
  // Shortened interval: 4-7 seconds
  idleTimer = setTimeout(showRandomMessage, 4000 + Math.random() * 3000);
};

const resetIdleTimer = () => {
  if (idleTimer) clearTimeout(idleTimer);
  if (messageTimer) clearTimeout(messageTimer);
  idleMessage.value = '';
};

onMounted(() => {
  updateViewport();
  position.value = {
    x: viewport.value.width - PANEL.width - EDGE_GAP,
    y: viewport.value.height - PANEL.height - EDGE_GAP,
  };
  ensureInBounds();
  
  // Initial position set
  if (wrapperRef.value) {
    wrapperRef.value.style.transform = `translate(${position.value.x}px, ${position.value.y}px)`;
  }

  window.addEventListener('resize', updateViewport);
  
  startIdleTimer();
  nextTick(initParticles);

  // Start dripping effect
  dripInterval = setInterval(() => {
    if (!expanded.value && Math.random() > 0.5) { // Lower probability
      const id = dropletIdCounter++;
      const size = Math.random() * 4 + 5; // 5-9px (Smaller)
      const relX = 32; // Fixed center
      const relY = 56; // Start at bottom edge
      
      // Determine current position (handle dragging state)
      let currentX = position.value.x;
      let currentY = position.value.y;

      if (isDragging.value) {
         currentX = dragTargetPos.x;
         currentY = dragTargetPos.y;
         // No need to sync position.value anymore as Vue binding is removed
      }
      
      // Trigger tension blob animation (localized bottom stretch)
      if (tensionRef.value) {
        tensionRef.value.animate([
          { opacity: 1, transform: 'translateX(-50%) translateY(0) scale(0.5)' }, // Start hidden inside bottom
          { opacity: 1, transform: 'translateX(-50%) translateY(6px) scale(0.9, 1.2)', offset: 0.4 }, // Push out bottom
          { opacity: 1, transform: 'translateX(-50%) translateY(2px) scale(0.6, 1.4)', offset: 0.6 }, // Stretch further
          { opacity: 0, transform: 'translateX(-50%) translateY(0) scale(1)', offset: 0.8 }, // Snap back & hide
          { opacity: 0, transform: 'translateX(-50%) translateY(0) scale(0.5)' } // Reset
        ], {
          duration: 600,
          easing: 'cubic-bezier(0.25, 0.46, 0.45, 0.94)'
        });
      }

      // Calculate absolute position
      // Use center point directly, handle centering via CSS transform
      const absoluteX = currentX + relX;
      const absoluteY = currentY + relY;
      
      // Calculate distance to bottom of screen
      const distToBottom = window.innerHeight - absoluteY;
      const fallDistance = Math.max(distToBottom, 20); 
      
      // Duration based on distance (gravity feel)
      // Faster fall for heavier feel
      const duration = 0.6 + (fallDistance / 1000); 

      droplets.value.push({ id, absoluteX, absoluteY, size, duration, fallDistance });

      // Cleanup droplet and spawn splashes
      setTimeout(() => {
        droplets.value = droplets.value.filter(d => d.id !== id);
        
        // Spawn splashes at impact point
        const impactY = absoluteY + fallDistance;
        const splashCount = Math.floor(Math.random() * 3) + 4; // 4-6 splashes
        
        for (let i = 0; i < splashCount; i++) {
          const sId = splashIdCounter++;
          const angle = (Math.random() * Math.PI) + Math.PI; // Upwards arc (180-360 deg) - actually CSS coords are different
          // We want them to fly UP (-y) and OUT (+/- x)
          // Random x velocity: -30 to 30
          const vx = (Math.random() - 0.5) * 60;
          // Random y velocity: -20 to -50 (Up)
          const vy = -20 - Math.random() * 30;
          
          splashes.value.push({
            id: sId,
            x: absoluteX + size/2, // Center of drop
            y: impactY,
            size: Math.random() * 2 + 1, // 2-5px
            tx: vx,
            ty: vy
          });
          
          // Cleanup splash
          setTimeout(() => {
            splashes.value = splashes.value.filter(s => s.id !== sId);
          }, 800);
        }
      }, duration * 1000); // Sync with impact time
    }
  }, 1500); // Slower frequency (was 800)
});


onUnmounted(() => {
  window.removeEventListener('resize', updateViewport);
  stopDrag();
  if (animationFrame) cancelAnimationFrame(animationFrame);
  if (dripInterval) clearInterval(dripInterval);
  resetIdleTimer();
});

watch(viewport, ensureInBounds);
watch(currentSize, ensureInBounds);

// Manual sync for position changes (when not dragging)
watch(position, (newPos) => {
  if (!isDragging.value && wrapperRef.value) {
    wrapperRef.value.style.transform = `translate(${newPos.x}px, ${newPos.y}px)`;
  }
}, { deep: true });
</script>

<style>
@keyframes drop-fall-y {
  0% { transform: translateX(-50%) translateY(0) scale(1); }
  100% { transform: translateX(-50%) translateY(var(--fall-dist)) scale(0.8, 4.2); }
}

@keyframes drop-shape {
  0% {
    opacity: 1;
    transform: rotate(45deg) scale(0.5);
    border-radius: 50%;
  }
  10% {
    opacity: 1;
    transform: rotate(45deg) scale(1);
    border-radius: 0 50% 50% 50%;
  }
  80% {
    opacity: 1;
    transform: rotate(45deg) scale(1);
    border-radius: 0 50% 50% 50%;
  }
  100% {
    opacity: 0;
    transform: rotate(0deg) scale(2, 0.1);
    border-radius: 50%;
  }
}

@keyframes splash-fly {
  0% {
    opacity: 1;
    transform: translate(-50%, 0) scale(1);
    animation-timing-function: cubic-bezier(0.215, 0.610, 0.355, 1.000); /* Ease out (decelerate going up) */
  }
  45% {
    /* Peak of arc */
    opacity: 0.9;
    transform: translate(calc(-50% + var(--tx)), var(--ty)) scale(0.9);
    animation-timing-function: cubic-bezier(0.550, 0.055, 0.675, 0.190); /* Ease in (accelerate going down) */
  }
  100% {
    /* Fall down */
    opacity: 0;
    transform: translate(calc(-50% + var(--tx) * 1.3), 120px) scale(0.5);
  }
}

@keyframes drip-recoil {
  0% { transform: translateY(0); }
  30% { transform: translateY(4px) scale(0.95, 1.05); } /* Pulled down */
  60% { transform: translateY(-2px) scale(1.02, 0.98); } /* Snap back */
  100% { transform: translateY(0); }
}
</style>

<style scoped>
@keyframes liquid-morph {
  0% { border-radius: 60% 40% 30% 70% / 60% 30% 70% 40%; }
  50% { border-radius: 30% 60% 70% 40% / 50% 60% 30% 60%; }
  100% { border-radius: 60% 40% 30% 70% / 60% 30% 70% 40%; }
}

.animate-liquid-morph {
  animation: liquid-morph 8s ease-in-out infinite;
}

.animate-drip-stretch {
  animation: drip-stretch 0.6s cubic-bezier(0.25, 0.46, 0.45, 0.94);
}

@keyframes drip-stretch {
  0% { border-radius: 50%; transform: translateY(0); }
  30% { border-radius: 50% 50% 50% 50% / 45% 45% 65% 65%; transform: translateY(4px); } /* Stretch bottom down */
  60% { border-radius: 50% 50% 45% 45% / 55% 55% 45% 45%; transform: translateY(-2px); } /* Snap back up */
  100% { border-radius: 50%; transform: translateY(0); }
}

.animate-drip-recoil {
  animation: drip-recoil 0.4s cubic-bezier(0.175, 0.885, 0.32, 1.275);
}

.fade-slide-enter-active,
.fade-slide-leave-active {
  transition: all 0.3s ease;
}

.fade-slide-enter-from,
.fade-slide-leave-to {
  opacity: 0;
  transform: translate(10px, -50%);
}
</style>

