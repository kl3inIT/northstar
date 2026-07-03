import { createRootRoute, createRoute, createRouter } from '@tanstack/react-router'
import { AppShell } from '@/components/app-shell'
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

export const router = createRouter({ routeTree })

declare module '@tanstack/react-router' {
  interface Register {
    router: typeof router
  }
}
