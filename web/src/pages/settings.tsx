import { useEffect, useMemo, useState, type ReactNode } from 'react'
import { BrainCircuit, CheckCircle2, Clock3, Eye, EyeOff, Globe2, Loader2, Pencil, Plus, PlugZap, RotateCcw, Save, Search, Trash2, Waypoints } from 'lucide-react'
import { toast } from 'sonner'
import { AutomationsSection } from '@/components/settings/automations-section'
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
  useResetWebResearchSettings,
  useUpdateWebResearchSettings,
  useWebResearchProviders,
  useWebResearchSettings,
  type WebProviderRoute,
  type WebResearchSettingsInput,
} from '@/lib/web-research-api'
import { cn } from '@/lib/utils'
import {
  AI_TASKS,
  useAiModels,
  useAiSettings,
  useDeleteAiGateway,
  useResetAiRoute,
  useSaveAiGateway,
  useTestAiGateway,
  useTestAiGatewayDraft,
  useUpdateAiRoute,
  type AiGateway,
  type AiGatewayCapability,
  type AiGatewayConnectionTest,
  type AiGatewayInput,
  type AiGatewayType,
  type AiRouteSelection,
  type AiTask,
} from '@/lib/ai-settings-api'

const SECTIONS = [
  { id: 'ai', label: 'AI models', icon: BrainCircuit },
  { id: 'web-research', label: 'Web research', icon: Globe2 },
  { id: 'automations', label: 'Automations', icon: Clock3 },
] as const

type SectionId = (typeof SECTIONS)[number]['id']

export function SettingsPage() {
  const [section, setSection] = useState<SectionId>('ai')

  return (
    <main className="w-full min-w-0 flex-1 overflow-auto px-4 py-6 md:px-10 md:py-8">
      <div className="mx-auto w-full max-w-5xl">
        <header className="mb-7">
          <h1 className="text-3xl font-bold">Settings</h1>
          <p className="mt-1 text-sm text-muted-foreground">Northstar preferences and integrations.</p>
        </header>

        <div className="grid min-w-0 gap-6 md:grid-cols-[13rem_minmax(0,1fr)] md:gap-10">
          <nav aria-label="Settings sections" className="min-w-0">
            <div className="flex gap-1 overflow-x-auto pb-1 [scrollbar-width:none] [&::-webkit-scrollbar]:hidden md:block md:space-y-1 md:overflow-visible md:pb-0">
              {SECTIONS.map((item) => (
                <Button
                  key={item.id}
                  type="button"
                  variant={section === item.id ? 'secondary' : 'ghost'}
                  className="h-9 min-w-max justify-start md:w-full"
                  aria-current={section === item.id ? 'page' : undefined}
                  onClick={() => setSection(item.id)}
                >
                  <item.icon className="size-4" />
                  {item.label}
                </Button>
              ))}
            </div>
          </nav>

          <section aria-labelledby={`${section}-heading`} className="min-w-0">
            {section === 'ai'
              ? <AiSettingsSection />
              : section === 'web-research'
                ? <WebResearchLoader onManageGateways={() => setSection('ai')} />
                : <AutomationsSection />}
          </section>
        </div>
      </div>
    </main>
  )
}

const AI_TASK_LABELS: Record<AiTask, { title: string; description: string }> = {
  ASSISTANT: { title: 'Assistant', description: 'Interactive chat, tools, and vision.' },
  CAPTURE: { title: 'Capture', description: 'Classify text, SMS, voice, and receipts.' },
  ALIGNMENT: { title: 'Reviews', description: 'Daily and weekly alignment commentary.' },
  TITLE: { title: 'Chat titles', description: 'Short background conversation titles.' },
  STUDY_GRADER: { title: 'Study grader', description: 'Writing grading and faithfulness checks.' },
  IMAGE_CAPTION: { title: 'Image indexing', description: 'Describe images before search indexing.' },
}

function AiSettingsSection() {
  const settings = useAiSettings()
  const [editingGateway, setEditingGateway] = useState<AiGateway | null | undefined>(undefined)
  if (settings.isLoading) {
    return <div className="flex h-52 items-center justify-center border-y"><Loader2 className="size-5 animate-spin text-muted-foreground" /></div>
  }
  if (settings.isError || !settings.data) {
    return <div className="border-y py-8 text-sm text-destructive">{settings.error?.message ?? 'Could not load AI settings.'}</div>
  }
  const configured = settings.data.gateways.filter((gateway) => gateway.configured)
  return (
    <div>
      <div className="pb-5">
        <h2 id="ai-heading" className="text-xl font-semibold">AI models</h2>
        <p className="mt-1 text-sm text-muted-foreground">
          Route each workload through a configured AI gateway.
        </p>
      </div>
      <Separator />
      <div className="divide-y">
        {AI_TASKS.map((task) => (
          <AiRouteRow
            key={task}
            task={task}
            selection={settings.data.routes[task]}
            gateways={configured}
          />
        ))}
      </div>
      <Separator />
      <div className="py-6">
        <div className="mb-3 flex items-center justify-between gap-3">
          <div className="flex items-center gap-2 text-sm font-medium">
            <Waypoints className="size-4 text-muted-foreground" />
            Gateways
          </div>
          <Button type="button" size="sm" variant="outline" onClick={() => setEditingGateway(null)}>
            <Plus className="size-4" />
            Add gateway
          </Button>
        </div>
        <div className="divide-y rounded-md border">
          {settings.data.gateways.map((gateway) => (
            <GatewayRow key={gateway.id} gateway={gateway} onEdit={() => setEditingGateway(gateway)} />
          ))}
        </div>
        <p className="mt-3 text-xs leading-relaxed text-muted-foreground">
          Deployment gateways come from server configuration. Gateways added here are encrypted and available immediately without a restart.
        </p>
      </div>
      {editingGateway !== undefined && (
        <GatewayDialog
          gateway={editingGateway}
          onClose={() => setEditingGateway(undefined)}
        />
      )}
    </div>
  )
}

function GatewayRow({ gateway, onEdit }: { gateway: AiGateway; onEdit: () => void }) {
  const test = useTestAiGateway()
  const remove = useDeleteAiGateway()

  const testConnection = () => test.mutate(gateway.id, {
    onSuccess: (result) => result.success
      ? toast.success(`${gateway.displayName} connected in ${result.latencyMillis} ms`)
      : toast.error(result.message),
    onError: (error) => toast.error(error.message),
  })

  const deleteGateway = () => {
    if (!window.confirm(`Delete ${gateway.displayName}? Routes using it will return to their app defaults.`)) return
    remove.mutate(gateway.id, {
      onSuccess: () => toast.success(`${gateway.displayName} deleted`),
      onError: (error) => toast.error(error.message),
    })
  }

  return (
    <div className="flex min-w-0 items-center gap-3 px-3 py-3">
      <div className="min-w-0 flex-1">
        <div className="flex flex-wrap items-center gap-2">
          <p className="truncate text-sm font-medium">{gateway.displayName}</p>
          <Badge variant="outline" className="text-[10px]">
            {gateway.source === 'DEPLOYMENT' ? 'Server' : 'Runtime'}
          </Badge>
        </div>
        <p className="mt-0.5 truncate text-xs text-muted-foreground">{gateway.baseUrl}</p>
        <p className="mt-1 text-xs text-muted-foreground">
          {gatewayTypeLabel(gateway.type)} · {gateway.capabilities.map(gatewayCapabilityLabel).join(' · ')}
        </p>
      </div>
      <Badge variant="outline" className={cn('hidden shrink-0 sm:inline-flex', gateway.configured && 'text-emerald-600 dark:text-emerald-400')}>
        {gateway.configured ? 'Ready' : 'Not configured'}
      </Badge>
      <div className="flex shrink-0 items-center gap-1">
        <Button
          type="button"
          size="icon"
          variant="ghost"
          title={`Test ${gateway.displayName}`}
          aria-label={`Test ${gateway.displayName}`}
          disabled={!gateway.configured || test.isPending}
          onClick={testConnection}
        >
          {test.isPending ? <Loader2 className="size-4 animate-spin" /> : <PlugZap className="size-4" />}
        </Button>
        {gateway.editable && (
          <>
            <Button type="button" size="icon" variant="ghost" title={`Edit ${gateway.displayName}`} aria-label={`Edit ${gateway.displayName}`} onClick={onEdit}>
              <Pencil className="size-4" />
            </Button>
            <Button
              type="button"
              size="icon"
              variant="ghost"
              className="text-destructive hover:text-destructive"
              title={`Delete ${gateway.displayName}`}
              aria-label={`Delete ${gateway.displayName}`}
              disabled={remove.isPending}
              onClick={deleteGateway}
            >
              <Trash2 className="size-4" />
            </Button>
          </>
        )}
      </div>
    </div>
  )
}

const GATEWAY_PRESETS = {
  openai: { label: 'OpenAI', type: 'OPENAI', id: 'openai-direct', name: 'OpenAI', baseUrl: 'https://api.openai.com/v1' },
  nineRouter: { label: '9Router', type: 'NINE_ROUTER', id: 'nine-router', name: '9Router', baseUrl: '' },
  openRouter: { label: 'OpenRouter', type: 'OPENAI_CHAT_COMPATIBLE', id: 'openrouter', name: 'OpenRouter', baseUrl: 'https://openrouter.ai/api/v1' },
  liteLlm: { label: 'LiteLLM', type: 'OPENAI_CHAT_COMPATIBLE', id: 'litellm', name: 'LiteLLM', baseUrl: '' },
  custom: { label: 'Generic OpenAI chat', type: 'OPENAI_CHAT_COMPATIBLE', id: 'custom-gateway', name: 'Custom gateway', baseUrl: '' },
} as const

type GatewayPreset = keyof typeof GATEWAY_PRESETS

function GatewayDialog({ gateway, onClose }: { gateway: AiGateway | null; onClose: () => void }) {
  const editing = gateway !== null
  const saveGateway = useSaveAiGateway()
  const testGateway = useTestAiGatewayDraft()
  const [preset, setPreset] = useState<GatewayPreset>('nineRouter')
  const [showKey, setShowKey] = useState(false)
  const [result, setResult] = useState<AiGatewayConnectionTest | null>(null)
  const [form, setForm] = useState<AiGatewayInput>(() => gateway ? {
    id: gateway.id,
    displayName: gateway.displayName,
    type: gateway.type,
    baseUrl: gateway.baseUrl,
    apiKey: '',
    models: gateway.configuredModels,
    discoverModels: gateway.discoverModels,
    timeoutSeconds: gateway.timeoutSeconds,
  } : presetForm('nineRouter'))

  const update = <K extends keyof AiGatewayInput>(key: K, value: AiGatewayInput[K]) => {
    setResult(null)
    setForm((current) => ({ ...current, [key]: value }))
  }

  const applyPreset = (value: GatewayPreset) => {
    setPreset(value)
    setResult(null)
    setForm(presetForm(value))
  }

  const test = () => testGateway.mutate(form, {
    onSuccess: (value) => {
      setResult(value)
      if (value.success && value.models.length > 0) {
        setForm((current) => ({ ...current, models: value.models.map((model) => model.id) }))
      }
    },
    onError: (error) => toast.error(error.message),
  })

  const save = () => saveGateway.mutate({ input: form, editing }, {
    onSuccess: () => {
      toast.success(`${form.displayName} saved`)
      onClose()
    },
    onError: (error) => toast.error(error.message),
  })

  const valid = form.id.trim().length >= 2 && form.displayName.trim() && form.baseUrl.trim()

  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="flex max-h-[calc(100vh-2rem)] flex-col overflow-hidden sm:max-w-2xl">
        <DialogHeader className="shrink-0">
          <DialogTitle>{editing ? `Edit ${gateway.displayName}` : 'Add AI gateway'}</DialogTitle>
          <DialogDescription>
            Add one connection, then reuse it for every capability that gateway supports. API keys are encrypted and never returned.
          </DialogDescription>
        </DialogHeader>

        <div className="grid min-h-0 flex-1 gap-5 overflow-y-auto py-2 pr-2">
          {!editing && (
            <Field label="Gateway type" htmlFor="gateway-preset" hint={gatewayTypeHint(form.type)}>
              <Select value={preset} onValueChange={(value) => applyPreset(value as GatewayPreset)}>
                <SelectTrigger id="gateway-preset"><SelectValue /></SelectTrigger>
                <SelectContent>
                  {Object.entries(GATEWAY_PRESETS).map(([id, item]) => <SelectItem key={id} value={id}>{item.label}</SelectItem>)}
                </SelectContent>
              </Select>
            </Field>
          )}
          {editing && (
            <Field label="Gateway type" htmlFor="gateway-type" hint={gatewayTypeHint(form.type)}>
              <Select value={form.type} onValueChange={(type) => update('type', type as AiGatewayType)}>
                <SelectTrigger id="gateway-type"><SelectValue /></SelectTrigger>
                <SelectContent>
                  <SelectItem value="OPENAI">OpenAI</SelectItem>
                  <SelectItem value="NINE_ROUTER">9Router</SelectItem>
                  <SelectItem value="OPENAI_CHAT_COMPATIBLE">OpenAI chat-compatible</SelectItem>
                </SelectContent>
              </Select>
            </Field>
          )}

          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="Name" htmlFor="gateway-name">
              <Input id="gateway-name" value={form.displayName} onChange={(event) => update('displayName', event.target.value)} />
            </Field>
            <Field label="Gateway ID" htmlFor="gateway-id" hint="Lowercase letters, numbers, and hyphens.">
              <Input id="gateway-id" value={form.id} disabled={editing} onChange={(event) => update('id', slug(event.target.value))} />
            </Field>
          </div>

          <Field label="Base URL" htmlFor="gateway-url" hint="Include the compatible API prefix, usually /v1.">
            <Input id="gateway-url" type="url" placeholder="https://router.example.com/v1" value={form.baseUrl} onChange={(event) => update('baseUrl', event.target.value)} />
          </Field>

          <Field label="API key" htmlFor="gateway-key" hint={editing ? 'Leave blank to keep the stored key.' : 'Stored encrypted with the server credential key.'}>
            <div className="relative">
              <Input id="gateway-key" className="pr-10" type={showKey ? 'text' : 'password'} value={form.apiKey ?? ''} onChange={(event) => update('apiKey', event.target.value)} />
              <Button type="button" size="icon" variant="ghost" className="absolute right-0 top-0" title={showKey ? 'Hide API key' : 'Show API key'} aria-label={showKey ? 'Hide API key' : 'Show API key'} onClick={() => setShowKey((value) => !value)}>
                {showKey ? <EyeOff className="size-4" /> : <Eye className="size-4" />}
              </Button>
            </div>
          </Field>

          <div className="grid gap-4 sm:grid-cols-[minmax(0,1fr)_9rem]">
            <Field label="Manual models" htmlFor="gateway-models" hint="One model ID per line. Discovered models replace this list after a successful test.">
              <Textarea id="gateway-models" className="min-h-24 resize-y font-mono text-xs" value={form.models.join('\n')} onChange={(event) => update('models', lines(event.target.value))} />
            </Field>
            <Field label="Timeout" htmlFor="gateway-timeout" hint="5-300 seconds.">
              <Input id="gateway-timeout" type="number" min={5} max={300} value={form.timeoutSeconds} onChange={(event) => update('timeoutSeconds', Number(event.target.value))} />
            </Field>
          </div>

          <div className="flex items-center justify-between gap-4 border-y py-4">
            <div>
              <Label htmlFor="gateway-discovery">Discover models</Label>
              <p className="mt-1 text-xs text-muted-foreground">Load available IDs from the gateway&apos;s /models endpoint.</p>
            </div>
            <Switch id="gateway-discovery" checked={form.discoverModels} onCheckedChange={(value) => update('discoverModels', value)} />
          </div>

          {result && (
            <div className={cn('flex items-start gap-2 rounded-md border px-3 py-2.5 text-sm', result.success ? 'border-emerald-500/30 bg-emerald-500/5 text-emerald-700 dark:text-emerald-300' : 'border-destructive/30 bg-destructive/5 text-destructive')}>
              {result.success ? <CheckCircle2 className="mt-0.5 size-4 shrink-0" /> : <PlugZap className="mt-0.5 size-4 shrink-0" />}
              <span>{result.message} · {result.latencyMillis} ms{result.models.length ? ` · ${result.models.length} models` : ''}</span>
            </div>
          )}
        </div>

        <DialogFooter className="shrink-0">
          <Button type="button" variant="outline" onClick={onClose}>Cancel</Button>
          <Button type="button" variant="secondary" disabled={!valid || testGateway.isPending} onClick={test}>
            {testGateway.isPending ? <Loader2 className="size-4 animate-spin" /> : <PlugZap className="size-4" />}
            Test connection
          </Button>
          <Button type="button" disabled={!valid || saveGateway.isPending} onClick={save}>
            {saveGateway.isPending ? <Loader2 className="size-4 animate-spin" /> : <Save className="size-4" />}
            Save gateway
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

function Field({ label, htmlFor, hint, children }: { label: string; htmlFor: string; hint?: string; children: ReactNode }) {
  return (
    <div className="grid self-start gap-2">
      <Label htmlFor={htmlFor}>{label}</Label>
      {children}
      {hint && <p className="text-xs leading-relaxed text-muted-foreground">{hint}</p>}
    </div>
  )
}

function presetForm(preset: GatewayPreset): AiGatewayInput {
  const item = GATEWAY_PRESETS[preset]
  return { id: item.id, displayName: item.name, type: item.type, baseUrl: item.baseUrl, apiKey: '', models: [], discoverModels: true, timeoutSeconds: 60 }
}

function gatewayTypeLabel(type: AiGatewayType) {
  if (type === 'OPENAI') return 'OpenAI'
  if (type === 'NINE_ROUTER') return '9Router'
  return 'OpenAI chat-compatible'
}

function gatewayTypeHint(type: AiGatewayType) {
  if (type === 'OPENAI') return 'Chat, web search, speech, and Realtime use OpenAI protocols.'
  if (type === 'NINE_ROUTER') return 'Chat, search, fetch, and speech use 9Router capability endpoints.'
  return 'Conservative contract: model listing and chat only.'
}

function gatewayCapabilityLabel(capability: AiGatewayCapability) {
  const labels: Record<AiGatewayCapability, string> = {
    CHAT: 'Chat',
    WEB_SEARCH: 'Search',
    WEB_FETCH: 'Fetch',
    SPEECH_TO_TEXT: 'STT',
    TEXT_TO_SPEECH: 'TTS',
    REALTIME: 'Realtime',
  }
  return labels[capability]
}

function slug(value: string) {
  return value.toLowerCase().replace(/[^a-z0-9-]+/g, '-').replace(/^-+|-+$/g, '').slice(0, 64)
}

function lines(value: string) {
  return [...new Set(value.split(/\r?\n/).map((item) => item.trim()).filter(Boolean))]
}

function AiRouteRow({
  task,
  selection,
  gateways,
}: {
  task: AiTask
  selection: AiRouteSelection
  gateways: AiGateway[]
}) {
  const update = useUpdateAiRoute()
  const reset = useResetAiRoute()
  const [gatewayId, setGatewayId] = useState(selection.route.gatewayId)
  const [modelId, setModelId] = useState(selection.route.modelId)
  const models = useAiModels(gatewayId)

  useEffect(() => {
    setGatewayId(selection.route.gatewayId)
    setModelId(selection.route.modelId)
  }, [selection])

  const gatewayModels = models.data ?? []
  const dirty = gatewayId !== selection.route.gatewayId || modelId !== selection.route.modelId
  const label = AI_TASK_LABELS[task]

  return (
    <div className="grid gap-3 py-5 lg:grid-cols-[minmax(10rem,1fr)_minmax(0,1.45fr)] lg:items-center lg:gap-8">
      <div className="min-w-0">
        <div className="flex flex-wrap items-center gap-2">
          <p className="text-sm font-medium">{label.title}</p>
          {selection.overridden && <Badge variant="secondary">Override</Badge>}
        </div>
        <p className="mt-1 text-xs leading-relaxed text-muted-foreground">{label.description}</p>
      </div>
      <div className="grid min-w-0 grid-cols-[minmax(0,0.8fr)_minmax(0,1.2fr)_auto] gap-2">
        <Select
          value={gatewayId}
          onValueChange={(value) => {
            setGatewayId(value)
            setModelId('')
          }}
        >
          <SelectTrigger className="min-w-0" aria-label={`${label.title} gateway`}><SelectValue /></SelectTrigger>
          <SelectContent>
            {gateways.map((gateway) => <SelectItem key={gateway.id} value={gateway.id}>{gateway.displayName}</SelectItem>)}
          </SelectContent>
        </Select>
        <Select value={modelId} onValueChange={setModelId} disabled={models.isLoading || gatewayModels.length === 0}>
          <SelectTrigger className="min-w-0" aria-label={`${label.title} model`}>
            {models.isLoading ? <Loader2 className="size-4 animate-spin" /> : <SelectValue placeholder="Select model" />}
          </SelectTrigger>
          <SelectContent>
            {gatewayModels.map((model) => <SelectItem key={model.id} value={model.id}>{model.displayName}</SelectItem>)}
            {modelId && !gatewayModels.some((model) => model.id === modelId) && <SelectItem value={modelId}>{modelId}</SelectItem>}
          </SelectContent>
        </Select>
        <div className="flex items-center gap-1">
          <Button
            size="icon"
            variant="ghost"
            title={`Restore ${label.title} default`}
            aria-label={`Restore ${label.title} default`}
            disabled={!selection.overridden || reset.isPending}
            onClick={() => reset.mutate(task, { onError: (error) => toast.error(error.message) })}
          >
            <RotateCcw className="size-4" />
          </Button>
          <Button
            size="icon"
            title={`Save ${label.title} route`}
            aria-label={`Save ${label.title} route`}
            disabled={!dirty || !modelId || update.isPending}
            onClick={() => update.mutate({ task, route: { gatewayId, modelId } }, {
              onSuccess: () => toast.success(`${label.title} route saved`),
              onError: (error) => toast.error(error.message),
            })}
          >
            {update.isPending ? <Loader2 className="size-4 animate-spin" /> : <Save className="size-4" />}
          </Button>
        </div>
      </div>
    </div>
  )
}

function WebResearchLoader({ onManageGateways }: { onManageGateways: () => void }) {
  const settings = useWebResearchSettings()
  const providers = useWebResearchProviders()
  const ai = useAiSettings()
  if (settings.isLoading || providers.isLoading || ai.isLoading) {
    return <div className="flex h-52 items-center justify-center border-y"><Loader2 className="size-5 animate-spin text-muted-foreground" /></div>
  }
  if (settings.isError || providers.isError || ai.isError) {
    return <div className="border-y py-8 text-sm text-destructive">{settings.error?.message ?? providers.error?.message ?? ai.error?.message ?? 'Could not load settings.'}</div>
  }
  return settings.data && providers.data && ai.data
    ? <WebResearchSection initial={settings.data} providers={providers.data} gateways={ai.data.gateways} onManageGateways={onManageGateways} />
    : null
}

function WebResearchSection({
  initial,
  providers,
  gateways,
  onManageGateways,
}: {
  initial: WebResearchSettingsInput & { overridden: boolean }
  providers: Array<{
    id: string
    displayName: string
    capabilities: Array<'SEARCH' | 'READ_PAGE'>
    configured: boolean
    routeRequired: boolean
    gatewayTypes: AiGatewayType[]
  }>
  gateways: AiGateway[]
  onManageGateways: () => void
}) {
  const update = useUpdateWebResearchSettings()
  const reset = useResetWebResearchSettings()
  const [form, setForm] = useState<WebResearchSettingsInput>({
    enabled: initial.enabled,
    searchProviderId: initial.searchProviderId,
    searchRoute: initial.searchRoute,
    pageReaderId: initial.pageReaderId,
    pageReaderRoute: initial.pageReaderRoute,
    fallbackEnabled: initial.fallbackEnabled,
  })

  useEffect(() => {
    setForm({
      enabled: initial.enabled,
      searchProviderId: initial.searchProviderId,
      searchRoute: initial.searchRoute,
      pageReaderId: initial.pageReaderId,
      pageReaderRoute: initial.pageReaderRoute,
      fallbackEnabled: initial.fallbackEnabled,
    })
  }, [initial])

  const searchProviders = useMemo(
    () => providers.filter((provider) => provider.capabilities.includes('SEARCH')),
    [providers],
  )
  const pageReaders = useMemo(
    () => providers.filter((provider) => provider.capabilities.includes('READ_PAGE')),
    [providers],
  )
  const dirty = form.enabled !== initial.enabled
    || form.searchProviderId !== initial.searchProviderId
    || form.searchRoute.gatewayId !== initial.searchRoute.gatewayId
    || form.searchRoute.targetId !== initial.searchRoute.targetId
    || form.pageReaderId !== initial.pageReaderId
    || form.pageReaderRoute.gatewayId !== initial.pageReaderRoute.gatewayId
    || form.pageReaderRoute.targetId !== initial.pageReaderRoute.targetId
    || form.fallbackEnabled !== initial.fallbackEnabled
  const selectedSearch = searchProviders.find((item) => item.id === form.searchProviderId)
  const selectedReader = pageReaders.find((item) => item.id === form.pageReaderId)
  const selectedReady = providerReady(selectedSearch, form.searchRoute, gateways, 'WEB_SEARCH')
    && providerReady(selectedReader, form.pageReaderRoute, gateways, 'WEB_FETCH')

  function save() {
    update.mutate(form, {
      onSuccess: () => toast.success('Web research settings saved'),
      onError: (error) => toast.error(error.message),
    })
  }

  function restoreDefaults() {
    reset.mutate(undefined, {
      onSuccess: (value) => {
        setForm({
          enabled: value.enabled,
          searchProviderId: value.searchProviderId,
          searchRoute: value.searchRoute,
          pageReaderId: value.pageReaderId,
          pageReaderRoute: value.pageReaderRoute,
          fallbackEnabled: value.fallbackEnabled,
        })
        toast.success('Application defaults restored')
      },
      onError: (error) => toast.error(error.message),
    })
  }

  return (
    <div>
      <div className="flex flex-wrap items-start justify-between gap-3 pb-5">
        <div>
          <div className="flex items-center gap-2">
            <h2 id="web-research-heading" className="text-xl font-semibold">Web research</h2>
            <Badge variant={initial.overridden ? 'secondary' : 'outline'}>
              {initial.overridden ? 'Runtime override' : 'App default'}
            </Badge>
          </div>
          <p className="mt-1 text-sm text-muted-foreground">Search and page access for Assistant.</p>
        </div>
        <Badge
          variant="outline"
          className={cn('gap-1.5', form.enabled && selectedReady && 'border-emerald-500/40 text-emerald-600 dark:text-emerald-400')}
        >
          <span className={cn('size-1.5 rounded-full bg-muted-foreground', form.enabled && selectedReady && 'bg-emerald-500')} />
          {form.enabled ? (selectedReady ? 'Ready' : 'Needs configuration') : 'Off'}
        </Badge>
      </div>

      <Separator />
      <SettingRow
        title="Assistant web access"
        description="Allow search and public page reading."
        control={(
          <Switch
            checked={form.enabled}
            onCheckedChange={(enabled) => setForm((value) => ({ ...value, enabled }))}
            aria-label="Enable Assistant web access"
          />
        )}
      />
      <Separator />
      <SettingRow
        title="Search provider"
        description="Current public information and external research."
        control={(
          <Select
            value={form.searchProviderId}
            onValueChange={(searchProviderId) => setForm((value) => ({
              ...value,
              searchProviderId,
              searchRoute: routeForProvider(searchProviders, searchProviderId, value.searchRoute, gateways, 'WEB_SEARCH'),
            }))}
          >
            <SelectTrigger className="w-full sm:w-64" aria-label="Search provider">
              <Search className="size-4" />
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {searchProviders.map((provider) => (
                <SelectItem key={provider.id} value={provider.id} disabled={!provider.configured && !provider.routeRequired}>
                  {provider.displayName}{provider.configured || provider.routeRequired ? '' : ' · not configured'}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        )}
      />
      {selectedSearch?.routeRequired && (
        <>
          <Separator />
          <SettingRow
            title="Search route"
            description="Use an existing gateway and a provider or combo target."
            control={<GatewayRouteControl
              capability="WEB_SEARCH"
              gateways={gateways}
              gatewayTypes={selectedSearch.gatewayTypes}
              route={form.searchRoute}
              targetPlaceholder={form.searchRoute.gatewayId && gateways.find((item) => item.id === form.searchRoute.gatewayId)?.type === 'OPENAI' ? 'gpt-5.5' : 'search-combo'}
              onChange={(searchRoute) => setForm((value) => ({ ...value, searchRoute }))}
            />}
          />
        </>
      )}
      <Separator />
      <SettingRow
        title="Page reader"
        description="Ordinary HTTP(S) links pasted into Assistant."
        control={(
          <Select
            value={form.pageReaderId}
            onValueChange={(pageReaderId) => setForm((value) => ({
              ...value,
              pageReaderId,
              pageReaderRoute: routeForProvider(pageReaders, pageReaderId, value.pageReaderRoute, gateways, 'WEB_FETCH'),
            }))}
          >
            <SelectTrigger className="w-full sm:w-64" aria-label="Page reader">
              <Globe2 className="size-4" />
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {pageReaders.map((provider) => (
                <SelectItem key={provider.id} value={provider.id} disabled={!provider.configured && !provider.routeRequired}>
                  {provider.displayName}{provider.configured || provider.routeRequired ? '' : ' · not configured'}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        )}
      />
      {selectedReader?.routeRequired && (
        <>
          <Separator />
          <SettingRow
            title="Fetch route"
            description="Use an existing gateway and a fetch provider or combo target."
            control={<GatewayRouteControl
              capability="WEB_FETCH"
              gateways={gateways}
              gatewayTypes={selectedReader.gatewayTypes}
              route={form.pageReaderRoute}
              targetPlaceholder="fetch-combo"
              onChange={(pageReaderRoute) => setForm((value) => ({ ...value, pageReaderRoute }))}
            />}
          />
        </>
      )}
      <Separator />
      <SettingRow
        title="Provider fallback"
        description="Use the server fallback order after a temporary provider failure."
        control={(
          <Switch
            checked={form.fallbackEnabled}
            onCheckedChange={(fallbackEnabled) => setForm((value) => ({ ...value, fallbackEnabled }))}
            aria-label="Enable provider fallback"
          />
        )}
      />
      <Separator />

      <div className="py-6">
        <div className="mb-3 flex items-center gap-2 text-sm font-medium">
          <Waypoints className="size-4 text-muted-foreground" />
          Available providers
        </div>
        <div className="divide-y rounded-md border">
          {providers.map((provider) => (
            <div key={provider.id} className="flex min-w-0 items-center justify-between gap-4 px-3 py-3">
              <div className="min-w-0">
                <p className="truncate text-sm font-medium">{provider.displayName}</p>
                <p className="mt-0.5 text-xs text-muted-foreground">
                  {provider.capabilities.map(capabilityLabel).join(' · ')}
                </p>
              </div>
              <Badge variant="outline" className={cn('shrink-0 gap-1', provider.configured && 'text-emerald-600 dark:text-emerald-400')}>
                <CheckCircle2 className="size-3" />
                 {provider.routeRequired ? 'Uses gateway' : provider.configured ? 'Configured' : 'Not configured'}
              </Badge>
            </div>
          ))}
        </div>
      </div>

      <div className="flex justify-end border-t py-4">
        <Button type="button" variant="ghost" size="sm" onClick={onManageGateways}>
          <Waypoints className="size-4" />
          Manage gateways
        </Button>
      </div>

      <div className="flex flex-col-reverse gap-2 border-t pt-5 sm:flex-row sm:items-center sm:justify-between">
        <Button
          type="button"
          variant="ghost"
          className="justify-start"
          onClick={restoreDefaults}
          disabled={reset.isPending || !initial.overridden}
        >
          <RotateCcw className={cn('size-4', reset.isPending && 'animate-spin')} />
          Restore defaults
        </Button>
        <Button type="button" onClick={save} disabled={!dirty || update.isPending || (form.enabled && !selectedReady)}>
          {update.isPending ? <Loader2 className="size-4 animate-spin" /> : <Save className="size-4" />}
          Save changes
        </Button>
      </div>
    </div>
  )
}

type WebProviderOption = {
  id: string
  configured: boolean
  routeRequired: boolean
  gatewayTypes: AiGatewayType[]
}

function providerReady(
  provider: WebProviderOption | undefined,
  route: WebProviderRoute,
  gateways: AiGateway[],
  capability: AiGatewayCapability,
) {
  if (!provider) return false
  if (!provider.routeRequired) return provider.configured
  const gateway = gateways.find((item) => item.id === route.gatewayId)
  return Boolean(gateway?.configured
    && provider.gatewayTypes.includes(gateway.type)
    && gateway.capabilities.includes(capability)
    && route.targetId.trim())
}

function routeForProvider(
  providers: WebProviderOption[],
  providerId: string,
  current: WebProviderRoute,
  gateways: AiGateway[],
  capability: AiGatewayCapability,
): WebProviderRoute {
  const provider = providers.find((item) => item.id === providerId)
  if (!provider?.routeRequired) return { gatewayId: '', targetId: '' }
  const compatible = gateways.filter((gateway) => gateway.configured
    && provider.gatewayTypes.includes(gateway.type)
    && gateway.capabilities.includes(capability))
  const gateway = compatible.find((item) => item.id === current.gatewayId) ?? compatible[0]
  if (!gateway) return { gatewayId: '', targetId: '' }
  return {
    gatewayId: gateway.id,
    targetId: current.gatewayId === gateway.id && current.targetId
      ? current.targetId
      : defaultTarget(gateway, capability),
  }
}

function defaultTarget(gateway: AiGateway, capability: AiGatewayCapability) {
  if (capability === 'WEB_FETCH') return 'fetch-combo'
  if (gateway.type === 'OPENAI') return gateway.configuredModels[0] ?? 'gpt-5.5'
  return 'search-combo'
}

function GatewayRouteControl({
  capability,
  gateways,
  gatewayTypes,
  route,
  targetPlaceholder,
  onChange,
}: {
  capability: AiGatewayCapability
  gateways: AiGateway[]
  gatewayTypes: AiGatewayType[]
  route: WebProviderRoute
  targetPlaceholder: string
  onChange: (route: WebProviderRoute) => void
}) {
  const compatible = gateways.filter((gateway) => gateway.configured
    && gatewayTypes.includes(gateway.type)
    && gateway.capabilities.includes(capability))
  return (
    <div className="grid w-full gap-2 sm:w-64">
      <Select
        value={route.gatewayId}
        onValueChange={(gatewayId) => {
          const gateway = gateways.find((item) => item.id === gatewayId)
          onChange({ gatewayId, targetId: gateway ? defaultTarget(gateway, capability) : '' })
        }}
      >
        <SelectTrigger aria-label={`${gatewayCapabilityLabel(capability)} gateway`}>
          <Waypoints className="size-4" />
          <SelectValue placeholder="Select gateway" />
        </SelectTrigger>
        <SelectContent>
          {compatible.map((gateway) => (
            <SelectItem key={gateway.id} value={gateway.id}>{gateway.displayName}</SelectItem>
          ))}
        </SelectContent>
      </Select>
      <Input
        value={route.targetId}
        placeholder={targetPlaceholder}
        aria-label={`${gatewayCapabilityLabel(capability)} target`}
        onChange={(event) => onChange({ ...route, targetId: event.target.value })}
      />
    </div>
  )
}

function SettingRow({ title, description, control }: { title: string; description: string; control: ReactNode }) {
  return (
    <div className="grid gap-3 py-5 sm:grid-cols-[minmax(0,1fr)_16rem] sm:items-center sm:gap-8">
      <div className="min-w-0">
        <p className="text-sm font-medium">{title}</p>
        <p className="mt-1 text-xs leading-relaxed text-muted-foreground">{description}</p>
      </div>
      <div className="flex min-w-0 justify-start sm:justify-end">{control}</div>
    </div>
  )
}

function capabilityLabel(capability: 'SEARCH' | 'READ_PAGE') {
  return capability === 'SEARCH' ? 'Search' : 'Page reader'
}
