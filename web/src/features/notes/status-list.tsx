import { Link } from '@tanstack/react-router'
import { Archive, Check, Undo2 } from 'lucide-react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { useNotesByStatus, useSetNoteStatus } from '@/lib/notes-api'
import type { NoteSummary } from '@/lib/notes-types'
import { cn } from '@/lib/utils'

/**
 * Sidebar list for the Staging / Archive tabs — a flat review queue instead of
 * the folder tree. Staging rows carry the MFI verdict actions (→ Resources,
 * Archive); archived rows can be restored.
 */
export function StatusList({ status, activeSlug }: { status: 'STAGING' | 'ARCHIVED'; activeSlug?: string }) {
  const { data: notes = [], isLoading } = useNotesByStatus(status)

  if (isLoading) {
    return <p className="px-3 py-2 text-sm text-muted-foreground">Loading…</p>
  }
  if (notes.length === 0) {
    return (
      <p className="px-3 py-2 text-sm text-muted-foreground">
        {status === 'STAGING'
          ? 'Staging is empty — newly captured notes await review here.'
          : 'No archived notes yet.'}
      </p>
    )
  }
  return (
    <div className="space-y-1">
      {notes.map((note) => (
        <StatusRow key={note.id} note={note} active={note.slug === activeSlug} />
      ))}
    </div>
  )
}

function StatusRow({ note, active }: { note: NoteSummary; active: boolean }) {
  const setStatus = useSetNoteStatus()
  const staging = note.status === 'STAGING'
  return (
    <div
      className={cn(
        'group rounded-md px-2 py-1.5 transition-colors hover:bg-accent',
        active && 'bg-accent',
      )}
    >
      <Link to="/notes/$slug" params={{ slug: note.slug }} className="block min-w-0">
        <p className="truncate text-sm font-medium">{note.title}</p>
        {note.snippet && <p className="line-clamp-2 text-xs text-muted-foreground">{note.snippet}</p>}
      </Link>
      <div className="mt-1 flex gap-1">
        {staging ? (
          <>
            <Button
              size="sm"
              variant="outline"
              className="h-6 flex-1 text-xs"
              disabled={setStatus.isPending}
              onClick={() =>
                setStatus.mutate(
                  { id: note.id, status: 'RESOURCE' },
                  { onSuccess: () => toast.success('Moved to Resources') },
                )
              }
            >
              <Check className="size-3" /> Resources
            </Button>
            <Button
              size="sm"
              variant="ghost"
              className="h-6 text-xs text-muted-foreground"
              disabled={setStatus.isPending}
              onClick={() =>
                setStatus.mutate(
                  { id: note.id, status: 'ARCHIVED' },
                  { onSuccess: () => toast.success('Archived') },
                )
              }
            >
              <Archive className="size-3" />
            </Button>
          </>
        ) : (
          <Button
            size="sm"
            variant="outline"
            className="h-6 flex-1 text-xs"
            disabled={setStatus.isPending}
            onClick={() =>
              setStatus.mutate(
                { id: note.id, status: 'RESOURCE' },
                { onSuccess: () => toast.success('Restored to Resources') },
              )
            }
          >
            <Undo2 className="size-3" /> Restore
          </Button>
        )}
      </div>
    </div>
  )
}
