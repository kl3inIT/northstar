import { CircleAlert, Loader2 } from 'lucide-react'
import { useMemo } from 'react'
import { toast } from 'sonner'
import {
  AutomationEditor,
} from '@/components/settings/automations-section'
import { Button } from '@/components/ui/button'
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from '@/components/ui/sheet'
import {
  useAutomationTypes,
  useAutomations,
  useCreateAutomation,
  useUpdateAutomation,
} from '@/lib/automation-api'
import {
  MORNING_BRIEF_TYPE,
  morningBriefRequest,
  type AutomationEditorTarget,
  type MorningBriefForm,
} from '@/lib/morning-brief-automation'

export function NorthstarBriefSettings({
  open,
  onOpenChange,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
}) {
  const automations = useAutomations()
  const types = useAutomationTypes()
  const create = useCreateAutomation()
  const update = useUpdateAutomation()
  const definition = automations.data?.find((item) => item.type === MORNING_BRIEF_TYPE)
  const descriptor = types.data?.find((item) => item.type === MORNING_BRIEF_TYPE)
  const target = useMemo<AutomationEditorTarget | null>(() => {
    if (definition) return definition
    if (descriptor) return { kind: 'new', descriptor }
    return null
  }, [definition, descriptor])

  function submit(form: MorningBriefForm) {
    const body = morningBriefRequest(form)
    const options = {
      onSuccess: () => {
        toast.success(definition ? 'Northstar Brief saved' : 'Northstar Brief enabled')
        onOpenChange(false)
      },
      onError: (error: Error) => toast.error(error.message),
    }
    if (definition) {
      update.mutate({ id: definition.id, body: { ...body, version: definition.version } }, options)
    } else {
      create.mutate({ ...body, type: MORNING_BRIEF_TYPE }, options)
    }
  }

  const loading = automations.isLoading || types.isLoading
  const error = automations.isError
    ? automations.error.message
    : types.isError
      ? types.error.message
      : !loading && !descriptor
        ? 'Northstar Brief is not available on this server.'
        : null

  if (loading || error || !target) {
    return (
      <Sheet open={open} onOpenChange={onOpenChange}>
        <SheetContent className="sm:max-w-lg">
          <SheetHeader>
            <SheetTitle>Northstar Brief settings</SheetTitle>
            <SheetDescription>Schedule and curate your private brief.</SheetDescription>
          </SheetHeader>
          <div className="grid min-h-64 place-items-center px-6 text-center">
            {loading ? <Loader2 className="size-5 animate-spin text-muted-foreground" /> : (
              <div>
                <CircleAlert className="mx-auto size-6 text-destructive" />
                <p className="mt-3 text-sm text-destructive">{error}</p>
                <Button variant="outline" className="mt-4" onClick={() => onOpenChange(false)}>Close</Button>
              </div>
            )}
          </div>
        </SheetContent>
      </Sheet>
    )
  }

  return (
    <AutomationEditor
      key={definition?.id ?? `new-${descriptor?.type ?? MORNING_BRIEF_TYPE}`}
      target={target}
      descriptor={descriptor}
      open={open}
      busy={create.isPending || update.isPending}
      onOpenChange={onOpenChange}
      onSubmit={submit}
    />
  )
}
