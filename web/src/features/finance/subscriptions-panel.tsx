import { useMemo, useState } from 'react'
import { toast } from 'sonner'
import {
  Banknote,
  CalendarClock,
  CircleCheck,
  CirclePause,
  Ellipsis,
  Pencil,
  Plus,
  Repeat2,
  Search,
  Sparkles,
  Trash2,
} from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { m } from '@/components/motion-primitives'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import { Switch } from '@/components/ui/switch'
import { cn } from '@/lib/utils'
import {
  useCategories,
  useCreateSubscription,
  useDeleteSubscription,
  usePaySubscription,
  useRecurringSuggestions,
  useSubscriptions,
  useUpdateSubscription,
  type Subscription,
  type RecurringChargeSuggestion,
} from '@/lib/finance-api'
import { formatFullDate, todayIso, vnd } from './format'

type StatusFilter = 'ALL' | 'ACTIVE' | 'PAUSED'
const EMPTY_CATEGORIES: string[] = []

function isWholeVnd(value: number): boolean {
  return Number.isSafeInteger(value) && value > 0
}

function isSubscriptionConflict(error: Error): boolean {
  return /modified concurrently|cycle changed/i.test(error.message)
}

export function SubscriptionsPanel() {
  const query = useSubscriptions()
  const suggestionsQuery = useRecurringSuggestions()
  const subscriptions = useMemo(() => query.data ?? [], [query.data])
  const categories = useCategories('EXPENSE').data ?? EMPTY_CATEGORIES
  const [search, setSearch] = useState('')
  const [status, setStatus] = useState<StatusFilter>('ALL')
  const [editor, setEditor] = useState<{ subscription?: Subscription; suggestion?: RecurringChargeSuggestion } | null>(null)
  const [paying, setPaying] = useState<Subscription | null>(null)
  const [deleting, setDeleting] = useState<Subscription | null>(null)

  const filtered = useMemo(() => {
    const term = search.trim().toLocaleLowerCase()
    return subscriptions.filter((subscription) => {
      if (status === 'ACTIVE' && !subscription.active) return false
      if (status === 'PAUSED' && subscription.active) return false
      return !term
        || subscription.name.toLocaleLowerCase().includes(term)
        || subscription.category.toLocaleLowerCase().includes(term)
    })
  }, [search, status, subscriptions])
  const active = subscriptions.filter((item) => item.active)
  const monthlyTotal = active.reduce((sum, item) => sum + item.monthlyEquivalent, 0)
  const nextCharge = active.toSorted((a, b) => a.nextDueOn.localeCompare(b.nextDueOn))[0]

  return (
    <div className="flex flex-col gap-4">
      <div className="grid grid-cols-1 gap-2 sm:grid-cols-3">
        <SubscriptionMetric icon={Banknote} label="Monthly cost" value={vnd(monthlyTotal)} />
        <SubscriptionMetric icon={CircleCheck} label="Active subscriptions" value={String(active.length)} />
        <SubscriptionMetric icon={CalendarClock} label="Next charge" value={nextCharge ? formatFullDate(nextCharge.nextDueOn) : 'None'} />
      </div>

      {suggestionsQuery.data && suggestionsQuery.data.length > 0 && (
        <RecurringSuggestions suggestions={suggestionsQuery.data} onTrack={(suggestion) => setEditor({ suggestion })} />
      )}

      <div className="flex flex-col gap-2 sm:flex-row sm:items-center">
        <div className="relative min-w-0 flex-1">
          <Search className="pointer-events-none absolute left-2.5 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
          <Input aria-label="Search subscriptions" value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Search subscriptions" className="pl-8" />
        </div>
        <div className="grid h-9 grid-cols-3 rounded-lg border bg-card p-0.5" role="group" aria-label="Filter subscriptions by status">
          {(['ALL', 'ACTIVE', 'PAUSED'] as const).map((item) => (
            <button
              key={item}
              type="button"
              aria-pressed={status === item}
              onClick={() => setStatus(item)}
              className={cn(
                'min-w-16 rounded-md px-2 text-xs font-medium transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring',
                status === item ? 'bg-foreground text-background' : 'text-muted-foreground hover:text-foreground',
              )}
            >
              {item === 'ALL' ? 'All' : item === 'ACTIVE' ? 'Active' : 'Paused'}
            </button>
          ))}
        </div>
        <Button size="sm" className="h-9" onClick={() => setEditor({})}><Plus className="size-4" /> Add subscription</Button>
      </div>

      {query.isLoading ? (
        <div className="overflow-hidden rounded-lg border bg-card">{Array.from({ length: 5 }, (_, index) => <div key={index} className="flex items-center gap-3 border-b p-4 last:border-b-0"><Skeleton className="size-10 rounded-lg" /><Skeleton className="h-8 flex-1" /><Skeleton className="h-8 w-32" /></div>)}</div>
      ) : query.error ? (
        <div className="rounded-lg border border-destructive/40 bg-destructive/5 p-4 text-sm text-destructive">Could not load subscriptions.</div>
      ) : filtered.length === 0 ? (
        <div className="flex min-h-48 flex-col items-center justify-center gap-2 rounded-lg border border-dashed bg-muted/10 p-6 text-center">
          <Repeat2 className="size-5 text-muted-foreground" />
          <p className="text-sm font-medium">No matching subscriptions</p>
          <p className="text-xs text-muted-foreground">Keep recurring charges here — they post to the ledger automatically on their due date.</p>
          <Button variant="outline" size="sm" onClick={() => setEditor({})}><Plus className="size-4" /> Add subscription</Button>
        </div>
      ) : (
        <div className="overflow-hidden rounded-lg border bg-card">
          {filtered.map((subscription) => (
            <SubscriptionRow
              key={subscription.id}
              subscription={subscription}
              onPay={() => setPaying(subscription)}
              onEdit={() => setEditor({ subscription })}
              onDelete={() => setDeleting(subscription)}
            />
          ))}
        </div>
      )}

      <SubscriptionDialog
        key={editor?.subscription?.id ?? editor?.suggestion?.key ?? (editor ? 'new-subscription' : 'closed-subscription')}
        editor={editor}
        categories={categories}
        onClose={() => setEditor(null)}
      />
      <PaymentDialog key={paying?.id ?? 'closed-payment'} subscription={paying} onClose={() => setPaying(null)} />
      <DeleteSubscriptionDialog subscription={deleting} onClose={() => setDeleting(null)} />
    </div>
  )
}

function RecurringSuggestions({ suggestions, onTrack }: {
  suggestions: RecurringChargeSuggestion[]
  onTrack: (suggestion: RecurringChargeSuggestion) => void
}) {
  return (
    <section className="overflow-hidden rounded-lg border bg-card" aria-labelledby="recurring-suggestions-title">
      <div className="flex items-center gap-2 border-b bg-muted/25 px-3 py-2.5 sm:px-4">
        <Sparkles className="size-4 text-amber-600 dark:text-amber-400" />
        <div className="min-w-0"><h2 id="recurring-suggestions-title" className="text-sm font-semibold">Recurring patterns found</h2><p className="text-[11px] text-muted-foreground">Review before tracking. Nothing is added automatically.</p></div>
      </div>
      <div className="divide-y">
        {suggestions.slice(0, 4).map((suggestion, index) => (
          <m.article key={suggestion.key} initial={{ opacity: 0, y: 6 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: index * 0.05 }} className="flex flex-col gap-2 p-3 sm:flex-row sm:items-center sm:px-4">
            <div className="min-w-0 flex-1"><p className="truncate text-sm font-medium">{suggestion.name}</p><p className="text-xs text-muted-foreground">{suggestion.occurrences} matching charges · next around {formatFullDate(suggestion.nextExpectedOn)}</p></div>
            <div className="flex items-center justify-between gap-3 sm:justify-end"><div className="text-right"><p className="text-sm font-semibold tabular-nums">{vnd(suggestion.amount)}</p><p className="text-[10px] capitalize text-muted-foreground">{suggestion.cycle.toLocaleLowerCase()}</p></div><Button size="sm" variant="outline" onClick={() => onTrack(suggestion)}><Plus className="size-4" /> Track</Button></div>
          </m.article>
        ))}
      </div>
    </section>
  )
}

function SubscriptionMetric({ icon: Icon, label, value }: { icon: typeof Banknote; label: string; value: string }) {
  return (
    <div className="flex min-w-0 items-center gap-3 rounded-lg border bg-card p-3">
      <div className="flex size-9 shrink-0 items-center justify-center rounded-full bg-primary/10 text-primary"><Icon className="size-4" /></div>
      <div className="min-w-0"><p className="text-xs text-muted-foreground">{label}</p><p className="truncate text-sm font-semibold tabular-nums sm:text-base">{value}</p></div>
    </div>
  )
}

function SubscriptionRow({ subscription, onPay, onEdit, onDelete }: {
  subscription: Subscription
  onPay: () => void
  onEdit: () => void
  onDelete: () => void
}) {
  const due = dueState(subscription)
  return (
    <article className="flex flex-col gap-3 border-b p-3 last:border-b-0 sm:flex-row sm:items-center sm:p-4">
      <div className="flex min-w-0 flex-1 items-center gap-3">
        <div className={cn('flex size-10 shrink-0 items-center justify-center rounded-lg', subscription.active ? 'bg-primary/10 text-primary' : 'bg-muted text-muted-foreground')}>
          {subscription.active ? <Repeat2 className="size-5" /> : <CirclePause className="size-5" />}
        </div>
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-2">
            <h3 className="truncate text-sm font-semibold">{subscription.name}</h3>
            <Badge variant="secondary" className="h-5 rounded px-1.5 text-[10px] font-normal">{subscription.category}</Badge>
            {subscription.cancelReminderOn && (
              <Badge variant="outline" className="h-5 rounded border-amber-300 bg-amber-50 px-1.5 text-[10px] font-normal text-amber-800 dark:border-amber-800 dark:bg-amber-950 dark:text-amber-300" title="A reminder task is created for this date">
                Cancel by {formatFullDate(subscription.cancelReminderOn)}
              </Badge>
            )}
          </div>
          <p className="mt-0.5 text-xs text-muted-foreground">Auto-charges {formatFullDate(subscription.nextDueOn)} · {subscription.cycle === 'MONTHLY' ? 'Monthly' : 'Yearly'}</p>
        </div>
      </div>
      <div className="flex items-center justify-between gap-3 pl-13 sm:justify-end sm:pl-0">
        <div className="text-right">
          <p className="text-sm font-semibold tabular-nums">{vnd(subscription.amount)}</p>
          <p className={cn('text-[11px]', due.tone)}>{subscription.active ? due.label : 'Paused'}</p>
        </div>
        <DropdownMenu>
          <DropdownMenuTrigger asChild><Button variant="ghost" size="icon" className="size-8" aria-label={`Actions for ${subscription.name}`}><Ellipsis className="size-4" /></Button></DropdownMenuTrigger>
          <DropdownMenuContent align="end">
            <DropdownMenuItem onClick={onEdit}><Pencil className="size-4" /> Edit</DropdownMenuItem>
            {subscription.active && (
              <DropdownMenuItem onClick={onPay}><CircleCheck className="size-4" /> Mark paid manually</DropdownMenuItem>
            )}
            <DropdownMenuItem className="text-destructive focus:text-destructive" onClick={onDelete}><Trash2 className="size-4" /> Delete</DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </div>
    </article>
  )
}

function dueState(subscription: Subscription): { label: string; tone: string } {
  const dayNumber = (iso: string) => {
    const [year, month, day] = iso.split('-').map(Number)
    return Date.UTC(year, month - 1, day) / 86_400_000
  }
  const days = dayNumber(subscription.nextDueOn) - dayNumber(todayIso())
  if (days < 0) return { label: 'Posting…', tone: 'text-muted-foreground' }
  if (days === 0) return { label: 'Charges today', tone: 'text-amber-600 dark:text-amber-400' }
  if (days <= 7) return { label: `Charges in ${days}d`, tone: 'text-amber-600 dark:text-amber-400' }
  return { label: `Charges in ${days}d`, tone: 'text-muted-foreground' }
}

function SubscriptionDialog({ editor, categories, onClose }: {
  editor: { subscription?: Subscription; suggestion?: RecurringChargeSuggestion } | null
  categories: string[]
  onClose: () => void
}) {
  const subscription = editor?.subscription
  const suggestion = editor?.suggestion
  const [name, setName] = useState(subscription?.name ?? suggestion?.name ?? '')
  const [amount, setAmount] = useState(subscription ? String(subscription.amount) : suggestion ? String(suggestion.amount) : '')
  const [category, setCategory] = useState(subscription?.category ?? suggestion?.category ?? categories[0] ?? 'Khác')
  const [cycle, setCycle] = useState<'MONTHLY' | 'YEARLY'>(subscription?.cycle ?? suggestion?.cycle ?? 'MONTHLY')
  const [nextDueOn, setNextDueOn] = useState(subscription?.nextDueOn ?? suggestion?.nextExpectedOn ?? todayIso())
  const [active, setActive] = useState(subscription?.active ?? true)
  const [cancelReminderOn, setCancelReminderOn] = useState(subscription?.cancelReminderOn ?? '')
  const categoryOptions = useMemo(
    () => Array.from(new Set([subscription?.category, suggestion?.category, ...categories, 'Khác'].filter((item): item is string => Boolean(item)))),
    [subscription?.category, suggestion?.category, categories],
  )
  const create = useCreateSubscription()
  const update = useUpdateSubscription()
  const pending = create.isPending || update.isPending

  function save() {
    const parsed = Number(amount)
    if (!name.trim() || !isWholeVnd(parsed) || !category || !nextDueOn) {
      toast.error('Enter a name, positive VND amount, category, and due date')
      return
    }
    const body = {
      name: name.trim(), amount: parsed, category, cycle, nextDueOn, active,
      cancelReminderOn: cancelReminderOn || undefined,
    }
    const options = {
      onSuccess: () => { toast.success(subscription ? 'Subscription updated' : 'Subscription created'); onClose() },
      onError: (error: Error) => {
        toast.error(error.message)
        if (isSubscriptionConflict(error)) onClose()
      },
    }
    if (subscription) update.mutate({ id: subscription.id, version: subscription.version, ...body }, options)
    else create.mutate(body, options)
  }

  return (
    <Dialog open={Boolean(editor)} onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="max-h-[calc(100dvh-2rem)] overflow-y-auto sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{subscription ? 'Edit subscription' : 'Add subscription'}</DialogTitle>
          <DialogDescription className={suggestion ? '' : 'sr-only'}>{suggestion ? `Prefilled from ${suggestion.occurrences} similar ledger entries. Confirm the schedule before saving.` : 'Set the charge amount, billing cycle, next due date, and active state.'}</DialogDescription>
        </DialogHeader>
        <div className="grid gap-3">
          <div className="grid gap-1.5"><Label htmlFor="subscription-name">Name</Label><Input id="subscription-name" value={name} onChange={(event) => setName(event.target.value)} placeholder="ChatGPT Plus" /></div>
          <div className="grid grid-cols-2 gap-3">
            <div className="grid gap-1.5"><Label htmlFor="subscription-amount">Amount (VND)</Label><Input id="subscription-amount" inputMode="numeric" value={amount} onChange={(event) => setAmount(event.target.value)} /></div>
            <div className="grid gap-1.5"><Label htmlFor="subscription-cycle">Cycle</Label><Select value={cycle} onValueChange={(value) => setCycle(value as 'MONTHLY' | 'YEARLY')}><SelectTrigger id="subscription-cycle"><SelectValue /></SelectTrigger><SelectContent><SelectItem value="MONTHLY">Monthly</SelectItem><SelectItem value="YEARLY">Yearly</SelectItem></SelectContent></Select></div>
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div className="grid gap-1.5"><Label htmlFor="subscription-category">Category</Label><Select value={category} onValueChange={setCategory}><SelectTrigger id="subscription-category"><SelectValue /></SelectTrigger><SelectContent>{categoryOptions.map((item) => <SelectItem key={item} value={item}>{item}</SelectItem>)}</SelectContent></Select></div>
            <div className="grid gap-1.5"><Label htmlFor="subscription-due">Next due</Label><Input id="subscription-due" type="date" value={nextDueOn} onChange={(event) => setNextDueOn(event.target.value)} /></div>
          </div>
          <div className="grid gap-1.5">
            <Label htmlFor="subscription-cancel-reminder">Remind me to cancel on</Label>
            <Input id="subscription-cancel-reminder" type="date" value={cancelReminderOn} onChange={(event) => setCancelReminderOn(event.target.value)} />
            <p className="text-xs text-muted-foreground">Optional — a task is created for that day (trial ending, planned cancellation). Leave empty for none.</p>
          </div>
          <div className="flex items-center justify-between rounded-lg border p-3"><div><Label htmlFor="subscription-active">Active subscription</Label><p className="text-xs text-muted-foreground">Paused items stay visible and never auto-charge</p></div><Switch id="subscription-active" checked={active} onCheckedChange={setActive} /></div>
        </div>
        <DialogFooter><Button variant="outline" onClick={onClose}>Cancel</Button><Button disabled={pending} onClick={save}>Save subscription</Button></DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

function PaymentDialog({ subscription, onClose }: { subscription: Subscription | null; onClose: () => void }) {
  const [occurredOn, setOccurredOn] = useState(todayIso())
  const pay = usePaySubscription()
  return (
    <Dialog open={Boolean(subscription)} onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="sm:max-w-sm">
        <DialogHeader>
          <DialogTitle>Mark subscription paid?</DialogTitle>
          <DialogDescription className="sr-only">Create one expense and advance this subscription to its next billing cycle.</DialogDescription>
        </DialogHeader>
        <p className="text-sm text-muted-foreground">{subscription ? `${subscription.name} · ${vnd(subscription.amount)}` : ''}</p>
        <div className="grid gap-1.5"><Label htmlFor="payment-date">Payment date</Label><Input id="payment-date" type="date" max={todayIso()} value={occurredOn} onChange={(event) => setOccurredOn(event.target.value)} /></div>
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>Cancel</Button>
          <Button disabled={pay.isPending || !occurredOn} onClick={() => subscription && pay.mutate({
            id: subscription.id,
            occurredOn,
            expectedDueOn: subscription.nextDueOn,
            version: subscription.version,
          }, {
            onSuccess: () => { toast.success('Payment added to transactions'); onClose() },
            onError: (error) => {
              toast.error(error.message)
              if (isSubscriptionConflict(error)) onClose()
            },
          })}><CircleCheck className="size-4" /> Mark paid</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

function DeleteSubscriptionDialog({ subscription, onClose }: { subscription: Subscription | null; onClose: () => void }) {
  const remove = useDeleteSubscription()
  return (
    <Dialog open={Boolean(subscription)} onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="sm:max-w-sm">
        <DialogHeader>
          <DialogTitle>Delete subscription?</DialogTitle>
          <DialogDescription className="sr-only">Delete the recurring definition without removing past transactions.</DialogDescription>
        </DialogHeader>
        <p className="text-sm text-muted-foreground">{subscription?.name ?? ''}</p>
        <DialogFooter><Button variant="outline" onClick={onClose}>Cancel</Button><Button variant="destructive" disabled={remove.isPending} onClick={() => subscription && remove.mutate(subscription.id, { onSuccess: () => { toast.success('Subscription deleted'); onClose() }, onError: (error) => toast.error(error.message) })}>Delete</Button></DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
