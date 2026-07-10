import { Link, Outlet, useLocation, useNavigate } from '@tanstack/react-router'
import {
  BookOpen,
  Bot,
  Calendar,
  CheckSquare,
  Compass,
  FileText,
  FolderKanban,
  LogOut,
  Settings,
  Sparkles,
  Target,
  Trophy,
  Wallet,
  type LucideIcon,
} from 'lucide-react'
import { useEffect } from 'react'
import { CommandMenu } from '@/components/command-menu'
import { PageTransition } from '@/components/motion'
import { ModeToggle } from '@/components/mode-toggle'
import { Button } from '@/components/ui/button'
import { useAuthSession, useLogout } from '@/lib/auth-api'
import { Toaster } from '@/components/ui/sonner'
import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarHeader,
  SidebarInset,
  SidebarMenu,
  SidebarMenuBadge,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarProvider,
  SidebarRail,
  SidebarTrigger,
} from '@/components/ui/sidebar'
import { useStagingCount } from '@/lib/notes-api'

interface NavItem {
  label: string
  icon: LucideIcon
  to?: '/notes' | '/tasks' | '/calendar' | '/assistant' | '/disciplines' | '/projects' | '/finance' | '/settings'
  exact?: boolean
}

const NAV: NavItem[] = [
  { label: 'Assistant', icon: Bot, to: '/assistant' },
  { label: 'Tasks', icon: CheckSquare, to: '/tasks' },
  { label: 'Calendar', icon: Calendar, to: '/calendar' },
  { label: 'Notes', icon: FileText, to: '/notes' },
  { label: 'Projects', icon: FolderKanban, to: '/projects' },
  { label: 'Disciplines', icon: Compass, to: '/disciplines' },
  { label: 'Finance', icon: Wallet, to: '/finance' },
  { label: 'Study', icon: BookOpen },
  { label: 'Scholarships', icon: Trophy },
  { label: 'Habits', icon: Target },
]

/**
 * App shell — col1 is a shadcn Sidebar (collapsible to an icon rail via the
 * edge rail or Cmd/Ctrl+B, Sheet drawer on mobile); routed content renders in
 * SidebarInset. Modules without a route yet are disabled placeholders.
 */
export function AppShell() {
  const pathname = useLocation({ select: (l) => l.pathname })
  const navigate = useNavigate()
  const isLogin = pathname === '/login'
  const session = useAuthSession()
  const logout = useLogout()
  // MFI review queue: machine-drafted notes waiting in Staging nag from the nav.
  const { data: stagingCount = 0 } = useStagingCount(Boolean(session.data?.authenticated))

  useEffect(() => {
    if (!session.isLoading && !session.data?.authenticated && !isLogin) void navigate({ to: '/login' })
  }, [isLogin, navigate, session.data?.authenticated, session.isLoading])

  useEffect(() => {
    function unauthorized() {
      if (!isLogin) void navigate({ to: '/login' })
    }
    window.addEventListener('northstar:unauthorized', unauthorized)
    return () => window.removeEventListener('northstar:unauthorized', unauthorized)
  }, [isLogin, navigate])

  // Global capture hotkey — Ctrl/Cmd+Shift+K jumps to the Capture page and
  // focuses the composer (plain Ctrl+K is the quick switcher).
  useEffect(() => {
    if (isLogin) return
    function down(e: KeyboardEvent) {
      if (e.key.toLowerCase() === 'k' && e.shiftKey && (e.metaKey || e.ctrlKey)) {
        e.preventDefault()
        navigate({ to: '/capture' }).then(() => {
          document.querySelector<HTMLTextAreaElement>('[data-capture-input]')?.focus()
        })
      }
    }
    document.addEventListener('keydown', down)
    return () => document.removeEventListener('keydown', down)
  }, [isLogin, navigate])

  if (isLogin) {
    return (
      <>
        <Toaster position="bottom-right" richColors />
        <PageTransition key={pathname} className="flex min-h-screen min-w-0 flex-1">
          <Outlet />
        </PageTransition>
      </>
    )
  }

  if (session.isLoading || !session.data?.authenticated) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-background">
        <Sparkles className="size-5 animate-pulse text-muted-foreground" />
      </div>
    )
  }

  return (
    <SidebarProvider>
      <CommandMenu onCapture={() => navigate({ to: '/capture' })} />
      <Toaster position="bottom-right" richColors />
      <Sidebar collapsible="icon">
        <SidebarHeader>
          <div className="flex items-center gap-2 px-1 py-1 group-data-[collapsible=icon]:justify-center group-data-[collapsible=icon]:px-0">
            <img src="/logo.png" alt="" className="size-6 shrink-0" />
            <span className="truncate text-base font-semibold group-data-[collapsible=icon]:hidden">
              Northstar
            </span>
          </div>
          <Button
            asChild
            className="mt-1 w-full justify-start gap-2 group-data-[collapsible=icon]:size-8 group-data-[collapsible=icon]:justify-center group-data-[collapsible=icon]:p-0"
          >
            <Link to="/capture">
              <Sparkles className="size-4 shrink-0" />
              <span className="flex-1 text-left group-data-[collapsible=icon]:hidden">Capture</span>
              <kbd className="text-[10px] opacity-70 group-data-[collapsible=icon]:hidden">⌃⇧K</kbd>
            </Link>
          </Button>
        </SidebarHeader>
        <SidebarContent>
          <SidebarMenu className="px-2">
            {NAV.map((item) => (
              <SidebarMenuItem key={item.label}>
                {item.to ? (
                  <>
                    <SidebarMenuButton
                      asChild
                      tooltip={item.label}
                      isActive={item.exact ? pathname === item.to : pathname.startsWith(item.to)}
                    >
                      <Link to={item.to}>
                        <item.icon />
                        <span>{item.label}</span>
                      </Link>
                    </SidebarMenuButton>
                    {item.label === 'Notes' && stagingCount > 0 && (
                      <SidebarMenuBadge className="rounded-full bg-primary text-primary-foreground">
                        {stagingCount}
                      </SidebarMenuBadge>
                    )}
                  </>
                ) : (
                  <SidebarMenuButton tooltip={item.label} className="cursor-default opacity-60">
                    <item.icon />
                    <span>{item.label}</span>
                  </SidebarMenuButton>
                )}
              </SidebarMenuItem>
            ))}
          </SidebarMenu>
        </SidebarContent>
        <SidebarFooter>
          <SidebarMenu>
            <SidebarMenuItem>
              <ModeToggle />
            </SidebarMenuItem>
            <SidebarMenuItem>
              <SidebarMenuButton asChild tooltip="Settings" isActive={pathname.startsWith('/settings')}>
                <Link to="/settings">
                  <Settings />
                  <span>Settings</span>
                </Link>
              </SidebarMenuButton>
            </SidebarMenuItem>
            <SidebarMenuItem>
              <SidebarMenuButton
                tooltip="Sign out"
                disabled={logout.isPending}
                onClick={() => logout.mutate(undefined, { onSuccess: () => void navigate({ to: '/login' }) })}
              >
                <LogOut />
                <span>Sign out</span>
              </SidebarMenuButton>
            </SidebarMenuItem>
          </SidebarMenu>
        </SidebarFooter>
        <SidebarRail />
      </Sidebar>
      <SidebarInset className="h-screen overflow-hidden">
        {/* Mobile-only top bar; desktop toggles via the edge rail or Cmd+B.
            A real bar (not an absolute overlay) so it never sits on top of
            page content that starts flush at the top, e.g. the notes tabs. */}
        <header className="flex h-12 shrink-0 items-center gap-1.5 border-b px-2 md:hidden">
          <SidebarTrigger />
          <img src="/logo.png" alt="" className="size-5" />
          <span className="text-sm font-semibold">Northstar</span>
        </header>
        <div className="flex min-h-0 min-w-0 flex-1">
          <PageTransition key={pathname} className="flex min-h-0 min-w-0 flex-1">
            <Outlet />
          </PageTransition>
        </div>
      </SidebarInset>
    </SidebarProvider>
  )
}
