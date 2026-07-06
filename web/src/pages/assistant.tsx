import { useQuery } from '@tanstack/react-query'
import { useChat } from '@ai-sdk/react'
import { DefaultChatTransport, type ToolUIPart, type UIMessage } from 'ai'
import { Bot, CalendarSearch, ListTodo, Loader2, Sparkles } from 'lucide-react'
import {
  Conversation,
  ConversationContent,
  ConversationEmptyState,
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
import { Suggestion, Suggestions } from '@/components/ai-elements/suggestion'
import { Tool, ToolContent, ToolHeader, ToolInput, ToolOutput } from '@/components/ai-elements/tool'
import { useStagingCount } from '@/lib/notes-api'
import { useTodayTasks } from '@/lib/tasks-api'

/**
 * The in-app assistant: useChat speaks the AI SDK UI Message Stream the api
 * emits (/api/assistant/chat), so text streams token-by-token and every tool
 * call renders live via the ai-elements Tool component. One durable
 * conversation ("default") — server-side JDBC memory carries context across
 * restarts, /history rehydrates the transcript on load.
 */
const CONVERSATION_ID = 'default'

interface Suggestion {
  icon: typeof ListTodo
  text: string
}

/**
 * Empty-state suggestions grounded in the user's actual state — a pile of
 * overdue tasks or a waiting Staging queue beats a generic "ask me anything".
 */
function useSuggestions(): Suggestion[] {
  const { data: today = [] } = useTodayTasks()
  const { data: stagingCount = 0 } = useStagingCount()

  const overdue = today.filter((t) => {
    if (t.status !== 'OPEN' || !t.dueDate) return false
    return t.dueDate < new Date().toISOString().slice(0, 10)
  }).length

  const suggestions: Suggestion[] = []
  if (overdue > 0) {
    suggestions.push({
      icon: ListTodo,
      text: `Help me triage my ${overdue} overdue task${overdue === 1 ? '' : 's'}`,
    })
  } else if (today.some((t) => t.status === 'OPEN')) {
    suggestions.push({ icon: ListTodo, text: 'What should I focus on today?' })
  }
  if (stagingCount > 0) {
    suggestions.push({
      icon: Sparkles,
      text: `Summarize the ${stagingCount} note${stagingCount === 1 ? '' : 's'} waiting in Staging`,
    })
  }
  suggestions.push({ icon: CalendarSearch, text: 'Find me a free 90-minute study slot tomorrow' })
  if (suggestions.length < 3) {
    suggestions.push({ icon: Sparkles, text: "What's on my calendar this week?" })
  }
  return suggestions.slice(0, 3)
}

interface HistoryMessage {
  role: 'user' | 'assistant'
  text: string
}

async function fetchHistory(): Promise<UIMessage[]> {
  const res = await fetch(`/api/assistant/history?conversationId=${CONVERSATION_ID}`)
  if (!res.ok) return []
  const messages = (await res.json()) as HistoryMessage[]
  return messages.map((m, i) => ({
    id: `history-${i}`,
    role: m.role,
    parts: [{ type: 'text', text: m.text }],
  }))
}

export function AssistantPage() {
  const { data: history, isPending } = useQuery({
    queryKey: ['assistant-history'],
    queryFn: fetchHistory,
    staleTime: Infinity,
    refetchOnWindowFocus: false,
  })

  if (isPending) {
    return (
      <div className="flex w-full flex-1 items-center justify-center">
        <Loader2 className="size-5 animate-spin text-muted-foreground" />
      </div>
    )
  }
  return <AssistantChat initialMessages={history ?? []} />
}

function AssistantChat({ initialMessages }: { initialMessages: UIMessage[] }) {
  const suggestions = useSuggestions()
  const { messages, sendMessage, status } = useChat({
    messages: initialMessages,
    transport: new DefaultChatTransport({
      api: '/api/assistant/chat',
      prepareSendMessagesRequest: ({ messages }) => {
        const last = messages.at(-1)
        const text = (last?.parts ?? [])
          .filter((p) => p.type === 'text')
          .map((p) => (p as { text: string }).text)
          .join('')
        return { body: { message: text, conversationId: CONVERSATION_ID } }
      },
    }),
  })

  function onSubmit(message: PromptInputMessage) {
    const text = message.text.trim()
    if (!text) return
    sendMessage({ text })
  }

  return (
    <div className="flex h-full w-full flex-1 flex-col gap-3 overflow-hidden px-10 py-6">
      <div className="flex items-center gap-2">
        <h1 className="text-3xl font-bold tracking-tight">Assistant</h1>
        <Bot className="size-5 text-primary" />
      </div>
      <p className="text-sm text-muted-foreground">
        Talks to your real tasks, notes and calendar — same tools external agents get over MCP.
      </p>

      <Conversation className="min-h-0 flex-1 rounded-xl border bg-card">
        <ConversationContent>
          {messages.length === 0 ? (
            <ConversationEmptyState
              icon={<Bot className="size-6" />}
              title="Ask about your day"
              description="It reads and writes your actual tasks, notes and calendar."
            >
              <Suggestions className="mt-2">
                {suggestions.map(({ icon: Icon, text }) => (
                  <Suggestion
                    key={text}
                    suggestion={text}
                    onClick={(s) => sendMessage({ text: s })}
                  >
                    <Icon className="size-3.5" /> {text}
                  </Suggestion>
                ))}
              </Suggestions>
            </ConversationEmptyState>
          ) : (
            messages.map((m) => (
              <Message from={m.role} key={m.id}>
                <MessageContent>
                  {m.parts.map((part, i) => {
                    if (part.type === 'text') {
                      return <MessageResponse key={i}>{part.text}</MessageResponse>
                    }
                    if (part.type.startsWith('tool-')) {
                      const tool = part as ToolUIPart
                      return (
                        <Tool key={i}>
                          <ToolHeader type={tool.type} state={tool.state} />
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
            ))
          )}
        </ConversationContent>
        <ConversationScrollButton />
      </Conversation>

      <PromptInput onSubmit={onSubmit}>
        <PromptInputBody>
          <PromptInputTextarea placeholder="Ask, plan, or book something…" autoFocus />
        </PromptInputBody>
        <PromptInputFooter>
          <span className="hidden text-xs text-muted-foreground sm:block">
            Enter to send · Shift+Enter for a new line
          </span>
          <PromptInputSubmit status={status} />
        </PromptInputFooter>
      </PromptInput>
    </div>
  )
}
