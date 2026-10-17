import { describe, expect, it } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import routes from './routes'

describe('yazdır rotası', () => {
  const router = createRouter({ history: createMemoryHistory(), routes })

  it('geçerli tarihli adresi çözer ve yazdır işaretini taşır', () => {
    const r = router.resolve('/yazdir/gun/2026-10-10')
    expect(r.name).toBe('yazdir-gun')
    expect(r.params.tarih).toBe('2026-10-10')
    expect(r.meta.yazdir).toBe(true)
  })

  it('denetim raporu yazdır rotası süzgeci sorgudan bileşen özelliklerine çevirir', () => {
    const r = router.resolve('/yazdir/degisiklik?bas=2026-10-06&bit=2026-10-12&tur=ATAMA')
    expect(r.name).toBe('yazdir-degisiklik')
    expect(r.meta.yazdir).toBe(true)
    const props = r.matched[0]!.props.default as (r: unknown) => Record<string, unknown>
    expect(props(r)).toEqual({
      bas: '2026-10-06',
      bit: '2026-10-12',
      trenNo: undefined,
      kullanici: undefined,
      tur: 'ATAMA',
    })
  })

  it('tarih biçimi dışındaki adresi çözmez', () => {
    expect(router.resolve('/yazdir/gun/bugun').name).toBeUndefined()
  })
})
