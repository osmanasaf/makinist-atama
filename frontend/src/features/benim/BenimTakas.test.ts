import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'

vi.mock('./benimApi', () => ({
  benimApi: {
    takaslar: vi.fn(),
    takasKabul: vi.fn(),
    takasRed: vi.fn(),
    takasIptal: vi.fn(),
  },
}))

import { ApiHata } from '@/api/istemci'
import BenimTakas from './BenimTakas.vue'
import { benimApi, type BenimTakas as Talep } from './benimApi'
import { rozetMetni } from './rozet'

function talep(p: Partial<Talep> = {}): Talep {
  return {
    id: 1,
    tur: 'TAKAS',
    durum: 'KARSI_ONAY_BEKLIYOR',
    gelen: true,
    yanitBekliyor: true,
    benimGorev: 'Yardımcı · Ankara 16.10 08:00 → Ankara 16.10 09:00',
    karsiSicil: 'D001',
    karsiGorev: 'Asıl · Ankara 15.10 08:00 → Ankara 15.10 09:00',
    neden: null,
    nedenNotu: null,
    olusturma: '2026-10-13T07:00:00Z',
    sonKabul: '2026-10-14T07:00:00Z',
    ...p,
  }
}

const kurulan: VueWrapper[] = []
afterEach(() => kurulan.splice(0).forEach((w) => w.unmount()))

async function kur() {
  const w = mount(BenimTakas)
  kurulan.push(w)
  await flushPromises()
  return w
}

describe('BenimTakas', () => {
  beforeEach(() => vi.resetAllMocks())

  it('yükleniyor, boş ve hata durumları', async () => {
    let bitir!: (v: Talep[]) => void
    vi.mocked(benimApi.takaslar).mockReturnValue(new Promise((c) => (bitir = c)))
    const w = mount(BenimTakas)
    kurulan.push(w)
    expect(w.get('[role="status"]').text()).toContain('Yükleniyor')
    bitir([])
    await flushPromises()
    expect(w.text()).toContain('talebiniz yok')

    vi.mocked(benimApi.takaslar).mockRejectedValue(
      new ApiHata(500, 'SUNUCU_HATASI', 'Sunucu hatası.'),
    )
    const h = await kur()
    expect(h.get('[role="alert"]').text()).toContain('Sunucu hatası.')
    expect(h.text()).toContain('Yeniden dene')
  })

  it('gelen talep: durum metin + simgeyle, kural ayrıntısı yok, kabul ve reddet düğmeleri; rozet', async () => {
    vi.mocked(benimApi.takaslar).mockResolvedValue([talep()])
    const w = await kur()
    const kart = w.get('article')
    expect(kart.text()).toContain('Yanıtınız bekleniyor') // gelen talepte yanıtı bekleyen benim (UX-1)
    expect(kart.text()).toContain('D001')
    expect(kart.text()).toContain('Size gelen talep')
    expect(kart.text()).not.toMatch(/ihlal|belge|dinlenme/i)
    expect(kart.findAll('button').map((b) => b.text())).toEqual(['Kabul et', 'Reddet'])
    expect(rozetMetni.value).toContain('1 takas talebi yanıtınızı bekliyor')
  })

  it('açan makinistte etiket "Karşı makinistin yanıtı bekleniyor"; reddedilende kimin reddettiği yazar (UX-1, UX-4)', async () => {
    vi.mocked(benimApi.takaslar).mockResolvedValue([
      talep({ gelen: false, yanitBekliyor: false }),
      talep({
        id: 2,
        gelen: false,
        yanitBekliyor: false,
        durum: 'REDDEDILDI',
        reddeden: 'ATAMACI',
      }),
      talep({
        id: 3,
        gelen: false,
        yanitBekliyor: false,
        durum: 'REDDEDILDI',
        reddeden: 'KARSI_MAKINIST',
      }),
    ])
    const kartlar = (await kur()).findAll('article')
    expect(kartlar[0]!.text()).toContain('Karşı makinistin yanıtı bekleniyor')
    expect(kartlar[1]!.text()).toContain('Reddedildi (atamacı)')
    expect(kartlar[2]!.text()).toContain('Reddedildi (karşı makinist)')
  })

  it('kabul: API çağrılır, liste yenilenir (ATAMACI_ONAYI_BEKLIYOR); çift tıklama tek istek', async () => {
    vi.mocked(benimApi.takaslar).mockResolvedValueOnce([talep()])
    let bitir!: () => void
    vi.mocked(benimApi.takasKabul).mockReturnValue(new Promise((c) => (bitir = () => c(talep()))))
    const w = await kur()
    vi.mocked(benimApi.takaslar).mockResolvedValue([
      talep({ durum: 'ATAMACI_ONAYI_BEKLIYOR', yanitBekliyor: false }),
    ])

    await w.get('article button').trigger('click')
    await w.get('article button').trigger('click')
    expect(benimApi.takasKabul).toHaveBeenCalledTimes(1)
    bitir()
    await flushPromises()
    expect(benimApi.takasKabul).toHaveBeenCalledWith(1)
    expect(w.get('article').text()).toContain('Atamacı onayı bekleniyor')
    expect(w.find('article button').exists()).toBe(false)
  })

  it('hata: sunucu mesajı gösterilir, liste yine yenilenir; reddet çağrılır', async () => {
    vi.mocked(benimApi.takaslar).mockResolvedValue([talep()])
    vi.mocked(benimApi.takasKabul).mockRejectedValue(
      new ApiHata(422, 'TAKAS_SURESI_GECTI', 'Talebin süresi doldu.'),
    )
    const w = await kur()
    await w.findAll('article button')[0]!.trigger('click')
    await flushPromises()
    expect(w.get('[role="alert"]').text()).toContain('süresi doldu')
    expect(vi.mocked(benimApi.takaslar).mock.calls.length).toBeGreaterThanOrEqual(2)

    vi.mocked(benimApi.takasRed).mockResolvedValue(talep({ durum: 'REDDEDILDI' }))
    await w.findAll('article button')[1]!.trigger('click')
    await flushPromises()
    expect(benimApi.takasRed).toHaveBeenCalledWith(1)
  })

  it('kendi talebim: neden gösterilir, geri çekilebilir; kapanmış talepte düğme yok', async () => {
    vi.mocked(benimApi.takaslar).mockResolvedValue([
      talep({ id: 2, gelen: false, yanitBekliyor: false, neden: 'IZIN', nedenNotu: 'düğün' }),
      talep({ id: 3, gelen: false, yanitBekliyor: false, durum: 'BAYAT' }),
    ])
    const w = await kur()
    const kartlar = w.findAll('article')
    expect(kartlar[0]!.text()).toContain('Neden: İzin')
    expect(kartlar[0]!.text()).toContain('düğün')
    expect(kartlar[0]!.text()).toContain('Sizin talebiniz')
    vi.mocked(benimApi.takasIptal).mockResolvedValue(talep({ id: 2, durum: 'IPTAL' }))
    await kartlar[0]!.get('button').trigger('click')
    await flushPromises()
    expect(benimApi.takasIptal).toHaveBeenCalledWith(2)
    expect(kartlar[1]!.text()).toContain('Görev değiştiği için geçersiz')
    expect(kartlar[1]!.find('button').exists()).toBe(false)
  })
})
