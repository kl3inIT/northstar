import {
  createRootRoute,
  createRoute,
  createRouter,
  Link,
  Outlet,
} from '@tanstack/react-router'

const NAV = [
  'Today',
  'Inbox',
  'Calendar',
  'Notes',
  'Study',
  'Scholarships',
  'Finance',
  'Habits',
  'Tasks',
] as const

function RootLayout() {
  return (
    <div style={{ display: 'flex', minHeight: '100vh' }}>
      <aside
        style={{
          width: 240,
          borderRight: '1px solid #e4e4e7',
          padding: 16,
          background: '#fafafa',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <img src="/logo.png" alt="Northstar" width={24} height={24} />
          <strong>Northstar</strong>
        </div>
        <nav style={{ display: 'flex', flexDirection: 'column', gap: 4, marginTop: 16 }}>
          {NAV.map((item) => (
            <Link key={item} to="/" style={{ color: '#71717a', textDecoration: 'none' }}>
              {item}
            </Link>
          ))}
        </nav>
      </aside>
      <main style={{ flex: 1, padding: 32 }}>
        <Outlet />
      </main>
    </div>
  )
}

function TodayPage() {
  return (
    <div>
      <h1 style={{ margin: 0 }}>Today</h1>
      <p style={{ color: '#71717a' }}>
        Northstar web shell. Screens map 1:1 to the Penpot wireframes; build them
        with shadcn/ui components.
      </p>
    </div>
  )
}

const rootRoute = createRootRoute({ component: RootLayout })
const indexRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/',
  component: TodayPage,
})

const routeTree = rootRoute.addChildren([indexRoute])

export const router = createRouter({ routeTree })

declare module '@tanstack/react-router' {
  interface Register {
    router: typeof router
  }
}
