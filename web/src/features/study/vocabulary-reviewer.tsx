import { useEffect, useMemo, useState, type MouseEvent, type ReactNode } from 'react'
import { AnimatePresence } from 'motion/react'
import {
  ArrowLeft,
  BookOpen,
  Check,
  ChevronDown,
  ChevronRight,
  CircleAlert,
  ImageIcon,
  Lightbulb,
  Loader2,
  RotateCcw,
  Sparkles,
  Split,
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
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from '@/components/ui/collapsible'
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
  useApplyVocabEnrichmentJob,
  useCheckVocabAnswer,
  useDiscardVocabEnrichmentJob,
  useRecordVocabReview,
  useStartVocabEnrichmentJob,
  useVocabEnrichmentJob,
  useVocabReviewCards,
  type VocabCard,
  type VocabEnrichmentJob,
  type VocabEnrichmentField,
  type VocabLanguage,
  type VocabRating,
  type VocabReviewCard,
} from '@/lib/study-api'
import { fileUrl } from '@/lib/files-api'
import { cn } from '@/lib/utils'
import {
  EMPTY_TALLY,
  enrichmentFieldsForRequest,
  exampleAudioAssetId,
  incrementRating,
  reviewIsComplete,
  reviewKeyboardAction,
  type RatingTally,
  wordAudioAssetId,
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
  { field: 'WORD_FORMATION', label: 'Word parts & family', description: 'Prefix, base/root, suffix, and related words when useful' },
  { field: 'IMAGE', label: 'Illustration', description: 'A text-free visual cue shown on the card front' },
  { field: 'AUDIO', label: 'Audio', description: 'Reusable word and example audio from your configured TTS route' },
]

let activeSpeechAudio: HTMLAudioElement | null = null

function browserSpeak(text: string, locale?: string) {
  if (!('speechSynthesis' in window)) {
    toast.error('Text-to-speech is not available in this browser')
    return
  }
  window.speechSynthesis.cancel()
  const utterance = new SpeechSynthesisUtterance(text)
  utterance.lang = locale || (/\p{Script=Han}/u.test(text) ? 'zh-CN' : 'en-US')
  window.speechSynthesis.speak(utterance)
}

function playSpeech(text: string, assetId?: string, locale?: string) {
  activeSpeechAudio?.pause()
  window.speechSynthesis?.cancel()
  if (!assetId) {
    browserSpeak(text, locale)
    return
  }
  const audio = new Audio(`/api/speech/assets/${encodeURIComponent(assetId)}/audio`)
  activeSpeechAudio = audio
  audio.addEventListener('error', () => browserSpeak(text, locale), { once: true })
  void audio.play().catch(() => browserSpeak(text, locale))
}

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
  onEdit: (card: VocabReviewCard) => void
  onPronounce: (card: VocabReviewCard) => void
}) {
  const queueQuery = useVocabReviewCards(limit, language, deck, true)
  const review = useRecordVocabReview()
  const checkAnswer = useCheckVocabAnswer()
  const startEnrichment = useStartVocabEnrichmentJob()
  const applyEnrichment = useApplyVocabEnrichmentJob()
  const discardEnrichment = useDiscardVocabEnrichmentJob()
  const [cards, setCards] = useState<VocabReviewCard[] | null>(null)
  const [index, setIndex] = useState(0)
  const [answer, setAnswer] = useState('')
  const [revealed, setRevealed] = useState(false)
  const [hintVisible, setHintVisible] = useState(false)
  const [detailsOpen, setDetailsOpen] = useState(false)
  const [tally, setTally] = useState<RatingTally>(EMPTY_TALLY)
  const [enrichmentOpen, setEnrichmentOpen] = useState(false)
  const [enrichmentCard, setEnrichmentCard] = useState<VocabReviewCard | null>(null)
  const [enrichmentJobId, setEnrichmentJobId] = useState<string | null>(null)
  const [notifiedJobId, setNotifiedJobId] = useState<string | null>(null)
  const enrichmentJob = useVocabEnrichmentJob(enrichmentJobId)

  useEffect(() => {
    if (cards === null && queueQuery.data) setCards(queueQuery.data)
  }, [cards, queueQuery.data])

  const card = cards?.[index]
  const metadata = useMemo(() => parseVocabMetadata(card?.metadata), [card?.metadata])
  const production = card?.direction === 'PRODUCTION'
  const question = card ? (production ? card.back : card.front) : ''
  const expectedAnswer = card ? (production ? card.front : card.back) : ''
  const complete = cards !== null && reviewIsComplete(index, cards.length)
  const progress = cards?.length ? Math.min(index / cards.length, 1) : 0
  const detailCount = [
    metadata.collocations?.length,
    metadata.synonyms?.length,
    metadata.antonyms?.length,
    metadata.contrast,
    metadata.mnemonic,
    metadata.wordFormation,
  ].filter(Boolean).length

  useEffect(() => {
    const job = enrichmentJob.data
    if (!job || job.id === notifiedJobId || job.status === 'PENDING') return
    setNotifiedJobId(job.id)
    if (job.status === 'READY') {
      toast.success(`Enrichment for “${job.cardFront}” is ready`, {
        action: { label: 'Review preview', onClick: () => setEnrichmentOpen(true) },
      })
    } else {
      toast.error(job.error || `Could not enrich “${job.cardFront}”`)
      setEnrichmentJobId(null)
    }
  }, [enrichmentJob.data, notifiedJobId])

  function showAnswer() {
    if (!card || review.isPending) return
    setRevealed(true)
  }

  function flipCard() {
    if (!card || review.isPending) return
    setRevealed((current) => !current)
  }

  function flipFromCardSurface(event: MouseEvent<HTMLDivElement>) {
    const target = event.target as HTMLElement
    if (target.closest('button, a, input, textarea, label, [role="checkbox"], [role="switch"]')) return
    if (window.getSelection()?.toString()) return
    flipCard()
  }

  function listen() {
    if (!card) return
    if (card.direction === 'PRODUCTION' && !revealed) return
    playSpeech(card.front, wordAudioAssetId(card.front, metadata), metadata.frontAudioLocale)
  }

  function submitAnswer() {
    if (!card || !answer.trim()) return
    checkAnswer.mutate({ id: card.id, answer: answer.trim(), direction: card.direction }, {
      onSuccess: () => setRevealed(true),
      onError: (error) => toast.error(error.message),
    })
  }

  function rate(rating: VocabRating) {
    if (!card || !revealed || review.isPending) return
    review.mutate({
      id: card.id,
      rating,
      direction: card.direction,
      previewedAt: card.previewedAt,
      schedulingVersion: card.schedulingVersion,
    }, {
      onSuccess: () => {
        setTally((current) => incrementRating(current, rating))
        setIndex((current) => current + 1)
        setAnswer('')
        setRevealed(false)
        setHintVisible(false)
        setDetailsOpen(false)
        checkAnswer.reset()
      },
      onError: (error) => toast.error(error.message),
    })
  }

  function replaceCard(updated: VocabCard) {
    setCards((current) => current?.map((item) => item.id === updated.id
      ? { ...item, front: updated.front, back: updated.back, metadata: updated.metadata,
          language: updated.language, deck: updated.deck, disciplineId: updated.disciplineId,
          suspended: updated.suspended, version: updated.version }
      : item) ?? null)
  }

  function openEnrichment(target: VocabReviewCard) {
    if (enrichmentJobId && enrichmentCard?.id !== target.id) {
      toast.info(`Enrichment for “${enrichmentCard?.front}” is still running`)
      return
    }
    setEnrichmentCard(target)
    setEnrichmentOpen(true)
  }

  function generateEnrichment(fields: VocabEnrichmentField[]) {
    if (!enrichmentCard) return
    startEnrichment.mutate({ id: enrichmentCard.id, fields }, {
      onSuccess: (job) => {
        setEnrichmentJobId(job.id)
        setNotifiedJobId(null)
        setEnrichmentOpen(false)
        toast.info(`Generating enrichment for “${job.cardFront}” — keep reviewing`)
      },
      onError: (error) => toast.error(error.message),
    })
  }

  function applyEnrichmentPreview() {
    if (!enrichmentJobId) return
    applyEnrichment.mutate(enrichmentJobId, {
      onSuccess: (updated) => {
        replaceCard(updated)
        setEnrichmentOpen(false)
        setEnrichmentJobId(null)
        setEnrichmentCard(null)
        toast.success('Enrichment applied')
      },
      onError: (error) => toast.error(error.message),
    })
  }

  function discardEnrichmentPreview() {
    if (!enrichmentJobId) {
      setEnrichmentOpen(false)
      setEnrichmentCard(null)
      return
    }
    discardEnrichment.mutate(enrichmentJobId, {
      onSuccess: () => {
        setEnrichmentOpen(false)
        setEnrichmentJobId(null)
        setEnrichmentCard(null)
      },
      onError: (error) => toast.error(error.message),
    })
  }

  function reviewMore() {
    setCards(null)
    setIndex(0)
    setAnswer('')
    setRevealed(false)
    setHintVisible(false)
    setDetailsOpen(false)
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
          <div><h2 className="font-semibold">You&apos;re caught up</h2><p className="text-sm text-muted-foreground">Nothing is due in this deck right now. FSRS will bring cards back when recall needs reinforcement.</p></div>
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
            <span className="flex items-center gap-2">
              {language === 'ENGLISH' ? 'English' : 'Chinese'} · {deck ?? 'All decks'}
              <Badge variant="outline" className="h-5 rounded px-1.5 text-[10px] font-medium">{scheduleLabel(card.schedulingState)}</Badge>
              {card.leech && <Badge variant="destructive" className="h-5 rounded px-1.5 text-[10px]">Leech · {card.lapseCount} lapses</Badge>}
            </span>
            <span className="tabular-nums">{index + 1} / {cards.length}</span>
          </div>
          <div role="progressbar" aria-valuemin={0} aria-valuemax={cards.length} aria-valuenow={index}
            className="h-1.5 overflow-hidden rounded-full bg-muted">
            <div className="h-full bg-primary transition-[width] motion-reduce:transition-none" style={{ width: `${progress * 100}%` }} />
          </div>
        </div>
        {enrichmentJobId && enrichmentCard && (
          <Button variant="outline" size="sm" className="shrink-0" onClick={() => setEnrichmentOpen(true)}>
            {enrichmentJob.data?.status === 'READY'
              ? <Sparkles />
              : <Loader2 className="animate-spin" />}
            <span className="hidden sm:inline">
              {enrichmentJob.data?.status === 'READY'
                ? `Review ${enrichmentCard.front} enrichment`
                : `Enriching ${enrichmentCard.front}…`}
            </span>
            <span className="sm:hidden">Enrichment</span>
          </Button>
        )}
      </header>

      <Card className={cn('overflow-hidden border-border/80 py-0 shadow-sm',
        revealed && 'flex max-h-[calc(100dvh-14rem)] flex-col')}>
        <CardContent onClick={flipFromCardSurface}
          className={cn('relative cursor-pointer px-5 pb-8 pt-16 sm:px-10 sm:pb-10 sm:pt-16',
            revealed && 'min-h-0 flex-1 overflow-y-auto overscroll-contain')}>
          {(!production || revealed) && (
            <Button variant="ghost" size="sm" onClick={listen} className="absolute right-4 top-3 sm:right-6">
              <Volume2 /> Listen <kbd className="ml-1 text-[10px] text-muted-foreground">R</kbd>
            </Button>
          )}
          <div className="mx-auto max-w-3xl">
            <div className="text-center">
              <Badge variant="outline" className="mb-5 gap-1.5 font-normal">
                {production ? <><Split /> Produce the word</> : <><BookOpen /> Recall the meaning</>}
              </Badge>
              {!revealed && metadata.frontImageId && (
                <img src={fileUrl(metadata.frontImageId)} alt={metadata.frontImageAlt || 'Mnemonic illustration for this vocabulary card'}
                  className="mx-auto mb-6 max-h-64 w-full max-w-xl rounded-xl border object-cover" />
              )}
              <button type="button" onClick={flipCard}
                aria-label={revealed ? `Show the question for ${card.front}` : `Show the answer for ${card.front}`}
                className="group inline-flex cursor-pointer items-center gap-2 rounded-md px-2 py-1 focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring focus-visible:ring-offset-2 focus-visible:ring-offset-background">
                <span className={cn('font-semibold tracking-tight', production ? 'text-2xl leading-relaxed sm:text-4xl' : 'text-4xl sm:text-6xl')}>{question}</span>
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
                    <Label htmlFor="vocab-answer">{production ? `Which ${language === 'ENGLISH' ? 'English' : 'Chinese'} expression matches?` : 'What does it mean?'}</Label>
                    <div className="relative">
                      <Textarea id="vocab-answer" value={answer} onChange={(event) => setAnswer(event.target.value)}
                        placeholder={production ? `Type the ${language === 'ENGLISH' ? 'English' : 'Chinese'} expression` : 'Type the meaning in Vietnamese or English'} className="min-h-24 pr-14 text-base"
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
                          <p className={cn('mt-3 font-medium leading-relaxed', production ? 'text-3xl' : 'text-xl')}>{expectedAnswer}</p>
                        </div>
                        {metadata.example && (
                          <MetadataSection title="Example">
                            <div className="flex items-start justify-between gap-3">
                              <p>{metadata.example}</p>
                              <Button variant="ghost" size="icon-sm" aria-label="Listen to example"
                                onClick={() => playSpeech(metadata.example!, exampleAudioAssetId(metadata), metadata.frontAudioLocale)}>
                                <Volume2 />
                              </Button>
                            </div>
                          </MetadataSection>
                        )}
                      </div>
                      <div className="flex flex-row gap-2 md:flex-col">
                        <Button variant="outline" onClick={() => openEnrichment(card)}><Sparkles /> Enrich card</Button>
                        <Button variant="outline" onClick={() => onPronounce(card)}><Volume2 /> Practice pronunciation</Button>
                        <Button variant="ghost" onClick={() => onEdit(card)}>Edit</Button>
                      </div>
                    </div>
                    {detailCount > 0 && (
                      <Collapsible open={detailsOpen} onOpenChange={setDetailsOpen} className="w-full rounded-lg border bg-card">
                        <CollapsibleTrigger asChild>
                          <Button variant="ghost" className="h-auto w-full justify-between rounded-lg px-4 py-3">
                            <span className="flex items-center gap-2"><BookOpen /> Study details <Badge variant="secondary">{detailCount}</Badge></span>
                            <ChevronDown className={cn('transition-transform', detailsOpen && 'rotate-180')} />
                          </Button>
                        </CollapsibleTrigger>
                        <CollapsibleContent>
                          <StudyDetails metadata={metadata} />
                        </CollapsibleContent>
                      </Collapsible>
                    )}
                  </m.div>
                )}
              </AnimatePresence>
            </div>
          </div>
        </CardContent>

        {revealed && (
          <CardFooter className="shrink-0 border-t bg-card/95 px-5 py-5 backdrop-blur sm:px-8">
            <ButtonGroup className="grid w-full grid-cols-2 sm:grid-cols-4" aria-label="Rate your recall">
              {RATINGS.map((item, ratingIndex) => (
                <Button key={item.rating} variant={item.rating === 'GOOD' ? 'default' : 'outline'}
                  className="h-auto min-h-16 flex-col gap-0.5" disabled={review.isPending}
                  onClick={() => rate(item.rating)}>
                  <span className="flex items-center gap-1.5"><item.icon /> {item.label}</span>
                  <span className={cn('text-[11px]', item.rating === 'GOOD' ? 'text-primary-foreground/75' : 'text-muted-foreground')}>
                    {card.ratingPreviews.find((preview) => preview.rating === item.rating)?.intervalLabel ?? '—'} · {item.hint} · {ratingIndex + 1}
                  </span>
                </Button>
              ))}
            </ButtonGroup>
          </CardFooter>
        )}
      </Card>

      {enrichmentCard && (
        <EnrichmentSheet open={enrichmentOpen} onOpenChange={setEnrichmentOpen}
          card={enrichmentCard} job={enrichmentJob.data}
          isStarting={startEnrichment.isPending} isApplying={applyEnrichment.isPending}
          onGenerate={generateEnrichment} onApply={applyEnrichmentPreview}
          onDiscard={discardEnrichmentPreview} />
      )}
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

function StudyDetails({ metadata }: { metadata: ReturnType<typeof parseVocabMetadata> }) {
  return (
    <div className="divide-y border-t">
      {(metadata.collocations || metadata.contrast) && (
        <DetailGroup title="Usage">
          {metadata.collocations && <DetailItem title="Collocations"><TagList values={metadata.collocations} /></DetailItem>}
          {metadata.contrast && <DetailItem title="Contrast"><p>{metadata.contrast}</p></DetailItem>}
        </DetailGroup>
      )}
      {(metadata.synonyms || metadata.antonyms) && (
        <DetailGroup title="Word relationships">
          <div className="grid gap-4 sm:grid-cols-2">
            {metadata.synonyms && <DetailItem title="Synonyms"><TagList values={metadata.synonyms} /></DetailItem>}
            {metadata.antonyms && <DetailItem title="Antonyms"><TagList values={metadata.antonyms} /></DetailItem>}
          </div>
        </DetailGroup>
      )}
      {(metadata.mnemonic || metadata.wordFormation) && (
        <DetailGroup title="Memory aids">
          {metadata.mnemonic && <DetailItem title="Mnemonic"><p>{metadata.mnemonic}</p></DetailItem>}
          {metadata.wordFormation && <DetailItem title="Word formation"><WordFormationContent value={metadata.wordFormation} /></DetailItem>}
        </DetailGroup>
      )}
    </div>
  )
}

function DetailGroup({ title, children }: { title: string; children: ReactNode }) {
  return <section className="space-y-3 p-4"><h3 className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">{title}</h3>{children}</section>
}

function DetailItem({ title, children }: { title: string; children: ReactNode }) {
  return <div className="space-y-1.5 text-sm leading-relaxed"><h4 className="font-medium">{title}</h4>{children}</div>
}

function WordFormation({ value }: { value: NonNullable<ReturnType<typeof parseVocabMetadata>['wordFormation']> }) {
  return <MetadataSection title="Word formation"><WordFormationContent value={value} /></MetadataSection>
}

function WordFormationContent({ value }: { value: NonNullable<ReturnType<typeof parseVocabMetadata>['wordFormation']> }) {
  const parts = value.parts ?? []
  return (
    <div className="space-y-3">
      {parts.length > 0 && (
        <div className="flex flex-wrap items-stretch gap-1.5">
          {parts.map((part, index) => (
            <div key={`${part.form ?? index}-${index}`} className="rounded-md border bg-background px-2.5 py-2">
              <p className="font-medium">{part.form}</p>
              <p className="text-[11px] uppercase tracking-wide text-muted-foreground">{part.kind}</p>
              <p className="mt-1 text-xs text-muted-foreground">{part.meaning}</p>
            </div>
          ))}
        </div>
      )}
      {value.explanation && <p>{value.explanation}</p>}
      {(value.family?.length ?? 0) > 0 && <TagList values={value.family ?? []} />}
    </div>
  )
}

function ReviewComplete({ total, tally, onReviewMore, onExit }: { total: number; tally: RatingTally; onReviewMore: () => void; onExit: () => void }) {
  return (
    <Card className="mx-auto w-full max-w-2xl py-10 text-center">
      <CardContent className="space-y-6">
        <div className="mx-auto flex size-12 items-center justify-center rounded-full bg-primary/10 text-primary"><Check className="size-6" /></div>
        <div><h2 className="text-2xl font-semibold">Due cards complete</h2><p className="mt-1 text-sm text-muted-foreground">You reviewed {total} {total === 1 ? 'card' : 'cards'}. FSRS has scheduled each next review.</p></div>
        <div className="grid grid-cols-4 divide-x rounded-lg border">
          {RATINGS.map(({ rating, label }) => <div key={rating} className="p-3"><p className="text-lg font-semibold tabular-nums">{tally[rating]}</p><p className="text-xs text-muted-foreground">{label}</p></div>)}
        </div>
        <div className="flex justify-center gap-2"><Button variant="outline" onClick={onExit}><ArrowLeft /> Back to vocabulary</Button><Button onClick={onReviewMore}><RotateCcw /> Check due cards</Button></div>
      </CardContent>
    </Card>
  )
}

function scheduleLabel(state: VocabReviewCard['schedulingState']): string {
  if (state === 'RELEARNING') return 'Relearning'
  if (state === 'LEARNING') return 'Learning'
  return 'Review'
}

function EnrichmentSheet({
  open,
  onOpenChange,
  card,
  job,
  isStarting,
  isApplying,
  onGenerate,
  onApply,
  onDiscard,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
  card: VocabReviewCard
  job?: VocabEnrichmentJob
  isStarting: boolean
  isApplying: boolean
  onGenerate: (fields: VocabEnrichmentField[]) => void
  onApply: () => void
  onDiscard: () => void
}) {
  const [selected, setSelected] = useState<Set<VocabEnrichmentField>>(new Set())
  const existing = parseVocabMetadata(card.metadata)

  function toggle(field: VocabEnrichmentField, checked: boolean) {
    setSelected((current) => {
      const next = new Set(current)
      if (checked) next.add(field); else next.delete(field)
      return next
    })
  }

  function generate() {
    const fields = enrichmentFieldsForRequest(selected, true)
    if (!fields) return
    onGenerate(fields)
  }

  const hasExisting = (field: VocabEnrichmentField) => {
    if (field === 'EXAMPLE') return Boolean(existing.example)
    if (field === 'COLLOCATIONS') return Boolean(existing.collocations?.length)
    if (field === 'SYNONYMS') return Boolean(existing.synonyms?.length)
    if (field === 'ANTONYMS') return Boolean(existing.antonyms?.length)
    if (field === 'CONTRAST') return Boolean(existing.contrast)
    if (field === 'MNEMONIC') return Boolean(existing.mnemonic)
    if (field === 'WORD_FORMATION') return Boolean(existing.wordFormation)
    if (field === 'IMAGE') return Boolean(existing.frontImageId)
    return Boolean(wordAudioAssetId(card.front, existing))
  }

  const preview = job?.status === 'READY' ? job.preview : undefined
  const hasPreview = Boolean(preview || job?.imageBase64 || job?.wordAudioBase64 || job?.exampleAudioBase64)

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent className="w-full sm:max-w-lg">
        <SheetHeader>
          <SheetTitle>Enrich “{card.front}”</SheetTitle>
          <SheetDescription>
            {hasPreview ? 'Review the result. The card changes only when you apply it.'
              : job?.status === 'PENDING' ? 'Generation continues in the background while you review.'
                : 'Select what to generate. Opening this panel does not call AI or change the card.'}
          </SheetDescription>
        </SheetHeader>
        <div className="flex-1 overflow-y-auto px-4">
          {job?.status === 'PENDING' ? (
            <div className="flex min-h-56 flex-col items-center justify-center gap-3 text-center">
              <Loader2 className="size-6 animate-spin text-primary" />
              <div><p className="text-sm font-medium">Generating enrichment</p><p className="text-xs text-muted-foreground">You can close this panel and keep reviewing.</p></div>
            </div>
          ) : hasPreview ? (
            <div className="space-y-3 pb-4">
              <h3 className="text-sm font-semibold">Preview</h3>
              {job?.imageBase64 && job.imageMediaType && (
                <img src={`data:${job.imageMediaType};base64,${job.imageBase64}`} alt={job.imageAlt || 'Generated mnemonic for this vocabulary card'}
                  className="aspect-[4/3] w-full rounded-xl border object-cover" />
              )}
              {job?.wordAudioBase64 && job.wordAudioMediaType && (
                <MetadataSection title="Word audio">
                  <audio controls preload="metadata" className="w-full"
                    src={`data:${job.wordAudioMediaType};base64,${job.wordAudioBase64}`} />
                </MetadataSection>
              )}
              {job?.exampleAudioBase64 && job.exampleAudioMediaType && (
                <MetadataSection title="Example audio">
                  <audio controls preload="metadata" className="w-full"
                    src={`data:${job.exampleAudioMediaType};base64,${job.exampleAudioBase64}`} />
                </MetadataSection>
              )}
              {preview?.example && <MetadataSection title="Example"><p>{preview.example}</p></MetadataSection>}
              {(preview?.collocations.length ?? 0) > 0 && <MetadataSection title="Collocations"><TagList values={preview?.collocations ?? []} /></MetadataSection>}
              {(preview?.synonyms.length ?? 0) > 0 && <MetadataSection title="Synonyms"><TagList values={preview?.synonyms ?? []} /></MetadataSection>}
              {(preview?.antonyms.length ?? 0) > 0 && <MetadataSection title="Antonyms"><TagList values={preview?.antonyms ?? []} /></MetadataSection>}
              {preview?.contrast && <MetadataSection title="Contrast"><p>{preview.contrast}</p></MetadataSection>}
              {preview?.mnemonic && <MetadataSection title="Mnemonic"><p>{preview.mnemonic}</p></MetadataSection>}
              {preview?.wordFormation && <WordFormation value={preview.wordFormation} />}
            </div>
          ) : (
            <div className="space-y-2">
              {ENRICHMENT_OPTIONS.map((option) => (
                <Label key={option.field} className="flex min-h-14 cursor-pointer items-start gap-3 rounded-lg border p-3 hover:bg-muted/40">
                  <Checkbox checked={selected.has(option.field)} onCheckedChange={(value) => toggle(option.field, value === true)} />
                  {option.field === 'IMAGE' ? <ImageIcon className="mt-0.5 size-4 shrink-0 text-muted-foreground" /> : null}
                  {option.field === 'AUDIO' ? <Volume2 className="mt-0.5 size-4 shrink-0 text-muted-foreground" /> : null}
                  <span className="min-w-0"><span className="flex items-center gap-2 text-sm font-medium">{option.label}{hasExisting(option.field) && <Badge variant="outline" className="text-[10px]">replace existing</Badge>}</span><span className="text-xs font-normal text-muted-foreground">{option.description}</span></span>
                </Label>
              ))}
            </div>
          )}
        </div>
        <SheetFooter>
          {hasPreview ? (
            <div className="flex gap-2"><Button variant="outline" className="flex-1" onClick={onDiscard}>Discard</Button><Button className="flex-1" onClick={onApply} disabled={isApplying}>{isApplying ? <Loader2 className="animate-spin" /> : <Check />} Apply to card</Button></div>
          ) : job?.status === 'PENDING' ? (
            <Button variant="outline" onClick={() => onOpenChange(false)}>Continue reviewing</Button>
          ) : (
            <Button onClick={generate} disabled={selected.size === 0 || isStarting}>{isStarting ? <Loader2 className="animate-spin" /> : <Sparkles />} Generate selected <ChevronRight /></Button>
          )}
        </SheetFooter>
      </SheetContent>
    </Sheet>
  )
}

function ReviewerSkeleton() {
  return <div className="mx-auto w-full max-w-5xl space-y-4"><Skeleton className="h-10 w-full" /><Skeleton className="h-[520px] w-full rounded-xl" /></div>
}
