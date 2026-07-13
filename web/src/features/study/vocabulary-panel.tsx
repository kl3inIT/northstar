import { useEffect, useMemo, useRef, useState } from 'react'
import { toast } from 'sonner'
import { BookOpenCheck, ChevronDown, CircleStop, Ellipsis, Flame, Headphones, Loader2, Mic, Pencil, Pin, PinOff, PauseCircle, PlayCircle, Search, Sparkles, Trash2, Volume2 } from 'lucide-react'
import { AudioRecorder } from './audio-recorder'
import { VocabularyReviewer } from './vocabulary-reviewer'
import { cardHasDueDirection, cardMatchesDeck, deckQuery } from './vocabulary-review-state'
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
import { Switch } from '@/components/ui/switch'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { Textarea } from '@/components/ui/textarea'
import { cn } from '@/lib/utils'
import {
  parseVocabMetadata,
  mergeVocabMetadata,
  useAssessVocabPronunciation,
  useDeleteVocabAudioAttempt,
  useDeleteVocabCard,
  usePinVocabAttempt,
  useRecordVocabDictation,
  useUpdateVocabCard,
  useUpdateVocabDeckSettings,
  useVocabCards,
  useVocabDeckSettings,
  useVocabAudioAttempts,
  type VocabAudioAttempt,
  type VocabCard,
  type VocabLanguage,
  type PronunciationAssessment,
} from '@/lib/study-api'
import { useWavRecorder } from '@/lib/use-wav-recorder'
import { audioPracticeReference, comparableAudioTrendAttempts, exampleAudioAssetId, parseDictationDiff, shadowingPracticeReference, wordAudioAssetId } from './vocabulary-review-state'

const EMPTY_CARDS: VocabCard[] = []
const WEEK_MS = 7 * 24 * 60 * 60 * 1000

function recallTone(probability: number): string {
  if (probability < 0.5) return 'text-destructive'
  if (probability < 0.7) return 'text-warning'
  return 'text-muted-foreground'
}

function scheduleLabel(state: VocabCard['schedulingState']): string {
  if (state === 'RELEARNING') return 'Relearning'
  if (state === 'LEARNING') return 'Learning'
  return 'Review'
}

export function VocabularyPanel() {
  const [language, setLanguage] = useState<VocabLanguage>('ENGLISH')
  const [deck, setDeck] = useState('ALL')
  const cardsQuery = useVocabCards(language)
  const cards = cardsQuery.data ?? EMPTY_CARDS
  const [search, setSearch] = useState('')
  const [editing, setEditing] = useState<VocabCard | null>(null)
  const [deleting, setDeleting] = useState<VocabCard | null>(null)
  const [pronouncing, setPronouncing] = useState<VocabCard | null>(null)
  const [reviewLimit, setReviewLimit] = useState<number | null>(null)
  const update = useUpdateVocabCard()
  const selectedDeck = deck === 'ALL' ? undefined : deck
  const deckSettings = useVocabDeckSettings(language, selectedDeck)
  const updateDeckSettings = useUpdateVocabDeckSettings()

  const deckOptions = useMemo(() => [...new Set([
    'General',
    ...cards.map((card) => card.deck).filter((value): value is string => Boolean(value)),
  ])].sort((a, b) => a.localeCompare(b)), [cards])
  const scopedCards = useMemo(() => cards.filter((card) => cardMatchesDeck(card.deck, deck)), [cards, deck])
  const active = useMemo(() => scopedCards.filter((c) => !c.suspended), [scopedCards])
  const dueCount = useMemo(
    () => active.filter((card) => cardHasDueDirection(card, Date.now())).length,
    [active],
  )
  const newThisWeek = useMemo(() => {
    const cutoff = Date.now() - WEEK_MS
    return scopedCards.filter((c) => new Date(c.createdAt).getTime() >= cutoff).length
  }, [scopedCards])

  const visible = useMemo(() => {
    const query = search.trim().toLocaleLowerCase()
    const filtered = query
      ? scopedCards.filter((c) =>
          c.front.toLocaleLowerCase().includes(query)
          || c.back.toLocaleLowerCase().includes(query)
          || (parseVocabMetadata(c.metadata).reading ?? '').toLocaleLowerCase().includes(query))
      : scopedCards
    // Due notes first, then weaker memories; suspended notes remain last.
    return [...filtered].sort((a, b) => {
      if (a.suspended !== b.suspended) return a.suspended ? 1 : -1
      const now = Date.now()
      const aDue = cardHasDueDirection(a, now)
      const bDue = cardHasDueDirection(b, now)
      if (aDue !== bDue) return aDue ? -1 : 1
      return a.recallProbability - b.recallProbability
    })
  }, [scopedCards, search])

  function toggleSuspended(card: VocabCard) {
    update.mutate(
      {
        id: card.id,
        front: card.front,
        back: card.back,
        metadata: card.metadata ?? undefined,
        language: card.language,
        deck: card.deck,
        disciplineId: card.disciplineId ?? undefined,
        suspended: !card.suspended,
        productionEnabled: card.productionEnabled,
      },
      {
        onSuccess: () => toast.success(card.suspended ? `Resumed “${card.front}”` : `Paused “${card.front}”`),
        onError: (error) => toast.error(error.message),
      },
    )
  }

  if (reviewLimit !== null) {
    return (
      <>
        <VocabularyReviewer limit={reviewLimit} language={language}
          deck={deckQuery(deck)} onExit={() => setReviewLimit(null)}
          onEdit={(reviewCard) => setEditing(cards.find((item) => item.id === reviewCard.id) ?? null)}
          onPronounce={(reviewCard) => setPronouncing(cards.find((item) => item.id === reviewCard.id) ?? null)} />
        <EditCardDialog key={editing?.id ?? 'none'} card={editing} onClose={() => setEditing(null)} />
        <PronunciationDialog key={pronouncing?.id ?? 'no-pronunciation'} card={pronouncing} onClose={() => setPronouncing(null)} />
      </>
    )
  }

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-col gap-3 rounded-lg border bg-card p-3 sm:flex-row sm:items-center sm:justify-between">
        <Tabs value={language} onValueChange={(value) => {
          setLanguage(value as VocabLanguage)
          setDeck('ALL')
          setSearch('')
        }}>
          <TabsList>
            <TabsTrigger value="ENGLISH">English</TabsTrigger>
            <TabsTrigger value="CHINESE">Chinese</TabsTrigger>
          </TabsList>
        </Tabs>
        <div className="flex items-center gap-2">
          <Label htmlFor="vocab-deck" className="text-xs text-muted-foreground">Review deck</Label>
          <Select value={deck} onValueChange={setDeck}>
            <SelectTrigger id="vocab-deck" className="w-44"><SelectValue /></SelectTrigger>
            <SelectContent>
              <SelectItem value="ALL">All decks</SelectItem>
              {deckOptions.map((item) => <SelectItem key={item} value={item}>{item}</SelectItem>)}
            </SelectContent>
          </Select>
          {selectedDeck && (
            <Label className="ml-2 flex cursor-pointer items-center gap-2 rounded-md border px-3 py-2 text-xs font-normal">
              <Switch size="sm" checked={deckSettings.data?.productionDefault ?? false}
                disabled={deckSettings.isLoading || updateDeckSettings.isPending}
                onCheckedChange={(productionDefault) => updateDeckSettings.mutate(
                  { language, deck: selectedDeck, productionDefault },
                  { onError: (error) => toast.error(error.message) },
                )} />
              New cards: two directions
            </Label>
          )}
        </div>
      </div>
      <VocabStats tracked={active.length} due={dueCount} newThisWeek={newThisWeek} />

      <div className="flex flex-col gap-2 sm:flex-row sm:items-center">
        <div className="relative min-w-0 flex-1">
          <Search className="pointer-events-none absolute left-2.5 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            aria-label="Search vocabulary"
            value={search}
            onChange={(event) => setSearch(event.target.value)}
            placeholder={`Search ${language === 'ENGLISH' ? 'English' : 'Chinese'} · ${deck === 'ALL' ? 'all decks' : deck}`}
            className="pl-8"
          />
        </div>
        <div className="flex shrink-0 items-center">
          <Button className="rounded-r-none" disabled={dueCount === 0} onClick={() => setReviewLimit(20)}>
            <PlayCircle /> Review due · {dueCount}
          </Button>
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button variant="default" size="icon" className="rounded-l-none border-l border-primary-foreground/20" disabled={dueCount === 0} aria-label="Choose review size">
                <ChevronDown />
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end">
              {[10, 20, 30].map((limit) => <DropdownMenuItem key={limit} onClick={() => setReviewLimit(limit)}>Review up to {limit} cards</DropdownMenuItem>)}
            </DropdownMenuContent>
          </DropdownMenu>
        </div>
      </div>

      {cardsQuery.error ? (
        <div className="rounded-lg border border-destructive/40 bg-destructive/5 p-4 text-sm text-destructive">
          Could not load vocabulary. Refresh the page to retry.
        </div>
      ) : (
        <CardsTable
          rows={visible}
          isLoading={cardsQuery.isLoading}
          onEdit={setEditing}
          onDelete={setDeleting}
          onPronounce={setPronouncing}
          onToggleSuspended={toggleSuspended}
        />
      )}

      <EditCardDialog key={editing?.id ?? 'none'} card={editing} onClose={() => setEditing(null)} />
      <DeleteCardDialog card={deleting} onClose={() => setDeleting(null)} />
      <PronunciationDialog key={pronouncing?.id ?? 'no-pronunciation'} card={pronouncing} onClose={() => setPronouncing(null)} />
    </div>
  )
}

function VocabStats({ tracked, due, newThisWeek }: { tracked: number; due: number; newThisWeek: number }) {
  const stats = [
    { label: 'Tracked words', value: String(tracked), icon: BookOpenCheck, tone: 'text-primary', bg: 'bg-primary/10' },
    {
      label: 'Due now',
      value: String(due),
      caption: 'FSRS · 90% retention',
      icon: Flame,
      tone: due > 0 ? 'text-warning' : 'text-muted-foreground',
      bg: due > 0 ? 'bg-warning/10' : 'bg-muted',
    },
    { label: 'New this week', value: String(newThisWeek), caption: 'pace ~10 a day', icon: Sparkles, tone: 'text-insight', bg: 'bg-insight/10' },
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

function CardsTable({ rows, isLoading, onEdit, onDelete, onPronounce, onToggleSuspended }: {
  rows: VocabCard[]
  isLoading: boolean
  onEdit: (card: VocabCard) => void
  onDelete: (card: VocabCard) => void
  onPronounce: (card: VocabCard) => void
  onToggleSuspended: (card: VocabCard) => void
}) {
  return (
    <div className="overflow-hidden rounded-lg border bg-card">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>Word</TableHead>
            <TableHead>Meaning</TableHead>
            <TableHead className="text-right">Recall</TableHead>
            <TableHead className="hidden text-right sm:table-cell">Reviews</TableHead>
            <TableHead />
          </TableRow>
        </TableHeader>
        <TableBody>
          {isLoading && Array.from({ length: 5 }, (_, index) => (
            <TableRow key={index}>
              <TableCell><Skeleton className="h-9 w-28" /></TableCell>
              <TableCell><Skeleton className="h-4 w-40" /></TableCell>
              <TableCell><Skeleton className="ml-auto h-4 w-10" /></TableCell>
              <TableCell className="hidden sm:table-cell"><Skeleton className="ml-auto h-4 w-8" /></TableCell>
              <TableCell><Skeleton className="h-8 w-8" /></TableCell>
            </TableRow>
          ))}
          {!isLoading && rows.length === 0 && (
            <TableRow>
              <TableCell colSpan={5} className="h-40 text-center">
                <div className="flex flex-col items-center gap-1.5">
                  <p className="text-sm font-medium">No vocabulary yet</p>
                  <p className="text-xs text-muted-foreground">
                    Capture one — "từ mới: 磨蹭 = lề mề" — the AI adds pinyin and an example
                  </p>
                </div>
              </TableCell>
            </TableRow>
          )}
          {!isLoading && rows.map((card) => {
            const meta = parseVocabMetadata(card.metadata)
            return (
              <TableRow key={card.id} className={cn(card.suspended && 'opacity-50')}>
                <TableCell>
                  <div className="min-w-28">
                    <p className="text-sm font-medium">{card.front}</p>
                    {(meta.reading || meta.partOfSpeech) && <p className="text-xs text-muted-foreground">{[meta.reading, meta.partOfSpeech].filter(Boolean).join(' · ')}</p>}
                    <Badge variant="outline" className="mt-1 h-5 rounded px-1.5 text-[10px] font-normal">{card.deck ?? 'General'}</Badge>
                    {card.productionEnabled && <Badge variant="secondary" className="ml-1 mt-1 h-5 rounded px-1.5 text-[10px] font-normal">2 directions</Badge>}
                    <Badge variant="outline" className="ml-1 mt-1 h-5 rounded px-1.5 text-[10px] font-normal">{scheduleLabel(card.schedulingState)}</Badge>
                    {(card.leech || card.productionLeech) && <Badge variant="destructive" className="ml-1 mt-1 h-5 rounded px-1.5 text-[10px] font-normal">Leech</Badge>}
                  </div>
                </TableCell>
                <TableCell>
                  <div className="min-w-32 max-w-md">
                    <p className="truncate text-sm">{card.back}</p>
                    {meta.example && <p className="truncate text-xs text-muted-foreground" title={meta.example}>{meta.example}</p>}
                    {card.suspended && (
                      <Badge variant="outline" className="mt-1 h-5 rounded px-1.5 text-[10px] font-normal text-muted-foreground">paused</Badge>
                    )}
                  </div>
                </TableCell>
                <TableCell className="text-right">
                  <span className={cn('text-sm font-semibold tabular-nums', recallTone(card.recallProbability))}>
                    {Math.round(card.recallProbability * 100)}%
                    {!card.suspended && cardHasDueDirection(card, Date.now()) && ' · due'}
                  </span>
                </TableCell>
                <TableCell className="hidden text-right sm:table-cell">
                  <span className="text-xs tabular-nums text-muted-foreground">{card.reviewCount}</span>
                </TableCell>
                <TableCell>
                  <div className="flex justify-end gap-1">
                    <Button variant="ghost" size="icon" className="size-8" aria-label={`Practice pronunciation for ${card.front}`} onClick={() => onPronounce(card)}>
                      <Mic className="size-4" />
                    </Button>
                    <DropdownMenu>
                    <DropdownMenuTrigger asChild>
                      <Button variant="ghost" size="icon" className="size-8" aria-label={`Actions for ${card.front}`}>
                        <Ellipsis className="size-4" />
                      </Button>
                    </DropdownMenuTrigger>
                    <DropdownMenuContent align="end">
                      <DropdownMenuItem onClick={() => onEdit(card)}><Pencil className="size-4" /> Edit</DropdownMenuItem>
                      <DropdownMenuItem onClick={() => onToggleSuspended(card)}>
                        {card.suspended ? <PlayCircle className="size-4" /> : <PauseCircle className="size-4" />}
                        {card.suspended ? 'Resume' : 'Pause'}
                      </DropdownMenuItem>
                      <DropdownMenuItem variant="destructive" onClick={() => onDelete(card)}><Trash2 className="size-4" /> Delete</DropdownMenuItem>
                    </DropdownMenuContent>
                    </DropdownMenu>
                  </div>
                </TableCell>
              </TableRow>
            )
          })}
        </TableBody>
      </Table>
    </div>
  )
}

function EditCardDialog({ card, onClose }: { card: VocabCard | null; onClose: () => void }) {
  return (
    <Dialog open={Boolean(card)} onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="sm:max-w-md">
        {card && <EditCardForm card={card} onClose={onClose} />}
      </DialogContent>
    </Dialog>
  )
}

function EditCardForm({ card, onClose }: { card: VocabCard; onClose: () => void }) {
  const meta = parseVocabMetadata(card.metadata)
  const [front, setFront] = useState(card.front)
  const [back, setBack] = useState(card.back)
  const [reading, setReading] = useState(meta.reading ?? '')
  const [partOfSpeech, setPartOfSpeech] = useState(meta.partOfSpeech ?? '')
  const [example, setExample] = useState(meta.example ?? '')
  const [language, setLanguage] = useState<VocabLanguage>(card.language)
  const [deck, setDeck] = useState(card.deck ?? '')
  const [productionEnabled, setProductionEnabled] = useState(card.productionEnabled)
  const update = useUpdateVocabCard()

  function save() {
    if (!front.trim() || !back.trim()) {
      toast.error('Enter both the word and its meaning')
      return
    }
    update.mutate(
      {
        id: card.id,
        front: front.trim(),
        back: back.trim(),
        metadata: mergeVocabMetadata(card.metadata, { reading, partOfSpeech, example }),
        language,
        deck: deck.trim() || undefined,
        disciplineId: card.disciplineId ?? undefined,
        suspended: card.suspended,
        productionEnabled,
      },
      {
        onSuccess: () => { toast.success('Card updated'); onClose() },
        onError: (error) => toast.error(error.message),
      },
    )
  }

  return (
    <>
      <DialogHeader><DialogTitle>Edit vocab card</DialogTitle></DialogHeader>
      <div className="grid gap-3">
        <div className="grid gap-3 sm:grid-cols-2">
          <div className="grid gap-1.5">
            <Label htmlFor="card-language">Language</Label>
            <Select value={language} onValueChange={(value) => setLanguage(value as VocabLanguage)}>
              <SelectTrigger id="card-language" className="w-full"><SelectValue /></SelectTrigger>
              <SelectContent><SelectItem value="ENGLISH">English</SelectItem><SelectItem value="CHINESE">Chinese</SelectItem></SelectContent>
            </Select>
          </div>
          <div className="grid gap-1.5">
            <Label htmlFor="card-deck">Deck</Label>
            <Input id="card-deck" placeholder="General" value={deck} onChange={(event) => setDeck(event.target.value)} />
          </div>
        </div>
        <div className="grid gap-3 sm:grid-cols-2">
          <div className="grid gap-1.5">
            <Label htmlFor="card-front">Word</Label>
            <Input id="card-front" value={front} onChange={(event) => setFront(event.target.value)} />
          </div>
          <div className="grid gap-1.5">
            <Label htmlFor="card-reading">Reading</Label>
            <Input id="card-reading" placeholder="pinyin / IPA" value={reading} onChange={(event) => setReading(event.target.value)} />
          </div>
        </div>
        <div className="grid gap-1.5">
          <Label htmlFor="card-part-of-speech">Part of speech</Label>
          <Input id="card-part-of-speech" placeholder="noun / verb / adjective / phrase" value={partOfSpeech} onChange={(event) => setPartOfSpeech(event.target.value)} />
        </div>
        <div className="grid gap-1.5">
          <Label htmlFor="card-back">Meaning</Label>
          <Input id="card-back" value={back} onChange={(event) => setBack(event.target.value)} />
        </div>
        <div className="grid gap-1.5">
          <Label htmlFor="card-example">Example</Label>
          <Input id="card-example" placeholder="One sentence — translation" value={example} onChange={(event) => setExample(event.target.value)} />
        </div>
        <Label className="flex cursor-pointer items-center justify-between gap-4 rounded-lg border p-3 font-normal">
          <span><span className="block text-sm font-medium">Practice both directions</span><span className="block text-xs text-muted-foreground">Meaning → word gets its own memory schedule.</span></span>
          <Switch checked={productionEnabled} onCheckedChange={setProductionEnabled} />
        </Label>
      </div>
      <DialogFooter>
        <Button variant="outline" onClick={onClose}>Cancel</Button>
        <Button onClick={save} disabled={update.isPending}>Save</Button>
      </DialogFooter>
    </>
  )
}

function DeleteCardDialog({ card, onClose }: { card: VocabCard | null; onClose: () => void }) {
  const remove = useDeleteVocabCard()
  return (
    <Dialog open={Boolean(card)} onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="sm:max-w-sm">
        <DialogHeader><DialogTitle>Delete vocab card?</DialogTitle></DialogHeader>
        <p className="text-sm text-muted-foreground">
          {card ? `“${card.front}” and its review history — pausing keeps both.` : ''}
        </p>
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>Cancel</Button>
          <Button
            variant="destructive"
            disabled={remove.isPending}
            onClick={() => card && remove.mutate(card.id, {
              onSuccess: () => { toast.success('Card deleted'); onClose() },
              onError: (error) => toast.error(error.message),
            })}
          >Delete</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

type AudioPracticeMode = 'WORD' | 'SHADOWING' | 'DICTATION'
type ShadowingStage = 'idle' | 'preparing' | 'following' | 'finishing' | 'ready'

function PronunciationDialog({ card, onClose }: { card: VocabCard | null; onClose: () => void }) {
  const [mode, setMode] = useState<AudioPracticeMode>('WORD')
  const [dictationAnswer, setDictationAnswer] = useState('')
  const [shadowingStage, setShadowingStage] = useState<ShadowingStage>('idle')
  const playbackRef = useRef<PracticeSpeechPlayback | null>(null)
  const shadowingSessionRef = useRef(0)
  const shadowingTailRef = useRef<number | null>(null)
  const recorder = useWavRecorder(30)
  const assessment = useAssessVocabPronunciation()
  const dictation = useRecordVocabDictation()
  const history = useVocabAudioAttempts(card?.id, Boolean(card))
  const metadata = card ? parseVocabMetadata(card.metadata) : {}
  const reference = card ? audioPracticeReference(card.front, metadata, mode) : ''
  const shadowingReference = shadowingPracticeReference(metadata)
  const shadowingActive = shadowingStage === 'preparing'
    || shadowingStage === 'following'
    || shadowingStage === 'finishing'

  function stopPlayback() {
    playbackRef.current?.stop()
    playbackRef.current = null
    if (shadowingTailRef.current !== null) window.clearTimeout(shadowingTailRef.current)
    shadowingTailRef.current = null
  }

  function playReference(): PracticeSpeechPlayback | null {
    if (!card || !reference) return null
    stopPlayback()
    const assetId = mode === 'WORD'
      ? wordAudioAssetId(card.front, metadata)
      : exampleAudioAssetId(metadata) || (mode === 'DICTATION' ? wordAudioAssetId(card.front, metadata) : undefined)
    const playback = playPracticeSpeech(reference, assetId, metadata.frontAudioLocale)
    playbackRef.current = playback
    void playback.finished.then(() => {
      if (playbackRef.current === playback) playbackRef.current = null
    })
    return playback
  }

  useEffect(() => {
    shadowingSessionRef.current += 1
    stopPlayback()
    recorder.reset()
    assessment.reset()
    dictation.reset()
    setDictationAnswer('')
    setShadowingStage('idle')
    // Each practice mode intentionally starts with a clean attempt.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [mode])

  useEffect(() => () => {
    shadowingSessionRef.current += 1
    playbackRef.current?.stop()
    if (shadowingTailRef.current !== null) window.clearTimeout(shadowingTailRef.current)
  }, [])

  function listen() {
    playReference()
  }

  async function startShadowing() {
    if (!card || !shadowingReference || shadowingActive) return
    const session = ++shadowingSessionRef.current
    stopPlayback()
    setShadowingStage('preparing')
    const microphoneReady = await recorder.start()
    if (!microphoneReady || shadowingSessionRef.current !== session) {
      if (microphoneReady) recorder.reset()
      if (shadowingSessionRef.current === session) setShadowingStage('idle')
      return
    }

    setShadowingStage('following')
    const playback = playReference()
    if (!playback) {
      recorder.reset()
      setShadowingStage('idle')
      return
    }
    await playback.finished
    if (shadowingSessionRef.current !== session) return
    setShadowingStage('finishing')
    shadowingTailRef.current = window.setTimeout(() => {
      if (shadowingSessionRef.current !== session) return
      recorder.stop()
      setShadowingStage('ready')
      shadowingTailRef.current = null
    }, 1_200)
  }

  function stopShadowing() {
    shadowingSessionRef.current += 1
    stopPlayback()
    if (recorder.state === 'recording') recorder.stop()
    else recorder.reset()
    setShadowingStage(recorder.state === 'recording' ? 'ready' : 'idle')
  }

  function submitSpeech() {
    if (!card || !recorder.audio || mode === 'DICTATION') return
    assessment.mutate({ id: card.id, audio: recorder.audio, mode }, {
      onError: (error) => toast.error(error.message),
    })
  }

  function submitDictation() {
    if (!card || !dictationAnswer.trim()) return
    dictation.mutate({ id: card.id, answer: dictationAnswer.trim() }, {
      onError: (error) => toast.error(error.message),
    })
  }

  return (
    <Dialog open={Boolean(card)} onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-2xl">
        {card && (
          <>
            <DialogHeader>
              <DialogTitle>Audio practice · {card.front}</DialogTitle>
              <p className="text-sm text-muted-foreground">Practice results are separate from FSRS review ratings.</p>
            </DialogHeader>
            <Tabs value={mode} onValueChange={(value) => setMode(value as AudioPracticeMode)}>
              <TabsList className="grid w-full grid-cols-3">
                <TabsTrigger value="WORD">Word</TabsTrigger>
                <TabsTrigger value="SHADOWING">Shadowing</TabsTrigger>
                <TabsTrigger value="DICTATION">Dictation</TabsTrigger>
              </TabsList>
            </Tabs>

            <div className="flex flex-col gap-4 rounded-lg border bg-muted/20 p-4">
              <div className="flex items-start justify-between gap-3">
                <div className="min-w-0">
                  <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
                    {mode === 'WORD' ? 'Listen, then say the word' : mode === 'SHADOWING' ? 'Follow connected speech' : 'Listen, then type'}
                  </p>
                  {mode !== 'DICTATION' && reference && <p className="mt-2 text-lg font-medium leading-relaxed">{reference}</p>}
                  {mode === 'WORD' && metadata.reading && <p className="text-sm text-muted-foreground">{metadata.reading}</p>}
                </div>
                <Button variant="outline" onClick={listen} disabled={!reference || shadowingActive}>
                  <Volume2 /> {mode === 'SHADOWING' ? 'Preview' : 'Listen'}
                </Button>
              </div>

              {mode === 'DICTATION' ? (
                <>
                  <Textarea value={dictationAnswer} onChange={(event) => setDictationAnswer(event.target.value)}
                    placeholder="Type exactly what you heard" aria-label="Dictation answer" />
                  <Button onClick={submitDictation} disabled={!dictationAnswer.trim() || dictation.isPending}>
                    {dictation.isPending ? 'Checking…' : 'Check dictation'}
                  </Button>
                  {dictation.data && <DictationResultView result={dictation.data} />}
                </>
              ) : mode === 'SHADOWING' ? (
                shadowingReference ? (
                  <>
                    <div className="flex items-start gap-3 rounded-lg border border-primary/20 bg-primary/5 p-3">
                      <Headphones className="mt-0.5 size-4 shrink-0 text-primary" />
                      <div className="space-y-1">
                        <p className="text-sm font-medium">Speak one beat behind the model</p>
                        <p className="text-xs leading-relaxed text-muted-foreground">
                          Keep talking while the model is playing. Headphones prevent the reference audio from leaking into your recording.
                        </p>
                      </div>
                    </div>

                    <div className="flex flex-wrap items-center justify-between gap-3 rounded-lg border bg-card p-3" aria-live="polite">
                      <div className="min-w-0">
                        {shadowingStage === 'preparing' && <p className="flex items-center gap-2 text-sm font-medium"><Loader2 className="size-4 animate-spin" /> Opening microphone…</p>}
                        {shadowingStage === 'following' && <p className="flex items-center gap-2 text-sm font-medium text-primary"><span className="size-2 animate-pulse rounded-full bg-primary" /> Follow now · {recorder.seconds.toFixed(1)}s</p>}
                        {shadowingStage === 'finishing' && <p className="flex items-center gap-2 text-sm font-medium"><span className="size-2 animate-pulse rounded-full bg-primary" /> Finish the last phrase…</p>}
                        {(shadowingStage === 'idle' || shadowingStage === 'ready') && <p className="text-sm font-medium">Mic and model start together</p>}
                        <p className="mt-1 text-xs text-muted-foreground">The recording stops automatically after the model.</p>
                      </div>
                      {shadowingActive ? (
                        <Button type="button" variant="destructive" onClick={stopShadowing}>
                          <CircleStop className="size-4" /> Stop
                        </Button>
                      ) : (
                        <Button type="button" onClick={() => void startShadowing()}>
                          <Mic className="size-4" /> {recorder.audio ? 'Try again' : 'Start shadowing'}
                        </Button>
                      )}
                    </div>

                    {recorder.audio && <BlobAudio blob={recorder.audio} label="Replay your shadowing attempt" />}
                    <Button onClick={submitSpeech} disabled={!recorder.audio || assessment.isPending || shadowingActive}>
                      {assessment.isPending ? 'Assessing…' : 'Save and assess pronunciation'}
                    </Button>
                    {assessment.data && <PronunciationResultView result={assessment.data} />}
                  </>
                ) : (
                  <div className="rounded-lg border border-dashed bg-card p-5 text-center">
                    <p className="text-sm font-medium">Add a connected example to unlock shadowing</p>
                    <p className="mx-auto mt-1 max-w-md text-xs leading-relaxed text-muted-foreground">
                      Shadowing needs a phrase or sentence of at least four words. It never falls back to the isolated vocabulary item.
                    </p>
                    <Button type="button" variant="outline" className="mt-4" onClick={onClose}>Back to card</Button>
                  </div>
                )
              ) : (
                <>
                  <AudioRecorder recorder={recorder} maximumSeconds={30} />
                  {recorder.audio && <BlobAudio blob={recorder.audio} label="Replay this recording before saving" />}
                  <Button onClick={submitSpeech} disabled={!recorder.audio || assessment.isPending}>
                    {assessment.isPending ? 'Assessing…' : 'Save and assess'}
                  </Button>
                  {assessment.data && <PronunciationResultView result={assessment.data} />}
                </>
              )}
            </div>

            <AttemptHistory cardId={card.id} attempts={history.data ?? []} loading={history.isLoading} mode={mode} />
          </>
        )}
      </DialogContent>
    </Dialog>
  )
}

interface PracticeSpeechPlayback {
  finished: Promise<void>
  stop: () => void
}

function playPracticeSpeech(text: string, assetId?: string, locale?: string): PracticeSpeechPlayback {
  let resolveFinished: () => void = () => {}
  let settled = false
  let stopCurrent: () => void = () => {}
  const finished = new Promise<void>((resolve) => { resolveFinished = resolve })
  const settle = () => {
    if (settled) return
    settled = true
    resolveFinished()
  }
  const fallback = () => {
    if (!('speechSynthesis' in window)) {
      toast.error('Text-to-speech is not available in this browser')
      settle()
      return
    }
    window.speechSynthesis.cancel()
    const utterance = new SpeechSynthesisUtterance(text)
    utterance.lang = locale || (/\p{Script=Han}/u.test(text) ? 'zh-CN' : 'en-US')
    utterance.onend = settle
    utterance.onerror = settle
    stopCurrent = () => {
      window.speechSynthesis.cancel()
      settle()
    }
    window.speechSynthesis.speak(utterance)
  }
  if (!assetId) {
    fallback()
  } else {
    const audio = new Audio(`/api/speech/assets/${encodeURIComponent(assetId)}/audio`)
    let fallbackStarted = false
    const useFallback = () => {
      if (fallbackStarted || settled) return
      fallbackStarted = true
      audio.pause()
      audio.removeEventListener('error', useFallback)
      audio.removeAttribute('src')
      fallback()
    }
    audio.addEventListener('ended', settle, { once: true })
    audio.addEventListener('error', useFallback, { once: true })
    stopCurrent = () => {
      audio.pause()
      audio.removeAttribute('src')
      settle()
    }
    void audio.play().catch(useFallback)
  }
  return {
    finished,
    stop: () => {
      stopCurrent()
      settle()
    },
  }
}

function BlobAudio({ blob, label }: { blob: Blob; label: string }) {
  const [url, setUrl] = useState('')
  useEffect(() => {
    const next = URL.createObjectURL(blob)
    setUrl(next)
    return () => URL.revokeObjectURL(next)
  }, [blob])
  return (
    <div>
      <p className="mb-1 text-xs text-muted-foreground">{label}</p>
      {url ? <audio controls src={url} className="w-full" /> : <Skeleton className="h-10 w-full" />}
    </div>
  )
}

function DictationResultView({ result }: { result: VocabAudioAttempt }) {
  const diff = parseDictationDiff(result.dictationDiff)
  return (
    <div className="rounded-lg border bg-card p-3" aria-live="polite">
      <p className="text-sm font-medium">{result.accuracy === 100 ? 'Exact match' : `${Math.round(result.accuracy ?? 0)}% text match`}</p>
      <p className="mt-1 text-sm">Answer: {result.referenceText}</p>
      {(result.accuracy ?? 0) < 100 && diff.length > 0 && (
        <div className="mt-3 flex flex-wrap items-center gap-1.5" aria-label="Dictation differences">
          {diff.map((token, index) => token.kind === 'MATCH' ? (
            <span key={`${token.kind}-${index}`} className="text-sm">{token.actual}</span>
          ) : token.kind === 'MISSING' ? (
            <Badge key={`${token.kind}-${index}`} variant="destructive">Missing: {token.expected}</Badge>
          ) : token.kind === 'EXTRA' ? (
            <Badge key={`${token.kind}-${index}`} variant="outline" className="border-warning/40 text-warning">Extra: {token.actual}</Badge>
          ) : (
            <Badge key={`${token.kind}-${index}`} variant="outline" className="border-warning/40 text-warning">
              {token.expected} → {token.actual}
            </Badge>
          ))}
        </div>
      )}
    </div>
  )
}

function AttemptHistory({ cardId, attempts, loading, mode }: {
  cardId: string
  attempts: VocabAudioAttempt[]
  loading: boolean
  mode: AudioPracticeMode
}) {
  const pin = usePinVocabAttempt()
  const remove = useDeleteVocabAudioAttempt()
  const scored = comparableAudioTrendAttempts(attempts, mode)
  const newest = scored[0]
  const oldest = scored.at(-1)
  const trend = (key: 'accuracy' | 'fluency' | 'prosody') => {
    const latestValue = newest?.[key]
    const oldestValue = oldest?.[key]
    return typeof latestValue === 'number' && typeof oldestValue === 'number' ? Math.round(latestValue - oldestValue) : null
  }

  return (
    <section className="space-y-3">
      <div className="flex items-center justify-between">
        <h3 className="flex items-center gap-2 text-sm font-semibold"><Headphones className="size-4" /> Practice history</h3>
        <span className="text-xs text-muted-foreground">Recordings expire after 180 days unless pinned</span>
      </div>
      {scored.length > 1 && (mode === 'DICTATION' ? (
        <div className="rounded-lg border bg-card p-2">
          <p className="text-xs text-muted-foreground">Text match trend</p>
          <p className="text-sm font-semibold tabular-nums">
            {trend('accuracy') === null ? '—' : `${trend('accuracy')! >= 0 ? '+' : ''}${trend('accuracy')}`}
          </p>
        </div>
      ) : (
        <div className="grid grid-cols-3 gap-2">
          {(['accuracy', 'fluency', 'prosody'] as const).map((key) => (
            <div key={key} className="rounded-lg border bg-card p-2">
              <p className="text-xs capitalize text-muted-foreground">{key}</p>
              <p className="text-sm font-semibold tabular-nums">{trend(key) === null ? '—' : `${trend(key)! >= 0 ? '+' : ''}${trend(key)}`}</p>
            </div>
          ))}
        </div>
      ))}
      {loading ? <Skeleton className="h-20 w-full" /> : attempts.length === 0 ? (
        <p className="rounded-lg border border-dashed p-4 text-center text-sm text-muted-foreground">No saved attempts yet.</p>
      ) : (
        <div className="max-h-64 space-y-2 overflow-y-auto pr-1">
          {attempts.map((attempt) => (
            <article key={attempt.id} className="rounded-lg border bg-card p-3">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <div className="flex items-center gap-2">
                  <Badge variant="secondary">{attempt.mode?.toLowerCase()}</Badge>
                  {attempt.providerId && <span className="text-xs text-muted-foreground">{attempt.providerId}</span>}
                  <span className="text-xs text-muted-foreground">{attempt.createdAt ? new Date(attempt.createdAt).toLocaleString() : ''}</span>
                </div>
                <div className="flex items-center gap-1">
                  {attempt.recordingAvailable && attempt.id && (
                    <Button size="icon-sm" variant="ghost" aria-label={attempt.recordingPinned ? 'Unpin recording' : 'Pin recording'}
                      onClick={() => pin.mutate({ attemptId: attempt.id!, pinned: !attempt.recordingPinned }, { onError: (error) => toast.error(error.message) })}>
                      {attempt.recordingPinned ? <PinOff /> : <Pin />}
                    </Button>
                  )}
                  {attempt.id && (
                    <Button size="icon-sm" variant="ghost" aria-label="Delete attempt"
                      onClick={() => remove.mutate({ attemptId: attempt.id!, cardId }, { onError: (error) => toast.error(error.message) })}>
                      <Trash2 />
                    </Button>
                  )}
                </div>
              </div>
              {attempt.mode === 'DICTATION' ? (
                <p className="mt-2 text-xs text-muted-foreground">Text match {Math.round(attempt.accuracy ?? 0)}%</p>
              ) : (
                <div className="mt-2 flex gap-3 text-xs tabular-nums text-muted-foreground">
                  <span>Accuracy {score(attempt.accuracy)}</span><span>Fluency {score(attempt.fluency)}</span><span>Prosody {score(attempt.prosody)}</span>
                </div>
              )}
              {attempt.recordingAvailable && attempt.recordingUrl && <audio controls preload="none" src={attempt.recordingUrl} className="mt-2 h-9 w-full" />}
            </article>
          ))}
        </div>
      )}
    </section>
  )
}

function score(value: number | undefined) {
  return typeof value === 'number' ? `${Math.round(value)}/100` : '—'
}

function PronunciationResultView({ result }: { result: PronunciationAssessment }) {
  return (
    <div className="flex flex-col gap-3 rounded-lg border bg-muted/20 p-3">
      <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">Azure delivery · unofficial</p>
      <div className="grid grid-cols-3 gap-2">
        <PronunciationScore label="Accuracy" value={result.accuracy} />
        <PronunciationScore label="Fluency" value={result.fluency} />
        <PronunciationScore label="Prosody" value={result.prosody} />
      </div>
      {result.recognizedText && <p className="text-xs text-muted-foreground">Heard: “{result.recognizedText}”</p>}
    </div>
  )
}

function PronunciationScore({ label, value }: { label: string; value: number | undefined }) {
  return <div className="rounded-lg bg-card p-3"><p className="text-xs text-muted-foreground">{label}</p><p className="text-xl font-semibold tabular-nums">{typeof value === 'number' ? Math.round(value) : '—'}<span className="text-xs font-normal text-muted-foreground"> / 100</span></p></div>
}
