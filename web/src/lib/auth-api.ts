import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiFetch } from './http'

export interface AuthSession {
  authenticated: boolean
  username: string | null
}

interface LoginInput {
  username: string
  password: string
}

interface ProblemBody {
  detail?: string
  title?: string
}

async function problemMessage(response: Response): Promise<string> {
  try {
    const body = (await response.json()) as ProblemBody
    return body.detail ?? body.title ?? `Request failed: ${response.status}`
  } catch {
    return `Request failed: ${response.status}`
  }
}

export async function getSession(): Promise<AuthSession> {
  const response = await apiFetch('/api/auth/me')
  if (!response.ok) throw new Error(await problemMessage(response))
  return (await response.json()) as AuthSession
}

export async function login(input: LoginInput): Promise<AuthSession> {
  const response = await apiFetch('/api/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(input),
  })
  if (!response.ok) throw new Error(await problemMessage(response))
  return (await response.json()) as AuthSession
}

export async function logout(): Promise<void> {
  const response = await apiFetch('/api/auth/logout', { method: 'POST' })
  if (!response.ok) throw new Error(await problemMessage(response))
}

export function useAuthSession() {
  return useQuery({
    queryKey: ['auth', 'session'],
    queryFn: getSession,
    retry: false,
    meta: { silent: true },
  })
}

export function useLogin() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: login,
    onSuccess: (session) => queryClient.setQueryData(['auth', 'session'], session),
  })
}

export function useLogout() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: logout,
    onMutate: async () => {
      await queryClient.cancelQueries()
    },
    onSuccess: () => {
      queryClient.clear()
      queryClient.setQueryData(['auth', 'session'], { authenticated: false, username: null })
    },
  })
}
