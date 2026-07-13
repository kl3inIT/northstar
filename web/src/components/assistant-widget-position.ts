export interface AssistantWidgetPosition {
  x: number
  y: number
}

export interface AssistantWidgetViewport {
  width: number
  height: number
}

export const ASSISTANT_WIDGET_SIZE = 44
export const ASSISTANT_WIDGET_MARGIN = 8
export const ASSISTANT_WIDGET_STORAGE_KEY = 'northstar.assistant-widget.position.v1'

const DEFAULT_RIGHT = 20
const DEFAULT_BOTTOM = 80

export function clampAssistantWidgetPosition(
  position: AssistantWidgetPosition,
  viewport: AssistantWidgetViewport,
): AssistantWidgetPosition {
  const maxX = Math.max(ASSISTANT_WIDGET_MARGIN, viewport.width - ASSISTANT_WIDGET_SIZE - ASSISTANT_WIDGET_MARGIN)
  const maxY = Math.max(ASSISTANT_WIDGET_MARGIN, viewport.height - ASSISTANT_WIDGET_SIZE - ASSISTANT_WIDGET_MARGIN)
  return {
    x: Math.min(maxX, Math.max(ASSISTANT_WIDGET_MARGIN, position.x)),
    y: Math.min(maxY, Math.max(ASSISTANT_WIDGET_MARGIN, position.y)),
  }
}

export function defaultAssistantWidgetPosition(viewport: AssistantWidgetViewport): AssistantWidgetPosition {
  return clampAssistantWidgetPosition({
    x: viewport.width - ASSISTANT_WIDGET_SIZE - DEFAULT_RIGHT,
    y: viewport.height - ASSISTANT_WIDGET_SIZE - DEFAULT_BOTTOM,
  }, viewport)
}

export function parseAssistantWidgetPosition(
  serialized: string | null,
  viewport: AssistantWidgetViewport,
): AssistantWidgetPosition | null {
  if (!serialized) return null
  try {
    const parsed: unknown = JSON.parse(serialized)
    if (!parsed || typeof parsed !== 'object') return null
    const value = parsed as Partial<AssistantWidgetPosition>
    if (typeof value.x !== 'number' || typeof value.y !== 'number'
      || !Number.isFinite(value.x) || !Number.isFinite(value.y)) return null
    return clampAssistantWidgetPosition({ x: value.x, y: value.y }, viewport)
  } catch {
    return null
  }
}
