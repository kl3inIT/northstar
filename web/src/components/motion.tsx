import { useEffect, useRef, useState, type ReactNode } from 'react'
import { LazyMotion, MotionConfig } from 'motion/react'
import { cn } from '@/lib/utils'
import { m } from '@/components/motion-primitives'

const loadFeatures = () => import('./motion-features').then((module) => module.default)
const easeOut = [0.16, 1, 0.3, 1] as const

export function MotionProvider({ children }: { children: ReactNode }) {
  return (
    <LazyMotion features={loadFeatures} strict>
      <MotionConfig reducedMotion="user" transition={{ duration: 0.18, ease: easeOut }}>
        {children}
      </MotionConfig>
    </LazyMotion>
  )
}

export function PageTransition({ children, className }: { children: ReactNode; className?: string }) {
  return (
    <m.div
      initial={{ opacity: 0, y: 4 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.18, ease: easeOut }}
      className={cn('min-w-0', className)}
    >
      {children}
    </m.div>
  )
}

export function CountUp({ value, format }: { value: number; format: (value: number) => string }) {
  const previous = useRef(0)
  const [display, setDisplay] = useState(value)

  useEffect(() => {
    const start = previous.current
    previous.current = value
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
      setDisplay(value)
      return
    }
    const startedAt = performance.now()
    let frame = 0
    const tick = (now: number) => {
      const progress = Math.min(1, (now - startedAt) / 200)
      const eased = 1 - (1 - progress) ** 3
      setDisplay(Math.round(start + (value - start) * eased))
      if (progress < 1) frame = requestAnimationFrame(tick)
    }
    frame = requestAnimationFrame(tick)
    return () => cancelAnimationFrame(frame)
  }, [value])

  return <>{format(display)}</>
}
