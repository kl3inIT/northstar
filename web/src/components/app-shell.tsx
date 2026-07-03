import { Link, Outlet } from '@tanstack/react-router'
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

const itemClass =
  'flex items-center gap-3 rounded-md px-3 py-2 text-sm font-medium text-muted-foreground transition-colors'

export function AppShell() {
  return (
    <div className="flex h-screen w-full overflow-hidden bg-background text-foreground">
      <CommandMenu />
      <aside className="flex w-60 shrink-0 flex-col border-r bg-muted/30">
        <div className="flex items-center gap-2 px-5 py-4">
          <img src="/logo.png" alt="" className="size-6" />
          <span className="text-base font-semibold">Northstar</span>
        </div>
        <nav className="flex flex-1 flex-col gap-0.5 px-3">
          {NAV.map((item) =>
            item.to ? (
              <Link
                key={item.label}
                to={item.to}
                activeOptions={{ exact: item.exact ?? false }}
                className={itemClass + ' hover:bg-accent hover:text-foreground'}
                activeProps={{ className: itemClass + ' bg-primary/10 text-primary' }}
              >
                <item.icon className="size-4 shrink-0" />
                {item.label}
              </Link>
            ) : (
              <span key={item.label} className={itemClass + ' cursor-default opacity-70'}>
                <item.icon className="size-4 shrink-0" />
                {item.label}
              </span>
            ),
          )}
        </nav>
        <div className="flex flex-col gap-0.5 border-t px-3 py-3">
          <span className={itemClass + ' cursor-default opacity-70'}>
            <Settings className="size-4 shrink-0" />
            Settings
          </span>
          <span className={itemClass + ' cursor-default opacity-70'}>
            <LogOut className="size-4 shrink-0" />
            Sign out
          </span>
        </div>
      </aside>
      <main className="flex min-w-0 flex-1 overflow-hidden">
        <Outlet />
      </main>
    </div>
  )
}
