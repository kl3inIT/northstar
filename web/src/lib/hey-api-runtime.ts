import type { CreateClientConfig } from './hey-api/client.gen'
import { apiFetch } from './http'

export const createClientConfig: CreateClientConfig = (config) => ({
  ...config,
  baseUrl: '/',
  credentials: 'same-origin',
  fetch: apiFetch,
})
