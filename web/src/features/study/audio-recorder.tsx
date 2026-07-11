import { CircleStop, Mic, RotateCcw } from 'lucide-react'
import { Button } from '@/components/ui/button'
import type { useWavRecorder } from '@/lib/use-wav-recorder'
import { cn } from '@/lib/utils'

type Recorder = ReturnType<typeof useWavRecorder>

export function AudioRecorder({ recorder, maximumSeconds, compact = false }: {
  recorder: Recorder
  maximumSeconds: number
  compact?: boolean
}) {
  const remaining = Math.max(0, maximumSeconds - recorder.seconds)
  return (
    <div className={cn('flex flex-wrap items-center gap-2', !compact && 'rounded-lg border bg-muted/20 p-3')}>
      {recorder.state === 'recording' ? (
        <Button type="button" variant="destructive" onClick={recorder.stop}>
          <CircleStop className="size-4" /> Stop
        </Button>
      ) : (
        <Button
          type="button"
          variant={recorder.state === 'ready' ? 'outline' : 'default'}
          disabled={recorder.state === 'requesting'}
          onClick={() => void recorder.start()}
        >
          {recorder.state === 'ready' ? <RotateCcw className="size-4" /> : <Mic className="size-4" />}
          {recorder.state === 'requesting' ? 'Opening mic…' : recorder.state === 'ready' ? 'Record again' : 'Record'}
        </Button>
      )}
      <div className="min-w-24 text-xs tabular-nums text-muted-foreground">
        {recorder.state === 'recording' && (
          <span className="inline-flex items-center gap-1.5 text-destructive">
            <span className="size-2 animate-pulse rounded-full bg-destructive" />
            {remaining.toFixed(1)}s left
          </span>
        )}
        {recorder.state === 'ready' && <span>WAV ready · {recorder.seconds.toFixed(1)}s</span>}
        {recorder.state === 'idle' && <span>Up to {maximumSeconds}s</span>}
      </div>
      {recorder.error && <p className="basis-full text-xs text-destructive">{recorder.error}</p>}
    </div>
  )
}
