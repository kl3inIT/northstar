import { defaultKeymap, history, historyKeymap, indentWithTab } from '@codemirror/commands'
import { markdown } from '@codemirror/lang-markdown'
import { indentUnit } from '@codemirror/language'
import { EditorState, type Extension } from '@codemirror/state'
import { GFM } from '@lezer/markdown'
import {
  placeholder as cmPlaceholder,
  drawSelection,
  dropCursor,
  EditorView,
  type KeyBinding,
  keymap,
} from '@codemirror/view'
import { headingDecorations } from './heading-decorations'
import { taskCheckboxes } from './task-checkbox'
import { noteEditorTheme } from './theme'
import { wikiLinkAutocomplete } from './wiki-link-autocomplete'
import { wikiLinkDecorations, wikiTitleAt } from './wiki-link-decorations'

export interface EditorExtensionsOptions {
  placeholder: string
  onChange: (markdown: string) => void
  getNoteTitles: () => string[]
  onOpenWikiLink: (title: string) => void
}

// Escape blurs the editor so keyboard users keep an exit from the Tab-trapping
// editor (the autocomplete popup's own Escape wins first while it is open).
const editorKeys: KeyBinding[] = [
  {
    key: 'Escape',
    run: (view) => {
      view.contentDOM.blur()
      return true
    },
  },
]

export function buildEditorExtensions({
  placeholder,
  onChange,
  getNoteTitles,
  onOpenWikiLink,
}: EditorExtensionsOptions): Extension[] {
  return [
    // basicSetup bundles these; we assemble the ones we want by hand. Without
    // them: no visible caret (drawSelection), no undo/redo (history), and
    // Enter/selection/word-motion keys are unwired (defaultKeymap).
    history(),
    drawSelection(),
    dropCursor(),
    EditorState.allowMultipleSelections.of(true),
    indentUnit.of('  '),
    markdown({ extensions: [GFM] }),
    ...noteEditorTheme,
    EditorView.lineWrapping,
    cmPlaceholder(placeholder),
    wikiLinkDecorations,
    headingDecorations,
    taskCheckboxes,
    // Cmd/Ctrl+click a [[wiki link]] → open the linked note (like Obsidian).
    EditorView.domEventHandlers({
      mousedown(event, view) {
        if (!(event.metaKey || event.ctrlKey)) return false
        const pos = view.posAtCoords({ x: event.clientX, y: event.clientY })
        if (pos == null) return false
        const title = wikiTitleAt(view, pos)
        if (!title) return false
        event.preventDefault()
        onOpenWikiLink(title)
        return true
      },
    }),
    // Must precede the editing keymap so the completion popup's Enter/Tab/arrow
    // bindings win while it is open.
    wikiLinkAutocomplete(getNoteTitles),
    keymap.of([...editorKeys, indentWithTab, ...defaultKeymap, ...historyKeymap]),
    EditorView.updateListener.of((u) => {
      if (u.docChanged) onChange(u.state.doc.toString())
    }),
  ]
}
