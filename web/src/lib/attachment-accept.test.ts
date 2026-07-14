import { describe, expect, it } from 'vitest'

import { fileMatchesAccept } from './attachment-accept'

describe('fileMatchesAccept', () => {
  const accept = 'image/*,application/pdf,.md,.java,.xlsx'

  it('accepts MIME wildcards, exact MIME types, and source extensions', () => {
    expect(fileMatchesAccept({ name: 'photo.png', type: 'image/png' }, accept)).toBe(true)
    expect(fileMatchesAccept({ name: 'paper.pdf', type: 'application/pdf' }, accept)).toBe(true)
    expect(fileMatchesAccept({ name: 'README.MD', type: '' }, accept)).toBe(true)
    expect(fileMatchesAccept({ name: 'Main.java', type: '' }, accept)).toBe(true)
  })

  it('rejects files outside the explicit composer allowlist', () => {
    expect(fileMatchesAccept({ name: 'archive.zip', type: 'application/zip' }, accept)).toBe(false)
    expect(fileMatchesAccept({ name: 'voice.mp3', type: 'audio/mpeg' }, accept)).toBe(false)
  })
})
