<script setup lang="ts">
import { computed } from 'vue'
import type { ApiAuthConfig } from '../services/api'

const auth = defineModel<ApiAuthConfig>({ required: true })

const inputClass = 'w-full border border-line rounded-lg px-4 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-accent focus:border-accent'
const labelClass = 'block text-xs font-medium text-muted mb-1'

const extraHeaderEntries = computed(() => Object.entries(auth.value.extraHeaders ?? {}))

function addExtraHeader() {
  auth.value.extraHeaders = { ...(auth.value.extraHeaders ?? {}), '': '' }
}

function removeExtraHeader(name: string) {
  const headers = { ...(auth.value.extraHeaders ?? {}) }
  delete headers[name]
  auth.value.extraHeaders = headers
}

function renameExtraHeader(oldName: string, newName: string) {
  const headers = { ...(auth.value.extraHeaders ?? {}) }
  const value = headers[oldName]
  delete headers[oldName]
  headers[newName] = value
  auth.value.extraHeaders = headers
}

function setExtraHeaderValue(name: string, value: string) {
  auth.value.extraHeaders = { ...(auth.value.extraHeaders ?? {}), [name]: value }
}
</script>

<template>
  <div>
    <label class="block text-sm font-medium text-ink mb-2">Authentication</label>
    <select v-model="auth.type" :class="[inputClass, 'mb-4']">
      <option value="NONE">None</option>
      <option value="API_KEY">API Key</option>
      <option value="BEARER_TOKEN">Bearer Token</option>
      <option value="BASIC_AUTH">Basic Auth</option>
      <option value="OAUTH2_CLIENT_CREDENTIALS">OAuth2 (client credentials)</option>
    </select>

    <div v-if="auth.type === 'API_KEY'" class="space-y-4 mb-4">
      <div>
        <label :class="labelClass">Header or query parameter name</label>
        <input v-model="auth.apiKeyName" type="text" placeholder="X-API-Key" :class="inputClass" />
      </div>
      <div class="flex gap-2 text-sm">
        <button
          type="button"
          class="px-3 py-1.5 rounded-md"
          :class="(auth.apiKeyLocation ?? 'HEADER') === 'HEADER' ? 'bg-ink text-white' : 'bg-surface-alt text-muted'"
          @click="auth.apiKeyLocation = 'HEADER'"
        >
          Header
        </button>
        <button
          type="button"
          class="px-3 py-1.5 rounded-md"
          :class="auth.apiKeyLocation === 'QUERY' ? 'bg-ink text-white' : 'bg-surface-alt text-muted'"
          @click="auth.apiKeyLocation = 'QUERY'"
        >
          Query param
        </button>
      </div>
      <div>
        <label :class="labelClass">API key value</label>
        <input v-model="auth.apiKeyValue" type="password" placeholder="secret-key" :class="inputClass" />
      </div>
    </div>

    <div v-else-if="auth.type === 'BEARER_TOKEN'" class="mb-4">
      <label :class="labelClass">Bearer token</label>
      <input v-model="auth.bearerToken" type="password" placeholder="eyJhbGciOi..." :class="inputClass" />
    </div>

    <div v-else-if="auth.type === 'BASIC_AUTH'" class="space-y-4 mb-4">
      <div>
        <label :class="labelClass">Username</label>
        <input v-model="auth.basicUsername" type="text" :class="inputClass" />
      </div>
      <div>
        <label :class="labelClass">Password</label>
        <input v-model="auth.basicPassword" type="password" :class="inputClass" />
      </div>
    </div>

    <div v-else-if="auth.type === 'OAUTH2_CLIENT_CREDENTIALS'" class="space-y-4 mb-4">
      <p class="text-xs text-muted -mt-1">
        Toolize fetches and refreshes its own access token from the token endpoint using the
        "client_credentials" grant — no token to paste or renew by hand.
      </p>
      <div>
        <label :class="labelClass">Token URL</label>
        <input v-model="auth.oauth2TokenUrl" type="text" placeholder="https://auth.example.com/oauth/token" :class="inputClass" />
      </div>
      <div>
        <label :class="labelClass">Client ID</label>
        <input v-model="auth.oauth2ClientId" type="text" :class="inputClass" />
      </div>
      <div>
        <label :class="labelClass">Client secret</label>
        <input v-model="auth.oauth2ClientSecret" type="password" :class="inputClass" />
      </div>
      <div>
        <label :class="labelClass">Scope (optional)</label>
        <input v-model="auth.oauth2Scope" type="text" placeholder="read write" :class="inputClass" />
      </div>
    </div>

    <div class="mt-2">
      <div class="flex items-center justify-between mb-2">
        <label class="block text-sm font-medium text-ink">Extra headers (optional)</label>
        <button type="button" class="text-xs text-accent hover:underline" @click="addExtraHeader">+ Add header</button>
      </div>
      <p class="text-xs text-muted mb-3">
        Sent on every request alongside the authentication above — e.g. a tenant id some APIs require.
      </p>
      <div v-for="[headerName, headerValue] in extraHeaderEntries" :key="headerName" class="flex gap-2 mb-2">
        <input
          :value="headerName"
          type="text"
          placeholder="Header-Name"
          class="w-2/5 border border-line rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-accent focus:border-accent"
          @change="renameExtraHeader(headerName, ($event.target as HTMLInputElement).value)"
        />
        <input
          :value="headerValue"
          type="text"
          placeholder="value"
          class="flex-1 border border-line rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-accent focus:border-accent"
          @input="setExtraHeaderValue(headerName, ($event.target as HTMLInputElement).value)"
        />
        <button type="button" class="text-muted hover:text-red-600 px-2" @click="removeExtraHeader(headerName)">✕</button>
      </div>
    </div>
  </div>
</template>