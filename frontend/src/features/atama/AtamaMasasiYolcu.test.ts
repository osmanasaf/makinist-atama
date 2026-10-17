import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'

vi.mock('./atamaApi', async (orijinal) => ({
  ...(await orijinal<typeof import('./atamaApi')>()),
  atamaApi: {
    gun: vi.fn(),
    adaylar: vi.fn(),
    ata: vi.fn(),
    kaldir: vi.fn(),
    kirmiziSayisi: vi.fn(),
    oneri: vi.fn(),
    uygunSeferler: vi.fn(),
    otomatik: vi.fn(),
    seferIptal: vi.fn(),
    seferGecikme: vi.fn(),
  },
}))

import AtamaMasasi from './AtamaMasasi.vue'
import { atamaApi, type GunListesi } from './atamaApi'
import { makinist, satir } from './testVerisi'

const bos = { template: '<div />' }
const asil = makinist({ atamaId: 1, makinistId: 1, sicil: 'D001', ad: 'Asıl Kişi' })
const yardimci = makinist({
  atamaId: 2,
  rol: 'YARDIMCI',
  makinistId: 2,
  sicil: 'D002',
  ad: 'Yardımcı Kişi',
})
const yolcu1 = makinist({ atamaId: 3, rol: 'YOLCU', makinistId: 3, sicil: 'D003', ad: 'Yolcu Bir' })
const yolcu2 = makinist({ atamaId: 4, rol: 'YOLCU', makinistId: 4, sicil: 'D004', ad: 'Yolcu İki' })

function gunVerisi(): GunListesi {
  return {
    tarih: '2026-10-12',
    uretimZamani: '2026-10-12T07:00:00Z',
    satirlar: [
      satir(1, { makinistler: [yolcu1, asil, yolcu2, yardimci] }), // karışık sırada gelir
      satir(2, { makinistler: [yolcu1] }), // yalnız yolcu: ASIL yok → BOŞ
    ],
  }
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

describe('AtamaMasasi: yolcu (F2b)', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(atamaApi.gun).mockImplementation(() => Promise.resolve(gunVerisi()))
    vi.mocked(atamaApi.adaylar).mockResolvedValue([])
    vi.mocked(atamaApi.kaldir).mockResolvedValue(undefined as never)
    vi.mocked(atamaApi.kirmiziSayisi).mockResolvedValue({
      bas: '2026-10-12',
      bit: '2026-10-19',
      kirmiziSefer: 0,
      kirmiziAtama: 0,
    })
  })

  it('görevliler asıl, yardımcı, yolcular sırasıyla; yolcu "Yolcu:" önekiyle (öneksiz kalmaz)', async () => {
    const w = await kur()
    const hucre = w.findAll('tr.sefer-satiri')[0]!.find('.makinist-sutun')
    const gorevliler = hucre.findAll('.gorevli').map((g) => g.find('span').text())

    expect(gorevliler).toEqual([
      'Asıl Kişi (D001)',
      'Yrd: Yardımcı Kişi (D002)',
      'Yolcu: Yolcu Bir (D003)',
      'Yolcu: Yolcu İki (D004)',
    ])
  })

  it('"BOŞ" yalnız ASIL yokluğuna bağlı: yalnız yolcusu olan sefer yine BOŞ, asıllı sefer değil', async () => {
    const w = await kur()
    const satirlar = w.findAll('tr.sefer-satiri')

    expect(satirlar[0]!.text()).not.toContain('BOŞ')
    expect(satirlar[1]!.text()).toContain('BOŞ')
    expect(satirlar[1]!.text()).toContain('Yolcu: Yolcu Bir (D003)')
  })

  it('"Yerine ata" yalnız asıl varsa; yolcu satırında yok', async () => {
    const w = await kur()
    const satirlar = w.findAll('tr.sefer-satiri')

    expect(satirlar[0]!.findAll('button').some((b) => b.text() === 'Yerine ata')).toBe(true)
    expect(satirlar[1]!.findAll('button').some((b) => b.text() === 'Yerine ata')).toBe(false)
  })

  it('yolcuyu kaldır: makinist kimliği gönderilir (bir seferde birden çok yolcu olabilir)', async () => {
    const w = await kur()
    const kaldirDugmesi = w
      .findAll('tr.sefer-satiri')[0]!
      .findAll('button')
      .find((b) => b.attributes('aria-label')?.includes('Yolcu Bir'))!
    await kaldirDugmesi.trigger('click')
    await flushPromises()

    const dialog = w.find('dialog')
    expect(dialog.text()).toContain('Yolcu')
    await dialog.find('select').setValue('IZIN')
    await dialog.find('form').trigger('submit')
    await flushPromises()

    expect(atamaApi.kaldir).toHaveBeenCalledWith(
      1,
      expect.objectContaining({ rol: 'YOLCU', makinistId: 3, neden: 'IZIN' }),
    )
  })
})
