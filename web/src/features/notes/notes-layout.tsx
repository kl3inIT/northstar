import { Outlet, useNavigate, useParams } from '@tanstack/react-router'
import { FilePlus2, FolderPlus, FolderTree as FolderTreeIcon, Plus, Search, Upload, X } from 'lucide-react'
import type { DragEvent } from 'react'
import { useMemo, useRef, useState } from 'react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { ScrollArea } from '@/components/ui/scroll-area'
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { ToggleGroup, ToggleGroupItem } from '@/components/ui/toggle-group'
import { fileUrl, uploadFile } from '@/lib/files-api'
import { buildFolderTree } from '@/lib/folder-tree'
import { cn } from '@/lib/utils'
import { useCreateNote, useNotes, useStagingCount } from '@/lib/notes-api'
import { FolderTree } from './folder-tree'
import { SearchPanel } from './search-panel'
import { StatusList } from './status-list'

type SidebarMode = 'files' | 'search'
type StatusTab = 'staging' | 'resources' | 'archive'
type CreateDialogState =
  | { kind: 'note'; folderPath: string }
  | { kind: 'folder'; parentPath: string }

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
  const [createDialog, setCreateDialog] = useState<CreateDialogState | null>(null)
  const [newTitle, setNewTitle] = useState('')
  const [newFolderName, setNewFolderName] = useState('')
  const [newFolderNoteTitle, setNewFolderNoteTitle] = useState('Index')
  const uploadInputRef = useRef<HTMLInputElement | null>(null)
  const uploadFolderPathRef = useRef('')
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

  function openNewNote(folderPath: string) {
    setNewTitle('')
    setCreateDialog({ kind: 'note', folderPath: normalizeFolderPath(folderPath) })
  }

  function openNewFolder(parentPath: string) {
    setNewFolderName('')
    setNewFolderNoteTitle('Index')
    setCreateDialog({ kind: 'folder', parentPath: normalizeFolderPath(parentPath) })
  }

  function submitNewNote() {
    if (!createDialog) return

    const title = newTitle.trim()
    const folderName = cleanFolderPath(newFolderName)
    const folderPath = createDialog.kind === 'note'
      ? createDialog.folderPath
      : joinFolderPath(createDialog.parentPath, folderName)
    const noteTitle = createDialog.kind === 'note'
      ? title
      : (newFolderNoteTitle.trim() || 'Index')

    if (!noteTitle || (createDialog.kind === 'folder' && !folderName)) return

    createNote.mutate(
      { title: noteTitle, folderPath, contentMarkdown: '', tags: [] },
      {
        onSuccess: (note) => {
          closeCreateDialog()
          navigate({ to: '/notes/$slug', params: { slug: note.slug } })
        },
      },
    )
  }

  function closeCreateDialog() {
    setCreateDialog(null)
    setNewTitle('')
    setNewFolderName('')
    setNewFolderNoteTitle('Index')
  }

  function openUploadFiles(folderPath: string) {
    uploadFolderPathRef.current = normalizeFolderPath(folderPath)
    uploadInputRef.current?.click()
  }

  async function addFilesToFolder(folderPath: string, files: File[]) {
    const MAX_BYTES = 25 * 1024 * 1024
    const uploadable = files.filter((file) => file.size <= MAX_BYTES)
    const oversize = files.find((file) => file.size > MAX_BYTES)

    if (oversize) toast.error(`${oversize.name} is over the 25MB limit.`)
    if (uploadable.length === 0) return

    try {
      const links = await Promise.all(
        uploadable.map(async (file) => {
          const meta = await uploadFile(file, file.name || 'file')
          return file.type.startsWith('image/')
            ? `![${meta.filename}](${fileUrl(meta.id)})`
            : `[${meta.filename}](${fileUrl(meta.id)})`
        }),
      )
      const title = uploadable.length === 1 ? titleFromFilename(uploadable[0].name) : `Files ${todayStamp()}`
      const note = await createNote.mutateAsync({
        title,
        folderPath: normalizeFolderPath(folderPath),
        contentMarkdown: links.join('\n'),
        tags: [],
      })
      toast.success(`Added ${uploadable.length} file${uploadable.length === 1 ? '' : 's'}`)
      navigate({ to: '/notes/$slug', params: { slug: note.slug } })
    } catch {
      toast.error('Upload failed — try again.')
    }
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
                <DropdownMenu>
                  <DropdownMenuTrigger asChild>
                    <Button size="sm" variant="outline" disabled={createNote.isPending}>
                      <Plus className="size-4" /> New
                    </Button>
                  </DropdownMenuTrigger>
                  <DropdownMenuContent align="end" className="w-48">
                    <DropdownMenuItem onSelect={() => openNewNote('')}>
                      <FilePlus2 className="size-4" /> New note
                    </DropdownMenuItem>
                    <DropdownMenuItem onSelect={() => openNewFolder('')}>
                      <FolderPlus className="size-4" /> New folder
                    </DropdownMenuItem>
                    <DropdownMenuSeparator />
                    <DropdownMenuItem onSelect={() => openUploadFiles('')}>
                      <Upload className="size-4" /> Upload files
                    </DropdownMenuItem>
                  </DropdownMenuContent>
                </DropdownMenu>
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
              <FolderTree
                tree={tree}
                activeSlug={params.slug}
                onCreateNote={openNewNote}
                onCreateFolder={openNewFolder}
                onUploadFiles={openUploadFiles}
              />
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
      <input
        ref={uploadInputRef}
        type="file"
        multiple
        className="hidden"
        onChange={(event) => {
          const files = Array.from(event.currentTarget.files ?? [])
          event.currentTarget.value = ''
          if (files.length > 0) void addFilesToFolder(uploadFolderPathRef.current, files)
        }}
      />
      <Dialog
        open={Boolean(createDialog)}
        onOpenChange={(open) => {
          if (!open) closeCreateDialog()
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{createDialog?.kind === 'folder' ? 'New Folder' : 'New Note'}</DialogTitle>
            <DialogDescription>
              {createDialog?.kind === 'folder'
                ? `Parent: ${createDialog.parentPath || 'Root'}`
                : `Folder: ${createDialog?.folderPath || 'Root'}`}
            </DialogDescription>
          </DialogHeader>
          <form
            className="space-y-4"
            onSubmit={(event) => {
              event.preventDefault()
              submitNewNote()
            }}
          >
            {createDialog?.kind === 'folder' ? (
              <>
                <div className="space-y-2">
                  <Label htmlFor="new-folder-name">Folder name</Label>
                  <Input
                    id="new-folder-name"
                    autoFocus
                    value={newFolderName}
                    onChange={(event) => setNewFolderName(event.target.value)}
                    placeholder="Folder name"
                  />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="new-folder-note-title">First note</Label>
                  <Input
                    id="new-folder-note-title"
                    value={newFolderNoteTitle}
                    onChange={(event) => setNewFolderNoteTitle(event.target.value)}
                    placeholder="Index"
                  />
                </div>
              </>
            ) : (
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
            )}
            <DialogFooter>
              <Button
                type="button"
                variant="outline"
                onClick={closeCreateDialog}
                disabled={createNote.isPending}
              >
                Cancel
              </Button>
              <Button
                type="submit"
                disabled={
                  createNote.isPending ||
                  (createDialog?.kind === 'note' ? !newTitle.trim() : !cleanFolderPath(newFolderName))
                }
              >
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

function normalizeFolderPath(path: string): string {
  return cleanFolderPath(path)
}

function cleanFolderPath(path: string): string {
  return path
    .replaceAll('\\', '/')
    .split('/')
    .map((segment) => segment.trim())
    .filter(Boolean)
    .join('/')
}

function joinFolderPath(parentPath: string, childPath: string): string {
  return [normalizeFolderPath(parentPath), cleanFolderPath(childPath)].filter(Boolean).join('/')
}

function titleFromFilename(filename: string): string {
  const clean = filename.trim() || 'Uploaded file'
  const dot = clean.lastIndexOf('.')
  return dot > 0 ? clean.slice(0, dot) : clean
}

function todayStamp(): string {
  return new Date().toISOString().slice(0, 10)
}
