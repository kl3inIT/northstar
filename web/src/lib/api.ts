import createClient from 'openapi-fetch'
import type { paths } from './api.gen'

// Typed HTTP client generated from the api's OpenAPI contract (springdoc → openapi.json
// → `pnpm gen:api`). Requests and responses are checked against the backend contract;
// never hand-write these types — regenerate after changing the api.
export const api = createClient<paths>({ baseUrl: '/' })
