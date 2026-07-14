/**
 * The attachment vault (V16): files are uploaded once (sha256-deduplicated
 * server-side) and served immutably from /api/files/{id}.
 */

import type { FileUIPart } from 'ai'
import {
  listAttachmentIndexStatus,
  uploadAttachment,
  type AttachmentIndexView,
} from './hey-api'
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

export type AttachmentPreparationState = NonNullable<AttachmentIndexView['status']> | 'UPLOADING'

export interface UploadedAttachment<T extends FileUIPart = FileUIPart> {
  meta: AttachmentMeta
  part: T
}

/** Rewrites a local data/object URL into the durable Attachment-vault URL. */
export async function uploadAttachmentPart<T extends FileUIPart>(file: T): Promise<UploadedAttachment<T>> {
  const blob = await fetch(file.url).then((response) => {
    if (!response.ok) throw new Error('Could not read the selected file')
    return response.blob()
  })
  const meta = await uploadFile(blob, file.filename ?? 'attachment')
  return {
    meta,
    part: { ...file, mediaType: meta.mimeType, url: fileUrl(meta.id) },
  }
}

export class AttachmentPreparationError extends Error {
  readonly statuses: AttachmentIndexView[]

  constructor(statuses: AttachmentIndexView[]) {
    const failed = statuses.find((status) => status.status === 'FAILED' || status.status === 'UNSUPPORTED')
    super(failed?.errorCode === 'ENCRYPTED_DOCUMENT'
      ? 'Password-protected documents are not supported.'
      : failed?.status === 'UNSUPPORTED'
        ? 'This file has no supported text to read.'
        : 'Document preparation failed — remove it or try again.')
    this.name = 'AttachmentPreparationError'
    this.statuses = statuses
  }
}

interface WaitOptions {
  signal?: AbortSignal
  pollMs?: number
  maxAttempts?: number
  onStatus?: (statuses: AttachmentIndexView[]) => void
  sleep?: (milliseconds: number) => Promise<void>
}

/** Polls the worker-owned derived index; never parses a document in the browser/API. */
export async function waitForAttachmentPreparation(
  ids: string[],
  options: WaitOptions = {},
): Promise<AttachmentIndexView[]> {
  if (ids.length === 0) return []
  const {
    signal,
    pollMs = 1_000,
    maxAttempts = 90,
    onStatus,
    sleep = (milliseconds) => new Promise((resolve) => setTimeout(resolve, milliseconds)),
  } = options
  for (let attempt = 0; attempt < maxAttempts; attempt += 1) {
    if (signal?.aborted) throw new DOMException('Document preparation cancelled', 'AbortError')
    const statuses = dataOrThrow(
      await listAttachmentIndexStatus({ query: { ids }, signal }),
      'Could not check document preparation',
    ) as AttachmentIndexView[]
    onStatus?.(statuses)
    if (statuses.some((status) => status.status === 'FAILED' || status.status === 'UNSUPPORTED')) {
      throw new AttachmentPreparationError(statuses)
    }
    const ready = new Set(statuses.filter((status) => status.status === 'READY').map((status) => status.attachmentId))
    if (ids.every((id) => ready.has(id))) return statuses
    await sleep(pollMs)
  }
  throw new Error('Document preparation is taking longer than expected. Your draft is still here.')
}
