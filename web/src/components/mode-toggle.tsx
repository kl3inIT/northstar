import { Moon, Sun } from 'lucide-react'
import { useTheme } from 'next-themes'
import { useEffect, useState } from 'react'
import { SidebarMenuButton } from '@/components/ui/sidebar'

/**
 * Light/dark switch in the sidebar footer. Rendered only after mount — the
 * resolved theme is unknown during SSR-less first paint and would flash the
 * wrong icon.
 */
export function ModeToggle() {
  const { resolvedTheme, setTheme } = useTheme()
  const [mounted, setMounted] = useState(false)

  useEffect(() => setMounted(true), [])

  const dark = mounted && resolvedTheme === 'dark'

  return (
    <SidebarMenuButton
      tooltip={dark ? 'Light mode' : 'Dark mode'}
      onClick={() => setTheme(dark ? 'light' : 'dark')}
    >
      {dark ? <Sun /> : <Moon />}
      <span>{dark ? 'Light mode' : 'Dark mode'}</span>
    </SidebarMenuButton>
  )
}
