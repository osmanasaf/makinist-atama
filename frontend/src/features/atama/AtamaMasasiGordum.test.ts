import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'

vi.mock('./atamaApi', async (orijinal) => ({
  ...(await orijinal<typeof import('./atamaApi')>()),
  atamaApi: {
    gun: vi.fn(),
    adaylar: vi.fn(),
    kirmiziSayisi: vi.fn(),
    gordumOzeti: vi.fn(),
  },
}))

import AtamaMasasi from './AtamaMasasi.vue'
import { atamaApi, type GordumOzeti, type GunListesi } from './atamaApi'
import { makinist, satir } from './testVerisi'

const bos = { template: '<div />' }

const gordu = makinist({ gordu: 'GORDU', gorduZamani: '2026-10-12T06:14:00Z' }) // 09:14 İstanbul
const gormedi = makinist({
  atamaId: 11,
  makinistId: 2,
  sicil: 'D002',
  ad: 'Görmeyen',
  gordu: 'GORMEDI',
})
const yakin = makinist({
  atamaId: 12,
  makinistId: 3,
  sicil: 'D003',
  ad: 'Yakın Kişi',
  gordu: 'GORMEDI',
  gorduUyari: true,
})
const hesapsiz = makinist({
  atamaId: 13,
  makinistId: 4,
  sicil: 'D004',
  ad: 'Hesapsız',
  gordu: 'HESAP_YOK',
})
const gecmis = makinist({
  atamaId: 14,
  makinistId: 5,
  sicil: 'D005',
  ad: 'Geçmiş Kişi',
  gordu: null,
})

const gun = (): GunListesi => ({
  tarih: '2026-10-12',
  uretimZamani: '2026-10-12T07:00:00Z',
  satirlar: [
    satir(1, { makinistler: [gordu] }),
    satir(2, { makinistler: [gormedi] }),
    satir(3, { makinistler: [yakin] }),
    satir(4, { makinistler: [hesapsiz] }),
    satir(5, { makinistler: [gecmis] }),
  ],
})

const ozet: GordumOzeti = {
  bas: '2026-10-12',
  bit: '2026-10-19',
  toplam: 13,
  gorulen: 6,
  gorulmeyen: 4,
  hesapsiz: 3,
  uyari: 1,
}

async function kur() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/atama', name: 'atama', component: bos },
      { path: '/yazdir/gun/:tarih', name: 'yazdir-gun', component: bos },
    ],
  })
  await router.push('/atama?tarih=2026-10-12')
  const w = mount(AtamaMasasi, { global: { plugins: [router] } })
  await flushPromises()
  return w
}

describe('AtamaMasasi: gördüm görünürlüğü', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(atamaApi.gun).mockResolvedValue(gun())
    vi.mocked(atamaApi.adaylar).mockResolvedValue([])
    vi.mocked(atamaApi.kirmiziSayisi).mockResolvedValue({
      bas: '2026-10-12',
      bit: '2026-10-19',
      kirmiziSefer: 0,
      kirmiziAtama: 0,
    })
    vi.mocked(atamaApi.gordumOzeti).mockResolvedValue(ozet)
  })

  it('her görevlide gördü saat (onay simgesi) / görmedi / hesap yok (metin + simge); geçmiş görevde bilgi yok', async () => {
    const w = await kur()
    const kartlar = w.findAll('tr.sefer-satiri')
    expect(kartlar[0]!.get('[data-test=gordu]').text()).toBe('gördü 09:14')
    expect(kartlar[0]!.find('[data-test=gordu] svg[data-simge="basari"]').exists()).toBe(true)
    expect(kartlar[1]!.get('[data-test=gormedi]').text()).toContain('görmedi')
    expect(kartlar[3]!.get('[data-test=hesap-yok]').text()).toContain('hesap yok')
    expect(kartlar[4]!.find('.gordum').exists()).toBe(false)
  })

  it('kalkışa yakın görmeyen satır vurgulanır (renkten bağımsız uyarı simgesi ve kalın metin); diğerleri vurgusuz', async () => {
    const w = await kur()
    const kartlar = w.findAll('tr.sefer-satiri')
    expect(kartlar[2]!.find('.gorevli.gordum-uyarili').exists()).toBe(true)
    expect(kartlar[2]!.get('[data-test=gormedi]').text()).toContain('görmedi')
    expect(kartlar[2]!.find('[data-test=gormedi] svg[data-simge="uyari"]').exists()).toBe(true)
    expect(kartlar[1]!.find('.gordum-uyarili').exists()).toBe(false)
  })

  it('üst çubuk rozeti: Görülmeyen ve hesapsız ayrı sayaç, kalkışa yakın sayısı', async () => {
    const w = await kur()
    const sayac = w.get('[data-test=gordum-sayac]')
    expect(sayac.text()).toContain('Görülmeyen: 4')
    expect(sayac.text()).toContain('telefon hesabı yok: 3')
    expect(sayac.text()).toContain('1 kalkışa yakın')
    expect(atamaApi.gordumOzeti).toHaveBeenCalledWith('2026-10-12', '2026-10-19')
  })

  it('sayaç yüklenemezse masa yine çalışır, rozet gösterilmez', async () => {
    vi.mocked(atamaApi.gordumOzeti).mockRejectedValue(new Error('x'))
    const w = await kur()
    expect(w.find('[data-test=gordum-sayac]').exists()).toBe(false)
    expect(w.findAll('tr.sefer-satiri')).toHaveLength(5)
  })
})
