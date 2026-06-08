import type { RubricAnnotation } from '@/types'

export interface HighlightSegment {
  text: string
  annotationIndex?: number
  severity?: string
  type?: string
}

interface HighlightRange {
  start: number
  end: number
  annotationIndex: number
  severity?: string
  type?: string
}

export function buildHighlightedSegments(content: string, annotations: RubricAnnotation[] = []): HighlightSegment[] {
  if (!content) return []
  const ranges = findAnnotationRanges(content, annotations)
  if (!ranges.length) return [{ text: content }]

  const segments: HighlightSegment[] = []
  let cursor = 0
  for (const range of ranges) {
    if (range.start > cursor) {
      segments.push({ text: content.slice(cursor, range.start) })
    }
    segments.push({
      text: content.slice(range.start, range.end),
      annotationIndex: range.annotationIndex,
      severity: range.severity,
      type: range.type
    })
    cursor = range.end
  }
  if (cursor < content.length) {
    segments.push({ text: content.slice(cursor) })
  }
  return segments
}

function findAnnotationRanges(content: string, annotations: RubricAnnotation[]): HighlightRange[] {
  const ranges: HighlightRange[] = []
  const used = new Set<string>()
  annotations.forEach((annotation, index) => {
    const quote = firstNonBlank(annotation.quote, annotation.original, annotation.context)
    if (!quote) return
    const match = findMatch(content, quote)
    if (!match) return
    const key = `${match.start}:${match.end}`
    if (used.has(key)) return
    used.add(key)
    ranges.push({
      start: match.start,
      end: match.end,
      annotationIndex: index,
      severity: annotation.severity,
      type: annotation.type
    })
  })
  return ranges
    .sort((a, b) => a.start - b.start || a.end - b.end)
    .filter(nonOverlapping())
}

function findMatch(content: string, quote: string): { start: number; end: number } | null {
  const exactIndex = content.indexOf(quote)
  if (exactIndex >= 0) {
    return { start: exactIndex, end: exactIndex + quote.length }
  }

  const lowerIndex = content.toLocaleLowerCase().indexOf(quote.toLocaleLowerCase())
  if (lowerIndex >= 0) {
    return { start: lowerIndex, end: lowerIndex + quote.length }
  }

  return findWhitespaceNormalizedMatch(content, quote)
}

function findWhitespaceNormalizedMatch(content: string, quote: string): { start: number; end: number } | null {
  const contentMap = normalizeWithMap(content)
  const normalizedQuote = normalizeForMatch(quote)
  if (!normalizedQuote) return null
  const startInNormalized = contentMap.normalized.indexOf(normalizedQuote)
  if (startInNormalized < 0) return null
  const endInNormalized = startInNormalized + normalizedQuote.length - 1
  return {
    start: contentMap.indexMap[startInNormalized],
    end: contentMap.indexMap[endInNormalized] + 1
  }
}

function normalizeWithMap(value: string) {
  let normalized = ''
  const indexMap: number[] = []
  for (let i = 0; i < value.length; i++) {
    const char = value[i]
    if (/\s/.test(char)) continue
    normalized += char.toLocaleLowerCase()
    indexMap.push(i)
  }
  return { normalized, indexMap }
}

function normalizeForMatch(value: string) {
  return value.replace(/\s+/g, '').toLocaleLowerCase()
}

function firstNonBlank(...values: Array<string | undefined>) {
  return values.find((value) => value && value.trim())?.trim() || ''
}

function nonOverlapping() {
  let lastEnd = -1
  return (range: HighlightRange) => {
    if (range.start < lastEnd) return false
    lastEnd = range.end
    return true
  }
}
