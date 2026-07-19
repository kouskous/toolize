<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { api, type CustomizationView, type ToolDetail, type ToolStatsView } from '../services/api'

const props = defineProps<{ id: string; toolName: string }>()

const tool = ref<ToolDetail | null>(null)
const loading = ref(true)
const error = ref<string | null>(null)

const argValues = ref<Record<string, string>>({})
const executing = ref(false)
const execResult = ref<{ status: number; body: any } | null>(null)
const execError = ref<string | null>(null)

const customizationView = ref<CustomizationView | null>(null)
const descriptionDraft = ref('')
const parameterDraft = ref<Record<string, string>>({})
const bodyDraft = ref('')
const savingCustomization = ref(false)
const customizationSaved = ref(false)
const customizationError = ref<string | null>(null)

const stats = ref<ToolStatsView | null>(null)

function formatRelativeTime(iso?: string): string {
  if (!iso) return 'never'
  const seconds = Math.floor((Date.now() - new Date(iso).getTime()) / 1000)
  if (seconds < 5) return 'just now'
  if (seconds < 60) return `${seconds}s ago`
  const minutes = Math.floor(seconds / 60)
  if (minutes < 60) return `${minutes}m ago`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours}h ago`
  return `${Math.floor(hours / 24)}d ago`
}

async function refreshStats() {
  try {
    stats.value = await api.getToolStats(props.id, props.toolName)
  } catch {
    // usage stats are informational only - a failure here shouldn't break the page
  }
}

const properties = computed(() => {
  if (!tool.value?.inputSchema?.properties) return [] as Array<[string, any]>
  return Object.entries(tool.value.inputSchema.properties) as Array<[string, any]>
})

const requiredFields = computed<string[]>(() => tool.value?.inputSchema?.required ?? [])

function applyCustomizationDrafts(view: CustomizationView) {
  descriptionDraft.value = view.customization.description ?? ''
  parameterDraft.value = { ...(view.customization.parameterDescriptions ?? {}) }
  bodyDraft.value = view.customization.parameterDescriptions?.body ?? ''
}

onMounted(async () => {
  try {
    const [toolData, customization] = await Promise.all([
      api.getTool(props.id, props.toolName),
      api.getToolCustomization(props.id, props.toolName)
    ])
    tool.value = toolData
    customizationView.value = customization
    applyCustomizationDrafts(customization)
    await refreshStats()
  } catch (e: any) {
    error.value = e.message
  } finally {
    loading.value = false
  }
})

async function saveCustomization() {
  savingCustomization.value = true
  customizationSaved.value = false
  customizationError.value = null
  try {
    const parameterDescriptions: Record<string, string> = {}
    for (const [name, value] of Object.entries(parameterDraft.value)) {
      if (value.trim() !== '') parameterDescriptions[name] = value
    }
    if (bodyDraft.value.trim() !== '') parameterDescriptions.body = bodyDraft.value

    await api.updateToolCustomization(props.id, props.toolName, {
      description: descriptionDraft.value.trim() === '' ? undefined : descriptionDraft.value,
      parameterDescriptions
    })

    const [toolData, customization] = await Promise.all([
      api.getTool(props.id, props.toolName),
      api.getToolCustomization(props.id, props.toolName)
    ])
    tool.value = toolData
    customizationView.value = customization
    applyCustomizationDrafts(customization)
    customizationSaved.value = true
    setTimeout(() => (customizationSaved.value = false), 2000)
  } catch (e: any) {
    customizationError.value = e.message
  } finally {
    savingCustomization.value = false
  }
}

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
    refreshStats()
  }
}
</script>

<template>
  <div v-if="loading" class="text-muted text-sm">Loading...</div>
  <div v-else-if="error" class="text-red-600 text-sm">{{ error }}</div>

  <div v-else-if="tool" class="max-w-xl">
    <p class="text-sm text-muted mb-1">Tool</p>
    <h1 class="text-2xl font-semibold text-ink tracking-tight mb-1">{{ tool.name }}</h1>
    <p class="text-sm text-muted mb-1">{{ tool.method }} {{ tool.path }}</p>
    <p class="text-sm text-muted mb-10">{{ tool.description }}</p>

    <div v-if="customizationView" class="border border-line rounded-xl p-8 mb-8">
      <h2 class="text-sm font-semibold text-ink mb-1">Tool definition for LLMs</h2>
      <p class="text-xs text-muted mb-6">
        This is what an AI agent sees when it decides whether and how to call this tool. Leave a field
        blank to keep using what the OpenAPI spec says.
      </p>

      <div class="mb-5">
        <label class="block text-sm font-medium text-ink mb-2">Tool description</label>
        <textarea
          v-model="descriptionDraft"
          rows="3"
          :placeholder="customizationView.defaultDescription || 'What this tool does and when an agent should use it'"
          class="w-full border border-line rounded-lg px-4 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-accent focus:border-accent"
        />
      </div>

      <div v-if="customizationView.parameters.length > 0" class="mb-2">
        <label class="block text-sm font-medium text-ink mb-2">Parameter descriptions</label>
        <div v-for="param in customizationView.parameters" :key="param.name" class="mb-3">
          <label class="block text-xs font-medium text-muted mb-1">
            {{ param.name }} <span class="text-muted">({{ param.in }}{{ param.required ? ', required' : '' }})</span>
          </label>
          <input
            v-model="parameterDraft[param.name]"
            type="text"
            :placeholder="param.defaultDescription || `${param.in} parameter`"
            class="w-full border border-line rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-accent focus:border-accent"
          />
        </div>
      </div>

      <div v-if="customizationView.hasBody" class="mb-6">
        <label class="block text-xs font-medium text-muted mb-1">body</label>
        <input
          v-model="bodyDraft"
          type="text"
          :placeholder="customizationView.bodyDefaultDescription || 'Request body'"
          class="w-full border border-line rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-accent focus:border-accent"
        />
      </div>

      <button
        :disabled="savingCustomization"
        @click="saveCustomization"
        class="px-4 py-2 rounded-lg bg-ink text-white text-sm font-medium hover:bg-accent transition-colors disabled:opacity-50"
      >
        {{ savingCustomization ? 'Saving...' : 'Save tool definition' }}
      </button>
      <p v-if="customizationSaved" class="mt-3 text-sm text-green-700">✓ Tool definition updated</p>
      <p v-if="customizationError" class="mt-3 text-sm text-red-600">{{ customizationError }}</p>
    </div>

    <div v-if="stats" class="border border-line rounded-xl p-8 mb-8">
      <h2 class="text-sm font-semibold text-ink mb-1">Usage</h2>
      <p class="text-xs text-muted mb-6">
        How often this tool has actually been called, by an MCP client or from the form below.
      </p>

      <div v-if="stats.totalCalls === 0" class="text-sm text-muted">
        Not called yet.
      </div>

      <div v-else>
        <div class="grid grid-cols-2 sm:grid-cols-4 gap-4 mb-6">
          <div>
            <p class="text-xs text-muted mb-1">Total calls</p>
            <p class="text-lg font-semibold text-ink">{{ stats.totalCalls }}</p>
          </div>
          <div>
            <p class="text-xs text-muted mb-1">Error rate</p>
            <p class="text-lg font-semibold" :class="stats.errorRate > 0 ? 'text-red-600' : 'text-ink'">
              {{ Math.round(stats.errorRate * 100) }}%
            </p>
          </div>
          <div>
            <p class="text-xs text-muted mb-1">Avg latency</p>
            <p class="text-lg font-semibold text-ink">{{ Math.round(stats.avgLatencyMs ?? 0) }} ms</p>
          </div>
          <div>
            <p class="text-xs text-muted mb-1">Last called</p>
            <p class="text-lg font-semibold text-ink">{{ formatRelativeTime(stats.lastCalledAt) }}</p>
          </div>
        </div>

        <p class="text-xs font-medium text-muted uppercase tracking-wide mb-2">Recent calls</p>
        <div class="space-y-1">
          <div
            v-for="(call, idx) in [...stats.recentCalls].reverse()"
            :key="idx"
            class="flex items-center justify-between text-xs py-1.5 border-b border-line last:border-0"
          >
            <span :class="call.status < 400 ? 'text-green-700' : 'text-red-600'" class="font-mono font-medium">
              HTTP {{ call.status }}
            </span>
            <span class="text-muted">{{ call.latencyMs }} ms</span>
            <span class="text-muted">{{ formatRelativeTime(call.timestamp) }}</span>
          </div>
        </div>
      </div>
    </div>

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
