import { useMemo } from 'react'
import { Area, AreaChart, Bar, BarChart, CartesianGrid, XAxis, YAxis } from 'recharts'
import { Activity, CalendarRange, ChartNoAxesCombined, TrendingUp } from 'lucide-react'
import { ChartContainer, ChartTooltip, ChartTooltipContent, type ChartConfig } from '@/components/ui/chart'
import { CountUp } from '@/components/motion'
import { m } from '@/components/motion-primitives'
import { Skeleton } from '@/components/ui/skeleton'
import { useFinanceInsights, type FinanceInsights } from '@/lib/finance-api'
import { cn } from '@/lib/utils'
import { todayIso, vnd } from './format'

const trendConfig = {
  expenseTotal: { label: 'Spent', color: 'var(--chart-1)' },
  exceptionalTotal: { label: 'One-off', color: 'var(--chart-4)' },
} satisfies ChartConfig

const categoryConfig = {
  total: { label: 'Spent', color: 'var(--chart-2)' },
} satisfies ChartConfig

const enterGroup = {
  hidden: { opacity: 0 },
  show: { opacity: 1, transition: { staggerChildren: 0.08 } },
}
const enterItem = {
  hidden: { opacity: 0, y: 8 },
  show: { opacity: 1, y: 0 },
}

export function InsightsPanel() {
  const query = useFinanceInsights(todayIso())
  if (query.isLoading) return <InsightsSkeleton />
  if (query.error || !query.data) {
    return <div className="rounded-lg border border-destructive/40 bg-destructive/5 p-4 text-sm text-destructive">Could not load finance insights.</div>
  }
  return <InsightsContent data={query.data} />
}

function InsightsContent({ data }: { data: FinanceInsights }) {
  const total = data.months.reduce((sum, month) => sum + month.expenseTotal, 0)
  const average = Math.round(total / Math.max(1, data.months.length))
  const highest = data.months.reduce((best, month) => month.expenseTotal > best.expenseTotal ? month : best, data.months[0])
  const activeDays = data.days.filter((day) => day.expenseTotal > 0).length

  return (
    <m.div variants={enterGroup} initial="hidden" animate="show" className="flex flex-col gap-4">
      <m.div variants={enterItem} className="grid grid-cols-2 gap-2 lg:grid-cols-4">
        <InsightMetric icon={CalendarRange} label="12-month spend" value={total} format={vnd} />
        <InsightMetric icon={Activity} label="Monthly average" value={average} format={vnd} />
        <InsightMetric icon={TrendingUp} label="Highest month" value={highest.expenseTotal} format={vnd} detail={formatMonth(highest.month)} />
        <InsightMetric icon={ChartNoAxesCombined} label="Active days" value={activeDays} format={(value) => `${value} / 365`} />
      </m.div>

      <m.section variants={enterItem} className="rounded-lg border bg-card p-4 sm:p-5" aria-labelledby="spend-trend-title">
        <div className="mb-4 flex flex-wrap items-start justify-between gap-3">
          <div><h2 id="spend-trend-title" className="text-sm font-semibold">Spending trend</h2><p className="mt-0.5 text-xs text-muted-foreground">Monthly outflow with one-off spending kept visible.</p></div>
          <div className="flex items-center gap-3 text-[11px] text-muted-foreground"><span className="inline-flex items-center gap-1.5"><span className="h-0.5 w-4 bg-chart-1" />Spent</span><span className="inline-flex items-center gap-1.5"><span className="w-4 border-t border-dashed border-chart-4" />One-off</span></div>
        </div>
        <ChartContainer config={trendConfig} className="h-[260px] w-full sm:h-[300px]">
          <AreaChart accessibilityLayer data={data.months} margin={{ top: 8, right: 8, left: -16, bottom: 0 }}>
            <defs>
              <linearGradient id="finance-spend-fill" x1="0" y1="0" x2="0" y2="1">
                <stop offset="5%" stopColor="var(--color-expenseTotal)" stopOpacity={0.28} />
                <stop offset="95%" stopColor="var(--color-expenseTotal)" stopOpacity={0.02} />
              </linearGradient>
            </defs>
            <CartesianGrid vertical={false} strokeDasharray="3 3" />
            <XAxis dataKey="month" tickLine={false} axisLine={false} tickMargin={10} tickFormatter={shortMonth} />
            <YAxis tickLine={false} axisLine={false} tickMargin={8} width={54} tickFormatter={compactVnd} />
            <ChartTooltip cursor={false} content={<ChartTooltipContent labelFormatter={(label) => formatMonth(String(label))} formatter={(value) => vnd(Number(value))} />} />
            <Area dataKey="expenseTotal" type="monotone" stroke="var(--color-expenseTotal)" strokeWidth={2} fill="url(#finance-spend-fill)" />
            <Area dataKey="exceptionalTotal" type="monotone" stroke="var(--color-exceptionalTotal)" strokeWidth={1.5} strokeDasharray="4 4" fill="transparent" />
          </AreaChart>
        </ChartContainer>
      </m.section>

      <div className="grid min-w-0 gap-4">
        <m.section variants={enterItem} className="min-w-0 rounded-lg border bg-card p-4 sm:p-5" aria-labelledby="category-title">
          <div className="mb-4">
            <h2 id="category-title" className="text-sm font-semibold">This month by category</h2>
            <p className="mt-0.5 text-xs text-muted-foreground">Where current-month spending is concentrated.</p>
          </div>
          {data.categories.length === 0 ? <EmptyChart label="No expenses this month" /> : (
            <ChartContainer config={categoryConfig} className="h-[280px] w-full">
              <BarChart accessibilityLayer data={data.categories.slice(0, 9)} layout="vertical" margin={{ top: 0, right: 12, bottom: 0, left: 8 }}>
                <CartesianGrid horizontal={false} strokeDasharray="3 3" />
                <XAxis type="number" tickLine={false} axisLine={false} tickMargin={8} tickFormatter={compactVnd} />
                <YAxis dataKey="name" type="category" tickLine={false} axisLine={false} width={86} tickMargin={8} tickFormatter={(value: string) => value.length > 13 ? `${value.slice(0, 12)}…` : value} />
                <ChartTooltip cursor={false} content={<ChartTooltipContent hideLabel formatter={(value) => vnd(Number(value))} />} />
                <Bar dataKey="total" fill="var(--color-total)" radius={[0, 4, 4, 0]} />
              </BarChart>
            </ChartContainer>
          )}
        </m.section>

        <m.section variants={enterItem} className="min-w-0 rounded-lg border bg-card p-4 sm:p-5" aria-labelledby="heatmap-title">
          <div className="flex flex-wrap items-start justify-between gap-3">
            <div>
              <h2 id="heatmap-title" className="text-sm font-semibold">Spending rhythm</h2>
              <p className="mt-0.5 text-xs text-muted-foreground">Trailing 365 days. Darker days carried more spending.</p>
            </div>
            <HeatLegend />
          </div>
          <SpendingHeatmap days={data.days} />
        </m.section>
      </div>
    </m.div>
  )
}

function InsightMetric({ icon: Icon, label, value, format, detail }: {
  icon: typeof Activity
  label: string
  value: number
  format: (value: number) => string
  detail?: string
}) {
  return (
    <div className="flex min-w-0 items-center gap-3 rounded-lg border bg-card p-3">
      <div className="hidden size-9 shrink-0 items-center justify-center rounded-full bg-primary/10 text-primary sm:flex"><Icon className="size-4" /></div>
      <div className="min-w-0">
        <p className="text-xs text-muted-foreground">{label}</p>
        <p className="truncate text-sm font-semibold tabular-nums sm:text-base"><CountUp value={value} format={format} /></p>
        {detail && <p className="truncate text-[11px] text-muted-foreground">{detail}</p>}
      </div>
    </div>
  )
}

function SpendingHeatmap({ days }: { days: FinanceInsights['days'] }) {
  const { cells, months, scaleMax } = useMemo(() => buildHeatmap(days), [days])
  const cell = 12
  const gap = 3
  const pitch = cell + gap
  return (
    <div className="mt-5 overflow-x-auto pb-1">
      <svg width={53 * pitch + 34} height={7 * pitch + 26} role="img" aria-label="365 day spending heatmap">
        {months.map((month) => <text key={`${month.label}-${month.column}`} x={month.column * pitch + 34} y={9} className="fill-muted-foreground text-[10px]">{month.label}</text>)}
        {['', 'Mon', '', 'Wed', '', 'Fri', ''].map((label, row) => label && <text key={label} x={0} y={row * pitch + 25} dominantBaseline="middle" className="fill-muted-foreground text-[10px]">{label}</text>)}
        {cells.map((item) => (
          <g key={item.date} aria-hidden="true">
            <rect
              x={item.column * pitch + 34}
              y={item.row * pitch + 17}
              width={cell}
              height={cell}
              rx={2}
              className={cn('transition-colors', heatClass(item.amount, scaleMax), item.outside && 'opacity-20')}
            />
            <title>{`${vnd(item.amount)} on ${formatDate(item.date)}`}</title>
          </g>
        ))}
      </svg>
    </div>
  )
}

function HeatLegend() {
  return <div className="flex items-center gap-1 text-[10px] text-muted-foreground"><span>Less</span>{['bg-muted', 'bg-primary/15', 'bg-primary/35', 'bg-primary/60', 'bg-primary/85'].map((tone) => <span key={tone} className={cn('size-2.5 rounded-[2px]', tone)} />)}<span>More</span></div>
}

function buildHeatmap(days: FinanceInsights['days']) {
  const lookup = new Map(days.map((day) => [day.date, day.expenseTotal]))
  const first = parseDate(days[0]?.date ?? todayIso())
  const last = days.at(-1)?.date ?? todayIso()
  const gridStart = new Date(first)
  gridStart.setDate(gridStart.getDate() - gridStart.getDay())
  const cells: Array<{ date: string; amount: number; column: number; row: number; outside: boolean }> = []
  const months: Array<{ label: string; column: number }> = []
  let lastMonth = ''
  for (let column = 0; column < 53; column++) {
    for (let row = 0; row < 7; row++) {
      const date = new Date(gridStart)
      date.setDate(date.getDate() + column * 7 + row)
      const iso = localIso(date)
      cells.push({ date: iso, amount: lookup.get(iso) ?? 0, column, row, outside: iso < days[0].date || iso > last })
      const monthKey = `${date.getFullYear()}-${date.getMonth()}`
      if (row === 0 && monthKey !== lastMonth) {
        lastMonth = monthKey
        months.push({ label: new Intl.DateTimeFormat('en', { month: 'short' }).format(date), column })
      }
    }
  }
  const amounts = days.map((day) => day.expenseTotal).filter(Boolean).toSorted((a, b) => a - b)
  const scaleMax = amounts[Math.floor((amounts.length - 1) * 0.9)] ?? 1
  return { cells, months, scaleMax }
}

function heatClass(amount: number, max: number): string {
  if (amount <= 0) return 'fill-muted'
  const ratio = amount / max
  if (ratio < 0.2) return 'fill-primary/15'
  if (ratio < 0.45) return 'fill-primary/35'
  if (ratio < 0.75) return 'fill-primary/60'
  return 'fill-primary/85'
}

function EmptyChart({ label }: { label: string }) {
  return <div className="flex h-[320px] items-center justify-center text-sm text-muted-foreground">{label}</div>
}

function InsightsSkeleton() {
  return <div className="space-y-4"><div className="grid grid-cols-2 gap-2 lg:grid-cols-4">{Array.from({ length: 4 }, (_, index) => <Skeleton key={index} className="h-18 rounded-lg" />)}</div><Skeleton className="h-[350px] rounded-lg" /><div className="grid gap-4 xl:grid-cols-2"><Skeleton className="h-[390px] rounded-lg" /><Skeleton className="h-[390px] rounded-lg" /></div></div>
}

function compactVnd(value: number): string {
  if (Math.abs(value) >= 1_000_000) return `${Math.round(value / 100_000) / 10}tr`
  if (Math.abs(value) >= 1_000) return `${Math.round(value / 1_000)}k`
  return String(value)
}

function shortMonth(value: string): string {
  const [year, month] = value.split('-').map(Number)
  return new Intl.DateTimeFormat('en', { month: 'short' }).format(new Date(year, month - 1, 1))
}

function formatMonth(value: string): string {
  const [year, month] = value.split('-').map(Number)
  return new Intl.DateTimeFormat('en', { month: 'long', year: 'numeric' }).format(new Date(year, month - 1, 1))
}

function parseDate(iso: string): Date {
  const [year, month, day] = iso.split('-').map(Number)
  return new Date(year, month - 1, day)
}

function localIso(date: Date): string {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}

function formatDate(iso: string): string {
  return new Intl.DateTimeFormat('en', { month: 'short', day: 'numeric', year: 'numeric' }).format(parseDate(iso))
}
