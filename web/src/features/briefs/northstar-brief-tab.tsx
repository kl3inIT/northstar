import { CalendarDays, ChevronDown, CircleAlert, ExternalLink, History, Loader2, Play, Settings2 } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { toast } from 'sonner'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from '@/components/ui/collapsible'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import { useAutomationRuns, useAutomations, useRunAutomationNow, type AutomationRun } from '@/lib/automation-api'
import { MORNING_BRIEF_TYPE } from '@/lib/morning-brief-automation'
import { useBriefNotes, useNote } from '@/lib/notes-api'
import type { NoteDetail, NoteStatus, NoteSummary } from '@/lib/notes-types'
import { NorthstarBriefSettings } from './northstar-brief-settings'
import { cn } from '@/lib/utils'

const issueDate = new Intl.DateTimeFormat(undefined, { weekday: 'short', day: '2-digit', month: 'short' })
const fullDate = new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' })

export function NorthstarBriefTab() {
  const briefs = useBriefNotes()
  const automations = useAutomations()
  const runNow = useRunAutomationNow()
  const [selectedSlug, setSelectedSlug] = useState<string>()
  const [settingsOpen, setSettingsOpen] = useState(false)
  const definition = automations.data?.find((item) => item.type === MORNING_BRIEF_TYPE)
  const runs = useAutomationRuns(definition?.id ?? null, Boolean(definition))

  useEffect(() => {
    if (!selectedSlug && briefs.data?.length) setSelectedSlug(briefs.data[0].slug)
    if (selectedSlug && briefs.data && !briefs.data.some((brief) => brief.slug === selectedSlug)) {
      setSelectedSlug(briefs.data[0]?.slug)
    }
  }, [briefs.data, selectedSlug])

  const summary = briefs.data?.find((brief) => brief.slug === selectedSlug)
  const note = useNote(selectedSlug)

  function execute() {
    if (!definition) return setSettingsOpen(true)
    runNow.mutate(definition.id, {
      onSuccess: () => toast.success('Northstar Brief queued in the worker'),
      onError: (error) => toast.error(error.message),
    })
  }

  return (
    <div className="pb-20">
      <div className="flex flex-col gap-3 border-b py-5 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <p className="text-sm font-semibold">Your scheduled intelligence</p>
          <p className="mt-1 text-xs text-muted-foreground">Official releases and community signals selected by your sources.</p>
        </div>
        <div className="flex items-center gap-2">
          {briefs.data?.length ? (
            <Select value={selectedSlug ?? ''} onValueChange={setSelectedSlug}>
              <SelectTrigger className="w-64" aria-label="Choose Northstar Brief issue"><SelectValue /></SelectTrigger>
              <SelectContent>
                {briefs.data.map((brief, index) => (
                  <SelectItem key={brief.id} value={brief.slug}>{index === 0 ? 'Latest · ' : ''}{issueDate.format(new Date(brief.createdAt))}</SelectItem>
                ))}
              </SelectContent>
            </Select>
          ) : null}
          <Button variant="outline" size="sm" className="border-insight/30 text-insight hover:bg-insight/10" disabled={runNow.isPending || automations.isLoading} onClick={execute}>
            {runNow.isPending ? <Loader2 className="animate-spin" /> : <Play className="text-insight" />}
            Run now
          </Button>
          <Button variant="outline" size="sm" onClick={() => setSettingsOpen(true)}><Settings2 /> Configure</Button>
        </div>
      </div>

      {briefs.isLoading ? <IssueSkeleton /> : briefs.isError ? (
        <div className="grid min-h-96 place-items-center text-center"><div><CircleAlert className="mx-auto size-7 text-destructive" /><p className="mt-3 text-sm font-medium">Northstar Briefs could not be loaded</p><p className="mt-1 text-xs text-muted-foreground">{briefs.error.message}</p></div></div>
      ) : !briefs.data?.length ? (
        <div className="grid min-h-96 place-items-center text-center">
          <div className="max-w-md"><p className="text-2xl font-semibold tracking-tight">No Northstar Brief yet</p><p className="mt-2 text-sm leading-6 text-muted-foreground">Choose your schedule and sources here. The worker will build the first issue without exposing it as a generic Automation.</p><Button className="mt-5" onClick={() => setSettingsOpen(true)}><Settings2 /> Configure Northstar Brief</Button></div>
        </div>
      ) : note.isLoading || !summary ? <IssueSkeleton /> : note.isError || !note.data ? (
        <div className="py-20 text-center text-sm text-destructive">This issue could not be opened.</div>
      ) : <Issue note={note.data} summary={summary} />}

      {definition && <RunHistory runs={runs.data ?? []} loading={runs.isLoading} error={runs.isError ? runs.error.message : null} />}
      <NorthstarBriefSettings open={settingsOpen} onOpenChange={setSettingsOpen} />
    </div>
  )
}

function RunHistory({ runs, loading, error }: { runs: AutomationRun[]; loading: boolean; error: string | null }) {
  return (
    <section className="mt-10 border-y py-5">
      <div className="flex items-center gap-2">
        <History className="size-4 text-muted-foreground" />
        <h3 className="text-sm font-semibold">Recent runs</h3>
      </div>
      {loading ? <Loader2 className="mt-4 size-4 animate-spin text-muted-foreground" /> : error ? (
        <p className="mt-3 text-xs text-destructive">{error}</p>
      ) : runs.length ? (
        <div className="mt-3 divide-y">
          {runs.map((run) => (
            <div key={run.id} className="flex flex-wrap items-center justify-between gap-2 py-2 text-xs">
              <span className="text-muted-foreground">{fullDate.format(new Date(run.scheduledFor))}</span>
              <div className="flex items-center gap-2">
                <span className="text-muted-foreground">{run.runKind === 'MANUAL' ? 'Manual' : 'Scheduled'}</span>
                <Badge variant={run.status === 'FAILED' ? 'destructive' : 'outline'} className="rounded-none">
                  {run.status.charAt(0) + run.status.slice(1).toLowerCase()}
                </Badge>
              </div>
            </div>
          ))}
        </div>
      ) : <p className="mt-3 text-xs text-muted-foreground">No runs yet.</p>}
    </section>
  )
}

function Issue({ note, summary }: { note: NoteDetail; summary: NoteSummary }) {
  const issue = useMemo(() => parseIssue(note.contentMarkdown, summary.title), [note.contentMarkdown, summary.title])
  const [open, setOpen] = useState<string>()
  return (
    <article>
      <header className="grid gap-4 border-b border-l-2 border-l-insight py-7 pl-5 sm:grid-cols-[minmax(0,1fr)_auto] sm:items-end">
        <div>
          <div className="mb-3 flex flex-wrap items-center gap-3 text-xs text-muted-foreground">
            <StatusBadge status={summary.status} />
            <span className="flex items-center gap-1.5"><CalendarDays className="size-3.5" /> {fullDate.format(new Date(summary.createdAt))}</span>
          </div>
          <h2 className="text-3xl font-semibold tracking-tight">{issue.title}</h2>
          {issue.subtitle && <p className="mt-2 text-sm text-muted-foreground">{issue.subtitle}</p>}
        </div>
        <span className="text-xs text-muted-foreground">{issue.items.length} selected stories</span>
      </header>

      {issue.groups.map((group) => (
        <section key={group.name} className="pt-8">
          <div className="flex items-baseline justify-between gap-3 border-b pb-3">
            <h3 className="flex items-center gap-2 text-xl font-semibold tracking-tight"><span className="size-2 rounded-full bg-insight" aria-hidden="true" />{group.name}</h3>
            <span className="text-xs text-muted-foreground">{group.items.length} stories</span>
          </div>
          {group.items.map((item, index) => {
            const key = `${group.name}-${index}-${item.title}`
            const expanded = open === key
            return (
              <Collapsible key={key} open={expanded} onOpenChange={() => setOpen((current) => current === key ? undefined : key)} className="border-b">
                <CollapsibleTrigger asChild>
                  <button className={cn('group grid w-full grid-cols-[2.25rem_minmax(0,1fr)_auto] items-center gap-3 border-l-2 border-l-transparent px-1 py-3 text-left hover:bg-muted/35 sm:grid-cols-[2.5rem_minmax(0,1fr)_12rem_2rem]', expanded && 'border-l-insight bg-insight/5')}>
                    <span className="text-right font-mono text-xs text-muted-foreground">{index + 1}</span>
                    <span className="text-sm font-semibold leading-5 sm:text-[15px]">{item.title}</span>
                    <span className="hidden truncate text-right text-xs text-muted-foreground sm:block">{item.metadata.join(' · ')}</span>
                    <ChevronDown className={cn('size-3.5 justify-self-end transition-transform', expanded && 'rotate-180')} />
                  </button>
                </CollapsibleTrigger>
                <CollapsibleContent>
                  <div className="border-t bg-muted/10 px-5 py-6 sm:px-14">
                    <div className="max-w-4xl space-y-4 text-[15px] leading-7">
                      {item.summary.map((paragraph) => <p key={paragraph}>{paragraph}</p>)}
                    </div>
                    {item.url && <Button asChild variant="link" className="mt-4 h-auto p-0"><a href={item.url} target="_blank" rel="noreferrer">Open original source <ExternalLink /></a></Button>}
                  </div>
                </CollapsibleContent>
              </Collapsible>
            )
          })}
        </section>
      ))}

      {issue.sourceStatus.length > 0 && (
        <section className="mt-8 border-y py-4">
          <p className="text-[10px] font-semibold uppercase tracking-[0.16em] text-muted-foreground">Source status</p>
          <div className="mt-3 flex flex-wrap gap-x-6 gap-y-2 text-xs text-muted-foreground">
            {issue.sourceStatus.map((status) => <span key={status}>{status}</span>)}
          </div>
        </section>
      )}
    </article>
  )
}

interface ParsedItem { title: string; url?: string; metadata: string[]; summary: string[] }
interface ParsedGroup { name: string; items: ParsedItem[] }

function parseIssue(markdown: string, fallbackTitle: string) {
  const lines = markdown.replace(/^\uFEFF/, '').split(/\r?\n/)
  let title = fallbackTitle.replace(/^Morning Brief\s*-\s*/i, '')
  let subtitle = ''
  const groups: ParsedGroup[] = []
  const sourceStatus: string[] = []
  let group: ParsedGroup | undefined
  let item: ParsedItem | undefined
  let inStatus = false

  function flushItem() {
    if (group && item) group.items.push({ ...item, summary: paragraphs(item.summary) })
    item = undefined
  }

  for (const raw of lines) {
    const line = raw.trim()
    const h1 = line.match(/^#\s+(.+?)\s*#*$/)
    if (h1) { title = h1[1]; continue }
    if (!subtitle) {
      const lead = line.match(/^_(.+)_$/)
      if (lead) { subtitle = lead[1]; continue }
    }
    const h2 = line.match(/^##\s+(.+)$/)
    if (h2) {
      flushItem()
      inStatus = /source status|trạng thái nguồn|^sources$/i.test(h2[1])
      group = inStatus ? undefined : { name: h2[1], items: [] }
      if (group) groups.push(group)
      continue
    }
    if (inStatus) {
      if (/^-\s+/.test(line)) sourceStatus.push(cleanInline(line.replace(/^-\s+/, '')))
      continue
    }
    if (group && /^\|.*\|$/.test(line)) {
      flushItem()
      const cells = line.slice(1, -1).split('|').map((value) => value.trim())
      if (cells.length < 2 || cells.every((cell) => /^:?-{3,}:?$/.test(cell))
        || /thay đổi quan trọng|development|headline/i.test(cells[0])) continue
      const link = cells[0].match(/\[[^\]]+]\((https?:\/\/[^)]+)\)/)
      const emphasized = cells[0].match(/\*\*([^*]+)\*\*/)?.[1]
      const first = cleanInline(cells[0])
      const why = cleanInline(cells[1])
      group.items.push({
        title: emphasized ?? first.split(/[.:]/, 1)[0],
        url: link?.[1],
        metadata: link ? [new URL(link[1]).hostname.replace(/^www\./, '')] : [],
        summary: [first, why ? `Why it matters: ${why}` : ''],
      })
      continue
    }
    const h3 = line.match(/^###\s+\[([^\]]+)]\(([^)]+)\)$/)
    if (h3) {
      flushItem()
      item = { title: h3[1], url: h3[2], metadata: [], summary: [] }
      continue
    }
    if (!item || !line || line === '---') continue
    const meta = line.match(/^_(.+)_$/)
    if (meta && item.metadata.length === 0) item.metadata = meta[1].split(' · ').map((value) => value.trim())
    else if (!/^_.*_$/.test(line)) item.summary.push(line)
  }
  flushItem()
  return { title, subtitle, groups: groups.filter((value) => value.items.length > 0), items: groups.flatMap((value) => value.items), sourceStatus }
}

function paragraphs(lines: string[]) {
  return lines.join(' ').split(/\n\s*\n/).map((value) => value.trim()).filter(Boolean)
}

function cleanInline(value: string) {
  return value
    .replace(/\(\[([^\]]+)]\([^)]+\)\)/g, '($1)')
    .replace(/\[([^\]]+)]\([^)]+\)/g, '$1')
    .replace(/\*\*|`/g, '')
    .trim()
}

function StatusBadge({ status }: { status: NoteStatus }) {
  const tone = status === 'STAGING'
    ? 'border-warning/35 bg-warning/10 text-warning'
    : status === 'RESOURCE'
      ? 'border-success/35 bg-success/10 text-success'
      : ''
  return <Badge variant="outline" className={cn('rounded-none', tone)}>{status === 'STAGING' ? 'Awaiting review' : status === 'RESOURCE' ? 'Saved' : 'Archived'}</Badge>
}

function IssueSkeleton() {
  return <div className="space-y-8 py-7"><Skeleton className="h-20 w-3/4" />{[1, 2, 3].map((value) => <div key={value} className="space-y-3"><Skeleton className="h-7 w-56" /><Skeleton className="h-12 w-full" /><Skeleton className="h-12 w-full" /></div>)}</div>
}
