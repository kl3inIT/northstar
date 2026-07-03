import { Link, Outlet, useLocation } from '@tanstack/react-router'
import {
  BookOpen,
  Calendar,
  CheckSquare,
  FileText,
  Inbox,
  LogOut,
  Settings,
  Sun,
  Target,
  Trophy,
  Wallet,
  type LucideIcon,
} from 'lucide-react'
import { CommandMenu } from '@/components/command-menu'
import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarHeader,
  SidebarInset,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarProvider,
  SidebarRail,
  SidebarTrigger,
} from '@/components/ui/sidebar'

interface NavItem {
  label: string
  icon: LucideIcon
  to?: '/' | '/notes'
  exact?: boolean
}

const NAV: NavItem[] = [
  { label: 'Today', icon: Sun, to: '/', exact: true },
  { label: 'Inbox', icon: Inbox },
  { label: 'Calendar', icon: Calendar },
  { label: 'Notes', icon: FileText, to: '/notes' },
  { label: 'Study', icon: BookOpen },
  { label: 'Scholarships', icon: Trophy },
  { label: 'Finance', icon: Wallet },
  { label: 'Habits', icon: Target },
  { label: 'Tasks', icon: CheckSquare },
]

/**
 * App shell — col1 is a shadcn Sidebar (collapsible to an icon rail via the
 * edge rail or Cmd/Ctrl+B, Sheet drawer on mobile); routed content renders in
 * SidebarInset. Modules without a route yet are disabled placeholders.
 */
export function AppShell() {
  const pathname = useLocation({ select: (l) => l.pathname })

  return (
    <SidebarProvider>
      <CommandMenu />
      <Sidebar collapsible="icon">
        <SidebarHeader>
          <div className="flex items-center gap-2 px-1 py-1 group-data-[collapsible=icon]:justify-center group-data-[collapsible=icon]:px-0">
            <img src="/logo.png" alt="" className="size-6 shrink-0" />
            <span className="truncate text-base font-semibold group-data-[collapsible=icon]:hidden">
              Northstar
            </span>
          </div>
        </SidebarHeader>
        <SidebarContent>
          <SidebarMenu className="px-2">
            {NAV.map((item) => (
              <SidebarMenuItem key={item.label}>
                {item.to ? (
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
