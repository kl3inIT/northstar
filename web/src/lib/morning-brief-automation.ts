import type { AutomationDefinition, AutomationType } from './automation-api'
import type { AutomationTrigger } from './hey-api'

type Day = AutomationTrigger['daysOfWeek'][number]

export interface MorningBriefForm {
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
  sourceIds: string[]
  githubRepositories: string
  feedUrls: string
  blueskyHandles: string
  firecrawlCreditBudget: number
}

export interface NewAutomationTarget {
  kind: 'new'
  descriptor: AutomationType
}

export type AutomationEditorTarget = AutomationDefinition | NewAutomationTarget

export const MORNING_BRIEF_TYPE = 'morning-brief.v1'

export function morningBriefRequest(form: MorningBriefForm) {
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
      sourceIds: form.sourceIds,
      githubRepositories: lines(form.githubRepositories),
      feedUrls: lines(form.feedUrls),
      blueskyHandles: lines(form.blueskyHandles),
      firecrawlCreditBudget: form.firecrawlCreditBudget,
    },
  }
}

export function isNewAutomationTarget(target: AutomationEditorTarget): target is NewAutomationTarget {
  return 'kind' in target && target.kind === 'new'
}

function lines(value: string) {
  return [...new Set(value.split(/\r?\n/).map((item) => item.trim()).filter(Boolean))]
}
