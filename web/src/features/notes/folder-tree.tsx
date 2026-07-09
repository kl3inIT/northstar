import { useNavigate } from '@tanstack/react-router'
import { ChevronRight, FilePlus2, FileText, Folder, FolderPlus, MoreHorizontal, Upload } from 'lucide-react'
import { useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react'
import {
  Tree,
  type MoveHandler,
  type NodeApi,
  type NodeRendererProps,
  type RowRendererProps,
  type TreeApi,
} from 'react-arborist'
import { toast } from 'sonner'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import type { TreeFolder } from '@/lib/folder-tree'
import { useMoveNoteToFolder } from '@/lib/notes-api'
import type { NoteSummary } from '@/lib/notes-types'
import { cn } from '@/lib/utils'

const INDENT = 12
const ROW_HEIGHT = 28

type FolderNodeData = {
  id: string
  kind: 'folder'
  name: string
  path: string
  count: number
  children: ArboristNode[]
}

type NoteNodeData = {
  id: string
  kind: 'note'
  name: string
  note: NoteSummary
}

type ArboristNode = FolderNodeData | NoteNodeData

export function FolderTree({
  tree,
  activeSlug,
  dragDisabled = false,
  onCreateNote,
  onCreateFolder,
  onUploadFiles,
}: {
  tree: TreeFolder
  activeSlug?: string
  dragDisabled?: boolean
  onCreateNote?: (folderPath: string) => void
  onCreateFolder?: (parentPath: string) => void
  onUploadFiles?: (folderPath: string) => void
}) {
  const navigate = useNavigate()
  const moveNote = useMoveNoteToFolder()
  const treeRef = useRef<TreeApi<ArboristNode> | undefined>(undefined)
  const [containerRef, size] = useMeasuredElement()
  const nodes = useMemo(() => toArboristNodes(tree), [tree])
  const initialOpenState = useMemo(() => topLevelOpenState(tree), [tree])
  const selectedId = activeSlug ? noteId(activeSlug) : undefined

  useEffect(() => {
    if (selectedId) void treeRef.current?.scrollTo(selectedId)
  }, [nodes, selectedId])

  if (tree.folders.length === 0 && tree.notes.length === 0) {
    return <p className="px-3 py-2 text-sm text-muted-foreground">No notes yet.</p>
  }

  const handleActivate = (node: NodeApi<ArboristNode>) => {
    if (node.data.kind === 'folder') {
      node.toggle()
      return
    }
    navigate({ to: '/notes/$slug', params: { slug: node.data.note.slug } })
  }

  const handleMove: MoveHandler<ArboristNode> = async ({ dragNodes, parentNode }) => {
    const destination = parentNode && !parentNode.isRoot && parentNode.data.kind === 'folder'
      ? parentNode.data.path
      : ''
    const notes = dragNodes
      .map((node) => node.data)
      .filter(isNoteNode)
      .map((node) => node.note)
      .filter((note) => note.folderPath !== destination)

    if (notes.length === 0) return

    try {
      await Promise.all(notes.map((note) => moveNote.mutateAsync({ note, folderPath: destination })))
      toast.success(
        notes.length === 1
          ? `Moved "${notes[0].title}" to ${destination || 'Root'}`
          : `Moved ${notes.length} notes to ${destination || 'Root'}`,
      )
    } catch {
      toast.error('Could not move note. Refresh and try again.')
    }
  }

  const disableTreeDrop = ({
    parentNode,
    dragNodes,
  }: {
    parentNode: NodeApi<ArboristNode>
    dragNodes: NodeApi<ArboristNode>[]
  }) =>
    dragDisabled ||
    moveNote.isPending ||
    !dragNodes.every((node) => node.data.kind === 'note') ||
    (!parentNode.isRoot && parentNode.data.kind !== 'folder')

  const renderNode = (props: NodeRendererProps<ArboristNode>) => (
    <TreeNode
      {...props}
      onCreateNote={onCreateNote}
      onCreateFolder={onCreateFolder}
      onUploadFiles={onUploadFiles}
    />
  )

  return (
    <div ref={containerRef} className="h-full min-h-0 text-sm">
      {size.height > 0 && size.width > 0 && (
        <Tree
          ref={treeRef}
          data={nodes}
          idAccessor={(node) => node.id}
          childrenAccessor={(node) => (node.kind === 'folder' ? node.children : null)}
          renderRow={TreeRow}
          rowHeight={ROW_HEIGHT}
          indent={INDENT}
          overscanCount={18}
          width={size.width}
          height={size.height}
          selection={selectedId}
          initialOpenState={initialOpenState}
          openByDefault={false}
          disableMultiSelection
          disableEdit
          disableSelect={(node) => node.kind === 'folder'}
          disableDrag={(node) => dragDisabled || moveNote.isPending || node.kind === 'folder'}
          disableDrop={disableTreeDrop}
          onActivate={handleActivate}
          onMove={handleMove}
          aria-label="Notes folder tree"
          className="outline-none"
        >
          {renderNode}
        </Tree>
      )}
    </div>
  )
}

function TreeNode({
  node,
  style,
  dragHandle,
  onCreateNote,
  onCreateFolder,
  onUploadFiles,
}: NodeRendererProps<ArboristNode> & {
  onCreateNote?: (folderPath: string) => void
  onCreateFolder?: (parentPath: string) => void
  onUploadFiles?: (folderPath: string) => void
}) {
  const data = node.data
  const isFolder = data.kind === 'folder'
  const canMutateFolder = isFolder && Boolean(onCreateNote || onCreateFolder || onUploadFiles)

  return (
    <div
      ref={dragHandle}
      style={style}
      className={cn(
        'group/tree-row flex h-full min-w-0 items-center gap-1.5 rounded-md pr-1',
        isFolder ? 'font-medium' : 'cursor-grab active:cursor-grabbing',
        node.isSelected && 'bg-primary/10 font-medium text-primary',
        node.willReceiveDrop && 'bg-primary/15 ring-1 ring-primary/30',
        node.isDragging && 'opacity-50',
      )}
      title={isFolder ? data.path : data.note.title}
    >
      {isFolder ? (
        <button
          type="button"
          aria-label={`${node.isOpen ? 'Collapse' : 'Expand'} ${data.name}`}
          onClick={(event) => {
            event.stopPropagation()
            node.toggle()
          }}
          className="grid size-4 shrink-0 place-items-center rounded-sm text-muted-foreground hover:bg-accent"
        >
          <ChevronRight
            className={cn('size-3.5 transition-transform', node.isOpen && 'rotate-90')}
          />
        </button>
      ) : (
        <span className="size-4 shrink-0" />
      )}

      {isFolder ? (
        <Folder className="size-4 shrink-0 text-muted-foreground" />
      ) : (
        <FileText
          className={cn('size-4 shrink-0', node.isSelected ? 'text-primary' : 'text-muted-foreground')}
        />
      )}

      <span className="min-w-0 flex-1 truncate">{data.name}</span>

      {isFolder && (
        <span className="shrink-0 text-[11px] font-normal text-muted-foreground">{data.count}</span>
      )}

      {canMutateFolder && (
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <button
              type="button"
              aria-label={`Folder actions for ${data.path}`}
              title="Folder actions"
              onClick={(event) => event.stopPropagation()}
              onPointerDown={(event) => event.stopPropagation()}
              className="grid size-6 shrink-0 place-items-center rounded-md text-muted-foreground opacity-0 transition-opacity hover:bg-accent hover:text-foreground focus-visible:opacity-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring group-hover/tree-row:opacity-100"
            >
              <MoreHorizontal className="size-4" />
            </button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end" className="w-48">
            <DropdownMenuLabel className="truncate text-xs text-muted-foreground">
              {data.path}
            </DropdownMenuLabel>
            {onCreateNote && (
              <DropdownMenuItem onSelect={() => onCreateNote(data.path)}>
                <FilePlus2 className="size-4" /> New note
              </DropdownMenuItem>
            )}
            {onCreateFolder && (
              <DropdownMenuItem onSelect={() => onCreateFolder(data.path)}>
                <FolderPlus className="size-4" /> New folder
              </DropdownMenuItem>
            )}
            {onUploadFiles && (
              <>
                <DropdownMenuSeparator />
                <DropdownMenuItem onSelect={() => onUploadFiles(data.path)}>
                  <Upload className="size-4" /> Upload files
                </DropdownMenuItem>
              </>
            )}
          </DropdownMenuContent>
        </DropdownMenu>
      )}
    </div>
  )
}

function TreeRow({ node, attrs, innerRef, children }: RowRendererProps<ArboristNode>) {
  const { className, style, ...rest } = attrs
  return (
    <div
      {...rest}
      ref={innerRef}
      onFocus={(event) => event.stopPropagation()}
      onClick={node.handleClick}
      className={cn('outline-none', className)}
      style={{ ...style, minWidth: 0, width: '100%' }}
    >
      {children}
    </div>
  )
}

function toArboristNodes(tree: TreeFolder): ArboristNode[] {
  return [...tree.folders.map(toFolderNode), ...tree.notes.map(toNoteNode)]
}

function toFolderNode(folder: TreeFolder): FolderNodeData {
  return {
    id: folderId(folder.path),
    kind: 'folder',
    name: folder.name,
    path: folder.path,
    count: folderNoteCount(folder),
    children: [...folder.folders.map(toFolderNode), ...folder.notes.map(toNoteNode)],
  }
}

function toNoteNode(note: NoteSummary): NoteNodeData {
  return {
    id: noteId(note.slug),
    kind: 'note',
    name: note.title,
    note,
  }
}

function topLevelOpenState(tree: TreeFolder) {
  return Object.fromEntries(tree.folders.map((folder) => [folderId(folder.path), true]))
}

function folderId(path: string) {
  return `folder:${path}`
}

function noteId(slug: string) {
  return `note:${slug}`
}

function isNoteNode(node: ArboristNode): node is NoteNodeData {
  return node.kind === 'note'
}

function folderNoteCount(folder: TreeFolder): number {
  return folder.notes.length + folder.folders.reduce((sum, child) => sum + folderNoteCount(child), 0)
}

function useMeasuredElement() {
  const ref = useRef<HTMLDivElement | null>(null)
  const [size, setSize] = useState({ width: 0, height: 0 })

  useLayoutEffect(() => {
    const element = ref.current
    if (!element) return

    const update = () => {
      setSize({ width: element.clientWidth, height: element.clientHeight })
    }

    update()
    const observer = new ResizeObserver(update)
    observer.observe(element)
    return () => observer.disconnect()
  }, [])

  return [ref, size] as const
}
