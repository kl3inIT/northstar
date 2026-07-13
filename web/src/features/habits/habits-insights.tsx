import { useEffect, useMemo, useRef } from 'react'
import { Activity, BarChart3, CheckCircle2 } from 'lucide-react'
import {
  ContributionGraph,
  ContributionGraphBlock,
  ContributionGraphCalendar,
  ContributionGraphFooter,
  ContributionGraphLegend,
  ContributionGraphTotalCount,
  type Activity as GraphActivity,
} from '@/components/kibo-ui/contribution-graph'
import { Skeleton } from '@/components/ui/skeleton'
import { useHabitInsights } from '@/lib/habits-api'
import { cn } from '@/lib/utils'
import { COLOR_DOT, insightRange } from './habit-utils'

const range = insightRange()

export function HabitsInsights() {
  const query = useHabitInsights(range.from, range.to)
  const graph = useMemo(() => aggregateDays(query.data?.habits ?? []), [query.data?.habits])
  const graphFrame = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const calendar = graphFrame.current?.querySelector<HTMLElement>('[data-habit-calendar]')
    if (!calendar || graph.length === 0) return
    calendar.scrollLeft = calendar.scrollWidth
  }, [graph])

  if (query.isLoading) return <InsightsSkeleton />
  if (query.isError) {
    return <div className="rounded-lg border border-destructive/30 p-5 text-sm text-destructive">{query.error.message}</div>
  }
  if (!query.data || query.data.habits.length === 0) {
    return <div className="grid min-h-64 place-items-center border-y text-sm text-muted-foreground">Track a habit to see consistency over time.</div>
  }

  const totalExpected = query.data.habits.reduce((sum, item) => sum + item.expected, 0)
  const totalCompleted = query.data.habits.reduce((sum, item) => sum + item.completed, 0)
  const totalExcused = query.data.habits.reduce((sum, item) => sum + item.excused, 0)
  const consistency = totalExpected === 0 ? 0 : Math.round(totalCompleted * 100 / totalExpected)

  return (
    <div className="grid min-w-0 gap-8">
      <section className="grid min-w-0 grid-cols-3 border-y" aria-label="Habit summary">
        <InsightStat icon={Activity} label="Consistency" value={`${consistency}%`} />
        <InsightStat icon={CheckCircle2} label="Completed" value={String(totalCompleted)} />
        <InsightStat icon={BarChart3} label="Excused" value={String(totalExcused)} />
      </section>

      <section className="grid min-w-0 gap-4">
        <div>
          <h2 className="text-base font-semibold">Daily rhythm</h2>
          <p className="mt-1 text-xs text-muted-foreground">Completed repetitions across all active habits · past 365 days</p>
        </div>
        <div ref={graphFrame} className="min-w-0 overflow-hidden rounded-lg border p-4 sm:p-5">
          <ContributionGraph
            data={graph}
            totalCount={totalCompleted}
            weekStart={1}
            labels={{ totalCount: '{{count}} completed repetitions', legend: { less: 'Quiet', more: 'Full' } }}
            className="min-w-0 w-full text-[11px]"
          >
            <ContributionGraphCalendar data-habit-calendar className="w-full">
              {({ activity, ...block }) => (
                <g>
                  <ContributionGraphBlock
                    activity={activity}
                    {...block}
                    className={cn(
                      'stroke-background stroke-[1px]',
                      'data-[level="0"]:fill-muted',
                      'data-[level="1"]:fill-success/20',
                      'data-[level="2"]:fill-success/40',
                      'data-[level="3"]:fill-success/70',
                      'data-[level="4"]:fill-success',
                    )}
                  />
                  <title>{activity.date}: {activity.count} completed</title>
                </g>
              )}
            </ContributionGraphCalendar>
            <ContributionGraphFooter className="w-full">
              <ContributionGraphTotalCount />
              <ContributionGraphLegend />
            </ContributionGraphFooter>
          </ContributionGraph>
        </div>
      </section>

      <section className="grid min-w-0 gap-3">
        <div>
          <h2 className="text-base font-semibold">Consistency by habit</h2>
          <p className="mt-1 text-xs text-muted-foreground">Completed / expected; paused and excused dates stay neutral.</p>
        </div>
        <div className="divide-y border-y">
          {[...query.data.habits]
            .sort((a, b) => b.consistency - a.consistency)
            .map((item) => (
              <div key={item.habit.id} className="grid gap-2 py-3 sm:grid-cols-[minmax(180px,1fr)_minmax(200px,2fr)_90px] sm:items-center sm:gap-5">
                <div className="flex min-w-0 items-center gap-2">
                  <span className={cn('size-2.5 shrink-0 rounded-full', COLOR_DOT[item.habit.color])} />
                  <span className="truncate text-sm font-medium">{item.habit.title}</span>
                </div>
                <div className="h-1.5 overflow-hidden rounded-full bg-muted">
                  <div className="h-full rounded-full bg-foreground/75" style={{ width: `${item.consistency}%` }} />
                </div>
                <p className="text-xs tabular-nums text-muted-foreground sm:text-right">
                  <span className="font-medium text-foreground">{item.consistency}%</span> · {item.completed}/{item.expected}
                </p>
              </div>
            ))}
        </div>
      </section>
    </div>
  )
}

function InsightStat({ icon: Icon, label, value }: { icon: typeof Activity; label: string; value: string }) {
  return (
    <div className="flex items-center gap-3 border-r px-3 py-4 last:border-r-0 sm:px-5">
      <Icon className="hidden size-4 text-muted-foreground sm:block" />
      <div>
        <p className="text-lg font-semibold tabular-nums sm:text-xl">{value}</p>
        <p className="text-[11px] text-muted-foreground sm:text-xs">{label}</p>
      </div>
    </div>
  )
}

function aggregateDays(items: NonNullable<ReturnType<typeof useHabitInsights>['data']>['habits']): GraphActivity[] {
  const values = new Map<string, number>()
  for (const item of items) {
    for (const day of item.days) {
      if (!values.has(day.date)) values.set(day.date, 0)
      if (day.state === 'DONE') values.set(day.date, (values.get(day.date) ?? 0) + 1)
    }
  }
  const maximum = Math.max(1, ...values.values())
  return [...values.entries()].map(([date, count]) => ({
    date,
    count,
    level: count === 0 ? 0 : Math.max(1, Math.ceil(count * 4 / maximum)),
  }))
}

function InsightsSkeleton() {
  return <div className="grid gap-6"><Skeleton className="h-20" /><Skeleton className="h-44" /><Skeleton className="h-52" /></div>
}
