import { useMemo, useState } from 'react'
import { toast } from 'sonner'
import { BookOpenCheck, ChevronDown, Ellipsis, Flame, Mic, Pencil, PauseCircle, PlayCircle, Search, Sparkles, Trash2 } from 'lucide-react'
import { AudioRecorder } from './audio-recorder'
import { VocabularyReviewer } from './vocabulary-reviewer'
import { cardMatchesDeck, deckQuery } from './vocabulary-review-state'
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
import { cn } from '@/lib/utils'
import {
  parseVocabMetadata,
  mergeVocabMetadata,
  useAssessVocabPronunciation,
  useDeleteVocabCard,
  useUpdateVocabCard,
  useUpdateVocabDeckSettings,
  useVocabCards,
  useVocabDeckSettings,
  type VocabCard,
  type VocabLanguage,
  type PronunciationAssessment,
} from '@/lib/study-api'
import { useWavRecorder } from '@/lib/use-wav-recorder'

const EMPTY_CARDS: VocabCard[] = []
const WEEK_MS = 7 * 24 * 60 * 60 * 1000

function recallTone(probability: number): string {
  if (probability < 0.5) return 'text-destructive'
  if (probability < 0.7) return 'text-warning'
  return 'text-muted-foreground'
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
  const atRiskCount = useMemo(
    () => active.filter((c) => c.recallProbability < 0.7).length,
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
    // At-risk first, suspended last — the table doubles as the priority list.
    return [...filtered].sort((a, b) => {
      if (a.suspended !== b.suspended) return a.suspended ? 1 : -1
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
      <VocabStats tracked={active.length} atRisk={atRiskCount} newThisWeek={newThisWeek} />

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
          <Button className="rounded-r-none" disabled={active.length === 0} onClick={() => setReviewLimit(20)}>
            <PlayCircle /> Review {language === 'ENGLISH' ? 'English' : 'Chinese'} · {deck === 'ALL' ? 'All decks' : deck}
          </Button>
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button variant="default" size="icon" className="rounded-l-none border-l border-primary-foreground/20" disabled={active.length === 0} aria-label="Choose review size">
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

function VocabStats({ tracked, atRisk, newThisWeek }: { tracked: number; atRisk: number; newThisWeek: number }) {
  const stats = [
    { label: 'Tracked words', value: String(tracked), icon: BookOpenCheck, tone: 'text-primary', bg: 'bg-primary/10' },
    {
      label: 'At risk now',
      value: String(atRisk),
      caption: 'recall under 70%',
      icon: Flame,
      tone: atRisk > 0 ? 'text-warning' : 'text-muted-foreground',
      bg: atRisk > 0 ? 'bg-warning/10' : 'bg-muted',
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
                    {!card.suspended && card.recallProbability < 0.7 && ' 🔥'}
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

function PronunciationDialog({ card, onClose }: { card: VocabCard | null; onClose: () => void }) {
  const recorder = useWavRecorder(30)
  const assessment = useAssessVocabPronunciation()
  const metadata = card ? parseVocabMetadata(card.metadata) : {}

  function submit() {
    if (!card || !recorder.audio) return
    assessment.mutate({ id: card.id, audio: recorder.audio }, {
      onError: (error) => toast.error(error.message),
    })
  }

  return (
    <Dialog open={Boolean(card)} onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="max-h-[85vh] overflow-y-auto sm:max-w-lg">
        {card && (
          <>
            <DialogHeader><DialogTitle>Pronounce “{card.front}”</DialogTitle></DialogHeader>
            <div className="flex flex-col gap-4">
              {metadata.reading && <p className="text-sm text-muted-foreground">{metadata.reading}</p>}
              <AudioRecorder recorder={recorder} maximumSeconds={30} />
              <Button onClick={submit} disabled={!recorder.audio || assessment.isPending}>
                {assessment.isPending ? 'Assessing…' : 'Assess pronunciation'}
              </Button>
              {assessment.data && <PronunciationResultView result={assessment.data} />}
            </div>
          </>
        )}
      </DialogContent>
    </Dialog>
  )
}

function PronunciationResultView({ result }: { result: PronunciationAssessment }) {
  return (
    <div className="flex flex-col gap-3 rounded-lg border bg-muted/20 p-3">
      <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">Azure delivery · unofficial</p>
      <div className="grid grid-cols-2 gap-2">
        <PronunciationScore label="Accuracy" value={result.accuracy} />
        <PronunciationScore label="Fluency" value={result.fluency} />
      </div>
      {result.recognizedText && <p className="text-xs text-muted-foreground">Heard: “{result.recognizedText}”</p>}
      {result.words && result.words.length > 0 && (
        <div className="flex flex-wrap gap-1.5">
          {result.words.map((word, index) => (
            <details key={`${word.word}-${index}`}>
              <summary className={cn('cursor-pointer list-none rounded-md border px-2 py-1 text-xs', pronunciationTone(word.accuracy))}>
                {word.word} {typeof word.accuracy === 'number' ? Math.round(word.accuracy) : '—'}
              </summary>
              {word.phonemes && word.phonemes.length > 0 && (
                <div className="mt-1 rounded-md border bg-popover p-2 text-xs shadow-sm">
                  {word.phonemes.map((phoneme, phonemeIndex) => (
                    <p key={`${phoneme.phoneme}-${phonemeIndex}`} className="tabular-nums">
                      {phoneme.phoneme}: {typeof phoneme.accuracy === 'number' ? Math.round(phoneme.accuracy) : '—'}
                    </p>
                  ))}
                </div>
              )}
            </details>
          ))}
        </div>
      )}
    </div>
  )
}

function PronunciationScore({ label, value }: { label: string; value: number | undefined }) {
  return <div className="rounded-lg bg-card p-3"><p className="text-xs text-muted-foreground">{label}</p><p className="text-xl font-semibold tabular-nums">{typeof value === 'number' ? Math.round(value) : '—'}<span className="text-xs font-normal text-muted-foreground"> / 100</span></p></div>
}

function pronunciationTone(value: number | undefined): string {
  if (value === undefined) return 'text-muted-foreground'
  if (value >= 80) return 'border-success/40 text-success'
  if (value >= 60) return 'border-warning/40 text-warning'
  return 'border-destructive/40 text-destructive'
}
