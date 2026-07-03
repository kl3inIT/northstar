import { Outlet, useNavigate, useParams } from '@tanstack/react-router'
import { FolderTree as FolderTreeIcon, Plus, Search, Sparkles, X } from 'lucide-react'
import { useMemo, useState } from 'react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { ScrollArea } from '@/components/ui/scroll-area'
import { ToggleGroup, ToggleGroupItem } from '@/components/ui/toggle-group'
import { buildFolderTree } from '@/lib/folder-tree'
import { useCreateNote, useNotes } from '@/lib/notes-api'
import { CaptureDialog } from './capture-dialog'
import { FolderTree } from './folder-tree'
import { SearchPanel } from './search-panel'

type SidebarMode = 'files' | 'search'

/**
 * Col2 — the notes sidebar, two modes (VSCode/Obsidian style):
 * Files = folder tree with an INSTANT client-side name/tag filter over the loaded
 * list; Search = server full-text search over note bodies with ranked, highlighted
 * results. Separate surfaces because they answer different questions ("open the
 * note I know" vs "which notes mention X").
 */
export function NotesLayout() {
  const [mode, setMode] = useState<SidebarMode>('files')
  const [filter, setFilter] = useState('')
  const [query, setQuery] = useState('')
  const [captureOpen, setCaptureOpen] = useState(false)
  const { data: notes = [], isLoading } = useNotes('')
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

  function onNew() {
    const title = window.prompt('Tên note mới')?.trim()
    if (!title) return
    createNote.mutate(
      { title, folderPath: '', contentMarkdown: '', tags: [] },
      { onSuccess: (note) => navigate({ to: '/notes/$slug', params: { slug: note.slug } }) },
    )
  }

  const isFiles = mode === 'files'
  const value = isFiles ? filter : query
  const setValue = isFiles ? setFilter : setQuery

  return (
    <div className="flex min-w-0 flex-1">
      <aside className="flex w-72 shrink-0 flex-col border-r">
        <div className="space-y-2 p-3">
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
            <div className="flex items-center gap-1.5">
              <Button size="sm" onClick={() => setCaptureOpen(true)}>
                <Sparkles className="size-4" /> Capture
              </Button>
              <Button size="sm" variant="outline" onClick={onNew} disabled={createNote.isPending}>
                <Plus className="size-4" /> New
              </Button>
            </div>
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
        </div>
        <ScrollArea className="flex-1 px-2 pb-2">
          {isFiles ? (
            isLoading ? (
              <p className="px-3 py-2 text-sm text-muted-foreground">Đang tải…</p>
            ) : (
              <FolderTree tree={tree} activeSlug={params.slug} />
            )
          ) : (
            <SearchPanel query={query} activeSlug={params.slug} />
          )}
        </ScrollArea>
        {isFiles && needle && !isLoading && (
          <p className="border-t px-3 py-2 text-xs text-muted-foreground">
            {visible.length} of {notes.length} notes
          </p>
        )}
      </aside>
      <div className="flex min-w-0 flex-1">
        <Outlet />
      </div>
      <CaptureDialog open={captureOpen} onOpenChange={setCaptureOpen} />
    </div>
  )
}
