<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { api, defaultAuthConfig, type ApiAuthConfig, type ApiProject, type EndpointInfo, type ToolSummary } from '../services/api'
import ToolList from '../components/ToolList.vue'
import AuthConfigFields from '../components/AuthConfigFields.vue'
import EndpointSelector from '../components/EndpointSelector.vue'

const props = defineProps<{ id: string }>()
const router = useRouter()

const project = ref<ApiProject | null>(null)
const tools = ref<ToolSummary[]>([])
const endpoints = ref<EndpointInfo[]>([])
const selectedOperationIds = ref<string[]>([])
const loading = ref(true)
const error = ref<string | null>(null)
const deleting = ref(false)

const authDraft = ref<ApiAuthConfig>(defaultAuthConfig())
const savingAuth = ref(false)
const authSaved = ref(false)

const savingEndpoints = ref(false)
const endpointsSaved = ref(false)

const endpointsChanged = computed(() => {
  const enabledNow = new Set(endpoints.value.filter(e => e.enabled).map(e => e.operationId))
  const selected = new Set(selectedOperationIds.value)
  if (enabledNow.size !== selected.size) return true
  for (const id of enabledNow) if (!selected.has(id)) return true
  return false
})

onMounted(async () => {
  try {
    const [p, t, e] = await Promise.all([
      api.getProject(props.id),
      api.listTools(props.id),
      api.listEndpoints(props.id)
    ])
    project.value = p
    tools.value = t
    endpoints.value = e
    selectedOperationIds.value = e.filter(x => x.enabled).map(x => x.operationId)
    authDraft.value = { ...defaultAuthConfig(), ...p.auth }
  } catch (e: any) {
    error.value = e.message
  } finally {
    loading.value = false
  }
})

async function saveAuth() {
  savingAuth.value = true
  authSaved.value = false
  error.value = null
  try {
    const updated = await api.updateAuth(props.id, authDraft.value)
    if (project.value) project.value.auth = updated.auth
    authSaved.value = true
    setTimeout(() => (authSaved.value = false), 2000)
  } catch (e: any) {
    error.value = e.message
  } finally {
    savingAuth.value = false
  }
}

async function saveEndpoints() {
  savingEndpoints.value = true
  endpointsSaved.value = false
  error.value = null
  try {
    const updated = await api.updateEndpoints(props.id, selectedOperationIds.value)
    project.value = updated
    const [t, e] = await Promise.all([api.listTools(props.id), api.listEndpoints(props.id)])
    tools.value = t
    endpoints.value = e
    endpointsSaved.value = true
    setTimeout(() => (endpointsSaved.value = false), 2000)
  } catch (e: any) {
    error.value = e.message
  } finally {
    savingEndpoints.value = false
  }
}

async function remove() {
  if (!confirm('Delete this API and all its generated tools?')) return
  deleting.value = true
  try {
    await api.deleteProject(props.id)
    router.push('/')
  } catch (e: any) {
    error.value = e.message
    deleting.value = false
  }
}
</script>

<template>
  <div v-if="loading" class="text-muted text-sm">Loading...</div>
  <div v-else-if="error" class="text-red-600 text-sm">{{ error }}</div>

  <div v-else-if="project">
    <div class="flex items-start justify-between mb-2">
      <h1 class="text-2xl font-semibold text-ink tracking-tight">{{ project.name }}</h1>
      <button
        :disabled="deleting"
        @click="remove"
        class="text-sm text-red-600 hover:underline disabled:opacity-50"
      >
        Delete API
      </button>
    </div>
    <p v-if="project.openApiUrl" class="text-sm text-muted mb-1">Source: {{ project.openApiUrl }}</p>
    <p class="text-sm text-muted mb-10">{{ project.toolsCount }} generated tools &middot; {{ project.status }}</p>

    <h2 class="text-sm font-semibold text-muted uppercase tracking-wide mb-4">Authentication</h2>
    <div class="border border-line rounded-xl p-8 mb-10">
      <AuthConfigFields v-model="authDraft" />
      <button
        :disabled="savingAuth"
        @click="saveAuth"
        class="mt-2 px-4 py-2 rounded-lg bg-ink text-white text-sm font-medium hover:bg-accent transition-colors disabled:opacity-50"
      >
        {{ savingAuth ? 'Saving...' : 'Save authentication' }}
      </button>
      <p v-if="authSaved" class="mt-3 text-sm text-green-700">✓ Authentication updated</p>
    </div>

    <h2 class="text-sm font-semibold text-muted uppercase tracking-wide mb-4">Endpoints</h2>
    <div class="border border-line rounded-xl p-8 mb-10">
      <p class="text-sm text-muted mb-4">
        Choose which endpoints of this API are exposed as MCP tools. Unselected endpoints stay in the
        spec but are not registered on <code>/mcp</code>.
      </p>
      <EndpointSelector v-model="selectedOperationIds" :endpoints="endpoints" />
      <button
        :disabled="savingEndpoints || !endpointsChanged"
        @click="saveEndpoints"
        class="mt-4 px-4 py-2 rounded-lg bg-ink text-white text-sm font-medium hover:bg-accent transition-colors disabled:opacity-50"
      >
        {{ savingEndpoints ? 'Saving...' : 'Save endpoint selection' }}
      </button>
      <p v-if="endpointsSaved" class="mt-3 text-sm text-green-700">✓ Endpoint selection updated</p>
    </div>

    <h2 class="text-sm font-semibold text-muted uppercase tracking-wide mb-4">Generated tools</h2>
    <ToolList :project-id="project.id" :tools="tools" />
  </div>

</template>
