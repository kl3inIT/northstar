import { useNavigate } from '@tanstack/react-router'
import { Loader2, Save, X } from 'lucide-react'
import { useRef, useState } from 'react'
import { toast } from 'sonner'
import { MarkdownBody } from '@/components/markdown-body'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { fileUrl, uploadFile } from '@/lib/files-api'
import { useUpdateNote } from '@/lib/notes-api'
import type { NoteDetail } from '@/lib/notes-types'

function tagsToText(tags: string[]): string {
  return tags.join(', ')
}

function textToTags(text: string): string[] {
  return text
    .split(',')
    .map((t) => t.trim())
    .filter(Boolean)
}

/**
 * Source editor for a note (title · folder · tags · markdown). Saves through
 * {@code PUT /api/notes/{id}}; when the title change moves the slug we navigate to
 * the new URL. A live preview mirrors what the read view will render.
 */
export function NoteEditor({ note, onDone }: { note: NoteDetail; onDone: () => void }) {
  const [title, setTitle] = useState(note.title)
  const [folderPath, setFolderPath] = useState(note.folderPath)
  const [tagsText, setTagsText] = useState(tagsToText(note.tags))
  const [content, setContent] = useState(note.contentMarkdown)
  const textareaRef = useRef<HTMLTextAreaElement>(null)
  const navigate = useNavigate()
  const update = useUpdateNote()

  /** Paste or drop an image → upload to the attachment vault, insert ![](…) at the caret. */
  async function insertImages(files: File[]) {
    const images = files.filter((f) => f.type.startsWith('image/'))
    if (images.length === 0) return
    try {
      const markdown = await Promise.all(
        images.map(async (f) => {
          const meta = await uploadFile(f, f.name || 'image')
          return `![${meta.filename}](${fileUrl(meta.id)})`
        }),
      )
      const el = textareaRef.current
      const at = el?.selectionStart ?? content.length
      const inserted = markdown.join('\n')
      setContent((prev) => `${prev.slice(0, at)}${inserted}${prev.slice(at)}`)
    } catch {
      toast.error('Image upload failed — try again.')
    }
  }

  const canSave = title.trim().length > 0 && !update.isPending

  function save() {
    if (!canSave) return
    update.mutate(
      {
        id: note.id,
        body: {
          title: title.trim(),
          folderPath,
          contentMarkdown: content,
          tags: textToTags(tagsText),
          version: note.version,
        },
      },
      {
        onSuccess: (next) => {
          onDone()
          if (next.slug !== note.slug) navigate({ to: '/notes/$slug', params: { slug: next.slug } })
        },
      },
    )
  }

  function onKeyDown(e: React.KeyboardEvent) {
    if ((e.metaKey || e.ctrlKey) && e.key === 's') {
      e.preventDefault()
      save()
    }
  }

  return (
    <div className="flex min-w-0 flex-1 flex-col" onKeyDown={onKeyDown}>
      <header className="space-y-3 border-b px-10 py-5">
        <Input
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          placeholder="Note title"
          className="h-auto border-0 px-0 text-2xl font-bold tracking-tight shadow-none focus-visible:ring-0"
        />
        <div className="flex flex-wrap items-end gap-4">
          <div className="grid gap-1.5">
            <Label htmlFor="note-folder" className="text-xs text-muted-foreground">
              Folder
            </Label>
            <Input
              id="note-folder"
              value={folderPath}
              onChange={(e) => setFolderPath(e.target.value)}
              placeholder="English/IELTS"
              className="h-8 w-56"
            />
          </div>
          <div className="grid flex-1 gap-1.5">
            <Label htmlFor="note-tags" className="text-xs text-muted-foreground">
              Tags (comma-separated)
            </Label>
            <Input
              id="note-tags"
              value={tagsText}
              onChange={(e) => setTagsText(e.target.value)}
              placeholder="writing, cohesion"
              className="h-8"
            />
          </div>
          <div className="flex items-center gap-2">
            <Button size="sm" variant="ghost" onClick={onDone} disabled={update.isPending}>
              <X className="size-4" /> Cancel
            </Button>
            <Button size="sm" onClick={save} disabled={!canSave}>
              {update.isPending ? <Loader2 className="size-4 animate-spin" /> : <Save className="size-4" />} Save
            </Button>
          </div>
        </div>
        {update.isError && (
          <p className="text-sm text-destructive">
            {(update.error as { status?: number } | null)?.status === 409
              ? 'This note was edited elsewhere — close the editor and reopen it to get the latest version.'
              : 'Save failed. Check the title and try again.'}
          </p>
        )}
      </header>
      <div className="grid min-h-0 flex-1 grid-cols-2 divide-x">
        <Textarea
          ref={textareaRef}
          value={content}
          onChange={(e) => setContent(e.target.value)}
          onPaste={(e) => {
            const files = [...e.clipboardData.files]
            if (files.some((f) => f.type.startsWith('image/'))) {
              e.preventDefault()
              insertImages(files)
            }
          }}
          onDrop={(e) => {
            const files = [...e.dataTransfer.files]
            if (files.some((f) => f.type.startsWith('image/'))) {
              e.preventDefault()
              insertImages(files)
            }
          }}
          placeholder="Write in Markdown. Link notes with [[Title]]. Paste images to attach them."
          className="min-h-0 resize-none rounded-none border-0 px-4 py-6 md:px-10 md:py-8 font-mono text-sm leading-relaxed shadow-none focus-visible:ring-0"
        />
        <div className="min-h-0 overflow-auto px-4 py-6 md:px-10 md:py-8">
          <MarkdownBody content={content} links={note.outgoingLinks} />
        </div>
      </div>
    </div>
  )
}
