<script setup lang="ts">
import { computed } from 'vue'

export interface SelectableEndpoint {
  operationId: string
  method: string
  path: string
  summary: string
}

const props = defineProps<{ endpoints: SelectableEndpoint[] }>()
const selected = defineModel<string[]>({ required: true })

const methodColors: Record<string, string> = {
  GET: 'bg-blue-50 text-blue-700',
  POST: 'bg-green-50 text-green-700',
  PUT: 'bg-amber-50 text-amber-700',
  PATCH: 'bg-amber-50 text-amber-700',
  DELETE: 'bg-red-50 text-red-700'
}

const selectedSet = computed(() => new Set(selected.value))
const allSelected = computed(() => props.endpoints.length > 0 && selected.value.length === props.endpoints.length)

function toggle(operationId: string) {
  if (selectedSet.value.has(operationId)) {
    selected.value = selected.value.filter(id => id !== operationId)
  } else {
    selected.value = [...selected.value, operationId]
  }
}

function selectAll() {
  selected.value = props.endpoints.map(e => e.operationId)
}

function selectNone() {
  selected.value = []
}
</script>

<template>
  <div>
    <div class="flex items-center justify-between mb-3">
      <p class="text-xs text-muted">
        {{ selected.length }} / {{ endpoints.length }} endpoints selected
      </p>
      <div class="flex gap-3 text-xs font-medium">
        <button type="button" class="text-accent hover:underline" :disabled="allSelected" @click="selectAll">
          Select all
        </button>
        <button type="button" class="text-accent hover:underline" :disabled="selected.length === 0" @click="selectNone">
          Select none
        </button>
      </div>
    </div>

    <div class="divide-y divide-line border border-line rounded-xl overflow-hidden max-h-80 overflow-y-auto">
      <label
        v-for="endpoint in endpoints"
        :key="endpoint.operationId"
        class="flex items-center gap-3 px-4 py-3 hover:bg-surface-alt transition-colors cursor-pointer"
      >
        <input
          type="checkbox"
          class="w-4 h-4 rounded border-line text-accent focus:ring-accent"
          :checked="selectedSet.has(endpoint.operationId)"
          @change="toggle(endpoint.operationId)"
        />
        <span
          class="text-xs font-mono font-semibold px-2 py-1 rounded-md w-16 text-center shrink-0"
          :class="methodColors[endpoint.method] || 'bg-gray-50 text-gray-700'"
        >
          {{ endpoint.method }}
        </span>
        <div class="min-w-0">
          <p class="text-sm font-medium text-ink truncate">{{ endpoint.path }}</p>
          <p class="text-xs text-muted truncate">{{ endpoint.summary || endpoint.operationId }}</p>
        </div>
      </label>

      <p v-if="endpoints.length === 0" class="px-6 py-8 text-sm text-muted text-center">
        No endpoints found in this specification.
      </p>
    </div>
  </div>
</template>
