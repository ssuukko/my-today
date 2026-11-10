// Financial values stay decimal strings; formatting must not round through Number.
export function decimal(value: string | null | undefined, currency?: string): string {
  if (value == null) return '미제공'
  const [whole, fraction = ''] = value.split('.')
  const digits = fraction.replace(/0+$/, '')
  const result = `${whole.replace(/\B(?=(\d{3})+(?!\d))/g, ',')}${digits ? `.${digits}` : ''}`
  return currency ? `${result} ${currency}` : result
}
