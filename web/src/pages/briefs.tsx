import { Newspaper, RadioTower } from 'lucide-react'
import { useState } from 'react'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { HuggingNewsTab } from '@/features/briefs/huggingnews-tab'
import { NorthstarBriefTab } from '@/features/briefs/northstar-brief-tab'

export function BriefsPage() {
  const [tab, setTab] = useState('huggingnews')

  return (
    <main className="min-h-0 min-w-0 flex-1 overflow-auto">
      <div className="mx-auto min-h-full w-full max-w-7xl px-4 py-5 md:px-8 md:py-7">
        <Tabs value={tab} onValueChange={setTab} className="gap-0">
          <header className="flex flex-wrap items-center justify-between gap-3 border-b pb-4">
            <div>
              <h1 className="text-3xl font-bold tracking-tight">Briefs</h1>
              <p className="mt-1 text-sm text-muted-foreground">Live AI signal and your own curated intelligence.</p>
            </div>
            <TabsList className="max-w-full">
              <TabsTrigger value="huggingnews" className="px-2 text-xs data-[state=active]:bg-info/10 data-[state=active]:text-info sm:px-3 sm:text-sm"><RadioTower className="hidden size-4 text-info sm:block" /> HuggingNews</TabsTrigger>
              <TabsTrigger value="northstar" className="px-2 text-xs data-[state=active]:bg-insight/10 data-[state=active]:text-insight sm:px-3 sm:text-sm"><Newspaper className="hidden size-4 text-insight sm:block" /> Northstar Brief</TabsTrigger>
            </TabsList>
          </header>

          <TabsContent value="huggingnews"><HuggingNewsTab /></TabsContent>
          <TabsContent value="northstar"><NorthstarBriefTab /></TabsContent>
        </Tabs>
      </div>
    </main>
  )
}
