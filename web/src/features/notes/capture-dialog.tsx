import { useNavigate } from '@tanstack/react-router'
import { ArrowLeft, Loader2, Sparkles } from 'lucide-react'
import { useState } from 'react'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { useDraftNote, type NoteDraft } from '@/lib/capture-api'
import { useCreateNote } from '@/lib/notes-api'

/**
 * AI capture: paste anything → one LLM call proposes a note (title, folder,
 * tags, cleaned Markdown with wiki-links) → the user reviews/edits → create.
 * The draft is never persisted without confirmation.
 */
export function CaptureDialog({ open, onOpenChange }: { open: boolean; onOpenChange: (v: boolean) => void }) {
  const [text, setText] = useState('')
  const [draft, setDraft] = useState<NoteDraft | null>(null)
  const extract = useDraftNote()
  const createNote = useCreateNote()
  const navigate = useNavigate()

  function reset() {
    setText('')
    setDraft(null)
    extract.reset()
    createNote.reset()
  }

  function onExtract() {
    if (!text.trim()) return
    extract.mutate(text, { onSuccess: setDraft })
  }

  function onCreate() {
    if (!draft || !draft.title.trim()) return
    createNote.mutate(
      {
        title: draft.title.trim(),
        folderPath: draft.folderPath,
        contentMarkdown: draft.contentMarkdown,
        tags: draft.tags,
      },
      {
        onSuccess: (note) => {
          onOpenChange(false)
          reset()
          navigate({ to: '/notes/$slug', params: { slug: note.slug } })
        },
      },
    )
  }

  return (
    <Dialog open={open} onOpenChange={(v) => { onOpenChange(v); if (!v) reset() }}>
      <DialogContent className="sm:max-w-2xl">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <Sparkles className="size-4 text-primary" /> Capture
          </DialogTitle>
          <DialogDescription>
            {draft
              ? 'Xem lại draft AI đề xuất rồi tạo note.'
              : 'Paste bất kỳ thứ gì — AI sẽ dựng thành note có cấu trúc.'}
          </DialogDescription>
        </DialogHeader>

        {!draft ? (
          <div className="space-y-3">
            <Textarea
              value={text}
              onChange={(e) => setText(e.target.value)}
              placeholder="Paste text, ý tưởng, đoạn bài học, link kèm ghi chú…"
              className="min-h-40"
              autoFocus
            />
            {extract.isError && (
              <p className="text-sm text-destructive">
                Extract thất bại — kiểm tra OPENAI_API_KEY của api rồi thử lại.
              </p>
            )}
            <div className="flex justify-end">
              <Button onClick={onExtract} disabled={!text.trim() || extract.isPending}>
                {extract.isPending ? <Loader2 className="size-4 animate-spin" /> : <Sparkles className="size-4" />}
                {extract.isPending ? 'Extracting…' : 'Extract'}
              </Button>
            </div>
          </div>
        ) : (
          <div className="space-y-3">
            <div className="grid gap-1.5">
              <Label htmlFor="cap-title">Title</Label>
              <Input
                id="cap-title"
                value={draft.title}
                onChange={(e) => setDraft({ ...draft, title: e.target.value })}
              />
            </div>
            <div className="grid grid-cols-2 gap-3">
              <div className="grid gap-1.5">
                <Label htmlFor="cap-folder">Folder</Label>
                <Input
                  id="cap-folder"
                  value={draft.folderPath}
                  onChange={(e) => setDraft({ ...draft, folderPath: e.target.value })}
                />
              </div>
              <div className="grid gap-1.5">
                <Label htmlFor="cap-tags">Tags (comma-separated)</Label>
                <Input
                  id="cap-tags"
                  value={draft.tags.join(', ')}
                  onChange={(e) =>
                    setDraft({ ...draft, tags: e.target.value.split(',').map((t) => t.trim()).filter(Boolean) })
                  }
                />
              </div>
            </div>
            <div className="grid gap-1.5">
              <Label htmlFor="cap-content">Content (Markdown)</Label>
              <Textarea
                id="cap-content"
                value={draft.contentMarkdown}
                onChange={(e) => setDraft({ ...draft, contentMarkdown: e.target.value })}
                className="min-h-48 font-mono text-sm"
              />
            </div>
            {createNote.isError && <p className="text-sm text-destructive">Tạo note thất bại.</p>}
            <div className="flex items-center justify-between">
              <Button variant="ghost" onClick={() => setDraft(null)} disabled={createNote.isPending}>
                <ArrowLeft className="size-4" /> Back
              </Button>
              <Button onClick={onCreate} disabled={!draft.title.trim() || createNote.isPending}>
                {createNote.isPending && <Loader2 className="size-4 animate-spin" />} Create note
              </Button>
            </div>
          </div>
        )}
      </DialogContent>
    </Dialog>
  )
}
