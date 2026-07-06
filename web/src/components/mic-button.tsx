import { Loader2, Mic, Square } from 'lucide-react'
import { toast } from 'sonner'
import { PromptInputButton } from '@/components/ai-elements/prompt-input'
import { useRealtimeDictation } from '@/lib/use-realtime-dictation'

/**
 * Live dictation for any PromptInput composer: text streams into the bound
 * textarea AS YOU SPEAK (OpenAI Realtime, gpt-realtime-whisper). Dictation-
 * first — the user reviews before submitting; nothing is stored server-side
 * and audio goes browser→OpenAI direct. `compact` renders icon-only states
 * (the ChatGPT-style in-input mic).
 */
export function MicButton({
  value,
  onChange,
  compact = false,
}: {
  value: string
  onChange: (text: string) => void
  compact?: boolean
}) {
  const { state, seconds, start, stop } = useRealtimeDictation(onChange, (msg) => toast.error(msg))

  if (state === 'connecting' || state === 'finishing') {
    return (
      <PromptInputButton variant="ghost" disabled className={compact ? 'rounded-full' : undefined}>
        <Loader2 className="size-4 animate-spin" />
        {!compact && (state === 'connecting' ? 'Connecting…' : 'Finalizing…')}
      </PromptInputButton>
    )
  }
  if (state === 'live') {
    return (
      <PromptInputButton
        variant="destructive"
        onClick={stop}
        className={compact ? 'rounded-full' : undefined}
      >
        <Square className="size-4" /> {Math.floor(seconds / 60)}:{String(seconds % 60).padStart(2, '0')}
      </PromptInputButton>
    )
  }
  return (
    <PromptInputButton
      variant="ghost"
      onClick={() => void start(value)}
      className={compact ? 'rounded-full' : undefined}
      title="Words appear as you speak (audio goes straight to OpenAI, never stored)"
    >
      <Mic className="size-4" />
      {!compact && 'Dictate'}
    </PromptInputButton>
  )
}
