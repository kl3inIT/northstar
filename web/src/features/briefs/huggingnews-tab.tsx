import {
  ArrowUpRight,
  ChevronDown,
  CircleAlert,
  Clock3,
  ExternalLink,
  RefreshCw,
  Search,
  Sparkles,
  TrendingUp,
} from 'lucide-react'
import { useMemo, useState } from 'react'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from '@/components/ui/collapsible'
import { Input } from '@/components/ui/input'
import { Skeleton } from '@/components/ui/skeleton'
import {
  huggingNewsUrl,
  useHuggingNewsFeed,
  useHuggingNewsStory,
  type BriefStory,
} from '@/lib/briefs-api'
import { cn } from '@/lib/utils'

const dayFormat = new Intl.DateTimeFormat(undefined, { weekday: 'long', month: 'short', day: 'numeric', year: 'numeric' })
const timeFormat = new Intl.DateTimeFormat(undefined, { month: 'short', day: 'numeric', hour: 'numeric', minute: '2-digit' })

export function HuggingNewsTab() {
  const feed = useHuggingNewsFeed()
  const [topic, setTopic] = useState('all')
  const [query, setQuery] = useState('')
  const [openStory, setOpenStory] = useState<string>()
  const tags = useMemo(() => {
    const counts = new Map<string, { name: string; count: number }>()
    feed.data?.days.forEach((day) => day.stories.forEach((story) => story.tags.forEach((tag) => {
      const current = counts.get(tag.slug)
      counts.set(tag.slug, { name: tag.name, count: (current?.count ?? 0) + 1 })
    })))
    return [...counts.entries()].sort((left, right) => right[1].count - left[1].count).slice(0, 10)
  }, [feed.data])

  if (feed.isLoading) return <FeedSkeleton />
  if (feed.isError || !feed.data) {
    return <ProviderError message={feed.error?.message ?? 'The live feed is unavailable.'} onRetry={() => void feed.refetch()} />
  }

  const normalizedQuery = query.trim().toLocaleLowerCase()
  const days = feed.data.days.map((day) => ({
    ...day,
    stories: day.stories.filter((story) => {
      const topicMatch = topic === 'all' || story.topic === topic || story.tags.some((tag) => tag.slug === topic)
      const queryMatch = !normalizedQuery || `${story.title} ${story.tags.map((tag) => tag.name).join(' ')}`.toLocaleLowerCase().includes(normalizedQuery)
      return topicMatch && queryMatch
    }),
  })).filter((day) => day.stories.length > 0)

  function toggle(story: BriefStory) {
    const key = `${story.topic}/${story.slug}`
    setOpenStory((current) => current === key ? undefined : key)
  }

  return (
    <div className="pb-20">
      <div className="flex flex-col gap-4 border-b py-5">
        <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
          <div className="flex flex-wrap items-center gap-2">
            <Button size="sm" variant="ghost" className={cn(topic === 'all' && 'bg-info/10 text-info hover:bg-info/15')} onClick={() => setTopic('all')}>All</Button>
            {tags.map(([slug, value]) => (
              <Button key={slug} size="sm" variant="ghost" className={cn(topic === slug && 'bg-info/10 text-info hover:bg-info/15')} onClick={() => setTopic(slug)}>
                {value.name}<span className="text-muted-foreground">{value.count}</span>
              </Button>
            ))}
          </div>
          <div className="flex items-center gap-2">
            <div className="relative min-w-0 flex-1 lg:w-72">
              <Search className="pointer-events-none absolute left-2.5 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
              <Input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Search this feed" className="pl-8" />
            </div>
            <Button size="icon" variant="outline" aria-label="Refresh HuggingNews" disabled={feed.isFetching} onClick={() => void feed.refetch()}>
              <RefreshCw className={cn(feed.isFetching && 'animate-spin')} />
            </Button>
          </div>
        </div>
        <div className="flex flex-wrap items-center justify-between gap-2 text-xs text-muted-foreground">
          <span>{feed.data.days.reduce((total, day) => total + day.stories.length, 0)} stories across {feed.data.days.length} days</span>
          <span className="flex items-center gap-1.5"><span className={cn('size-1.5 rounded-full', feed.data.stale ? 'bg-warning' : 'bg-success')} /><Clock3 className="size-3.5" /> Updated {timeFormat.format(new Date(feed.data.updatedAt))}{feed.data.stale && ' · cached'}</span>
        </div>
      </div>

      {topic === 'all' && !normalizedQuery && feed.data.tldr.length > 0 && (
        <section className="grid gap-5 border-b py-7 lg:grid-cols-[10rem_1fr]" aria-labelledby="tldr-heading">
          <div>
            <div className="flex items-center gap-2 text-xs font-semibold uppercase tracking-[0.18em] text-muted-foreground">
              <Sparkles className="size-3.5 text-warning" /> <span id="tldr-heading">TL;DR</span>
            </div>
            <p className="mt-1 text-xs text-muted-foreground">Top signals</p>
          </div>
          <ol className="grid gap-x-10 gap-y-2 text-sm leading-6 md:grid-cols-2">
            {feed.data.tldr.map((item, index) => (
              <li key={item.storyId}>
                <button className="group flex w-full gap-3 text-left" onClick={() => setOpenStory(`${item.topic}/${item.slug}`)}>
                  <span className="w-5 shrink-0 text-right font-mono text-xs text-muted-foreground">{index + 1}.</span>
                  <span className="font-medium group-hover:underline">{item.text}</span>
                </button>
              </li>
            ))}
          </ol>
        </section>
      )}

      {days.length === 0 ? (
        <div className="py-20 text-center text-sm text-muted-foreground">No stories match this filter.</div>
      ) : days.map((day) => (
        <section key={day.date} className="pt-8">
          <div className="flex flex-wrap items-end justify-between gap-3 border-b pb-3">
            <div className="flex flex-wrap items-baseline gap-x-3 gap-y-1">
              <span className="h-5 w-1 rounded-full bg-info" aria-hidden="true" />
              <h2 className="text-xl font-semibold tracking-tight">{dayFormat.format(new Date(`${day.date}T12:00:00`))}</h2>
              <span className="text-xs text-muted-foreground">{day.stories.length} stories</span>
            </div>
            <div className="flex gap-3 text-xs text-muted-foreground">
              {day.topics.slice(0, 5).map((item) => <span key={item.topic}>{titleCase(item.topic)} {item.count}</span>)}
            </div>
          </div>
          <div>
            {day.stories.map((story) => {
              const key = `${story.topic}/${story.slug}`
              return <StoryRow key={story.id} story={story} open={openStory === key} onOpen={() => toggle(story)} />
            })}
          </div>
        </section>
      ))}
    </div>
  )
}

function StoryRow({ story, open, onOpen }: { story: BriefStory; open: boolean; onOpen: () => void }) {
  return (
    <Collapsible open={open} onOpenChange={onOpen} className="border-b">
      <CollapsibleTrigger asChild>
        <button className={cn('group grid w-full grid-cols-[2.25rem_minmax(0,1fr)_auto] items-center gap-3 border-l-2 border-l-transparent px-1 py-3 text-left transition-colors hover:bg-muted/35 sm:grid-cols-[2.5rem_minmax(0,1fr)_7rem_8rem_4.5rem]', open && 'border-l-info bg-info/5')}>
          <span className="text-right font-mono text-xs text-muted-foreground">{story.rank || '—'}</span>
          <span className="min-w-0 text-sm font-semibold leading-5 sm:text-[15px]">
            <span className="mr-2 inline-flex items-center gap-1">
              {story.fresh && <span className="font-mono text-[10px] font-bold text-warning">NEW</span>}
              {(story.score ?? 0) >= 0.5 && <TrendingUp className="size-3.5 text-warning" aria-label="Trending" />}
            </span>
            {story.title}
            {story.update && <span className="ml-1 text-muted-foreground" aria-label="Updated from an earlier story">↩</span>}
          </span>
          <span className="hidden text-right text-xs font-medium sm:block">{titleCase(primaryLabel(story))}</span>
          <span className="hidden text-right text-xs text-muted-foreground sm:block">{relativeTime(story.publishedAt)}</span>
          <span className="flex items-center justify-end gap-2 font-mono text-xs text-muted-foreground">
            {story.tweetCount ?? 0}/{story.sourceCount ?? 0}<ChevronDown className={cn('size-3.5 transition-transform', open && 'rotate-180')} />
          </span>
        </button>
      </CollapsibleTrigger>
      <CollapsibleContent className="overflow-hidden data-[state=closed]:animate-collapsible-up data-[state=open]:animate-collapsible-down">
        {open && <StoryDetail story={story} />}
      </CollapsibleContent>
    </Collapsible>
  )
}

function StoryDetail({ story }: { story: BriefStory }) {
  const detail = useHuggingNewsStory(story.topic, story.slug, true)
  if (detail.isLoading) return <DetailSkeleton />
  if (detail.isError || !detail.data) {
    return (
      <div className="border-t bg-muted/10 px-5 py-7 text-sm">
        <p className="text-destructive">{detail.error?.message ?? 'Story detail is unavailable.'}</p>
        <Button asChild variant="link" className="mt-2 h-auto p-0"><a href={huggingNewsUrl(story.topic, story.slug)} target="_blank" rel="noreferrer">Open on HuggingNews <ArrowUpRight /></a></Button>
      </div>
    )
  }
  const value = detail.data
  return (
    <div className="border-t border-info/20 bg-info/[0.025] px-5 py-7 sm:px-14">
      <div className="grid gap-7 lg:grid-cols-[minmax(0,1fr)_18rem]">
        <div className="min-w-0">
          <div className="space-y-3">
            <MetaLine label="Topics" values={[...new Set(value.story.tags.slice(0, 3).map((tag) => tag.name))]} accent />
            <MetaLine label="Tags" values={value.story.tags.map((tag) => tag.name)} />
            <MetaLine label="Keywords" values={value.entities.map((entity) => entity.text)} />
          </div>
          <div className="mt-6 space-y-4 text-[15px] leading-7">
            {value.summary.split(/\n\s*\n/).map((paragraph) => <p key={paragraph}>{paragraph}</p>)}
          </div>
          {value.previousStory && (
            <a href={huggingNewsUrl(value.previousStory.topic, value.previousStory.slug)} target="_blank" rel="noreferrer" className="mt-6 block border border-dashed p-4 transition-colors hover:bg-muted/40">
              <p className="text-[10px] font-semibold uppercase tracking-[0.16em] text-muted-foreground">Earlier version</p>
              <p className="mt-2 text-sm font-semibold">{value.previousStory.title}</p>
              <p className="mt-1 font-mono text-xs text-muted-foreground">{value.previousStory.tweetCount} tweets · {value.previousStory.sourceCount} sources</p>
            </a>
          )}
        </div>
        <aside>
          <div className="flex items-center justify-between border-b pb-2">
            <p className="text-[10px] font-semibold uppercase tracking-[0.16em] text-muted-foreground">Key sources</p>
            <Button asChild size="xs" variant="ghost"><a href={huggingNewsUrl(story.topic, story.slug)} target="_blank" rel="noreferrer">HuggingNews <ExternalLink /></a></Button>
          </div>
          <div className="divide-y">
            {value.sources.map((source) => (
              <a key={source.url} href={source.url} target="_blank" rel="noreferrer" className="block py-3 text-xs hover:bg-muted/30">
                <div className="flex items-center justify-between gap-2">
                  <Badge variant="outline" className={cn('rounded-none font-mono text-[9px] uppercase', sourceTone(source.label))}>{source.label}</Badge>
                  <span className="text-muted-foreground">@{source.author}</span>
                </div>
                <p className="mt-2 leading-5">{source.highlight}</p>
              </a>
            ))}
          </div>
        </aside>
      </div>
    </div>
  )
}

function MetaLine({ label, values, accent = false }: { label: string; values: string[]; accent?: boolean }) {
  if (!values.length) return null
  return (
    <div className="grid gap-2 sm:grid-cols-[5.5rem_1fr] sm:items-start">
      <span className="pt-1 text-[10px] font-semibold uppercase tracking-[0.16em] text-muted-foreground">{label}</span>
      <div className="flex flex-wrap gap-1.5">
        {values.map((value) => <Badge key={value} variant={accent ? 'secondary' : 'outline'} className="rounded-full font-normal">{value}</Badge>)}
      </div>
    </div>
  )
}

function DetailSkeleton() {
  return <div className="border-t bg-muted/10 px-14 py-7"><div className="space-y-3"><Skeleton className="h-6 w-2/3" /><Skeleton className="h-20 w-full" /><Skeleton className="h-16 w-5/6" /></div></div>
}

function FeedSkeleton() {
  return <div className="space-y-8 py-6"><Skeleton className="h-10 w-full" /><Skeleton className="h-36 w-full" />{[1, 2, 3].map((value) => <div key={value} className="space-y-3"><Skeleton className="h-7 w-64" /><Skeleton className="h-12 w-full" /><Skeleton className="h-12 w-full" /><Skeleton className="h-12 w-full" /></div>)}</div>
}

function ProviderError({ message, onRetry }: { message: string; onRetry: () => void }) {
  return <div className="grid min-h-96 place-items-center text-center"><div><CircleAlert className="mx-auto size-7 text-destructive" /><p className="mt-3 text-sm font-medium">HuggingNews could not be loaded</p><p className="mt-1 max-w-md text-xs text-muted-foreground">{message}</p><Button variant="outline" size="sm" className="mt-4" onClick={onRetry}><RefreshCw /> Retry</Button></div></div>
}

function primaryLabel(story: BriefStory) {
  return story.tags.find((tag) => tag.slug !== 'ai')?.name ?? story.topic
}

function sourceTone(label: string) {
  const value = label.toLocaleLowerCase()
  if (value === 'source') return 'border-info/35 bg-info/5 text-info'
  if (value === 'analysis') return 'border-insight/35 bg-insight/5 text-insight'
  if (value === 'support') return 'border-success/35 bg-success/5 text-success'
  return 'border-warning/35 bg-warning/5 text-warning'
}

function titleCase(value: string) {
  return value.replace(/-/g, ' ').replace(/\b\w/g, (character) => character.toUpperCase())
}

function relativeTime(value: string) {
  const elapsed = Date.now() - Date.parse(value)
  const hours = Math.max(0, Math.floor(elapsed / 3_600_000))
  if (hours < 24) return `${hours}h ago`
  return timeFormat.format(new Date(value))
}
