import { useState } from 'react'
import { BookOpenCheck, ListChecks, Mic, PenLine } from 'lucide-react'
import { LogPanel } from '@/features/study/log-panel'
import { SpeakingPanel } from '@/features/study/speaking-panel'
import { VocabularyPanel } from '@/features/study/vocabulary-panel'
import { WritingPanel } from '@/features/study/writing-panel'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'

/**
 * Study combines history with focused practice. Entries are captured on the
 * Capture page or in chat; Vocabulary owns the card review workflow, while
 * Writing shows feedback produced through the Assistant and Speaking owns its
 * audio practice flow.
 */
export function StudyPage() {
  const [tab, setTab] = useState<'log' | 'vocabulary' | 'writing' | 'speaking'>('log')

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
            <TabsTrigger value="writing" className="px-2 text-xs sm:px-3 sm:text-sm">
              <PenLine className="hidden size-4 sm:block" />
              Writing
            </TabsTrigger>
            <TabsTrigger value="speaking" className="px-2 text-xs sm:px-3 sm:text-sm">
              <Mic className="hidden size-4 sm:block" />
              Speaking
            </TabsTrigger>
          </TabsList>

          <TabsContent value="log">
            <LogPanel />
          </TabsContent>
          <TabsContent value="vocabulary">
            <VocabularyPanel />
          </TabsContent>
          <TabsContent value="writing">
            <WritingPanel />
          </TabsContent>
          <TabsContent value="speaking">
            <SpeakingPanel />
          </TabsContent>
        </Tabs>
      </div>
    </main>
  )
}
