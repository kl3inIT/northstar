import { useNavigate } from '@tanstack/react-router'
import { FileText, Plus, Sparkles } from 'lucide-react'
import { useEffect, useState } from 'react'
import {
  CommandDialog,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
  CommandSeparator,
} from '@/components/ui/command'
import { useCreateNote, useNotes } from '@/lib/notes-api'

/**
 * Cmd/Ctrl+K quick switcher — fuzzy jump-to-note by title (cmdk's built-in
 * subsequence scorer), plus a "New note" action. Navigation only; full-text
 * body search lives in the notes sidebar's Search mode.
 */
export function CommandMenu({ onCapture }: { onCapture?: () => void }) {
  const [open, setOpen] = useState(false)
  const { data: notes = [] } = useNotes('')
  const navigate = useNavigate()
  const createNote = useCreateNote()

  useEffect(() => {
    function down(e: KeyboardEvent) {
      if (e.key === 'k' && (e.metaKey || e.ctrlKey)) {
        e.preventDefault()
        setOpen((v) => !v)
      }
    }
    document.addEventListener('keydown', down)
    return () => document.removeEventListener('keydown', down)
  }, [])

  function go(slug: string) {
    setOpen(false)
    navigate({ to: '/notes/$slug', params: { slug } })
  }

  function onNew() {
    setOpen(false)
    const title = window.prompt('New note title')?.trim()
    if (!title) return
    createNote.mutate(
      { title, folderPath: '', contentMarkdown: '', tags: [] },
      { onSuccess: (note) => navigate({ to: '/notes/$slug', params: { slug: note.slug } }) },
    )
  }

  return (
    <CommandDialog open={open} onOpenChange={setOpen}>
      <CommandInput placeholder="Jump to a note…" />
      <CommandList>
        <CommandEmpty>No notes found.</CommandEmpty>
        <CommandGroup heading="Notes">
          {notes.map((note) => (
            <CommandItem
              key={note.id}
              value={`${note.title} ${note.folderPath} ${note.tags.join(' ')}`}
              onSelect={() => go(note.slug)}
            >
              <FileText className="text-muted-foreground" />
              <span className="truncate">{note.title}</span>
              {note.folderPath && (
                <span className="ml-auto truncate text-xs text-muted-foreground">
                  {note.folderPath}
                </span>
              )}
            </CommandItem>
          ))}
        </CommandGroup>
        <CommandSeparator />
        <CommandGroup heading="Actions">
          {onCapture && (
            <CommandItem
              onSelect={() => {
                setOpen(false)
                onCapture()
              }}
            >
              <Sparkles className="text-muted-foreground" />
              Capture (AI)
            </CommandItem>
          )}
          <CommandItem onSelect={onNew}>
            <Plus className="text-muted-foreground" />
            New note
          </CommandItem>
        </CommandGroup>
      </CommandList>
    </CommandDialog>
  )
}
