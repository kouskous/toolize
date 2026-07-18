<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { api, defaultAuthConfig, type EndpointSummary } from '../services/api'
import AuthConfigFields from '../components/AuthConfigFields.vue'
import EndpointSelector from '../components/EndpointSelector.vue'

const router = useRouter()

const step = ref<'form' | 'endpoints'>('form')

const name = ref('')
const openApiUrl = ref('')
const file = ref<File | null>(null)
const mode = ref<'url' | 'file'>('url')
const auth = ref(defaultAuthConfig())

const endpoints = ref<EndpointSummary[]>([])
const selectedOperationIds = ref<string[]>([])

const loadingEndpoints = ref(false)
const importing = ref(false)
const error = ref<string | null>(null)
const successMessage = ref<string | null>(null)

function onFileChange(e: Event) {
  const target = e.target as HTMLInputElement
  file.value = target.files?.[0] ?? null
}

async function loadEndpoints() {
  error.value = null

  if (!name.value.trim()) {
    error.value = 'API name is required'
    return
  }

  loadingEndpoints.value = true
  try {
    endpoints.value = mode.value === 'url'
      ? await api.previewFromUrl(openApiUrl.value)
      : await (async () => {
          if (!file.value) throw new Error('Please choose a YAML or JSON file')
          return api.previewFromFile(file.value)
        })()

    selectedOperationIds.value = endpoints.value.map(e => e.operationId)
    step.value = 'endpoints'
  } catch (e: any) {
    error.value = e.message
  } finally {
    loadingEndpoints.value = false
  }
}

function backToForm() {
  step.value = 'form'
  error.value = null
}

async function submit() {
  error.value = null
  successMessage.value = null

  importing.value = true
  try {
    const project = mode.value === 'url'
      ? await api.importFromUrl(name.value, openApiUrl.value, auth.value, selectedOperationIds.value)
      : await (async () => {
          if (!file.value) throw new Error('Please choose a YAML or JSON file')
          return api.importFromFile(name.value, file.value, auth.value, selectedOperationIds.value)
        })()

    successMessage.value = `${project.toolsCount} MCP tools generated`
    setTimeout(() => router.push(`/projects/${project.id}`), 900)
  } catch (e: any) {
    error.value = e.message
  } finally {
    importing.value = false
  }
}
</script>

<template>
  <div class="max-w-xl mx-auto">
    <h1 class="text-2xl font-semibold text-ink tracking-tight mb-2">Import your API</h1>
    <p class="text-muted mb-10">Generate MCP tools instantly from an OpenAPI specification.</p>

    <div v-if="step === 'form'" class="border border-line rounded-xl p-8">
      <div class="mb-6">
        <label class="block text-sm font-medium text-ink mb-2">API Name</label>
        <input
          v-model="name"
          type="text"
          placeholder="CRM API"
          class="w-full border border-line rounded-lg px-4 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-accent focus:border-accent"
        />
      </div>

      <div class="mb-6 flex gap-2 text-sm">
        <button
          class="px-3 py-1.5 rounded-md"
          :class="mode === 'url' ? 'bg-ink text-white' : 'bg-surface-alt text-muted'"
          @click="mode = 'url'"
        >
          OpenAPI URL
        </button>
        <button
          class="px-3 py-1.5 rounded-md"
          :class="mode === 'file' ? 'bg-ink text-white' : 'bg-surface-alt text-muted'"
          @click="mode = 'file'"
        >
          Upload YAML / JSON
        </button>
      </div>

      <div v-if="mode === 'url'" class="mb-8">
        <label class="block text-sm font-medium text-ink mb-2">OpenAPI URL</label>
        <input
          v-model="openApiUrl"
          type="text"
          placeholder="https://api.company.com/openapi.json"
          class="w-full border border-line rounded-lg px-4 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-accent focus:border-accent"
        />
      </div>

      <div v-else class="mb-8">
        <label class="block text-sm font-medium text-ink mb-2">Specification file</label>
        <input
          type="file"
          accept=".yaml,.yml,.json"
          @change="onFileChange"
          class="w-full text-sm text-muted file:mr-4 file:py-2 file:px-4 file:rounded-lg file:border-0 file:bg-surface-alt file:text-ink file:text-sm hover:file:bg-line"
        />
      </div>

      <div class="mb-8">
        <AuthConfigFields v-model="auth" />
      </div>

      <button
        :disabled="loadingEndpoints"
        @click="loadEndpoints"
        class="w-full py-2.5 rounded-lg bg-ink text-white text-sm font-medium hover:bg-accent transition-colors disabled:opacity-50"
      >
        {{ loadingEndpoints ? 'Reading specification...' : 'Next: choose endpoints' }}
      </button>

      <p v-if="error" class="mt-4 text-sm text-red-600">{{ error }}</p>
    </div>

    <div v-else class="border border-line rounded-xl p-8">
      <div class="mb-6">
        <h2 class="text-sm font-semibold text-ink mb-1">Choose the endpoints to expose</h2>
        <p class="text-xs text-muted">
          Only selected endpoints are generated as MCP tools. You can change this later from the API page.
        </p>
      </div>

      <EndpointSelector v-model="selectedOperationIds" :endpoints="endpoints" />

      <div class="flex gap-3 mt-8">
        <button
          type="button"
          :disabled="importing"
          class="px-4 py-2.5 rounded-lg border border-line text-sm font-medium text-ink hover:bg-surface-alt transition-colors disabled:opacity-50"
          @click="backToForm"
        >
          Back
        </button>
        <button
          :disabled="importing || selectedOperationIds.length === 0"
          @click="submit"
          class="flex-1 py-2.5 rounded-lg bg-ink text-white text-sm font-medium hover:bg-accent transition-colors disabled:opacity-50"
        >
          {{ importing ? 'Importing...' : `Import ${selectedOperationIds.length} endpoint${selectedOperationIds.length === 1 ? '' : 's'}` }}
        </button>
      </div>

      <p v-if="successMessage" class="mt-4 text-sm text-green-700">✓ API imported successfully — {{ successMessage }}</p>
      <p v-if="error" class="mt-4 text-sm text-red-600">{{ error }}</p>
    </div>
  </div>

</template>
