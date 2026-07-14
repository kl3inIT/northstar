/** Implements the MIME and extension forms from the native input accept grammar. */
export function fileMatchesAccept(
  file: Pick<File, 'name' | 'type'>,
  accept?: string,
): boolean {
  if (!accept || accept.trim() === '') return true
  const filename = file.name.toLowerCase()
  return accept
    .split(',')
    .map((value) => value.trim().toLowerCase())
    .filter(Boolean)
    .some((pattern) => {
      if (pattern.startsWith('.')) return filename.endsWith(pattern)
      if (pattern.endsWith('/*')) return file.type.toLowerCase().startsWith(pattern.slice(0, -1))
      return file.type.toLowerCase() === pattern
    })
}
