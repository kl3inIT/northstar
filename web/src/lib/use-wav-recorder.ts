import { useEffect, useRef, useState } from 'react'

export type WavRecorderState = 'idle' | 'requesting' | 'recording' | 'ready'

const TARGET_SAMPLE_RATE = 16_000
const WORKLET_SOURCE = `
class NorthstarWavCapture extends AudioWorkletProcessor {
  process(inputs) {
    const channel = inputs[0] && inputs[0][0];
    if (channel) {
      const copy = new Float32Array(channel);
      this.port.postMessage(copy.buffer, [copy.buffer]);
    }
    return true;
  }
}
registerProcessor('northstar-wav-capture', NorthstarWavCapture);
`

function downsample(input: Float32Array, sourceRate: number): Float32Array {
  if (sourceRate === TARGET_SAMPLE_RATE) return input
  const ratio = sourceRate / TARGET_SAMPLE_RATE
  const output = new Float32Array(Math.floor(input.length / ratio))
  for (let index = 0; index < output.length; index += 1) {
    const start = Math.floor(index * ratio)
    const end = Math.max(start + 1, Math.floor((index + 1) * ratio))
    let sum = 0
    for (let source = start; source < end && source < input.length; source += 1) {
      sum += input[source]
    }
    output[index] = sum / (end - start)
  }
  return output
}

function encodeWav(samples: Float32Array): Blob {
  const buffer = new ArrayBuffer(44 + samples.length * 2)
  const view = new DataView(buffer)
  const text = (offset: number, value: string) => {
    for (let index = 0; index < value.length; index += 1) view.setUint8(offset + index, value.charCodeAt(index))
  }
  text(0, 'RIFF')
  view.setUint32(4, 36 + samples.length * 2, true)
  text(8, 'WAVE')
  text(12, 'fmt ')
  view.setUint32(16, 16, true)
  view.setUint16(20, 1, true)
  view.setUint16(22, 1, true)
  view.setUint32(24, TARGET_SAMPLE_RATE, true)
  view.setUint32(28, TARGET_SAMPLE_RATE * 2, true)
  view.setUint16(32, 2, true)
  view.setUint16(34, 16, true)
  text(36, 'data')
  view.setUint32(40, samples.length * 2, true)
  for (let index = 0; index < samples.length; index += 1) {
    const sample = Math.max(-1, Math.min(1, samples[index]))
    view.setInt16(44 + index * 2, sample < 0 ? sample * 0x8000 : sample * 0x7fff, true)
  }
  return new Blob([buffer], { type: 'audio/wav' })
}

export function useWavRecorder(maximumSeconds = 60) {
  const [state, setState] = useState<WavRecorderState>('idle')
  const [seconds, setSeconds] = useState(0)
  const [audio, setAudio] = useState<Blob | null>(null)
  const [error, setError] = useState<string | null>(null)
  const contextRef = useRef<AudioContext | null>(null)
  const streamRef = useRef<MediaStream | null>(null)
  const nodeRef = useRef<AudioWorkletNode | null>(null)
  const chunksRef = useRef<Float32Array[]>([])
  const sampleRateRef = useRef(48_000)
  const startedAtRef = useRef(0)
  const timerRef = useRef<number | null>(null)

  function release() {
    if (timerRef.current !== null) window.clearInterval(timerRef.current)
    timerRef.current = null
    nodeRef.current?.disconnect()
    nodeRef.current = null
    streamRef.current?.getTracks().forEach((track) => track.stop())
    streamRef.current = null
    void contextRef.current?.close().catch(() => {})
    contextRef.current = null
  }

  function finish() {
    if (contextRef.current === null) return
    const sampleCount = chunksRef.current.reduce((total, chunk) => total + chunk.length, 0)
    const joined = new Float32Array(sampleCount)
    let offset = 0
    for (const chunk of chunksRef.current) {
      joined.set(chunk, offset)
      offset += chunk.length
    }
    release()
    if (joined.length === 0) {
      setError('No microphone audio was captured. Try recording again.')
      setState('idle')
      return
    }
    setAudio(encodeWav(downsample(joined, sampleRateRef.current)))
    setState('ready')
  }

  async function start() {
    release()
    setState('requesting')
    setAudio(null)
    setError(null)
    setSeconds(0)
    chunksRef.current = []
    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        audio: {
          echoCancellation: true,
          noiseSuppression: true,
          autoGainControl: true,
        },
      })
      const context = new AudioContext()
      streamRef.current = stream
      contextRef.current = context
      sampleRateRef.current = context.sampleRate
      const moduleUrl = URL.createObjectURL(new Blob([WORKLET_SOURCE], { type: 'text/javascript' }))
      try {
        await context.audioWorklet.addModule(moduleUrl)
      } finally {
        URL.revokeObjectURL(moduleUrl)
      }
      const source = context.createMediaStreamSource(stream)
      const node = new AudioWorkletNode(context, 'northstar-wav-capture')
      nodeRef.current = node
      node.port.onmessage = (event: MessageEvent<ArrayBuffer>) => {
        chunksRef.current.push(new Float32Array(event.data))
      }
      source.connect(node)
      startedAtRef.current = performance.now()
      setState('recording')
      timerRef.current = window.setInterval(() => {
        const elapsed = Math.min(maximumSeconds, (performance.now() - startedAtRef.current) / 1000)
        setSeconds(elapsed)
        if (elapsed >= maximumSeconds) finish()
      }, 100)
      return true
    } catch {
      release()
      setState('idle')
      setError('Microphone access failed. Check browser permission and try again.')
      return false
    }
  }

  function reset() {
    release()
    chunksRef.current = []
    setAudio(null)
    setSeconds(0)
    setError(null)
    setState('idle')
  }

  useEffect(() => release, [])

  return { state, seconds, audio, error, start, stop: finish, reset }
}
