<script setup lang="ts">
import type { ApiAuthConfig } from '../services/api'

const auth = defineModel<ApiAuthConfig>({ required: true })

const inputClass = 'w-full border border-line rounded-lg px-4 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-accent focus:border-accent'
const labelClass = 'block text-xs font-medium text-muted mb-1'
</script>

<template>
  <div>
    <label class="block text-sm font-medium text-ink mb-2">Authentication</label>
    <select v-model="auth.type" :class="[inputClass, 'mb-4']">
      <option value="NONE">None</option>
      <option value="API_KEY">API Key</option>
      <option value="BEARER_TOKEN">Bearer Token</option>
      <option value="BASIC_AUTH">Basic Auth</option>
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
  </div>
</template>