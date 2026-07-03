import { Link } from '@tanstack/react-router'
import { ChevronDown, ChevronRight, FileText, Folder } from 'lucide-react'
import { useState } from 'react'
import type { TreeFolder } from '@/lib/folder-tree'
import type { NoteSummary } from '@/lib/notes-types'
import { cn } from '@/lib/utils'

const INDENT = 12

export function FolderTree({ tree, activeSlug }: { tree: TreeFolder; activeSlug?: string }) {
  if (tree.folders.length === 0 && tree.notes.length === 0) {
    return <p className="px-3 py-2 text-sm text-muted-foreground">Chưa có note nào.</p>
  }
  return (
    <div className="py-1 text-sm">
      {tree.folders.map((folder) => (
        <FolderNode key={folder.path} folder={folder} depth={0} activeSlug={activeSlug} />
      ))}
      {tree.notes.map((note) => (
        <NoteLeaf key={note.id} note={note} depth={0} activeSlug={activeSlug} />
      ))}
    </div>
  )
}

function FolderNode({
  folder,
  depth,
  activeSlug,
}: {
  folder: TreeFolder
  depth: number
  activeSlug?: string
}) {
  const [open, setOpen] = useState(true)
  const Chevron = open ? ChevronDown : ChevronRight
  return (
    <div>
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        className="flex w-full items-center gap-1.5 rounded-md py-1 pr-2 text-left hover:bg-accent"
        style={{ paddingLeft: depth * INDENT + 8 }}
      >
        <Chevron className="size-3.5 shrink-0 text-muted-foreground" />
        <Folder className="size-4 shrink-0 text-muted-foreground" />
        <span className="truncate font-medium">{folder.name}</span>
      </button>
      {open && (
        <div>
          {folder.folders.map((child) => (
            <FolderNode key={child.path} folder={child} depth={depth + 1} activeSlug={activeSlug} />
          ))}
          {folder.notes.map((note) => (
            <NoteLeaf key={note.id} note={note} depth={depth + 1} activeSlug={activeSlug} />
          ))}
        </div>
      )}
    </div>
  )
}

function NoteLeaf({
  note,
  depth,
  activeSlug,
}: {
  note: NoteSummary
  depth: number
  activeSlug?: string
}) {
  const active = note.slug === activeSlug
  return (
    <Link
      to="/notes/$slug"
      params={{ slug: note.slug }}
      className={cn(
        'flex items-center gap-1.5 rounded-md py-1 pr-2 hover:bg-accent',
        active && 'bg-primary/10 font-medium text-primary',
      )}
      style={{ paddingLeft: depth * INDENT + 8 + 18 }}
    >
      <FileText
        className={cn('size-4 shrink-0', active ? 'text-primary' : 'text-muted-foreground')}
      />
      <span className="truncate">{note.title}</span>
    </Link>
  )
}
