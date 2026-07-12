import { Link } from '@tanstack/react-router'
import { useMemo, useState, type ReactNode, type SyntheticEvent } from 'react'
import {
  CalendarClock,
  ChevronDown,
  ChevronUp,
  Clock3,
  ExternalLink,
  FileText,
  History,
  Loader2,
  Newspaper,
  Pencil,
  Play,
  Plus,
  Save,
  Trash2,
  Workflow,
} from 'lucide-react'
import { toast } from 'sonner'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Separator } from '@/components/ui/separator'
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetFooter,
  SheetHeader,
  SheetTitle,
} from '@/components/ui/sheet'
import { Switch } from '@/components/ui/switch'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { Textarea } from '@/components/ui/textarea'
import {
  useAutomationRuns,
  useAutomationTypes,
  useAutomations,
  useCreateAutomation,
  useDeleteAutomation,
  useRunAutomationNow,
  useUpdateAutomation,
  type AutomationDefinition,
  type AutomationRun,
  type AutomationType,
} from '@/lib/automation-api'
import type { AutomationTrigger } from '@/lib/hey-api'
import {
  MORNING_BRIEF_TYPE,
  isNewAutomationTarget,
  morningBriefRequest,
  type AutomationEditorTarget,
  type MorningBriefForm,
} from '@/lib/morning-brief-automation'

type Day = AutomationTrigger['daysOfWeek'][number]

const DAYS: Array<{ value: Day; short: string }> = [
  { value: 'MONDAY', short: 'Mon' },
  { value: 'TUESDAY', short: 'Tue' },
  { value: 'WEDNESDAY', short: 'Wed' },
  { value: 'THURSDAY', short: 'Thu' },
  { value: 'FRIDAY', short: 'Fri' },
  { value: 'SATURDAY', short: 'Sat' },
  { value: 'SUNDAY', short: 'Sun' },
]

const DEFAULT_DAYS = DAYS.map((day) => day.value)

const BRIEF_SOURCES = [
  { id: 'github', label: 'GitHub' },
  { id: 'rss', label: 'RSS / Atom' },
  { id: 'hacker-news', label: 'Hacker News' },
  { id: 'bluesky', label: 'Bluesky' },
  { id: 'firecrawl', label: 'Firecrawl' },
] as const

const DEFAULT_BRIEF_SOURCE_IDS = BRIEF_SOURCES.map((source) => source.id)
const DEFAULT_REPOSITORIES = ['openai/codex', 'anthropics/claude-code', 'flutter/flutter', 'dart-lang/sdk', 'spring-projects/spring-ai', 'facebook/react']
const DEFAULT_FEEDS = ['https://openai.com/news/rss.xml', 'https://simonwillison.net/atom/everything/', 'https://github.blog/feed/', 'https://spring.io/blog.atom', 'https://react.dev/rss.xml', 'https://inside.java/feed.xml']
const DEFAULT_BLUESKY_HANDLES = ['bcherny.bsky.social', 'simonwillison.net', 'gergely.pragmaticengineer.com', 'addyosmani.bsky.social']

export function AutomationsSection() {
  const automations = useAutomations()
  const types = useAutomationTypes()
  const create = useCreateAutomation()
  const update = useUpdateAutomation()
  const remove = useDeleteAutomation()
  const runNow = useRunAutomationNow()
  const [editing, setEditing] = useState<AutomationEditorTarget | null>(null)
  const [typePickerOpen, setTypePickerOpen] = useState(false)
  const visibleAutomations = useMemo(
    () => automations.data?.filter((definition) => definition.type !== MORNING_BRIEF_TYPE) ?? [],
    [automations.data],
  )
  const visibleTypes = useMemo(
    () => types.data?.filter((descriptor) => descriptor.type !== MORNING_BRIEF_TYPE) ?? [],
    [types.data],
  )
  const typeById = useMemo(
    () => new Map(types.data?.map((descriptor) => [descriptor.type, descriptor]) ?? []),
    [types.data],
  )

  function toggle(definition: AutomationDefinition, enabled: boolean) {
    update.mutate({
      id: definition.id,
      body: {
        name: definition.name,
        enabled,
        trigger: definition.trigger,
        workflowConfig: definition.workflowConfig,
        version: definition.version,
      },
    }, {
      onSuccess: () => toast.success(enabled ? 'Automation enabled' : 'Automation paused'),
      onError: (error) => toast.error(error.message),
    })
  }

  function execute(definition: AutomationDefinition) {
    runNow.mutate(definition.id, {
      onSuccess: () => toast.success('Automation queued'),
      onError: (error) => toast.error(error.message),
    })
  }

  function deleteDefinition(definition: AutomationDefinition) {
    if (!window.confirm(`Delete "${definition.name}" and its schedule? Run history is retained.`)) return
    remove.mutate({ id: definition.id, version: definition.version }, {
      onSuccess: () => toast.success('Automation deleted'),
      onError: (error) => toast.error(error.message),
    })
  }

  return (
    <div>
      <div className="flex flex-wrap items-start justify-between gap-3 pb-5">
        <div>
          <h2 id="automations-heading" className="text-xl font-semibold">Automations</h2>
          <p className="mt-1 text-sm text-muted-foreground">Scheduled workflows that run without opening Northstar.</p>
        </div>
        {visibleTypes.length > 0 && (
          <Button type="button" onClick={() => setTypePickerOpen(true)}>
            <Plus className="size-4" />
            New automation
          </Button>
        )}
      </div>
      <Separator />

      {automations.isLoading ? (
        <div className="flex h-52 items-center justify-center">
          <Loader2 className="size-5 animate-spin text-muted-foreground" />
        </div>
      ) : automations.isError ? (
        <div className="py-8 text-sm text-destructive">{automations.error.message}</div>
      ) : visibleAutomations.length ? (
        <div className="divide-y">
          {visibleAutomations.map((definition) => (
            <AutomationRow
              key={definition.id}
              definition={definition}
              busy={update.isPending || remove.isPending || runNow.isPending}
              onToggle={toggle}
              onRun={execute}
              onEdit={setEditing}
              onDelete={deleteDefinition}
              typeName={typeById.get(definition.type)?.displayName ?? definition.type}
            />
          ))}
        </div>
      ) : (
        <div className="my-8 flex min-h-48 flex-col items-center justify-center border-y px-4 text-center">
          <CalendarClock className="mb-3 size-7 text-muted-foreground" />
          <p className="text-sm font-medium">No scheduled workflows</p>
          <p className="mt-1 max-w-sm text-xs leading-relaxed text-muted-foreground">
            Create a scheduled workflow to run recurring work in the background.
          </p>
        </div>
      )}

      <AutomationTypePicker
        open={typePickerOpen}
        types={visibleTypes}
        loading={types.isLoading}
        error={types.isError ? types.error.message : null}
        onOpenChange={setTypePickerOpen}
        onSelect={(descriptor) => {
          setTypePickerOpen(false)
          setEditing({ kind: 'new', descriptor })
        }}
      />

      <AutomationEditor
        key={editing && isNewAutomationTarget(editing) ? `new-${editing.descriptor.type}` : editing?.id ?? 'closed'}
        target={editing}
        descriptor={editing && !isNewAutomationTarget(editing) ? typeById.get(editing.type) : undefined}
        open={editing !== null}
        busy={create.isPending || update.isPending}
        onOpenChange={(open) => !open && setEditing(null)}
        onSubmit={(form) => {
          const body = morningBriefRequest(form)
          const creating = editing !== null && isNewAutomationTarget(editing)
          const options = {
            onSuccess: () => {
              toast.success(creating ? 'Automation created' : 'Automation saved')
              setEditing(null)
            },
            onError: (error: Error) => toast.error(error.message),
          }
          if (creating) create.mutate({ ...body, type: editing.descriptor.type }, options)
          else if (editing) update.mutate({
            id: editing.id,
            body: { ...body, version: editing.version },
          }, options)
        }}
      />
    </div>
  )
}

function AutomationRow({
  definition,
  busy,
  onToggle,
  onRun,
  onEdit,
  onDelete,
  typeName,
}: {
  definition: AutomationDefinition
  busy: boolean
  onToggle: (definition: AutomationDefinition, enabled: boolean) => void
  onRun: (definition: AutomationDefinition) => void
  onEdit: (definition: AutomationDefinition) => void
  onDelete: (definition: AutomationDefinition) => void
  typeName: string
}) {
  const [historyOpen, setHistoryOpen] = useState(false)
  const runs = useAutomationRuns(definition.id, historyOpen)

  return (
    <div className="py-5">
      <div className="flex min-w-0 flex-col gap-4 sm:flex-row sm:items-center">
        <div className="flex min-w-0 flex-1 items-start gap-3">
          <div className="grid size-9 shrink-0 place-items-center rounded-md bg-muted">
            <FileText className="size-4 text-muted-foreground" />
          </div>
          <div className="min-w-0">
            <div className="flex flex-wrap items-center gap-2">
              <p className="truncate text-sm font-semibold">{definition.name}</p>
              <Badge variant="outline">{typeName}</Badge>
              {!definition.scheduleSynced && <Badge variant="outline">Syncing</Badge>}
              <Badge variant={definition.enabled ? 'secondary' : 'outline'}>
                {definition.enabled ? 'Active' : 'Paused'}
              </Badge>
            </div>
            <p className="mt-1 flex items-center gap-1.5 text-xs text-muted-foreground">
              <Clock3 className="size-3.5" />
              {scheduleLabel(definition.trigger)}
            </p>
          </div>
        </div>

        <div className="flex flex-wrap items-center gap-1 sm:justify-end">
          <Switch
            checked={definition.enabled}
            disabled={busy}
            onCheckedChange={(enabled) => onToggle(definition, enabled)}
            aria-label={`${definition.enabled ? 'Pause' : 'Enable'} ${definition.name}`}
          />
          <Button type="button" variant="ghost" size="icon" title="Run now" disabled={busy} onClick={() => onRun(definition)}>
            <Play className="size-4" />
            <span className="sr-only">Run now</span>
          </Button>
          <Button type="button" variant="ghost" size="icon" title="Edit" disabled={busy || !hasEditor(definition.type)} onClick={() => onEdit(definition)}>
            <Pencil className="size-4" />
            <span className="sr-only">Edit</span>
          </Button>
          <Button type="button" variant="ghost" size="icon" title="Delete" disabled={busy} onClick={() => onDelete(definition)}>
            <Trash2 className="size-4" />
            <span className="sr-only">Delete</span>
          </Button>
          <Button type="button" variant="ghost" size="sm" onClick={() => setHistoryOpen((value) => !value)}>
            <History className="size-4" />
            History
            {historyOpen ? <ChevronUp className="size-3.5" /> : <ChevronDown className="size-3.5" />}
          </Button>
        </div>
      </div>

      {historyOpen && (
        <div className="ml-0 mt-4 border-l pl-4 sm:ml-12">
          {runs.isLoading ? (
            <Loader2 className="size-4 animate-spin text-muted-foreground" />
          ) : runs.isError ? (
            <p className="text-xs text-destructive">{runs.error.message}</p>
          ) : runs.data?.length ? (
            <div className="space-y-2">
              {runs.data.map((run) => <RunRow key={run.id} run={run} />)}
            </div>
          ) : (
            <p className="text-xs text-muted-foreground">No runs yet.</p>
          )}
        </div>
      )}
    </div>
  )
}

function AutomationTypePicker({
  open,
  types,
  loading,
  error,
  onOpenChange,
  onSelect,
}: {
  open: boolean
  types: AutomationType[]
  loading: boolean
  error: string | null
  onOpenChange: (open: boolean) => void
  onSelect: (descriptor: AutomationType) => void
}) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>New automation</DialogTitle>
          <DialogDescription>Choose a workflow type.</DialogDescription>
        </DialogHeader>
        {loading ? (
          <div className="grid h-32 place-items-center">
            <Loader2 className="size-5 animate-spin text-muted-foreground" />
          </div>
        ) : error ? (
          <p className="py-8 text-sm text-destructive">{error}</p>
        ) : types.length ? (
          <div className="grid gap-2">
            {types.map((descriptor) => {
              const available = hasEditor(descriptor.type)
              const Icon = descriptor.type === MORNING_BRIEF_TYPE ? Newspaper : Workflow
              return (
                <button
                  key={descriptor.type}
                  type="button"
                  disabled={!available}
                  className="flex min-h-20 w-full items-center gap-4 rounded-md border p-4 text-left transition-colors hover:bg-muted/60 disabled:cursor-not-allowed disabled:opacity-50"
                  onClick={() => onSelect(descriptor)}
                >
                  <span className="grid size-10 shrink-0 place-items-center rounded-md bg-muted">
                    <Icon className="size-5 text-muted-foreground" />
                  </span>
                  <span className="min-w-0 flex-1">
                    <span className="flex flex-wrap items-center gap-2">
                      <span className="font-medium">{descriptor.displayName}</span>
                    </span>
                    <span className="mt-1 block text-sm leading-relaxed text-muted-foreground">{descriptor.description}</span>
                  </span>
                </button>
              )
            })}
          </div>
        ) : (
          <p className="py-8 text-center text-sm text-muted-foreground">No automation types are available.</p>
        )}
      </DialogContent>
    </Dialog>
  )
}

function RunRow({ run }: { run: AutomationRun }) {
  return (
    <div className="flex flex-wrap items-center justify-between gap-2 text-xs">
      <span className="text-muted-foreground">{new Date(run.scheduledFor).toLocaleString()}</span>
      <div className="flex items-center gap-2">
        <span className="text-muted-foreground">{run.runKind === 'MANUAL' ? 'Manual' : 'Scheduled'}</span>
        <Badge variant={run.status === 'FAILED' ? 'destructive' : 'outline'}>{statusLabel(run.status)}</Badge>
        {run.outputType === 'NOTE' && run.outputId && (
          <Button asChild variant="ghost" size="xs">
            <Link to="/briefs">
              Open
              <ExternalLink className="size-3" />
            </Link>
          </Button>
        )}
      </div>
    </div>
  )
}

export function AutomationEditor({
  target,
  descriptor,
  open,
  busy,
  onOpenChange,
  onSubmit,
}: {
  target: AutomationEditorTarget | null
  descriptor?: AutomationType
  open: boolean
  busy: boolean
  onOpenChange: (open: boolean) => void
  onSubmit: (form: MorningBriefForm) => void
}) {
  const initial = useMemo(() => formFrom(target), [target])
  const [form, setForm] = useState(initial)
  const [tab, setTab] = useState('schedule')
  const creating = target !== null && isNewAutomationTarget(target)
  const type = creating ? target.descriptor : descriptor

  function submit(event: SyntheticEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!form.name.trim()) {
      setTab('schedule')
      return toast.error('Name is required')
    }
    if (!form.time || !form.timezone.trim() || !form.days.length) {
      setTab('schedule')
      return toast.error('Complete the schedule')
    }
    if (!form.sourceIds.length) {
      setTab('sources')
      return toast.error('Enable at least one source')
    }
    onSubmit(form)
  }

  function toggleSource(sourceId: string, enabled: boolean) {
    setForm({
      ...form,
      sourceIds: enabled
        ? [...form.sourceIds, sourceId]
        : form.sourceIds.filter((value) => value !== sourceId),
    })
  }

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent
        className="w-full gap-0 p-0 sm:max-w-3xl"
        onOpenAutoFocus={(event) => event.preventDefault()}
      >
        <SheetHeader className="border-b px-5 py-4 sm:px-6">
          <div className="flex items-center gap-3 pr-8">
            <span className="grid size-9 shrink-0 place-items-center rounded-md bg-muted">
              <Newspaper className="size-4 text-muted-foreground" />
            </span>
            <div className="min-w-0">
              <div className="flex items-center gap-2">
                <SheetTitle>{creating ? `New ${type?.displayName ?? 'automation'}` : `Edit ${type?.displayName ?? 'automation'}`}</SheetTitle>
              </div>
              <SheetDescription>{type?.description ?? 'Configure this scheduled workflow.'}</SheetDescription>
            </div>
          </div>
        </SheetHeader>

        <Tabs value={tab} onValueChange={setTab} className="min-h-0 flex-1 gap-0">
          <div className="border-b px-5 sm:px-6">
            <TabsList variant="line" className="h-11 w-full justify-start">
              <TabsTrigger value="schedule">Schedule</TabsTrigger>
              <TabsTrigger value="content">Content</TabsTrigger>
              <TabsTrigger value="sources">Sources</TabsTrigger>
            </TabsList>
          </div>

          <form id="automation-form" className="min-h-0 flex-1 overflow-y-auto px-5 py-5 sm:px-6" onSubmit={submit}>
            <TabsContent value="schedule" className="space-y-6">
              <div className="grid gap-4 sm:grid-cols-[minmax(0,1fr)_auto] sm:items-end">
                <Field label="Name" htmlFor="automation-name">
                  <Input id="automation-name" value={form.name} maxLength={160} onChange={(event) => setForm({ ...form, name: event.target.value })} />
                </Field>
                <div className="flex h-9 items-center gap-2">
                  <Switch checked={form.enabled} onCheckedChange={(enabled) => setForm({ ...form, enabled })} id="automation-enabled" />
                  <Label htmlFor="automation-enabled">Enabled</Label>
                </div>
              </div>

              <div className="grid gap-4 sm:grid-cols-2">
                <Field label="Time" htmlFor="automation-time">
                  <Input id="automation-time" type="time" value={form.time} onChange={(event) => setForm({ ...form, time: event.target.value })} />
                </Field>
                <Field label="Timezone" htmlFor="automation-timezone">
                  <Input id="automation-timezone" value={form.timezone} onChange={(event) => setForm({ ...form, timezone: event.target.value })} />
                </Field>
              </div>

              <div>
                <Label>Days</Label>
                <div className="mt-2 grid grid-cols-4 gap-1 sm:grid-cols-7">
                  {DAYS.map((day) => {
                    const selected = form.days.includes(day.value)
                    return (
                      <Button key={day.value} type="button" size="sm" variant={selected ? 'secondary' : 'outline'} aria-pressed={selected} onClick={() => setForm({
                        ...form,
                        days: selected ? form.days.filter((value) => value !== day.value) : [...form.days, day.value],
                      })}>
                        {day.short}
                      </Button>
                    )
                  })}
                </div>
              </div>

              <div className="flex items-center justify-between gap-4 border-y py-4">
                <div>
                  <Label htmlFor="automation-save-note">Save as note</Label>
                  <p className="mt-1 text-xs text-muted-foreground">Store each completed brief in the review queue.</p>
                </div>
                <Switch id="automation-save-note" checked={form.saveAsNote} onCheckedChange={(saveAsNote) => setForm({ ...form, saveAsNote })} />
              </div>
            </TabsContent>

            <TabsContent value="content" className="space-y-6">
              <Field label="Topics" htmlFor="automation-topics" hint="One topic per line.">
                <Textarea id="automation-topics" rows={10} value={form.topics} onChange={(event) => setForm({ ...form, topics: event.target.value })} placeholder={'AI agents\nJava\nSpring AI'} />
              </Field>
              <div className="grid gap-4 sm:grid-cols-3">
                <Field label="Language" htmlFor="automation-language">
                  <Select value={form.language} onValueChange={(language) => setForm({ ...form, language })}>
                    <SelectTrigger id="automation-language"><SelectValue /></SelectTrigger>
                    <SelectContent><SelectItem value="vi">Vietnamese</SelectItem><SelectItem value="en">English</SelectItem></SelectContent>
                  </Select>
                </Field>
                <Field label="Lookback" htmlFor="automation-lookback">
                  <Select value={String(form.lookbackHours)} onValueChange={(value) => setForm({ ...form, lookbackHours: Number(value) })}>
                    <SelectTrigger id="automation-lookback"><SelectValue /></SelectTrigger>
                    <SelectContent><SelectItem value="24">Past 24 hours</SelectItem><SelectItem value="72">Past 3 days</SelectItem><SelectItem value="168">Past week</SelectItem></SelectContent>
                  </Select>
                </Field>
                <Field label="Maximum items" htmlFor="automation-max-items">
                  <Input id="automation-max-items" type="number" min={1} max={20} value={form.maxItems} onChange={(event) => setForm({ ...form, maxItems: Number(event.target.value) })} />
                </Field>
              </div>
            </TabsContent>

            <TabsContent value="sources" className="space-y-3">
              <SourcePanel label="GitHub releases" enabled={form.sourceIds.includes('github')} onEnabledChange={(enabled) => toggleSource('github', enabled)}>
                <Field label="Repositories" htmlFor="automation-repositories" hint="owner/repository, one per line">
                  <Textarea id="automation-repositories" rows={6} value={form.githubRepositories} onChange={(event) => setForm({ ...form, githubRepositories: event.target.value })} />
                </Field>
              </SourcePanel>
              <SourcePanel label="RSS / Atom" enabled={form.sourceIds.includes('rss')} onEnabledChange={(enabled) => toggleSource('rss', enabled)}>
                <Field label="Feed URLs" htmlFor="automation-feeds" hint="Public HTTPS URL, one per line">
                  <Textarea id="automation-feeds" rows={7} value={form.feedUrls} onChange={(event) => setForm({ ...form, feedUrls: event.target.value })} />
                </Field>
              </SourcePanel>
              <SourcePanel label="Hacker News" enabled={form.sourceIds.includes('hacker-news')} onEnabledChange={(enabled) => toggleSource('hacker-news', enabled)} />
              <SourcePanel label="Bluesky people" enabled={form.sourceIds.includes('bluesky')} onEnabledChange={(enabled) => toggleSource('bluesky', enabled)}>
                <Field label="Handles" htmlFor="automation-bluesky" hint="Verified handle, one per line">
                  <Textarea id="automation-bluesky" rows={5} value={form.blueskyHandles} onChange={(event) => setForm({ ...form, blueskyHandles: event.target.value })} />
                </Field>
              </SourcePanel>
              <SourcePanel label="Firecrawl fallback" enabled={form.sourceIds.includes('firecrawl')} onEnabledChange={(enabled) => toggleSource('firecrawl', enabled)}>
                <div className="grid gap-4 sm:grid-cols-[minmax(0,1fr)_12rem]">
                  <Field label="Exact queries" htmlFor="automation-queries" hint="Leave empty to generate queries from topics.">
                    <Textarea id="automation-queries" rows={5} value={form.queries} onChange={(event) => setForm({ ...form, queries: event.target.value })} />
                  </Field>
                  <Field label="Credit estimate" htmlFor="automation-firecrawl-budget" hint="5–50 per run">
                    <Input id="automation-firecrawl-budget" type="number" min={5} max={50} value={form.firecrawlCreditBudget} onChange={(event) => setForm({ ...form, firecrawlCreditBudget: Number(event.target.value) })} />
                  </Field>
                </div>
              </SourcePanel>
              <div className="pt-3">
                <Field label="Blocked domains" htmlFor="automation-blocked" hint="Optional, one domain per line.">
                  <Textarea id="automation-blocked" rows={3} value={form.blockedDomains} onChange={(event) => setForm({ ...form, blockedDomains: event.target.value })} placeholder={'example.com\nspam.example'} />
                </Field>
              </div>
            </TabsContent>
          </form>
        </Tabs>

        <SheetFooter className="border-t px-5 py-4 sm:flex-row sm:justify-end sm:px-6">
          <Button type="button" variant="outline" onClick={() => onOpenChange(false)} disabled={busy}>Cancel</Button>
          <Button type="submit" form="automation-form" disabled={busy}>
            {busy ? <Loader2 className="size-4 animate-spin" /> : <Save className="size-4" />}
            Save automation
          </Button>
        </SheetFooter>
      </SheetContent>
    </Sheet>
  )
}

function SourcePanel({
  label,
  enabled,
  onEnabledChange,
  children,
}: {
  label: string
  enabled: boolean
  onEnabledChange: (enabled: boolean) => void
  children?: ReactNode
}) {
  const id = `source-${label.toLowerCase().replace(/[^a-z0-9]+/g, '-')}`
  return (
    <section className="rounded-md border">
      <div className="flex min-h-14 items-center justify-between gap-4 px-4 py-3">
        <Label htmlFor={id} className="font-medium">{label}</Label>
        <Switch id={id} checked={enabled} onCheckedChange={onEnabledChange} />
      </div>
      {enabled && children && <div className="border-t px-4 py-4">{children}</div>}
    </section>
  )
}

function Field({ label, htmlFor, hint, children }: { label: string; htmlFor: string; hint?: string; children: ReactNode }) {
  return (
    <div className="min-w-0 space-y-2">
      <Label htmlFor={htmlFor}>{label}</Label>
      {children}
      {hint && <p className="text-xs leading-relaxed text-muted-foreground">{hint}</p>}
    </div>
  )
}

function formFrom(target: AutomationEditorTarget | null): MorningBriefForm {
  if (!target || isNewAutomationTarget(target)) {
    const config = target?.descriptor.defaultConfig ?? {}
    return {
      name: target?.descriptor.displayName ?? 'Morning Brief',
      enabled: true,
      time: '07:00',
      timezone: Intl.DateTimeFormat().resolvedOptions().timeZone || 'Asia/Bangkok',
      days: DEFAULT_DAYS,
      language: stringValue(config.language, 'vi'),
      lookbackHours: numberValue(config.lookbackHours, 24),
      maxItems: numberValue(config.maxItems, 6),
      topics: stringList(config.topics).join('\n'),
      queries: stringList(config.queries).join('\n'),
      blockedDomains: stringList(config.blockedDomains).join('\n'),
      saveAsNote: typeof config.saveAsNote === 'boolean' ? config.saveAsNote : true,
      sourceIds: stringListOr(config.sourceIds, DEFAULT_BRIEF_SOURCE_IDS),
      githubRepositories: stringListOr(config.githubRepositories, DEFAULT_REPOSITORIES).join('\n'),
      feedUrls: stringListOr(config.feedUrls, DEFAULT_FEEDS).join('\n'),
      blueskyHandles: stringListOr(config.blueskyHandles, DEFAULT_BLUESKY_HANDLES).join('\n'),
      firecrawlCreditBudget: numberValue(config.firecrawlCreditBudget, 25),
    }
  }
  const config = target.workflowConfig
  return {
    name: target.name,
    enabled: target.enabled,
    time: target.trigger.localTime.slice(0, 5),
    timezone: target.trigger.timezone,
    days: target.trigger.daysOfWeek,
    language: stringValue(config.language, 'vi'),
    lookbackHours: numberValue(config.lookbackHours, 24),
    maxItems: numberValue(config.maxItems, 6),
    topics: stringList(config.topics).join('\n'),
    queries: stringList(config.queries).join('\n'),
    blockedDomains: stringList(config.blockedDomains).join('\n'),
    saveAsNote: typeof config.saveAsNote === 'boolean' ? config.saveAsNote : true,
    sourceIds: stringListOr(config.sourceIds, DEFAULT_BRIEF_SOURCE_IDS),
    githubRepositories: stringListOr(config.githubRepositories, DEFAULT_REPOSITORIES).join('\n'),
    feedUrls: stringListOr(config.feedUrls, DEFAULT_FEEDS).join('\n'),
    blueskyHandles: stringListOr(config.blueskyHandles, DEFAULT_BLUESKY_HANDLES).join('\n'),
    firecrawlCreditBudget: numberValue(config.firecrawlCreditBudget, 25),
  }
}

function hasEditor(type: string) {
  return type === MORNING_BRIEF_TYPE
}

function stringList(value: unknown): string[] {
  return Array.isArray(value) ? value.filter((item): item is string => typeof item === 'string') : []
}

function stringListOr(value: unknown, fallback: readonly string[]): string[] {
  return value === undefined || value === null ? [...fallback] : stringList(value)
}

function stringValue(value: unknown, fallback: string) {
  return typeof value === 'string' ? value : fallback
}

function numberValue(value: unknown, fallback: number) {
  return typeof value === 'number' ? value : fallback
}

function scheduleLabel(trigger: AutomationTrigger) {
  const allDays = trigger.daysOfWeek.length === 7
  const weekdays = trigger.daysOfWeek.length === 5 && !trigger.daysOfWeek.includes('SATURDAY') && !trigger.daysOfWeek.includes('SUNDAY')
  const days = allDays ? 'Every day' : weekdays ? 'Weekdays' : trigger.daysOfWeek.map((day) => day.slice(0, 3).toLowerCase()).join(', ')
  return `${days} at ${trigger.localTime.slice(0, 5)} · ${trigger.timezone}`
}

function statusLabel(status: AutomationRun['status']) {
  return status.charAt(0) + status.slice(1).toLowerCase()
}
