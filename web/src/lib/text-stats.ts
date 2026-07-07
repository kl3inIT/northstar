/** Word count + reading time for a note body. ~200 wpm is the usual adult prose rate. */
export function textStats(markdown: string): { words: number; minutes: number } {
  const words = (markdown.trim().match(/\S+/g) ?? []).length
  const minutes = words === 0 ? 0 : Math.max(1, Math.ceil(words / 200))
  return { words, minutes }
}
