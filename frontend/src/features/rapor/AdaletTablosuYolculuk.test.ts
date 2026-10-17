import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import AdaletTablosu from './AdaletTablosu.vue'
import { adaletYaniti } from './testVerisi'

describe('AdaletTablosu: yolculuk (F2b)', () => {
  it('sefer sayısının yanında "+N yolculuk" yalnız yolculuğu olan satırda görünür', () => {
    const rapor = {
      ...adaletYaniti,
      satirlar: adaletYaniti.satirlar.map((s, i) => (i === 0 ? { ...s, yolculukSayisi: 2 } : s)),
    }
    const w = mount(AdaletTablosu, { props: { rapor } })
    const satirlar = w.findAll('tbody tr')

    expect(satirlar[0]!.get('[data-test="yolculuk-sayisi"]').text()).toBe('(+2 yolculuk)')
    expect(satirlar[1]!.find('[data-test="yolculuk-sayisi"]').exists()).toBe(false)
  })
})
