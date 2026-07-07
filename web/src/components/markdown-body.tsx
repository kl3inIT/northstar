import { Link } from '@tanstack/react-router'
import { cjk } from '@streamdown/cjk'
import { code } from '@streamdown/code'
import { math } from '@streamdown/math'
import { mermaid } from '@streamdown/mermaid'
import rehypeSlug from 'rehype-slug'
import { defaultRehypePlugins, defaultRemarkPlugins, Streamdown } from 'streamdown'
import { remarkHighlight } from '@/lib/remark-highlight'
import type { NoteRef } from '@/lib/notes-types'

const WIKI = /\[\[\s*([^\]|]+?)\s*(?:\|\s*([^\]]*?)\s*)?\]\]/g

/** Rewrite [[Title]] / [[Title|alias]] into markdown links with a wiki: scheme. */
function linkifyWiki(markdown: string): string {
  return markdown.replace(WIKI, (_match, title: string, alias?: string) => {
    const label = (alias && alias.trim()) || title.trim()
    return `[${label}](wiki:${encodeURIComponent(title.trim())})`
  })
}

// Reuse the same rich-markdown engine as the assistant so notes get syntax-
// highlighted code, KaTeX math and Mermaid diagrams for free.
const plugins = { cjk, code, math, mermaid }

// Preserve the wiki: scheme (react-markdown's default sanitizer would strip it)
// AND keep the sanitizer's protection: only wiki:, safe web schemes, and
// relative paths (attachments, /notes) pass; javascript:/data:/vbscript: and any
// other scheme are neutralized to "" so a link smuggled in via extracted
// document text or pasted content can't run script.
const safeUrl = (url: string): string => {
  if (url.startsWith('wiki:')) return url
  try {
    const u = new URL(url, 'https://northstar.local/')
    if (u.protocol === 'http:' || u.protocol === 'https:' || u.protocol === 'mailto:') return url
  } catch {
    // Unparseable — fall through to neutralized.
  }
  return ''
}

export function MarkdownBody({ content, links }: { content: string; links: NoteRef[] }) {
  const byTitle = new Map(links.map((link) => [link.title.toLowerCase(), link]))

  return (
    <div className="text-[0.95rem] leading-7 [&>*:first-child]:mt-0 [&>*:last-child]:mb-0 [&_mark]:rounded [&_mark]:bg-primary/20 [&_mark]:px-0.5 [&_:is(h1,h2,h3,h4)]:scroll-mt-4">
      <Streamdown
        plugins={plugins}
        parseIncompleteMarkdown={false}
        urlTransform={safeUrl}
        remarkPlugins={[...Object.values(defaultRemarkPlugins), remarkHighlight]}
        rehypePlugins={[...Object.values(defaultRehypePlugins), rehypeSlug]}
        components={{
          a({ href, children }) {
            if (href?.startsWith('wiki:')) {
              const title = decodeURIComponent(href.slice('wiki:'.length))
              const ref = byTitle.get(title.toLowerCase())
              if (ref?.resolved && ref.slug) {
                return (
                  <Link
                    to="/notes/$slug"
                    params={{ slug: ref.slug }}
                    className="rounded bg-primary/10 px-1 py-0.5 text-primary no-underline hover:bg-primary/20"
                  >
                    {children}
                  </Link>
                )
              }
              return (
                <span
                  className="rounded bg-muted px-1 py-0.5 text-muted-foreground underline decoration-dashed underline-offset-2"
                  title="Note does not exist yet"
                >
                  {children}
                </span>
              )
            }
            return (
              <a href={href} target="_blank" rel="noreferrer" className="text-primary">
                {children}
              </a>
            )
          },
        }}
      >
        {linkifyWiki(content)}
      </Streamdown>
    </div>
  )
}
