<template>
  <div class="splash" :class="{ 'splash-exit': exiting }">
    <div class="splash-content">

      <!-- Título: Ribeye, blanco, borde negro, neón -->
      <h1 class="title-pisoton" :class="{ visible: titleVisible }">
        Distriasociados SAS
      </h1>

      <!-- Subtítulo: blanco, efecto neón -->
      <p class="subtitle-neon" :class="{ visible: subtitleVisible }">
        Powered by JnSoftware
      </p>

      <!-- Barra de carga (3 segundos) -->
      <div class="progress-track">
        <div class="progress-fill" :class="{ running: barRunning }"></div>
      </div>

    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'

const emit = defineEmits(['done'])

const titleVisible    = ref(false)
const subtitleVisible = ref(false)
const barRunning      = ref(false)
const exiting         = ref(false)

onMounted(() => {
  // Barra empieza casi de inmediato
  setTimeout(() => { barRunning.value      = true }, 200)
  // Título con pisotón
  setTimeout(() => { titleVisible.value    = true }, 350)
  // Subtítulo con neón
  setTimeout(() => { subtitleVisible.value = true }, 1100)
  // Fade out al terminar la barra (~3.2 s)
  setTimeout(() => { exiting.value         = true }, 3200)
  // Avisa al padre que terminó
  setTimeout(() => emit('done'), 3700)
})
</script>

<style scoped>
/* ══════════════════════════
   FONDO
══════════════════════════ */
.splash {
  position: fixed;
  inset: 0;
  z-index: 9999;
  display: flex;
  align-items: center;
  justify-content: center;
  background: radial-gradient(ellipse at center,
    #C41212 0%,
    #8B0000 48%,
    #2D0000 100%
  );
  opacity: 1;
  transition: opacity 0.5s ease;
}

.splash-exit {
  opacity: 0;
  pointer-events: none;
}

/* ══════════════════════════
   CONTENEDOR CENTRAL
══════════════════════════ */
.splash-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1.2rem;
  text-align: center;
  padding: 2rem;
  width: 100%;
  max-width: 600px;
}

/* ══════════════════════════
   TÍTULO — Ribeye + pisotón
   blanco · borde negro · neón
══════════════════════════ */
.title-pisoton {
  font-family: 'Ribeye', cursive;
  font-size: clamp(2.4rem, 6vw, 4rem);
  font-weight: 400;          /* Ribeye sólo tiene regular */
  color: #ffffff;
  -webkit-text-stroke: 3px #000000;
  paint-order: stroke fill;
  letter-spacing: 2px;
  line-height: 1.15;
  opacity: 0;
  transform: scale(3) translateY(-12px);
  filter: blur(8px);
}

.title-pisoton.visible {
  animation:
    pisoton    0.65s cubic-bezier(0.23, 1, 0.32, 1) forwards,
    titleNeon  1.8s ease-in-out 0.65s infinite alternate;
}

/* pisotón: entra enorme y borroso, se estampa */
@keyframes pisoton {
  0%   { opacity: 0; transform: scale(2.8) translateY(-14px); filter: blur(8px); }
  40%  { opacity: 1; transform: scale(0.9)  translateY(5px);  filter: blur(0);   }
  62%  { transform: scale(1.06) translateY(-3px); }
  80%  { transform: scale(0.97) translateY(1px);  }
  100% { opacity: 1; transform: scale(1)    translateY(0);    filter: blur(0);   }
}

/* neón sobre el título (brillo rojo pulsante) */
@keyframes titleNeon {
  from {
    text-shadow:
      0 0 8px  #ffffff,
      0 0 18px #ffffff,
      0 0 35px #ff9999,
      0 0 60px #ff2020,
      0 0 100px #cc0000,
      0 0 140px #990000;
  }
  to {
    text-shadow:
      0 0 4px  #ffffff,
      0 0 10px #ffcccc,
      0 0 20px #ff6666,
      0 0 40px #dd1111,
      0 0 70px rgba(180,0,0,0.45);
  }
}

/* ══════════════════════════
   SUBTÍTULO — neón puro
   blanco · brillo pulsante
══════════════════════════ */
.subtitle-neon {
  font-family: 'Inter', 'Segoe UI', sans-serif;
  font-size: clamp(0.8rem, 2vw, 1rem);
  font-weight: 500;
  letter-spacing: 3px;
  text-transform: uppercase;
  color: #ffffff;
  opacity: 0;
  transform: translateY(8px);
}

.subtitle-neon.visible {
  animation:
    neonEntrada 0.4s ease forwards,
    neonGlow    1.6s ease-in-out 0.4s infinite alternate;
}

@keyframes neonEntrada {
  from { opacity: 0; transform: translateY(8px); }
  to   { opacity: 1; transform: translateY(0);   }
}

@keyframes neonGlow {
  from {
    text-shadow:
      0 0 4px  #ffffff,
      0 0 10px #ffffff,
      0 0 22px #ff9999,
      0 0 42px #ff2020,
      0 0 75px #cc0000,
      0 0 95px #990000;
  }
  to {
    text-shadow:
      0 0 2px  #ffffff,
      0 0 6px  #ffcccc,
      0 0 14px #ff5555,
      0 0 28px #cc0000,
      0 0 50px rgba(180,0,0,0.4);
  }
}

/* ══════════════════════════
   BARRA DE CARGA (3 segundos)
══════════════════════════ */
.progress-track {
  width: min(320px, 80vw);
  height: 5px;
  background: rgba(255,255,255,0.12);
  border-radius: 99px;
  overflow: hidden;
  margin-top: 0.8rem;
  box-shadow: 0 0 8px rgba(0,0,0,0.4);
}

.progress-fill {
  height: 100%;
  width: 0%;
  border-radius: 99px;
  background: linear-gradient(90deg, #ff6666 0%, #ffffff 100%);
  box-shadow:
    0 0 6px  #ff4444,
    0 0 14px #ff0000,
    0 0 24px rgba(200,0,0,0.6);
}

.progress-fill.running {
  animation: fillBar 3s linear forwards;
}

@keyframes fillBar {
  from { width: 0%; }
  to   { width: 100%; }
}
</style>
