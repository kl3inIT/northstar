import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  contributeSavingsGoal,
  createBalanceCheckIn,
  createBudget,
  createSavingsGoal,
  createSubscription,
  deleteBudget,
  deleteSavingsGoal,
  deleteSubscription,
  deleteTransaction,
  getFinanceInsights,
  getMonthSummary,
  listBudgets,
  listBalanceCheckIns,
  listCategories,
  listSavingsGoals,
  listRecurringSuggestions,
  listSubscriptions,
  listTransactions,
  paySubscription,
  recordTransactions,
  updateBudget,
  updateSavingsGoal,
  updateSubscription,
  updateTransaction,
  undoBalanceCheckIn,
} from './hey-api'
import { dataOrThrow, voidOrThrow } from './hey-api-result'
import type {
  BudgetRequest,
  BudgetSummary,
  BalanceCheckInRequest,
  BalanceCheckInSummary,
  FinanceInsights,
  MonthSummary,
  SavingsGoalRequest,
  SavingsGoalSummary,
  RecurringSuggestion,
  SubscriptionPaymentRequest,
  SubscriptionRequest,
  SubscriptionSummary,
  UpdateSavingsGoalRequest,
  UpdateSubscriptionRequest,
  TransactionSummary,
} from './hey-api'

export type Transaction = TransactionSummary
export type Budget = BudgetSummary
export type SavingsGoal = SavingsGoalSummary
export type Subscription = SubscriptionSummary
export type BalanceCheckIn = BalanceCheckInSummary
export type RecurringChargeSuggestion = RecurringSuggestion
export type TransactionType = TransactionSummary['type']
export type { FinanceInsights, MonthSummary }

const tzHeaders = { 'X-Timezone': Intl.DateTimeFormat().resolvedOptions().timeZone }

export interface TransactionInput {
  type: TransactionType
  amount: number
  occurredOn: string
  description: string
  category?: string
  exceptional: boolean
}

/** One month's ledger (yyyy-MM), newest first. */
export function useTransactions(month: string) {
  return useQuery({
    queryKey: ['finance', month],
    queryFn: async () =>
      dataOrThrow(await listTransactions({ query: { month }, headers: tzHeaders })),
  })
}

/** Header aggregates for the month: totals, one-off aggregate, category totals. */
export function useMonthSummary(month: string) {
  return useQuery({
    queryKey: ['finance-summary', month],
    queryFn: async () =>
      dataOrThrow(await getMonthSummary({ query: { month }, headers: tzHeaders })),
  })
}

/** The constrained category vocabulary (seed ∪ used) for the row editor's select. */
export function useCategories(type: TransactionType) {
  return useQuery({
    queryKey: ['finance-categories', type],
    staleTime: 5 * 60 * 1000,
    queryFn: async () => dataOrThrow(await listCategories({ query: { type } })),
  })
}

function useInvalidateFinance() {
  const queryClient = useQueryClient()
  return () => {
    queryClient.invalidateQueries({ queryKey: ['finance'] })
    queryClient.invalidateQueries({ queryKey: ['finance-summary'] })
    queryClient.invalidateQueries({ queryKey: ['finance-categories'] })
    queryClient.invalidateQueries({ queryKey: ['finance-budgets'] })
    queryClient.invalidateQueries({ queryKey: ['finance-insights'] })
    queryClient.invalidateQueries({ queryKey: ['finance-recurring-suggestions'] })
  }
}

/** Save a confirmed capture batch — every item from one message in one call. */
export function useRecordTransactions() {
  const invalidate = useInvalidateFinance()
  return useMutation({
    mutationFn: async (items: TransactionInput[]) => {
      return dataOrThrow(await recordTransactions({ body: { items } }))
    },
    onSuccess: invalidate,
  })
}

export function useUpdateTransaction() {
  const invalidate = useInvalidateFinance()
  return useMutation({
    mutationFn: async ({ id, ...body }: Omit<TransactionInput, 'type'> & { id: string }) => {
      return dataOrThrow(await updateTransaction({ path: { id }, body }))
    },
    onSuccess: invalidate,
  })
}

export function useDeleteTransaction() {
  const invalidate = useInvalidateFinance()
  return useMutation({
    mutationFn: async (id: string) => {
      voidOrThrow(await deleteTransaction({ path: { id } }))
    },
    onSuccess: invalidate,
  })
}

export function useBalanceCheckIns() {
  return useQuery({
    queryKey: ['finance-balance-check-ins'],
    queryFn: async () => dataOrThrow(await listBalanceCheckIns()),
  })
}

export function useCreateBalanceCheckIn() {
  const queryClient = useQueryClient()
  const invalidateFinance = useInvalidateFinance()
  return useMutation({
    mutationFn: async (body: BalanceCheckInRequest) =>
      dataOrThrow(await createBalanceCheckIn({ body, headers: tzHeaders })),
    onSuccess: () => {
      invalidateFinance()
      queryClient.invalidateQueries({ queryKey: ['finance-balance-check-ins'] })
    },
  })
}

export function useUndoBalanceCheckIn() {
  const queryClient = useQueryClient()
  const invalidateFinance = useInvalidateFinance()
  return useMutation({
    mutationFn: async (id: string) => {
      voidOrThrow(await undoBalanceCheckIn({ path: { id } }))
    },
    onSuccess: () => {
      invalidateFinance()
      queryClient.invalidateQueries({ queryKey: ['finance-balance-check-ins'] })
    },
  })
}

export function useFinanceInsights(through: string) {
  return useQuery({
    queryKey: ['finance-insights', through],
    queryFn: async () => dataOrThrow(await getFinanceInsights({
      query: { through },
      headers: tzHeaders,
    })),
  })
}

export function useRecurringSuggestions() {
  return useQuery({
    queryKey: ['finance-recurring-suggestions'],
    queryFn: async () => dataOrThrow(await listRecurringSuggestions({ headers: tzHeaders })),
  })
}

export function useBudgets(month: string) {
  return useQuery({
    queryKey: ['finance-budgets', month],
    queryFn: async () =>
      dataOrThrow(await listBudgets({ query: { month }, headers: tzHeaders })),
  })
}

export function useCreateBudget() {
  const invalidate = useInvalidateFinance()
  return useMutation({
    mutationFn: async (body: BudgetRequest) => dataOrThrow(await createBudget({ body })),
    onSuccess: invalidate,
  })
}

export function useUpdateBudget() {
  const invalidate = useInvalidateFinance()
  return useMutation({
    mutationFn: async ({ id, ...body }: BudgetRequest & { id: string }) =>
      dataOrThrow(await updateBudget({ path: { id }, body })),
    onSuccess: invalidate,
  })
}

export function useDeleteBudget() {
  const invalidate = useInvalidateFinance()
  return useMutation({
    mutationFn: async (id: string) => voidOrThrow(await deleteBudget({ path: { id } })),
    onSuccess: invalidate,
  })
}

export function useSavingsGoals() {
  return useQuery({
    queryKey: ['finance-savings-goals'],
    queryFn: async () => dataOrThrow(await listSavingsGoals()),
  })
}

export function useCreateSavingsGoal() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (body: SavingsGoalRequest) =>
      dataOrThrow(await createSavingsGoal({ body })),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['finance-savings-goals'] }),
  })
}

export function useUpdateSavingsGoal() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async ({ id, ...body }: UpdateSavingsGoalRequest & { id: string }) =>
      dataOrThrow(await updateSavingsGoal({ path: { id }, body })),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['finance-savings-goals'] }),
  })
}

export function useContributeSavingsGoal() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async ({ id, amount }: { id: string; amount: number }) =>
      dataOrThrow(await contributeSavingsGoal({ path: { id }, body: { amount } })),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['finance-savings-goals'] }),
  })
}

export function useDeleteSavingsGoal() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (id: string) => voidOrThrow(await deleteSavingsGoal({ path: { id } })),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['finance-savings-goals'] }),
  })
}

export function useSubscriptions() {
  return useQuery({
    queryKey: ['finance-subscriptions'],
    queryFn: async () => dataOrThrow(await listSubscriptions()),
  })
}

function useInvalidateSubscriptions() {
  const queryClient = useQueryClient()
  return () => {
    queryClient.invalidateQueries({ queryKey: ['finance-subscriptions'] })
    queryClient.invalidateQueries({ queryKey: ['finance-recurring-suggestions'] })
  }
}

export function useCreateSubscription() {
  const invalidate = useInvalidateSubscriptions()
  return useMutation({
    mutationFn: async (body: SubscriptionRequest) =>
      dataOrThrow(await createSubscription({ body })),
    onSuccess: invalidate,
  })
}

export function useUpdateSubscription() {
  const invalidate = useInvalidateSubscriptions()
  return useMutation({
    mutationFn: async ({ id, ...body }: UpdateSubscriptionRequest & { id: string }) =>
      dataOrThrow(await updateSubscription({ path: { id }, body })),
    onSettled: invalidate,
  })
}

export function usePaySubscription() {
  const invalidateFinance = useInvalidateFinance()
  const invalidateSubscriptions = useInvalidateSubscriptions()
  return useMutation({
    mutationFn: async ({ id, ...body }: SubscriptionPaymentRequest & { id: string }) =>
      dataOrThrow(await paySubscription({ path: { id }, body, headers: tzHeaders })),
    onSettled: () => {
      invalidateFinance()
      invalidateSubscriptions()
    },
  })
}

export function useDeleteSubscription() {
  const invalidate = useInvalidateSubscriptions()
  return useMutation({
    mutationFn: async (id: string) => voidOrThrow(await deleteSubscription({ path: { id } })),
    onSuccess: invalidate,
  })
}
