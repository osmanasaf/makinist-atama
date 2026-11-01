import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'

vi.mock('./benimApi', () => ({
  benimApi: { parolaDegistir: vi.fn(), ozet: vi.fn(), belgeler: vi.fn() },
}))

import { ApiHata } from '@/api/istemci'
import BenimBelgeler from './BenimBelgeler.vue'
import BenimOzet from './BenimOzet.vue'
import BenimParola from './BenimParola.vue'
import { benimApi, type BenimOzet as Ozet } from './benimApi'

/** 07-qa UX bulguları: makinist dilinde açıklamalar, boşalmayan alan, belge için ne yapılacağı. */
describe('Benim: kullanılabilirlik (07-qa UX)', () => {
  beforeEach(() => vi.resetAllMocks())

  it('parola kuralı hatasında (yeni parola) eski parola alanı boşalmaz', async () => {
    vi.mocked(benimApi.parolaDegistir).mockRejectedValue(
      new ApiHata(400, 'DOGRULAMA', 'Geçersiz.', {
        alanlar: { yeniParola: 'Parola giriş adını (sicil ya da e-posta) içeremez.' },
      }),
    )
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/giris', name: 'giris', component: { template: '<div />' } }],
    })
    const w = mount(BenimParola, { global: { plugins: [router] } })
    await w.get('#eski-parola').setValue('gecici-parola-1')
    await w.get('#yeni-parola').setValue('d001-yeni-parola-1')
    await w.get('#tekrar-parola').setValue('d001-yeni-parola-1')
    await w.get('form').trigger('submit')
    await flushPromises()

    expect(w.get('#yeni-parola-hata').text()).toContain('içeremez')
    expect((w.get('#eski-parola').element as HTMLInputElement).value).toBe('gecici-parola-1')
  })

  it('özet: gece saati açıklanır; ortalamaya fark ve ortalama görev saati hiç gösterilmez (OK-35)', async () => {
    const bos: Ozet = {
      ay: '2026-11',
      adaleteDahil: true,
      planlananDahil: true,
      seferSayisi: 0,
      gorevSaat: 0,
      km: 0,
      kmBilinmeyenSefer: 0,
      geceSaat: 0,
      izinGunu: 5,
    }
    vi.mocked(benimApi.ozet).mockResolvedValue(bos)
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/benim/parola', component: { template: '<div />' } }],
    })
    const w = mount(BenimOzet, { global: { plugins: [router] } })
    await flushPromises()

    expect(w.text()).toContain('gece vardiyası saatlerine denk gelen')
    expect(w.text()).not.toMatch(/ortalama/i)
    expect(w.text()).not.toContain('Ortalamaya fark')
  })

  it('belge: süresi dolan ya da yaklaşan için "başvurun" cümlesi; geçerli belgede yok', async () => {
    vi.mocked(benimApi.belgeler).mockResolvedValue({
      bugun: '2026-10-12',
      uyariGun: 30,
      enKotu: 'DOLDU',
      belgeler: [
        { tur: 'SAGLIK', ad: 'Sağlık raporu', bitis: '2026-10-11', kalanGun: -1, durum: 'DOLDU' },
        { tur: 'EHLIYET', ad: 'Ehliyet', bitis: '2026-11-01', kalanGun: 20, durum: 'YAKLASIYOR' },
        {
          tur: 'PSIKOTEKNIK',
          ad: 'Psikoteknik',
          bitis: '2028-01-01',
          kalanGun: 446,
          durum: 'GECERLI',
        },
      ],
    })
    const w = mount(BenimBelgeler)
    await flushPromises()

    const satirlar = w.findAll('li').map((li) => li.text())
    expect(satirlar[0]).toContain('atamacınıza ya da eğitim birimine başvurun')
    expect(satirlar[1]).toContain('başvurun')
    expect(satirlar[2]).not.toContain('başvurun')
  })
})
