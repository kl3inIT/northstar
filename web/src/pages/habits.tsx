import { useMemo, useState } from 'react'
import { Archive, CalendarCheck2, ChartNoAxesCombined, ListFilter, Plus, Target } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { TooltipProvider } from '@/components/ui/tooltip'
import { HabitEditorDialog } from '@/features/habits/habit-editor-dialog'
import { EmptyHabits, HabitListRow, TodayHabitRow } from '@/features/habits/habit-row'
import { HabitsInsights } from '@/features/habits/habits-insights'
import { todayProgress } from '@/features/habits/habit-utils'
import { useHabits, useTodayHabits, type Habit } from '@/lib/habits-api'

export function HabitsPage() {
  const [tab, setTab] = useState<'today' | 'all' | 'insights'>('today')
  const [editing, setEditing] = useState<Habit | null | undefined>(undefined)

  return (
    <TooltipProvider>
      <main className="w-full min-w-0 flex-1 overflow-auto px-4 py-6 md:px-10 md:py-8">
        <div className="mx-auto flex max-w-6xl flex-col gap-5 pb-8">
          <header className="flex flex-wrap items-start justify-between gap-3">
            <div>
              <h1 className="text-3xl font-bold tracking-tight">Habits</h1>
              <p className="mt-1 text-sm text-muted-foreground">Repeat what matters, with pauses and exceptions treated as part of real life.</p>
            </div>
            <Button onClick={() => setEditing(null)}><Plus className="size-4" /> New habit</Button>
          </header>

          <Tabs value={tab} onValueChange={(value) => setTab(value as typeof tab)} className="gap-5">
            <TabsList>
              <TabsTrigger value="today"><CalendarCheck2 className="hidden size-4 sm:block" /> Today</TabsTrigger>
              <TabsTrigger value="all"><ListFilter className="hidden size-4 sm:block" /> All habits</TabsTrigger>
              <TabsTrigger value="insights"><ChartNoAxesCombined className="hidden size-4 sm:block" /> Insights</TabsTrigger>
            </TabsList>
            <TabsContent value="today"><TodayPanel onEdit={setEditing} /></TabsContent>
            <TabsContent value="all"><AllHabitsPanel onEdit={setEditing} /></TabsContent>
            <TabsContent value="insights"><HabitsInsights /></TabsContent>
          </Tabs>
        </div>
      </main>
      <HabitEditorDialog open={editing !== undefined} habit={editing ?? undefined} onClose={() => setEditing(undefined)} />
    </TooltipProvider>
  )
}

function TodayPanel({ onEdit }: { onEdit: (habit: Habit) => void }) {
  const query = useTodayHabits()
  const progress = useMemo(() => todayProgress(query.data ?? []), [query.data])

  if (query.isLoading) return <ListSkeleton />
  if (query.isError) return <ErrorLine message={query.error.message} />
  if (!query.data?.length) return <div className="rounded-lg border"><EmptyHabits /></div>

  return (
    <div className="grid gap-5">
      <div className="flex flex-wrap items-end justify-between gap-3 border-y px-1 py-4">
        <div className="flex items-baseline gap-2">
          <span className="text-3xl font-semibold tabular-nums">{progress.done}</span>
          <span className="text-sm text-muted-foreground">of {progress.due} due today</span>
        </div>
        <div className="flex items-center gap-3">
          <div className="h-1.5 w-28 overflow-hidden rounded-full bg-muted sm:w-44">
            <div className="h-full rounded-full bg-success transition-[width] duration-200" style={{ width: `${progress.rate}%` }} />
          </div>
          <span className="text-sm font-medium tabular-nums">{progress.rate}%</span>
        </div>
      </div>

      <section className="rounded-lg border" aria-label="Today's habits">
        <div className="hidden grid-cols-[minmax(220px,1fr)_130px_224px_72px_40px] gap-4 border-b bg-muted/30 px-5 py-2 text-[11px] font-medium uppercase text-muted-foreground sm:grid">
          <span>Habit</span><span>Consistency</span><span>Last 7 days</span><span className="text-center">Today</span><span />
        </div>
        {query.data.map((item) => <TodayHabitRow key={item.habit.id} item={item} onEdit={onEdit} />)}
      </section>
      <p className="text-xs text-muted-foreground">Consistency counts scheduled opportunities. Paused and excused days are neutral; streaks are secondary.</p>
    </div>
  )
}

function AllHabitsPanel({ onEdit }: { onEdit: (habit: Habit) => void }) {
  const query = useHabits(true)
  if (query.isLoading) return <ListSkeleton />
  if (query.isError) return <ErrorLine message={query.error.message} />
  if (!query.data?.length) return <div className="rounded-lg border"><EmptyHabits /></div>
  const active = query.data.filter((habit) => habit.status === 'ACTIVE')
  const archived = query.data.filter((habit) => habit.status === 'ARCHIVED')

  return (
    <div className="grid gap-7">
      <section>
        <div className="mb-2 flex items-center gap-2 text-sm font-medium"><Target className="size-4" /> Active <span className="text-muted-foreground">{active.length}</span></div>
        <div className="rounded-lg border">{active.length ? active.map((habit) => <HabitListRow key={habit.id} habit={habit} onEdit={onEdit} />) : <EmptyHabits />}</div>
      </section>
      {archived.length > 0 && (
        <section>
          <div className="mb-2 flex items-center gap-2 text-sm font-medium"><Archive className="size-4" /> Archive <span className="text-muted-foreground">{archived.length}</span></div>
          <div className="rounded-lg border">{archived.map((habit) => <HabitListRow key={habit.id} habit={habit} onEdit={onEdit} />)}</div>
        </section>
      )}
    </div>
  )
}

function ListSkeleton() {
  return <div className="grid gap-3"><Skeleton className="h-16" /><Skeleton className="h-20" /><Skeleton className="h-20" /><Skeleton className="h-20" /></div>
}

function ErrorLine({ message }: { message: string }) {
  return <div className="rounded-lg border border-destructive/30 p-5 text-sm text-destructive">{message}</div>
}
