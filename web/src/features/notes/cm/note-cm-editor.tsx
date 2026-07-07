import { EditorSelection, EditorState } from '@codemirror/state'
import { EditorView } from '@codemirror/view'
import { forwardRef, useEffect, useImperativeHandle, useLayoutEffect, useRef } from 'react'
import { cn } from '@/lib/utils'
import './editor.css'
import { buildEditorExtensions } from './extensions'

export interface NoteCmEditorHandle {
  /** Insert text at the caret (used by the async file-upload flow). */
  insertAtCursor: (text: string) => void
  focus: () => void
}

interface NoteCmEditorProps {
  value: string
  onChange: (markdown: string) => void
  /** Files pasted or dropped into the editor — the host uploads and inserts links. */
  onFiles: (files: File[]) => void
  /** Current note titles, for `[[` autocomplete. */
  noteTitles: string[]
  /** Cmd/Ctrl+click on a [[wiki link]]. */
  onOpenWikiLink: (title: string) => void
  placeholder: string
  className?: string
}

/**
 * The note body as a CodeMirror 6 "decorated source" editor: the buffer is raw
 * Markdown verbatim, styled in place (headings, emphasis, [[wiki links]]) while
 * markers stay visible-but-muted. Because the value IS the Markdown string,
 * every edit round-trips losslessly — no WYSIWYG serialization that could mangle
 * links or break the AI index. Modeled on usememos/memos (MIT).
 */
export const NoteCmEditor = forwardRef<NoteCmEditorHandle, NoteCmEditorProps>(function NoteCmEditor(
  { value, onChange, onFiles, noteTitles, onOpenWikiLink, placeholder, className },
  ref,
) {
  const hostRef = useRef<HTMLDivElement>(null)
  const viewRef = useRef<EditorView | null>(null)
  // Latest callbacks/data behind refs so the view mounts once and never rebuilds.
  const onChangeRef = useRef(onChange)
  onChangeRef.current = onChange
  const onOpenRef = useRef(onOpenWikiLink)
  onOpenRef.current = onOpenWikiLink
  const titlesRef = useRef(noteTitles)
  titlesRef.current = noteTitles

  // useLayoutEffect so the EditorView (and its placeholder) mount before paint,
  // avoiding a one-frame empty-host flicker.
  useLayoutEffect(() => {
    if (!hostRef.current) return
    const view = new EditorView({
      state: EditorState.create({
        doc: value,
        extensions: buildEditorExtensions({
          placeholder,
          onChange: (md) => onChangeRef.current(md),
          getNoteTitles: () => titlesRef.current,
          onOpenWikiLink: (title) => onOpenRef.current(title),
        }),
      }),
      parent: hostRef.current,
    })
    viewRef.current = view
    return () => {
      view.destroy()
      viewRef.current = null
    }
    // Mount once; external value is synced in the effect below.
  }, [])

  // Push external value changes (e.g. loading a different note) into the view
  // without wiping it on every keystroke echo.
  useEffect(() => {
    const view = viewRef.current
    if (!view || view.state.doc.toString() === value) return
    view.dispatch({ changes: { from: 0, to: view.state.doc.length, insert: value } })
  }, [value])

  useImperativeHandle(ref, () => ({
    insertAtCursor: (text: string) => {
      const view = viewRef.current
      if (!view) return
      const { from, to } = view.state.selection.main
      view.dispatch({
        changes: { from, to, insert: text },
        selection: EditorSelection.cursor(from + text.length),
        scrollIntoView: true,
      })
      view.focus()
    },
    focus: () => viewRef.current?.focus(),
  }))

  function filesFrom(list: FileList | null | undefined): File[] {
    return list ? [...list] : []
  }

  return (
    <div
      ref={hostRef}
      className={cn('note-cm w-full', className)}
      onPaste={(e) => {
        const files = filesFrom(e.clipboardData.files)
        if (files.length > 0) {
          e.preventDefault()
          onFiles(files)
        }
      }}
      onDrop={(e) => {
        const files = filesFrom(e.dataTransfer.files)
        if (files.length > 0) {
          e.preventDefault()
          onFiles(files)
        }
      }}
    />
  )
})
