import { autocompletion, type CompletionContext, type CompletionResult } from '@codemirror/autocomplete'
import type { Extension } from '@codemirror/state'

// Typing after `[[` up to the caret, before any closing `]` or newline.
const WIKI_BEFORE = /\[\[([^\]\n]*)$/

export function makeWikiCompletionSource(getTitles: () => string[]) {
  return (ctx: CompletionContext): CompletionResult | null => {
    const before = ctx.matchBefore(WIKI_BEFORE)
    if (!before || (before.from === before.to && !ctx.explicit)) return null
    const typed = before.text.slice(2).toLowerCase() // drop the leading "[["
    const options = getTitles()
      .filter((title) => title.toLowerCase().includes(typed))
      .slice(0, 20)
      // Complete to the title AND close the brackets, so `[[iel` → `[[IELTS]]`.
      .map((title) => ({ label: title, type: 'keyword', apply: `${title}]]` }))
    if (options.length === 0) return null
    return { from: before.from + 2, options } // insert after the "[["
  }
}

/** `[[` opens a menu of existing note titles; picking one closes the brackets. */
export function wikiLinkAutocomplete(getTitles: () => string[]): Extension {
  return autocompletion({ override: [makeWikiCompletionSource(getTitles)] })
}
