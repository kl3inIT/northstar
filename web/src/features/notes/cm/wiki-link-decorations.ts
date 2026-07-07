import { RangeSetBuilder } from '@codemirror/state'
import { Decoration, type DecorationSet, EditorView } from '@codemirror/view'
import { viewportDecorations } from './viewport-decorations'

// [[Title]] or [[Title|alias]] — mirrors the reading view's grammar
// (components/markdown-body.tsx) so the editor and render agree on what a link is.
const WIKI_RE = /\[\[\s*([^\]|]+?)\s*(?:\|\s*[^\]]*?\s*)?\]\]/g
const wikiMark = Decoration.mark({ class: 'cm-wikilink' })

function build(view: EditorView): DecorationSet {
  const builder = new RangeSetBuilder<Decoration>()
  for (const { from, to } of view.visibleRanges) {
    const text = view.state.doc.sliceString(from, to)
    WIKI_RE.lastIndex = 0
    let m: RegExpExecArray | null
    while ((m = WIKI_RE.exec(text)) !== null) {
      builder.add(from + m.index, from + m.index + m[0].length, wikiMark)
    }
  }
  return builder.finish()
}

/** Styles `[[wiki links]]` as chips (see `.cm-wikilink` in editor.css). */
export const wikiLinkDecorations = viewportDecorations(build)

/**
 * The wiki-link title under a document position, or null. Used by the editor's
 * Cmd/Ctrl+click handler to open the linked note. Scans only the clicked line.
 */
export function wikiTitleAt(view: EditorView, pos: number): string | null {
  const line = view.state.doc.lineAt(pos)
  const col = pos - line.from
  WIKI_RE.lastIndex = 0
  let m: RegExpExecArray | null
  while ((m = WIKI_RE.exec(line.text)) !== null) {
    if (col >= m.index && col <= m.index + m[0].length) {
      return m[1].trim()
    }
  }
  return null
}
