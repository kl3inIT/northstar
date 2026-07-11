import { useMemo, useState } from 'react'
import { toast } from 'sonner'
import { CalendarClock, Ellipsis, Hourglass, Pencil, Target, Timer, Trash2 } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { cn } from '@/lib/utils'
import {
  useDeleteStudySession,
  useStudySessions,
  useStudySkills,
  useStudySummary,
  useUpdateStudySession,
  type StudySession,
} from '@/lib/study-api'

const DAY_LABEL = new Intl.DateTimeFormat('en-US', { month: 'short', day: 'numeric' })
const EMPTY_SESSIONS: StudySession[] = []
const EMPTY_SKILLS: string[] = []

function iso(date: Date): string {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}

function formatDay(value: string): string {
  return DAY_LABEL.format(new Date(`${value}T00:00:00`))
}

function formatMinutes(minutes: number): string {
  const hours = Math.floor(minutes / 60)
  const rest = minutes % 60
  if (hours === 0) return `${rest}m`
  return rest === 0 ? `${hours}h` : `${hours}h ${rest}m`
}

type KindFilter = 'ALL' | 'PRACTICE' | 'MOCK'

export function LogPanel() {
  const today = new Date()
  const from = iso(new Date(today.getFullYear(), today.getMonth(), today.getDate() - 29))
  const to = iso(today)
  const sessionsQuery = useStudySessions(from, to)
  const summaryQuery = useStudySummary()
  const sessions = sessionsQuery.data ?? EMPTY_SESSIONS
  const [skill, setSkill] = useState('ALL')
  const [kind, setKind] = useState<KindFilter>('ALL')
  const [editing, setEditing] = useState<StudySession | null>(null)
  const [deleting, setDeleting] = useState<StudySession | null>(null)

  const skills = useMemo(
    () => Array.from(new Set(sessions.map((s) => s.skill))).sort((a, b) => a.localeCompare(b)),
    [sessions],
  )
  const filtered = useMemo(
    () => sessions.filter((s) => (skill === 'ALL' || s.skill === skill) && (kind === 'ALL' || s.kind === kind)),
    [kind, sessions, skill],
  )

  return (
    <div className="flex flex-col gap-4">
      <LogStats summary={summaryQuery.data} />

      <div className="flex flex-col gap-2 sm:flex-row sm:items-center">
        <Select value={skill} onValueChange={setSkill}>
          <SelectTrigger className="w-full sm:w-44" aria-label="Filter by skill">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="ALL">All skills</SelectItem>
            {skills.map((item) => <SelectItem key={item} value={item}>{item}</SelectItem>)}
          </SelectContent>
        </Select>
        <div className="grid h-9 w-full grid-cols-3 rounded-lg border bg-card p-0.5 sm:w-auto" role="group" aria-label="Filter by session kind">
          {(['ALL', 'PRACTICE', 'MOCK'] as const).map((item) => (
            <button
              key={item}
              type="button"
              onClick={() => setKind(item)}
              aria-pressed={kind === item}
              className={cn(
                'min-w-0 rounded-md px-2 text-xs font-medium transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring sm:min-w-20',
                kind === item ? 'bg-foreground text-background' : 'text-muted-foreground hover:text-foreground',
              )}
            >
              {item === 'ALL' ? 'All' : item === 'PRACTICE' ? 'Practice' : 'Mock'}
            </button>
          ))}
        </div>
        <p className="text-xs text-muted-foreground sm:ml-auto">Last 30 days</p>
      </div>

      {sessionsQuery.error ? (
        <div className="rounded-lg border border-destructive/40 bg-destructive/5 p-4 text-sm text-destructive">
          Could not load the study log. Refresh the page to retry.
        </div>
      ) : (
        <SessionsTable
          rows={filtered}
          isLoading={sessionsQuery.isLoading}
          onEdit={setEditing}
          onDelete={setDeleting}
        />
      )}

      <EditSessionDialog key={editing?.id ?? 'none'} session={editing} onClose={() => setEditing(null)} />
      <DeleteSessionDialog session={deleting} onClose={() => setDeleting(null)} />
    </div>
  )
}

function LogStats({ summary }: { summary?: ReturnType<typeof useStudySummary>['data'] }) {
  const values = summary ?? {
    weekStart: '', totalMinutes: 0, sessionCount: 0, previousWeekMinutes: 0, bySkill: [],
  }
  const delta = values.totalMinutes - values.previousWeekMinutes
  const topSkill = values.bySkill[0]
  const stats = [
    { label: 'This week', value: formatMinutes(values.totalMinutes), icon: Timer, tone: 'text-primary', bg: 'bg-primary/10' },
    { label: 'Sessions', value: String(values.sessionCount), icon: CalendarClock, tone: 'text-sky-600 dark:text-sky-400', bg: 'bg-sky-500/10' },
    {
      label: 'vs last week',
      value: `${delta >= 0 ? '+' : '−'}${formatMinutes(Math.abs(delta))}`,
      icon: Hourglass,
      tone: delta >= 0 ? 'text-emerald-600 dark:text-emerald-400' : 'text-amber-600 dark:text-amber-400',
      bg: delta >= 0 ? 'bg-emerald-500/10' : 'bg-amber-500/10',
    },
    {
      label: 'Most practiced',
      value: topSkill ? topSkill.skill : '—',
      caption: topSkill ? `${formatMinutes(topSkill.minutes)} · ${topSkill.sessions} ${topSkill.sessions === 1 ? 'session' : 'sessions'}` : 'No sessions yet this week',
      icon: Target,
      tone: 'text-violet-600 dark:text-violet-400',
      bg: 'bg-violet-500/10',
    },
  ]
  return (
    <div className="grid grid-cols-2 gap-2 lg:grid-cols-4">
      {stats.map((stat) => (
        <div key={stat.label} className="flex min-w-0 items-center gap-3 rounded-lg border bg-card p-3">
          <div className={cn('hidden size-9 shrink-0 items-center justify-center rounded-full xl:flex', stat.bg)}>
            <stat.icon className={cn('size-4', stat.tone)} />
          </div>
          <div className="min-w-0">
            <p className="text-xs text-muted-foreground">{stat.label}</p>
            <p className="truncate text-sm font-semibold tabular-nums sm:text-base" title={stat.value}>{stat.value}</p>
            {stat.caption && <p className="truncate text-[11px] text-muted-foreground">{stat.caption}</p>}
          </div>
        </div>
      ))}
    </div>
  )
}

function SessionsTable({ rows, isLoading, onEdit, onDelete }: {
  rows: StudySession[]
  isLoading: boolean
  onEdit: (session: StudySession) => void
  onDelete: (session: StudySession) => void
}) {
  return (
    <div className="overflow-hidden rounded-lg border bg-card">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>Date</TableHead>
            <TableHead>Activity</TableHead>
            <TableHead className="hidden sm:table-cell">Duration</TableHead>
            <TableHead className="text-right">Result</TableHead>
            <TableHead />
          </TableRow>
        </TableHeader>
        <TableBody>
          {isLoading && Array.from({ length: 5 }, (_, index) => (
            <TableRow key={index}>
              <TableCell><Skeleton className="h-4 w-12" /></TableCell>
              <TableCell><Skeleton className="h-9 w-44" /></TableCell>
              <TableCell className="hidden sm:table-cell"><Skeleton className="h-4 w-12" /></TableCell>
              <TableCell><Skeleton className="ml-auto h-4 w-14" /></TableCell>
              <TableCell><Skeleton className="h-8 w-8" /></TableCell>
            </TableRow>
          ))}
          {!isLoading && rows.length === 0 && (
            <TableRow>
              <TableCell colSpan={5} className="h-40 text-center">
                <div className="flex flex-col items-center gap-1.5">
                  <p className="text-sm font-medium">No study sessions match this view</p>
                  <p className="text-xs text-muted-foreground">
                    Log one on Capture — "làm listening 25p đúng 18/25"
                  </p>
                </div>
              </TableCell>
            </TableRow>
          )}
          {!isLoading && rows.map((session) => (
            <TableRow key={session.id}>
              <TableCell>
                <time dateTime={session.occurredOn} className="text-xs text-muted-foreground">
                  {formatDay(session.occurredOn)}
                </time>
              </TableCell>
              <TableCell>
                <div className="min-w-40 max-w-md">
                  <p className="truncate text-sm font-medium">
                    {session.skill}
                    {session.notes ? <span className="font-normal text-muted-foreground"> · {session.notes}</span> : null}
                  </p>
                  {session.kind === 'MOCK' && (
                    <Badge variant="outline" className="mt-1 h-5 rounded border-violet-400/50 px-1.5 text-[10px] font-normal text-violet-700 dark:text-violet-300">
                      mock test
                    </Badge>
                  )}
                </div>
              </TableCell>
              <TableCell className="hidden sm:table-cell">
                <span className="text-xs tabular-nums text-muted-foreground">
                  {session.durationMinutes != null ? formatMinutes(session.durationMinutes) : '—'}
                </span>
              </TableCell>
              <TableCell className="text-right">
                {session.scoreRaw != null && session.scoreMax != null ? (
                  <span className="text-sm font-semibold tabular-nums">
                    {session.scoreRaw}/{session.scoreMax}
                    <span className="ml-1 text-xs font-normal text-muted-foreground">
                      ({Math.round((session.scoreRaw / session.scoreMax) * 100)}%)
                    </span>
                  </span>
                ) : (
                  <span className="text-xs text-muted-foreground">—</span>
                )}
              </TableCell>
              <TableCell>
                <DropdownMenu>
                  <DropdownMenuTrigger asChild>
                    <Button variant="ghost" size="icon" className="size-8" aria-label={`Actions for ${session.skill} on ${session.occurredOn}`}>
                      <Ellipsis className="size-4" />
                    </Button>
                  </DropdownMenuTrigger>
                  <DropdownMenuContent align="end">
                    <DropdownMenuItem onClick={() => onEdit(session)}><Pencil className="size-4" /> Edit</DropdownMenuItem>
                    <DropdownMenuItem variant="destructive" onClick={() => onDelete(session)}><Trash2 className="size-4" /> Delete</DropdownMenuItem>
                  </DropdownMenuContent>
                </DropdownMenu>
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </div>
  )
}

function EditSessionDialog({ session, onClose }: { session: StudySession | null; onClose: () => void }) {
  return (
    <Dialog open={Boolean(session)} onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="sm:max-w-md">
        {session && <EditSessionForm session={session} onClose={onClose} />}
      </DialogContent>
    </Dialog>
  )
}

function EditSessionForm({ session, onClose }: { session: StudySession; onClose: () => void }) {
  const [occurredOn, setOccurredOn] = useState(session.occurredOn)
  const [skill, setSkill] = useState(session.skill)
  const [kind, setKind] = useState(session.kind)
  const [duration, setDuration] = useState(session.durationMinutes != null ? String(session.durationMinutes) : '')
  const [scoreRaw, setScoreRaw] = useState(session.scoreRaw != null ? String(session.scoreRaw) : '')
  const [scoreMax, setScoreMax] = useState(session.scoreMax != null ? String(session.scoreMax) : '')
  const [notes, setNotes] = useState(session.notes ?? '')
  const skills = useStudySkills().data ?? EMPTY_SKILLS
  const update = useUpdateStudySession()
  const options = skills.includes(skill) ? skills : [skill, ...skills]

  function parseOptional(value: string, field: string): number | undefined {
    if (value.trim() === '') return undefined
    const parsed = Number(value)
    if (!Number.isInteger(parsed) || parsed < 0) {
      throw new Error(`${field} must be a whole number`)
    }
    return parsed
  }

  function save() {
    let durationMinutes: number | undefined
    let raw: number | undefined
    let max: number | undefined
    try {
      durationMinutes = parseOptional(duration, 'Duration')
      raw = parseOptional(scoreRaw, 'Score')
      max = parseOptional(scoreMax, 'Score max')
    } catch (error) {
      toast.error((error as Error).message)
      return
    }
    if (!occurredOn || !skill.trim()) {
      toast.error('Enter a date and skill')
      return
    }
    if ((raw == null) !== (max == null)) {
      toast.error('Score needs both parts — 18 of 25, or leave both empty')
      return
    }
    update.mutate(
      {
        id: session.id,
        occurredOn,
        skill: skill.trim(),
        kind,
        durationMinutes,
        scoreRaw: raw,
        scoreMax: max,
        notes: notes.trim() || undefined,
        disciplineId: session.disciplineId ?? undefined,
      },
      {
        onSuccess: () => { toast.success('Session updated'); onClose() },
        onError: (error) => toast.error(error.message),
      },
    )
  }

  return (
    <>
      <DialogHeader><DialogTitle>Edit study session</DialogTitle></DialogHeader>
      <div className="grid gap-3">
        <div className="grid gap-3 sm:grid-cols-2">
          <div className="grid gap-1.5">
            <Label htmlFor="session-date">Date</Label>
            <Input id="session-date" type="date" value={occurredOn} onChange={(event) => setOccurredOn(event.target.value)} />
          </div>
          <div className="grid gap-1.5">
            <Label htmlFor="session-skill">Skill</Label>
            <Select value={skill} onValueChange={setSkill}>
              <SelectTrigger id="session-skill"><SelectValue /></SelectTrigger>
              <SelectContent>{options.map((item) => <SelectItem key={item} value={item}>{item}</SelectItem>)}</SelectContent>
            </Select>
          </div>
        </div>
        <div className="grid gap-3 sm:grid-cols-2">
          <div className="grid gap-1.5">
            <Label htmlFor="session-kind">Kind</Label>
            <Select value={kind} onValueChange={(value) => setKind(value as StudySession['kind'])}>
              <SelectTrigger id="session-kind"><SelectValue /></SelectTrigger>
              <SelectContent>
                <SelectItem value="PRACTICE">Practice</SelectItem>
                <SelectItem value="MOCK">Mock test</SelectItem>
              </SelectContent>
            </Select>
          </div>
          <div className="grid gap-1.5">
            <Label htmlFor="session-duration">Minutes</Label>
            <Input id="session-duration" inputMode="numeric" placeholder="25" value={duration} onChange={(event) => setDuration(event.target.value)} />
          </div>
        </div>
        <div className="grid gap-3 sm:grid-cols-2">
          <div className="grid gap-1.5">
            <Label htmlFor="session-score">Score</Label>
            <Input id="session-score" inputMode="numeric" placeholder="18" value={scoreRaw} onChange={(event) => setScoreRaw(event.target.value)} />
          </div>
          <div className="grid gap-1.5">
            <Label htmlFor="session-score-max">out of</Label>
            <Input id="session-score-max" inputMode="numeric" placeholder="25" value={scoreMax} onChange={(event) => setScoreMax(event.target.value)} />
          </div>
        </div>
        <div className="grid gap-1.5">
          <Label htmlFor="session-notes">Notes</Label>
          <Input id="session-notes" placeholder="HSK4 · Cam 18 test 2 · topic..." value={notes} onChange={(event) => setNotes(event.target.value)} />
        </div>
      </div>
      <DialogFooter>
        <Button variant="outline" onClick={onClose}>Cancel</Button>
        <Button onClick={save} disabled={update.isPending}>Save</Button>
      </DialogFooter>
    </>
  )
}

function DeleteSessionDialog({ session, onClose }: { session: StudySession | null; onClose: () => void }) {
  const remove = useDeleteStudySession()
  return (
    <Dialog open={Boolean(session)} onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="sm:max-w-sm">
        <DialogHeader><DialogTitle>Delete study session?</DialogTitle></DialogHeader>
        <p className="text-sm text-muted-foreground">
          {session ? `${session.skill} on ${formatDay(session.occurredOn)}${session.notes ? ` · ${session.notes}` : ''}` : ''}
        </p>
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>Cancel</Button>
          <Button
            variant="destructive"
            disabled={remove.isPending}
            onClick={() => session && remove.mutate(session.id, {
              onSuccess: () => { toast.success('Session deleted'); onClose() },
              onError: (error) => toast.error(error.message),
            })}
          >Delete</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
