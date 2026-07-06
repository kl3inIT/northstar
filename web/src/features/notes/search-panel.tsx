import { Link } from '@tanstack/react-router'
import { Fragment } from 'react'
import { Card } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { useNotes } from '@/lib/notes-api'
import type { NoteSummary } from '@/lib/notes-types'
import { useDebouncedValue } from '@/lib/use-debounced-value'
import { cn } from '@/lib/utils'

/**
 * Full-text search results (Postgres tsvector, ranked). The snippet arrives with
 * literal {@code <mark>} markers around matched words; we split on them and render
 * highlights as React elements — never innerHTML.
 */
export function SearchPanel({ query, activeSlug }: { query: string; activeSlug?: string }) {
  const debounced = useDebouncedValue(query.trim())
  const { data: results = [], isLoading, isFetching } = useNotes(debounced)

  if (!debounced) {
    return (
      <p className="px-3 py-2 text-sm text-muted-foreground">
        Search across all note contents — type keywords, "quoted phrases", or -exclusions.
      </p>
    )
  }
  if (isLoading) {
    return (
      <div className="space-y-2 px-1 py-1">
        <Skeleton className="h-20 w-full" />
        <Skeleton className="h-20 w-full" />
      </div>
    )
  }
  if (results.length === 0) {
    return <p className="px-3 py-2 text-sm text-muted-foreground">No results for “{debounced}”.</p>
  }

  return (
    <div className="space-y-2 px-1 py-1">
      {results.map((note) => (
        <ResultCard key={note.id} note={note} active={note.slug === activeSlug} />
      ))}
      <p className={cn('px-2 pt-1 text-xs text-muted-foreground', isFetching && 'opacity-60')}>
        {results.length} results · ranked by relevance
      </p>
    </div>
  )
}

function ResultCard({ note, active }: { note: NoteSummary; active: boolean }) {
  return (
    <Link to="/notes/$slug" params={{ slug: note.slug }} className="block">
      <Card className={cn('gap-1.5 p-3 transition-colors hover:bg-accent', active && 'border-primary/40 bg-primary/5')}>
        <div className="truncate text-sm font-medium">{note.title}</div>
        {note.snippet && (
          <p className="line-clamp-2 text-xs text-muted-foreground">
            <HighlightedSnippet text={note.snippet} />
          </p>
        )}
        {note.folderPath && (
          <div className="text-xs text-muted-foreground/70">{note.folderPath.split('/').join(' / ')}</div>
        )}
      </Card>
    </Link>
  )
}

/** Split on the <mark>…</mark> markers from ts_headline; odd segments are matches. */
function HighlightedSnippet({ text }: { text: string }) {
  const parts = text.split(/<\/?mark>/)
  return (
    <>
      {parts.map((part, i) =>
        i % 2 === 1 ? (
          <mark key={i} className="rounded-sm bg-primary/15 px-0.5 font-medium text-primary">
            {part}
          </mark>
        ) : (
          <Fragment key={i}>{part}</Fragment>
        ),
      )}
    </>
  )
}
