import { useMemo, useState, type ComponentType } from 'react'
import { toast } from 'sonner'
import {
  Car,
  CircleDollarSign,
  Coffee,
  Gamepad2,
  Gift,
  GraduationCap,
  HeartPulse,
  House,
  Ellipsis,
  Pencil,
  PiggyBank,
  Plane,
  Plus,
  ReceiptText,
  ShoppingBag,
  Target,
  Trash2,
  Users,
  Utensils,
} from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { m } from '@/components/motion-primitives'
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from '@/components/ui/dropdown-menu'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import { cn } from '@/lib/utils'
import {
  useBudgets,
  useCategories,
  useContributeSavingsGoal,
  useCreateBudget,
  useCreateSavingsGoal,
  useDeleteBudget,
  useDeleteSavingsGoal,
  useSavingsGoals,
  useUpdateBudget,
  useUpdateSavingsGoal,
  type Budget,
  type SavingsGoal,
} from '@/lib/finance-api'
import { formatFullDate, vnd } from './format'

type Icon = ComponentType<{ className?: string }>
const EMPTY_BUDGETS: Budget[] = []
const EMPTY_GOALS: SavingsGoal[] = []
const EMPTY_CATEGORIES: string[] = []

const CATEGORY_ICONS: Array<[string, Icon]> = [
  ['an uong', Utensils], ['cafe', Coffee], ['di lai', Car], ['hoa don', ReceiptText],
  ['nha cua', House], ['mua sam', ShoppingBag], ['suc khoe', HeartPulse],
  ['giai tri', Gamepad2], ['hoc tap', GraduationCap], ['du lich', Plane],
  ['hieu hi', Gift], ['gia dinh', Users],
]

function plain(value: string): string {
  return value.normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/đ/g, 'd').toLocaleLowerCase()
}

function categoryIcon(category: string): Icon {
  const normalized = plain(category)
  return CATEGORY_ICONS.find(([key]) => normalized.includes(key))?.[1] ?? CircleDollarSign
}

function isWholeVnd(value: number, allowZero = false): boolean {
  return Number.isSafeInteger(value) && (allowZero ? value >= 0 : value > 0)
}

export function PlanningPanel({ month }: { month: string }) {
  const budgetsQuery = useBudgets(month)
  const goalsQuery = useSavingsGoals()
  const expenseCategories = useCategories('EXPENSE').data ?? EMPTY_CATEGORIES
  const budgets = budgetsQuery.data ?? EMPTY_BUDGETS
  const goals = goalsQuery.data ?? EMPTY_GOALS
  const [budgetEditor, setBudgetEditor] = useState<{ budget?: Budget } | null>(null)
  const [goalEditor, setGoalEditor] = useState<{ goal?: SavingsGoal } | null>(null)
  const [contributing, setContributing] = useState<SavingsGoal | null>(null)
  const [deletingBudget, setDeletingBudget] = useState<Budget | null>(null)
  const [deletingGoal, setDeletingGoal] = useState<SavingsGoal | null>(null)

  const totals = useMemo(() => budgets.reduce((result, budget) => ({
    allocated: result.allocated + budget.limitAmount,
    spent: result.spent + budget.spentAmount,
  }), { allocated: 0, spent: 0 }), [budgets])

  return (
    <div className="flex flex-col gap-8">
      <section className="flex flex-col gap-4" aria-labelledby="monthly-budgets-title">
        <div className="flex flex-wrap items-end justify-between gap-3">
          <div>
            <h2 id="monthly-budgets-title" className="text-base font-semibold">Monthly budgets</h2>
          </div>
          <Button size="sm" onClick={() => setBudgetEditor({})}><Plus className="size-4" /> Add budget</Button>
        </div>

        <div className="grid grid-cols-3 gap-2 rounded-lg border bg-card p-3">
          <PlanningMetric label="Allocated" value={vnd(totals.allocated)} />
          <PlanningMetric label="Spent" value={vnd(totals.spent)} />
          <PlanningMetric label="Available" value={vnd(totals.allocated - totals.spent)} tone={totals.spent > totals.allocated ? 'danger' : undefined} />
        </div>

        {budgetsQuery.isLoading ? (
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-4">
            {Array.from({ length: 4 }, (_, index) => <Skeleton key={index} className="h-48 rounded-lg" />)}
          </div>
        ) : budgetsQuery.error ? (
          <InlineError message="Could not load monthly budgets." />
        ) : budgets.length === 0 ? (
          <EmptyPlanningState icon={Target} title="No budgets for this month" action="Add budget" onAction={() => setBudgetEditor({})} />
        ) : (
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-4">
            {budgets.map((budget) => (
              <BudgetRingCard key={budget.id} budget={budget} onEdit={() => setBudgetEditor({ budget })} onDelete={() => setDeletingBudget(budget)} />
            ))}
          </div>
        )}
      </section>

      <section className="flex flex-col gap-4 border-t pt-7" aria-labelledby="savings-goals-title">
        <div className="flex flex-wrap items-end justify-between gap-3">
          <div>
            <h2 id="savings-goals-title" className="text-base font-semibold">Savings goals</h2>
          </div>
          <Button size="sm" onClick={() => setGoalEditor({})}><Plus className="size-4" /> Add goal</Button>
        </div>

        {goalsQuery.isLoading ? (
          <div className="grid gap-3 md:grid-cols-2">{Array.from({ length: 2 }, (_, index) => <Skeleton key={index} className="h-40 rounded-lg" />)}</div>
        ) : goalsQuery.error ? (
          <InlineError message="Could not load savings goals." />
        ) : goals.length === 0 ? (
          <EmptyPlanningState icon={PiggyBank} title="No savings goals yet" action="Add goal" onAction={() => setGoalEditor({})} />
        ) : (
          <div className="grid gap-3 md:grid-cols-2">
            {goals.map((goal) => (
              <SavingsGoalCard
                key={goal.id}
                goal={goal}
                onContribute={() => setContributing(goal)}
                onEdit={() => setGoalEditor({ goal })}
                onDelete={() => setDeletingGoal(goal)}
              />
            ))}
          </div>
        )}
      </section>

      <BudgetDialog
        key={budgetEditor?.budget?.id ?? (budgetEditor ? 'new-budget' : 'closed-budget')}
        editor={budgetEditor}
        month={month}
        categories={expenseCategories}
        onClose={() => setBudgetEditor(null)}
      />
      <GoalDialog
        key={goalEditor?.goal?.id ?? (goalEditor ? 'new-goal' : 'closed-goal')}
        editor={goalEditor}
        onClose={() => setGoalEditor(null)}
      />
      <ContributionDialog key={contributing?.id ?? 'closed-contribution'} goal={contributing} onClose={() => setContributing(null)} />
      <DeleteBudgetDialog budget={deletingBudget} onClose={() => setDeletingBudget(null)} />
      <DeleteGoalDialog goal={deletingGoal} onClose={() => setDeletingGoal(null)} />
    </div>
  )
}

function PlanningMetric({ label, value, tone }: { label: string; value: string; tone?: 'danger' }) {
  return (
    <div className="min-w-0">
      <p className="text-[11px] text-muted-foreground">{label}</p>
      <p className={cn('truncate text-sm font-semibold tabular-nums sm:text-base', tone === 'danger' && 'text-destructive')} title={value}>{value}</p>
    </div>
  )
}

const RADIUS = 42
const CIRCUMFERENCE = 2 * Math.PI * RADIUS

function BudgetRingCard({ budget, onEdit, onDelete }: { budget: Budget; onEdit: () => void; onDelete: () => void }) {
  const percent = Math.min(Math.max(budget.progressPercent, 0), 100)
  const offset = CIRCUMFERENCE - (percent / 100) * CIRCUMFERENCE
  const nearLimit = budget.progressPercent >= 80 && !budget.overBudget
  const Icon = categoryIcon(budget.category)
  return (
    <article className={cn('group relative flex min-h-52 min-w-0 flex-col items-center justify-center rounded-lg border bg-card p-4 transition-colors hover:bg-muted/20', budget.overBudget && 'border-destructive/50 bg-destructive/[0.025]', budget.inherited && 'border-dashed')}>
      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <Button variant="ghost" size="icon" className="absolute right-2 top-2 size-7" aria-label={`Actions for ${budget.category} budget`}>
            <Ellipsis className="size-4" />
          </Button>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="end">
          {budget.inherited ? (
            <DropdownMenuItem onClick={onEdit}><Pencil className="size-4" /> Set for this month</DropdownMenuItem>
          ) : (
            <>
              <DropdownMenuItem onClick={onEdit}><Pencil className="size-4" /> Edit budget</DropdownMenuItem>
              <DropdownMenuItem variant="destructive" onClick={onDelete}><Trash2 className="size-4" /> Delete budget</DropdownMenuItem>
            </>
          )}
        </DropdownMenuContent>
      </DropdownMenu>
      <div className="relative size-24 sm:size-28">
        <svg viewBox="0 0 100 100" className="size-full -rotate-90" role="img" aria-label={`${budget.category}: ${budget.progressPercent}% of monthly budget used`}>
          <circle cx="50" cy="50" r={RADIUS} fill="none" stroke="currentColor" className="text-muted" strokeWidth="7" />
          <m.circle
            cx="50" cy="50" r={RADIUS} fill="none" stroke="currentColor" strokeWidth="7" strokeLinecap="round"
            strokeDasharray={CIRCUMFERENCE}
            initial={{ strokeDashoffset: CIRCUMFERENCE }}
            animate={{ strokeDashoffset: offset }}
            transition={{ duration: 0.2 }}
            className={cn(budget.overBudget ? 'text-destructive' : nearLimit ? 'text-warning' : 'text-primary')}
          />
        </svg>
        <div className={cn('absolute inset-0 flex flex-col items-center justify-center', budget.overBudget ? 'text-destructive' : nearLimit ? 'text-warning' : 'text-primary')}>
          <Icon className="size-5" />
          <span className="mt-1 text-[11px] font-semibold tabular-nums">{budget.progressPercent}%</span>
        </div>
      </div>
      <div className="mt-2 min-w-0 text-center">
        <h3 className="truncate text-sm font-semibold">{budget.category}</h3>
        {budget.inherited && (
          <Badge variant="outline" className="mt-0.5 max-w-full rounded text-[10px] font-normal text-muted-foreground" title="No budget set for this month — showing the last set month's limit against this month's spending. Edit it to pin a limit for this month.">
            carried over
          </Badge>
        )}
        <p className="mt-0.5 truncate text-xs font-medium tabular-nums" title={`${vnd(budget.spentAmount)} of ${vnd(budget.limitAmount)}`}>{vnd(budget.spentAmount)}</p>
        <p className="truncate text-[11px] tabular-nums text-muted-foreground">of {vnd(budget.limitAmount)}</p>
        {budget.overBudget ? (
          <Badge variant="destructive" className="mt-1.5 max-w-full rounded text-[10px]">{vnd(Math.abs(budget.remainingAmount))} over</Badge>
        ) : (
          <p className="mt-1 text-[11px] tabular-nums text-muted-foreground">{vnd(budget.remainingAmount)} left</p>
        )}
      </div>
    </article>
  )
}

function SavingsGoalCard({ goal, onContribute, onEdit, onDelete }: {
  goal: SavingsGoal
  onContribute: () => void
  onEdit: () => void
  onDelete: () => void
}) {
  const status = goalStatus(goal)
  const width = Math.min(Math.max(goal.progressPercent, 0), 100)
  return (
    <article className="rounded-lg border bg-card p-4">
      <div className="flex items-start gap-3">
        <div className="flex size-10 shrink-0 items-center justify-center rounded-lg bg-primary/10 text-primary"><PiggyBank className="size-5" /></div>
        <div className="min-w-0 flex-1">
          <div className="flex items-start justify-between gap-2">
            <div className="min-w-0"><h3 className="truncate text-sm font-semibold">{goal.name}</h3><p className="text-xs text-muted-foreground">{goal.targetDate ? `Target ${formatFullDate(goal.targetDate)}` : 'No target date'}</p></div>
            <Badge variant={status.variant} className="shrink-0 rounded text-[10px]">{status.label}</Badge>
          </div>
          <div className="mt-3 flex items-baseline gap-1"><span className="text-lg font-semibold tabular-nums">{vnd(goal.savedAmount)}</span><span className="text-xs text-muted-foreground">/ {vnd(goal.targetAmount)}</span></div>
          <div className="mt-2 h-2 overflow-hidden rounded-full bg-muted" role="progressbar" aria-label={`${goal.name} savings progress`} aria-valuemin={0} aria-valuemax={100} aria-valuenow={width}><div className={cn('h-full rounded-full transition-[width] duration-500', goal.completed ? 'bg-success' : 'bg-primary')} style={{ width: `${width}%` }} /></div>
          <div className="mt-2 flex items-center justify-between gap-3 text-[11px] text-muted-foreground"><span>{goal.progressPercent}% saved</span><span>{goal.monthlyContribution > 0 ? `${vnd(goal.monthlyContribution)}/month` : `${vnd(goal.remainingAmount)} left`}</span></div>
        </div>
      </div>
      <div className="mt-4 flex items-center justify-end gap-1 border-t pt-3">
        <Button variant="ghost" size="icon-sm" aria-label={`Edit ${goal.name}`} onClick={onEdit}><Pencil className="size-3.5" /></Button>
        <Button variant="ghost" size="icon-sm" className="text-muted-foreground hover:text-destructive" aria-label={`Delete ${goal.name}`} onClick={onDelete}><Trash2 className="size-3.5" /></Button>
        <Button size="sm" onClick={onContribute} disabled={goal.completed}><Plus className="size-3.5" /> Add funds</Button>
      </div>
    </article>
  )
}

function goalStatus(goal: SavingsGoal): { label: string; variant: 'secondary' | 'outline' | 'destructive' } {
  if (goal.completed) return { label: 'Complete', variant: 'secondary' }
  if (!goal.targetDate) return { label: 'Active', variant: 'outline' }
  const target = new Date(`${goal.targetDate}T00:00:00`)
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  if (target < today) return { label: 'Past target', variant: 'destructive' }
  const monthsLeft = Math.max(1, (target.getFullYear() - today.getFullYear()) * 12 + target.getMonth() - today.getMonth())
  const required = Math.ceil(goal.remainingAmount / monthsLeft)
  return goal.monthlyContribution >= required
    ? { label: 'On track', variant: 'secondary' }
    : { label: 'Behind', variant: 'destructive' }
}

function EmptyPlanningState({ icon: Icon, title, action, onAction }: { icon: Icon; title: string; action: string; onAction: () => void }) {
  return (
    <div className="flex min-h-40 flex-col items-center justify-center gap-2 rounded-lg border border-dashed bg-muted/10 p-6 text-center">
      <Icon className="size-5 text-muted-foreground" />
      <p className="text-sm font-medium">{title}</p>
      <Button variant="outline" size="sm" onClick={onAction}><Plus className="size-4" /> {action}</Button>
    </div>
  )
}

function InlineError({ message }: { message: string }) {
  return <div className="rounded-lg border border-destructive/40 bg-destructive/5 p-4 text-sm text-destructive">{message}</div>
}

function BudgetDialog({ editor, month, categories, onClose }: {
  editor: { budget?: Budget } | null
  month: string
  categories: string[]
  onClose: () => void
}) {
  const budget = editor?.budget
  // An inherited budget belongs to the SOURCE month — saving it materializes a
  // new row for the month on screen instead of editing last month's limit.
  const materialize = Boolean(budget?.inherited)
  const [category, setCategory] = useState(budget?.category ?? categories[0] ?? 'Khác')
  const [amount, setAmount] = useState(budget ? String(budget.limitAmount) : '')
  const categoryOptions = useMemo(
    () => Array.from(new Set([budget?.category, ...categories, 'Khác'].filter((item): item is string => Boolean(item)))),
    [budget?.category, categories],
  )
  const create = useCreateBudget()
  const update = useUpdateBudget()
  const pending = create.isPending || update.isPending

  function save() {
    const limitAmount = Number(amount)
    if (!isWholeVnd(limitAmount) || !category) {
      toast.error('Enter a category and positive VND limit')
      return
    }
    const options = {
      onSuccess: () => { toast.success(budget && !materialize ? 'Budget updated' : 'Budget saved'); onClose() },
      onError: (error: Error) => toast.error(error.message),
    }
    if (budget && !materialize) update.mutate({ id: budget.id, month, category, limitAmount }, options)
    else create.mutate({ month, category, limitAmount }, options)
  }

  return (
    <Dialog open={Boolean(editor)} onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="sm:max-w-sm">
        <DialogHeader><DialogTitle>{materialize ? 'Set budget for this month' : budget ? 'Edit monthly budget' : 'Add monthly budget'}</DialogTitle></DialogHeader>
        {materialize && <p className="text-xs text-muted-foreground">Carried over from the last set month — saving pins it to this month.</p>}
        <div className="grid gap-3">
          <div className="grid gap-1.5"><Label htmlFor="budget-category">Category</Label><Select value={category} onValueChange={setCategory}><SelectTrigger id="budget-category"><SelectValue /></SelectTrigger><SelectContent>{categoryOptions.map((item) => <SelectItem key={item} value={item}>{item}</SelectItem>)}</SelectContent></Select></div>
          <div className="grid gap-1.5"><Label htmlFor="budget-amount">Monthly limit (VND)</Label><Input id="budget-amount" inputMode="numeric" value={amount} onChange={(event) => setAmount(event.target.value)} placeholder="3000000" /></div>
        </div>
        <DialogFooter><Button variant="outline" onClick={onClose}>Cancel</Button><Button disabled={pending} onClick={save}>Save budget</Button></DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

function GoalDialog({ editor, onClose }: { editor: { goal?: SavingsGoal } | null; onClose: () => void }) {
  const goal = editor?.goal
  const [name, setName] = useState(goal?.name ?? '')
  const [targetAmount, setTargetAmount] = useState(goal ? String(goal.targetAmount) : '')
  const [savedAmount, setSavedAmount] = useState(goal ? String(goal.savedAmount) : '0')
  const [targetDate, setTargetDate] = useState(goal?.targetDate ?? '')
  const [monthlyContribution, setMonthlyContribution] = useState(goal ? String(goal.monthlyContribution) : '0')
  const create = useCreateSavingsGoal()
  const update = useUpdateSavingsGoal()
  const pending = create.isPending || update.isPending

  function save() {
    if (savedAmount.trim() === '' || monthlyContribution.trim() === '') {
      toast.error('Saved now and monthly plan are required; enter 0 when none')
      return
    }
    const target = Number(targetAmount)
    const saved = Number(savedAmount)
    const monthly = Number(monthlyContribution)
    if (!name.trim() || !isWholeVnd(target) || !isWholeVnd(saved, true) || !isWholeVnd(monthly, true)) {
      toast.error('Enter a name and valid whole-VND amounts')
      return
    }
    const body = { name: name.trim(), targetAmount: target, savedAmount: saved, targetDate: targetDate || undefined, monthlyContribution: monthly }
    const options = {
      onSuccess: () => { toast.success(goal ? 'Savings goal updated' : 'Savings goal created'); onClose() },
      onError: (error: Error) => toast.error(error.message),
    }
    if (goal) update.mutate({ id: goal.id, version: goal.version, ...body }, options)
    else create.mutate(body, options)
  }

  return (
    <Dialog open={Boolean(editor)} onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader><DialogTitle>{goal ? 'Edit savings goal' : 'Add savings goal'}</DialogTitle></DialogHeader>
        <div className="grid gap-3">
          <div className="grid gap-1.5"><Label htmlFor="goal-name">Name</Label><Input id="goal-name" value={name} onChange={(event) => setName(event.target.value)} placeholder="Emergency fund" /></div>
          <div className="grid gap-3 sm:grid-cols-2">
            <div className="grid gap-1.5"><Label htmlFor="goal-target">Target (VND)</Label><Input id="goal-target" inputMode="numeric" value={targetAmount} onChange={(event) => setTargetAmount(event.target.value)} /></div>
            <div className="grid gap-1.5"><Label htmlFor="goal-saved">Saved now</Label><Input id="goal-saved" inputMode="numeric" value={savedAmount} onChange={(event) => setSavedAmount(event.target.value)} /></div>
          </div>
          <div className="grid gap-3 sm:grid-cols-2">
            <div className="grid gap-1.5"><Label htmlFor="goal-date">Target date</Label><Input id="goal-date" type="date" value={targetDate} onChange={(event) => setTargetDate(event.target.value)} /></div>
            <div className="grid gap-1.5"><Label htmlFor="goal-monthly">Monthly plan</Label><Input id="goal-monthly" inputMode="numeric" value={monthlyContribution} onChange={(event) => setMonthlyContribution(event.target.value)} /></div>
          </div>
        </div>
        <DialogFooter><Button variant="outline" onClick={onClose}>Cancel</Button><Button disabled={pending} onClick={save}>Save goal</Button></DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

function ContributionDialog({ goal, onClose }: { goal: SavingsGoal | null; onClose: () => void }) {
  const [amount, setAmount] = useState('')
  const contribute = useContributeSavingsGoal()
  function save() {
    const parsed = Number(amount)
    if (!goal || !isWholeVnd(parsed)) {
      toast.error('Enter a positive whole-VND amount')
      return
    }
    contribute.mutate({ id: goal.id, amount: parsed }, {
      onSuccess: () => { toast.success('Contribution added'); setAmount(''); onClose() },
      onError: (error) => toast.error(error.message),
    })
  }
  return (
    <Dialog open={Boolean(goal)} onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="sm:max-w-sm">
        <DialogHeader><DialogTitle>Add to {goal?.name ?? 'goal'}</DialogTitle></DialogHeader>
        <div className="grid gap-1.5"><Label htmlFor="contribution-amount">Contribution (VND)</Label><Input id="contribution-amount" autoFocus inputMode="numeric" value={amount} onChange={(event) => setAmount(event.target.value)} /></div>
        <DialogFooter><Button variant="outline" onClick={onClose}>Cancel</Button><Button disabled={contribute.isPending} onClick={save}>Add money</Button></DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

function DeleteBudgetDialog({ budget, onClose }: { budget: Budget | null; onClose: () => void }) {
  const remove = useDeleteBudget()
  return <ConfirmDeleteDialog open={Boolean(budget)} title="Delete budget?" detail={budget ? `${budget.category} · ${vnd(budget.limitAmount)}` : ''} pending={remove.isPending} onClose={onClose} onDelete={() => budget && remove.mutate(budget.id, { onSuccess: () => { toast.success('Budget deleted'); onClose() }, onError: (error) => toast.error(error.message) })} />
}

function DeleteGoalDialog({ goal, onClose }: { goal: SavingsGoal | null; onClose: () => void }) {
  const remove = useDeleteSavingsGoal()
  return <ConfirmDeleteDialog open={Boolean(goal)} title="Delete savings goal?" detail={goal ? `${goal.name} · ${vnd(goal.savedAmount)} saved` : ''} pending={remove.isPending} onClose={onClose} onDelete={() => goal && remove.mutate(goal.id, { onSuccess: () => { toast.success('Savings goal deleted'); onClose() }, onError: (error) => toast.error(error.message) })} />
}

function ConfirmDeleteDialog({ open, title, detail, pending, onClose, onDelete }: { open: boolean; title: string; detail: string; pending: boolean; onClose: () => void; onDelete: () => void }) {
  return (
    <Dialog open={open} onOpenChange={(next) => !next && onClose()}>
      <DialogContent className="sm:max-w-sm"><DialogHeader><DialogTitle>{title}</DialogTitle></DialogHeader><p className="text-sm text-muted-foreground">{detail}</p><DialogFooter><Button variant="outline" onClick={onClose}>Cancel</Button><Button variant="destructive" disabled={pending} onClick={onDelete}>Delete</Button></DialogFooter></DialogContent>
    </Dialog>
  )
}
