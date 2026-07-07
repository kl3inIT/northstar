import { RangeSetBuilder } from '@codemirror/state'
import {
  Decoration,
  type DecorationSet,
  EditorView,
  ViewPlugin,
  type ViewUpdate,
  WidgetType,
} from '@codemirror/view'

// A GFM task item: a bullet, then `[ ]` / `[x]`. The bracket group starts right
// after the captured prefix so we know exactly where the state char lives.
const TASK = /^(\s*[-*+]\s+)\[([ xX])\]/

/**
 * A real checkbox rendered in place of the `[ ]` / `[x]` source. Toggling it
 * flips the single state char in the document — exact and lossless (the editor
 * IS the Markdown), so no index/position mapping guesswork. Modeled on how
 * Obsidian's live preview and usememos/memos surface tasks in the editor.
 */
class CheckboxWidget extends WidgetType {
  readonly checked: boolean
  readonly innerPos: number

  constructor(checked: boolean, innerPos: number) {
    super()
    this.checked = checked
    this.innerPos = innerPos
  }

  eq(other: CheckboxWidget) {
    return other.checked === this.checked && other.innerPos === this.innerPos
  }

  toDOM(view: EditorView) {
    const box = document.createElement('input')
    box.type = 'checkbox'
    box.checked = this.checked
    box.className = 'cm-task-checkbox'
    box.setAttribute('aria-label', 'Toggle task')
    // mousedown (not click) + preventDefault so the editor doesn't also move the
    // caret into the now-atomic widget; flip the state char under it.
    box.addEventListener('mousedown', (e) => {
      e.preventDefault()
      view.dispatch({
        changes: { from: this.innerPos, to: this.innerPos + 1, insert: this.checked ? ' ' : 'x' },
      })
    })
    return box
  }

  ignoreEvent() {
    return true
  }
}

function build(view: EditorView): DecorationSet {
  const builder = new RangeSetBuilder<Decoration>()
  for (const { from, to } of view.visibleRanges) {
    const startLine = view.state.doc.lineAt(from).number
    const endLine = view.state.doc.lineAt(to).number
    for (let n = startLine; n <= endLine; n++) {
      const line = view.state.doc.line(n)
      const m = TASK.exec(line.text)
      if (!m) continue
      const bracketStart = line.from + m[1].length // position of '['
      const checked = m[2].toLowerCase() === 'x'
      builder.add(
        bracketStart,
        bracketStart + 3, // replace the whole `[ ]` / `[x]`
        Decoration.replace({ widget: new CheckboxWidget(checked, bracketStart + 1) }),
      )
    }
  }
  return builder.finish()
}

/** Renders `- [ ]` / `- [x]` as clickable checkboxes that toggle the source. */
export const taskCheckboxes = ViewPlugin.fromClass(
  class {
    decorations: DecorationSet
    constructor(view: EditorView) {
      this.decorations = build(view)
    }
    update(u: ViewUpdate) {
      if (u.docChanged || u.viewportChanged) {
        this.decorations = build(u.view)
      }
    }
  },
  {
    decorations: (v) => v.decorations,
    // The replaced bracket is atomic so the caret steps over it as one unit.
    provide: (plugin) =>
      EditorView.atomicRanges.of((view) => view.plugin(plugin)?.decorations ?? Decoration.none),
  },
)
