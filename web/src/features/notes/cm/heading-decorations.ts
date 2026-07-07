import { RangeSetBuilder } from '@codemirror/state'
import { Decoration, type DecorationSet, EditorView } from '@codemirror/view'
import { viewportDecorations } from './viewport-decorations'

// A heading line: 1–6 hashes followed by a space (ATX; a space is required so a
// bare `#tag`-style token at line start is not styled as a heading).
const HEADING_LINE = /^(#{1,6})\s/

const lineDecorations = [1, 2, 3, 4, 5, 6].map((level) => Decoration.line({ class: `cm-md-h${level}` }))

function build(view: EditorView): DecorationSet {
  const builder = new RangeSetBuilder<Decoration>()
  for (const { from, to } of view.visibleRanges) {
    const startLine = view.state.doc.lineAt(from).number
    const endLine = view.state.doc.lineAt(to).number
    for (let n = startLine; n <= endLine; n++) {
      const line = view.state.doc.line(n)
      const m = HEADING_LINE.exec(line.text)
      if (m) {
        builder.add(line.from, line.from, lineDecorations[m[1].length - 1])
      }
    }
  }
  return builder.finish()
}

/** Sizes heading lines (`.cm-md-h1`…`h6`) so they read like the rendered view. */
export const headingDecorations = viewportDecorations(build)
