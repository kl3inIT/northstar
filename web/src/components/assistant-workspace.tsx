import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Link } from '@tanstack/react-router'
import { useChat } from '@ai-sdk/react'
import {
  DefaultChatTransport,
  type FileUIPart,
  type SourceDocumentUIPart,
  type SourceUrlUIPart,
  type ToolUIPart,
  type UIMessage,
} from 'ai'
import {
  CalendarDays,
  Check,
  CheckCircle2,
  ChevronsUpDown,
  Copy,
  History as HistoryIcon,
  Loader2,
  Globe2,
  MessageSquarePlus,
  NotebookText,
  PanelRightClose,
  PanelRightOpen,
  Search,
  Trash2,
  Volume2,
  Wrench,
  X,
  XCircle,
  type LucideIcon,
} from 'lucide-react'
import { useEffect, useRef, useState, type MouseEvent, type ReactNode } from 'react'
import { toast } from 'sonner'
import {
  Attachment,
  AttachmentPreview,
  AttachmentRemove,
  Attachments,
} from '@/components/ai-elements/attachments'
import {
  ChainOfThought,
  ChainOfThoughtContent,
  ChainOfThoughtHeader,
  ChainOfThoughtSearchResult,
  ChainOfThoughtSearchResults,
  ChainOfThoughtStep,
} from '@/components/ai-elements/chain-of-thought'
import {
  Conversation,
  ConversationContent,
  ConversationScrollButton,
} from '@/components/ai-elements/conversation'
import {
  InlineCitation,
  InlineCitationCard,
  InlineCitationCardBody,
  InlineCitationCardTrigger,
  InlineCitationSource,
  InlineCitationText,
} from '@/components/ai-elements/inline-citation'
import { Message, MessageAction, MessageActions, MessageContent, MessageResponse } from '@/components/ai-elements/message'
import {
  AudioPlayer,
  AudioPlayerControlBar,
  AudioPlayerDurationDisplay,
  AudioPlayerElement,
  AudioPlayerMuteButton,
  AudioPlayerPlayButton,
  AudioPlayerTimeDisplay,
  AudioPlayerTimeRange,
} from '@/components/ai-elements/audio-player'
import {
  ModelSelector,
  ModelSelectorContent,
  ModelSelectorEmpty,
  ModelSelectorGroup,
  ModelSelectorInput,
  ModelSelectorItem,
  ModelSelectorList,
  ModelSelectorName,
  ModelSelectorTrigger,
} from '@/components/ai-elements/model-selector'
import { ModelProviderMark } from '@/components/model-provider-mark'
import {
  PromptInput,
  PromptInputActionAddAttachments,
  PromptInputActionAddScreenshot,
  PromptInputActionMenu,
  PromptInputActionMenuContent,
  PromptInputActionMenuTrigger,
  PromptInputBody,
  PromptInputFooter,
  PromptInputHeader,
  PromptInputSubmit,
  PromptInputTextarea,
  PromptInputTools,
  usePromptInputAttachments,
  type PromptInputMessage,
} from '@/components/ai-elements/prompt-input'
import { Suggestion } from '@/components/ai-elements/suggestion'
import { Source, Sources, SourcesContent, SourcesTrigger } from '@/components/ai-elements/sources'
import { Tool, ToolContent, ToolHeader, ToolInput, ToolOutput } from '@/components/ai-elements/tool'
import { Button } from '@/components/ui/button'
import { Bubble, BubbleContent } from '@/components/ui/bubble'
import {
  Sheet,
  SheetContent,
  SheetTrigger,
} from '@/components/ui/sheet'
import { MicButton } from '@/components/mic-button'
import {
  deleteAssistantConversation,
  listAssistantConversations,
  listAssistantHistory,
} from '@/lib/hey-api'
import { dataOrThrow, voidOrThrow } from '@/lib/hey-api-result'
import type {
  ConversationSummary as ApiConversationSummary,
  HistoryMessage as ApiHistoryMessage,
} from '@/lib/hey-api'
import { fileUrl, uploadFile } from '@/lib/files-api'
import { apiFetch } from '@/lib/http'
import {
  useChatAiModels,
  useAssistantConversationModel,
  useUpdateAssistantConversationModel,
} from '@/lib/ai-settings-api'
import { useStagingCount } from '@/lib/notes-api'
import { useTodayTasks } from '@/lib/tasks-api'
import { useSynthesizeSpeech, type SpeechAsset } from '@/lib/speech-api'
import { cn } from '@/lib/utils'

/**
 * The in-app assistant, organized the way zeromail's chat is: a borderless
 * center column (greeting + pill input + quiet suggestion chips on first
 * visit, then plain-text answers with minimal tool rows), and a chat-history
 * panel on the RIGHT titled by each conversation's first message. useChat
 * speaks the AI SDK UI Message Stream the api emits; conversations are
 * durable (JDBC memory) and /history rehydrates on switch.
 */

/**
 * Citation links from search_knowledge. Overriding `a` replaces Streamdown's
 * dialog-wrapped link component, so we re-establish its safety ourselves:
 *  - in-app note links ride the router (same tab, SPA);
 *  - same-origin links (our own /api/files downloads) open a new tab directly;
 *  - EXTERNAL links are model-authored and could be smuggled in by a
 *    prompt-injection payload inside a note or an uploaded document, so they
 *    reveal their real destination and require a confirmation before leaving
 *    the app — the guard Streamdown's dialog used to provide.
 */
const CITATION_LINK = 'font-medium text-primary underline underline-offset-2 hover:no-underline'

const CHAT_MARKDOWN_COMPONENTS = {
  a: ({ href, children }: { href?: string; children?: ReactNode }) => {
    if (href?.startsWith('/notes/')) {
      return (
        <Link to="/notes/$slug" params={{ slug: href.slice('/notes/'.length) }} className={CITATION_LINK}>
          {children}
        </Link>
      )
    }
    if (href?.startsWith('/')) {
      return (
        <a href={href} target="_blank" rel="noreferrer" className={CITATION_LINK}>
          {children}
        </a>
      )
    }
    if (!href) return <>{children}</>
    const confirmExternal = (event: MouseEvent<HTMLAnchorElement>) => {
      const ok = window.confirm(
        `This link was written by the assistant and leads outside Northstar:\n\n${href}\n\nOpen it?`,
      )
      if (!ok) event.preventDefault()
    }
    return (
      <InlineCitation>
        <InlineCitationText>
          <a href={href} target="_blank" rel="noreferrer nofollow" className={CITATION_LINK} onClick={confirmExternal}>
            {children}
          </a>
        </InlineCitationText>
        <InlineCitationCard>
          <InlineCitationCardTrigger sources={[href]} />
          <InlineCitationCardBody>
            <div className="space-y-3 p-3">
              <InlineCitationSource title={plainText(children) || href} url={href} />
              <a href={href} target="_blank" rel="noreferrer nofollow" className={CITATION_LINK} onClick={confirmExternal}>
                Open source
              </a>
            </div>
          </InlineCitationCardBody>
        </InlineCitationCard>
      </InlineCitation>
    )
  },
}

function plainText(value: ReactNode): string {
  if (typeof value === 'string' || typeof value === 'number') return String(value)
  if (Array.isArray(value)) return value.map(plainText).join('')
  return ''
}

/** Kills the InputGroup chrome: zeromail's input is a soft pill, no border, no ring. */
const PILL_INPUT =
  'w-full [&_[data-slot=input-group]]:rounded-3xl [&_[data-slot=input-group]]:!border-transparent ' +
  '[&_[data-slot=input-group]]:bg-muted/60 [&_[data-slot=input-group]]:shadow-none ' +
  '[&_[data-slot=input-group]]:!ring-0'

// The server always populates every field; the generated schema marks them
// optional (no `required` in the contract), so assert them present here.
type ConversationSummary = Required<ApiConversationSummary>
type HistoryMessage = Omit<ApiHistoryMessage, 'parts'> & { parts?: unknown }

async function fetchConversations(signal?: AbortSignal): Promise<ConversationSummary[]> {
  return dataOrThrow(await listAssistantConversations({ signal })) as ConversationSummary[]
}

async function fetchHistory(conversationId: string, signal?: AbortSignal): Promise<UIMessage[]> {
  const data = dataOrThrow(
    await listAssistantHistory({ query: { conversationId }, signal }),
  ) as HistoryMessage[]
  return (data ?? []).map((m, i) => ({
    id: `history-${i}`,
    role: (m.role ?? 'assistant') as 'user' | 'assistant',
    parts: historyParts(m),
  }))
}

function historyParts(message: HistoryMessage): UIMessage['parts'] {
  if (Array.isArray(message.parts)) {
    const parts = message.parts.filter(isHistoryPart)
    if (parts.length > 0) return parts
  }
  return message.text ? [{ type: 'text', text: message.text }] : []
}

function isHistoryPart(part: unknown): part is UIMessage['parts'][number] {
  if (!isRecord(part) || typeof part.type !== 'string') return false
  if (part.type === 'text') return typeof part.text === 'string'
  if (part.type === 'file') return typeof part.url === 'string'
  if (part.type === 'source-url') return typeof part.sourceId === 'string' && typeof part.url === 'string'
  if (part.type === 'source-document') {
    return typeof part.sourceId === 'string' && typeof part.mediaType === 'string' && typeof part.title === 'string'
  }
  if (part.type.startsWith('tool-')) {
    return typeof part.toolCallId === 'string' && typeof part.state === 'string'
  }
  return false
}

/**
 * Empty-state suggestions grounded in the user's actual state — a pile of
 * overdue tasks or a waiting Staging queue beats a generic "ask me anything".
 */
function useSuggestions(): string[] {
  const { data: today = [] } = useTodayTasks()
  const { data: stagingCount = 0 } = useStagingCount()

  const overdue = today.filter((t) => {
    if (t.status !== 'OPEN' || !t.dueDate) return false
    return t.dueDate < new Date().toISOString().slice(0, 10)
  }).length

  const suggestions: string[] = []
  if (overdue > 0) {
    suggestions.push(`Help me triage my ${overdue} overdue task${overdue === 1 ? '' : 's'}`)
  } else if (today.some((t) => t.status === 'OPEN')) {
    suggestions.push('What should I focus on today?')
  }
  if (stagingCount > 0) {
    suggestions.push(`Summarize the ${stagingCount} note${stagingCount === 1 ? '' : 's'} waiting in Staging`)
  }
  suggestions.push('Find me a free 90-minute study slot tomorrow')
  if (suggestions.length < 3) {
    suggestions.push("What's on my calendar this week?")
  }
  return suggestions.slice(0, 4)
}

function greeting(): string {
  const hour = new Date().getHours()
  if (hour < 12) return 'Good morning'
  if (hour < 18) return 'Good afternoon'
  return 'Good evening'
}

export function AssistantWorkspace({ compact = false }: { compact?: boolean }) {
  const queryClient = useQueryClient()
  const { data: conversations = [], isPending } = useQuery({
    queryKey: ['assistant-conversations'],
    queryFn: ({ signal }) => fetchConversations(signal),
  })
  const [conversationId, setConversationId] = useState<string | null>(null)
  const [historyOpen, setHistoryOpen] = useState(
    () => localStorage.getItem('assistant-history-open') !== '0',
  )
  const [mobileHistoryOpen, setMobileHistoryOpen] = useState(false)

  function toggleHistory() {
    setHistoryOpen((open) => {
      localStorage.setItem('assistant-history-open', open ? '0' : '1')
      return !open
    })
  }
  // Once, on load: resume the most recent conversation, else start fresh.
  const bootstrapped = useRef(false)
  useEffect(() => {
    if (bootstrapped.current || isPending) return
    bootstrapped.current = true
    setConversationId(conversations[0]?.id ?? crypto.randomUUID())
  }, [isPending, conversations])

  function remove(id: string) {
    deleteAssistantConversation({ path: { id } })
      .then((result) => {
        voidOrThrow(result)
        void queryClient.invalidateQueries({ queryKey: ['assistant-conversations'] })
        queryClient.removeQueries({ queryKey: ['assistant-history', id] })
        if (id === conversationId) setConversationId(crypto.randomUUID())
      })
      .catch(() => toast.error('Delete failed — try again.'))
  }

  if (conversationId === null) {
    return (
      <div className="flex w-full flex-1 items-center justify-center">
        <Loader2 className="size-5 animate-spin text-muted-foreground" />
      </div>
    )
  }
  return (
    <div className="relative flex h-full w-full flex-1 overflow-hidden">
      <ChatColumn key={conversationId} conversationId={conversationId} compact={compact} />

      {compact ? (
        <>
          <Button
            size="icon"
            variant="ghost"
            className="absolute right-3 top-3 size-7"
            aria-label="Chat history"
            title="Chat history"
            onClick={() => setMobileHistoryOpen(true)}
          >
            <HistoryIcon className="size-4" />
          </Button>
          {mobileHistoryOpen && (
            <div className="absolute inset-0 z-20 bg-background">
              <HistoryPanel
                conversations={conversations}
                activeId={conversationId}
                onNew={() => {
                  setConversationId(crypto.randomUUID())
                  setMobileHistoryOpen(false)
                }}
                onSelect={(id) => {
                  setConversationId(id)
                  setMobileHistoryOpen(false)
                }}
                onRemove={remove}
                onClose={() => setMobileHistoryOpen(false)}
              />
            </div>
          )}
        </>
      ) : (
        <Sheet open={mobileHistoryOpen} onOpenChange={setMobileHistoryOpen}>
          <SheetTrigger asChild>
            <Button
              size="icon"
              variant="ghost"
              className="absolute right-3 top-3 size-7 lg:hidden"
              aria-label="Chat history"
              title="Chat history"
            >
              <HistoryIcon className="size-4" />
            </Button>
          </SheetTrigger>
          <SheetContent side="right" showCloseButton={false} className="flex w-80 flex-col gap-0 p-0">
            <HistoryPanel
              conversations={conversations}
              activeId={conversationId}
              onNew={() => {
                setConversationId(crypto.randomUUID())
                setMobileHistoryOpen(false)
              }}
              onSelect={(id) => {
                setConversationId(id)
                setMobileHistoryOpen(false)
              }}
              onRemove={remove}
              onClose={() => setMobileHistoryOpen(false)}
            />
          </SheetContent>
        </Sheet>
      )}

      {!compact && !historyOpen && (
        <Button
          size="icon"
          variant="ghost"
          className="absolute right-3 top-3 hidden size-7 lg:inline-flex"
          aria-label="Show chat history"
          title="Show chat history"
          onClick={toggleHistory}
        >
          <PanelRightOpen className="size-4" />
        </Button>
      )}

      {!compact && historyOpen && (
      <aside className="hidden w-72 shrink-0 flex-col border-l lg:flex">
        <div className="flex items-center justify-between px-4 pb-2 pt-4">
          <h2 className="text-sm font-semibold">Chat history</h2>
          <div className="flex items-center">
            <Button
              size="icon"
              variant="ghost"
              className="size-7"
              aria-label="New chat"
              title="New chat"
              onClick={() => setConversationId(crypto.randomUUID())}
            >
              <MessageSquarePlus className="size-4" />
            </Button>
            <Button
              size="icon"
              variant="ghost"
              className="size-7"
              aria-label="Hide chat history"
              title="Hide chat history"
              onClick={toggleHistory}
            >
              <PanelRightClose className="size-4" />
            </Button>
          </div>
        </div>
        <HistoryList
          conversations={conversations}
          activeId={conversationId}
          onSelect={setConversationId}
          onRemove={remove}
        />
      </aside>
      )}
    </div>
  )
}

function HistoryPanel({
  conversations,
  activeId,
  onNew,
  onSelect,
  onRemove,
  onClose,
}: {
  conversations: ConversationSummary[]
  activeId: string
  onNew: () => void
  onSelect: (id: string) => void
  onRemove: (id: string) => void
  onClose: () => void
}) {
  return (
    <div className="flex h-full min-h-0 flex-col">
      <header className="flex items-center justify-between border-b px-4 py-3">
        <h2 className="text-sm font-semibold">Chat history</h2>
        <div className="flex items-center gap-1">
          <Button size="icon" variant="ghost" className="size-7" aria-label="New chat" title="New chat" onClick={onNew}>
            <MessageSquarePlus className="size-4" />
          </Button>
          <Button size="icon" variant="ghost" className="size-7" aria-label="Close history" title="Close history" onClick={onClose}>
            <X className="size-4" />
          </Button>
        </div>
      </header>
      <HistoryList
        conversations={conversations}
        activeId={activeId}
        onSelect={onSelect}
        onRemove={onRemove}
      />
    </div>
  )
}

function HistoryList({
  conversations,
  activeId,
  onSelect,
  onRemove,
}: {
  conversations: ConversationSummary[]
  activeId: string
  onSelect: (id: string) => void
  onRemove: (id: string) => void
}) {
  return (
    <div className="min-h-0 flex-1 overflow-y-auto px-2 pb-3 pt-2">
      {conversations.length === 0 && (
        <p className="px-2 py-1 text-xs text-muted-foreground">No conversations yet.</p>
      )}
      {conversations.map((c) => (
        <div
          key={c.id}
          className={cn(
            'group flex w-full items-center gap-1 rounded-lg',
            c.id === activeId ? 'bg-muted' : 'hover:bg-muted/50',
          )}
        >
          <button
            type="button"
            onClick={() => onSelect(c.id)}
            className="min-w-0 flex-1 px-2 py-2 text-left"
          >
            <span className="block truncate text-sm font-medium">{c.title}</span>
            <span className="text-xs text-muted-foreground">
              {formatLastAt(c.lastAt)} · {c.messages}
            </span>
          </button>
          <Button
            size="icon"
            variant="ghost"
            className="mr-1 size-6 shrink-0 opacity-0 transition-opacity group-hover:opacity-100"
            aria-label="Delete conversation"
            title="Delete conversation"
            onClick={() => onRemove(c.id)}
          >
            <Trash2 className="size-3.5 text-muted-foreground" />
          </Button>
        </div>
      ))}
    </div>
  )
}

function formatLastAt(iso: string): string {
  const date = new Date(iso)
  return date.toLocaleString('en-US', {
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

/** Keyed by conversationId — switching conversations rehydrates from /history. */
function ChatColumn({ conversationId, compact = false }: { conversationId: string; compact?: boolean }) {
  const { data: history, isPending } = useQuery({
    queryKey: ['assistant-history', conversationId],
    queryFn: ({ signal }) => fetchHistory(conversationId, signal),
    staleTime: Infinity,
    refetchOnWindowFocus: false,
  })

  if (isPending) {
    return (
      <div className="flex flex-1 items-center justify-center">
        <Loader2 className="size-5 animate-spin text-muted-foreground" />
      </div>
    )
  }
  return <AssistantChat conversationId={conversationId} initialMessages={history ?? []} compact={compact} />
}

function AssistantChat({
  conversationId,
  initialMessages,
  compact = false,
}: {
  conversationId: string
  initialMessages: UIMessage[]
  compact?: boolean
}) {
  const suggestions = useSuggestions()
  const queryClient = useQueryClient()
  const modelSelection = useAssistantConversationModel(conversationId)
  const availableModels = useChatAiModels()
  const updateModel = useUpdateAssistantConversationModel(conversationId)
  const { messages, sendMessage, status, stop } = useChat({
    messages: initialMessages,
    onFinish: () => {
      // A finished turn may have created/completed things and retitles the list.
      void queryClient.invalidateQueries({ queryKey: ['assistant-conversations'] })
      void queryClient.invalidateQueries({ queryKey: ['tasks'] })
      void queryClient.invalidateQueries({ queryKey: ['notes'] })
      void queryClient.invalidateQueries({
        queryKey: ['assistant-history', conversationId],
        refetchType: 'none',
      })
    },
    transport: new DefaultChatTransport({
      api: '/api/assistant/chat',
      fetch: apiFetch,
      credentials: 'same-origin',
      prepareSendMessagesRequest: ({ messages, messageId }) => {
        const last = messages.at(-1)
        const text = (last?.parts ?? [])
          .filter((p) => p.type === 'text')
          .map((p) => (p as { text: string }).text)
          .join('')
        // Image parts point at stored files (/api/files/{id}, uploaded on
        // submit); the api loads the bytes for the model and keeps markdown
        // markers in memory so history re-renders them.
        const attachmentIds = (last?.parts ?? [])
          .filter((p): p is FileUIPart => p.type === 'file')
          .map((p) => p.url.split('/').pop())
          .filter(Boolean)
        const clientTurnId = messageId ?? last?.id
        return {
          headers: clientTurnId ? { 'Idempotency-Key': clientTurnId } : undefined,
          body: {
            message: text,
            conversationId,
            attachmentIds,
            gatewayId: modelSelection.data?.gatewayId,
            modelId: modelSelection.data?.modelId,
          },
        }
      },
    }),
  })

  const [text, setText] = useState('')
  const submitLock = useRef(false)
  const [isUploading, setIsUploading] = useState(false)
  const busy = status === 'submitted' || status === 'streaming'
  const latestMessage = messages.at(-1)
  const showWaiting = busy && (
    latestMessage === undefined ||
    latestMessage.role === 'user' ||
    (latestMessage.role === 'assistant' && !messageHasVisibleOutput(latestMessage))
  )

  async function onSubmit(message: PromptInputMessage) {
    // Reject rather than resolve so PromptInput keeps its text/attachments while
    // the first submission owns them.
    if (submitLock.current || busy) throw new Error('Assistant turn already in progress')
    const trimmed = message.text.trim()
    const images = message.files.filter((f) => f.mediaType?.startsWith('image/'))
    if (!trimmed && images.length === 0) return
    submitLock.current = true
    setIsUploading(true)
    try {
      let uploaded: FileUIPart[]
      try {
        uploaded = await Promise.all(images.map(uploadImage))
      } catch {
        toast.error('Image upload failed — try again.')
        throw new Error('upload failed') // keeps the composer content for retry
      }
      setIsUploading(false)
      // sendMessage synchronously moves the submitted content into the transcript.
      // Do not keep the composer waiting for the model/tool stream to finish.
      const turn = sendMessage({ text: trimmed, files: uploaded })
      setText('')
      const releaseSubmitLock = () => {
        submitLock.current = false
      }
      void turn.then(releaseSubmitLock, releaseSubmitLock)
    } catch (error) {
      submitLock.current = false
      setIsUploading(false)
      throw error
    }
  }

  const input = (
    <PromptInput
      onSubmit={onSubmit}
      className={PILL_INPUT}
      accept="image/*"
      globalDrop
      multiple
      maxFiles={3}
      maxFileSize={8 * 1024 * 1024}
      onError={(err) =>
        toast.error(
          err.code === 'max_file_size'
            ? 'Images must be under 8MB.'
            : err.code === 'max_files'
              ? 'Up to 3 images per message.'
              : 'Only images can be attached.',
        )
      }
    >
      <PromptInputHeader>
        <AttachmentStrip />
      </PromptInputHeader>
      <PromptInputBody>
        <PromptInputTextarea
          placeholder="Message Northstar…"
          autoFocus
          className="min-h-12"
          value={text}
          onChange={(e) => setText(e.currentTarget.value)}
        />
      </PromptInputBody>
      <PromptInputFooter>
        <PromptInputTools>
          <PromptInputActionMenu>
            <PromptInputActionMenuTrigger aria-label="Add attachment" tooltip="Add attachment" />
            <PromptInputActionMenuContent>
              <PromptInputActionAddAttachments label="Add images" />
              <PromptInputActionAddScreenshot />
            </PromptInputActionMenuContent>
          </PromptInputActionMenu>
          <ModelPicker
            gatewayId={modelSelection.data?.gatewayId}
            modelId={modelSelection.data?.modelId}
            gateways={availableModels.gateways}
            models={availableModels.models}
            disabled={busy || modelSelection.isLoading || availableModels.isLoading || updateModel.isPending}
            onChange={(route) => {
              updateModel.mutate(route, {
                onError: (error) => toast.error(error.message),
              })
            }}
          />
        </PromptInputTools>
        <div className="flex items-center gap-1">
          <MicButton value={text} onChange={setText} compact />
          <PromptInputSubmit
            status={isUploading ? 'submitted' : status}
            onStop={stop}
            disabled={isUploading}
            className="rounded-full"
          />
        </div>
      </PromptInputFooter>
    </PromptInput>
  )

  // First visit, zeromail-style: greeting + pill input + quiet chips, all
  // centered mid-screen. The transcript layout only exists once there is one.
  if (messages.length === 0) {
    return (
      <div className={cn(
        'flex min-w-0 flex-1 flex-col items-center justify-center overflow-y-auto',
        compact ? 'gap-4 px-4' : 'gap-5 px-6',
      )}>
        <h1 className={cn('font-semibold tracking-tight', compact ? 'text-2xl' : 'text-3xl')}>
          {greeting()}
        </h1>
        <div className="w-full max-w-2xl">{input}</div>
        {showWaiting && <WaitingMessage />}
        {/* Plain wrap, not the horizontal ScrollArea — zeromail centers chips in rows. */}
        <div className="flex max-w-2xl flex-wrap justify-center gap-2">
          {suggestions.map((text) => (
            <Suggestion
              key={text}
              suggestion={text}
              variant="outline"
              className="bg-transparent text-foreground"
              onClick={(s) => sendMessage({ text: s })}
            />
          ))}
        </div>
      </div>
    )
  }

  return (
    <div className="flex min-w-0 flex-1 flex-col overflow-hidden">
      <Conversation className="min-h-0 flex-1">
        <ConversationContent className={cn(
          'mx-auto w-full max-w-3xl px-4',
          compact ? 'py-4' : 'py-6',
        )}>
          {messages.map((m) => {
            if (!messageHasVisibleOutput(m)) return null
            const toolParts = m.parts.filter(isToolPart)
            const fileParts = m.parts.filter((part): part is FileUIPart => part.type === 'file')
            const sources = uniqueSources(m.parts.filter(isSourcePart))
            const messageText = m.parts
              .filter((part) => part.type === 'text')
              .map((part) => part.text)
              .join('\n')
            const speechTextId = `assistant-speech-${m.id}`
            return (
              <Message from={m.role} key={m.id}>
                {fileParts.length > 0 && (
                  <Attachments variant="grid">
                    {fileParts.map((file, index) => (
                      <Attachment
                        key={`${m.id}-file-${index}`}
                        data={{ ...file, id: `${m.id}-file-${index}` }}
                        className="cursor-pointer"
                        role="button"
                        tabIndex={0}
                        title={file.filename ?? 'Open attachment'}
                        onClick={() => window.open(file.url, '_blank', 'noopener,noreferrer')}
                        onKeyDown={(event) => {
                          if (event.key === 'Enter' || event.key === ' ') window.open(file.url, '_blank', 'noopener,noreferrer')
                        }}
                      >
                        <AttachmentPreview />
                      </Attachment>
                    ))}
                  </Attachments>
                )}
                <MessageContent
                  className={compact
                    ? 'w-full group-[.is-user]:rounded-none group-[.is-user]:bg-transparent group-[.is-user]:p-0'
                    : undefined}
                >
                  {m.role === 'assistant' && toolParts.length > 0 && <ToolWorkflow tools={toolParts} />}
                  {compact && messageText.trim() ? (
                    <Bubble
                      align={m.role === 'user' ? 'end' : 'start'}
                      variant={m.role === 'user' ? 'default' : 'secondary'}
                    >
                      <BubbleContent>
                        <MessageText parts={m.parts} speechTextId={speechTextId} />
                      </BubbleContent>
                    </Bubble>
                  ) : (
                    <MessageText parts={m.parts} speechTextId={speechTextId} />
                  )}
                </MessageContent>
                {sources.length > 0 && (
                  <Sources className="mb-0">
                    <SourcesTrigger count={sources.length} />
                    <SourcesContent>
                      {sources.map((source) => {
                        const href = source.type === 'source-url' ? source.url : source.sourceId
                        const title = source.title ?? href
                        return (
                          <Source
                            key={`${source.type}-${source.sourceId}`}
                            href={href}
                            title={title}
                            target={href.startsWith('/') ? '_self' : '_blank'}
                          />
                        )
                      })}
                    </SourcesContent>
                  </Sources>
                )}
                {messageText.trim() && (
                  m.role === 'assistant' ? (
                    <AssistantMessageActions textElementId={speechTextId} rawText={messageText} />
                  ) : (
                  <MessageActions className="justify-end">
                    <MessageAction
                      tooltip="Copy message"
                      label="Copy message"
                      onClick={() => navigator.clipboard.writeText(messageText)
                        .then(() => toast.success('Message copied'))
                        .catch(() => toast.error('Could not copy message'))}
                    >
                      <Copy className="size-4" />
                    </MessageAction>
                  </MessageActions>
                  )
                )}
              </Message>
            )
          })}
          {showWaiting && <WaitingMessage />}
        </ConversationContent>
        <ConversationScrollButton />
      </Conversation>

      <div className={cn(
        'mx-auto w-full max-w-3xl px-4',
        compact ? 'pb-4' : 'pb-6',
      )}>{input}</div>
    </div>
  )
}

function MessageText({ parts, speechTextId }: { parts: UIMessage['parts']; speechTextId: string }) {
  return (
    <div id={speechTextId}>
      {parts.map((part, index) => {
        if (part.type === 'text') {
          return (
            <MessageResponse key={index} components={CHAT_MARKDOWN_COMPONENTS}>
              {part.text}
            </MessageResponse>
          )
        }
        return null
      })}
    </div>
  )
}

function AssistantMessageActions({ textElementId, rawText }: { textElementId: string; rawText: string }) {
  const synthesize = useSynthesizeSpeech()
  const [asset, setAsset] = useState<SpeechAsset | null>(null)
  const audio = useRef<HTMLAudioElement | null>(null)

  useEffect(() => {
    if (!asset || !audio.current) return
    void audio.current.play().catch(() => {})
  }, [asset])

  function readAloud() {
    if (asset && audio.current) {
      if (audio.current.paused) void audio.current.play().catch(() => {})
      else audio.current.pause()
      return
    }
    const visibleText = document.getElementById(textElementId)?.innerText.trim() || rawText.trim()
    if (!visibleText) return
    if (visibleText.length > 4096) {
      toast.error('This response is too long to read aloud.')
      return
    }
    synthesize.mutate(visibleText, {
      onSuccess: setAsset,
      onError: (error) => toast.error(error.message),
    })
  }

  return (
    <div className="flex max-w-md flex-col items-start gap-2">
      <MessageActions>
        <MessageAction
          tooltip="Read aloud"
          label="Read aloud"
          disabled={synthesize.isPending}
          onClick={readAloud}
        >
          {synthesize.isPending ? <Loader2 className="size-4 animate-spin" /> : <Volume2 className="size-4" />}
        </MessageAction>
        <MessageAction
          tooltip="Copy message"
          label="Copy message"
          onClick={() => navigator.clipboard.writeText(rawText)
            .then(() => toast.success('Message copied'))
            .catch(() => toast.error('Could not copy message'))}
        >
          <Copy className="size-4" />
        </MessageAction>
      </MessageActions>
      {asset && (
        <AudioPlayer className="w-full max-w-xs rounded-md border bg-muted/20 px-1 py-1">
          <AudioPlayerElement ref={audio} src={asset.audioUrl} preload="metadata" />
          <AudioPlayerControlBar className="w-full">
            <AudioPlayerPlayButton />
            <AudioPlayerTimeDisplay />
            <AudioPlayerTimeRange className="min-w-20 flex-1" />
            <AudioPlayerDurationDisplay />
            <AudioPlayerMuteButton />
          </AudioPlayerControlBar>
        </AudioPlayer>
      )}
    </div>
  )
}

function ModelPicker({
  gatewayId,
  modelId,
  gateways,
  models,
  disabled,
  onChange,
}: {
  gatewayId?: string
  modelId?: string
  gateways: Array<{ id: string; displayName: string }>
  models: Array<{ gatewayId: string; id: string; displayName: string }>
  disabled: boolean
  onChange: (route: { gatewayId: string; modelId: string }) => void
}) {
  const [open, setOpen] = useState(false)
  if (!gatewayId || !modelId) {
    return <Loader2 className="mx-2 size-3.5 animate-spin text-muted-foreground" />
  }
  const current = models.find((model) => model.gatewayId === gatewayId && model.id === modelId)
  const currentGateway = gateways.find((gateway) => gateway.id === gatewayId)
  return (
    <ModelSelector open={open} onOpenChange={setOpen}>
      <ModelSelectorTrigger asChild>
        <Button
          type="button"
          variant="ghost"
          size="sm"
          className="h-8 max-w-52 gap-1.5 px-2 text-xs font-normal"
          aria-label="Assistant model"
          title={`Model via ${currentGateway?.displayName ?? gatewayId}`}
          disabled={disabled}
        >
          <ModelProviderMark modelId={modelId} gatewayName={gatewayId} />
          <span className="truncate">{current?.displayName ?? modelId}</span>
          <ChevronsUpDown className="size-3.5 shrink-0 text-muted-foreground" />
        </Button>
      </ModelSelectorTrigger>
      <ModelSelectorContent title="Choose Assistant model" className="sm:max-w-lg">
        <ModelSelectorInput placeholder="Search models..." />
        <ModelSelectorList>
          <ModelSelectorEmpty>No models found.</ModelSelectorEmpty>
          {gateways.map((gateway) => {
            const gatewayModels = models.filter((model) => model.gatewayId === gateway.id)
            if (gatewayModels.length === 0 && gateway.id !== gatewayId) return null
            const items = gatewayModels.length > 0
              ? gatewayModels
              : [{ gatewayId, id: modelId, displayName: modelId }]
            return (
              <ModelSelectorGroup key={gateway.id} heading={gateway.displayName}>
                {items.map((model) => (
                  <ModelSelectorItem
                    key={`${model.gatewayId}:${model.id}`}
                    value={`${gateway.displayName} ${model.displayName} ${model.id}`}
                    onSelect={() => {
                      onChange({ gatewayId: model.gatewayId, modelId: model.id })
                      setOpen(false)
                    }}
                  >
                    <ModelProviderMark modelId={model.id} gatewayName={model.gatewayId} />
                    <ModelSelectorName>{model.displayName}</ModelSelectorName>
                    {model.gatewayId === gatewayId && model.id === modelId && <Check className="size-4" />}
                  </ModelSelectorItem>
                ))}
              </ModelSelectorGroup>
            )
          })}
        </ModelSelectorList>
      </ModelSelectorContent>
    </ModelSelector>
  )
}

function isToolPart(part: UIMessage['parts'][number]): part is ToolUIPart {
  return part.type.startsWith('tool-')
}

function partHasVisibleOutput(part: UIMessage['parts'][number]): boolean {
  if (part.type === 'text') return part.text.trim().length > 0
  if (part.type === 'file') return Boolean((part as FileUIPart).url)
  if (part.type === 'source-url') return Boolean((part as SourceUrlUIPart).url)
  if (part.type === 'source-document') return Boolean((part as SourceDocumentUIPart).sourceId)
  return isToolPart(part)
}

function messageHasVisibleOutput(message: UIMessage): boolean {
  return message.parts.some(partHasVisibleOutput)
}

type SourcePart = SourceUrlUIPart | SourceDocumentUIPart

function isSourcePart(part: UIMessage['parts'][number]): part is SourcePart {
  return part.type === 'source-url' || part.type === 'source-document'
}

function uniqueSources(sources: SourcePart[]) {
  return [...new Map(sources.map((source) => [source.sourceId, source])).values()]
}

function ToolWorkflow({ tools }: { tools: ToolUIPart[] }) {
  const [open, setOpen] = useState(true)
  const complete = tools.filter((tool) => tool.state === 'output-available').length

  return (
    <ChainOfThought open={open} onOpenChange={setOpen} className="mb-3">
      <ChainOfThoughtHeader>
        Workflow · {complete}/{tools.length} complete
      </ChainOfThoughtHeader>
      <ChainOfThoughtContent>
        {tools.map((tool) => {
          const name = toolName(tool)
          const outputLabels = outputPreviewLabels(tool.output)
          return (
            <ChainOfThoughtStep
              key={tool.toolCallId ?? `${tool.type}-${name}`}
              icon={toolIcon(name, tool)}
              label={toolLabel(name)}
              description={toolDescription(tool)}
              status={toolStatus(tool)}
            >
              {outputLabels.length > 0 && (
                <ChainOfThoughtSearchResults>
                  {outputLabels.map((label) => (
                    <ChainOfThoughtSearchResult key={label}>{label}</ChainOfThoughtSearchResult>
                  ))}
                </ChainOfThoughtSearchResults>
              )}
              <Tool className="mb-0 border-border/60 bg-background/60">
                <ToolHeader title="Details" type={tool.type} state={tool.state} className="px-3 py-2" />
                <ToolContent className="space-y-3 px-3 py-3">
                  {tool.input !== undefined && <ToolInput input={tool.input} />}
                  <ToolOutput output={tool.output} errorText={tool.errorText} />
                </ToolContent>
              </Tool>
            </ChainOfThoughtStep>
          )
        })}
      </ChainOfThoughtContent>
    </ChainOfThought>
  )
}

function toolName(tool: ToolUIPart): string {
  return tool.type.replace(/^tool-/, '')
}

function toolLabel(name: string): string {
  if (name === 'search_web') return 'Search the web'
  if (name === 'read_web_page') return 'Read web page'
  const normalized = name.replaceAll('_', ' ')
  if (name.includes('search') || name.includes('find')) return `Search ${targetLabel(name)}`
  if (name.startsWith('create')) return `Create ${targetLabel(name)}`
  if (name.startsWith('update')) return `Update ${targetLabel(name)}`
  if (name.startsWith('delete')) return `Delete ${targetLabel(name)}`
  if (name.startsWith('list')) return `List ${targetLabel(name)}`
  if (name.startsWith('set')) return `Set ${targetLabel(name)}`
  return normalized.charAt(0).toUpperCase() + normalized.slice(1)
}

function targetLabel(name: string): string {
  if (name.includes('web')) return 'the web'
  if (name.includes('task')) return 'tasks'
  if (name.includes('note') || name.includes('knowledge')) return 'notes'
  if (name.includes('event') || name.includes('calendar') || name.includes('slot')) return 'calendar'
  if (name.includes('project') || name.includes('milestone')) return 'projects'
  if (name.includes('discipline')) return 'disciplines'
  if (name.includes('review')) return 'review'
  return name.replaceAll('_', ' ')
}

function toolIcon(name: string, tool: ToolUIPart): LucideIcon {
  if (tool.state === 'output-error' || tool.errorText) return XCircle
  if (tool.state === 'output-available') return CheckCircle2
  if (name === 'search_web' || name === 'read_web_page') return Globe2
  if (name.includes('search') || name.includes('find')) return Search
  if (name.includes('event') || name.includes('calendar') || name.includes('slot')) return CalendarDays
  if (name.includes('note') || name.includes('knowledge') || name.includes('review')) return NotebookText
  return Wrench
}

function toolStatus(tool: ToolUIPart): 'complete' | 'active' | 'pending' {
  if (tool.state === 'output-available' || tool.state === 'output-error' || tool.errorText) return 'complete'
  if (tool.state === 'input-available' || tool.state === 'input-streaming') return 'active'
  return 'pending'
}

function toolDescription(tool: ToolUIPart): string {
  if (tool.errorText) return tool.errorText
  const input = inputSummary(tool.input)
  const result = resultSummary(tool.output)
  if (input && result) return `${input} · ${result}`
  return input || result || toolStateLabel(tool.state)
}

function toolStateLabel(state: ToolUIPart['state']): string {
  if (state === 'input-streaming') return 'Preparing input'
  if (state === 'input-available') return 'Running'
  if (state === 'output-available') return 'Done'
  if (state === 'approval-requested') return 'Waiting for approval'
  if (state === 'approval-responded') return 'Approval recorded'
  if (state === 'output-denied') return 'Denied'
  if (state === 'output-error') return 'Error'
  return 'Pending'
}

function inputSummary(input: unknown): string {
  if (input == null) return ''
  if (typeof input === 'string') return truncate(input)
  if (!isRecord(input)) return ''

  const keys = [
    'query',
    'q',
    'text',
    'title',
    'date',
    'days',
    'taskId',
    'noteSlug',
    'projectId',
    'disciplineName',
    'folderPath',
  ]
  const parts = keys
    .map((key) => [key, input[key]] as const)
    .filter((entry): entry is readonly [string, string | number | boolean] =>
      typeof entry[1] === 'string' || typeof entry[1] === 'number' || typeof entry[1] === 'boolean',
    )
    .slice(0, 3)
    .map(([key, value]) => `${humanize(key)}: ${truncate(String(value), 80)}`)

  return parts.join(' · ')
}

function resultSummary(output: unknown): string {
  if (output == null) return ''
  const count = collectionCount(output)
  if (count !== null) return `${count} result${count === 1 ? '' : 's'}`
  if (typeof output === 'string') return truncate(output, 120)
  if (isRecord(output)) {
    const title = firstString(output, ['title', 'name', 'message', 'summary', 'status'])
    if (title) return truncate(title, 120)
  }
  return 'Done'
}

function outputPreviewLabels(output: unknown): string[] {
  const items = outputItems(output)
  return items
    .map((item) => (isRecord(item) ? firstString(item, ['title', 'name', 'subject', 'slug', 'id']) : ''))
    .filter(Boolean)
    .slice(0, 5)
    .map((label) => truncate(label, 44))
}

function outputItems(output: unknown): unknown[] {
  if (Array.isArray(output)) return output
  if (!isRecord(output)) return []
  for (const key of ['sources', 'results', 'items', 'content', 'tasks', 'events', 'notes', 'projects']) {
    const value = output[key]
    if (Array.isArray(value)) return value
  }
  return []
}

function collectionCount(value: unknown): number | null {
  if (Array.isArray(value)) return value.length
  if (!isRecord(value)) return null
  for (const key of ['sources', 'results', 'items', 'content', 'tasks', 'events', 'notes', 'projects']) {
    const nested = value[key]
    if (Array.isArray(nested)) return nested.length
  }
  return null
}

function firstString(record: Record<string, unknown>, keys: string[]): string {
  for (const key of keys) {
    const value = record[key]
    if (typeof value === 'string' && value.trim()) return value.trim()
  }
  return ''
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function humanize(key: string): string {
  return key
    .replace(/Id$/, '')
    .replace(/([a-z])([A-Z])/g, '$1 $2')
    .replaceAll('_', ' ')
    .toLowerCase()
}

function truncate(value: string, max = 100): string {
  return value.length <= max ? value : `${value.slice(0, max - 1)}…`
}

/**
 * Stores one composer image in the attachment vault (V16) and rewrites its
 * part to the durable /api/files URL the api and history both understand.
 */
async function uploadImage(file: FileUIPart): Promise<FileUIPart> {
  const blob = await fetch(file.url).then((r) => r.blob())
  const meta = await uploadFile(blob, file.filename ?? 'image')
  return { ...file, url: fileUrl(meta.id) }
}

/** Thumbnails of images queued for the next message, each removable. */
function AttachmentStrip() {
  const attachments = usePromptInputAttachments()
  if (attachments.files.length === 0) return null
  return (
    <Attachments variant="grid" className="ml-0 w-full justify-start px-3 pt-3">
      {attachments.files.map((file) => (
        <Attachment key={file.id} data={file} onRemove={() => attachments.remove(file.id)} className="size-14">
          <AttachmentPreview />
          <AttachmentRemove label={`Remove ${file.filename ?? 'image'}`} className="-right-1.5 -top-1.5" />
        </Attachment>
      ))}
    </Attachments>
  )
}

function WaitingMessage(): ReactNode {
  return (
    <Message from="assistant">
      <MessageContent>
        <span className="flex items-center gap-2 py-1 text-sm text-muted-foreground">
          <Loader2 className="size-4 animate-spin" aria-hidden="true" />
          Thinking…
        </span>
      </MessageContent>
    </Message>
  )
}
