import { useMemo, useState, type ReactNode, type SyntheticEvent } from 'react'
import {
  CalendarClock,
  ChevronDown,
  ChevronUp,
  Clock3,
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
  DialogFooter,
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
import { Switch } from '@/components/ui/switch'
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

type Day = AutomationTrigger['daysOfWeek'][number]

interface MorningBriefForm {
  name: string
  enabled: boolean
  time: string
  timezone: string
  days: Day[]
  language: string
  lookbackHours: number
  maxItems: number
  topics: string
  queries: string
  blockedDomains: string
  saveAsNote: boolean
}

interface NewAutomationTarget {
  kind: 'new'
  descriptor: AutomationType
}

type AutomationEditorTarget = AutomationDefinition | NewAutomationTarget

const MORNING_BRIEF_TYPE = 'morning-brief.v1'

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

export function AutomationsSection() {
  const automations = useAutomations()
  const types = useAutomationTypes()
  const create = useCreateAutomation()
  const update = useUpdateAutomation()
  const remove = useDeleteAutomation()
  const runNow = useRunAutomationNow()
  const [editing, setEditing] = useState<AutomationEditorTarget | null>(null)
  const [typePickerOpen, setTypePickerOpen] = useState(false)
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
        <Button type="button" onClick={() => setTypePickerOpen(true)}>
          <Plus className="size-4" />
          New automation
        </Button>
      </div>
      <Separator />

      {automations.isLoading ? (
        <div className="flex h-52 items-center justify-center">
          <Loader2 className="size-5 animate-spin text-muted-foreground" />
        </div>
      ) : automations.isError ? (
        <div className="py-8 text-sm text-destructive">{automations.error.message}</div>
      ) : automations.data?.length ? (
        <div className="divide-y">
          {automations.data.map((definition) => (
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
        types={types.data ?? []}
        loading={types.isLoading}
        error={types.isError ? types.error.message : null}
        onOpenChange={setTypePickerOpen}
        onSelect={(descriptor) => {
          setTypePickerOpen(false)
          setEditing({ kind: 'new', descriptor })
        }}
      />

      <AutomationEditor
        key={editing && isNewTarget(editing) ? `new-${editing.descriptor.type}` : editing?.id ?? 'closed'}
        target={editing}
        descriptor={editing && !isNewTarget(editing) ? typeById.get(editing.type) : undefined}
        open={editing !== null}
        busy={create.isPending || update.isPending}
        onOpenChange={(open) => !open && setEditing(null)}
        onSubmit={(form) => {
          const body = request(form)
          const creating = editing !== null && isNewTarget(editing)
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
                      <Badge variant="outline">v{descriptor.configVersion}</Badge>
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
      </div>
    </div>
  )
}

function AutomationEditor({
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
  const creating = target !== null && isNewTarget(target)
  const type = creating ? target.descriptor : descriptor

  function submit(event: SyntheticEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!form.name.trim()) return toast.error('Name is required')
    if (!form.time) return toast.error('Schedule time is required')
    if (!form.timezone.trim()) return toast.error('Timezone is required')
    if (!form.days.length) return toast.error('Choose at least one day')
    if (!lines(form.topics).length && !lines(form.queries).length) {
      return toast.error('Add at least one topic or search query')
    }
    onSubmit(form)
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent
        className="max-h-[calc(100dvh-2rem)] overflow-y-auto sm:max-w-2xl"
        onOpenAutoFocus={(event) => event.preventDefault()}
      >
        <DialogHeader>
          <DialogTitle>{creating ? `New ${type?.displayName ?? 'automation'}` : `Edit ${type?.displayName ?? 'automation'}`}</DialogTitle>
          <DialogDescription>{type?.description ?? 'Configure this scheduled workflow.'}</DialogDescription>
        </DialogHeader>

        <form id="automation-form" className="space-y-6" onSubmit={submit}>
          <div className="flex items-center gap-3 rounded-md border px-3 py-2.5">
            <Newspaper className="size-4 text-muted-foreground" />
            <div className="min-w-0 flex-1">
              <p className="text-xs text-muted-foreground">Type</p>
              <p className="truncate text-sm font-medium">{type?.displayName ?? MORNING_BRIEF_TYPE}</p>
            </div>
            {type && <Badge variant="outline">v{type.configVersion}</Badge>}
          </div>
          <div className="grid gap-4 sm:grid-cols-[minmax(0,1fr)_auto] sm:items-end">
            <Field label="Name" htmlFor="automation-name">
              <Input
                id="automation-name"
                value={form.name}
                maxLength={160}
                onChange={(event) => setForm({ ...form, name: event.target.value })}
              />
            </Field>
            <div className="flex h-9 items-center gap-2">
              <Switch checked={form.enabled} onCheckedChange={(enabled) => setForm({ ...form, enabled })} id="automation-enabled" />
              <Label htmlFor="automation-enabled">Enabled</Label>
            </div>
          </div>

          <div>
            <p className="mb-3 text-sm font-medium">Schedule</p>
            <div className="grid gap-4 sm:grid-cols-2">
              <Field label="Time" htmlFor="automation-time">
                <Input id="automation-time" type="time" value={form.time} onChange={(event) => setForm({ ...form, time: event.target.value })} />
              </Field>
              <Field label="Timezone" htmlFor="automation-timezone">
                <Input id="automation-timezone" value={form.timezone} onChange={(event) => setForm({ ...form, timezone: event.target.value })} />
              </Field>
            </div>
            <div className="mt-4">
              <Label>Days</Label>
              <div className="mt-2 grid grid-cols-4 gap-1 sm:grid-cols-7">
                {DAYS.map((day) => {
                  const selected = form.days.includes(day.value)
                  return (
                    <Button
                      key={day.value}
                      type="button"
                      size="sm"
                      variant={selected ? 'secondary' : 'outline'}
                      aria-pressed={selected}
                      onClick={() => setForm({
                        ...form,
                        days: selected ? form.days.filter((value) => value !== day.value) : [...form.days, day.value],
                      })}
                    >
                      {day.short}
                    </Button>
                  )
                })}
              </div>
            </div>
          </div>

          <Separator />
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="Topics" htmlFor="automation-topics" hint="One per line. Used to generate search queries.">
              <Textarea id="automation-topics" rows={5} value={form.topics} onChange={(event) => setForm({ ...form, topics: event.target.value })} placeholder={'AI agents\nJava\nSpring AI'} />
            </Field>
            <Field label="Exact search queries" htmlFor="automation-queries" hint="Optional. When present, these replace topic-generated queries.">
              <Textarea id="automation-queries" rows={5} value={form.queries} onChange={(event) => setForm({ ...form, queries: event.target.value })} placeholder="Latest Java platform announcements" />
            </Field>
          </div>

          <div className="grid gap-4 sm:grid-cols-3">
            <Field label="Language" htmlFor="automation-language">
              <Select value={form.language} onValueChange={(language) => setForm({ ...form, language })}>
                <SelectTrigger id="automation-language"><SelectValue /></SelectTrigger>
                <SelectContent>
                  <SelectItem value="vi">Vietnamese</SelectItem>
                  <SelectItem value="en">English</SelectItem>
                </SelectContent>
              </Select>
            </Field>
            <Field label="Lookback" htmlFor="automation-lookback">
              <Select value={String(form.lookbackHours)} onValueChange={(value) => setForm({ ...form, lookbackHours: Number(value) })}>
                <SelectTrigger id="automation-lookback"><SelectValue /></SelectTrigger>
                <SelectContent>
                  <SelectItem value="24">Past 24 hours</SelectItem>
                  <SelectItem value="72">Past 3 days</SelectItem>
                  <SelectItem value="168">Past week</SelectItem>
                </SelectContent>
              </Select>
            </Field>
            <Field label="Maximum sources" htmlFor="automation-max-items">
              <Input id="automation-max-items" type="number" min={1} max={10} value={form.maxItems} onChange={(event) => setForm({ ...form, maxItems: Number(event.target.value) })} />
            </Field>
          </div>

          <Field label="Blocked domains" htmlFor="automation-blocked" hint="Optional, one domain per line.">
            <Textarea id="automation-blocked" rows={2} value={form.blockedDomains} onChange={(event) => setForm({ ...form, blockedDomains: event.target.value })} placeholder={'example.com\nspam.example'} />
          </Field>

          <div className="flex items-center justify-between gap-4 border-y py-4">
            <div>
              <Label htmlFor="automation-save-note">Save as note</Label>
              <p className="mt-1 text-xs text-muted-foreground">Adds each brief to Briefs in Staging for review.</p>
            </div>
            <Switch id="automation-save-note" checked={form.saveAsNote} onCheckedChange={(saveAsNote) => setForm({ ...form, saveAsNote })} />
          </div>
        </form>

        <DialogFooter>
          <Button type="button" variant="outline" onClick={() => onOpenChange(false)} disabled={busy}>Cancel</Button>
          <Button type="submit" form="automation-form" disabled={busy}>
            {busy ? <Loader2 className="size-4 animate-spin" /> : <Save className="size-4" />}
            Save automation
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
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
  if (!target || isNewTarget(target)) {
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
  }
}

function request(form: MorningBriefForm) {
  return {
    name: form.name.trim(),
    enabled: form.enabled,
    trigger: {
      kind: 'DAILY' as const,
      localTime: form.time,
      daysOfWeek: form.days,
      timezone: form.timezone.trim(),
      catchUpWindowMinutes: 240,
    },
    workflowConfig: {
      language: form.language,
      lookbackHours: form.lookbackHours,
      maxItems: form.maxItems,
      topics: lines(form.topics),
      queries: lines(form.queries),
      blockedDomains: lines(form.blockedDomains),
      saveAsNote: form.saveAsNote,
    },
  }
}

function isNewTarget(target: AutomationEditorTarget): target is NewAutomationTarget {
  return 'kind' in target && target.kind === 'new'
}

function hasEditor(type: string) {
  return type === MORNING_BRIEF_TYPE
}

function lines(value: string) {
  return [...new Set(value.split(/\r?\n/).map((item) => item.trim()).filter(Boolean))]
}

function stringList(value: unknown): string[] {
  return Array.isArray(value) ? value.filter((item): item is string => typeof item === 'string') : []
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
