export const VND = new Intl.NumberFormat('vi-VN')

export function vnd(amount: number): string {
  return `${VND.format(amount)} ₫`
}

export function todayIso(): string {
  const today = new Date()
  return `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-${String(today.getDate()).padStart(2, '0')}`
}

export function formatDay(isoDate: string): string {
  const [year, month, day] = isoDate.split('-').map(Number)
  return new Intl.DateTimeFormat('en', { month: 'short', day: 'numeric' })
    .format(new Date(year, month - 1, day))
}

export function formatFullDate(isoDate: string): string {
  const [year, month, day] = isoDate.split('-').map(Number)
  return new Intl.DateTimeFormat('en', { month: 'short', day: 'numeric', year: 'numeric' })
    .format(new Date(year, month - 1, day))
}
