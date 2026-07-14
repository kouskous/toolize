import { createRouter, createWebHistory } from 'vue-router'
import Dashboard from '../views/Dashboard.vue'
import ImportApi from '../views/ImportApi.vue'
import ApiDetails from '../views/ApiDetails.vue'
import ToolDetails from '../views/ToolDetails.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'dashboard', component: Dashboard },
    { path: '/import', name: 'import', component: ImportApi },
    { path: '/projects/:id', name: 'api-details', component: ApiDetails, props: true },
    { path: '/projects/:id/tools/:toolName', name: 'tool-details', component: ToolDetails, props: true }
  ]
})

export default router
