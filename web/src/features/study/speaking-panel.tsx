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
  parseSpeakingIeltsEstimate,
  parseWritingErrors,
  useAssessSpeakingAttempt,
  useDeleteSpeakingFeedback,
  useGenerateSpeakingQuestion,
  useSpeakingFeedback,
  type SpeakingAttempt,
  type SpeakingFeedback,
  type SpeakingIeltsEstimate,
  type WritingError,
} from '@/lib/study-api'
import { useWavRecorder } from '@/lib/use-wav-recorder'
import { cn } from '@/lib/utils'

const EMPTY_FEEDBACK: SpeakingFeedback[] = []

function shownScore(value: number | undefined | null): string {
  return typeof value === 'number' ? String(Math.round(value)) : '—'
}

function shownBandRange(minimum: number | undefined, maximum: number | undefined): string {
  if (minimum === undefined || maximum === undefined) return '—'
  return minimum === maximum ? minimum.toFixed(1) : `${minimum.toFixed(1)}–${maximum.toFixed(1)}`
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
  const latestEstimate = parseSpeakingIeltsEstimate(rows[0]?.ieltsEstimate)
  const stats = [
    { label: 'Attempts', value: String(rows.length), caption: 'saved practice', icon: Mic, tone: 'text-primary', bg: 'bg-primary/10' },
    { label: 'Latest estimate', value: shownBandRange(latestEstimate?.overallMin, latestEstimate?.overallMax), caption: 'one answer · low confidence', icon: Sparkles, tone: 'text-info', bg: 'bg-info/10' },
    { label: 'Latest pronunciation', value: shownScore(rows[0]?.pronunciation), caption: 'provider score · 0–100', icon: Mic, tone: 'text-insight', bg: 'bg-insight/10' },
    { label: 'Recent change', value: trend === null ? '—' : `${trend > 0 ? '+' : ''}${Math.round(trend)}`, caption: 'vs previous attempt', icon: TrendIcon, tone: trend !== null && trend > 0 ? 'text-success' : 'text-muted-foreground', bg: trend !== null && trend > 0 ? 'bg-success/10' : 'bg-muted' },
  ]
  return (
    <div className="grid grid-cols-2 gap-2 xl:grid-cols-4">
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
  const estimate = parseSpeakingIeltsEstimate(attempt.feedback.ieltsEstimate)
  const errors = parseWritingErrors(attempt.feedback.topErrors)
  return (
    <section className="flex flex-col gap-4 rounded-lg border bg-card p-4">
      <div><h2 className="text-sm font-semibold">Latest result</h2><p className="text-xs text-muted-foreground">Practice estimate and provider measurements stay separate.</p></div>
      {estimate && <IeltsEstimateCard estimate={estimate} version={attempt.feedback.estimateVersion} />}
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

const CRITERION_LABELS: Record<SpeakingIeltsEstimate['criteria'][number]['key'], string> = {
  FC: 'Fluency & coherence',
  LR: 'Lexical resource',
  GRA: 'Grammar range & accuracy',
  P: 'Pronunciation',
}

function IeltsEstimateCard({ estimate, version }: { estimate: SpeakingIeltsEstimate; version: string }) {
  return (
    <div className="rounded-lg border border-info/30 bg-info/5 p-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <div className="flex flex-wrap items-center gap-2">
            <p className="text-xs font-semibold uppercase tracking-wide text-info">{estimate.label}</p>
            <Badge variant="outline" className="border-info/40 text-info">{estimate.confidence.toLowerCase()} confidence</Badge>
          </div>
          <p className="mt-1 text-xs text-muted-foreground">One answer only · not an official IELTS result</p>
        </div>
        <div className="text-right">
          <p className="text-xs text-muted-foreground">Overall estimate</p>
          <p className="text-2xl font-semibold tabular-nums text-info">{shownBandRange(estimate.overallMin, estimate.overallMax)}</p>
        </div>
      </div>
      <div className="mt-4 grid gap-2 sm:grid-cols-2 xl:grid-cols-4">
        {estimate.criteria.map((criterion) => (
          <div key={criterion.key} className="rounded-md border bg-background/70 p-3">
            <p className="text-xs text-muted-foreground">{CRITERION_LABELS[criterion.key]}</p>
            <p className="text-lg font-semibold tabular-nums">{shownBandRange(criterion.minBand, criterion.maxBand)}</p>
            <p className="text-[11px] uppercase tracking-wide text-muted-foreground">{criterion.confidence.toLowerCase()} confidence</p>
          </div>
        ))}
      </div>
      <details className="mt-3 rounded-md border bg-background/50 p-3">
        <summary className="cursor-pointer text-xs font-semibold">Why these ranges?</summary>
        <div className="mt-3 space-y-3">
          {estimate.criteria.map((criterion) => (
            <div key={criterion.key} className="text-xs leading-relaxed">
              <p className="font-medium">{CRITERION_LABELS[criterion.key]} · {shownBandRange(criterion.minBand, criterion.maxBand)}</p>
              <p className="text-muted-foreground">“{criterion.evidenceQuote}” — {criterion.justification}</p>
            </div>
          ))}
          <p className="text-[11px] text-muted-foreground">Scorer {version}</p>
        </div>
      </details>
    </div>
  )
}

function ScoreGroup({ title, scores }: { title: string; scores: [string, number | undefined | null][] }) {
  return <div><p className="mb-2 text-xs font-semibold uppercase tracking-wide text-muted-foreground">{title}</p><div className="grid grid-cols-3 gap-2">{scores.map(([label, score]) => <div key={label} className="rounded-lg bg-muted/40 p-3"><p className="text-xs text-muted-foreground">{label}</p><p className="text-xl font-semibold tabular-nums">{shownScore(score)}<span className="text-xs font-normal text-muted-foreground"> / 100</span></p></div>)}</div></div>
}

function scoreTone(score: number | undefined): string {
  if (score === undefined) return ''
  if (score >= 80) return 'border-success/40 text-success'
  if (score >= 60) return 'border-warning/40 text-warning'
  return 'border-destructive/40 text-destructive'
}

function Transcript({ text, errors }: { text: string; errors: WritingError[] }) {
  const quotes = errors.map((error) => error.quote).filter(Boolean).sort((a, b) => b.length - a.length)
  if (quotes.length === 0) return <p className="rounded-lg bg-muted/30 p-3 text-sm leading-relaxed">{text}</p>
  const pattern = new RegExp(`(${quotes.map((quote) => quote.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')).join('|')})`, 'gi')
  const matches = new Set(quotes.map((quote) => quote.toLocaleLowerCase()))
  return <p className="rounded-lg bg-muted/30 p-3 text-sm leading-relaxed">{text.split(pattern).map((part, index) => matches.has(part.toLocaleLowerCase()) ? <mark key={index} className="rounded bg-warning/25 px-0.5 text-inherit">{part}</mark> : part)}</p>
}

function SpeakingHistory({ rows, isLoading, onView, onDelete }: { rows: SpeakingFeedback[]; isLoading: boolean; onView: (row: SpeakingFeedback) => void; onDelete: (row: SpeakingFeedback) => void }) {
  return <div className="overflow-hidden rounded-lg border bg-card"><Table><TableHeader><TableRow><TableHead>Date</TableHead><TableHead>Question</TableHead><TableHead className="text-right">Estimate</TableHead><TableHead className="hidden text-right sm:table-cell">Pronunciation</TableHead><TableHead /></TableRow></TableHeader><TableBody>
    {isLoading && Array.from({ length: 3 }, (_, index) => <TableRow key={index}><TableCell><Skeleton className="h-4 w-20" /></TableCell><TableCell><Skeleton className="h-4 w-64" /></TableCell><TableCell><Skeleton className="ml-auto h-4 w-16" /></TableCell><TableCell className="hidden sm:table-cell"><Skeleton className="ml-auto h-4 w-10" /></TableCell><TableCell><Skeleton className="h-8 w-8" /></TableCell></TableRow>)}
    {!isLoading && rows.length === 0 && <TableRow><TableCell colSpan={5} className="h-36 text-center"><p className="text-sm font-medium">No speaking attempts yet</p><p className="text-xs text-muted-foreground">Generate a question and record a short answer above.</p></TableCell></TableRow>}
    {!isLoading && rows.map((row) => { const estimate = parseSpeakingIeltsEstimate(row.ieltsEstimate); return <TableRow key={row.id}><TableCell className="whitespace-nowrap text-sm text-muted-foreground">{new Date(row.submittedAt).toLocaleDateString()}</TableCell><TableCell><button type="button" onClick={() => onView(row)} className="max-w-xs truncate text-left text-sm font-medium hover:underline sm:max-w-lg">{row.question}</button></TableCell><TableCell className="text-right font-medium tabular-nums">{shownBandRange(estimate?.overallMin, estimate?.overallMax)}</TableCell><TableCell className="hidden text-right tabular-nums sm:table-cell">{shownScore(row.pronunciation)}</TableCell><TableCell><DropdownMenu><DropdownMenuTrigger asChild><Button variant="ghost" size="icon" className="size-8" aria-label="Speaking attempt actions"><Ellipsis className="size-4" /></Button></DropdownMenuTrigger><DropdownMenuContent align="end"><DropdownMenuItem onClick={() => onView(row)}><Eye className="size-4" /> View feedback</DropdownMenuItem><DropdownMenuItem variant="destructive" onClick={() => onDelete(row)}><Trash2 className="size-4" /> Delete</DropdownMenuItem></DropdownMenuContent></DropdownMenu></TableCell></TableRow> })}
  </TableBody></Table></div>
}

function SpeakingDetailDialog({ feedback, onClose }: { feedback: SpeakingFeedback | null; onClose: () => void }) {
  const errors = feedback ? parseWritingErrors(feedback.topErrors) : []
  const content = feedback ? parseSpeakingContentScores(feedback.contentScores) : null
  const estimate = feedback ? parseSpeakingIeltsEstimate(feedback.ieltsEstimate) : null
  return <Dialog open={Boolean(feedback)} onOpenChange={(open) => !open && onClose()}><DialogContent className="max-h-[85vh] overflow-y-auto sm:max-w-2xl">{feedback && <><DialogHeader><DialogTitle className="pr-6">{feedback.question}</DialogTitle></DialogHeader><div className="flex flex-col gap-4"><p className="text-xs text-muted-foreground">Unofficial practice · {feedback.deliveryProvider} {feedback.providerRevision} · content by {feedback.graderModel}</p>{estimate && <IeltsEstimateCard estimate={estimate} version={feedback.estimateVersion} />}<ScoreGroup title="Measured delivery · 0–100" scores={[["Pronunciation", feedback.pronunciation], ["Fluency", feedback.fluency], ["Prosody", feedback.prosody]]} /><ScoreGroup title="AI content · 0–100 · unofficial" scores={[["Vocabulary", content?.vocabulary], ["Grammar", content?.grammar], ["Topic", content?.topic]]} /><p className="text-sm leading-relaxed">{feedback.summary}</p><Transcript text={feedback.transcript} errors={errors} />{errors.length > 0 && <div className="space-y-2">{errors.map((error, index) => <div key={index} className="rounded-lg border border-warning/30 bg-warning/5 p-3 text-xs"><p className="font-medium">{error.label}</p><p className="text-muted-foreground"><span className="line-through">{error.quote}</span> {' → '} <span className="text-foreground">{error.fix}</span></p></div>)}</div>}</div></>}</DialogContent></Dialog>
}

function DeleteSpeakingDialog({ feedback, onClose }: { feedback: SpeakingFeedback | null; onClose: () => void }) {
  const remove = useDeleteSpeakingFeedback()
  return <Dialog open={Boolean(feedback)} onOpenChange={(open) => !open && onClose()}><DialogContent className="sm:max-w-sm"><DialogHeader><DialogTitle>Delete this speaking feedback?</DialogTitle></DialogHeader><p className="text-sm text-muted-foreground">The recording was never stored. This removes the transcript, scores, and coaching feedback.</p><DialogFooter><Button variant="outline" onClick={onClose}>Cancel</Button><Button variant="destructive" disabled={remove.isPending} onClick={() => feedback && remove.mutate(feedback.id, { onSuccess: () => { toast.success('Speaking feedback deleted'); onClose() }, onError: (error) => toast.error(error.message) })}>Delete</Button></DialogFooter></DialogContent></Dialog>
}
