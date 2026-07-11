import { useMemo, useState } from 'react'
import { toast } from 'sonner'
import { Ellipsis, Eye, Mic, Minus, Sparkles, Trash2, TrendingDown, TrendingUp } from 'lucide-react'
import { AudioRecorder } from './audio-recorder'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import {
  parseSpeakingContentScores,
  parseWritingErrors,
  useAssessSpeakingAttempt,
  useDeleteSpeakingFeedback,
  useGenerateSpeakingQuestion,
  useSpeakingFeedback,
  type SpeakingAttempt,
  type SpeakingFeedback,
  type WritingError,
} from '@/lib/study-api'
import { useWavRecorder } from '@/lib/use-wav-recorder'
import { cn } from '@/lib/utils'

const EMPTY_FEEDBACK: SpeakingFeedback[] = []

function shownScore(value: number | undefined | null): string {
  return typeof value === 'number' ? String(Math.round(value)) : '—'
}

export function SpeakingPanel() {
  const feedbackQuery = useSpeakingFeedback()
  const rows = feedbackQuery.data ?? EMPTY_FEEDBACK
  const questionMutation = useGenerateSpeakingQuestion()
  const attemptMutation = useAssessSpeakingAttempt()
  const recorder = useWavRecorder(60)
  const [part, setPart] = useState<1 | 2 | 3>(1)
  const [question, setQuestion] = useState('')
  const [viewing, setViewing] = useState<SpeakingFeedback | null>(null)
  const [deleting, setDeleting] = useState<SpeakingFeedback | null>(null)

  const pronunciationTrend = useMemo(() => {
    if (rows.length < 2 || rows[0].pronunciation === undefined || rows[1].pronunciation === undefined) return null
    return rows[0].pronunciation - rows[1].pronunciation
  }, [rows])

  function getQuestion() {
    questionMutation.mutate(part, {
      onSuccess: (value) => {
        setQuestion(value.question)
        recorder.reset()
        attemptMutation.reset()
      },
      onError: (error) => toast.error(error.message),
    })
  }

  function submit() {
    if (!question.trim() || !recorder.audio) return
    attemptMutation.mutate({ question, audio: recorder.audio }, {
      onSuccess: () => toast.success('Speaking feedback saved'),
      onError: (error) => toast.error(error.message),
    })
  }

  return (
    <div className="flex flex-col gap-4">
      <SpeakingStats rows={rows} trend={pronunciationTrend} />

      <section className="rounded-lg border bg-card p-4">
        <div className="flex flex-col gap-3">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <div>
              <h2 className="text-sm font-semibold">Practice one answer</h2>
              <p className="text-xs text-muted-foreground">Delivery is measured; content coaching is unofficial.</p>
            </div>
            <div className="flex items-center gap-2">
              <Select value={String(part)} onValueChange={(value) => setPart(Number(value) as 1 | 2 | 3)}>
                <SelectTrigger size="sm" aria-label="Speaking part"><SelectValue /></SelectTrigger>
                <SelectContent>
                  <SelectItem value="1">Part 1</SelectItem>
                  <SelectItem value="2">Part 2</SelectItem>
                  <SelectItem value="3">Part 3</SelectItem>
                </SelectContent>
              </Select>
              <Button type="button" variant="outline" size="sm" onClick={getQuestion} disabled={questionMutation.isPending}>
                <Sparkles className="size-4" /> {questionMutation.isPending ? 'Generating…' : 'Get a question'}
              </Button>
            </div>
          </div>

          {question ? (
            <div className="flex flex-col gap-3">
              <p className="rounded-lg bg-muted/40 p-3 text-sm font-medium leading-relaxed">{question}</p>
              <AudioRecorder recorder={recorder} maximumSeconds={60} />
              <div className="flex justify-end">
                <Button type="button" onClick={submit} disabled={!recorder.audio || attemptMutation.isPending}>
                  <Mic className="size-4" /> {attemptMutation.isPending ? 'Assessing…' : 'Assess answer'}
                </Button>
              </div>
            </div>
          ) : (
            <div className="rounded-lg border border-dashed p-6 text-center text-sm text-muted-foreground">
              Choose a part and generate one practice question.
            </div>
          )}
        </div>
      </section>

      {attemptMutation.data && <AttemptResultCard attempt={attemptMutation.data} />}

      {feedbackQuery.error ? (
        <div className="rounded-lg border border-destructive/40 bg-destructive/5 p-4 text-sm text-destructive">
          Could not load speaking history. Refresh the page to retry.
        </div>
      ) : (
        <SpeakingHistory rows={rows} isLoading={feedbackQuery.isLoading} onView={setViewing} onDelete={setDeleting} />
      )}

      <SpeakingDetailDialog feedback={viewing} onClose={() => setViewing(null)} />
      <DeleteSpeakingDialog feedback={deleting} onClose={() => setDeleting(null)} />
    </div>
  )
}

function SpeakingStats({ rows, trend }: { rows: SpeakingFeedback[]; trend: number | null }) {
  const TrendIcon = trend === null || trend === 0 ? Minus : trend > 0 ? TrendingUp : TrendingDown
  const stats = [
    { label: 'Attempts', value: String(rows.length), caption: 'saved practice', icon: Mic, tone: 'text-primary', bg: 'bg-primary/10' },
    { label: 'Latest pronunciation', value: shownScore(rows[0]?.pronunciation), caption: 'provider score · 0–100', icon: Mic, tone: 'text-violet-600 dark:text-violet-400', bg: 'bg-violet-500/10' },
    { label: 'Recent change', value: trend === null ? '—' : `${trend > 0 ? '+' : ''}${Math.round(trend)}`, caption: 'vs previous attempt', icon: TrendIcon, tone: trend !== null && trend > 0 ? 'text-emerald-600 dark:text-emerald-400' : 'text-muted-foreground', bg: trend !== null && trend > 0 ? 'bg-emerald-500/10' : 'bg-muted' },
  ]
  return (
    <div className="grid grid-cols-3 gap-2">
      {stats.map((stat) => (
        <div key={stat.label} className="flex min-w-0 items-center gap-3 rounded-lg border bg-card p-3">
          <div className={cn('hidden size-9 shrink-0 items-center justify-center rounded-full xl:flex', stat.bg)}><stat.icon className={cn('size-4', stat.tone)} /></div>
          <div className="min-w-0"><p className="text-xs text-muted-foreground">{stat.label}</p><p className="truncate text-sm font-semibold tabular-nums sm:text-base">{stat.value}</p><p className="truncate text-[11px] text-muted-foreground">{stat.caption}</p></div>
        </div>
      ))}
    </div>
  )
}

function AttemptResultCard({ attempt }: { attempt: SpeakingAttempt }) {
  const content = parseSpeakingContentScores(attempt.feedback.contentScores)
  const errors = parseWritingErrors(attempt.feedback.topErrors)
  return (
    <section className="flex flex-col gap-4 rounded-lg border bg-card p-4">
      <div><h2 className="text-sm font-semibold">Latest result</h2><p className="text-xs text-muted-foreground">No overall score or IELTS band is calculated.</p></div>
      <ScoreGroup title={`${attempt.feedback.deliveryProvider} delivery · unofficial`} scores={[
        ['Pronunciation', attempt.delivery.pronunciation], ['Fluency', attempt.delivery.fluency], ['Prosody', attempt.delivery.prosody],
      ]} />
      <ScoreGroup title="AI content feedback · unofficial" scores={[
        ['Vocabulary', content?.vocabulary], ['Grammar', content?.grammar], ['Topic', content?.topic],
      ]} />
      <p className="text-sm leading-relaxed">{attempt.feedback.summary}</p>
      <Transcript text={attempt.feedback.transcript} errors={errors} />
      {attempt.delivery.words && attempt.delivery.words.length > 0 && (
        <details className="rounded-lg border p-3"><summary className="cursor-pointer text-xs font-semibold uppercase tracking-wide text-muted-foreground">Word delivery</summary><div className="mt-2 flex flex-wrap gap-1.5">{attempt.delivery.words.map((word, index) => <Badge key={`${word.word}-${index}`} variant="outline" className={scoreTone(word.accuracy)}>{word.word} {shownScore(word.accuracy)}</Badge>)}</div></details>
      )}
    </section>
  )
}

function ScoreGroup({ title, scores }: { title: string; scores: [string, number | undefined | null][] }) {
  return <div><p className="mb-2 text-xs font-semibold uppercase tracking-wide text-muted-foreground">{title}</p><div className="grid grid-cols-3 gap-2">{scores.map(([label, score]) => <div key={label} className="rounded-lg bg-muted/40 p-3"><p className="text-xs text-muted-foreground">{label}</p><p className="text-xl font-semibold tabular-nums">{shownScore(score)}<span className="text-xs font-normal text-muted-foreground"> / 100</span></p></div>)}</div></div>
}

function scoreTone(score: number | undefined): string {
  if (score === undefined) return ''
  if (score >= 80) return 'border-emerald-500/40 text-emerald-700 dark:text-emerald-300'
  if (score >= 60) return 'border-amber-500/40 text-amber-700 dark:text-amber-300'
  return 'border-destructive/40 text-destructive'
}

function Transcript({ text, errors }: { text: string; errors: WritingError[] }) {
  const quotes = errors.map((error) => error.quote).filter(Boolean).sort((a, b) => b.length - a.length)
  if (quotes.length === 0) return <p className="rounded-lg bg-muted/30 p-3 text-sm leading-relaxed">{text}</p>
  const pattern = new RegExp(`(${quotes.map((quote) => quote.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')).join('|')})`, 'gi')
  const matches = new Set(quotes.map((quote) => quote.toLocaleLowerCase()))
  return <p className="rounded-lg bg-muted/30 p-3 text-sm leading-relaxed">{text.split(pattern).map((part, index) => matches.has(part.toLocaleLowerCase()) ? <mark key={index} className="rounded bg-amber-200/70 px-0.5 text-inherit dark:bg-amber-800/60">{part}</mark> : part)}</p>
}

function SpeakingHistory({ rows, isLoading, onView, onDelete }: { rows: SpeakingFeedback[]; isLoading: boolean; onView: (row: SpeakingFeedback) => void; onDelete: (row: SpeakingFeedback) => void }) {
  return <div className="overflow-hidden rounded-lg border bg-card"><Table><TableHeader><TableRow><TableHead>Date</TableHead><TableHead>Question</TableHead><TableHead className="text-right">Pronunciation</TableHead><TableHead className="hidden text-right sm:table-cell">Fluency</TableHead><TableHead /></TableRow></TableHeader><TableBody>
    {isLoading && Array.from({ length: 3 }, (_, index) => <TableRow key={index}><TableCell><Skeleton className="h-4 w-20" /></TableCell><TableCell><Skeleton className="h-4 w-64" /></TableCell><TableCell><Skeleton className="ml-auto h-4 w-10" /></TableCell><TableCell className="hidden sm:table-cell"><Skeleton className="ml-auto h-4 w-10" /></TableCell><TableCell><Skeleton className="h-8 w-8" /></TableCell></TableRow>)}
    {!isLoading && rows.length === 0 && <TableRow><TableCell colSpan={5} className="h-36 text-center"><p className="text-sm font-medium">No speaking attempts yet</p><p className="text-xs text-muted-foreground">Generate a question and record a short answer above.</p></TableCell></TableRow>}
    {!isLoading && rows.map((row) => <TableRow key={row.id}><TableCell className="whitespace-nowrap text-sm text-muted-foreground">{new Date(row.submittedAt).toLocaleDateString()}</TableCell><TableCell><button type="button" onClick={() => onView(row)} className="max-w-xs truncate text-left text-sm font-medium hover:underline sm:max-w-lg">{row.question}</button></TableCell><TableCell className="text-right tabular-nums">{shownScore(row.pronunciation)}</TableCell><TableCell className="hidden text-right tabular-nums sm:table-cell">{shownScore(row.fluency)}</TableCell><TableCell><DropdownMenu><DropdownMenuTrigger asChild><Button variant="ghost" size="icon" className="size-8" aria-label="Speaking attempt actions"><Ellipsis className="size-4" /></Button></DropdownMenuTrigger><DropdownMenuContent align="end"><DropdownMenuItem onClick={() => onView(row)}><Eye className="size-4" /> View feedback</DropdownMenuItem><DropdownMenuItem variant="destructive" onClick={() => onDelete(row)}><Trash2 className="size-4" /> Delete</DropdownMenuItem></DropdownMenuContent></DropdownMenu></TableCell></TableRow>)}
  </TableBody></Table></div>
}

function SpeakingDetailDialog({ feedback, onClose }: { feedback: SpeakingFeedback | null; onClose: () => void }) {
  const errors = feedback ? parseWritingErrors(feedback.topErrors) : []
  const content = feedback ? parseSpeakingContentScores(feedback.contentScores) : null
  return <Dialog open={Boolean(feedback)} onOpenChange={(open) => !open && onClose()}><DialogContent className="max-h-[85vh] overflow-y-auto sm:max-w-2xl">{feedback && <><DialogHeader><DialogTitle className="pr-6">{feedback.question}</DialogTitle></DialogHeader><div className="flex flex-col gap-4"><p className="text-xs text-muted-foreground">Unofficial practice · {feedback.deliveryProvider} {feedback.providerRevision} · content by {feedback.graderModel}</p><ScoreGroup title="Measured delivery · 0–100" scores={[["Pronunciation", feedback.pronunciation], ["Fluency", feedback.fluency], ["Prosody", feedback.prosody]]} /><ScoreGroup title="AI content · 0–100 · unofficial" scores={[["Vocabulary", content?.vocabulary], ["Grammar", content?.grammar], ["Topic", content?.topic]]} /><p className="text-sm leading-relaxed">{feedback.summary}</p><Transcript text={feedback.transcript} errors={errors} />{errors.length > 0 && <div className="space-y-2">{errors.map((error, index) => <div key={index} className="rounded-lg border border-amber-500/30 bg-amber-500/5 p-3 text-xs"><p className="font-medium">{error.label}</p><p className="text-muted-foreground"><span className="line-through">{error.quote}</span> {' → '} <span className="text-foreground">{error.fix}</span></p></div>)}</div>}</div></>}</DialogContent></Dialog>
}

function DeleteSpeakingDialog({ feedback, onClose }: { feedback: SpeakingFeedback | null; onClose: () => void }) {
  const remove = useDeleteSpeakingFeedback()
  return <Dialog open={Boolean(feedback)} onOpenChange={(open) => !open && onClose()}><DialogContent className="sm:max-w-sm"><DialogHeader><DialogTitle>Delete this speaking feedback?</DialogTitle></DialogHeader><p className="text-sm text-muted-foreground">The recording was never stored. This removes the transcript, scores, and coaching feedback.</p><DialogFooter><Button variant="outline" onClick={onClose}>Cancel</Button><Button variant="destructive" disabled={remove.isPending} onClick={() => feedback && remove.mutate(feedback.id, { onSuccess: () => { toast.success('Speaking feedback deleted'); onClose() }, onError: (error) => toast.error(error.message) })}>Delete</Button></DialogFooter></DialogContent></Dialog>
}
