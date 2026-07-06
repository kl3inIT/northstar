import { Link, Outlet, useLocation, useNavigate } from '@tanstack/react-router'
import {
  BookOpen,
  Bot,
  Calendar,
  CheckSquare,
  FileText,
  LogOut,
  Settings,
  Sparkles,
  Sun,
  Target,
  Trophy,
  Wallet,
  type LucideIcon,
} from 'lucide-react'
import { useEffect } from 'react'
import { CommandMenu } from '@/components/command-menu'
import { ModeToggle } from '@/components/mode-toggle'
import { Button } from '@/components/ui/button'
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
  to?: '/' | '/notes' | '/tasks' | '/calendar' | '/assistant'
  exact?: boolean
}

const NAV: NavItem[] = [
  { label: 'Today', icon: Sun, to: '/', exact: true },
  { label: 'Tasks', icon: CheckSquare, to: '/tasks' },
  { label: 'Calendar', icon: Calendar, to: '/calendar' },
  { label: 'Notes', icon: FileText, to: '/notes' },
  { label: 'Assistant', icon: Bot, to: '/assistant' },
  { label: 'Study', icon: BookOpen },
  { label: 'Scholarships', icon: Trophy },
  { label: 'Finance', icon: Wallet },
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
  // MFI review queue: machine-drafted notes waiting in Staging nag from the nav.
  const { data: stagingCount = 0 } = useStagingCount()

  // Global capture hotkey — Ctrl/Cmd+Shift+K jumps to the Capture page and
  // focuses the composer (plain Ctrl+K is the quick switcher).
  useEffect(() => {
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
  }, [navigate])

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
              <SidebarMenuButton tooltip="Settings" className="cursor-default opacity-60">
                <Settings />
                <span>Settings</span>
              </SidebarMenuButton>
            </SidebarMenuItem>
            <SidebarMenuItem>
              <SidebarMenuButton tooltip="Sign out" className="cursor-default opacity-60">
                <LogOut />
                <span>Sign out</span>
              </SidebarMenuButton>
            </SidebarMenuItem>
          </SidebarMenu>
        </SidebarFooter>
        <SidebarRail />
      </Sidebar>
      <SidebarInset className="h-screen overflow-hidden">
        {/* Mobile-only opener; desktop toggles via the edge rail or Cmd+B. */}
        <SidebarTrigger className="absolute left-2 top-2 z-10 md:hidden" />
        <div className="flex h-full min-w-0">
          <Outlet />
        </div>
      </SidebarInset>
    </SidebarProvider>
  )
}
