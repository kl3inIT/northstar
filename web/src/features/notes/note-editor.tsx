import { useNavigate } from '@tanstack/react-router'
import { Eye, Loader2, Save } from 'lucide-react'
import { useRef, useState } from 'react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { fileUrl, uploadFile } from '@/lib/files-api'
import { useNoteIndex, useUpdateNote } from '@/lib/notes-api'
import type { NoteDetail } from '@/lib/notes-types'
import { useProjects } from '@/lib/projects-api'
import { textStats } from '@/lib/text-stats'
import { NoteCmEditor, type NoteCmEditorHandle } from './cm/note-cm-editor'

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
 * Source editor for a note (title · folder · tags · markdown). The body is a
 * CodeMirror "decorated source" editor (see {@link ./cm/note-cm-editor}) —
 * Markdown stays the literal buffer, styled in place, so saves round-trip
 * losslessly. Saves through {@code PUT /api/notes/{id}}; when the title change
 * moves the slug we navigate to the new URL.
 */
export function NoteEditor({ note, onDone }: { note: NoteDetail; onDone: () => void }) {
  const [title, setTitle] = useState(note.title)
  const [folderPath, setFolderPath] = useState(note.folderPath)
  const [tagsText, setTagsText] = useState(tagsToText(note.tags))
  const [projectId, setProjectId] = useState<string | null>(note.projectId)
  const [content, setContent] = useState(note.contentMarkdown)
  const editorRef = useRef<NoteCmEditorHandle>(null)
  const navigate = useNavigate()
  const update = useUpdateNote()
  const { data: index } = useNoteIndex()
  const { data: projects = [] } = useProjects()
  const titles = (index ?? []).map((n) => n.title)
  const stats = textStats(content)

  /**
   * Paste or drop files → upload to the attachment vault, insert at the caret:
   * images render inline as ![](…), documents (pdf/docx/…) become [name](…)
   * links. Uploaded documents are Tika-extracted and embedded by the api, so
   * search_knowledge finds their content, not just this note's text.
   */
  async function insertFiles(files: File[]) {
    const MAX_BYTES = 25 * 1024 * 1024 // matches the api's multipart cap
    const oversize = files.filter((f) => f.size > MAX_BYTES)
    if (oversize.length > 0) {
      toast.error(`${oversize[0].name} is over the 25MB limit.`)
    }
    const uploadable = files.filter((f) => f.size <= MAX_BYTES)
    if (uploadable.length === 0) return
    try {
      const markdown = await Promise.all(
        uploadable.map(async (f) => {
          const meta = await uploadFile(f, f.name || 'file')
          return f.type.startsWith('image/')
            ? `![${meta.filename}](${fileUrl(meta.id)})`
            : `[${meta.filename}](${fileUrl(meta.id)})`
        }),
      )
      editorRef.current?.insertAtCursor(markdown.join('\n'))
    } catch {
      toast.error('Upload failed — try again.')
    }
  }

  /** Cmd/Ctrl+click on a [[wiki link]] → open the target note if it exists. */
  function openWikiLink(linkTitle: string) {
    const target = (index ?? []).find((n) => n.title.toLowerCase() === linkTitle.toLowerCase())
    if (target) {
      navigate({ to: '/notes/$slug', params: { slug: target.slug } })
    } else {
      toast.info(`“${linkTitle}” does not exist yet.`)
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
          projectId,
          version: note.version,
        },
      },
      {
        onSuccess: (next) => {
          toast.success('Saved')
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
      <header className="border-b px-4 py-5 md:px-10">
       <div className="mx-auto w-full max-w-5xl space-y-3">
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
          <div className="grid w-64 gap-1.5">
            <Label className="text-xs text-muted-foreground">
              Project
            </Label>
            <Select
              value={projectId ?? 'none'}
              onValueChange={(value) => setProjectId(value === 'none' ? null : value)}
            >
              <SelectTrigger className="h-8 w-full">
                <SelectValue placeholder="No project" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="none">No project</SelectItem>
                {projects.map((project) => (
                  <SelectItem key={project.id} value={project.id}>
                    {project.name}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <div className="flex items-center gap-2">
            <Button size="sm" variant="ghost" onClick={onDone} disabled={update.isPending}>
              <Eye className="size-4" /> Reading
            </Button>
            <Button size="sm" onClick={save} disabled={!canSave}>
              {update.isPending ? <Loader2 className="size-4 animate-spin" /> : <Save className="size-4" />} Save
            </Button>
          </div>
        </div>
        <p className="text-xs text-muted-foreground">
          {stats.words} words · ~{stats.minutes} min read
        </p>
        {update.isError && (
          <p className="text-sm text-destructive">
            {(update.error as { status?: number } | null)?.status === 409
              ? 'This note was edited elsewhere — switch to Reading and back to get the latest version.'
              : 'Save failed. Check the title and try again.'}
          </p>
        )}
       </div>
      </header>
      <div className="min-h-0 flex-1 overflow-auto px-4 py-6 text-sm leading-relaxed md:px-10 md:py-8">
       <div className="mx-auto w-full max-w-3xl">
        <NoteCmEditor
          ref={editorRef}
          value={content}
          onChange={setContent}
          onFiles={insertFiles}
          noteTitles={titles}
          onOpenWikiLink={openWikiLink}
          placeholder="Write in Markdown. Link notes with [[Title]]. Paste or drop images and files (PDF, DOCX…) to attach them."
        />
       </div>
      </div>
    </div>
  )
}
