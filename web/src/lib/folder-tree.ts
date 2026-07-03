import type { NoteSummary } from './notes-types'

/**
 * A node in the derived folder tree. Folders are not stored — they exist exactly
 * while a note sits in them, so the tree is built from the distinct {@code
 * folderPath} values of the notes (Obsidian-style).
 */
export interface TreeFolder {
  name: string
  path: string
  folders: TreeFolder[]
  notes: NoteSummary[]
}

export function buildFolderTree(notes: NoteSummary[]): TreeFolder {
  const root: TreeFolder = { name: '', path: '', folders: [], notes: [] }

  for (const note of notes) {
    const segments = note.folderPath ? note.folderPath.split('/').filter(Boolean) : []
    let current = root
    let path = ''
    for (const segment of segments) {
      path = path ? `${path}/${segment}` : segment
      let child = current.folders.find((f) => f.name === segment)
      if (!child) {
        child = { name: segment, path, folders: [], notes: [] }
        current.folders.push(child)
      }
      current = child
    }
    current.notes.push(note)
  }

  const sort = (folder: TreeFolder) => {
    folder.folders.sort((a, b) => a.name.localeCompare(b.name))
    folder.notes.sort((a, b) => a.title.localeCompare(b.title))
    folder.folders.forEach(sort)
  }
  sort(root)
  return root
}
