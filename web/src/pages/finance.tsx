import { lazy, Suspense, useState } from 'react'
import { ChartNoAxesCombined, ChevronLeft, ChevronRight, ListFilter, Repeat2, Target } from 'lucide-react'
import { PageTransition } from '@/components/motion'
import { PlanningPanel } from '@/features/finance/planning-panel'
import { SubscriptionsPanel } from '@/features/finance/subscriptions-panel'
import { TransactionsPanel } from '@/features/finance/transactions-panel'
import { Button } from '@/components/ui/button'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip'

const MONTH_LABEL = new Intl.DateTimeFormat('en', { month: 'long', year: 'numeric' })
const InsightsPanel = lazy(() => import('@/features/finance/insights-panel').then((module) => ({ default: module.InsightsPanel })))

function currentMonth(): string {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
}

function shiftMonth(month: string, delta: number): string {
  const [year, value] = month.split('-').map(Number)
  const shifted = new Date(year, value - 1 + delta, 1)
  return `${shifted.getFullYear()}-${String(shifted.getMonth() + 1).padStart(2, '0')}`
}

function formatMonth(month: string): string {
  const [year, value] = month.split('-').map(Number)
  return MONTH_LABEL.format(new Date(year, value - 1, 1))
}

export function FinancePage() {
  const [month, setMonth] = useState(currentMonth)
  const [tab, setTab] = useState<'transactions' | 'planning' | 'subscriptions' | 'insights'>('transactions')

  return (
    <TooltipProvider>
      <main className="w-full min-w-0 flex-1 overflow-auto px-4 py-6 md:px-10 md:py-8">
        <div className="flex flex-col gap-5 pb-4">
          <header className="flex flex-wrap items-center justify-between gap-3">
            <div>
              <h1 className="text-3xl font-bold tracking-tight">Finance</h1>
              <p className="mt-1 text-sm text-muted-foreground">
                Where the money went — capture entries on Capture; review, budget and subscriptions here.
              </p>
            </div>

            {tab !== 'subscriptions' && tab !== 'insights' && <div className="flex h-9 items-center rounded-lg border bg-card p-0.5" role="group" aria-label="Choose finance month">
              <Tooltip>
                <TooltipTrigger asChild>
                  <Button
                    type="button"
                    variant="ghost"
                    size="icon-sm"
                    onClick={() => setMonth((value) => shiftMonth(value, -1))}
                  >
                    <ChevronLeft className="size-4" />
                    <span className="sr-only">Previous month</span>
                  </Button>
                </TooltipTrigger>
                <TooltipContent>Previous month</TooltipContent>
              </Tooltip>
              <span className="min-w-32 px-2 text-center text-sm font-medium tabular-nums">
                {formatMonth(month)}
              </span>
              <Tooltip>
                <TooltipTrigger asChild>
                  <Button
                    type="button"
                    variant="ghost"
                    size="icon-sm"
                    onClick={() => setMonth((value) => shiftMonth(value, 1))}
                  >
                    <ChevronRight className="size-4" />
                    <span className="sr-only">Next month</span>
                  </Button>
                </TooltipTrigger>
                <TooltipContent>Next month</TooltipContent>
              </Tooltip>
            </div>}
          </header>

          <Tabs value={tab} onValueChange={(value) => setTab(value as typeof tab)} className="gap-5">
            <TabsList className="max-w-full">
              <TabsTrigger value="transactions" className="px-2 text-xs sm:px-3 sm:text-sm">
                <ListFilter className="hidden size-4 sm:block" />
                Transactions
              </TabsTrigger>
              <TabsTrigger value="planning" className="px-2 text-xs sm:px-3 sm:text-sm">
                <Target className="hidden size-4 sm:block" />
                Budgets &amp; goals
              </TabsTrigger>
              <TabsTrigger value="subscriptions" className="px-2 text-xs sm:px-3 sm:text-sm">
                <Repeat2 className="hidden size-4 sm:block" />
                Subscriptions
              </TabsTrigger>
              <TabsTrigger value="insights" className="px-2 text-xs sm:px-3 sm:text-sm">
                <ChartNoAxesCombined className="hidden size-4 sm:block" />
                Insights
              </TabsTrigger>
            </TabsList>

            <TabsContent value="transactions">
              <PageTransition key={`transactions-${month}`}><TransactionsPanel month={month} /></PageTransition>
            </TabsContent>
            <TabsContent value="planning">
              <PageTransition key={`planning-${month}`}><PlanningPanel month={month} /></PageTransition>
            </TabsContent>
            <TabsContent value="subscriptions">
              <PageTransition><SubscriptionsPanel /></PageTransition>
            </TabsContent>
            <TabsContent value="insights">
              <PageTransition><Suspense fallback={<div className="h-72 animate-pulse rounded-lg border bg-muted/20" />}><InsightsPanel /></Suspense></PageTransition>
            </TabsContent>
          </Tabs>
        </div>
      </main>
    </TooltipProvider>
  )
}
