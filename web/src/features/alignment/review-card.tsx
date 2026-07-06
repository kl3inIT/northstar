import { Link } from '@tanstack/react-router'
import { ArrowUpRight, RefreshCw, Sparkles } from 'lucide-react'
import { useState } from 'react'
import { toast } from 'sonner'
import { MarkdownBody } from '@/components/markdown-body'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { useGenerateReview, useReview, type ReviewPeriod } from '@/lib/alignment-api'

/**
 * Alignment, MFI-style but machine-drafted: the AI writes the daily/weekly
 * review from real task/calendar/capture data — the user only reads it (and can
 * add a line by editing the Journal note it lives in). No nightly writing ritual.
 */
export function ReviewCard() {
  const [period, setPeriod] = useState<ReviewPeriod>('daily')
  const { data: note, isLoading } = useReview(period)
  const generate = useGenerateReview(period)

  const onGenerate = () =>
    generate.mutate(undefined, {
      onSuccess: () => toast.success(period === 'daily' ? 'Daily review ready' : 'Weekly review ready'),
      onError: () => toast.error('Could not generate the review — try again later'),
    })

  return (
    <section className="rounded-xl border">
      <div className="flex flex-wrap items-center gap-3 border-b px-4 py-3">
        <h2 className="flex items-center gap-2 text-sm font-semibold">
          <Sparkles className="size-4 text-primary" /> Review
        </h2>
        <Tabs value={period} onValueChange={(v) => setPeriod(v as ReviewPeriod)} className="ml-auto">
          <TabsList className="h-8">
            <TabsTrigger value="daily" className="px-3 text-xs">
              Today
            </TabsTrigger>
            <TabsTrigger value="weekly" className="px-3 text-xs">
              This week
            </TabsTrigger>
          </TabsList>
        </Tabs>
      </div>

      <div className="px-4 py-4">
        {isLoading ? (
          <div className="space-y-2">
            <Skeleton className="h-4 w-3/4" />
            <Skeleton className="h-4 w-1/2" />
          </div>
        ) : note ? (
          <>
            <MarkdownBody content={note.contentMarkdown} links={note.outgoingLinks} />
            <div className="mt-4 flex items-center gap-2 border-t pt-3">
              <Button size="sm" variant="ghost" onClick={onGenerate} disabled={generate.isPending}>
                <RefreshCw className="size-4" /> {generate.isPending ? 'Generating…' : 'Refresh'}
              </Button>
              <Button asChild size="sm" variant="ghost" className="ml-auto">
                <Link to="/notes/$slug" params={{ slug: note.slug }}>
                  Open in Notes <ArrowUpRight className="size-4" />
                </Link>
              </Button>
            </div>
          </>
        ) : (
          <div className="flex flex-col items-start gap-3">
            <p className="text-sm text-muted-foreground">
              {period === 'daily'
                ? "AI reads today's tasks, calendar, and captures, then drafts the review — you just read it."
                : 'AI looks back at the week: what got done, what slipped, and where to focus next week.'}
            </p>
            <Button size="sm" onClick={onGenerate} disabled={generate.isPending}>
              <Sparkles className="size-4" />
              {generate.isPending ? 'Generating…' : period === 'daily' ? 'Generate daily review' : 'Generate weekly review'}
            </Button>
          </div>
        )}
      </div>
    </section>
  )
}
