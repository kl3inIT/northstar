import { syntaxHighlighting } from '@codemirror/language'
import { tags as t, tagHighlighter } from '@lezer/highlight'

/**
 * Maps markdown syntax tokens to stable class names; ALL visual styling lives in
 * plain CSS (`editor.css`, scoped to `.note-cm`) so the editor is themed with the
 * app's theme tokens — the same model Obsidian / GitHub use for an app-integrated
 * CM6 editor (class-based tokens + CSS, not a CSS-in-JS theme).
 *
 * Markers (`#`, `**`, backticks, `>`) get `.cm-md-mark` and are muted so the
 * styled text they wrap leads — the "decorated source" feel. Heading line
 * classes come from {@link ./heading-decorations}, wiki links from
 * {@link ./wiki-link-decorations}. Adapted from usememos/memos (MIT).
 */
const markdownHighlighter = tagHighlighter([
  { tag: t.strong, class: 'cm-md-strong' },
  { tag: t.emphasis, class: 'cm-md-emphasis' },
  { tag: t.strikethrough, class: 'cm-md-strike' },
  { tag: t.monospace, class: 'cm-md-code' },
  { tag: t.link, class: 'cm-md-link' },
  { tag: t.url, class: 'cm-md-url' },
  { tag: t.quote, class: 'cm-md-quote' },
  { tag: [t.processingInstruction, t.meta, t.contentSeparator], class: 'cm-md-mark' },
])

export const noteEditorTheme = [syntaxHighlighting(markdownHighlighter)]
