import { useState } from 'react'
import { BookOpenCheck, ListChecks, PenLine } from 'lucide-react'
import { LogPanel } from '@/features/study/log-panel'
import { VocabularyPanel } from '@/features/study/vocabulary-panel'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'

/**
 * Study — read-mostly view over the study log. Entries are captured on the
 * Capture page or in chat; this page answers "how much, what, and is it
 * moving". Vocabulary (SRS) and Writing (tutor feedback) tabs land in the
 * next increments of the study module.
 */
export function StudyPage() {
  const [tab, setTab] = useState<'log' | 'vocabulary' | 'writing'>('log')

  return (
    <main className="w-full min-w-0 flex-1 overflow-auto px-4 py-6 md:px-10 md:py-8">
      <div className="flex flex-col gap-5 pb-4">
        <header>
          <h1 className="text-3xl font-bold tracking-tight">Study</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            What you practiced and where it's heading — log sessions on Capture or in chat.
          </p>
        </header>

        <Tabs value={tab} onValueChange={(value) => setTab(value as typeof tab)} className="gap-5">
          <TabsList className="max-w-full">
            <TabsTrigger value="log" className="px-2 text-xs sm:px-3 sm:text-sm">
              <ListChecks className="hidden size-4 sm:block" />
              Log
            </TabsTrigger>
            <TabsTrigger value="vocabulary" className="px-2 text-xs sm:px-3 sm:text-sm">
              <BookOpenCheck className="hidden size-4 sm:block" />
              Vocabulary
            </TabsTrigger>
            <TabsTrigger value="writing" disabled className="px-2 text-xs sm:px-3 sm:text-sm">
              <PenLine className="hidden size-4 sm:block" />
              Writing
            </TabsTrigger>
          </TabsList>

          <TabsContent value="log">
            <LogPanel />
          </TabsContent>
          <TabsContent value="vocabulary">
            <VocabularyPanel />
          </TabsContent>
        </Tabs>
      </div>
    </main>
  )
}
