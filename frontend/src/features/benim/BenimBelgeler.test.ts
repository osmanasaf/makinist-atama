import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

vi.mock('./benimApi', () => ({ benimApi: { belgeler: vi.fn() } }))

import { ApiHata } from '@/api/istemci'
import BenimBelgeler from './BenimBelgeler.vue'
import { benimApi } from './benimApi'

async function kur() {
  const w = mount(BenimBelgeler)
  await flushPromises()
  return w
}

describe('BenimBelgeler', () => {
  beforeEach(() => vi.resetAllMocks())

  it('belge durumlarını simge + metinle ve kalan günle gösterir', async () => {
    vi.mocked(benimApi.belgeler).mockResolvedValue({
      bugun: '2026-10-12',
      uyariGun: 30,
      enKotu: 'DOLDU',
      belgeler: [
        { tur: 'SAGLIK', ad: 'Sağlık raporu', bitis: '2026-10-11', kalanGun: -1, durum: 'DOLDU' },
        { tur: 'EHLIYET', ad: 'Ehliyet', bitis: '2026-11-01', kalanGun: 20, durum: 'YAKLASIYOR' },
        {
          tur: 'PSIKOTEKNIK',
          ad: 'Psikoteknik raporu',
          bitis: '2026-10-12',
          kalanGun: 0,
          durum: 'YAKLASIYOR',
        },
        {
          tur: 'ARAC_TIPI',
          ad: 'Araç tipi sınavı: YHT',
          bitis: '2028-01-01',
          kalanGun: 446,
          durum: 'GECERLI',
        },
        { tur: 'GENEL_SURUS', ad: 'Son sürüş', bitis: null, kalanGun: null, durum: 'GIRILMEMIS' },
      ],
    })
    const w = await kur()
    const satirlar = w.findAll('li').map((li) => li.text())
    expect(satirlar[0]).toContain('Süresi doldu')
    expect(satirlar[0]).toContain('11.10.2026 · 1 gün geçti')
    expect(satirlar[1]).toContain('Yaklaşıyor')
    expect(satirlar[1]).toContain('20 gün kaldı')
    expect(satirlar[2]).toContain('Bugün son gün')
    expect(satirlar[3]).toContain('Geçerli')
    expect(satirlar[4]).toContain('Girilmemiş')
    expect(w.findAll('li')[0]!.classes()).toContain('doldu')
    // emoji yerine simge: ton + simge + metin (OK-32 K2)
    expect(w.findAll('li').map((li) => li.find('svg')?.attributes('data-simge'))).toEqual([
      'engel',
      'uyari',
      'uyari',
      'basari',
      'daire',
    ])
  })

  it('boş ve hata durumları', async () => {
    vi.mocked(benimApi.belgeler).mockResolvedValueOnce({
      bugun: '2026-10-12',
      uyariGun: 30,
      enKotu: 'GECERLI',
      belgeler: [],
    })
    expect((await kur()).text()).toContain('Kayıtlı belgeniz yok')
    vi.mocked(benimApi.belgeler).mockRejectedValueOnce(
      new ApiHata(403, 'HESAP_KAPALI', 'Hesabınız kapalı.'),
    )
    expect((await kur()).get('[role=alert]').text()).toContain('Hesabınız kapalı.')
  })
})
