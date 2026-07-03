import { Link } from '@tanstack/react-router'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import type { NoteRef } from '@/lib/notes-types'

const WIKI = /\[\[\s*([^\]|]+?)\s*(?:\|\s*([^\]]*?)\s*)?\]\]/g

/** Rewrite [[Title]] / [[Title|alias]] into markdown links with a wiki: scheme. */
function linkifyWiki(markdown: string): string {
  return markdown.replace(WIKI, (_match, title: string, alias?: string) => {
    const label = (alias && alias.trim()) || title.trim()
    return `[${label}](wiki:${encodeURIComponent(title.trim())})`
  })
}

export function MarkdownBody({ content, links }: { content: string; links: NoteRef[] }) {
  const byTitle = new Map(links.map((link) => [link.title.toLowerCase(), link]))

  return (
    <div className="prose prose-zinc max-w-none prose-headings:font-semibold prose-a:font-medium">
      <ReactMarkdown
        remarkPlugins={[remarkGfm]}
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
                  title="Note chưa tồn tại"
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
      </ReactMarkdown>
    </div>
  )
}
