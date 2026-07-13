import { useEffect, useState } from 'react'
import { Check, Loader2 } from 'lucide-react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { ToggleGroup, ToggleGroupItem } from '@/components/ui/toggle-group'
import { cn } from '@/lib/utils'
import { useSaveHabit, type Habit, type HabitInput } from '@/lib/habits-api'
import {
  defaultHabitInput,
  HABIT_COLORS,
  inputFromHabit,
  WEEKDAYS,
  type HabitColor,
  type Weekday,
} from './habit-utils'

interface HabitEditorDialogProps {
  open: boolean
  habit?: Habit
  onClose: () => void
}

export function HabitEditorDialog({ open, habit, onClose }: HabitEditorDialogProps) {
  const [input, setInput] = useState<HabitInput>(defaultHabitInput)
  const save = useSaveHabit()

  useEffect(() => {
    if (open) setInput(habit ? inputFromHabit(habit) : defaultHabitInput())
  }, [habit, open])

  function set<K extends keyof HabitInput>(key: K, value: HabitInput[K]) {
    setInput((current) => ({ ...current, [key]: value }))
  }

  function submit() {
    const title = input.title.trim()
    if (!title) {
      toast.error('Name the behaviour you want to repeat')
      return
    }
    if (input.frequencyType === 'ON_DAYS' && input.days.length === 0) {
      toast.error('Choose at least one day')
      return
    }
    save.mutate({
      id: habit?.id,
      input: {
        ...input,
        title,
        cue: input.cue?.trim() || undefined,
        notes: input.notes?.trim() || undefined,
        days: input.frequencyType === 'ON_DAYS' ? input.days : [],
      },
    }, {
      onSuccess: () => {
        toast.success(habit ? 'Habit updated' : 'Habit created')
        onClose()
      },
      onError: (error) => toast.error(error.message),
    })
  }

  return (
    <Dialog open={open} onOpenChange={(next) => !next && onClose()}>
      <DialogContent className="max-h-[calc(100dvh-2rem)] overflow-y-auto sm:max-w-xl">
        <DialogHeader>
          <DialogTitle>{habit ? 'Edit habit' : 'New habit'}</DialogTitle>
          <DialogDescription>
            Track a repeated behaviour in its usual context. One-off obligations belong in Tasks.
          </DialogDescription>
        </DialogHeader>

        <div className="grid gap-5 py-1">
          <div className="grid gap-2">
            <Label htmlFor="habit-title">Behaviour</Label>
            <Input
              id="habit-title"
              autoFocus
              maxLength={120}
              placeholder="Read for 20 minutes"
              value={input.title}
              onChange={(event) => set('title', event.target.value)}
            />
          </div>

          <div className="grid gap-2">
            <Label htmlFor="habit-cue">Cue</Label>
            <Input
              id="habit-cue"
              maxLength={255}
              placeholder="After dinner"
              value={input.cue ?? ''}
              onChange={(event) => set('cue', event.target.value)}
            />
            <p className="text-xs text-muted-foreground">A stable moment or place that should trigger the behaviour.</p>
          </div>

          <fieldset className="grid gap-3">
            <legend className="mb-2 text-sm font-medium">Schedule</legend>
            <ToggleGroup
              type="single"
              variant="outline"
              value={input.frequencyType}
              onValueChange={(value) => value && set('frequencyType', value as HabitInput['frequencyType'])}
              className="w-full"
            >
              <ToggleGroupItem value="ON_DAYS" className="flex-1">Specific days</ToggleGroupItem>
              <ToggleGroupItem value="WEEKLY_TARGET" className="flex-1">Weekly target</ToggleGroupItem>
            </ToggleGroup>

            {input.frequencyType === 'ON_DAYS' ? (
              <div className="grid grid-cols-7 gap-1.5" aria-label="Days of week">
                {WEEKDAYS.map(([value, label]) => {
                  const selected = input.days.includes(value)
                  return (
                    <Button
                      key={value}
                      type="button"
                      size="icon"
                      variant={selected ? 'default' : 'outline'}
                      className="size-9 w-full"
                      aria-pressed={selected}
                      title={value.toLowerCase()}
                      onClick={() => set('days', selected
                        ? input.days.filter((day) => day !== value)
                        : [...input.days, value as Weekday])}
                    >
                      {label}
                    </Button>
                  )
                })}
              </div>
            ) : (
              <div className="flex items-center gap-3">
                <Input
                  aria-label="Weekly target"
                  type="number"
                  min={1}
                  max={7}
                  className="w-20"
                  value={input.weeklyTarget}
                  onChange={(event) => set('weeklyTarget', Math.max(1, Math.min(7, Number(event.target.value))))}
                />
                <span className="text-sm text-muted-foreground">different days each week</span>
              </div>
            )}
          </fieldset>

          <fieldset className="grid gap-2">
            <legend className="mb-1 text-sm font-medium">Color</legend>
            <div className="flex flex-wrap gap-2">
              {HABIT_COLORS.map((color) => (
                <button
                  key={color.value}
                  type="button"
                  className={cn(
                    'relative size-8 rounded-full border-2 border-background ring-offset-2 transition-transform hover:scale-105 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring',
                    color.className,
                    input.color === color.value && 'ring-2 ring-foreground/60',
                  )}
                  aria-label={color.label}
                  aria-pressed={input.color === color.value}
                  onClick={() => set('color', color.value as HabitColor)}
                >
                  {input.color === color.value && <Check className="absolute inset-0 m-auto size-4 text-white" />}
                </button>
              ))}
            </div>
          </fieldset>

          <div className="grid gap-2">
            <Label htmlFor="habit-notes">Minimum version or notes</Label>
            <Textarea
              id="habit-notes"
              rows={3}
              placeholder="On difficult days, one page still counts."
              value={input.notes ?? ''}
              onChange={(event) => set('notes', event.target.value)}
            />
          </div>
        </div>

        <DialogFooter>
          <Button type="button" variant="outline" onClick={onClose}>Cancel</Button>
          <Button type="button" disabled={save.isPending} onClick={submit}>
            {save.isPending && <Loader2 className="size-4 animate-spin" />}
            {habit ? 'Save changes' : 'Create habit'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
