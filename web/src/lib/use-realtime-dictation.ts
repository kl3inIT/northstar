import { useRef, useState } from 'react'

/**
 * Live dictation ("nói đến đâu chữ lên đến đó") over the OpenAI Realtime API:
 * the api mints an ephemeral client secret (/api/capture/realtime-session),
 * the browser opens a WebSocket STRAIGHT to OpenAI (audio never touches our
 * server, real key never reaches the browser) and streams mic PCM16 @ 24kHz;
 * transcription deltas append into the composer as they arrive, and the
 * `completed` transcript replaces the accumulated deltas with the polished
 * sentence when the user stops.
 */
export type DictationState = 'idle' | 'connecting' | 'live' | 'finishing'

/** AudioWorklet: batch mic float32 frames into 100ms Int16 PCM chunks. */
const WORKLET_SRC = `
class Pcm16Capture extends AudioWorkletProcessor {
  constructor() { super(); this.buf = []; this.len = 0; }
  process(inputs) {
    const ch = inputs[0] && inputs[0][0];
    if (!ch) return true;
    this.buf.push(new Float32Array(ch)); this.len += ch.length;
    if (this.len >= 2400) {
      const all = new Float32Array(this.len); let o = 0;
      for (const b of this.buf) { all.set(b, o); o += b.length; }
      const out = new Int16Array(all.length);
      for (let i = 0; i < all.length; i++) {
        const s = Math.max(-1, Math.min(1, all[i]));
        out[i] = s < 0 ? s * 0x8000 : s * 0x7fff;
      }
      this.port.postMessage(out.buffer, [out.buffer]);
      this.buf = []; this.len = 0;
    }
    return true;
  }
}
registerProcessor('pcm16-capture', Pcm16Capture);
`

function toBase64(buffer: ArrayBuffer): string {
  const bytes = new Uint8Array(buffer)
  let bin = ''
  for (let i = 0; i < bytes.length; i += 0x8000) {
    bin += String.fromCharCode(...bytes.subarray(i, i + 0x8000))
  }
  return btoa(bin)
}

function join(base: string, segment: string): string {
  if (!base) return segment
  if (!segment) return base
  return `${base} ${segment}`
}

export function useRealtimeDictation(
  onChange: (text: string) => void,
  onError: (message: string) => void,
) {
  const [state, setState] = useState<DictationState>('idle')
  const [seconds, setSeconds] = useState(0)
  const wsRef = useRef<WebSocket | null>(null)
  const ctxRef = useRef<AudioContext | null>(null)
  const streamRef = useRef<MediaStream | null>(null)
  const timerRef = useRef<number | null>(null)
  const finishTimerRef = useRef<number | null>(null)
  const baseRef = useRef('')
  const segRef = useRef('')

  function cleanup() {
    if (timerRef.current !== null) window.clearInterval(timerRef.current)
    if (finishTimerRef.current !== null) window.clearTimeout(finishTimerRef.current)
    timerRef.current = null
    finishTimerRef.current = null
    streamRef.current?.getTracks().forEach((t) => t.stop())
    streamRef.current = null
    void ctxRef.current?.close().catch(() => {})
    ctxRef.current = null
    const ws = wsRef.current
    wsRef.current = null
    if (ws && ws.readyState <= WebSocket.OPEN) ws.close()
    setState('idle')
  }

  async function start(baseText: string) {
    setState('connecting')
    try {
      const res = await fetch('/api/capture/realtime-session', { method: 'POST' })
      if (!res.ok) throw new Error(`mint failed: ${res.status}`)
      const { clientSecret } = (await res.json()) as { clientSecret: string }

      const stream = await navigator.mediaDevices.getUserMedia({ audio: true })
      streamRef.current = stream
      // 24kHz context ==> the worklet's PCM already matches the session format.
      const ctx = new AudioContext({ sampleRate: 24000 })
      ctxRef.current = ctx
      await ctx.audioWorklet.addModule(
        URL.createObjectURL(new Blob([WORKLET_SRC], { type: 'text/javascript' })),
      )

      // Browser WebSockets cannot set headers; the ephemeral secret rides as a
      // subprotocol (the only variant OpenAI accepts — probed, not guessed).
      const ws = new WebSocket('wss://api.openai.com/v1/realtime', [
        'realtime',
        `openai-insecure-api-key.${clientSecret}`,
      ])
      wsRef.current = ws

      ws.onopen = () => {
        const source = ctx.createMediaStreamSource(stream)
        const node = new AudioWorkletNode(ctx, 'pcm16-capture')
        node.port.onmessage = (e) => {
          if (ws.readyState === WebSocket.OPEN) {
            ws.send(
              JSON.stringify({ type: 'input_audio_buffer.append', audio: toBase64(e.data as ArrayBuffer) }),
            )
          }
        }
        source.connect(node) // not wired to destination: no echo
        baseRef.current = baseText.trim()
        segRef.current = ''
        setSeconds(0)
        timerRef.current = window.setInterval(() => setSeconds((s) => s + 1), 1000)
        setState('live')
      }

      ws.onmessage = (e) => {
        const ev = JSON.parse(e.data as string) as { type: string; delta?: string; transcript?: string; error?: { message?: string } }
        if (ev.type === 'conversation.item.input_audio_transcription.delta' && ev.delta) {
          segRef.current += ev.delta
          onChange(join(baseRef.current, segRef.current.trim()))
        } else if (ev.type === 'conversation.item.input_audio_transcription.completed') {
          baseRef.current = join(baseRef.current, (ev.transcript ?? segRef.current).trim())
          segRef.current = ''
          onChange(baseRef.current)
          cleanup()
        } else if (ev.type === 'error') {
          onError(ev.error?.message ?? 'Realtime session error')
          cleanup()
        }
      }

      ws.onclose = () => {
        if (wsRef.current === ws) cleanup()
      }
    } catch {
      cleanup()
      onError('Không mở được phiên dictation — kiểm tra mic/mạng rồi thử lại.')
    }
  }

  /** Stop the mic, commit the buffer, let `completed` deliver the polished text. */
  function stop() {
    setState('finishing')
    if (timerRef.current !== null) window.clearInterval(timerRef.current)
    streamRef.current?.getTracks().forEach((t) => t.stop())
    const ws = wsRef.current
    if (ws && ws.readyState === WebSocket.OPEN) {
      ws.send(JSON.stringify({ type: 'input_audio_buffer.commit' }))
      // Safety net: if `completed` never arrives, keep the deltas we already have.
      finishTimerRef.current = window.setTimeout(cleanup, 4000)
    } else {
      cleanup()
    }
  }

  return { state, seconds, start, stop }
}
