<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { api, type ToolDetail } from '../services/api'

const props = defineProps<{ id: string; toolName: string }>()

const tool = ref<ToolDetail | null>(null)
const loading = ref(true)
const error = ref<string | null>(null)

const argValues = ref<Record<string, string>>({})
const executing = ref(false)
const execResult = ref<{ status: number; body: any } | null>(null)
const execError = ref<string | null>(null)

const properties = computed(() => {
  if (!tool.value?.inputSchema?.properties) return [] as Array<[string, any]>
  return Object.entries(tool.value.inputSchema.properties) as Array<[string, any]>
})

const requiredFields = computed<string[]>(() => tool.value?.inputSchema?.required ?? [])

onMounted(async () => {
  try {
    tool.value = await api.getTool(props.id, props.toolName)
  } catch (e: any) {
    error.value = e.message
  } finally {
    loading.value = false
  }
})

function formatBodyForDisplay(body: any) {
  if (typeof body !== 'string') return JSON.stringify(body, null, 2)
  try {
    return JSON.stringify(JSON.parse(body), null, 2)
  } catch {
    return body
  }
}

async function run() {
  execError.value = null
  execResult.value = null
  executing.value = true
  try {
    const args: Record<string, any> = {}
    for (const [key, value] of Object.entries(argValues.value)) {
      if (value !== '') args[key] = value
    }
    execResult.value = await api.executeTool(props.id, props.toolName, args)
  } catch (e: any) {
    execError.value = e.message
  } finally {
    executing.value = false
  }
}
</script>

<template>
  <div v-if="loading" class="text-muted text-sm">Loading...</div>
  <div v-else-if="error" class="text-red-600 text-sm">{{ error }}</div>

  <div v-else-if="tool" class="max-w-xl">
    <p class="text-sm text-muted mb-1">Tool</p>
    <h1 class="text-2xl font-semibold text-ink tracking-tight mb-1">{{ tool.name }}</h1>
    <p class="text-sm text-muted mb-10">{{ tool.method }} {{ tool.path }}</p>

    <div class="border border-line rounded-xl p-8 mb-8">
      <h2 class="text-sm font-semibold text-ink mb-6">Parameters</h2>

      <div v-if="properties.length === 0" class="text-sm text-muted mb-2">
        This tool takes no parameters.
      </div>

      <div v-for="[key, schema] in properties" :key="key" class="mb-5">
        <label class="block text-sm font-medium text-ink mb-2">
          {{ key }}
          <span v-if="requiredFields.includes(key)" class="text-red-500">*</span>
        </label>
        <textarea
          v-if="key === 'body'"
          v-model="argValues[key]"
          rows="5"
          placeholder="{ }"
          class="w-full border border-line rounded-lg px-4 py-2.5 text-sm font-mono focus:outline-none focus:ring-2 focus:ring-accent focus:border-accent"
        />
        <input
          v-else
          v-model="argValues[key]"
          type="text"
          :placeholder="schema.type"
          class="w-full border border-line rounded-lg px-4 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-accent focus:border-accent"
        />
      </div>

      <button
        :disabled="executing"
        @click="run"
        class="mt-2 w-full py-2.5 rounded-lg bg-ink text-white text-sm font-medium hover:bg-accent transition-colors disabled:opacity-50"
      >
        {{ executing ? 'Executing...' : 'Execute' }}
      </button>
    </div>

    <div v-if="execError" class="text-sm text-red-600 mb-4">{{ execError }}</div>

    <div v-if="execResult" class="border border-line rounded-xl overflow-hidden">
      <div
        class="px-6 py-3 text-xs font-mono font-semibold border-b border-line"
        :class="execResult.status < 400 ? 'bg-green-50 text-green-700' : 'bg-red-50 text-red-700'"
      >
        HTTP {{ execResult.status }}
      </div>
      <pre class="px-6 py-4 text-xs font-mono overflow-x-auto bg-surface-alt">{{ formatBodyForDisplay(execResult.body) }}</pre>
    </div>
  </div>

</template>
