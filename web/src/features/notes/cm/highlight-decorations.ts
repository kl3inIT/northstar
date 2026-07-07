import { RangeSetBuilder } from '@codemirror/state'
import { Decoration, type DecorationSet, EditorView } from '@codemirror/view'
import { viewportDecorations } from './viewport-decorations'

// ==text== highlight, mirroring the reading view's <mark> (see remark-highlight).
const HL_RE = /==([^=]+)==/g
const hlMark = Decoration.mark({ class: 'cm-highlight' })

function build(view: EditorView): DecorationSet {
  const builder = new RangeSetBuilder<Decoration>()
  for (const { from, to } of view.visibleRanges) {
    const text = view.state.doc.sliceString(from, to)
    HL_RE.lastIndex = 0
    let m: RegExpExecArray | null
    while ((m = HL_RE.exec(text)) !== null) {
      builder.add(from + m.index, from + m.index + m[0].length, hlMark)
    }
  }
  return builder.finish()
}

/** Tints `==highlighted==` runs in the editor to match the reading view. */
export const highlightDecorations = viewportDecorations(build)
