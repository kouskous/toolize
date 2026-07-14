<script setup lang="ts">
import { RouterLink } from 'vue-router'
import type { ToolSummary } from '../services/api'

defineProps<{ projectId: string; tools: ToolSummary[] }>()

const methodColors: Record<string, string> = {
  GET: 'bg-blue-50 text-blue-700',
  POST: 'bg-green-50 text-green-700',
  PUT: 'bg-amber-50 text-amber-700',
  PATCH: 'bg-amber-50 text-amber-700',
  DELETE: 'bg-red-50 text-red-700'
}
</script>

<template>
  <div class="divide-y divide-line border border-line rounded-xl overflow-hidden">
    <RouterLink
      v-for="tool in tools"
      :key="tool.name"
      :to="`/projects/${projectId}/tools/${tool.name}`"
      class="flex items-center justify-between px-6 py-4 hover:bg-surface-alt transition-colors"
    >
      <div class="flex items-center gap-3">
        <span
          class="text-xs font-mono font-semibold px-2 py-1 rounded-md w-16 text-center"
          :class="methodColors[tool.method] || 'bg-gray-50 text-gray-700'"
        >
          {{ tool.method }}
        </span>
        <div>
          <p class="text-sm font-medium text-ink">{{ tool.path }}</p>
          <p class="text-xs text-muted">{{ tool.name }}</p>
        </div>
      </div>
      <span class="text-muted text-sm">&rarr;</span>
    </RouterLink>
    <p v-if="tools.length === 0" class="px-6 py-8 text-sm text-muted text-center">
      No tools generated yet.
    </p>
  </div>

</template>
