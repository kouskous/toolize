import { reactive, readonly } from 'vue'

interface AuthState {
  authenticated: boolean
  username: string | null
  checked: boolean
}

const state = reactive<AuthState>({
  authenticated: false,
  username: null,
  checked: false
})

async function checkStatus(): Promise<boolean> {
  try {
    const res = await fetch('/api/auth/status')
    const body = await res.json()
    state.authenticated = !!body.authenticated
    state.username = body.username ?? null
  } catch {
    state.authenticated = false
    state.username = null
  } finally {
    state.checked = true
  }
  return state.authenticated
}

async function login(username: string, password: string): Promise<void> {
  const res = await fetch('/api/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ username, password })
  })
  if (!res.ok) {
    throw new Error('Identifiants invalides')
  }
  state.authenticated = true
  state.username = username
  state.checked = true
}

async function logout(): Promise<void> {
  await fetch('/api/auth/logout', { method: 'POST' })
  state.authenticated = false
  state.username = null
}

export const authStore = {
  state: readonly(state),
  checkStatus,
  login,
  logout
}
