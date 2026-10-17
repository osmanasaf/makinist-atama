import { describe, expect, it } from 'vitest'
import { sayiBicimle } from './sayi'

describe('sayiBicimle', () => {
  it('en çok 2 ondalık, Türkçe virgül, gereksiz sıfır ve binlik ayraç yok', () => {
    expect(sayiBicimle(1.4666666666666666)).toBe('1,47')
    expect(sayiBicimle(28.700000000000003)).toBe('28,7')
    expect(sayiBicimle(0)).toBe('0')
    expect(sayiBicimle(120)).toBe('120')
    expect(sayiBicimle(245)).toBe('245')
    expect(sayiBicimle(12345.5)).toBe('12345,5')
    expect(sayiBicimle(-0.001)).toBe('0')
  })
})
