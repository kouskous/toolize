<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { api, type DatabaseType, type SetupStatus } from '../services/api'

const status = ref<SetupStatus | null>(null)
const loading = ref(true)
const loadError = ref<string | null>(null)

const type = ref<DatabaseType>('POSTGRESQL')
const host = ref('')
const port = ref(5432)
const database = ref('')
const username = ref('')
const password = ref('')

const DEFAULT_PORTS: Record<DatabaseType, number> = {
  POSTGRESQL: 5432,
  MYSQL: 3306,
  ORACLE: 1521
}

watch(type, (newType) => {
  port.value = DEFAULT_PORTS[newType]
})

const testing = ref(false)
const testResult = ref<{ success: boolean; message: string } | null>(null)

const saving = ref(false)
const saveResult = ref<{ success: boolean; message: string } | null>(null)
const restarting = ref(false)

onMounted(async () => {
  try {
    status.value = await api.getSetupStatus()
  } catch (e: any) {
    loadError.value = e.message
  } finally {
    loading.value = false
  }
})

function currentRequest() {
  return {
    type: type.value,
    host: host.value,
    port: port.value,
    database: database.value,
    username: username.value,
    password: password.value
  }
}

async function testConnection() {
  testing.value = true
  testResult.value = null
  try {
    testResult.value = await api.testDatasource(currentRequest())
  } catch (e: any) {
    testResult.value = { success: false, message: e.message }
  } finally {
    testing.value = false
  }
}

async function saveAndRestart() {
  saving.value = true
  saveResult.value = null
  try {
    const result = await api.saveDatasource(currentRequest())
    saveResult.value = result
    if (result.success) {
      restarting.value = true
    }
  } catch (e: any) {
    saveResult.value = { success: false, message: e.message }
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div v-if="loading" class="text-muted text-sm">Loading...</div>
  <div v-else-if="loadError" class="text-red-600 text-sm">{{ loadError }}</div>

  <div v-else class="max-w-xl">
    <h1 class="text-2xl font-semibold text-ink tracking-tight mb-2">Database</h1>
    <p class="text-muted mb-10">Where Toolize stores imported APIs, auth config, and tool customizations.</p>

    <div v-if="restarting" class="border border-line rounded-xl p-8 mb-8 bg-accent/5">
      <h2 class="text-sm font-semibold text-ink mb-2">Restarting...</h2>
      <p class="text-sm text-muted">
        Toolize is restarting to connect to your new database. This page (and the rest of the app) will
        be unavailable for a few seconds, then you can refresh.
      </p>
    </div>

    <template v-else>
      <div class="border border-line rounded-xl p-8 mb-8">
        <h2 class="text-sm font-semibold text-ink mb-1">Currently active</h2>
        <p class="text-sm text-muted" v-if="status?.activeType === 'H2'">
          <strong class="text-ink">Embedded database (H2)</strong> — great for trying Toolize out. Everything
          is stored in a single file and survives restarts, but it isn't meant for production traffic at scale.
        </p>
        <p class="text-sm text-muted" v-else>
          <strong class="text-ink">{{ status?.activeType }}</strong> — connected to an external database.
        </p>
      </div>

      <h2 class="text-sm font-semibold text-muted uppercase tracking-wide mb-4">Connect a production database</h2>
      <div class="border border-line rounded-xl p-8">
        <p class="text-sm text-muted mb-6">
          Point Toolize at your own PostgreSQL, MySQL, or Oracle instance. The connection is tested before
          anything is saved, and Toolize restarts once to apply it.
        </p>

        <div class="mb-5">
          <label class="block text-sm font-medium text-ink mb-2">Database type</label>
          <select v-model="type" class="w-full border border-line rounded-lg px-4 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-accent focus:border-accent">
            <option value="POSTGRESQL">PostgreSQL</option>
            <option value="MYSQL">MySQL</option>
            <option value="ORACLE">Oracle</option>
          </select>
        </div>

        <div class="grid grid-cols-3 gap-4 mb-5">
          <div class="col-span-2">
            <label class="block text-xs font-medium text-muted mb-1">Host</label>
            <input v-model="host" type="text" placeholder="db.example.com" class="w-full border border-line rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-accent focus:border-accent" />
          </div>
          <div>
            <label class="block text-xs font-medium text-muted mb-1">Port</label>
            <input v-model.number="port" type="number" class="w-full border border-line rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-accent focus:border-accent" />
          </div>
        </div>

        <div class="mb-5">
          <label class="block text-xs font-medium text-muted mb-1">Database name</label>
          <input v-model="database" type="text" placeholder="toolize" class="w-full border border-line rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-accent focus:border-accent" />
        </div>

        <div class="grid grid-cols-2 gap-4 mb-6">
          <div>
            <label class="block text-xs font-medium text-muted mb-1">Username</label>
            <input v-model="username" type="text" class="w-full border border-line rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-accent focus:border-accent" />
          </div>
          <div>
            <label class="block text-xs font-medium text-muted mb-1">Password</label>
            <input v-model="password" type="password" class="w-full border border-line rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-accent focus:border-accent" />
          </div>
        </div>

        <div class="flex gap-3">
          <button
            :disabled="testing || saving"
            @click="testConnection"
            class="px-4 py-2.5 rounded-lg border border-line text-sm font-medium text-ink hover:bg-surface-alt transition-colors disabled:opacity-50"
          >
            {{ testing ? 'Testing...' : 'Test connection' }}
          </button>
          <button
            :disabled="saving || testing"
            @click="saveAndRestart"
            class="flex-1 py-2.5 rounded-lg bg-ink text-white text-sm font-medium hover:bg-accent transition-colors disabled:opacity-50"
          >
            {{ saving ? 'Saving...' : 'Save & restart' }}
          </button>
        </div>

        <p v-if="testResult" class="mt-4 text-sm" :class="testResult.success ? 'text-green-700' : 'text-red-600'">
          {{ testResult.success ? '✓' : '✗' }} {{ testResult.message }}
        </p>
        <p v-if="saveResult && !saveResult.success" class="mt-4 text-sm text-red-600">{{ saveResult.message }}</p>
      </div>
    </template>
  </div>
</template>
