import { Link } from '@tanstack/react-router'
import {
  CalendarDays,
  CircleAlert,
  Clock3,
  Newspaper,
  Settings2,
} from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { MarkdownBody } from '@/components/markdown-body'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import { useBriefNotes, useNote } from '@/lib/notes-api'
import type { NoteDetail, NoteStatus, NoteSummary } from '@/lib/notes-types'

const dateTime = new Intl.DateTimeFormat(undefined, {
  dateStyle: 'medium',
  timeStyle: 'short',
})

const issueDate = new Intl.DateTimeFormat(undefined, {
  weekday: 'short',
  day: '2-digit',
  month: 'short',
})

export function BriefsPage() {
  const briefs = useBriefNotes()
  const [selectedSlug, setSelectedSlug] = useState<string>()

  useEffect(() => {
    if (!selectedSlug && briefs.data?.length) setSelectedSlug(briefs.data[0].slug)
    if (selectedSlug && briefs.data && !briefs.data.some((brief) => brief.slug === selectedSlug)) {
      setSelectedSlug(briefs.data[0]?.slug)
    }
  }, [briefs.data, selectedSlug])

  const selectedSummary = useMemo(
    () => briefs.data?.find((brief) => brief.slug === selectedSlug),
    [briefs.data, selectedSlug],
  )
  const selected = useNote(selectedSlug)

  return (
    <main className="min-h-0 min-w-0 flex-1 overflow-auto">
      <div className="mx-auto flex min-h-full w-full max-w-5xl flex-col px-4 py-5 md:px-8 md:py-7">
        <header className="flex items-center justify-between gap-4 border-b pb-5">
          <div className="flex min-w-0 items-center gap-3">
            <span className="grid size-9 shrink-0 place-items-center rounded-md border bg-muted/40">
              <Newspaper className="size-4 text-muted-foreground" />
            </span>
            <div className="min-w-0">
              <h1 className="text-2xl font-semibold">Briefs</h1>
              <p className="text-xs text-muted-foreground">
                {briefs.data?.length ?? 0} {(briefs.data?.length ?? 0) === 1 ? 'issue' : 'issues'}
              </p>
            </div>
          </div>
          <Button asChild variant="outline" size="sm">
            <Link to="/settings">
              <Settings2 />
              Automation
            </Link>
          </Button>
        </header>

        {briefs.isLoading ? (
          <BriefsSkeleton />
        ) : briefs.isError ? (
          <div className="grid flex-1 place-items-center py-20 text-center">
            <div>
              <CircleAlert className="mx-auto size-7 text-destructive" />
              <p className="mt-3 text-sm font-medium">Briefs could not be loaded</p>
              <p className="mt-1 text-xs text-muted-foreground">{briefs.error.message}</p>
            </div>
          </div>
        ) : briefs.data?.length ? (
          <div className="flex-1">
            <div className="flex flex-col gap-2 border-b py-4 sm:flex-row sm:items-center sm:justify-between">
              <Select value={selectedSlug} onValueChange={setSelectedSlug}>
                <SelectTrigger className="w-full sm:w-96" aria-label="Choose brief">
                  <SelectValue placeholder="Choose an issue" />
                </SelectTrigger>
                <SelectContent>
                  {briefs.data.map((brief, index) => (
                    <SelectItem key={brief.id} value={brief.slug}>
                      {index === 0 ? 'Latest · ' : ''}{issueDate.format(new Date(brief.createdAt))} · {issueLabel(brief.title)}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              <span className="text-xs text-muted-foreground">Newest first</span>
            </div>

            <article className="min-w-0 py-8 sm:py-10">
              {selected.isLoading || !selectedSummary ? (
                <ArticleSkeleton />
              ) : selected.isError ? (
                <div className="py-16 text-center">
                  <CircleAlert className="mx-auto size-6 text-destructive" />
                  <p className="mt-3 text-sm">This issue could not be opened.</p>
                </div>
              ) : selected.data ? (
                <BriefArticle note={selected.data} summary={selectedSummary} />
              ) : null}
            </article>
          </div>
        ) : (
          <EmptyBriefs />
        )}
      </div>
    </main>
  )
}

function BriefArticle({ note, summary }: { note: NoteDetail; summary: NoteSummary }) {
  const document = useMemo(() => splitBrief(note.contentMarkdown, summary.title), [note.contentMarkdown, summary.title])

  return (
    <div className="mx-auto max-w-3xl pb-16">
      <header className="mb-9 border-b pb-8">
        <div className="mb-5 flex flex-wrap items-center gap-x-4 gap-y-2 text-xs text-muted-foreground">
          <StatusBadge status={summary.status} />
          <span className="flex items-center gap-1.5">
            <CalendarDays className="size-3.5" />
            {dateTime.format(new Date(summary.createdAt))}
          </span>
          <span className="flex items-center gap-1.5">
            <Clock3 className="size-3.5" />
            Updated {dateTime.format(new Date(summary.updatedAt))}
          </span>
        </div>
        <h2 className="max-w-3xl font-serif text-3xl font-semibold leading-tight sm:text-4xl">
          {document.title}
        </h2>
        {document.subtitle && (
          <p className="mt-3 max-w-2xl text-sm leading-6 text-muted-foreground">{document.subtitle}</p>
        )}
      </header>

      <MarkdownBody
        content={document.body}
        links={note.outgoingLinks}
        className="brief-typography"
      />
    </div>
  )
}

function StatusBadge({ status }: { status: NoteStatus }) {
  return (
    <Badge variant={status === 'STAGING' ? 'secondary' : 'outline'} className="rounded-md">
      {statusLabel(status)}
    </Badge>
  )
}

function statusLabel(status: NoteStatus) {
  if (status === 'STAGING') return 'Awaiting review'
  if (status === 'RESOURCE') return 'Saved'
  return 'Archived'
}

function issueLabel(title: string) {
  return title.replace(/^Morning Brief\s*-\s*/i, '')
}

function splitBrief(content: string, fallbackTitle: string) {
  const lines = content.replace(/^\uFEFF/, '').split(/\r?\n/)
  while (lines[0]?.trim() === '') lines.shift()

  const heading = lines[0]?.match(/^#\s+(.+?)\s*#*\s*$/)
  const title = heading?.[1]?.trim() || issueLabel(fallbackTitle)
  if (heading) lines.shift()
  while (lines[0]?.trim() === '') lines.shift()

  const lead = lines[0]?.trim().match(/^_(.+)_$/)
  const subtitle = lead?.[1]?.trim() ?? ''
  if (lead) lines.shift()
  while (lines[0]?.trim() === '') lines.shift()

  return { title, subtitle, body: lines.join('\n').trim() }
}

function EmptyBriefs() {
  return (
    <div className="grid flex-1 place-items-center py-20">
      <div className="max-w-md text-center">
        <span className="mx-auto grid size-11 place-items-center rounded-md border bg-muted/40">
          <Newspaper className="size-5 text-muted-foreground" />
        </span>
        <h2 className="mt-5 font-serif text-2xl font-semibold">No issues yet</h2>
        <p className="mt-2 text-sm leading-6 text-muted-foreground">
          Completed Morning Brief runs appear here for review.
        </p>
        <Button asChild className="mt-5">
          <Link to="/settings">
            <Settings2 />
            Configure automation
          </Link>
        </Button>
      </div>
    </div>
  )
}

function BriefsSkeleton() {
  return (
    <div className="flex-1 py-8">
      <Skeleton className="mb-8 h-9 w-full max-w-96" />
      <ArticleSkeleton />
    </div>
  )
}

function ArticleSkeleton() {
  return (
    <div className="mx-auto w-full max-w-3xl space-y-5">
      <Skeleton className="h-6 w-40" />
      <Skeleton className="h-12 w-4/5" />
      <Skeleton className="h-4 w-2/3" />
      <Skeleton className="h-px w-full" />
      <Skeleton className="h-40 w-full" />
    </div>
  )
}
