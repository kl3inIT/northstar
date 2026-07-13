import assert from 'node:assert/strict'
import test from 'node:test'
import {
  clampAssistantWidgetPosition,
  crossedAssistantWidgetDragThreshold,
  defaultAssistantWidgetPosition,
  parseAssistantWidgetPosition,
} from '../src/components/assistant-widget-position.ts'

const viewport = { width: 1_000, height: 800 }

test('assistant widget defaults to the existing bottom-right offset', () => {
  assert.deepEqual(defaultAssistantWidgetPosition(viewport), { x: 936, y: 676 })
})

test('assistant widget positions stay inside the visible viewport', () => {
  assert.deepEqual(clampAssistantWidgetPosition({ x: -100, y: 900 }, viewport), { x: 8, y: 748 })
  assert.deepEqual(parseAssistantWidgetPosition('{"x":9999,"y":-10}', viewport), { x: 948, y: 8 })
  assert.equal(parseAssistantWidgetPosition('{"x":"nope","y":20}', viewport), null)
  assert.equal(parseAssistantWidgetPosition('broken json', viewport), null)
})

test('small pointer movement remains a click while a real move starts dragging', () => {
  assert.equal(crossedAssistantWidgetDragThreshold(3, 3), false)
  assert.equal(crossedAssistantWidgetDragThreshold(4, 3), true)
})
