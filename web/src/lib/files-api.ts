/**
 * The attachment vault (V16): files are uploaded once (sha256-deduplicated
 * server-side) and served immutably from /api/files/{id}.
 */

import { apiFetch } from './http'

export interface AttachmentMeta {
  id: string
  filename: string
  mimeType: string
  sizeBytes: number
}

export async function uploadFile(blob: Blob, filename: string): Promise<AttachmentMeta> {
  const form = new FormData()
  form.append('file', blob, filename)
  const res = await apiFetch('/api/files', { method: 'POST', body: form })
  if (!res.ok) throw new Error(`upload failed: ${res.status}`)
  return (await res.json()) as AttachmentMeta
}

export function fileUrl(id: string): string {
  return `/api/files/${id}`
}
