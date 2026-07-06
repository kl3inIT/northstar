import { FileText } from 'lucide-react'

export function NotesEmpty() {
  return (
    <div className="flex flex-1 flex-col items-center justify-center gap-2 text-muted-foreground">
      <FileText className="size-10 opacity-40" />
      <p className="text-sm">Select a note to read, or create a new one.</p>
    </div>
  )
}
