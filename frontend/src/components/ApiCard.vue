<script setup lang="ts">
import { RouterLink } from 'vue-router'
import type { ApiProject } from '../services/api'

defineProps<{ project: ApiProject }>()
</script>

<template>
  <RouterLink
    :to="`/projects/${project.id}`"
    class="block border border-line rounded-xl p-6 hover:border-accent transition-colors bg-surface"
  >
    <div class="flex items-start justify-between">
      <div>
        <h3 class="text-base font-semibold text-ink">{{ project.name }}</h3>
        <p class="text-sm text-muted mt-1">{{ project.toolsCount }} MCP Tools</p>
      </div>
      <span
        class="text-xs font-medium px-2.5 py-1 rounded-full"
        :class="project.status === 'ACTIVE'
          ? 'bg-green-50 text-green-700'
          : 'bg-red-50 text-red-700'"
      >
        {{ project.status }}
      </span>
    </div>
    <p v-if="project.openApiUrl" class="text-xs text-muted mt-4 truncate">
      {{ project.openApiUrl }}
    </p>
  </RouterLink>

</template>
