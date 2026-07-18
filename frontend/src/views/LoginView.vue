<script setup lang="ts">
import { ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { authStore } from '../stores/auth'

const router = useRouter()
const route = useRoute()

const username = ref('')
const password = ref('')
const loading = ref(false)
const error = ref('')

async function onSubmit() {
  if (!username.value || !password.value) return
  loading.value = true
  error.value = ''
  try {
    await authStore.login(username.value, password.value)
    const redirect = (route.query.redirect as string) || '/'
    router.replace(redirect)
  } catch {
    error.value = 'Incorrect credentials. Please try again.'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="min-h-screen flex items-center justify-center px-4 bg-surface-alt relative overflow-hidden">
    <div class="pointer-events-none absolute -top-32 -left-32 w-96 h-96 rounded-full bg-accent/20 blur-3xl"></div>
    <div class="pointer-events-none absolute -bottom-32 -right-32 w-96 h-96 rounded-full bg-accent/10 blur-3xl"></div>

    <div class="relative w-full max-w-sm">
      <div class="flex flex-col items-center mb-8">
        <div class="w-12 h-12 rounded-2xl bg-accent flex items-center justify-center shadow-lg shadow-accent/30 mb-4">
          <span class="text-white text-xl font-semibold">T</span>
        </div>
        <h1 class="text-xl font-semibold tracking-tight text-ink">Toolize</h1>
        <p class="text-sm text-muted mt-1">MCP server administration</p>
      </div>

      <form
        @submit.prevent="onSubmit"
        class="bg-surface border border-line rounded-2xl shadow-xl shadow-ink/5 p-8 space-y-5"
      >
        <div class="space-y-1.5">
          <label for="username" class="text-sm font-medium text-ink">Username</label>
          <input
            id="username"
            v-model="username"
            type="text"
            autocomplete="username"
            autofocus
            class="w-full rounded-lg border border-line bg-surface px-3.5 py-2.5 text-sm text-ink placeholder:text-muted focus:outline-none focus:ring-2 focus:ring-accent focus:border-accent transition-colors"
            placeholder="admin"
          />
        </div>

        <div class="space-y-1.5">
          <label for="password" class="text-sm font-medium text-ink">Password</label>
          <input
            id="password"
            v-model="password"
            type="password"
            autocomplete="current-password"
            class="w-full rounded-lg border border-line bg-surface px-3.5 py-2.5 text-sm text-ink placeholder:text-muted focus:outline-none focus:ring-2 focus:ring-accent focus:border-accent transition-colors"
            placeholder="••••••••"
          />
        </div>

        <p v-if="error" class="text-sm text-red-600 bg-red-50 border border-red-100 rounded-lg px-3 py-2">
          {{ error }}
        </p>

        <button
          type="submit"
          :disabled="loading"
          class="w-full rounded-lg bg-ink text-white text-sm font-medium py-2.5 hover:bg-accent transition-colors disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center gap-2"
        >
          <svg
            v-if="loading"
            class="animate-spin h-4 w-4"
            viewBox="0 0 24 24"
            fill="none"
          >
            <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4" />
            <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v4a4 4 0 00-4 4H4z" />
          </svg>
          <span>{{ loading ? 'Signing in...' : 'Sign in' }}</span>
        </button>
      </form>

      <p class="text-center text-xs text-muted mt-6">
        Access restricted to MCP server administrators.
      </p>
    </div>
  </div>
</template>
