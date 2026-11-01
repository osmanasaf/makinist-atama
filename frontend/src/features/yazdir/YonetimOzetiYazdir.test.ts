import { readFileSync } from 'node:fs'
import { join } from 'node:path'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'

vi.mock('@/features/rapor/ozetApi', async (orijinal) => ({
  ...(await orijinal<typeof import('@/features/rapor/ozetApi')>()),
  ozetApi: { getir: vi.fn(), denetimPaketi: vi.fn() },
}))

import { kurumGuncelle, kurumSifirla } from '@/composables/kurum'
import { kullanici } from '@/composables/oturum'
import { ozetApi } from '@/features/rapor/ozetApi'
import { ornek } from '@/features/rapor/testVerisi'
import YonetimOzetiYazdir from './YonetimOzetiYazdir.vue'
import routes from './routes'

const bos = { template: '<div />' }

async function kur(props: Record<string, string> = { ay: '2026-10' }) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/rapor/ozet', name: 'rapor-ozet', component: bos }],
  })
  await router.push('/rapor/ozet')
  const w = mount(YonetimOzetiYazdir, { props, global: { plugins: [router] } })
  await flushPromises()
  return w
}

describe('YonetimOzetiYazdir (F4, FR-4.4, AK-4.7)', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(ozetApi.getir).mockResolvedValue(ornek())
    kullanici.value = {
      id: 1,
      eposta: 'a@b.c',
      adSoyad: 'Mehmet Demir',
      rol: 'YONETICI',
      aktif: true,
    }
    kurumGuncelle({ ad: 'Örnek İşletme (demo)', logoVar: false, logoSurum: 0 })
  })

  it('ay ile ister; başlıkta kurum adı ve dönem, altbilgide sunucu zamanı, yazdıran ve kayıt sayısı', async () => {
    const w = await kur()
    expect(ozetApi.getir).toHaveBeenCalledWith('2026-10')
    expect(w.get('[data-test="yazdir-kurum"]').text()).toContain('Örnek İşletme (demo)')
    expect(w.get('h1').text()).toBe('Yönetim özeti')
    expect(w.get('.yazdir-filtre').text()).toContain('Dönem: 2026-10 (takvim ayı)')
    const alt = w.get('footer').text().replace(/\s+/g, ' ')
    expect(alt).toContain('Son güncelleme: 12.10.2026 10:20')
    expect(alt).toContain('Yazdıran: Mehmet Demir')
    expect(alt).toContain('Kayıt sayısı: 40')
    expect(w.get('.no-print button').attributes('disabled')).toBeUndefined()
  })

  it('kurum adı girilmemişse ürün adı başlıkta yer almaz ("kurum adı" yalnız girilmişse); gövde ekranla aynı sayılar, çubuk yok', async () => {
    kurumSifirla()
    const w = await kur()
    expect(w.find('[data-test="yazdir-kurum"]').exists()).toBe(false)
    expect(w.get('[data-test="doluluk"]').text().replace(/\s+/g, ' ')).toContain(
      '6 / 9 sefer dolu (%66,7)',
    )
    expect(w.get('[data-test="adalet-std"]').text()).toBe('8,16 sa')
    expect(w.find('.cubuk').exists()).toBe(false)
    expect(w.findAll('table').every((t) => t.classes().includes('yazdir-tablo'))).toBe(true)
  })

  it('demo veri varsa dönem satırında "DEMO – gerçek değil"', async () => {
    vi.mocked(ozetApi.getir).mockResolvedValue(ornek({ demo: true }))
    const w = await kur()
    expect(w.get('.yazdir-filtre').text()).toContain('DEMO – gerçek değil')
  })

  it('veri hazır değilken (hata) basılmaz: Yazdır pasif, hata görünür', async () => {
    vi.mocked(ozetApi.getir).mockRejectedValue(new Error('x'))
    const w = await kur()
    expect(w.find('[data-test="doluluk"]').exists()).toBe(false)
    expect(w.get('.no-print button').attributes('disabled')).toBeDefined()
    expect(w.get('[role="alert"]').text()).toContain('Özet yüklenemedi')
  })

  it('ay yoksa istek atılmaz, "Ay gerekli." gösterilir', async () => {
    const w = await kur({})
    expect(ozetApi.getir).not.toHaveBeenCalled()
    expect(w.get('[role="alert"]').text()).toBe('Ay gerekli.')
  })

  it('yazdır rotası: menüsüz (meta.yazdir), sorgu özelliklere çevrilir', () => {
    const router = createRouter({ history: createMemoryHistory(), routes })
    const r = router.resolve('/yazdir/ozet?ay=2026-10')
    expect(r.name).toBe('yazdir-ozet')
    expect(r.meta.yazdir).toBe(true)
    expect(r.meta.menu).toBeUndefined()
    const props = r.matched[0]!.props.default as (r: unknown) => Record<string, unknown>
    expect(props(r)).toEqual({ ay: '2026-10' })
  })

  it('kaynak: ham renk ve emoji yok (belirteç ve currentcolor)', () => {
    for (const dosya of [
      'src/features/yazdir/YonetimOzetiYazdir.vue',
      'src/features/rapor/OzetGovdesi.vue',
      'src/features/rapor/YonetimOzeti.vue',
    ]) {
      const kaynak = readFileSync(join(process.cwd(), dosya), 'utf8')
      expect(kaynak).not.toMatch(/#[0-9a-fA-F]{3,8}\b|rgba?\(|hsla?\(/)
      expect(kaynak).not.toMatch(/\p{Extended_Pictographic}/u)
    }
  })
})
