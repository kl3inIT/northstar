import { Outlet, useNavigate, useParams } from '@tanstack/react-router'
import { FolderTree as FolderTreeIcon, Plus, Search, X } from 'lucide-react'
import type { DragEvent } from 'react'
import { useMemo, useState } from 'react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { ScrollArea } from '@/components/ui/scroll-area'
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { ToggleGroup, ToggleGroupItem } from '@/components/ui/toggle-group'
import { buildFolderTree } from '@/lib/folder-tree'
import { cn } from '@/lib/utils'
import { useCreateNote, useNotes, useStagingCount } from '@/lib/notes-api'
import { FolderTree } from './folder-tree'
import { SearchPanel } from './search-panel'
import { StatusList } from './status-list'

type SidebarMode = 'files' | 'search'
type StatusTab = 'staging' | 'resources' | 'archive'

/**
 * Col2 — the notes sidebar. MFI working-state tabs on top: Staging (machine
 * drafts awaiting review), Resources (the trusted KB — folder tree + full-text
 * search, VSCode/Obsidian style), Archive (discarded, restorable). Files =
 * folder tree with an INSTANT client-side name/tag filter; Search = server
 * full-text search with ranked, highlighted results.
 */
export function NotesLayout() {
  const [tab, setTab] = useState<StatusTab>('resources')
  const [mode, setMode] = useState<SidebarMode>('files')
  const [filter, setFilter] = useState('')
  const [query, setQuery] = useState('')
  const [newDialogOpen, setNewDialogOpen] = useState(false)
  const [newTitle, setNewTitle] = useState('')
  const { data: notes = [], isLoading } = useNotes('')
  const { data: stagingCount = 0 } = useStagingCount()
  const params = useParams({ strict: false }) as { slug?: string }
  const navigate = useNavigate()
  const createNote = useCreateNote()

  const needle = filter.trim().toLowerCase()
  const visible = useMemo(
    () =>
      needle
        ? notes.filter(
            (n) =>
              n.title.toLowerCase().includes(needle) ||
              n.tags.some((t) => t.includes(needle)),
          )
        : notes,
    [notes, needle],
  )
  const tree = useMemo(() => buildFolderTree(visible), [visible])

  function submitNewNote() {
    const title = newTitle.trim()
    if (!title) return
    createNote.mutate(
      { title, folderPath: '', contentMarkdown: '', tags: [] },
      {
        onSuccess: (note) => {
          setNewDialogOpen(false)
          setNewTitle('')
          navigate({ to: '/notes/$slug', params: { slug: note.slug } })
        },
      },
    )
  }

  const isFiles = mode === 'files'
  const value = isFiles ? filter : query
  const setValue = isFiles ? setFilter : setQuery

  // Mobile is single-pane: the list until a note is picked, then the note
  // full-width (NoteView renders a back-to-list button under md).
  const hasNote = Boolean(params.slug)
  const blockSidebarFileDrop = (event: DragEvent<HTMLElement>) => {
    if (!Array.from(event.dataTransfer.types).includes('Files')) return
    event.preventDefault()
    event.stopPropagation()
    if (event.type === 'drop') {
      toast.info('Open a note in Edit mode to attach files.')
    }
  }

  return (
    <div className="flex min-h-0 min-w-0 flex-1">
      <aside
        onDragOver={blockSidebarFileDrop}
        onDrop={blockSidebarFileDrop}
        className={cn(
          'min-h-0 w-full shrink-0 flex-col overflow-hidden border-r md:flex md:w-72',
          hasNote ? 'hidden' : 'flex',
        )}
      >
        <div className="shrink-0 space-y-2 p-3">
          <Tabs value={tab} onValueChange={(v) => setTab(v as StatusTab)}>
            <TabsList className="w-full">
              <TabsTrigger value="staging" className="flex-1 gap-1.5">
                Staging
                {stagingCount > 0 && (
                  <span className="rounded-full bg-primary px-1.5 text-[10px] font-semibold text-primary-foreground">
                    {stagingCount}
                  </span>
                )}
              </TabsTrigger>
              <TabsTrigger value="resources" className="flex-1">
                Resources
              </TabsTrigger>
              <TabsTrigger value="archive" className="flex-1">
                Archive
              </TabsTrigger>
            </TabsList>
          </Tabs>
          {tab === 'resources' && (
            <>
              <div className="flex items-center justify-between gap-2">
                <ToggleGroup
                  type="single"
                  variant="outline"
                  size="sm"
                  value={mode}
                  onValueChange={(v) => v && setMode(v as SidebarMode)}
                >
                  <ToggleGroupItem value="files" aria-label="Files">
                    <FolderTreeIcon className="size-4" />
                  </ToggleGroupItem>
                  <ToggleGroupItem value="search" aria-label="Full-text search">
                    <Search className="size-4" />
                  </ToggleGroupItem>
                </ToggleGroup>
                <Button
                  size="sm"
                  variant="outline"
                  onClick={() => setNewDialogOpen(true)}
                  disabled={createNote.isPending}
                >
                  <Plus className="size-4" /> New
                </Button>
              </div>
              <div className="relative">
                <Search className="absolute left-2.5 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
                <Input
                  value={value}
                  onChange={(e) => setValue(e.target.value)}
                  placeholder={isFiles ? 'Filter by name or #tag' : 'Search note contents'}
                  className="px-8"
                />
                {value && (
                  <button
                    type="button"
                    aria-label="Clear"
                    onClick={() => setValue('')}
                    className="absolute right-2 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground"
                  >
                    <X className="size-4" />
                  </button>
                )}
              </div>
            </>
          )}
        </div>
        {tab === 'resources' && isFiles ? (
          <div className="min-h-0 flex-1 px-2 pb-2">
            {isLoading ? (
              <p className="px-3 py-2 text-sm text-muted-foreground">Loading…</p>
            ) : (
              <FolderTree tree={tree} activeSlug={params.slug} />
            )}
          </div>
        ) : (
          /* Radix wraps viewport children in a display:table div that sizes to
             content — force it to block so rows honor the pane width. */
          <ScrollArea className="min-h-0 flex-1 overflow-hidden px-2 pb-2 [&_[data-slot=scroll-area-viewport]>div]:!block">
            {tab === 'staging' ? (
              <StatusList status="STAGING" activeSlug={params.slug} />
            ) : tab === 'archive' ? (
              <StatusList status="ARCHIVED" activeSlug={params.slug} />
            ) : (
              <SearchPanel query={query} activeSlug={params.slug} />
            )}
          </ScrollArea>
        )}
        {tab === 'resources' && isFiles && needle && !isLoading && (
          <p className="shrink-0 border-t px-3 py-2 text-xs text-muted-foreground">
            {visible.length} of {notes.length} notes
          </p>
        )}
      </aside>
      <Dialog
        open={newDialogOpen}
        onOpenChange={(open) => {
          setNewDialogOpen(open)
          if (!open) setNewTitle('')
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>New Note</DialogTitle>
          </DialogHeader>
          <form
            className="space-y-4"
            onSubmit={(event) => {
              event.preventDefault()
              submitNewNote()
            }}
          >
            <div className="space-y-2">
              <Label htmlFor="new-note-title">Title</Label>
              <Input
                id="new-note-title"
                autoFocus
                value={newTitle}
                onChange={(event) => setNewTitle(event.target.value)}
                placeholder="Note title"
              />
            </div>
            <DialogFooter>
              <Button
                type="button"
                variant="outline"
                onClick={() => setNewDialogOpen(false)}
                disabled={createNote.isPending}
              >
                Cancel
              </Button>
              <Button type="submit" disabled={createNote.isPending || !newTitle.trim()}>
                Create
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
      <div className={cn('min-w-0 flex-1 md:flex', hasNote ? 'flex' : 'hidden')}>
        <Outlet />
      </div>
    </div>
  )
}
