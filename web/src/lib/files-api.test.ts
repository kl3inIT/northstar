import { beforeEach, describe, expect, it, vi } from 'vitest'

const { listAttachmentIndexStatus } = vi.hoisted(() => ({
  listAttachmentIndexStatus: vi.fn(),
}))

vi.mock('./hey-api', () => ({
  listAttachmentIndexStatus,
  uploadAttachment: vi.fn(),
}))

import {
  AttachmentPreparationError,
  waitForAttachmentPreparation,
} from './files-api'

describe('waitForAttachmentPreparation', () => {
  beforeEach(() => listAttachmentIndexStatus.mockReset())

  it('waits through processing and resolves only when every requested file is ready', async () => {
    listAttachmentIndexStatus
      .mockResolvedValueOnce({ data: [
        { attachmentId: 'one', status: 'PROCESSING' },
        { attachmentId: 'two', status: 'PENDING' },
      ] })
      .mockResolvedValueOnce({ data: [
        { attachmentId: 'one', status: 'READY' },
        { attachmentId: 'two', status: 'READY' },
      ] })
    const updates: string[][] = []

    const result = await waitForAttachmentPreparation(['one', 'two'], {
      sleep: async () => undefined,
      onStatus: (statuses) => updates.push(statuses.map((status) => status.status ?? '')),
    })

    expect(result.every((status) => status.status === 'READY')).toBe(true)
    expect(updates).toEqual([['PROCESSING', 'PENDING'], ['READY', 'READY']])
    expect(listAttachmentIndexStatus).toHaveBeenCalledTimes(2)
  })

  it('fails closed with a safe message when the worker rejects a document', async () => {
    listAttachmentIndexStatus.mockResolvedValue({ data: [
      { attachmentId: 'one', status: 'FAILED', errorCode: 'ENCRYPTED_DOCUMENT' },
    ] })

    await expect(waitForAttachmentPreparation(['one'], { sleep: async () => undefined }))
      .rejects.toEqual(expect.objectContaining<Partial<AttachmentPreparationError>>({
        message: 'Password-protected documents are not supported.',
      }))
  })

  it('times out without losing control to an endless poll', async () => {
    listAttachmentIndexStatus.mockResolvedValue({ data: [
      { attachmentId: 'one', status: 'PROCESSING' },
    ] })

    await expect(waitForAttachmentPreparation(['one'], {
      maxAttempts: 2,
      sleep: async () => undefined,
    })).rejects.toThrow('taking longer than expected')
    expect(listAttachmentIndexStatus).toHaveBeenCalledTimes(2)
  })
})
