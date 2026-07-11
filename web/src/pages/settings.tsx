import { useEffect, useMemo, useState } from 'react'
import { BrainCircuit, CheckCircle2, Clock3, Globe2, Loader2, RotateCcw, Save, Search, Waypoints } from 'lucide-react'
import { toast } from 'sonner'
import { AutomationsSection } from '@/components/settings/automations-section'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Separator } from '@/components/ui/separator'
import { Switch } from '@/components/ui/switch'
import {
  useResetWebResearchSettings,
  useUpdateWebResearchSettings,
  useWebResearchProviders,
  useWebResearchSettings,
  type WebResearchSettingsInput,
} from '@/lib/web-research-api'
import { cn } from '@/lib/utils'
import {
  AI_TASKS,
  useAiModels,
  useAiSettings,
  useResetAiRoute,
  useUpdateAiRoute,
  type AiGateway,
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
            <div className="flex overflow-x-auto pb-1 md:block md:space-y-1 md:overflow-visible md:pb-0">
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
                ? <WebResearchLoader />
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
          Route each workload through a configured OpenAI-compatible gateway.
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
        <div className="mb-3 flex items-center gap-2 text-sm font-medium">
          <Waypoints className="size-4 text-muted-foreground" />
          Configured gateways
        </div>
        <div className="divide-y rounded-md border">
          {settings.data.gateways.map((gateway) => (
            <div key={gateway.id} className="flex items-center justify-between gap-4 px-3 py-3">
              <div className="min-w-0">
                <p className="truncate text-sm font-medium">{gateway.displayName}</p>
                <p className="mt-0.5 truncate text-xs text-muted-foreground">{gateway.id}</p>
              </div>
              <Badge variant="outline" className={cn('shrink-0', gateway.configured && 'text-emerald-600 dark:text-emerald-400')}>
                {gateway.configured ? 'Ready' : 'Not configured'}
              </Badge>
            </div>
          ))}
        </div>
      </div>
    </div>
  )
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

function WebResearchLoader() {
  const settings = useWebResearchSettings()
  const providers = useWebResearchProviders()
  if (settings.isLoading || providers.isLoading) {
    return <div className="flex h-52 items-center justify-center border-y"><Loader2 className="size-5 animate-spin text-muted-foreground" /></div>
  }
  if (settings.isError || providers.isError) {
    return <div className="border-y py-8 text-sm text-destructive">{settings.error?.message ?? providers.error?.message ?? 'Could not load settings.'}</div>
  }
  return settings.data && providers.data ? <WebResearchSection initial={settings.data} providers={providers.data} /> : null
}

function WebResearchSection({
  initial,
  providers,
}: {
  initial: WebResearchSettingsInput & { overridden: boolean }
  providers: Array<{
    id: string
    displayName: string
    capabilities: Array<'SEARCH' | 'READ_PAGE'>
    configured: boolean
  }>
}) {
  const update = useUpdateWebResearchSettings()
  const reset = useResetWebResearchSettings()
  const [form, setForm] = useState<WebResearchSettingsInput>({
    enabled: initial.enabled,
    searchProviderId: initial.searchProviderId,
    pageReaderId: initial.pageReaderId,
    fallbackEnabled: initial.fallbackEnabled,
  })

  useEffect(() => {
    setForm({
      enabled: initial.enabled,
      searchProviderId: initial.searchProviderId,
      pageReaderId: initial.pageReaderId,
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
    || form.pageReaderId !== initial.pageReaderId
    || form.fallbackEnabled !== initial.fallbackEnabled
  const selectedReady = searchProviders.find((item) => item.id === form.searchProviderId)?.configured
    && pageReaders.find((item) => item.id === form.pageReaderId)?.configured

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
          pageReaderId: value.pageReaderId,
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
            onValueChange={(searchProviderId) => setForm((value) => ({ ...value, searchProviderId }))}
          >
            <SelectTrigger className="w-full sm:w-64" aria-label="Search provider">
              <Search className="size-4" />
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {searchProviders.map((provider) => (
                <SelectItem key={provider.id} value={provider.id} disabled={!provider.configured}>
                  {provider.displayName}{provider.configured ? '' : ' · not configured'}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        )}
      />
      <Separator />
      <SettingRow
        title="Page reader"
        description="Ordinary HTTP(S) links pasted into Assistant."
        control={(
          <Select
            value={form.pageReaderId}
            onValueChange={(pageReaderId) => setForm((value) => ({ ...value, pageReaderId }))}
          >
            <SelectTrigger className="w-full sm:w-64" aria-label="Page reader">
              <Globe2 className="size-4" />
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {pageReaders.map((provider) => (
                <SelectItem key={provider.id} value={provider.id} disabled={!provider.configured}>
                  {provider.displayName}{provider.configured ? '' : ' · not configured'}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        )}
      />
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
                {provider.configured ? 'Configured' : 'Not configured'}
              </Badge>
            </div>
          ))}
        </div>
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

function SettingRow({ title, description, control }: { title: string; description: string; control: React.ReactNode }) {
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
