import {
  Archive,
  Check,
  CircleDashed,
  Clock3,
  MoreHorizontal,
  Pause,
  Pencil,
  RotateCcw,
  ShieldCheck,
} from 'lucide-react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'
import { cn } from '@/lib/utils'
import {
  useClearHabitCheckIn,
  usePauseHabit,
  useResumeHabit,
  useSetHabitArchived,
  useSetHabitCheckIn,
  type Habit,
  type TodayHabit,
} from '@/lib/habits-api'
import { COLOR_DOT, localDate, recentDayLabel, scheduleLabel } from './habit-utils'

const STATE_STYLE: Record<TodayHabit['todayState'], string> = {
  DONE: 'border-emerald-500 bg-emerald-500 text-white',
  EXCUSED: 'border-muted-foreground/30 bg-muted text-muted-foreground',
  OPEN: 'border-border bg-background text-transparent',
  MISSED: 'border-rose-400/50 bg-rose-500/10 text-transparent',
  PAUSED: 'border-dashed border-muted-foreground/30 bg-muted/40 text-transparent',
  NOT_SCHEDULED: 'border-transparent bg-muted/30 text-transparent',
}

interface TodayHabitRowProps {
  item: TodayHabit
  onEdit: (habit: Habit) => void
}

export function TodayHabitRow({ item, onEdit }: TodayHabitRowProps) {
  const checkIn = useSetHabitCheckIn()
  const clear = useClearHabitCheckIn()
  const pause = usePauseHabit()
  const resume = useResumeHabit()
  const archive = useSetHabitArchived()
  const date = localDate()
  const pending = checkIn.isPending || clear.isPending || pause.isPending || resume.isPending || archive.isPending

  function complete() {
    const mutation = item.todayState === 'DONE'
      ? () => clear.mutate({ id: item.habit.id, date }, mutationOptions('Completion cleared'))
      : () => checkIn.mutate({ id: item.habit.id, date, status: 'DONE' }, mutationOptions('Marked done'))
    mutation()
  }

  function mutationOptions(success: string) {
    return {
      onSuccess: () => toast.success(success),
      onError: (error: Error) => toast.error(error.message),
    }
  }

  return (
    <div className="grid grid-cols-[minmax(0,1fr)_40px] items-center gap-4 border-b px-4 py-3 last:border-b-0 sm:grid-cols-[minmax(220px,1fr)_130px_224px_72px_40px] sm:px-5">
      <div className="flex min-w-0 items-start gap-3">
        <span className={cn('mt-1 size-2.5 shrink-0 rounded-full', COLOR_DOT[item.habit.color])} />
        <div className="min-w-0">
          <p className="truncate text-sm font-medium">{item.habit.title}</p>
          <p className="mt-0.5 truncate text-xs text-muted-foreground">
            {item.habit.cue || scheduleLabel(item.habit)}
          </p>
        </div>
      </div>

      <div className="hidden sm:block">
        <p className="text-sm font-medium tabular-nums">{item.consistency30}%</p>
        <p className="text-xs text-muted-foreground">30-day consistency</p>
      </div>

      <div className="col-span-2 grid grid-cols-7 gap-1.5 sm:col-span-1" aria-label={`${item.habit.title} recent seven days`}>
        {item.recentDays.map((day) => {
          const label = recentDayLabel(day.date)
          return (
            <Tooltip key={day.date}>
              <TooltipTrigger asChild>
                <div className="grid justify-items-center gap-1">
                  <span className="text-[10px] text-muted-foreground">{label.weekday}</span>
                  <span className={cn('grid size-6 place-items-center rounded-full border', STATE_STYLE[day.state])}>
                    {day.state === 'DONE' && <Check className="size-3.5" />}
                  </span>
                </div>
              </TooltipTrigger>
              <TooltipContent>{day.date} · {day.state.toLowerCase().replace('_', ' ')}</TooltipContent>
            </Tooltip>
          )
        })}
      </div>

      <Button
        size="sm"
        variant={item.todayState === 'DONE' ? 'secondary' : 'default'}
        className="col-span-2 w-full sm:col-span-1 sm:w-auto"
        disabled={pending || item.todayState === 'PAUSED' || item.todayState === 'NOT_SCHEDULED'}
        onClick={complete}
      >
        <Check className="size-4" />
        {item.todayState === 'DONE' ? 'Done' : 'Check in'}
      </Button>

      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <Button className="col-start-2 row-start-1 sm:col-auto sm:row-auto" size="icon-sm" variant="ghost" aria-label={`Actions for ${item.habit.title}`}>
            <MoreHorizontal className="size-4" />
          </Button>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="end">
          <DropdownMenuItem onClick={() => onEdit(item.habit)}><Pencil /> Edit</DropdownMenuItem>
          {item.todayState !== 'EXCUSED' && item.todayState !== 'PAUSED' && (
            <DropdownMenuItem onClick={() => checkIn.mutate(
              { id: item.habit.id, date, status: 'EXCUSED' }, mutationOptions('Marked excused'),
            )}>
              <ShieldCheck /> Excuse today
            </DropdownMenuItem>
          )}
          {item.todayState === 'EXCUSED' && (
            <DropdownMenuItem onClick={() => clear.mutate(
              { id: item.habit.id, date }, mutationOptions('Exception cleared'),
            )}>
              <RotateCcw /> Clear exception
            </DropdownMenuItem>
          )}
          <DropdownMenuSeparator />
          {item.habit.paused ? (
            <DropdownMenuItem onClick={() => resume.mutate(
              { id: item.habit.id }, mutationOptions('Habit resumed'),
            )}><Clock3 /> Resume</DropdownMenuItem>
          ) : (
            <DropdownMenuItem onClick={() => pause.mutate(
              { id: item.habit.id }, mutationOptions('Habit paused'),
            )}><Pause /> Pause</DropdownMenuItem>
          )}
          <DropdownMenuItem variant="destructive" onClick={() => archive.mutate(
            { id: item.habit.id, archived: true }, mutationOptions('Habit archived'),
          )}><Archive /> Archive</DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>
    </div>
  )
}

interface HabitListRowProps {
  habit: Habit
  onEdit: (habit: Habit) => void
}

export function HabitListRow({ habit, onEdit }: HabitListRowProps) {
  const pause = usePauseHabit()
  const resume = useResumeHabit()
  const archive = useSetHabitArchived()
  const archived = habit.status === 'ARCHIVED'
  const pending = pause.isPending || resume.isPending || archive.isPending
  const options = {
    onSuccess: () => toast.success(archived ? 'Habit restored' : 'Habit updated'),
    onError: (error: Error) => toast.error(error.message),
  }

  return (
    <div className={cn('flex items-center gap-4 border-b px-4 py-3.5 last:border-b-0 sm:px-5', archived && 'opacity-60')}>
      <span className={cn('size-2.5 shrink-0 rounded-full', COLOR_DOT[habit.color])} />
      <div className="min-w-0 flex-1">
        <div className="flex flex-wrap items-center gap-2">
          <p className="truncate text-sm font-medium">{habit.title}</p>
          {habit.paused && <span className="rounded-sm bg-muted px-1.5 py-0.5 text-[10px] text-muted-foreground">Paused</span>}
          {archived && <span className="rounded-sm bg-muted px-1.5 py-0.5 text-[10px] text-muted-foreground">Archived</span>}
        </div>
        <p className="mt-0.5 truncate text-xs text-muted-foreground">
          {scheduleLabel(habit)}{habit.cue ? ` · ${habit.cue}` : ''}
        </p>
      </div>
      <div className="hidden text-right sm:block">
        <p className="text-xs text-muted-foreground">Since {habit.schedule.effectiveFrom}</p>
      </div>
      {!archived && <Button size="icon-sm" variant="ghost" title="Edit habit" onClick={() => onEdit(habit)}><Pencil /></Button>}
      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <Button size="icon-sm" variant="ghost" disabled={pending} aria-label={`Actions for ${habit.title}`}><MoreHorizontal /></Button>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="end">
          {archived ? (
            <DropdownMenuItem onClick={() => archive.mutate({ id: habit.id, archived: false }, options)}>
              <RotateCcw /> Restore
            </DropdownMenuItem>
          ) : (
            <>
              {habit.paused ? (
                <DropdownMenuItem onClick={() => resume.mutate({ id: habit.id }, options)}><Clock3 /> Resume</DropdownMenuItem>
              ) : (
                <DropdownMenuItem onClick={() => pause.mutate({ id: habit.id }, options)}><Pause /> Pause</DropdownMenuItem>
              )}
              <DropdownMenuSeparator />
              <DropdownMenuItem variant="destructive" onClick={() => archive.mutate({ id: habit.id, archived: true }, options)}>
                <Archive /> Archive
              </DropdownMenuItem>
            </>
          )}
        </DropdownMenuContent>
      </DropdownMenu>
    </div>
  )
}

export function EmptyHabits() {
  return (
    <div className="grid min-h-56 place-items-center px-6 py-12 text-center">
      <div>
        <CircleDashed className="mx-auto size-7 text-muted-foreground" />
        <p className="mt-3 text-sm font-medium">No habits yet</p>
        <p className="mt-1 text-xs text-muted-foreground">Start with one behaviour tied to a reliable cue.</p>
      </div>
    </div>
  )
}
