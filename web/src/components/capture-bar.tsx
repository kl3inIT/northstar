import { useNavigate } from '@tanstack/react-router'
import { useQueryClient } from '@tanstack/react-query'
import { Sparkles } from 'lucide-react'
import { useCallback, useEffect, useState } from 'react'
import { toast } from 'sonner'
import { Dialog, DialogContent, DialogTitle } from '@/components/ui/dialog'
import { Textarea } from '@/components/ui/textarea'
import { capture } from '@/lib/capture-api'

/**
 * Global capture: a minimal floating bar (Ctrl+Shift+K). Enter fires and CLOSES
 * immediately — the AI classifies (task vs note) and creates in the background;
 * a toast reports what was created with Undo / Open. No review dialog.
 */
export function CaptureBar({ open, onOpenChange }: { open: boolean; onOpenChange: (v: boolean) => void }) {
  const [text, setText] = useState('')
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  const fire = useCallback(
    (raw: string) => {
      const promise = capture(raw)
      toast.promise(promise, {
        loading: 'Capturing…',
        success: (result) => {
          queryClient.invalidateQueries({ queryKey: ['tasks'] })
          queryClient.invalidateQueries({ queryKey: ['notes'] })
          const undo = () =>
            result.undo().then(() => {
              queryClient.invalidateQueries({ queryKey: ['tasks'] })
              queryClient.invalidateQueries({ queryKey: ['notes'] })
            })
          if (result.kind === 'TASK') {
            return {
              message: `Task: ${result.title}`,
              description: result.dueDate ? `Due ${result.dueDate}` : 'No due date',
              action: { label: 'Undo', onClick: undo },
            }
          }
          return {
            message: `Note: ${result.title}`,
            description: result.folderPath || 'Root',
            action: {
              label: 'Open',
              onClick: () => navigate({ to: '/notes/$slug', params: { slug: result.slug } }),
            },
            cancel: { label: 'Undo', onClick: undo },
          }
        },
        error: 'Capture thất bại — thử lại.',
      })
    },
    [navigate, queryClient],
  )

  function onKeyDown(e: React.KeyboardEvent) {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      const raw = text.trim()
      if (!raw) return
      setText('')
      onOpenChange(false)
      fire(raw)
    }
  }

  // Reset the draft text when reopened.
  useEffect(() => {
    if (open) setText('')
  }, [open])

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent
        className="top-1/3 gap-2 p-3 sm:max-w-xl"
        showCloseButton={false}
        aria-describedby={undefined}
      >
        <DialogTitle className="flex items-center gap-2 text-sm font-medium text-muted-foreground">
          <Sparkles className="size-4 text-primary" /> Capture — task hoặc note, AI tự phân loại
        </DialogTitle>
        <Textarea
          value={text}
          onChange={(e) => setText(e.target.value)}
          onKeyDown={onKeyDown}
          placeholder="Gõ hoặc paste… Enter để capture, Shift+Enter xuống dòng"
          className="min-h-20 resize-none border-0 px-1 shadow-none focus-visible:ring-0"
          autoFocus
        />
      </DialogContent>
    </Dialog>
  )
}
