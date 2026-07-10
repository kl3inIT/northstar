import { useMemo, useState } from 'react'
import { toast } from 'sonner'
import {
  ArrowDownLeft,
  ArrowUpDown,
  ArrowUpRight,
  Download,
  Ellipsis,
  LockKeyhole,
  Pencil,
  Scale,
  Search,
  Sparkles,
  Trash2,
  TrendingUp,
} from 'lucide-react'
import {
  createColumnHelper,
  flexRender,
  getCoreRowModel,
  getSortedRowModel,
  useReactTable,
  type SortingState,
} from '@tanstack/react-table'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { CountUp } from '@/components/motion'
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
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { cn } from '@/lib/utils'
import {
  useCategories,
  useBalanceCheckIns,
  useCreateBalanceCheckIn,
  useDeleteTransaction,
  useMonthSummary,
  useTransactions,
  useUpdateTransaction,
  type Transaction,
  type TransactionType,
} from '@/lib/finance-api'
import { formatDay, formatFullDate, todayIso, vnd } from './format'

type TypeFilter = 'ALL' | TransactionType
const EMPTY_TRANSACTIONS: Transaction[] = []

export function TransactionsPanel({ month }: { month: string }) {
  const transactions = useTransactions(month)
  const summaryQuery = useMonthSummary(month)
  const rows = transactions.data ?? EMPTY_TRANSACTIONS
  const summary = summaryQuery.data
  const [search, setSearch] = useState('')
  const [category, setCategory] = useState('ALL')
  const [type, setType] = useState<TypeFilter>('ALL')
  const [editing, setEditing] = useState<Transaction | null>(null)
  const [deleting, setDeleting] = useState<Transaction | null>(null)
  const [reconciling, setReconciling] = useState(false)

  const categories = useMemo(
    () => Array.from(new Set(rows.map((row) => row.category))).sort((a, b) => a.localeCompare(b)),
    [rows],
  )
  const filtered = useMemo(() => {
    const query = search.trim().toLocaleLowerCase()
    return rows.filter((row) => {
      if (type !== 'ALL' && row.type !== type) return false
      if (category !== 'ALL' && row.category !== category) return false
      return !query
        || row.description.toLocaleLowerCase().includes(query)
        || row.category.toLocaleLowerCase().includes(query)
    })
  }, [category, rows, search, type])

  return (
    <div className="flex flex-col gap-4">
      <TransactionStats summary={summary} />

      <div className="flex flex-col gap-2 lg:flex-row lg:items-center">
        <div className="relative min-w-0 flex-1">
          <Search className="pointer-events-none absolute left-2.5 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            aria-label="Search transactions"
            value={search}
            onChange={(event) => setSearch(event.target.value)}
            placeholder="Search description or category"
            className="pl-8"
          />
        </div>
        <Select value={category} onValueChange={setCategory}>
          <SelectTrigger className="w-full lg:w-44" aria-label="Filter by category">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="ALL">All categories</SelectItem>
            {categories.map((item) => <SelectItem key={item} value={item}>{item}</SelectItem>)}
          </SelectContent>
        </Select>
        <div className="grid h-9 w-full grid-cols-3 rounded-lg border bg-card p-0.5 lg:w-auto" role="group" aria-label="Filter by transaction type">
          {(['ALL', 'INCOME', 'EXPENSE'] as const).map((item) => (
            <button
              key={item}
              type="button"
              onClick={() => setType(item)}
              aria-pressed={type === item}
              className={cn(
                'min-w-0 rounded-md px-2 text-xs font-medium transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring lg:min-w-16',
                type === item ? 'bg-foreground text-background' : 'text-muted-foreground hover:text-foreground',
              )}
            >
              {item === 'ALL' ? 'All' : item === 'INCOME' ? 'Income' : 'Expense'}
            </button>
          ))}
        </div>
        <Button type="button" variant="outline" size="sm" className="h-9" disabled={rows.length === 0} onClick={() => exportCsv(rows, month)}>
          <Download className="size-4" /> Export CSV
        </Button>
        <Button type="button" size="sm" className="h-9" onClick={() => setReconciling(true)}>
          <Scale className="size-4" /> Balance check-in
        </Button>
      </div>

      {transactions.error || summaryQuery.error ? (
        <div className="rounded-lg border border-destructive/40 bg-destructive/5 p-4 text-sm text-destructive">
          Could not load finance data. Refresh the page to retry.
        </div>
      ) : (
        <TransactionsTable
          rows={filtered}
          isLoading={transactions.isLoading}
          onEdit={setEditing}
          onDelete={setDeleting}
        />
      )}

      <EditTransactionDialog
        key={editing?.id ?? 'none'}
        transaction={editing}
        onClose={() => setEditing(null)}
      />
      <DeleteTransactionDialog transaction={deleting} onClose={() => setDeleting(null)} />
      <BalanceCheckInDialog open={reconciling} onClose={() => setReconciling(false)} />
    </div>
  )
}

function TransactionStats({ summary }: {
  summary?: ReturnType<typeof useMonthSummary>['data']
}) {
  const values = summary ?? {
    month: '', expenseTotal: 0, incomeTotal: 0, net: 0, exceptionalTotal: 0,
    exceptionalCount: 0, previousMonthExpenseTotal: 0, categories: [],
  }
  const stats = [
    { label: 'Total in', value: values.incomeTotal, format: vnd, icon: ArrowDownLeft, tone: 'text-emerald-600 dark:text-emerald-400', bg: 'bg-emerald-500/10' },
    { label: 'Total out', value: values.expenseTotal, format: vnd, icon: ArrowUpRight, tone: 'text-rose-600 dark:text-rose-400', bg: 'bg-rose-500/10' },
    { label: 'Net', value: values.net, format: (value: number) => `${value >= 0 ? '+' : ''}${vnd(value)}`, icon: TrendingUp, tone: values.net >= 0 ? 'text-primary' : 'text-destructive', bg: 'bg-primary/10' },
    { label: 'One-off', value: values.exceptionalTotal, format: vnd, icon: Sparkles, tone: 'text-amber-600 dark:text-amber-400', bg: 'bg-amber-500/10' },
  ]
  return (
    <div className="grid grid-cols-2 gap-2 lg:grid-cols-4">
      {stats.map((stat, index) => (
        <m.div key={stat.label} initial={{ opacity: 0, y: 6 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: index * 0.04 }} className="flex min-w-0 items-center gap-3 rounded-lg border bg-card p-3">
          <div className={cn('hidden size-9 shrink-0 items-center justify-center rounded-full xl:flex', stat.bg)}>
            <stat.icon className={cn('size-4', stat.tone)} />
          </div>
          <div className="min-w-0">
            <p className="text-xs text-muted-foreground">{stat.label}</p>
            <p className="truncate text-sm font-semibold tabular-nums sm:text-base" title={stat.format(stat.value)}><CountUp value={stat.value} format={stat.format} /></p>
            {index === 3 && (
              <p className="truncate text-[11px] text-muted-foreground">
                {values.exceptionalCount} one-off {values.exceptionalCount === 1 ? 'entry' : 'entries'} this month
              </p>
            )}
          </div>
        </m.div>
      ))}
    </div>
  )
}

const columnHelper = createColumnHelper<Transaction>()

function TransactionsTable({ rows, isLoading, onEdit, onDelete }: {
  rows: Transaction[]
  isLoading: boolean
  onEdit: (transaction: Transaction) => void
  onDelete: (transaction: Transaction) => void
}) {
  const [sorting, setSorting] = useState<SortingState>([])
  const columns = useMemo(() => [
    columnHelper.accessor('occurredOn', {
      header: ({ column }) => (
        <SortButton label="Date" sorted={column.getIsSorted()} onClick={column.getToggleSortingHandler()} />
      ),
      cell: (info) => <span className="text-xs text-muted-foreground">{formatDay(info.getValue())}</span>,
    }),
    columnHelper.accessor('description', {
      header: 'Description',
      enableSorting: false,
      cell: (info) => (
        <div className="min-w-44 max-w-md">
          <p className="truncate text-sm font-medium">{info.getValue()}</p>
          <div className="mt-1 flex items-center gap-1">
            <Badge variant="secondary" className="h-5 rounded px-1.5 text-[10px] font-normal">{info.row.original.category}</Badge>
            {info.row.original.exceptional && (
              <Badge variant="outline" className="h-5 rounded border-amber-400/50 px-1.5 text-[10px] font-normal text-amber-700 dark:text-amber-300">one-off</Badge>
            )}
          </div>
        </div>
      ),
    }),
    columnHelper.accessor('source', {
      header: 'Source',
      enableSorting: false,
      cell: (info) => <span className="text-xs capitalize text-muted-foreground">{info.getValue().toLocaleLowerCase()}</span>,
    }),
    columnHelper.accessor('amount', {
      header: ({ column }) => (
        <SortButton label="Amount" align="right" sorted={column.getIsSorted()} onClick={column.getToggleSortingHandler()} />
      ),
      cell: (info) => {
        const transaction = info.row.original
        return (
          <span className={cn('block text-right text-sm font-semibold tabular-nums', transaction.type === 'INCOME' && 'text-emerald-600 dark:text-emerald-400')}>
            {transaction.type === 'INCOME' ? '+' : '-'}{vnd(transaction.amount)}
          </span>
        )
      },
    }),
    columnHelper.display({
      id: 'actions',
      header: '',
      cell: (info) => (
        info.row.original.source === 'RECONCILIATION' ? (
          <span className="inline-flex items-center gap-1 text-[11px] text-muted-foreground"><LockKeyhole className="size-3" /> Locked</span>
        ) : (
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <Button variant="ghost" size="icon" className="size-8" aria-label={`Actions for ${info.row.original.description}`}>
              <Ellipsis className="size-4" />
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end">
            <DropdownMenuItem onClick={() => onEdit(info.row.original)}><Pencil className="size-4" /> Edit</DropdownMenuItem>
            <DropdownMenuItem className="text-destructive focus:text-destructive" onClick={() => onDelete(info.row.original)}><Trash2 className="size-4" /> Delete</DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
        )
      ),
    }),
  ], [onDelete, onEdit])
  const table = useReactTable({
    data: rows,
    columns,
    state: { sorting },
    onSortingChange: setSorting,
    getCoreRowModel: getCoreRowModel(),
    getSortedRowModel: getSortedRowModel(),
  })

  return (
    <div className="overflow-hidden rounded-lg border bg-card">
      <div className="divide-y md:hidden">
        {isLoading && Array.from({ length: 5 }, (_, index) => (
          <div key={index} className="flex min-h-20 items-center gap-3 p-3">
            <Skeleton className="h-4 w-12 shrink-0" />
            <div className="min-w-0 flex-1 space-y-2"><Skeleton className="h-4 w-36" /><Skeleton className="h-4 w-20" /></div>
            <Skeleton className="h-4 w-20 shrink-0" />
          </div>
        ))}
        {!isLoading && rows.length === 0 && <EmptyTransactions />}
        {!isLoading && rows.map((transaction) => (
          <article key={transaction.id} className="grid grid-cols-[auto_minmax(0,1fr)_auto] items-center gap-3 p-3">
            <time dateTime={transaction.occurredOn} className="w-12 text-xs text-muted-foreground">
              {formatDay(transaction.occurredOn)}
            </time>
            <div className="min-w-0">
              <p className="truncate text-sm font-medium">{transaction.description}</p>
              <div className="mt-1 flex min-w-0 items-center gap-1">
                <Badge variant="secondary" className="h-5 max-w-full truncate rounded px-1.5 text-[10px] font-normal">
                  {transaction.category}
                </Badge>
                {transaction.exceptional && (
                  <Badge variant="outline" className="h-5 shrink-0 rounded border-amber-400/50 px-1.5 text-[10px] font-normal text-amber-700 dark:text-amber-300">
                    one-off
                  </Badge>
                )}
              </div>
            </div>
            <div className="flex shrink-0 items-center gap-1">
              <span className={cn('max-w-28 truncate text-right text-sm font-semibold tabular-nums', transaction.type === 'INCOME' && 'text-emerald-600 dark:text-emerald-400')} title={vnd(transaction.amount)}>
                {transaction.type === 'INCOME' ? '+' : '-'}{vnd(transaction.amount)}
              </span>
              <TransactionActions transaction={transaction} onEdit={onEdit} onDelete={onDelete} />
            </div>
          </article>
        ))}
      </div>

      <div className="hidden md:block">
        <Table>
        <TableHeader>
          {table.getHeaderGroups().map((group) => (
            <TableRow key={group.id}>
              {group.headers.map((header) => (
                <TableHead
                  key={header.id}
                  className={header.column.id === 'source' ? 'hidden md:table-cell' : undefined}
                  aria-sort={header.column.getIsSorted() === 'asc' ? 'ascending' : header.column.getIsSorted() === 'desc' ? 'descending' : undefined}
                >
                  {flexRender(header.column.columnDef.header, header.getContext())}
                </TableHead>
              ))}
            </TableRow>
          ))}
        </TableHeader>
        <TableBody>
          {isLoading && Array.from({ length: 5 }, (_, index) => (
            <TableRow key={index}>
              <TableCell><Skeleton className="h-4 w-14" /></TableCell>
              <TableCell><Skeleton className="h-9 w-52" /></TableCell>
              <TableCell className="hidden md:table-cell"><Skeleton className="h-4 w-16" /></TableCell>
              <TableCell><Skeleton className="ml-auto h-4 w-24" /></TableCell>
              <TableCell><Skeleton className="h-8 w-8" /></TableCell>
            </TableRow>
          ))}
          {!isLoading && rows.length === 0 && (
            <TableRow>
              <TableCell colSpan={5} className="h-40 text-center">
                <EmptyTransactions compact />
              </TableCell>
            </TableRow>
          )}
          {!isLoading && table.getRowModel().rows.map((row) => (
            <TableRow key={row.id}>
              {row.getVisibleCells().map((cell) => (
                <TableCell key={cell.id} className={cell.column.id === 'source' ? 'hidden md:table-cell' : undefined}>
                  {flexRender(cell.column.columnDef.cell, cell.getContext())}
                </TableCell>
              ))}
            </TableRow>
          ))}
        </TableBody>
        </Table>
      </div>
    </div>
  )
}

function EmptyTransactions({ compact = false }: { compact?: boolean }) {
  return (
    <div className={cn('flex flex-col items-center justify-center gap-1.5 text-center', compact ? '' : 'min-h-40 p-6')}>
      <p className="text-sm font-medium">No transactions match this view</p>
    </div>
  )
}

function TransactionActions({ transaction, onEdit, onDelete }: {
  transaction: Transaction
  onEdit: (transaction: Transaction) => void
  onDelete: (transaction: Transaction) => void
}) {
  if (transaction.source === 'RECONCILIATION') {
    return <span className="flex size-8 items-center justify-center text-muted-foreground" title="Managed by balance check-in"><LockKeyhole className="size-3.5" /></span>
  }
  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button variant="ghost" size="icon" className="size-8" aria-label={`Actions for ${transaction.description}`}>
          <Ellipsis className="size-4" />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end">
        <DropdownMenuItem onClick={() => onEdit(transaction)}><Pencil className="size-4" /> Edit</DropdownMenuItem>
        <DropdownMenuItem variant="destructive" onClick={() => onDelete(transaction)}><Trash2 className="size-4" /> Delete</DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  )
}

function BalanceCheckInDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  const history = useBalanceCheckIns()
  const create = useCreateBalanceCheckIn()
  const latest = history.data?.[0]
  const [actualBalance, setActualBalance] = useState('')
  const [checkedOn, setCheckedOn] = useState(todayIso())

  function submit() {
    const actual = Number(actualBalance)
    if (!Number.isSafeInteger(actual) || actual < 0 || !checkedOn) {
      toast.error('Enter a non-negative whole-VND balance and date')
      return
    }
    create.mutate({ actualBalance: actual, checkedOn }, {
      onSuccess: (result) => {
        if (!result.adjustment) toast.success(latest ? 'Ledger already matches your balance' : 'Balance baseline saved')
        else if (result.discrepancy < 0) toast.success(`${vnd(Math.abs(result.discrepancy))} added as unrecorded spending`)
        else toast.success(`${vnd(result.discrepancy)} added as unrecorded income`)
        setActualBalance('')
        onClose()
      },
      onError: (error) => toast.error(error.message),
    })
  }

  return (
    <Dialog open={open} onOpenChange={(value) => !value && onClose()}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>Balance check-in</DialogTitle>
          <DialogDescription>Enter the combined end-of-day balance across your wallets and accounts.</DialogDescription>
        </DialogHeader>
        {latest && (
          <div className="flex items-center justify-between rounded-lg border bg-muted/30 p-3 text-sm">
            <div><p className="font-medium">Last check-in</p><p className="text-xs text-muted-foreground">{formatFullDate(latest.checkedOn)}</p></div>
            <p className="font-semibold tabular-nums">{vnd(latest.actualBalance)}</p>
          </div>
        )}
        <div className="grid gap-3 sm:grid-cols-2">
          <div className="grid gap-1.5"><Label htmlFor="check-in-balance">Actual balance (VND)</Label><Input id="check-in-balance" autoFocus inputMode="numeric" placeholder="0" value={actualBalance} onChange={(event) => setActualBalance(event.target.value)} /></div>
          <div className="grid gap-1.5"><Label htmlFor="check-in-date">End-of-day date</Label><Input id="check-in-date" type="date" max={todayIso()} value={checkedOn} onChange={(event) => setCheckedOn(event.target.value)} /></div>
        </div>
        <p className="text-xs leading-relaxed text-muted-foreground">Northstar compares this with the prior check-in plus recorded income and spending. Any difference becomes a locked adjustment transaction in category Khác.</p>
        <DialogFooter><Button variant="outline" onClick={onClose}>Cancel</Button><Button onClick={submit} disabled={create.isPending}>Reconcile balance</Button></DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

function exportCsv(rows: Transaction[], month: string) {
  const header = ['Date', 'Type', 'Description', 'Category', 'Amount VND', 'One-off', 'Source']
  const body = rows.map((row) => [row.occurredOn, row.type, row.description, row.category, String(row.amount), row.exceptional ? 'yes' : 'no', row.source])
  const csv = `\uFEFF${[header, ...body].map((line) => line.map(csvCell).join(',')).join('\r\n')}`
  const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }))
  const link = document.createElement('a')
  link.href = url
  link.download = `northstar-transactions-${month}.csv`
  link.click()
  URL.revokeObjectURL(url)
}

function csvCell(value: string): string {
  return /[",\r\n]/.test(value) ? `"${value.replaceAll('"', '""')}"` : value
}

function SortButton({ label, align, sorted, onClick }: {
  label: string
  align?: 'right'
  sorted: false | 'asc' | 'desc'
  onClick?: (event: unknown) => void
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={cn('inline-flex items-center gap-1 text-xs font-medium hover:text-foreground', align === 'right' && 'ml-auto flex')}
      aria-label={`Sort by ${label}`}
    >
      {label}<ArrowUpDown className={cn('size-3.5', sorted && 'text-foreground')} />
    </button>
  )
}

function EditTransactionDialog({ transaction, onClose }: { transaction: Transaction | null; onClose: () => void }) {
  return (
    <Dialog open={Boolean(transaction)} onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="sm:max-w-md">
        {transaction && <EditTransactionForm transaction={transaction} onClose={onClose} />}
      </DialogContent>
    </Dialog>
  )
}

function EditTransactionForm({ transaction, onClose }: { transaction: Transaction; onClose: () => void }) {
  const [amount, setAmount] = useState(String(transaction.amount))
  const [occurredOn, setOccurredOn] = useState(transaction.occurredOn)
  const [description, setDescription] = useState(transaction.description)
  const [category, setCategory] = useState(transaction.category)
  const [exceptional, setExceptional] = useState(transaction.exceptional)
  const categories = useCategories(transaction.type).data ?? []
  const update = useUpdateTransaction()
  const options = categories.includes(category) ? categories : [category, ...categories]

  function save() {
    const parsedAmount = Number(amount)
    if (!Number.isInteger(parsedAmount) || parsedAmount <= 0 || !description.trim() || !occurredOn) {
      toast.error('Enter a positive VND amount, date, and description')
      return
    }
    update.mutate(
      { id: transaction.id, amount: parsedAmount, occurredOn, description: description.trim(), category, exceptional },
      {
        onSuccess: () => { toast.success('Transaction updated'); onClose() },
        onError: (error) => toast.error(error.message),
      },
    )
  }

  return (
    <>
      <DialogHeader><DialogTitle>Edit transaction</DialogTitle></DialogHeader>
      <div className="grid gap-3">
        <div className="grid gap-3 sm:grid-cols-2">
          <div className="grid gap-1.5"><Label htmlFor="tx-amount">Amount (VND)</Label><Input id="tx-amount" inputMode="numeric" value={amount} onChange={(event) => setAmount(event.target.value)} /></div>
          <div className="grid gap-1.5"><Label htmlFor="tx-date">Date</Label><Input id="tx-date" type="date" value={occurredOn} onChange={(event) => setOccurredOn(event.target.value)} /></div>
        </div>
        <div className="grid gap-1.5"><Label htmlFor="tx-description">Description</Label><Input id="tx-description" value={description} onChange={(event) => setDescription(event.target.value)} /></div>
        <div className="grid gap-1.5"><Label htmlFor="tx-category">Category</Label><Select value={category} onValueChange={setCategory}><SelectTrigger id="tx-category"><SelectValue /></SelectTrigger><SelectContent>{options.map((item) => <SelectItem key={item} value={item}>{item}</SelectItem>)}</SelectContent></Select></div>
        <div className="flex items-center justify-between rounded-lg border p-3">
          <div><Label htmlFor="tx-exceptional">One-off purchase</Label><p className="text-xs text-muted-foreground">Separate this from routine spending in reviews</p></div>
          <Switch id="tx-exceptional" checked={exceptional} onCheckedChange={setExceptional} />
        </div>
      </div>
      <DialogFooter><Button variant="outline" onClick={onClose}>Cancel</Button><Button onClick={save} disabled={update.isPending}>Save</Button></DialogFooter>
    </>
  )
}

function DeleteTransactionDialog({ transaction, onClose }: { transaction: Transaction | null; onClose: () => void }) {
  const remove = useDeleteTransaction()
  return (
    <Dialog open={Boolean(transaction)} onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="sm:max-w-sm">
        <DialogHeader><DialogTitle>Delete transaction?</DialogTitle></DialogHeader>
        <p className="text-sm text-muted-foreground">{transaction ? `${transaction.description} · ${vnd(transaction.amount)}` : ''}</p>
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>Cancel</Button>
          <Button
            variant="destructive"
            disabled={remove.isPending}
            onClick={() => transaction && remove.mutate(transaction.id, {
              onSuccess: () => { toast.success('Transaction deleted'); onClose() },
              onError: (error) => toast.error(error.message),
            })}
          >Delete</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
