import { client } from './hey-api/client.gen'
import { apiFetch } from './http'

client.setConfig({
  baseUrl: '/',
  credentials: 'same-origin',
  fetch: apiFetch,
})
