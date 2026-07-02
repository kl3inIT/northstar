import createClient from 'openapi-fetch'

// Typed HTTP client against the Spring api.
//
// After the api exposes OpenAPI and you run `pnpm gen:api`, a `src/lib/api.gen.d.ts`
// file appears with a `paths` type. Switch the two lines below to make every call
// type-checked against the backend contract:
//
//   import type { paths } from './api.gen'
//   export const api = createClient<paths>({ baseUrl: '/' })

export const api = createClient({ baseUrl: '/' })
