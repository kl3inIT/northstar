import { Bot } from 'lucide-react'
import { ModelSelectorLogo } from '@/components/ai-elements/model-selector'

export function ModelProviderMark({ modelId, gatewayName }: { modelId: string; gatewayName?: string }) {
  const provider = modelLogoProvider(modelId, gatewayName)
  if (!provider) {
    return (
      <span className="flex size-4 shrink-0 items-center justify-center text-muted-foreground" aria-hidden="true">
        <Bot className="size-3.5" />
      </span>
    )
  }
  return <ModelSelectorLogo provider={provider} className="size-4 shrink-0" aria-hidden="true" />
}

function modelLogoProvider(modelId: string, gatewayName?: string): string | undefined {
  const id = modelId.toLowerCase()
  const providers: Array<[RegExp, string]> = [
    [/claude|anthropic/, 'anthropic'],
    [/(^|[/:_-])gpt|(^|[/:_-])o[134](?:$|[/:_-])|openai|codex/, 'openai'],
    [/gemini|google/, 'google'],
    [/deepseek/, 'deepseek'],
    [/grok|(^|[/:_-])xai(?:$|[/:_-])/, 'xai'],
    [/mistral|mixtral/, 'mistral'],
    [/llama|(^|[/:_-])meta(?:$|[/:_-])/, 'llama'],
    [/qwen|alibaba/, 'alibaba'],
    [/kimi|moonshot/, 'moonshotai'],
    [/perplexity/, 'perplexity'],
    [/openrouter/, 'openrouter'],
    [/groq/, 'groq'],
    [/azure/, 'azure'],
    [/hugging[ -]?face/, 'huggingface'],
    [/nvidia/, 'nvidia'],
    [/cerebras/, 'cerebras'],
    [/bedrock|amazon/, 'amazon-bedrock'],
    [/together/, 'togetherai'],
    [/fireworks/, 'fireworks-ai'],
  ]
  const matched = providers.find(([pattern]) => pattern.test(id))
  if (matched) return matched[1]
  return gatewayName?.toLowerCase() === 'openai' ? 'openai' : undefined
}
