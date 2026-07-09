const CSRF_COOKIE = 'XSRF-TOKEN'
const CSRF_HEADER = 'X-XSRF-TOKEN'
const SAFE_METHODS = new Set(['GET', 'HEAD', 'OPTIONS', 'TRACE'])

function isSameOrigin(input: RequestInfo | URL): boolean {
  if (typeof window === 'undefined') return true
  const url = input instanceof Request ? input.url : input.toString()
  return new URL(url, window.location.origin).origin === window.location.origin
}

export function readCookie(name: string): string | null {
  if (typeof document === 'undefined') return null
  const prefix = `${name}=`
  const match = document.cookie.split('; ').find((part) => part.startsWith(prefix))
  return match ? decodeURIComponent(match.slice(prefix.length)) : null
}

async function ensureCsrfToken() {
  if (readCookie(CSRF_COOKIE)) return
  await fetch('/api/auth/csrf', { credentials: 'same-origin' })
}

function requestMethod(input: RequestInfo | URL, init?: RequestInit): string {
  if (init?.method) return init.method.toUpperCase()
  if (input instanceof Request) return input.method.toUpperCase()
  return 'GET'
}

function requestHeaders(input: RequestInfo | URL, init?: RequestInit): Headers {
  const headers = new Headers(input instanceof Request ? input.headers : undefined)
  if (init?.headers) {
    new Headers(init.headers).forEach((value, key) => headers.set(key, value))
  }
  return headers
}

/**
 * Same-origin API fetch: keep the session in an HttpOnly cookie and attach the
 * SPA CSRF token only for state-changing requests. Cross-origin fetches are
 * intentionally left untouched so credentials do not leak to arbitrary URLs.
 */
export async function apiFetch(input: RequestInfo | URL, init: RequestInit = {}): Promise<Response> {
  const method = requestMethod(input, init)
  const headers = requestHeaders(input, init)

  if (isSameOrigin(input) && !SAFE_METHODS.has(method)) {
    await ensureCsrfToken()
    const token = readCookie(CSRF_COOKIE)
    if (token && !headers.has(CSRF_HEADER)) headers.set(CSRF_HEADER, token)
  }

  const response = await fetch(input, {
    ...init,
    headers,
    credentials: init.credentials ?? 'same-origin',
  })

  if (response.status === 401 && isSameOrigin(input)) {
    window.dispatchEvent(new Event('northstar:unauthorized'))
  }

  return response
}
