import { useMemo, useState } from 'react'
import { toast } from 'sonner'
import { Ellipsis, Eye, MessageSquareText, Minus, PenLine, Repeat2, Trash2, TrendingDown, TrendingUp } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { cn } from '@/lib/utils'
import {
  parseWritingCriteria,
  parseWritingErrors,
  useDeleteWritingFeedback,
  useWritingFeedback,
  type WritingFeedback,
} from '@/lib/study-api'

const EMPTY_FEEDBACK: WritingFeedback[] = []

function bandRange(feedback: WritingFeedback): string {
  return `~${feedback.overallMin.toFixed(1)}–${feedback.overallMax.toFixed(1)}`
}

function bandMid(feedback: WritingFeedback): number {
  return (feedback.overallMin + feedback.overallMax) / 2
}

export function WritingPanel() {
  const feedbackQuery = useWritingFeedback()
  const rows = feedbackQuery.data ?? EMPTY_FEEDBACK
  const [viewing, setViewing] = useState<WritingFeedback | null>(null)
  const [deleting, setDeleting] = useState<WritingFeedback | null>(null)

  // Rows arrive newest first; the trend compares the two most recent essays.
  const trend = useMemo(() => {
    if (rows.length < 2) return null
    return bandMid(rows[0]) - bandMid(rows[1])
  }, [rows])

  const recurring = useMemo(() => {
    const counts = new Map<string, number>()
    for (const row of rows) {
      for (const error of parseWritingErrors(row.topErrors)) {
        const key = error.label.trim().toLocaleLowerCase()
        counts.set(key, (counts.get(key) ?? 0) + 1)
      }
    }
    return [...counts.entries()]
      .filter(([, count]) => count >= 2)
      .sort((a, b) => b[1] - a[1])
      .slice(0, 3)
  }, [rows])

  return (
    <div className="flex flex-col gap-4">
      <WritingStats
        graded={rows.length}
        latest={rows.length > 0 ? bandRange(rows[0]) : '—'}
        trend={trend}
      />

      <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
        <p className="flex items-center gap-1.5 text-xs text-muted-foreground">
          <MessageSquareText className="size-3.5" />
          Grading happens in chat — paste your essay and tell the Assistant "chấm bài đi"
        </p>
        {recurring.length > 0 && (
          <p className="flex min-w-0 items-center gap-1.5 text-xs text-muted-foreground">
            <Repeat2 className="size-3.5 shrink-0 text-warning" />
            <span className="truncate">
              Recurring: {recurring.map(([label, count]) => `${label} (×${count})`).join(' · ')}
            </span>
          </p>
        )}
      </div>

      {feedbackQuery.error ? (
        <div className="rounded-lg border border-destructive/40 bg-destructive/5 p-4 text-sm text-destructive">
          Could not load writing feedback. Refresh the page to retry.
        </div>
      ) : (
        <FeedbackTable
          rows={rows}
          isLoading={feedbackQuery.isLoading}
          onView={setViewing}
          onDelete={setDeleting}
        />
      )}

      <ViewFeedbackDialog feedback={viewing} onClose={() => setViewing(null)} />
      <DeleteFeedbackDialog feedback={deleting} onClose={() => setDeleting(null)} />
    </div>
  )
}

function WritingStats({ graded, latest, trend }: { graded: number; latest: string; trend: number | null }) {
  const TrendIcon = trend === null || trend === 0 ? Minus : trend > 0 ? TrendingUp : TrendingDown
  const trendLabel = trend === null ? 'needs 2+ essays' : trend === 0 ? 'holding steady' : trend > 0 ? 'improving' : 'dipped last essay'
  const stats = [
    { label: 'Essays graded', value: String(graded), icon: PenLine, tone: 'text-primary', bg: 'bg-primary/10' },
    { label: 'Latest estimate', value: latest, caption: 'unofficial band range', icon: PenLine, tone: 'text-insight', bg: 'bg-insight/10' },
    {
      label: 'Trend',
      value: trend === null ? '—' : `${trend > 0 ? '+' : ''}${trend.toFixed(2)}`,
      caption: trendLabel,
      icon: TrendIcon,
      tone: trend === null || trend === 0
        ? 'text-muted-foreground'
        : trend > 0 ? 'text-success' : 'text-warning',
      bg: trend === null || trend === 0 ? 'bg-muted' : trend > 0 ? 'bg-success/10' : 'bg-warning/10',
    },
  ]
  return (
    <div className="grid grid-cols-3 gap-2">
      {stats.map((stat) => (
        <div key={stat.label} className="flex min-w-0 items-center gap-3 rounded-lg border bg-card p-3">
          <div className={cn('hidden size-9 shrink-0 items-center justify-center rounded-full xl:flex', stat.bg)}>
            <stat.icon className={cn('size-4', stat.tone)} />
          </div>
          <div className="min-w-0">
            <p className="text-xs text-muted-foreground">{stat.label}</p>
            <p className="truncate text-sm font-semibold tabular-nums sm:text-base">{stat.value}</p>
            {stat.caption && <p className="truncate text-[11px] text-muted-foreground">{stat.caption}</p>}
          </div>
        </div>
      ))}
    </div>
  )
}

function FeedbackTable({ rows, isLoading, onView, onDelete }: {
  rows: WritingFeedback[]
  isLoading: boolean
  onView: (feedback: WritingFeedback) => void
  onDelete: (feedback: WritingFeedback) => void
}) {
  return (
    <div className="overflow-hidden rounded-lg border bg-card">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>Date</TableHead>
            <TableHead>Task</TableHead>
            <TableHead className="text-right">Estimate</TableHead>
            <TableHead className="hidden text-right sm:table-cell">Words</TableHead>
            <TableHead className="hidden md:table-cell">Criteria</TableHead>
            <TableHead />
          </TableRow>
        </TableHeader>
        <TableBody>
          {isLoading && Array.from({ length: 3 }, (_, index) => (
            <TableRow key={index}>
              <TableCell><Skeleton className="h-4 w-20" /></TableCell>
              <TableCell><Skeleton className="h-4 w-44" /></TableCell>
              <TableCell><Skeleton className="ml-auto h-5 w-16" /></TableCell>
              <TableCell className="hidden sm:table-cell"><Skeleton className="ml-auto h-4 w-10" /></TableCell>
              <TableCell className="hidden md:table-cell"><Skeleton className="h-4 w-36" /></TableCell>
              <TableCell><Skeleton className="h-8 w-8" /></TableCell>
            </TableRow>
          ))}
          {!isLoading && rows.length === 0 && (
            <TableRow>
              <TableCell colSpan={6} className="h-40 text-center">
                <div className="flex flex-col items-center gap-1.5">
                  <p className="text-sm font-medium">No graded essays yet</p>
                  <p className="text-xs text-muted-foreground">
                    Paste an essay to the Assistant and ask it to grade — feedback lands here
                  </p>
                </div>
              </TableCell>
            </TableRow>
          )}
          {!isLoading && rows.map((row) => {
            const criteria = parseWritingCriteria(row.criteria)
            return (
              <TableRow key={row.id}>
                <TableCell className="whitespace-nowrap text-sm tabular-nums text-muted-foreground">
                  {new Date(row.submittedAt).toLocaleDateString()}
                </TableCell>
                <TableCell>
                  <button
                    type="button"
                    onClick={() => onView(row)}
                    className="max-w-xs truncate text-left text-sm font-medium hover:underline sm:max-w-md"
                    title={row.taskLabel}
                  >
                    {row.taskLabel}
                  </button>
                </TableCell>
                <TableCell className="text-right">
                  <Badge variant="secondary" className="tabular-nums">{bandRange(row)}</Badge>
                </TableCell>
                <TableCell className="hidden text-right sm:table-cell">
                  <span className="text-xs tabular-nums text-muted-foreground">{row.wordCount}</span>
                </TableCell>
                <TableCell className="hidden md:table-cell">
                  <span className="text-xs tabular-nums text-muted-foreground">
                    {criteria.map((c) => `${c.key} ${c.band.toFixed(1)}`).join(' · ')}
                  </span>
                </TableCell>
                <TableCell>
                  <DropdownMenu>
                    <DropdownMenuTrigger asChild>
                      <Button variant="ghost" size="icon" className="size-8" aria-label={`Actions for ${row.taskLabel}`}>
                        <Ellipsis className="size-4" />
                      </Button>
                    </DropdownMenuTrigger>
                    <DropdownMenuContent align="end">
                      <DropdownMenuItem onClick={() => onView(row)}><Eye className="size-4" /> View feedback</DropdownMenuItem>
                      <DropdownMenuItem variant="destructive" onClick={() => onDelete(row)}><Trash2 className="size-4" /> Delete</DropdownMenuItem>
                    </DropdownMenuContent>
                  </DropdownMenu>
                </TableCell>
              </TableRow>
            )
          })}
        </TableBody>
      </Table>
    </div>
  )
}

function ViewFeedbackDialog({ feedback, onClose }: { feedback: WritingFeedback | null; onClose: () => void }) {
  const criteria = feedback ? parseWritingCriteria(feedback.criteria) : []
  const errors = feedback ? parseWritingErrors(feedback.topErrors) : []
  return (
    <Dialog open={Boolean(feedback)} onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="max-h-[85vh] overflow-y-auto sm:max-w-2xl">
        {feedback && (
          <>
            <DialogHeader>
              <DialogTitle className="pr-6">{feedback.taskLabel}</DialogTitle>
            </DialogHeader>
            <div className="flex flex-col gap-4 text-sm">
              <div className="flex flex-wrap items-center gap-2">
                <Badge variant="secondary" className="tabular-nums">{bandRange(feedback)}</Badge>
                <span className="text-xs text-muted-foreground">
                  unofficial estimate · {feedback.wordCount} words · graded by {feedback.graderModel} on{' '}
                  {new Date(feedback.submittedAt).toLocaleDateString()}
                </span>
              </div>

              <p className="leading-relaxed">{feedback.summary}</p>

              {criteria.length > 0 && (
                <section className="flex flex-col gap-2">
                  <h3 className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">By criterion</h3>
                  {criteria.map((criterion) => (
                    <div key={criterion.key} className="rounded-lg border bg-muted/30 p-3">
                      <p className="mb-1 flex items-center gap-2 font-medium">
                        <span>{criterion.key}</span>
                        <Badge variant="outline" className="tabular-nums">{criterion.band.toFixed(1)}</Badge>
                      </p>
                      <p className="text-xs leading-relaxed text-muted-foreground">{criterion.justification}</p>
                    </div>
                  ))}
                </section>
              )}

              {errors.length > 0 && (
                <section className="flex flex-col gap-2">
                  <h3 className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">Top error patterns</h3>
                  {errors.map((error, index) => (
                    <div key={index} className="rounded-lg border border-warning/30 bg-warning/5 p-3">
                      <p className="mb-1 text-xs font-medium">{error.label}</p>
                      <p className="text-xs text-muted-foreground">
                        <span className="line-through decoration-destructive/60">{error.quote}</span>
                        {' → '}
                        <span className="text-foreground">{error.fix}</span>
                      </p>
                    </div>
                  ))}
                </section>
              )}

              <details className="rounded-lg border p-3">
                <summary className="cursor-pointer text-xs font-semibold uppercase tracking-wide text-muted-foreground">
                  Essay ({feedback.wordCount} words)
                </summary>
                <p className="mt-2 whitespace-pre-wrap text-xs leading-relaxed text-muted-foreground">
                  {feedback.essayMarkdown}
                </p>
              </details>
            </div>
          </>
        )}
      </DialogContent>
    </Dialog>
  )
}

function DeleteFeedbackDialog({ feedback, onClose }: { feedback: WritingFeedback | null; onClose: () => void }) {
  const remove = useDeleteWritingFeedback()
  return (
    <Dialog open={Boolean(feedback)} onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="sm:max-w-sm">
        <DialogHeader><DialogTitle>Delete this feedback?</DialogTitle></DialogHeader>
        <p className="text-sm text-muted-foreground">
          {feedback ? `“${feedback.taskLabel}” — the grading cannot be reproduced; a re-grade may score differently.` : ''}
        </p>
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>Cancel</Button>
          <Button
            variant="destructive"
            disabled={remove.isPending}
            onClick={() => feedback && remove.mutate(feedback.id, {
              onSuccess: () => { toast.success('Feedback deleted'); onClose() },
              onError: (error) => toast.error(error.message),
            })}
          >Delete</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
