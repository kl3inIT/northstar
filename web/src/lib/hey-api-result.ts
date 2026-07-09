import './hey-api-config'

type ApiResult<T> = {
  data?: T
  error?: unknown
}

function toError(error: unknown, fallback: string): Error {
  if (error instanceof Error) return error
  if (typeof error === 'string') return new Error(error)
  if (error && typeof error === 'object') {
    const record = error as Record<string, unknown>
    const message = record.detail ?? record.title ?? record.message
    if (typeof message === 'string') return new Error(message)
  }
  return new Error(fallback)
}

export function dataOrThrow<T>(result: ApiResult<T>, fallback = 'API request failed'): T {
  if (result.error) throw toError(result.error, fallback)
  if (result.data === undefined) throw new Error(fallback)
  return result.data
}

export function voidOrThrow(result: { error?: unknown }, fallback = 'API request failed'): void {
  if (result.error) throw toError(result.error, fallback)
}
