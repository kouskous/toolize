<script setup lang="ts">
withDefaults(defineProps<{ size?: number }>(), { size: 28 })
</script>

<template>
  <div
    class="bg-accent flex items-center justify-center shrink-0"
    :style="{ width: `${size}px`, height: `${size}px`, borderRadius: `${Math.round(size * 0.22)}px` }"
  >
    <svg viewBox="0 0 100 100" :style="{ width: `${size * 0.58}px`, height: `${size * 0.58}px` }" fill="white">
      <polygon points="50,6 59,50 50,94 41,50" />
      <polygon points="6,50 50,59 94,50 50,41" />
    </svg>
  </div>
</template>
