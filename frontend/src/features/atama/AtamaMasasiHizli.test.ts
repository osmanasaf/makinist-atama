// O1 hızlı doldurma (OK-34, OK-37; MK-150..154; AK-4.1..4.10): temiz adaya onaysız atama, Geri al, sıradaki boş, kısayollar.
import { readFileSync } from 'node:fs'
import { join } from 'node:path'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { ApiHata } from '@/api/istemci'

vi.mock('./atamaApi', async (orijinal) => ({
  ...(await orijinal<typeof import('./atamaApi')>()),
  atamaApi: {
    gun: vi.fn(),
    adaylar: vi.fn(),
    ata: vi.fn(),
    kaldir: vi.fn(),
    kirmiziSayisi: vi.fn(),
    gordumOzeti: vi.fn(),
  },
}))

import AtamaMasasi from './AtamaMasasi.vue'
import { atamaApi, type Aday, type GunSatiri } from './atamaApi'
import { makinist, satir } from './testVerisi'

const bos = { template: '<div />' }
const temiz: Aday = {
  makinistId: 7,
  sicil: 'D007',
  adSoyad: 'Aday Bir',
  bagliGar: 'Ankara',
  uygun: true,
  ihlaller: [],
  buAySaat: 100,
  buAyKm: 5000,
  buSeferdeRol: null,
}
const uyarili: Aday = {
  ...temiz,
  makinistId: 8,
  sicil: 'D008',
  adSoyad: 'Aday Uyarılı',
  ihlaller: [
    { kod: 'K-20', siddet: 'UYARI', olculen: 9, esik: 8, birim: 's', metin: 'Günlük 9 s' },
  ],
}

let durum: GunSatiri[]
let atamaSayaci: number
const satirlar = (w: ReturnType<typeof mount>) => w.findAll('tr.sefer-satiri')

async function kur() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/atama', name: 'atama', component: bos },
      { path: '/yazdir/gun/:tarih', name: 'yazdir-gun', component: bos },
    ],
  })
  await router.push('/atama?tarih=2026-10-12')
  const w = mount(AtamaMasasi, { global: { plugins: [router] }, attachTo: document.body })
  await flushPromises()
  return w
}

describe('AtamaMasasi: hızlı doldurma', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    // 3 boş sefer (1,2,3) + 1 dolu (4); `ata` durumu gerçek sunucu gibi değiştirir
    durum = [1, 2, 3].map((i) => satir(i)).concat(satir(4, { makinistler: [makinist()] }))
    atamaSayaci = 100
    vi.mocked(atamaApi.gun).mockImplementation(() =>
      Promise.resolve({
        tarih: '2026-10-12',
        uretimZamani: '2026-10-12T07:00:00Z',
        satirlar: structuredClone(durum),
      }),
    )
    vi.mocked(atamaApi.kirmiziSayisi).mockResolvedValue({
      bas: '2026-10-12',
      bit: '2026-10-19',
      kirmiziSefer: 0,
      kirmiziAtama: 0,
    })
    vi.mocked(atamaApi.adaylar).mockResolvedValue([uyarili, temiz])
    vi.mocked(atamaApi.ata).mockImplementation((seferId, govde) => {
      const id = ++atamaSayaci
      durum
        .find((s) => s.seferId === seferId)!
        .makinistler.push(
          makinist({
            atamaId: id,
            rol: govde.rol ?? 'ASIL',
            makinistId: govde.makinistId,
            ad: 'Aday Bir',
            sicil: 'D007',
          }),
        )
      return Promise.resolve({
        atamaId: id,
        seferId,
        rol: govde.rol ?? 'ASIL',
        makinistId: govde.makinistId,
        sicil: 'D007',
        ad: 'Aday Bir',
      } as Awaited<ReturnType<typeof atamaApi.ata>>)
    })
  })
  afterEach(() => {
    vi.useRealTimers()
    document.body.innerHTML = ''
  })

  const temizSatir = (w: ReturnType<typeof mount>) =>
    w.findAll('tr.aday').find((r) => r.text().includes('Aday Bir'))!

  it('AK-4.1: temiz adaya tık diyalog açmaz; tek ata çağrısı (gerekçe/neden yok)', async () => {
    const w = await kur()
    await satirlar(w)[0]!.trigger('click')
    await flushPromises()
    await temizSatir(w).trigger('click')
    await flushPromises()
    expect(w.find('dialog').exists()).toBe(false)
    expect(atamaApi.ata).toHaveBeenCalledTimes(1)
    expect(atamaApi.ata).toHaveBeenCalledWith(1, { makinistId: 7, rol: 'ASIL' })
    expect(Object.keys(vi.mocked(atamaApi.ata).mock.calls[0]![1])).toEqual(['makinistId', 'rol'])
  })

  it('AK-4.2: uyarılı aday, dolu rolde değiştir ve YOLCU rolü mevcut diyalogla yapılır', async () => {
    const w = await kur()
    await satirlar(w)[0]!.trigger('click')
    await flushPromises()
    await w
      .findAll('tr.aday')
      .find((r) => r.text().includes('Aday Uyarılı'))!
      .trigger('click')
    expect(w.find('dialog').exists()).toBe(true)
    expect(atamaApi.ata).not.toHaveBeenCalled()
    await w.get('dialog button[type="button"]:not([aria-label="Kapat"])').trigger('click') // Vazgeç
    // dolu rol (sefer 4: ASIL var) → değiştir diyaloğu
    await satirlar(w)[3]!.trigger('click')
    await flushPromises()
    await temizSatir(w).trigger('click')
    expect(w.get('dialog').text()).toContain('Makinisti değiştir')
    await w.get('dialog button[type="button"]:not([aria-label="Kapat"])').trigger('click')
    // YOLCU rolü
    await satirlar(w)[0]!.trigger('click')
    await flushPromises()
    await w.findAll('input[type="radio"]')[3]!.setValue(true)
    await flushPromises()
    await temizSatir(w).trigger('click')
    expect(w.find('dialog').exists()).toBe(true)
    expect(atamaApi.ata).not.toHaveBeenCalled()
  })

  it('AK-4.3: 3 boş sefer → 3 ata, 3 eylem; seçim sırayla ilerler, arada hiç diyalog yok', async () => {
    const w = await kur()
    await satirlar(w)[0]!.trigger('click') // ilk sefer seçimi (sayılmaz)
    await flushPromises()
    for (const beklenen of [1, 2, 3]) {
      expect(satirlar(w).findIndex((r) => r.attributes('aria-selected') === 'true') + 1).toBe(
        beklenen,
      )
      await temizSatir(w).trigger('click') // sefer başına tek eylem
      await flushPromises()
      expect(w.find('dialog').exists()).toBe(false)
    }
    expect(vi.mocked(atamaApi.ata).mock.calls.map((c) => c[0])).toEqual([1, 2, 3])
    expect(w.get('[role="status"]').text()).toContain('Boş sefer kalmadı.')
  })

  it('AK-4.5: şerit tek mesaj (Atandı … Sıradaki … Ctrl+Z) + Geri al düğmesi, aria-keyshortcuts', async () => {
    const w = await kur()
    await satirlar(w)[0]!.trigger('click')
    await flushPromises()
    await temizSatir(w).trigger('click')
    await flushPromises()
    const serit = w.get('[role="status"] [data-test="geri-al-seridi"]')
    expect(serit.text()).toContain('Atandı: Aday Bir (D007) → T1001 08:30.')
    expect(serit.text()).toContain('Sıradaki: T1002 08:30.')
    expect(serit.text()).toContain('Geri almak için Ctrl+Z.')
    expect(w.get('[data-test="geri-al"]').attributes('aria-keyshortcuts')).toBe('Control+Z')
  })

  it('AK-4.6: Geri al kaldır ucunu atamaId ile çağırır; bildirim ve yenileme', async () => {
    vi.mocked(atamaApi.kaldir).mockImplementation(() => {
      durum[0]!.makinistler = []
      return Promise.resolve(undefined)
    })
    const w = await kur()
    await satirlar(w)[0]!.trigger('click')
    await flushPromises()
    await temizSatir(w).trigger('click')
    await flushPromises()
    const gunOnce = vi.mocked(atamaApi.gun).mock.calls.length
    await w.get('[data-test="geri-al"]').trigger('click')
    await flushPromises()
    expect(atamaApi.kaldir).toHaveBeenCalledWith(1, {
      rol: 'ASIL',
      neden: 'DIGER',
      nedenNotu: 'Atama geri alındı (hızlı atama sonrası)',
      atamaId: 101,
    })
    expect(w.find('[data-test="geri-al"]').exists()).toBe(false)
    expect(w.get('[role="status"]').text()).toBe('Atama kaldırıldı.')
    expect(vi.mocked(atamaApi.gun).mock.calls.length).toBeGreaterThan(gunOnce)
    expect(document.activeElement).not.toBe(document.body) // odak kaybolmaz
  })

  it('YARIŞ (OK-37): araya başka atama girdi → sunucu 409; "Atama bu arada değişti; geri alınmadı." ve masa yenilenir', async () => {
    vi.mocked(atamaApi.kaldir).mockRejectedValue(new ApiHata(409, 'ATAMA_DEGISTI', 'sunucu'))
    const w = await kur()
    await satirlar(w)[0]!.trigger('click')
    await flushPromises()
    await temizSatir(w).trigger('click')
    await flushPromises()
    durum[0]!.makinistler = [
      makinist({ atamaId: 999, makinistId: 99, ad: 'Başka Kişi', sicil: 'D099' }),
    ] // başka atamacı rolü değiştirdi
    await w.get('[data-test="geri-al"]').trigger('click')
    await flushPromises()
    expect(vi.mocked(atamaApi.kaldir).mock.calls[0]![1]).toMatchObject({ atamaId: 101 }) // yanlış kişiyi silmemek için kimlikle
    expect(w.get('[role="status"]').text()).toBe('Atama bu arada değişti; geri alınmadı.')
    expect(w.text()).toContain('Başka Kişi') // gerçek atama yerinde, ekran güncel
  })

  it('AK-4.5: şerit 10 sn sonra kapanır; üstüne gelince durur, ayrılınca 10 sn yeniden başlar', async () => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] })
    const w = await kur()
    await satirlar(w)[0]!.trigger('click')
    await flushPromises()
    await temizSatir(w).trigger('click')
    await flushPromises()
    const serit = () => w.find('[data-test="geri-al-seridi"]')
    expect(serit().exists()).toBe(true)
    await serit().trigger('pointerenter')
    vi.advanceTimersByTime(60_000)
    await w.vm.$nextTick()
    expect(serit().exists()).toBe(true)
    await serit().trigger('pointerleave')
    vi.advanceTimersByTime(9_999)
    await w.vm.$nextTick()
    expect(serit().exists()).toBe(true)
    vi.advanceTimersByTime(1)
    await w.vm.$nextTick()
    expect(serit().exists()).toBe(false)
  })

  it('AK-4.8/MK-154: N tuşu yalnız masa/panel odaktayken sıradaki boşa geçer; yazı alanında etkisiz', async () => {
    const w = await kur()
    await satirlar(w)[0]!.trigger('click')
    await flushPromises()
    await satirlar(w)[0]!.trigger('keydown', { key: 'n' })
    await flushPromises()
    expect(satirlar(w)[1]!.attributes('aria-selected')).toBe('true')
    // yazı alanı (rol radyosu / gün girdisi): kısayol çalışmaz
    await w.get('input[type="radio"]').trigger('keydown', { key: 'n' })
    await flushPromises()
    expect(satirlar(w)[1]!.attributes('aria-selected')).toBe('true')
    // masa dışında (üst çubuk gün girdisi) hiç dinlenmez
    await w.get('#atama-gun').trigger('keydown', { key: 'n' })
    expect(satirlar(w)[1]!.attributes('aria-selected')).toBe('true')
    // modifiyeli N (Ctrl+N tarayıcı) yok sayılır
    await satirlar(w)[1]!.trigger('keydown', { key: 'n', ctrlKey: true })
    expect(satirlar(w)[1]!.attributes('aria-selected')).toBe('true')
  })

  it('"Sıradaki boş (N)" düğmesi görünür, seçimi ilerletir ve panele odak verir; boş kalmayınca bildirir', async () => {
    const w = await kur()
    const dugme = () => w.find('[data-test="siradaki-bos"]')
    expect(dugme().attributes('aria-keyshortcuts')).toBe('N')
    await dugme().trigger('click')
    await flushPromises()
    expect(satirlar(w)[0]!.attributes('aria-selected')).toBe('true')
    expect(w.get('aside').element.contains(document.activeElement)).toBe(true)
  })

  it('boş sefer yoksa "Sıradaki boş" düğmesi görünmez', async () => {
    durum = [satir(4, { makinistler: [makinist()] })]
    const w = await kur()
    expect(w.find('[data-test="siradaki-bos"]').exists()).toBe(false)
  })

  it('Ctrl+Z (masa odaktayken) Geri al yapar; şerit yokken tarayıcıya bırakır', async () => {
    vi.mocked(atamaApi.kaldir).mockResolvedValue(undefined)
    const w = await kur()
    await satirlar(w)[0]!.trigger('click')
    await flushPromises()
    await satirlar(w)[0]!.trigger('keydown', { key: 'z', ctrlKey: true }) // şerit yok
    expect(atamaApi.kaldir).not.toHaveBeenCalled()
    await temizSatir(w).trigger('click')
    await flushPromises()
    await satirlar(w)[1]!.trigger('keydown', { key: 'z', ctrlKey: true })
    await flushPromises()
    expect(atamaApi.kaldir).toHaveBeenCalledTimes(1)
  })

  it('AK-4.9: çift tetik (Enter tekrarı) ikinci ata çağrısı üretmez', async () => {
    const w = await kur()
    await satirlar(w)[0]!.trigger('click')
    await flushPromises()
    const aday = temizSatir(w)
    void aday.trigger('click')
    void aday.trigger('keydown', { key: 'Enter' })
    await flushPromises()
    expect(atamaApi.ata).toHaveBeenCalledTimes(1)
  })

  it('AK-4.10: sunucu reddederse (422) mesaj role=alert, diyalog zorlanmaz, gün yenilenir', async () => {
    vi.mocked(atamaApi.ata).mockRejectedValue(
      new ApiHata(422, 'KURAL_IHLALI', 'Kural ihlali: D007 bu sefere atanamaz.', {
        ihlaller: [
          {
            kod: 'K-11',
            siddet: 'ENGEL',
            olculen: 8,
            esik: 11,
            birim: 's',
            metin: 'Dinlenme 8 s < 11 s',
          },
        ],
      }),
    )
    const w = await kur()
    await satirlar(w)[0]!.trigger('click')
    await flushPromises()
    const gunOnce = vi.mocked(atamaApi.gun).mock.calls.length
    await temizSatir(w).trigger('click')
    await flushPromises()
    const uyari = w.get('[role="alert"]')
    expect(uyari.text()).toContain('Kural ihlali: D007 bu sefere atanamaz.')
    expect(uyari.text()).toContain('Dinlenme 8 s < 11 s')
    expect(w.find('dialog').exists()).toBe(false)
    expect(w.find('[data-test="geri-al-seridi"]').exists()).toBe(false)
    expect(vi.mocked(atamaApi.gun).mock.calls.length).toBeGreaterThan(gunOnce)
  })

  it('MK-154: odak aday panelindeyse yeni seferin adayları yüklenince Önerilen satırına taşınır', async () => {
    const w = await kur()
    await satirlar(w)[0]!.trigger('click')
    await flushPromises()
    ;(temizSatir(w).element as HTMLElement).focus()
    await temizSatir(w).trigger('click')
    await flushPromises()
    const odakli = document.activeElement as HTMLElement
    expect(odakli.tagName).toBe('TR')
    expect(odakli.textContent).toContain('Aday Bir')
    expect(odakli.textContent).toContain('Önerilen')
    expect(satirlar(w)[1]!.attributes('aria-selected')).toBe('true')
  })

  it('D1/B-01: sıradaki seferin aday listesi yüklenirken eski listeden tık/Enter atamaz; liste gelince atar', async () => {
    const w = await kur()
    await satirlar(w)[0]!.trigger('click')
    await flushPromises()
    // 2. seferin listesi geç döner (bayat pencere)
    let bitir!: (a: Aday[]) => void
    vi.mocked(atamaApi.adaylar).mockImplementationOnce(
      () => new Promise<Aday[]>((coz) => (bitir = coz)),
    )
    await temizSatir(w).trigger('click') // 1. atama: sıradaki 2. sefere geçer
    await flushPromises()
    expect(atamaApi.ata).toHaveBeenCalledTimes(1)
    expect(w.get('table.aday-tablo').attributes('aria-busy')).toBe('true')
    // hızlı ikinci Enter / tık: hâlâ ESKİ seferin listesi
    await temizSatir(w).trigger('keydown', { key: 'Enter' })
    await temizSatir(w).trigger('click')
    await flushPromises()
    expect(atamaApi.ata).toHaveBeenCalledTimes(1)
    bitir([uyarili, temiz])
    await flushPromises()
    await temizSatir(w).trigger('click')
    await flushPromises()
    expect(atamaApi.ata).toHaveBeenCalledTimes(2)
    expect(atamaApi.ata).toHaveBeenLastCalledWith(2, { makinistId: 7, rol: 'ASIL' })
  })

  it('D1/QA-5: Geri al şeridi akışta yer tutmaz (sabit konum): kapanınca içerik kaymaz', async () => {
    const w = await kur()
    await satirlar(w)[0]!.trigger('click')
    await flushPromises()
    await temizSatir(w).trigger('click')
    await flushPromises()
    expect(w.get('[data-test="geri-al-seridi"]').classes()).toContain('geri-al-seridi')
    const kaynak = readFileSync(join(process.cwd(), 'src/features/atama/AtamaMasasi.vue'), 'utf8')
    expect(kaynak).toMatch(/[.]geri-al-seridi [{][^}]*position: fixed/)
  })

  it('MK-154: odak masada/başka yerdeyse odak çalınmaz', async () => {
    const w = await kur()
    await satirlar(w)[0]!.trigger('click')
    await flushPromises()
    const dis = w.get('#atama-gun').element as HTMLElement
    dis.focus()
    await temizSatir(w).trigger('click') // fare tıklaması odağı taşımaz (jsdom)
    await flushPromises()
    expect(document.activeElement).toBe(dis)
  })
})
