import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

vi.mock('./benimApi', () => ({ benimApi: { gorevler: vi.fn() } }))

import { ApiHata } from '@/api/istemci'
import BenimGorevler from './BenimGorevler.vue'
import { benimApi, type BenimGorev, type BenimGorevler as Yanit } from './benimApi'
import { rozetMetni } from './rozet'

function gorev(p: Partial<BenimGorev>): BenimGorev {
  return {
    atamaId: 1,
    seferId: 1,
    tarih: '2026-10-12',
    sira: 1,
    trenNo: 'T1',
    rol: 'ASIL',
    durum: 'PLANLI',
    kalkisGar: 'Ankara',
    varisGar: 'Sivas',
    planlananKalkis: '2026-10-12T05:00:00Z', // 08:00 İstanbul
    planlananVaris: '2026-10-12T07:00:00Z', // 10:00
    guncelKalkis: '2026-10-12T05:00:00Z',
    guncelVaris: '2026-10-12T07:00:00Z',
    kalkisGecikmeDk: 0,
    varisGecikmeDk: 0,
    varisErtesiGun: false,
    aracTipi: 'YHT',
    hat: 'Ankara-Sivas',
    km: 250,
    esMakinist: null,
    gordu: 'GORULDU',
    gorduZamani: null,
    ozet: '',
    eskiOzet: null,
    ...p,
  }
}

const yanit = (gorevler: BenimGorev[]): Yanit => ({
  bugun: '2026-10-12',
  ufukGun: 7,
  gorevler,
  kaldirilanlar: [],
})

async function kur() {
  const w = mount(BenimGorevler)
  await flushPromises()
  return w
}

describe('BenimGorevler', () => {
  beforeEach(() => vi.resetAllMocks())

  it('yükleniyor durumunu gösterir', () => {
    vi.mocked(benimApi.gorevler).mockReturnValue(new Promise(() => {}))
    const w = mount(BenimGorevler)
    expect(w.text()).toContain('Yükleniyor')
  })

  it('dört görevi Bugün / Yarın / tarihli gruplarda gösterir; 9. gün sunucudan gelmez', async () => {
    vi.mocked(benimApi.gorevler).mockResolvedValue(
      yanit([
        gorev({ atamaId: 1, trenNo: 'T0' }),
        gorev({ atamaId: 2, trenNo: 'T1', tarih: '2026-10-13' }),
        gorev({ atamaId: 3, trenNo: 'T3', tarih: '2026-10-15' }),
        gorev({ atamaId: 4, trenNo: 'T6', tarih: '2026-10-18' }),
      ]),
    )
    const w = await kur()
    expect(w.findAll('article')).toHaveLength(4)
    expect(w.findAll('h2').map((h) => h.text().split(' ')[0])).toEqual([
      'Bugün',
      'Yarın',
      '15.10.2026',
      '18.10.2026',
    ])
    expect(w.text()).toContain('08:00 – 10:00')
    expect(w.text()).toContain('Ankara → Sivas')
    expect(rozetMetni.value).toBe('4 görev listelendi')
  })

  it('iptal sefer İPTAL etiketiyle (renge ek metin) görünür; gecikme ve ertesi gün varışı yazılır', async () => {
    vi.mocked(benimApi.gorevler).mockResolvedValue(
      yanit([
        gorev({ atamaId: 1, trenNo: 'IPT', durum: 'IPTAL' }),
        gorev({
          atamaId: 2,
          trenNo: 'GEC',
          kalkisGecikmeDk: 25,
          guncelKalkis: '2026-10-12T05:25:00Z',
          guncelVaris: '2026-10-12T23:10:00Z',
          varisErtesiGun: true,
          esMakinist: 'Veli Kaya',
        }),
      ]),
    )
    const w = await kur()
    const kartlar = w.findAll('article')
    expect(kartlar[0]!.text()).toContain('İPTAL')
    expect(kartlar[0]!.classes()).toContain('iptal')
    expect(kartlar[1]!.text()).toContain('25 dk gecikme')
    expect(kartlar[1]!.text()).toContain('planlanan 08:00')
    expect(kartlar[1]!.text()).toContain('08:25 – 02:10 (ertesi gün)')
    expect(kartlar[1]!.text()).toContain('Veli Kaya')
    expect(rozetMetni.value).toBe('1 görev listelendi') // iptal sayılmaz
  })

  it('onay bekleyen kart gün grubunda çizilmediği için gününü kendisi yazar (QA BUG-1)', async () => {
    vi.mocked(benimApi.gorevler).mockResolvedValue(
      yanit([
        gorev({ atamaId: 1, trenNo: 'T0', gordu: 'YENI' }),
        gorev({ atamaId: 2, trenNo: 'T1', tarih: '2026-10-13', gordu: 'YENI' }),
        gorev({ atamaId: 3, trenNo: 'T3', tarih: '2026-10-15', gordu: 'DEGISTI' }),
      ]),
    )
    const w = await kur()
    const gunler = w
      .findAll('section[data-onay] article')
      .map((k) => k.get('[data-test=kart-gunu]').text())
    // Değişti bölümü Yeni'den önce gelir
    expect(gunler).toEqual(['15.10.2026', 'Bugün (12.10.2026)', 'Yarın (13.10.2026)'])
  })

  it('boş durumda ufku söyler', async () => {
    vi.mocked(benimApi.gorevler).mockResolvedValue(yanit([]))
    const w = await kur()
    expect(w.text()).toContain('Önümüzdeki 7 gün için göreviniz yok')
  })

  it('hata durumunda mesaj ve yeniden dene düğmesi gösterir', async () => {
    vi.mocked(benimApi.gorevler).mockRejectedValueOnce(
      new ApiHata(0, 'AG_HATASI', 'Sunucuya ulaşılamadı.'),
    )
    const w = await kur()
    expect(w.get('[role=alert]').text()).toContain('Sunucuya ulaşılamadı.')
    vi.mocked(benimApi.gorevler).mockResolvedValue(yanit([gorev({})]))
    await w.get('[role=alert] button').trigger('click')
    await flushPromises()
    expect(w.findAll('article')).toHaveLength(1)
  })
})
