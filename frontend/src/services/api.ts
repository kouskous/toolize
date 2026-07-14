export interface ApiProject {
  id: string
  name: string
  openApiUrl?: string
  baseUrl?: string
  toolsCount: number
  status: 'ACTIVE' | 'ERROR'
  errorMessage?: string
  importedAt?: string
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

async function handle<T>(res: Response): Promise<T> {
  if (res.status === 401) {
    if (!window.location.pathname.startsWith('/login')) {
      window.location.href = `/login?redirect=${encodeURIComponent(window.location.pathname)}`
    }
    throw new Error('Session expirée')
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

  importFromUrl(name: string, openApiUrl: string): Promise<ApiProject> {
    return fetch('/api/projects/import', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name, openApiUrl })
    }).then(res => handle<ApiProject>(res))
  },

  importFromFile(name: string, file: File): Promise<ApiProject> {
    const formData = new FormData()
    formData.append('name', name)
    formData.append('file', file)
    return fetch('/api/projects/import-file', {
      method: 'POST',
      body: formData
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

  getTool(projectId: string, toolName: string): Promise<ToolDetail> {
    return fetch(`/api/projects/${projectId}/tools/${toolName}`).then(res => handle<ToolDetail>(res))
  },

  executeTool(projectId: string, toolName: string, args: Record<string, any>): Promise<{ status: number; body: any }> {
    return fetch(`/api/projects/${projectId}/tools/${toolName}/execute`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ arguments: args })
    }).then(res => handle<{ status: number; body: any }>(res))
  }
}
