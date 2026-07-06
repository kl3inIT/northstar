import { createRootRoute, createRoute, createRouter, lazyRouteComponent, Link, redirect } from '@tanstack/react-router'
import { AlertTriangle, Loader2 } from 'lucide-react'
import { AppShell } from '@/components/app-shell'
import { Button } from '@/components/ui/button'

// Every page is a lazy chunk: the initial bundle is just the shell + router;
// a page's code loads on navigation (and preloads on hover/touch via
// defaultPreload: 'intent' below).
const rootRoute = createRootRoute({ component: AppShell })

// The assistant is the home screen — the old Today page was a worse copy of
// Tasks, and daily/weekly reviews are drafted from chat (draft_review tool).
const indexRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/',
  beforeLoad: () => {
    throw redirect({ to: '/assistant' })
  },
})

const captureRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: 'capture',
  component: lazyRouteComponent(() => import('@/pages/capture'), 'CapturePage'),
})

const tasksRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: 'tasks',
  component: lazyRouteComponent(() => import('@/pages/tasks'), 'TasksPage'),
})

const calendarRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: 'calendar',
  component: lazyRouteComponent(() => import('@/pages/calendar'), 'CalendarPage'),
})

const assistantRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: 'assistant',
  component: lazyRouteComponent(() => import('@/pages/assistant'), 'AssistantPage'),
})

const disciplinesRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: 'disciplines',
  component: lazyRouteComponent(() => import('@/pages/disciplines'), 'DisciplinesPage'),
})

const disciplineRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: 'disciplines/$id',
  component: lazyRouteComponent(() => import('@/pages/disciplines'), 'DisciplinePage'),
})

const projectsRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: 'projects',
  component: lazyRouteComponent(() => import('@/pages/projects'), 'ProjectsPage'),
})

const notesRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: 'notes',
  component: lazyRouteComponent(() => import('@/features/notes/notes-layout'), 'NotesLayout'),
})

const notesIndexRoute = createRoute({
  getParentRoute: () => notesRoute,
  path: '/',
  component: lazyRouteComponent(() => import('@/features/notes/notes-empty'), 'NotesEmpty'),
})

const noteRoute = createRoute({
  getParentRoute: () => notesRoute,
  path: '$slug',
  component: lazyRouteComponent(() => import('@/features/notes/note-view'), 'NoteView'),
})

const routeTree = rootRoute.addChildren([
  indexRoute,
  captureRoute,
  tasksRoute,
  calendarRoute,
  assistantRoute,
  disciplinesRoute,
  disciplineRoute,
  projectsRoute,
  notesRoute.addChildren([notesIndexRoute, noteRoute]),
])

/** Route-level error boundary — a crashed view degrades to this instead of a white page. */
function RouteError({ error }: { error: Error }) {
  return (
    <div className="flex w-full flex-1 flex-col items-center justify-center gap-3 p-10 text-center">
      <AlertTriangle className="size-8 text-destructive" />
      <p className="text-sm font-medium">Something went wrong while rendering this page.</p>
      <p className="max-w-md truncate text-xs text-muted-foreground">{error.message}</p>
      <Button size="sm" variant="outline" onClick={() => window.location.reload()}>
        Reload
      </Button>
    </div>
  )
}

function RouteNotFound() {
  return (
    <div className="flex w-full flex-1 flex-col items-center justify-center gap-3 p-10 text-center">
      <p className="text-3xl font-bold">404</p>
      <p className="text-sm text-muted-foreground">This page does not exist.</p>
      <Button asChild size="sm" variant="outline">
        <Link to="/assistant">Back home</Link>
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
