import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'

vi.mock('./benimApi', () => ({ benimApi: { parolaDegistir: vi.fn() } }))

import { ApiHata } from '@/api/istemci'
import { kullanici } from '@/composables/oturum'
import BenimParola from './BenimParola.vue'
import { benimApi } from './benimApi'

async function kur() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/giris', name: 'giris', component: { template: '<div />' } },
      { path: '/benim/parola', name: 'benim-parola', component: BenimParola },
    ],
  })
  await router.push('/benim/parola')
  const w = mount(BenimParola, { global: { plugins: [router] } })
  return { w, router }
}

async function doldur(
  w: Awaited<ReturnType<typeof kur>>['w'],
  eski: string,
  yeni: string,
  tekrar: string,
) {
  await w.get('#eski-parola').setValue(eski)
  await w.get('#yeni-parola').setValue(yeni)
  await w.get('#tekrar-parola').setValue(tekrar)
  await w.get('form').trigger('submit')
  await flushPromises()
}

describe('BenimParola', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    kullanici.value = {
      id: 1,
      eposta: 'd001',
      adSoyad: 'Ali',
      rol: 'MAKINIST',
      aktif: true,
      makinistId: 5,
      parolaDegistirmeli: true,
    }
  })

  it('zorunlu ilk değişimde geçici parola uyarısı çıkar', async () => {
    const { w } = await kur()
    expect(w.get('[role=status]').text()).toContain('geçici parola')
  })

  it('istemci doğrulaması: kısa parola, aynı parola, uyuşmayan tekrar; hata mesajları alanla ilişkili', async () => {
    const { w } = await kur()
    await doldur(w, 'gecici-parola-1', 'kisa', 'kisa')
    expect(benimApi.parolaDegistir).not.toHaveBeenCalled()
    expect(w.get('#yeni-parola').attributes('aria-describedby')).toBe('yeni-parola-hata')
    expect(w.get('#yeni-parola-hata').text()).toContain('en az 10')
    await doldur(w, 'gecici-parola-1', 'gecici-parola-1', 'gecici-parola-1')
    expect(w.get('#yeni-parola-hata').text()).toContain('eskisiyle aynı')
    await doldur(w, 'gecici-parola-1', 'yeni-parola-uzun-1', 'baska-bir-sey-123')
    expect(w.get('#tekrar-parola-hata').text()).toContain('aynı değil')
    expect(benimApi.parolaDegistir).not.toHaveBeenCalled()
  })

  it('başarıda oturum yerelde temizlenir ve giriş sayfasına yönlenir (parola=degisti)', async () => {
    vi.mocked(benimApi.parolaDegistir).mockResolvedValue(undefined)
    const { w, router } = await kur()
    await doldur(w, 'gecici-parola-1', 'yeni-parola-uzun-1', 'yeni-parola-uzun-1')
    expect(benimApi.parolaDegistir).toHaveBeenCalledWith('gecici-parola-1', 'yeni-parola-uzun-1')
    expect(kullanici.value).toBeNull()
    expect(router.currentRoute.value.name).toBe('giris')
    expect(router.currentRoute.value.query.parola).toBe('degisti')
  })

  it('yanlış eski parola sunucu hatasını gösterir ve eski parola alanını boşaltır', async () => {
    vi.mocked(benimApi.parolaDegistir).mockRejectedValue(
      new ApiHata(400, 'PAROLA_HATALI', 'Eski parola hatalı.', {
        alanlar: { eskiParola: 'Eski parola hatalı.' },
      }),
    )
    const { w } = await kur()
    await doldur(w, 'yanlis-parola-1', 'yeni-parola-uzun-1', 'yeni-parola-uzun-1')
    expect(w.get('#eski-parola-hata').text()).toBe('Eski parola hatalı.')
    expect((w.get('#eski-parola').element as HTMLInputElement).value).toBe('')
  })

  it('QA BUG-6: doğrulama hatasında odak ilk geçersiz alana gider ve hata metni role=alert ile duyurulur', async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/giris', name: 'giris', component: { template: '<div />' } }],
    })
    const w = mount(BenimParola, { global: { plugins: [router] }, attachTo: document.body })
    await w.get('#eski-parola').setValue('eski-parola-1')
    await w.get('#yeni-parola').setValue('kisa')
    await w.get('#tekrar-parola').setValue('kisa')
    await w.get('form').trigger('submit')
    await flushPromises()
    expect(document.activeElement).toBe(w.get('#yeni-parola').element)
    const hata = w.get('#yeni-parola-hata')
    expect(hata.attributes('role')).toBe('alert')
    expect(w.get('#yeni-parola').attributes('aria-describedby')).toBe('yeni-parola-hata')
    w.unmount()
  })
})
