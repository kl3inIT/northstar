import { Link, useLocation } from '@tanstack/react-router'
import { Bot, Loader2, Maximize2, X } from 'lucide-react'
import { lazy, Suspense, useEffect, useRef, useState } from 'react'
import { Button } from '@/components/ui/button'
import {
  ASSISTANT_WIDGET_STORAGE_KEY,
  clampAssistantWidgetPosition,
  crossedAssistantWidgetDragThreshold,
  defaultAssistantWidgetPosition,
  parseAssistantWidgetPosition,
  type AssistantWidgetPosition,
} from '@/components/assistant-widget-position'
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from '@/components/ui/popover'

const AssistantWorkspace = lazy(() =>
  import('@/components/assistant-workspace').then((module) => ({
    default: module.AssistantWorkspace,
  })),
)

export function AssistantWidget() {
  const pathname = useLocation({ select: (location) => location.pathname })
  const [open, setOpen] = useState(false)
  const [position, setPosition] = useState<AssistantWidgetPosition | null>(null)
  const positionRef = useRef<AssistantWidgetPosition | null>(null)
  const suppressClickRef = useRef(false)
  const dragRef = useRef<{
    pointerId: number
    pointerX: number
    pointerY: number
    start: AssistantWidgetPosition
    moved: boolean
  } | null>(null)
  const available = pathname !== '/assistant' && pathname !== '/login'

  function viewport() {
    return { width: window.innerWidth, height: window.innerHeight }
  }

  function updatePosition(next: AssistantWidgetPosition, persist = false) {
    const clamped = clampAssistantWidgetPosition(next, viewport())
    positionRef.current = clamped
    setPosition(clamped)
    if (persist) localStorage.setItem(ASSISTANT_WIDGET_STORAGE_KEY, JSON.stringify(clamped))
  }

  useEffect(() => {
    const currentViewport = viewport()
    const initial = parseAssistantWidgetPosition(
      localStorage.getItem(ASSISTANT_WIDGET_STORAGE_KEY),
      currentViewport,
    ) ?? defaultAssistantWidgetPosition(currentViewport)
    positionRef.current = initial
    setPosition(initial)

    function keepInsideViewport() {
      if (positionRef.current) updatePosition(positionRef.current, true)
    }

    window.addEventListener('resize', keepInsideViewport)
    return () => window.removeEventListener('resize', keepInsideViewport)
    // Position setup is intentionally browser-only and runs once.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  useEffect(() => {
    if (!available) setOpen(false)
  }, [available])

  function startDrag(event: React.PointerEvent<HTMLButtonElement>) {
    if (event.button !== 0) return
    const rect = event.currentTarget.getBoundingClientRect()
    dragRef.current = {
      pointerId: event.pointerId,
      pointerX: event.clientX,
      pointerY: event.clientY,
      start: { x: rect.left, y: rect.top },
      moved: false,
    }
    event.currentTarget.setPointerCapture(event.pointerId)
  }

  function moveDrag(event: React.PointerEvent<HTMLButtonElement>) {
    const drag = dragRef.current
    if (!drag || drag.pointerId !== event.pointerId) return
    const deltaX = event.clientX - drag.pointerX
    const deltaY = event.clientY - drag.pointerY
    if (!drag.moved && !crossedAssistantWidgetDragThreshold(deltaX, deltaY)) return
    if (!drag.moved) {
      drag.moved = true
      setOpen(false)
    }
    event.preventDefault()
    updatePosition({ x: drag.start.x + deltaX, y: drag.start.y + deltaY })
  }

  function finishDrag(event: React.PointerEvent<HTMLButtonElement>, suppressClick = true) {
    const drag = dragRef.current
    if (!drag || drag.pointerId !== event.pointerId) return
    if (event.currentTarget.hasPointerCapture(event.pointerId)) {
      event.currentTarget.releasePointerCapture(event.pointerId)
    }
    dragRef.current = null
    if (!drag.moved) return
    suppressClickRef.current = suppressClick
    if (positionRef.current) updatePosition(positionRef.current, true)
  }

  function moveWithKeyboard(event: React.KeyboardEvent<HTMLButtonElement>) {
    if (!event.altKey) return
    const delta = 16
    const offsets: Partial<Record<string, AssistantWidgetPosition>> = {
      ArrowLeft: { x: -delta, y: 0 },
      ArrowRight: { x: delta, y: 0 },
      ArrowUp: { x: 0, y: -delta },
      ArrowDown: { x: 0, y: delta },
    }
    const offset = offsets[event.key]
    if (!offset) return
    event.preventDefault()
    const current = positionRef.current ?? defaultAssistantWidgetPosition(viewport())
    updatePosition({ x: current.x + offset.x, y: current.y + offset.y }, true)
  }

  useEffect(() => {
    if (!available) return

    function toggle(event: KeyboardEvent) {
      if (event.key.toLowerCase() !== 'j' || (!event.metaKey && !event.ctrlKey)) return
      event.preventDefault()
      setOpen((current) => !current)
    }

    document.addEventListener('keydown', toggle)
    return () => document.removeEventListener('keydown', toggle)
  }, [available])

  if (!available) return null

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button
          size="icon"
          className="fixed z-40 size-11 cursor-grab rounded-full shadow-lg active:cursor-grabbing"
          style={position ? { left: position.x, top: position.y, touchAction: 'none' } : { right: 20, bottom: 80, touchAction: 'none' }}
          aria-label="Open Assistant; drag to reposition"
          title="Open Assistant (Ctrl+J) · Drag to move · Alt+Arrow to reposition"
          onPointerDown={startDrag}
          onPointerMove={moveDrag}
          onPointerUp={finishDrag}
          onPointerCancel={(event) => finishDrag(event, false)}
          onKeyDown={moveWithKeyboard}
          onClickCapture={(event) => {
            if (!suppressClickRef.current) return
            suppressClickRef.current = false
            event.preventDefault()
            event.stopPropagation()
          }}
        >
          <Bot className="size-5" />
        </Button>
      </PopoverTrigger>
      <PopoverContent
        side="top"
        align="end"
        sideOffset={12}
        collisionPadding={8}
        aria-label="Assistant chat widget"
        className="flex h-[min(36rem,calc(100dvh-7rem))] w-[min(28rem,calc(100vw-1rem))] flex-col gap-0 overflow-hidden rounded-xl p-0 shadow-xl"
      >
        <header className="flex shrink-0 items-center gap-3 border-b px-4 py-3 text-left">
          <div className="flex size-8 shrink-0 items-center justify-center rounded-md bg-primary/10 text-primary">
            <Bot className="size-4" />
          </div>
          <div className="min-w-0 flex-1">
            <h2 className="text-sm font-semibold">Assistant</h2>
            <p className="truncate text-xs text-muted-foreground">
              Your current Northstar conversation
            </p>
          </div>
          <Button size="icon" variant="ghost" className="size-8" asChild>
            <Link
              to="/assistant"
              onClick={() => setOpen(false)}
              aria-label="Open full Assistant"
              title="Open full Assistant"
            >
              <Maximize2 className="size-4" />
            </Link>
          </Button>
          <Button
            size="icon"
            variant="ghost"
            className="size-8"
            aria-label="Close Assistant"
            title="Close Assistant"
            onClick={() => setOpen(false)}
          >
            <X className="size-4" />
          </Button>
        </header>
        <div className="flex min-h-0 flex-1 overflow-hidden">
          {open && (
            <Suspense
              fallback={(
                <div className="flex flex-1 items-center justify-center">
                  <Loader2 className="size-5 animate-spin text-muted-foreground" />
                </div>
              )}
            >
              <AssistantWorkspace compact />
            </Suspense>
          )}
        </div>
      </PopoverContent>
    </Popover>
  )
}
