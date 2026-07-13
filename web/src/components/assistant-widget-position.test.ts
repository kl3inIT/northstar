import { describe, expect, test } from 'vitest'
import {
  clampAssistantWidgetPosition,
  defaultAssistantWidgetPosition,
  parseAssistantWidgetPosition,
} from './assistant-widget-position'

const viewport = { width: 1_000, height: 800 }

describe('assistant widget position', () => {
  test('defaults to the existing bottom-right offset', () => {
    expect(defaultAssistantWidgetPosition(viewport)).toEqual({ x: 936, y: 676 })
  })

  test('stays inside the visible viewport and rejects invalid storage', () => {
    expect(clampAssistantWidgetPosition({ x: -100, y: 900 }, viewport)).toEqual({ x: 8, y: 748 })
    expect(parseAssistantWidgetPosition('{"x":9999,"y":-10}', viewport)).toEqual({ x: 948, y: 8 })
    expect(parseAssistantWidgetPosition('{"x":"nope","y":20}', viewport)).toBeNull()
    expect(parseAssistantWidgetPosition('broken json', viewport)).toBeNull()
  })
})
