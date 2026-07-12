import { useEffect, useMemo, useState, type ReactNode } from 'react'
import { AnimatePresence } from 'motion/react'
import {
  ArrowLeft,
  BookOpen,
  Check,
  ChevronRight,
  CircleAlert,
  Lightbulb,
  Loader2,
  RotateCcw,
  Sparkles,
  Volume2,
  X,
} from 'lucide-react'
import { toast } from 'sonner'
import { MicButton } from '@/components/mic-button'
import { m } from '@/components/motion-primitives'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { ButtonGroup } from '@/components/ui/button-group'
import { Card, CardContent, CardFooter } from '@/components/ui/card'
import { Checkbox } from '@/components/ui/checkbox'
import { Label } from '@/components/ui/label'
import { Separator } from '@/components/ui/separator'
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetFooter,
  SheetHeader,
  SheetTitle,
} from '@/components/ui/sheet'
import { Skeleton } from '@/components/ui/skeleton'
import { Textarea } from '@/components/ui/textarea'
import {
  parseVocabMetadata,
  useCheckVocabAnswer,
  usePreviewVocabEnrichment,
  useRecordVocabReview,
  useUpdateVocabCard,
  useVocabReviewCards,
  type VocabCard,
  type VocabEnrichmentField,
  type VocabLanguage,
  type VocabRating,
} from '@/lib/study-api'
import { cn } from '@/lib/utils'
import {
  EMPTY_TALLY,
  enrichmentFieldsForRequest,
  incrementRating,
  reviewIsComplete,
  reviewKeyboardAction,
  type RatingTally,
} from './vocabulary-review-state'

const RATINGS: Array<{ rating: VocabRating; label: string; hint: string; icon: typeof RotateCcw }> = [
  { rating: 'AGAIN', label: 'Again', hint: 'Forgot', icon: RotateCcw },
  { rating: 'HARD', label: 'Hard', hint: 'Difficult', icon: CircleAlert },
  { rating: 'GOOD', label: 'Good', hint: 'Remembered', icon: Check },
  { rating: 'EASY', label: 'Easy', hint: 'Immediate', icon: Sparkles },
]

const ENRICHMENT_OPTIONS: Array<{ field: VocabEnrichmentField; label: string; description: string }> = [
  { field: 'EXAMPLE', label: 'Example', description: 'Natural sentence with translation' },
  { field: 'COLLOCATIONS', label: 'Collocations', description: 'Words commonly used together' },
  { field: 'SYNONYMS', label: 'Synonyms', description: 'Nearby words with similar meaning' },
  { field: 'ANTONYMS', label: 'Antonyms', description: 'Useful opposites' },
  { field: 'CONTRAST', label: 'Contrast', description: 'Difference from easily confused words' },
  { field: 'MNEMONIC', label: 'Mnemonic', description: 'A truthful memory hook' },
]

export function VocabularyReviewer({
  limit,
  language,
  deck,
  onExit,
  onEdit,
  onPronounce,
}: {
  limit: number
  language: VocabLanguage
  deck?: string
  onExit: () => void
  onEdit: (card: VocabCard) => void
  onPronounce: (card: VocabCard) => void
}) {
  const queueQuery = useVocabReviewCards(limit, language, deck, true)
  const review = useRecordVocabReview()
  const checkAnswer = useCheckVocabAnswer()
  const [cards, setCards] = useState<VocabCard[] | null>(null)
  const [index, setIndex] = useState(0)
  const [answer, setAnswer] = useState('')
  const [revealed, setRevealed] = useState(false)
  const [hintVisible, setHintVisible] = useState(false)
  const [tally, setTally] = useState<RatingTally>(EMPTY_TALLY)
  const [enrichmentOpen, setEnrichmentOpen] = useState(false)

  useEffect(() => {
    if (cards === null && queueQuery.data) setCards(queueQuery.data)
  }, [cards, queueQuery.data])

  const card = cards?.[index]
  const metadata = useMemo(() => parseVocabMetadata(card?.metadata), [card?.metadata])
  const complete = cards !== null && reviewIsComplete(index, cards.length)
  const progress = cards?.length ? Math.min(index / cards.length, 1) : 0

  function showAnswer() {
    if (!card || review.isPending) return
    setRevealed(true)
  }

  function flipCard() {
    if (!card || review.isPending) return
    setRevealed((current) => !current)
  }

  function listen() {
    if (!card || !('speechSynthesis' in window)) {
      toast.error('Text-to-speech is not available in this browser')
      return
    }
    window.speechSynthesis.cancel()
    const utterance = new SpeechSynthesisUtterance(card.front)
    utterance.lang = /[\u3400-\u9fff]/u.test(card.front) ? 'zh-CN' : 'en-US'
    window.speechSynthesis.speak(utterance)
  }

  function submitAnswer() {
    if (!card || !answer.trim()) return
    checkAnswer.mutate({ id: card.id, answer: answer.trim() }, {
      onSuccess: () => setRevealed(true),
      onError: (error) => toast.error(error.message),
    })
  }

  function rate(rating: VocabRating) {
    if (!card || !revealed || review.isPending) return
    review.mutate({ id: card.id, rating }, {
      onSuccess: () => {
        setTally((current) => incrementRating(current, rating))
        setIndex((current) => current + 1)
        setAnswer('')
        setRevealed(false)
        setHintVisible(false)
        checkAnswer.reset()
      },
      onError: (error) => toast.error(error.message),
    })
  }

  function replaceCard(updated: VocabCard) {
    setCards((current) => current?.map((item) => item.id === updated.id ? updated : item) ?? null)
  }

  function reviewMore() {
    setCards(null)
    setIndex(0)
    setAnswer('')
    setRevealed(false)
    setHintVisible(false)
    setTally(EMPTY_TALLY)
    checkAnswer.reset()
    void queueQuery.refetch()
  }

  useEffect(() => {
    function keydown(event: KeyboardEvent) {
      const target = event.target as HTMLElement | null
      const typing = target?.matches('input, textarea, [contenteditable="true"]')
      const action = reviewKeyboardAction(event.key, { enrichmentOpen, typing: Boolean(typing), revealed })
      if (!action) return
      if (action.type === 'exit') return onExit()
      event.preventDefault()
      if (action.type === 'flip') flipCard()
      else if (action.type === 'listen') listen()
      else rate(action.rating)
    }
    window.addEventListener('keydown', keydown)
    return () => window.removeEventListener('keydown', keydown)
  })

  if (queueQuery.isLoading || cards === null) return <ReviewerSkeleton />
  if (queueQuery.error) {
    return (
      <div className="rounded-lg border border-destructive/40 bg-destructive/5 p-5 text-sm text-destructive">
        Could not start the review. {queueQuery.error.message}
        <Button variant="outline" size="sm" className="ml-3" onClick={onExit}>Back</Button>
      </div>
    )
  }
  if (cards.length === 0) {
    return (
      <Card className="mx-auto max-w-2xl py-12 text-center">
        <CardContent className="flex flex-col items-center gap-3">
          <BookOpen className="size-7 text-muted-foreground" />
          <div><h2 className="font-semibold">No active cards to review</h2><p className="text-sm text-muted-foreground">Resume a paused card or ask the Assistant to save a word.</p></div>
          <Button variant="outline" onClick={onExit}><ArrowLeft /> Back to vocabulary</Button>
        </CardContent>
      </Card>
    )
  }
  if (complete) return <ReviewComplete total={cards.length} tally={tally} onReviewMore={reviewMore} onExit={onExit} />
  if (!card) return null

  return (
    <div className="mx-auto flex w-full max-w-5xl flex-col gap-4">
      <header className="flex items-center gap-4">
        <Button variant="ghost" size="sm" onClick={onExit}><X /> Exit</Button>
        <div className="min-w-0 flex-1">
          <div className="mb-1 flex items-center justify-between text-xs text-muted-foreground">
            <span>{language === 'ENGLISH' ? 'English' : 'Chinese'} · {deck ?? 'All decks'}</span><span className="tabular-nums">{index + 1} / {cards.length}</span>
          </div>
          <div role="progressbar" aria-valuemin={0} aria-valuemax={cards.length} aria-valuenow={index}
            className="h-1.5 overflow-hidden rounded-full bg-muted">
            <div className="h-full bg-primary transition-[width] motion-reduce:transition-none" style={{ width: `${progress * 100}%` }} />
          </div>
        </div>
      </header>

      <Card className="overflow-hidden border-border/80 py-0 shadow-sm">
        <CardContent className="relative px-5 pb-8 pt-16 sm:px-10 sm:pb-10 sm:pt-16">
          <Button variant="ghost" size="sm" onClick={listen} className="absolute right-4 top-3 sm:right-6">
            <Volume2 /> Listen <kbd className="ml-1 text-[10px] text-muted-foreground">R</kbd>
          </Button>
          <div className="mx-auto max-w-3xl">
            <div className="text-center">
              <button type="button" onClick={flipCard}
                aria-label={revealed ? `Show the question for ${card.front}` : `Show the answer for ${card.front}`}
                className="group inline-flex cursor-pointer items-center gap-2 rounded-md px-2 py-1 focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring focus-visible:ring-offset-2 focus-visible:ring-offset-background">
                <span className="text-4xl font-semibold tracking-tight sm:text-6xl">{card.front}</span>
                <RotateCcw aria-hidden="true"
                  className="size-4 shrink-0 text-muted-foreground opacity-0 transition-opacity group-hover:opacity-60 group-focus-visible:opacity-60 motion-reduce:transition-none" />
              </button>
              <span className="sr-only" aria-live="polite">{revealed ? 'Answer shown' : 'Question shown'}</span>
              {!revealed && hintVisible && (
                <p className="mt-3 text-sm text-muted-foreground">
                  {metadata.partOfSpeech || 'No saved part-of-speech hint'}
                </p>
              )}
            </div>

            <div style={{ perspective: '1200px' }}>
              <AnimatePresence mode="wait" initial={false}>
                {!revealed ? (
                  <m.div key="question" initial={{ opacity: 0, rotateY: -8 }} animate={{ opacity: 1, rotateY: 0 }}
                    exit={{ opacity: 0, rotateY: 8 }} className="mx-auto mt-9 max-w-2xl space-y-3"
                    style={{ transformStyle: 'preserve-3d' }}>
                    <Label htmlFor="vocab-answer">What does it mean?</Label>
                    <div className="relative">
                      <Textarea id="vocab-answer" value={answer} onChange={(event) => setAnswer(event.target.value)}
                        placeholder="Type or dictate your answer" className="min-h-24 pr-14 text-base"
                        onKeyDown={(event) => { if (event.key === 'Enter' && (event.ctrlKey || event.metaKey)) submitAnswer() }} />
                      <div className="absolute bottom-2 right-2"><MicButton value={answer} onChange={setAnswer} compact /></div>
                    </div>
                    <div className="flex flex-wrap items-center justify-between gap-2">
                      <Button variant="ghost" onClick={() => setHintVisible((visible) => !visible)}>
                        <Lightbulb /> {hintVisible ? 'Hide hint' : 'Hint'}
                      </Button>
                      <div className="flex gap-2">
                        <Button variant="outline" onClick={showAnswer}>Show answer <kbd className="ml-1 text-[10px]">Space</kbd></Button>
                        <Button onClick={submitAnswer} disabled={!answer.trim() || checkAnswer.isPending}
                          title={!answer.trim() ? 'Type an answer first' : undefined}>
                          {checkAnswer.isPending ? <Loader2 className="animate-spin" /> : <Check />}
                          Check answer
                        </Button>
                      </div>
                    </div>
                  </m.div>
                ) : (
                  <m.div key="answer" initial={{ opacity: 0, rotateY: 8 }} animate={{ opacity: 1, rotateY: 0 }}
                    exit={{ opacity: 0, rotateY: -8 }} className="mt-8 space-y-6"
                    style={{ transformStyle: 'preserve-3d' }}>
                    {checkAnswer.data && <AnswerAssessment verdict={checkAnswer.data.verdict} feedback={checkAnswer.data.feedback} answer={answer} />}
                    <Separator />
                    <div className="grid gap-6 md:grid-cols-[minmax(0,1fr)_auto]">
                      <div className="space-y-4">
                        <div>
                          <div className="flex flex-wrap items-center gap-2 text-sm text-muted-foreground">
                            {metadata.partOfSpeech && <Badge variant="outline">{metadata.partOfSpeech}</Badge>}
                            {metadata.reading && <span>{metadata.reading}</span>}
                          </div>
                          <p className="mt-3 text-xl font-medium leading-relaxed">{card.back}</p>
                        </div>
                        {metadata.example && <MetadataSection title="Example"><p>{metadata.example}</p></MetadataSection>}
                        {metadata.collocations && <MetadataSection title="Collocations"><TagList values={metadata.collocations} /></MetadataSection>}
                        {(metadata.synonyms || metadata.antonyms) && (
                          <div className="grid gap-4 sm:grid-cols-2">
                            {metadata.synonyms && <MetadataSection title="Synonyms"><TagList values={metadata.synonyms} /></MetadataSection>}
                            {metadata.antonyms && <MetadataSection title="Antonyms"><TagList values={metadata.antonyms} /></MetadataSection>}
                          </div>
                        )}
                        {metadata.contrast && <MetadataSection title="Contrast"><p>{metadata.contrast}</p></MetadataSection>}
                        {metadata.mnemonic && <MetadataSection title="Mnemonic"><p>{metadata.mnemonic}</p></MetadataSection>}
                      </div>
                      <div className="flex flex-row gap-2 md:flex-col">
                        <Button variant="outline" onClick={() => setEnrichmentOpen(true)}><Sparkles /> Enrich card</Button>
                        <Button variant="outline" onClick={() => onPronounce(card)}><Volume2 /> Practice pronunciation</Button>
                        <Button variant="ghost" onClick={() => onEdit(card)}>Edit</Button>
                      </div>
                    </div>
                  </m.div>
                )}
              </AnimatePresence>
            </div>
          </div>
        </CardContent>

        {revealed && (
          <CardFooter className="border-t bg-muted/20 px-5 py-5 sm:px-8">
            <ButtonGroup className="grid w-full grid-cols-2 sm:grid-cols-4" aria-label="Rate your recall">
              {RATINGS.map((item, ratingIndex) => (
                <Button key={item.rating} variant={item.rating === 'GOOD' ? 'default' : 'outline'}
                  className="h-auto min-h-16 flex-col gap-0.5" disabled={review.isPending}
                  onClick={() => rate(item.rating)}>
                  <span className="flex items-center gap-1.5"><item.icon /> {item.label}</span>
                  <span className={cn('text-[11px]', item.rating === 'GOOD' ? 'text-primary-foreground/75' : 'text-muted-foreground')}>
                    {item.hint} · {ratingIndex + 1}
                  </span>
                </Button>
              ))}
            </ButtonGroup>
          </CardFooter>
        )}
      </Card>

      <EnrichmentSheet open={enrichmentOpen} onOpenChange={setEnrichmentOpen}
        card={card} onApplied={replaceCard} />
    </div>
  )
}

function AnswerAssessment({ verdict, feedback, answer }: { verdict: 'CORRECT' | 'CLOSE' | 'MISSED'; feedback: string; answer: string }) {
  const labels = { CORRECT: 'Correct', CLOSE: 'Close', MISSED: 'Missed' } as const
  return (
    <div className="rounded-lg border bg-muted/25 p-4" aria-live="polite">
      <div className="flex items-center gap-2"><Badge variant={verdict === 'MISSED' ? 'destructive' : 'secondary'}>{labels[verdict]}</Badge><span className="text-sm text-muted-foreground">Your answer: {answer}</span></div>
      <p className="mt-2 text-sm">{feedback}</p>
    </div>
  )
}

function MetadataSection({ title, children }: { title: string; children: ReactNode }) {
  return <section className="rounded-lg border bg-card p-4"><h3 className="mb-2 text-xs font-semibold uppercase tracking-wide text-muted-foreground">{title}</h3><div className="text-sm leading-relaxed">{children}</div></section>
}

function TagList({ values }: { values: string[] }) {
  return <div className="flex flex-wrap gap-1.5">{values.map((value) => <Badge key={value} variant="secondary">{value}</Badge>)}</div>
}

function ReviewComplete({ total, tally, onReviewMore, onExit }: { total: number; tally: RatingTally; onReviewMore: () => void; onExit: () => void }) {
  return (
    <Card className="mx-auto w-full max-w-2xl py-10 text-center">
      <CardContent className="space-y-6">
        <div className="mx-auto flex size-12 items-center justify-center rounded-full bg-primary/10 text-primary"><Check className="size-6" /></div>
        <div><h2 className="text-2xl font-semibold">Review complete</h2><p className="mt-1 text-sm text-muted-foreground">You reviewed {total} {total === 1 ? 'card' : 'cards'}. Your memory model is up to date.</p></div>
        <div className="grid grid-cols-4 divide-x rounded-lg border">
          {RATINGS.map(({ rating, label }) => <div key={rating} className="p-3"><p className="text-lg font-semibold tabular-nums">{tally[rating]}</p><p className="text-xs text-muted-foreground">{label}</p></div>)}
        </div>
        <div className="flex justify-center gap-2"><Button variant="outline" onClick={onExit}><ArrowLeft /> Back to vocabulary</Button><Button onClick={onReviewMore}><RotateCcw /> Review more</Button></div>
      </CardContent>
    </Card>
  )
}

function EnrichmentSheet({ open, onOpenChange, card, onApplied }: {
  open: boolean
  onOpenChange: (open: boolean) => void
  card: VocabCard
  onApplied: (card: VocabCard) => void
}) {
  const [selected, setSelected] = useState<Set<VocabEnrichmentField>>(new Set())
  const preview = usePreviewVocabEnrichment()
  const update = useUpdateVocabCard()
  const existing = parseVocabMetadata(card.metadata)

  function close() {
    onOpenChange(false)
    setSelected(new Set())
    preview.reset()
  }

  function toggle(field: VocabEnrichmentField, checked: boolean) {
    setSelected((current) => {
      const next = new Set(current)
      if (checked) next.add(field); else next.delete(field)
      return next
    })
    preview.reset()
  }

  function generate() {
    const fields = enrichmentFieldsForRequest(selected, true)
    if (!fields) return
    preview.mutate({ id: card.id, fields }, {
      onError: (error) => toast.error(error.message),
    })
  }

  function apply() {
    if (!preview.data) return
    update.mutate({ id: card.id, front: card.front, back: card.back,
      metadata: preview.data.metadata, disciplineId: card.disciplineId ?? undefined,
      language: card.language, deck: card.deck,
      suspended: card.suspended }, {
      onSuccess: (updated) => { onApplied(updated); toast.success('Enrichment applied'); close() },
      onError: (error) => toast.error(error.message),
    })
  }

  const hasExisting = (field: VocabEnrichmentField) => {
    if (field === 'EXAMPLE') return Boolean(existing.example)
    if (field === 'COLLOCATIONS') return Boolean(existing.collocations?.length)
    if (field === 'SYNONYMS') return Boolean(existing.synonyms?.length)
    if (field === 'ANTONYMS') return Boolean(existing.antonyms?.length)
    if (field === 'CONTRAST') return Boolean(existing.contrast)
    return Boolean(existing.mnemonic)
  }

  return (
    <Sheet open={open} onOpenChange={(next) => next ? onOpenChange(true) : close()}>
      <SheetContent className="w-full sm:max-w-lg">
        <SheetHeader>
          <SheetTitle>Enrich “{card.front}”</SheetTitle>
          <SheetDescription>Select what to generate. Opening this panel does not call AI or change the card.</SheetDescription>
        </SheetHeader>
        <div className="flex-1 overflow-y-auto px-4">
          <div className="space-y-2">
            {ENRICHMENT_OPTIONS.map((option) => (
              <Label key={option.field} className="flex min-h-14 cursor-pointer items-start gap-3 rounded-lg border p-3 hover:bg-muted/40">
                <Checkbox checked={selected.has(option.field)} onCheckedChange={(value) => toggle(option.field, value === true)} />
                <span className="min-w-0"><span className="flex items-center gap-2 text-sm font-medium">{option.label}{hasExisting(option.field) && <Badge variant="outline" className="text-[10px]">replace existing</Badge>}</span><span className="text-xs font-normal text-muted-foreground">{option.description}</span></span>
              </Label>
            ))}
          </div>

          {preview.data && (
            <div className="mt-5 space-y-3 border-t pt-5">
              <h3 className="text-sm font-semibold">Preview</h3>
              {preview.data.example && <MetadataSection title="Example"><p>{preview.data.example}</p></MetadataSection>}
              {preview.data.collocations.length > 0 && <MetadataSection title="Collocations"><TagList values={preview.data.collocations} /></MetadataSection>}
              {preview.data.synonyms.length > 0 && <MetadataSection title="Synonyms"><TagList values={preview.data.synonyms} /></MetadataSection>}
              {preview.data.antonyms.length > 0 && <MetadataSection title="Antonyms"><TagList values={preview.data.antonyms} /></MetadataSection>}
              {preview.data.contrast && <MetadataSection title="Contrast"><p>{preview.data.contrast}</p></MetadataSection>}
              {preview.data.mnemonic && <MetadataSection title="Mnemonic"><p>{preview.data.mnemonic}</p></MetadataSection>}
            </div>
          )}
        </div>
        <SheetFooter>
          {preview.data ? (
            <div className="flex gap-2"><Button variant="outline" className="flex-1" onClick={close}>Discard</Button><Button className="flex-1" onClick={apply} disabled={update.isPending}>{update.isPending ? <Loader2 className="animate-spin" /> : <Check />} Apply to card</Button></div>
          ) : (
            <Button onClick={generate} disabled={selected.size === 0 || preview.isPending}>{preview.isPending ? <Loader2 className="animate-spin" /> : <Sparkles />} Generate selected <ChevronRight /></Button>
          )}
        </SheetFooter>
      </SheetContent>
    </Sheet>
  )
}

function ReviewerSkeleton() {
  return <div className="mx-auto w-full max-w-5xl space-y-4"><Skeleton className="h-10 w-full" /><Skeleton className="h-[520px] w-full rounded-xl" /></div>
}
