import { Link, useParams } from '@tanstack/react-router'
import {
  Archive,
  Check,
  ChevronLeft,
  Clock,
  FileText,
  Info,
  Link2,
  List,
  PanelRightClose,
  PanelRightOpen,
  Pencil,
  Undo2,
} from 'lucide-react'
import { useEffect, useState } from 'react'
import { toast } from 'sonner'
import { MarkdownBody } from '@/components/markdown-body'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Separator } from '@/components/ui/separator'
import { Skeleton } from '@/components/ui/skeleton'
import { noteOutline } from '@/lib/note-outline'
import { textStats } from '@/lib/text-stats'
import { useNote, useSetNoteStatus } from '@/lib/notes-api'
import type { NoteDetail, NoteRef } from '@/lib/notes-types'
import { useProjects, type Project } from '@/lib/projects-api'
import { cn } from '@/lib/utils'
import { NoteEditor } from './note-editor'

const NOTE_SIDEBAR_KEY = 'note-right-sidebar-open'

function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })
}

export function NoteView() {
  const { slug } = useParams({ from: '/notes/$slug' })
  const { data: note, isLoading, isError } = useNote(slug)
  const { data: projects = [] } = useProjects()
  // Reading-first: opening a note shows the rendered view; the Edit button opens
  // the CodeMirror editor. Return to reading on every note switch.
  const [editing, setEditing] = useState(false)
  const [sidebarOpen, setSidebarOpen] = useState(
    () => localStorage.getItem(NOTE_SIDEBAR_KEY) !== '0',
  )
  useEffect(() => setEditing(false), [slug])

  if (isLoading) {
    return (
      <div className="min-w-0 flex-1 space-y-4 px-4 py-6 md:px-10 md:py-8">
        <Skeleton className="h-9 w-2/3" />
        <Skeleton className="h-4 w-40" />
        <Skeleton className="h-64 w-full" />
      </div>
    )
  }
  if (isError || !note) {
    return (
      <div className="flex flex-1 items-center justify-center text-sm text-muted-foreground">
        Note “{slug}” not found.
      </div>
    )
  }

  if (editing) {
    // key by id so the editor's local state resets when switching notes.
    return <NoteEditor key={note.id} note={note} onDone={() => setEditing(false)} />
  }

  const crumbs = note.folderPath ? note.folderPath.split('/') : []
  const stats = textStats(note.contentMarkdown)
  const project = note.projectId ? projects.find((p) => p.id === note.projectId) : undefined
  const toggleSidebar = () => {
    setSidebarOpen((open) => {
      localStorage.setItem(NOTE_SIDEBAR_KEY, open ? '0' : '1')
      return !open
    })
  }

  return (
    <div className="flex min-w-0 flex-1">
      <article className="min-w-0 flex-1 overflow-auto px-4 py-6 md:px-10 md:py-8">
       <div className={cn('mx-auto w-full', sidebarOpen ? 'max-w-5xl' : 'max-w-none')}>
        {/* Mobile is single-pane (the list pane is hidden) — offer a way back. */}
        <Button asChild size="sm" variant="ghost" className="-ml-2 mb-3 md:hidden">
          <Link to="/notes">
            <ChevronLeft className="size-4" /> All notes
          </Link>
        </Button>
        {note.status !== 'RESOURCE' && <StatusBanner note={note} />}
        <div className="flex items-start justify-between gap-4">
          <div className="min-w-0">
            {crumbs.length > 0 && (
              <nav className="mb-3 text-sm text-muted-foreground">{crumbs.join('  /  ')}</nav>
            )}
            <h1 className="text-3xl font-bold tracking-tight">{note.title}</h1>
          </div>
          <div className="flex shrink-0 items-center gap-2">
            <Button
              size="icon-sm"
              variant="ghost"
              className="hidden text-muted-foreground lg:inline-flex"
              aria-label={sidebarOpen ? 'Hide note sidebar' : 'Show note sidebar'}
              title={sidebarOpen ? 'Hide note sidebar' : 'Show note sidebar'}
              onClick={toggleSidebar}
            >
              {sidebarOpen ? <PanelRightClose className="size-4" /> : <PanelRightOpen className="size-4" />}
            </Button>
            <Button size="sm" variant="outline" onClick={() => setEditing(true)}>
              <Pencil className="size-4" /> Edit
            </Button>
          </div>
        </div>
        <div className="mt-3 flex flex-wrap items-center gap-2 text-sm text-muted-foreground">
          <span className="flex items-center gap-1.5">
            <Clock className="size-3.5" /> Updated {formatDate(note.updatedAt)}
          </span>
          {stats.words > 0 && (
            <span className="flex items-center gap-1.5">
              <FileText className="size-3.5" /> {stats.words} words · ~{stats.minutes} min
            </span>
          )}
          {note.tags.map((tag) => (
            <Badge key={tag}>#{tag}</Badge>
          ))}
        </div>
        <Separator className="my-6" />
        <MarkdownBody
          content={note.contentMarkdown}
          links={note.outgoingLinks}
          suppressFirstHeading={note.title}
        />
       </div>
      </article>
      {sidebarOpen && (
        <aside className="hidden w-80 shrink-0 overflow-auto border-l p-5 lg:block">
          <RightPanel note={note} project={project} />
        </aside>
      )}
    </div>
  )
}

/** MFI review bar: a staging note asks for its verdict; an archived one offers restore. */
function StatusBanner({ note }: { note: NoteDetail }) {
  const setStatus = useSetNoteStatus()
  const staging = note.status === 'STAGING'
  return (
    <div className="mb-6 flex flex-wrap items-center gap-3 rounded-lg border bg-muted/50 px-4 py-2.5">
      <p className="min-w-0 flex-1 text-sm text-muted-foreground">
        {staging ? 'This note was machine-drafted and is awaiting review (Staging).' : 'This note is in the Archive.'}
      </p>
      {staging ? (
        <>
          <Button
            size="sm"
            disabled={setStatus.isPending}
            onClick={() =>
              setStatus.mutate(
                { id: note.id, status: 'RESOURCE' },
                { onSuccess: () => toast.success('Moved to Resources') },
              )
            }
          >
            <Check className="size-4" /> Move to Resources
          </Button>
          <Button
            size="sm"
            variant="outline"
            disabled={setStatus.isPending}
            onClick={() =>
              setStatus.mutate(
                { id: note.id, status: 'ARCHIVED' },
                { onSuccess: () => toast.success('Archived') },
              )
            }
          >
            <Archive className="size-4" /> Archive
          </Button>
        </>
      ) : (
        <Button
          size="sm"
          variant="outline"
          disabled={setStatus.isPending}
          onClick={() =>
            setStatus.mutate(
              { id: note.id, status: 'RESOURCE' },
              { onSuccess: () => toast.success('Restored to Resources') },
            )
          }
        >
          <Undo2 className="size-4" /> Restore
        </Button>
      )}
    </div>
  )
}

function RightPanel({ note, project }: { note: NoteDetail; project?: Project }) {
  const outline = noteOutline(note.contentMarkdown)
  return (
    <div className="space-y-6">
      {outline.length > 1 && (
        <>
          <section>
            <h2 className="mb-3 flex items-center gap-2 text-sm font-semibold">
              <List className="size-4 text-muted-foreground" /> Outline
            </h2>
            <nav className="space-y-1 text-sm">
              {outline.map((h) => (
                <button
                  key={h.id}
                  type="button"
                  onClick={() =>
                    document.getElementById(h.id)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
                  }
                  style={{ paddingLeft: `${(h.level - 1) * 0.75}rem` }}
                  className="block w-full truncate text-left text-muted-foreground hover:text-foreground"
                >
                  {h.text}
                </button>
              ))}
            </nav>
          </section>
          <Separator />
        </>
      )}
      <section>
        <h2 className="mb-3 flex items-center gap-2 text-sm font-semibold">
          <Link2 className="size-4 text-muted-foreground" /> Backlinks
        </h2>
        {note.backlinks.length === 0 ? (
          <p className="text-sm text-muted-foreground">No backlinks yet.</p>
        ) : (
          <div className="space-y-2">
            {note.backlinks.map((link) => (
              <BacklinkCard key={link.slug ?? link.title} link={link} />
            ))}
          </div>
        )}
      </section>
      <Separator />
      <section>
        <h2 className="mb-3 flex items-center gap-2 text-sm font-semibold">
          <Info className="size-4 text-muted-foreground" /> Metadata
        </h2>
        <dl className="space-y-2.5 text-sm">
          <Row label="Created" value={formatDate(note.createdAt)} />
          <Row label="Updated" value={formatDate(note.updatedAt)} />
          <div className="flex items-start justify-between gap-3">
            <dt className="text-muted-foreground">Tags</dt>
            <dd className="flex flex-wrap justify-end gap-1">
              {note.tags.length ? note.tags.map((t) => <Badge key={t}>#{t}</Badge>) : '—'}
            </dd>
          </div>
          <Row label="Folder" value={note.folderPath || 'Root'} />
          <div className="flex items-start justify-between gap-3">
            <dt className="text-muted-foreground">Project</dt>
            <dd className="min-w-0 text-right">
              {project ? (
                <Link
                  to="/projects/$id"
                  params={{ id: project.id }}
                  className="text-primary hover:underline"
                >
                  {project.name}
                </Link>
              ) : note.projectId ? (
                <span className="text-muted-foreground">Unknown project</span>
              ) : (
                <span className="text-muted-foreground">None</span>
              )}
            </dd>
          </div>
        </dl>
      </section>
    </div>
  )
}

function BacklinkCard({ link }: { link: NoteRef }) {
  const body = (
    <>
      <div className="flex items-center gap-1.5 text-sm font-medium">
        <FileText className="size-3.5 shrink-0 text-muted-foreground" />
        <span className="truncate">{link.title}</span>
      </div>
      {link.snippet && <p className="mt-1 line-clamp-2 text-xs text-muted-foreground">{link.snippet}</p>}
    </>
  )
  return link.slug ? (
    <Link to="/notes/$slug" params={{ slug: link.slug }}>
      <Card className="p-3 transition-colors hover:bg-accent">{body}</Card>
    </Link>
  ) : (
    <Card className="p-3">{body}</Card>
  )
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center justify-between gap-3">
      <dt className="text-muted-foreground">{label}</dt>
      <dd className="text-right">{value}</dd>
    </div>
  )
}
