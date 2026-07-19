export type AuthType = 'NONE' | 'API_KEY' | 'BEARER_TOKEN' | 'BASIC_AUTH' | 'OAUTH2_CLIENT_CREDENTIALS'
export type ApiKeyLocation = 'HEADER' | 'QUERY'

export interface ApiAuthConfig {
  type: AuthType
  apiKeyName?: string
  apiKeyLocation?: ApiKeyLocation
  apiKeyValue?: string
  bearerToken?: string
  basicUsername?: string
  basicPassword?: string
  oauth2TokenUrl?: string
  oauth2ClientId?: string
  oauth2ClientSecret?: string
  oauth2Scope?: string
  extraHeaders?: Record<string, string>
}

export function defaultAuthConfig(): ApiAuthConfig {
  return { type: 'NONE', apiKeyLocation: 'HEADER', extraHeaders: {} }
}

export interface ApiProject {
  id: string
  name: string
  openApiUrl?: string
  baseUrl?: string
  toolsCount: number
  status: 'ACTIVE' | 'ERROR'
  errorMessage?: string
  importedAt?: string
  auth: ApiAuthConfig
}

export interface ToolSummary {
  name: string
  description: string
  method: string
  path: string
}

export interface ToolDetail extends ToolSummary {
  inputSchema: any
}

export interface ToolCustomization {
  description?: string
  parameterDescriptions?: Record<string, string>
}

export interface ParameterDefault {
  name: string
  in: string
  required: boolean
  type: string
  defaultDescription?: string
}

export interface CustomizationView {
  operationId: string
  toolName: string
  defaultDescription?: string
  parameters: ParameterDefault[]
  hasBody: boolean
  bodyDefaultDescription?: string
  customization: ToolCustomization
}

export interface EndpointSummary {
  operationId: string
  method: string
  path: string
  summary: string
}

export interface EndpointInfo extends EndpointSummary {
  enabled: boolean
}

export interface PreviewResponse {
  endpoints: EndpointSummary[]
  suggestedAuth: ApiAuthConfig | null
}

export interface CallRecord {
  timestamp: string
  status: number
  latencyMs: number
}

export interface ToolStatsView {
  totalCalls: number
  errorCalls: number
  errorRate: number
  lastCalledAt?: string
  lastStatus?: number
  avgLatencyMs?: number
  recentCalls: CallRecord[]
}

export interface ProjectStatsSummary {
  totalCalls: number
  errorCalls: number
  lastCalledAt?: string
}

async function handle<T>(res: Response): Promise<T> {
  if (res.status === 401) {
    if (!window.location.pathname.startsWith('/login')) {
      window.location.href = `/login?redirect=${encodeURIComponent(window.location.pathname)}`
    }
    throw new Error('Session expired')
  }
  if (!res.ok) {
    let message = `Request failed (${res.status})`
    try {
      const body = await res.json()
      if (body?.error) message = body.error
    } catch {
      // ignore
    }
    throw new Error(message)
  }
  return res.json() as Promise<T>
}

export const api = {
  listProjects(): Promise<ApiProject[]> {
    return fetch('/api/projects').then(res => handle<ApiProject[]>(res))
  },

  getProject(id: string): Promise<ApiProject> {
    return fetch(`/api/projects/${id}`).then(res => handle<ApiProject>(res))
  },

  previewFromUrl(openApiUrl: string): Promise<PreviewResponse> {
    return fetch('/api/projects/preview', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ openApiUrl })
    }).then(res => handle<PreviewResponse>(res))
  },

  previewFromFile(file: File): Promise<PreviewResponse> {
    const formData = new FormData()
    formData.append('file', file)
    return fetch('/api/projects/preview-file', {
      method: 'POST',
      body: formData
    }).then(res => handle<PreviewResponse>(res))
  },

  importFromUrl(name: string, openApiUrl: string, auth?: ApiAuthConfig, enabledOperationIds?: string[]): Promise<ApiProject> {
    return fetch('/api/projects/import', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name, openApiUrl, auth, enabledOperationIds })
    }).then(res => handle<ApiProject>(res))
  },

  importFromFile(name: string, file: File, auth?: ApiAuthConfig, enabledOperationIds?: string[]): Promise<ApiProject> {
    const formData = new FormData()
    formData.append('name', name)
    formData.append('file', file)
    if (enabledOperationIds) {
      for (const id of enabledOperationIds) formData.append('enabledOperationIds', id)
    }
    if (auth) {
      formData.append('authType', auth.type)
      if (auth.apiKeyName) formData.append('apiKeyName', auth.apiKeyName)
      if (auth.apiKeyLocation) formData.append('apiKeyLocation', auth.apiKeyLocation)
      if (auth.apiKeyValue) formData.append('apiKeyValue', auth.apiKeyValue)
      if (auth.bearerToken) formData.append('bearerToken', auth.bearerToken)
      if (auth.basicUsername) formData.append('basicUsername', auth.basicUsername)
      if (auth.basicPassword) formData.append('basicPassword', auth.basicPassword)
      if (auth.oauth2TokenUrl) formData.append('oauth2TokenUrl', auth.oauth2TokenUrl)
      if (auth.oauth2ClientId) formData.append('oauth2ClientId', auth.oauth2ClientId)
      if (auth.oauth2ClientSecret) formData.append('oauth2ClientSecret', auth.oauth2ClientSecret)
      if (auth.oauth2Scope) formData.append('oauth2Scope', auth.oauth2Scope)
      if (auth.extraHeaders && Object.keys(auth.extraHeaders).length > 0) {
        formData.append('extraHeadersJson', JSON.stringify(auth.extraHeaders))
      }
    }
    return fetch('/api/projects/import-file', {
      method: 'POST',
      body: formData
    }).then(res => handle<ApiProject>(res))
  },

  updateAuth(id: string, auth: ApiAuthConfig): Promise<ApiProject> {
    return fetch(`/api/projects/${id}/auth`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(auth)
    }).then(res => handle<ApiProject>(res))
  },

  deleteProject(id: string): Promise<void> {
    return fetch(`/api/projects/${id}`, { method: 'DELETE' }).then(res => {
      if (!res.ok) throw new Error(`Delete failed (${res.status})`)
    })
  },

  listTools(projectId: string): Promise<ToolSummary[]> {
    return fetch(`/api/projects/${projectId}/tools`).then(res => handle<ToolSummary[]>(res))
  },

  listEndpoints(projectId: string): Promise<EndpointInfo[]> {
    return fetch(`/api/projects/${projectId}/endpoints`).then(res => handle<EndpointInfo[]>(res))
  },

  updateEndpoints(projectId: string, enabledOperationIds: string[]): Promise<ApiProject> {
    return fetch(`/api/projects/${projectId}/endpoints`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ enabledOperationIds })
    }).then(res => handle<ApiProject>(res))
  },

  getTool(projectId: string, toolName: string): Promise<ToolDetail> {
    return fetch(`/api/projects/${projectId}/tools/${toolName}`).then(res => handle<ToolDetail>(res))
  },

  getToolCustomization(projectId: string, toolName: string): Promise<CustomizationView> {
    return fetch(`/api/projects/${projectId}/tools/${toolName}/customize`).then(res => handle<CustomizationView>(res))
  },

  updateToolCustomization(projectId: string, toolName: string, customization: ToolCustomization): Promise<void> {
    return fetch(`/api/projects/${projectId}/tools/${toolName}/customize`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(customization)
    }).then(res => {
      if (!res.ok) throw new Error(`Request failed (${res.status})`)
    })
  },

  executeTool(projectId: string, toolName: string, args: Record<string, any>): Promise<{ status: number; body: any }> {
    return fetch(`/api/projects/${projectId}/tools/${toolName}/execute`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ arguments: args })
    }).then(res => handle<{ status: number; body: any }>(res))
  },

  getToolStats(projectId: string, toolName: string): Promise<ToolStatsView> {
    return fetch(`/api/projects/${projectId}/tools/${toolName}/stats`).then(res => handle<ToolStatsView>(res))
  },

  getProjectStats(projectId: string): Promise<ProjectStatsSummary> {
    return fetch(`/api/projects/${projectId}/stats`).then(res => handle<ProjectStatsSummary>(res))
  }
}
