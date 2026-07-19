<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import { authStore } from './stores/auth'
import BrandMark from './components/BrandMark.vue'

const route = useRoute()
const router = useRouter()

const isLoginPage = computed(() => route.name === 'login')

async function onLogout() {
  await authStore.logout()
  router.replace({ name: 'login' })
}
</script>

<template>
  <div v-if="isLoginPage" class="min-h-screen bg-surface-alt">
    <RouterView />
  </div>

  <div v-else class="min-h-screen bg-surface">
    <header class="border-b border-line">
      <div class="max-w-content mx-auto px-8 py-5 flex items-center justify-between">
        <RouterLink to="/" class="flex items-center gap-2">
          <BrandMark :size="28" />
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
          <button
            type="button"
            @click="onLogout"
            class="text-muted hover:text-ink transition-colors"
            title="Log out"
          >
            Log out
          </button>
        </nav>
      </div>
    </header>

    <main class="max-w-content mx-auto px-8 py-12">
      <RouterView />
    </main>
  </div>
</template>
