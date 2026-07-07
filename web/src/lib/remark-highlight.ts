import { visit } from 'unist-util-visit'

// ==text== → a <mark>. `emphasis` with an hName override renders as <mark>
// (not <em>), reusing a node type mdast-util-to-hast already knows.
const HIGHLIGHT = /==([^=]+)==/g

/** remark plugin: turn `==text==` into highlighted <mark> spans. */
export function remarkHighlight() {
  // The mdast tree types would pull in @types/mdast; keep it loose and local.
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  return (tree: any) => {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    visit(tree, 'text', (node: any, index: number | undefined, parent: any) => {
      if (parent == null || index == null || typeof node.value !== 'string' || !node.value.includes('==')) {
        return
      }
      const value: string = node.value
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      const out: any[] = []
      let last = 0
      HIGHLIGHT.lastIndex = 0
      let m: RegExpExecArray | null
      while ((m = HIGHLIGHT.exec(value)) !== null) {
        if (m.index > last) out.push({ type: 'text', value: value.slice(last, m.index) })
        out.push({ type: 'emphasis', data: { hName: 'mark' }, children: [{ type: 'text', value: m[1] }] })
        last = m.index + m[0].length
      }
      if (out.length === 0) return
      if (last < value.length) out.push({ type: 'text', value: value.slice(last) })
      parent.children.splice(index, 1, ...out)
      return index + out.length // continue after the inserted nodes
    })
  }
}
