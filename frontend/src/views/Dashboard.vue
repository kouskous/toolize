<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { api, type ApiProject } from '../services/api'
import ApiCard from '../components/ApiCard.vue'

const projects = ref<ApiProject[]>([])
const loading = ref(true)
const error = ref<string | null>(null)

onMounted(async () => {
  try {
    projects.value = await api.listProjects()
  } catch (e: any) {
    error.value = e.message
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div>
    <div class="mb-10">
      <h1 class="text-2xl font-semibold text-ink tracking-tight">Your APIs</h1>
      <p class="text-muted mt-2">
        Turn your APIs into AI tools. Import an OpenAPI specification and generate an MCP server instantly.
      </p>
    </div>

    <div v-if="loading" class="text-muted text-sm">Loading...</div>
    <div v-else-if="error" class="text-red-600 text-sm">{{ error }}</div>

    <div v-else-if="projects.length === 0" class="border border-dashed border-line rounded-xl py-20 text-center">
      <p class="text-ink font-medium">No APIs imported yet</p>
      <p class="text-muted text-sm mt-1">Import your first OpenAPI specification to generate MCP tools.</p>
      <RouterLink
        to="/import"
        class="inline-block mt-6 px-5 py-2.5 rounded-lg bg-ink text-white text-sm font-medium hover:bg-accent transition-colors"
      >
        + Add API
      </RouterLink>
    </div>

    <div v-else class="grid grid-cols-1 md:grid-cols-2 gap-4">
      <ApiCard v-for="project in projects" :key="project.id" :project="project" />
    </div>
  </div>

</template>
