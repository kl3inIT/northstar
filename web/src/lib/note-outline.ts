import GithubSlugger from 'github-slugger'

export interface OutlineItem {
  id: string
  text: string
  level: number
}

const HEADING = /^(#{1,6})\s+(.+?)\s*#*\s*$/
const WIKI = /\[\[\s*([^\]|]+?)\s*(?:\|\s*([^\]]*?)\s*)?\]\]/g
const MD_LINK = /\[([^\]]+)\]\([^)]*\)/g
const INLINE_MARK = /[*_`~]/g

/**
 * Plain-text heading label for the outline — strips the inline Markdown a
 * heading might carry so the label reads cleanly AND its slug matches what
 * rehype-slug generates from the rendered heading text (same github-slugger),
 * so clicking an item scrolls to the right section.
 */
function headingText(raw: string): string {
  return raw
    .replace(WIKI, (_m, title: string, alias?: string) => (alias?.trim() || title.trim()))
    .replace(MD_LINK, '$1')
    .replace(INLINE_MARK, '')
    .trim()
}

/** Parse a note's ATX headings into a flat outline, ids matching rehype-slug. */
export function noteOutline(markdown: string): OutlineItem[] {
  const slugger = new GithubSlugger()
  const items: OutlineItem[] = []
  for (const line of markdown.split('\n')) {
    const m = HEADING.exec(line)
    if (!m) continue
    const text = headingText(m[2])
    if (!text) continue
    items.push({ level: m[1].length, text, id: slugger.slug(text) })
  }
  return items
}
