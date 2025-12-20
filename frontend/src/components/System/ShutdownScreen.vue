<template>
  <div 
    class="fixed inset-0 z-[99999] bg-black flex flex-col items-center justify-center overflow-hidden"
  >
    <canvas ref="canvasRef" class="absolute inset-0 w-full h-full"></canvas>
    
    <div class="z-10 relative flex flex-col items-center justify-center h-full pointer-events-none">
      <div class="relative flex flex-col items-center transition-opacity duration-500" :class="{ 'opacity-0': isTextFaded }">
        <!-- Glowing Text -->
        <h1 
          class="text-4xl md:text-6xl font-black tracking-widest text-transparent bg-clip-text bg-gradient-to-b from-white via-red-100 to-red-900"
          style="text-shadow: 0 0 30px rgba(239, 68, 68, 0.6);"
        >
          正在关机
        </h1>
        
        <!-- Subtitle -->
        <div class="mt-4">
          <div class="h-[1px] w-24 bg-gradient-to-r from-transparent via-red-500 to-transparent mb-2"></div>
          <span class="text-red-400/80 text-xs font-mono tracking-[0.5em] uppercase animate-pulse block text-center">System Halting</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue';

const emit = defineEmits(['finish']);

const canvasRef = ref(null);
const isTextFaded = ref(false);

// Animation Config
const PARTICLE_COUNT = 800;
const ANIMATION_DURATION = 1500; // Adjusted for real-time consistency (was 3000 frame-based)

let ctx = null;
let width = 0;
let height = 0;
let animationFrameId = null;
let particles = [];
let startTime = 0;

class Particle {
  constructor() {
    this.reset();
  }

  reset() {
    // Start from random positions on screen
    this.x = Math.random() * width;
    this.y = Math.random() * height;
    
    // Target is center
    const centerX = width / 2;
    const centerY = height / 2;
    
    // Calculate angle and distance to center
    const dx = this.x - centerX;
    const dy = this.y - centerY;
    this.angle = Math.atan2(dy, dx);
    this.radius = Math.sqrt(dx * dx + dy * dy);
    
    this.size = Math.random() * 2 + 0.5;
    this.speed = Math.random() * 0.05 + 0.02; // Speed of suction
    this.color = Math.random() > 0.6 ? '#f87171' : (Math.random() > 0.5 ? '#ffffff' : '#ef4444'); // Red theme
    this.alpha = Math.random() * 0.5 + 0.5;
  }

  update(progress) {
    // Spiral into center
    // As progress goes 0 -> 1, radius goes current -> 0
    
    // Accelerate suction
    const suction = Math.pow(progress, 3) * 50; 
    
    this.radius -= (this.speed * 100) + suction;
    this.angle += 0.1; // Spin while sucking
    
    if (this.radius < 0) this.radius = 0;

    this.x = width / 2 + Math.cos(this.angle) * this.radius;
    this.y = height / 2 + Math.sin(this.angle) * this.radius;
    
    // Fade out near end
    if (progress > 0.8) {
      this.alpha *= 0.9;
    }
  }

  draw() {
    if (this.radius <= 0.1) return;
    
    ctx.beginPath();
    ctx.arc(this.x, this.y, this.size, 0, Math.PI * 2);
    ctx.fillStyle = this.color;
    ctx.globalAlpha = Math.max(0, this.alpha);
    ctx.fill();
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
  
  // Fade text halfway through
  setTimeout(() => {
    isTextFaded.value = true;
  }, ANIMATION_DURATION * 0.3);

  setTimeout(() => {
    emit('finish');
  }, ANIMATION_DURATION);
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
  
  // Trail effect (stronger trail for suction look)
  ctx.fillStyle = 'rgba(0, 0, 0, 0.2)';
  ctx.fillRect(0, 0, width, height);
  
  const now = performance.now();
  const elapsed = now - startTime;
  const progress = Math.min(1, elapsed / ANIMATION_DURATION);
  
  particles.forEach(p => {
    p.update(progress);
    p.draw();
  });
  
  if (progress < 1) {
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
