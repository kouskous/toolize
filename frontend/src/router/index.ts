import { createRouter, createWebHistory } from 'vue-router'
import Dashboard from '../views/Dashboard.vue'
import ImportApi from '../views/ImportApi.vue'
import ApiDetails from '../views/ApiDetails.vue'
import ToolDetails from '../views/ToolDetails.vue'
import LoginView from '../views/LoginView.vue'
import SetupView from '../views/SetupView.vue'
import { authStore } from '../stores/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: LoginView, meta: { public: true } },
    { path: '/', name: 'dashboard', component: Dashboard },
    { path: '/import', name: 'import', component: ImportApi },
    { path: '/projects/:id', name: 'api-details', component: ApiDetails, props: true },
    { path: '/projects/:id/tools/:toolName', name: 'tool-details', component: ToolDetails, props: true },
    { path: '/setup/database', name: 'setup-database', component: SetupView }
  ]
})

router.beforeEach(async (to) => {
  if (!authStore.state.checked) {
    await authStore.checkStatus()
  }

  if (to.meta.public) {
    if (to.name === 'login' && authStore.state.authenticated) {
      return { path: '/' }
    }
    return true
  }

  if (!authStore.state.authenticated) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }

  return true
})

export default router
