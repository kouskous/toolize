<script setup lang="ts">
import { RouterLink, RouterView } from 'vue-router'
</script>

<template>
  <div class="min-h-screen bg-surface">
    <header class="border-b border-line">
      <div class="max-w-content mx-auto px-8 py-5 flex items-center justify-between">
        <RouterLink to="/" class="flex items-center gap-2">
          <div class="w-7 h-7 rounded-lg bg-accent flex items-center justify-center">
            <span class="text-white text-sm font-semibold">T</span>
          </div>
          <span class="text-lg font-semibold tracking-tight text-ink">Toolize</span>
        </RouterLink>
        <nav class="flex items-center gap-6 text-sm text-muted">
          <RouterLink to="/" class="hover:text-ink transition-colors">Dashboard</RouterLink>
          <RouterLink
            to="/import"
            class="px-4 py-2 rounded-lg bg-ink text-white text-sm font-medium hover:bg-accent transition-colors"
          >
            + Add API
          </RouterLink>
        </nav>
      </div>
    </header>

    <main class="max-w-content mx-auto px-8 py-12">
      <RouterView />
    </main>
  </div>
</template>
