import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useChat } from '@ai-sdk/react'
import { DefaultChatTransport, type ToolUIPart, type UIMessage } from 'ai'
import { Loader2, MessageSquarePlus, Trash2 } from 'lucide-react'
import { useEffect, useRef, useState, type ReactNode } from 'react'
import { toast } from 'sonner'
import {
  Conversation,
  ConversationContent,
  ConversationScrollButton,
} from '@/components/ai-elements/conversation'
import { Message, MessageContent, MessageResponse } from '@/components/ai-elements/message'
import {
  PromptInput,
  PromptInputBody,
  PromptInputFooter,
  PromptInputSubmit,
  PromptInputTextarea,
  type PromptInputMessage,
} from '@/components/ai-elements/prompt-input'
import { Suggestion } from '@/components/ai-elements/suggestion'
import { Tool, ToolContent, ToolHeader, ToolInput, ToolOutput } from '@/components/ai-elements/tool'
import { Button } from '@/components/ui/button'
import { useStagingCount } from '@/lib/notes-api'
import { useTodayTasks } from '@/lib/tasks-api'
import { cn } from '@/lib/utils'

/**
 * The in-app assistant, organized the way zeromail's chat is: a borderless
 * center column (greeting + pill input + quiet suggestion chips on first
 * visit, then plain-text answers with minimal tool rows), and a chat-history
 * panel on the RIGHT titled by each conversation's first message. useChat
 * speaks the AI SDK UI Message Stream the api emits; conversations are
 * durable (JDBC memory) and /history rehydrates on switch.
 */

/** Kills the InputGroup chrome: zeromail's input is a soft pill, no border, no ring. */
const PILL_INPUT =
  'w-full [&_[data-slot=input-group]]:rounded-3xl [&_[data-slot=input-group]]:!border-transparent ' +
  '[&_[data-slot=input-group]]:bg-muted/60 [&_[data-slot=input-group]]:shadow-none ' +
  '[&_[data-slot=input-group]]:!ring-0'

interface ConversationSummary {
  id: string
  title: string
  lastAt: string
  messages: number
}

interface HistoryMessage {
  role: 'user' | 'assistant'
  text: string
}

async function fetchConversations(): Promise<ConversationSummary[]> {
  const res = await fetch('/api/assistant/conversations')
  if (!res.ok) return []
  return (await res.json()) as ConversationSummary[]
}

async function fetchHistory(conversationId: string): Promise<UIMessage[]> {
  const res = await fetch(`/api/assistant/history?conversationId=${conversationId}`)
  if (!res.ok) return []
  const messages = (await res.json()) as HistoryMessage[]
  return messages.map((m, i) => ({
    id: `history-${i}`,
    role: m.role,
    parts: [{ type: 'text', text: m.text }],
  }))
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

export function AssistantPage() {
  const queryClient = useQueryClient()
  const { data: conversations = [], isPending } = useQuery({
    queryKey: ['assistant-conversations'],
    queryFn: fetchConversations,
  })
  const [conversationId, setConversationId] = useState<string | null>(null)
  // Once, on load: resume the most recent conversation, else start fresh.
  const bootstrapped = useRef(false)
  useEffect(() => {
    if (bootstrapped.current || isPending) return
    bootstrapped.current = true
    setConversationId(conversations[0]?.id ?? crypto.randomUUID())
  }, [isPending, conversations])

  function remove(id: string) {
    fetch(`/api/assistant/conversations/${id}`, { method: 'DELETE' })
      .then(() => {
        queryClient.invalidateQueries({ queryKey: ['assistant-conversations'] })
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
    <div className="flex h-full w-full flex-1 overflow-hidden">
      <ChatColumn key={conversationId} conversationId={conversationId} />

      <aside className="hidden w-72 shrink-0 flex-col border-l lg:flex">
        <div className="flex items-center justify-between px-4 pb-2 pt-4">
          <h2 className="text-sm font-semibold">Chat history</h2>
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
        </div>
        <div className="min-h-0 flex-1 overflow-y-auto px-2 pb-3">
          {conversations.length === 0 && (
            <p className="px-2 py-1 text-xs text-muted-foreground">No conversations yet.</p>
          )}
          {conversations.map((c) => (
            <div
              key={c.id}
              className={cn(
                'group flex w-full items-center gap-1 rounded-lg',
                c.id === conversationId ? 'bg-muted' : 'hover:bg-muted/50',
              )}
            >
              <button
                type="button"
                onClick={() => setConversationId(c.id)}
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
                onClick={() => remove(c.id)}
              >
                <Trash2 className="size-3.5 text-muted-foreground" />
              </Button>
            </div>
          ))}
        </div>
      </aside>
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
function ChatColumn({ conversationId }: { conversationId: string }) {
  const { data: history, isPending } = useQuery({
    queryKey: ['assistant-history', conversationId],
    queryFn: () => fetchHistory(conversationId),
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
  return <AssistantChat conversationId={conversationId} initialMessages={history ?? []} />
}

function AssistantChat({
  conversationId,
  initialMessages,
}: {
  conversationId: string
  initialMessages: UIMessage[]
}) {
  const suggestions = useSuggestions()
  const queryClient = useQueryClient()
  const { messages, sendMessage, status } = useChat({
    messages: initialMessages,
    onFinish: () => {
      // A finished turn may have created/completed things and retitles the list.
      queryClient.invalidateQueries({ queryKey: ['assistant-conversations'] })
      queryClient.invalidateQueries({ queryKey: ['tasks'] })
      queryClient.invalidateQueries({ queryKey: ['notes'] })
      queryClient.removeQueries({ queryKey: ['assistant-history', conversationId] })
    },
    transport: new DefaultChatTransport({
      api: '/api/assistant/chat',
      prepareSendMessagesRequest: ({ messages }) => {
        const last = messages.at(-1)
        const text = (last?.parts ?? [])
          .filter((p) => p.type === 'text')
          .map((p) => (p as { text: string }).text)
          .join('')
        return { body: { message: text, conversationId } }
      },
    }),
  })

  function onSubmit(message: PromptInputMessage) {
    const text = message.text.trim()
    if (!text) return
    sendMessage({ text })
  }

  const input = (
    <PromptInput onSubmit={onSubmit} className={PILL_INPUT}>
      <PromptInputBody>
        <PromptInputTextarea placeholder="Message Northstar…" autoFocus className="min-h-12" />
      </PromptInputBody>
      <PromptInputFooter>
        <span />
        <PromptInputSubmit status={status} className="rounded-full" />
      </PromptInputFooter>
    </PromptInput>
  )

  // First visit, zeromail-style: greeting + pill input + quiet chips, all
  // centered mid-screen. The transcript layout only exists once there is one.
  if (messages.length === 0) {
    return (
      <div className="flex min-w-0 flex-1 flex-col items-center justify-center gap-5 overflow-y-auto px-6">
        <h1 className="text-3xl font-semibold tracking-tight">{greeting()}</h1>
        <div className="w-full max-w-2xl">{input}</div>
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
        <ConversationContent className="mx-auto w-full max-w-3xl px-4 py-6">
          {messages.map((m) => (
            <Message from={m.role} key={m.id}>
              <MessageContent>
                {m.parts.map((part, i) => {
                  if (part.type === 'text') {
                    return <MessageResponse key={i}>{part.text}</MessageResponse>
                  }
                  if (part.type.startsWith('tool-')) {
                    const tool = part as ToolUIPart
                    return (
                      // zeromail's tool row: a quiet one-liner, no card chrome.
                      <Tool key={i} className="mb-0 border-0 bg-transparent">
                        <ToolHeader
                          type={tool.type}
                          state={tool.state}
                          className="px-0 py-1 text-muted-foreground"
                        />
                        <ToolContent>
                          {tool.input !== undefined && <ToolInput input={tool.input} />}
                          <ToolOutput output={tool.output} errorText={tool.errorText} />
                        </ToolContent>
                      </Tool>
                    )
                  }
                  return null
                })}
              </MessageContent>
            </Message>
          ))}
          {status === 'submitted' && <PendingDots />}
        </ConversationContent>
        <ConversationScrollButton />
      </Conversation>

      <div className="mx-auto w-full max-w-3xl px-4 pb-6">{input}</div>
    </div>
  )
}

function PendingDots(): ReactNode {
  return (
    <Message from="assistant">
      <MessageContent>
        <span className="flex items-center gap-1 py-1">
          {[0, 0.16, 0.32].map((delay) => (
            <span
              key={delay}
              className="size-1.5 animate-bounce rounded-full bg-muted-foreground/60"
              style={{ animationDelay: `${delay}s` }}
            />
          ))}
        </span>
      </MessageContent>
    </Message>
  )
}
