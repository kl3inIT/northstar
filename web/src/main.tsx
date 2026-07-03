import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { QueryCache, QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ReactQueryDevtools } from '@tanstack/react-query-devtools'
import { RouterProvider } from '@tanstack/react-router'
import { toast } from 'sonner'
import { router } from './router'
import './index.css'

/**
 * staleTime > 0 stops refetch storms when views remount (the default 0 marks
 * everything stale immediately); background query failures surface once as a
 * toast instead of dying silently — mutations keep their local error handling.
 */
const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      retry: 1,
    },
  },
  queryCache: new QueryCache({
    onError: (_error, query) => {
      if (query.meta?.silent) return
      toast.error('Tải dữ liệu thất bại — thử lại sau.', { id: `query-${query.queryHash}` })
    },
  }),
})

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
      <ReactQueryDevtools initialIsOpen={false} />
    </QueryClientProvider>
  </StrictMode>,
)
