/**
 * The attachment vault (V16): files are uploaded once (sha256-deduplicated
 * server-side) and served immutably from /api/files/{id}.
 */

import { uploadAttachment } from './hey-api'
import { dataOrThrow } from './hey-api-result'

export interface AttachmentMeta {
  id: string
  filename: string
  mimeType: string
  sizeBytes: number
}

export async function uploadFile(blob: Blob, filename: string): Promise<AttachmentMeta> {
  const file = blob instanceof File ? blob : new File([blob], filename, { type: blob.type })
  return dataOrThrow(await uploadAttachment({ body: { file } }), 'upload failed') as AttachmentMeta
}

export function fileUrl(id: string): string {
  return `/api/files/${id}`
}
