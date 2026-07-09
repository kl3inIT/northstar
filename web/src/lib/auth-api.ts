import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  getAuthSession,
  login as loginRequest,
  logout as logoutRequest,
  type AuthSession as ApiAuthSession,
  type LoginRequest,
} from './hey-api'
import { dataOrThrow, voidOrThrow } from './hey-api-result'

export interface AuthSession {
  authenticated: boolean
  username: string | null
}

type LoginInput = LoginRequest

function normalizeSession(session: ApiAuthSession): AuthSession {
  return {
    authenticated: session.authenticated ?? false,
    username: session.username ?? null,
  }
}

export async function getSession(): Promise<AuthSession> {
  return normalizeSession(dataOrThrow(await getAuthSession()))
}

export async function login(input: LoginInput): Promise<AuthSession> {
  return normalizeSession(dataOrThrow(await loginRequest({ body: input })))
}

export async function logout(): Promise<void> {
  voidOrThrow(await logoutRequest())
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
