import { Link, useLocation } from '@tanstack/react-router'
import { Bot, Loader2, Maximize2, X } from 'lucide-react'
import { lazy, Suspense, useEffect, useState } from 'react'
import { Button } from '@/components/ui/button'
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
  const available = pathname !== '/assistant' && pathname !== '/login'

  useEffect(() => {
    if (!available) setOpen(false)
  }, [available])

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
          className="fixed bottom-20 right-5 z-40 size-11 rounded-full shadow-lg"
          aria-label="Open Assistant"
          title="Open Assistant (Ctrl+J)"
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
