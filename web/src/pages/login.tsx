import { useEffect, useState } from 'react'
import { useNavigate } from '@tanstack/react-router'
import { Loader2, LogIn } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { useAuthSession, useLogin } from '@/lib/auth-api'

export function LoginPage() {
  const navigate = useNavigate()
  const session = useAuthSession()
  const login = useLogin()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')

  useEffect(() => {
    if (session.data?.authenticated) void navigate({ to: '/assistant' })
  }, [navigate, session.data?.authenticated])

  return (
    <main className="flex min-h-screen w-full items-center justify-center bg-background px-4">
      <Card className="w-full max-w-sm">
        <CardHeader>
          <CardTitle>Northstar</CardTitle>
          <CardDescription>Sign in to your personal operating system.</CardDescription>
        </CardHeader>
        <CardContent>
          <form
            className="grid gap-4"
            onSubmit={(event) => {
              event.preventDefault()
              login.mutate(
                { username: username.trim(), password },
                { onSuccess: () => void navigate({ to: '/assistant' }) },
              )
            }}
          >
            <div className="grid gap-2">
              <Label htmlFor="username">Username</Label>
              <Input
                id="username"
                autoComplete="username"
                value={username}
                onChange={(event) => setUsername(event.target.value)}
                disabled={login.isPending}
                required
              />
            </div>
            <div className="grid gap-2">
              <Label htmlFor="password">Password</Label>
              <Input
                id="password"
                type="password"
                autoComplete="current-password"
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                disabled={login.isPending}
                required
              />
            </div>
            {login.error && <p className="text-sm text-destructive">{login.error.message}</p>}
            <Button
              type="submit"
              className="disabled:bg-muted disabled:text-muted-foreground disabled:opacity-100"
              disabled={login.isPending || !username.trim() || !password}
            >
              {login.isPending ? <Loader2 className="size-4 animate-spin" /> : <LogIn className="size-4" />}
              Sign in
            </Button>
          </form>
        </CardContent>
      </Card>
    </main>
  )
}
