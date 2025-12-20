<template>
  <div 
    class="fixed inset-0 z-[99999] bg-black flex flex-col items-center justify-center overflow-hidden transition-opacity duration-1000"
    :class="{ 'opacity-0 pointer-events-none': isFadingOut }"
  >
    <canvas ref="canvasRef" class="absolute inset-0 w-full h-full"></canvas>
    
    <div class="z-10 relative flex flex-col items-center justify-center h-full pointer-events-none">
      <div class="relative flex flex-col items-center">
        <!-- Glowing Text -->
        <h1 
          class="text-6xl md:text-8xl font-black tracking-widest text-transparent bg-clip-text bg-gradient-to-b from-white via-blue-100 to-blue-900 transition-all duration-[1000ms] transform"
          :class="[
            showText ? 'opacity-100 scale-100 blur-0 translate-y-0' : 'opacity-0 scale-110 blur-xl translate-y-10'
          ]"
          style="text-shadow: 0 0 50px rgba(59, 130, 246, 0.8);"
        >
          麦麦提OS
        </h1>
        
        <!-- Subtitle -->
        <div 
          class="mt-8 transition-all duration-1000 delay-500"
          :class="showText ? 'opacity-100 translate-y-0' : 'opacity-0 translate-y-4'"
        >
          <div class="h-[1px] w-32 bg-gradient-to-r from-transparent via-blue-500 to-transparent mb-2"></div>
          <span class="text-blue-400/80 text-xs font-mono tracking-[0.8em] uppercase animate-pulse block text-center">System Initialized</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue';

const emit = defineEmits(['finish']);

const canvasRef = ref(null);
const showText = ref(false);
const isFadingOut = ref(false);
const isPlaying = ref(true);

// Animation Config
const PARTICLE_COUNT = 800;
const ANIMATION_DURATION = 2500; // Adjusted for real-time consistency (was 6000 frame-based)
const TEXT_HOLD_DURATION = 1500; // Extra time to keep text visible

let ctx = null;
let width = 0;
let height = 0;
let animationFrameId = null;
let particles = [];
let startTime = 0;

class Particle {
  constructor() {
    this.reset(true);
  }

  reset(initial = false) {
    this.angle = Math.random() * Math.PI * 2;
    this.radius = Math.random() * (initial ? 1000 : 500) + 100; // Distance from center
    this.size = Math.random() * 2 + 0.5;
    this.speed = Math.random() * 0.02 + 0.005;
    this.color = Math.random() > 0.6 ? '#60a5fa' : (Math.random() > 0.5 ? '#ffffff' : '#3b82f6');
    this.alpha = 0;
    this.z = Math.random() * 2; // Depth
  }

  update(progress) {
    // Phase 1: Spiral In (0 - 0.5)
    if (progress < 0.5) {
      this.radius *= 0.992; // Slower shrinking for elegance
      this.angle += this.speed * 2; // Slower rotation
      this.alpha = Math.min(1, this.alpha + 0.05);
    }
    // Phase 2: Black Hole / Compression (0.5 - 0.6)
    else if (progress < 0.6) {
      this.radius *= 0.92; // Less aggressive compression
      this.angle += this.speed * 8;
      this.alpha = 1;
    }
    // Phase 3: Big Bang (0.6 - 1.0)
    else {
      if (!this.exploded) {
        const angle = Math.random() * Math.PI * 2;
        const speed = Math.random() * 20 + 5;
        this.vx = Math.cos(angle) * speed;
        this.vy = Math.sin(angle) * speed;
        this.exploded = true;
      }
      this.x += this.vx;
      this.y += this.vy;
      this.alpha -= 0.01;
      return; // Skip polar coord calc
    }

    // Polar to Cartesian
    if (!this.exploded) {
      this.x = width / 2 + Math.cos(this.angle) * this.radius;
      this.y = height / 2 + Math.sin(this.angle) * this.radius * 0.6; // Elliptical
    }
  }

  draw() {
    const now = performance.now();
    const elapsed = now - startTime;
    const scale = this.exploded ? 1 : (1 + Math.sin(elapsed * 0.001 + this.z) * 0.2);
    ctx.beginPath();
    ctx.arc(this.x, this.y, this.size * scale, 0, Math.PI * 2);
    ctx.fillStyle = this.color;
    ctx.globalAlpha = Math.max(0, this.alpha);
    ctx.shadowBlur = this.size * 5;
    ctx.shadowColor = this.color;
    ctx.fill();
    ctx.shadowBlur = 0;
    ctx.globalAlpha = 1;
  }
}

const init = () => {
  const canvas = canvasRef.value;
  ctx = canvas.getContext('2d');
  resize();
  
  for (let i = 0; i < PARTICLE_COUNT; i++) {
    particles.push(new Particle());
  }
  
  startTime = performance.now();
  loop();
  
  setTimeout(() => {
    showText.value = true;
  }, ANIMATION_DURATION * 0.55);

  setTimeout(() => {
    isFadingOut.value = true;
    setTimeout(() => {
      isPlaying.value = false;
      emit('finish');
    }, 1000);
  }, ANIMATION_DURATION + TEXT_HOLD_DURATION);
};

const resize = () => {
  if (!canvasRef.value) return;
  width = window.innerWidth;
  height = window.innerHeight;
  canvasRef.value.width = width;
  canvasRef.value.height = height;
};

const loop = () => {
  if (!ctx) return;
  
  // Trail effect
  ctx.fillStyle = 'rgba(0, 0, 0, 0.15)';
  ctx.fillRect(0, 0, width, height);
  
  const now = performance.now();
  const elapsed = now - startTime;
  const progress = Math.min(1, elapsed / ANIMATION_DURATION);
  
  particles.forEach(p => {
    p.update(progress);
    p.draw();
  });
  
  if (isPlaying.value) {
    animationFrameId = requestAnimationFrame(loop);
  }
};

onMounted(() => {
  window.addEventListener('resize', resize);
  init();
});

onUnmounted(() => {
  window.removeEventListener('resize', resize);
  cancelAnimationFrame(animationFrameId);
});
</script>
