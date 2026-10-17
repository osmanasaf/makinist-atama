import { describe, expect, it } from 'vitest'
import { NOBET_NOTU, nobetGeciyor } from './nobetNotu'

describe('nobetGeciyor', () => {
  it('nöbet geçen metni, büyük/küçük harf ve ek fark etmeden bulur', () => {
    expect(nobetGeciyor(['Günlük 9 s > 8 s', 'Gece nöbeti süresi 10 s 57 dk > 7 s 30 dk'])).toBe(
      true,
    )
    expect(nobetGeciyor(['NÖBET süresi'])).toBe(true)
  })
  it('metin yoksa ya da nöbet geçmiyorsa false', () => {
    expect(nobetGeciyor([])).toBe(false)
    expect(nobetGeciyor(['Günlük 9 s > 8 s', undefined, null, ''])).toBe(false)
  })
  it('not tek cümle ve 4 saati söyler', () => {
    expect(NOBET_NOTU).toContain('4 saatten az')
  })
})
