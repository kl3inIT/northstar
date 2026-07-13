import { Link, useLocation } from '@tanstack/react-router'
import { Bot, Loader2, Maximize2, X } from 'lucide-react'
import { motion, useMotionValue } from 'motion/react'
import { lazy, Suspense, useEffect, useRef, useState } from 'react'
import { Button } from '@/components/ui/button'
import {
  ASSISTANT_WIDGET_STORAGE_KEY,
  clampAssistantWidgetPosition,
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
const MotionButton = motion.create(Button)

export function AssistantWidget() {
  const pathname = useLocation({ select: (location) => location.pathname })
  const [open, setOpen] = useState(false)
  const [positioned, setPositioned] = useState(false)
  const constraintsRef = useRef<HTMLDivElement>(null)
  const suppressClickRef = useRef(false)
  const x = useMotionValue(0)
  const y = useMotionValue(0)
  const available = pathname !== '/assistant' && pathname !== '/login'

  function viewport() {
    return { width: window.innerWidth, height: window.innerHeight }
  }

  function updatePosition(next: AssistantWidgetPosition, persist = false) {
    const clamped = clampAssistantWidgetPosition(next, viewport())
    x.set(clamped.x)
    y.set(clamped.y)
    if (persist) localStorage.setItem(ASSISTANT_WIDGET_STORAGE_KEY, JSON.stringify(clamped))
  }

  useEffect(() => {
    const currentViewport = viewport()
    const initial = parseAssistantWidgetPosition(
      localStorage.getItem(ASSISTANT_WIDGET_STORAGE_KEY),
      currentViewport,
    ) ?? defaultAssistantWidgetPosition(currentViewport)
    updatePosition(initial)
    setPositioned(true)

    function keepInsideViewport() {
      updatePosition({ x: x.get(), y: y.get() }, true)
    }

    window.addEventListener('resize', keepInsideViewport)
    return () => window.removeEventListener('resize', keepInsideViewport)
    // Position setup is intentionally browser-only and runs once.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  useEffect(() => {
    if (!available) setOpen(false)
  }, [available])

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
    const current = positioned
      ? { x: x.get(), y: y.get() }
      : defaultAssistantWidgetPosition(viewport())
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
    <>
      <div ref={constraintsRef} className="pointer-events-none fixed inset-2" aria-hidden="true" />
      <Popover open={open} onOpenChange={setOpen}>
        <PopoverTrigger asChild>
          <MotionButton
            size="icon"
            className="fixed z-40 size-11 cursor-grab rounded-full shadow-lg active:cursor-grabbing"
            style={positioned
              ? { left: 0, top: 0, x, y, touchAction: 'none' }
              : { right: 20, bottom: 80, touchAction: 'none' }}
            drag={positioned}
            dragConstraints={constraintsRef}
            dragElastic={0}
            dragMomentum={false}
            whileDrag={{ scale: 1.06 }}
            aria-label="Open Assistant; drag to reposition"
            title="Open Assistant (Ctrl+J) · Drag to move · Alt+Arrow to reposition"
            onDragStart={() => {
              suppressClickRef.current = true
              setOpen(false)
            }}
            onDragEnd={() => updatePosition({ x: x.get(), y: y.get() }, true)}
            onKeyDown={moveWithKeyboard}
            onClickCapture={(event) => {
              if (!suppressClickRef.current) return
              suppressClickRef.current = false
              event.preventDefault()
              event.stopPropagation()
            }}
          >
            <Bot className="size-5" />
          </MotionButton>
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
    </>
  )
}
