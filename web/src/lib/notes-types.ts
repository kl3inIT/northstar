import type {
  CreateNoteRequest,
  NoteDetail as ApiNoteDetail,
  NoteRef as ApiNoteRef,
  NoteSummary as ApiNoteSummary,
  UpdateNoteRequest,
} from './hey-api'

/**
 * The api's records always serialize every field; springdoc marks them optional only
 * because it cannot yet see Java record non-nullability, so we assert presence at the
 * client edge. These types are DERIVED from the generated contract — regenerate with
 * `pnpm gen:api` after any api change and drift surfaces here as a type error.
 */
type Present<T> = T extends readonly (infer U)[]
  ? Present<U>[]
  : T extends object
    ? { [K in keyof T]-?: Present<NonNullable<T[K]>> }
    : T

// A wiki-link target: slug is genuinely null when the linked note does not exist yet.
export type NoteRef = Omit<Present<ApiNoteRef>, 'slug'> & { slug: string | null }

export type NoteSummary = Omit<Present<ApiNoteSummary>, 'projectId'> & { projectId: string | null }

/** MFI working state: STAGING (chờ duyệt) → RESOURCE (kho) / ARCHIVED (lưu trữ). */
export type NoteStatus = NoteSummary['status']

export type NoteDetail = Omit<Present<ApiNoteDetail>, 'outgoingLinks' | 'backlinks' | 'projectId'> & {
  projectId: string | null
  outgoingLinks: NoteRef[]
  backlinks: NoteRef[]
}

export type NoteInput = Omit<CreateNoteRequest, 'projectId'> & { projectId?: string | null }

export type NoteUpdate = Omit<UpdateNoteRequest, 'projectId'> & { projectId?: string | null }
