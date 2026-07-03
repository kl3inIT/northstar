import { createRootRoute, createRoute, createRouter, Link } from '@tanstack/react-router'
import { AlertTriangle, Loader2 } from 'lucide-react'
import { AppShell } from '@/components/app-shell'
import { Button } from '@/components/ui/button'
import { NoteView } from '@/features/notes/note-view'
import { NotesEmpty } from '@/features/notes/notes-empty'
import { NotesLayout } from '@/features/notes/notes-layout'
import { CapturePage } from '@/pages/capture'
import { TasksPage } from '@/pages/tasks'
import { TodayPage } from '@/pages/today'

const rootRoute = createRootRoute({ component: AppShell })

const indexRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/',
  component: TodayPage,
})

const captureRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: 'capture',
  component: CapturePage,
})

const tasksRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: 'tasks',
  component: TasksPage,
})

const notesRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: 'notes',
  component: NotesLayout,
})

const notesIndexRoute = createRoute({
  getParentRoute: () => notesRoute,
  path: '/',
  component: NotesEmpty,
})

const noteRoute = createRoute({
  getParentRoute: () => notesRoute,
  path: '$slug',
  component: NoteView,
})

const routeTree = rootRoute.addChildren([
  indexRoute,
  captureRoute,
  tasksRoute,
  notesRoute.addChildren([notesIndexRoute, noteRoute]),
])

/** Route-level error boundary — a crashed view degrades to this instead of a white page. */
function RouteError({ error }: { error: Error }) {
  return (
    <div className="flex w-full flex-1 flex-col items-center justify-center gap-3 p-10 text-center">
      <AlertTriangle className="size-8 text-destructive" />
      <p className="text-sm font-medium">Có lỗi khi hiển thị trang này.</p>
      <p className="max-w-md truncate text-xs text-muted-foreground">{error.message}</p>
      <Button size="sm" variant="outline" onClick={() => window.location.reload()}>
        Tải lại
      </Button>
    </div>
  )
}

function RouteNotFound() {
  return (
    <div className="flex w-full flex-1 flex-col items-center justify-center gap-3 p-10 text-center">
      <p className="text-3xl font-bold">404</p>
      <p className="text-sm text-muted-foreground">Trang này không tồn tại.</p>
      <Button asChild size="sm" variant="outline">
        <Link to="/">Về Today</Link>
      </Button>
    </div>
  )
}

function RoutePending() {
  return (
    <div className="flex w-full flex-1 items-center justify-center p-10">
      <Loader2 className="size-5 animate-spin text-muted-foreground" />
    </div>
  )
}

export const router = createRouter({
  routeTree,
  defaultPreload: 'intent',
  defaultErrorComponent: RouteError,
  defaultNotFoundComponent: RouteNotFound,
  defaultPendingComponent: RoutePending,
})

declare module '@tanstack/react-router' {
  interface Register {
    router: typeof router
  }
}
