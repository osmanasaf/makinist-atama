import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'

vi.mock('@/composables/oturum', () => ({ girisYap: vi.fn() }))

import { ApiHata } from '@/api/istemci'
import { girisYap } from '@/composables/oturum'
import GirisSayfasi from './GirisSayfasi.vue'

async function kur(sorgu = '') {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: { template: '<div>ana</div>' } },
      { path: '/giris', name: 'giris', component: GirisSayfasi },
      { path: '/atama', component: { template: '<div>atama</div>' } },
    ],
  })
  await router.push('/giris' + sorgu)
  await router.isReady()
  const w = mount(GirisSayfasi, { global: { plugins: [router] } })
  return { w, router }
}

async function doldurVeGonder(w: ReturnType<typeof mount>) {
  await w.get('#eposta').setValue('  ayse@ornek.test ')
  await w.get('#parola').setValue('gizli-parola-123')
  await w.get('form').trigger('submit')
  await flushPromises()
}

describe('GirisSayfasi', () => {
  beforeEach(() => vi.resetAllMocks())

  it('başarılı girişte e-postayı kırpıp ana sayfaya gider', async () => {
    vi.mocked(girisYap).mockResolvedValue()
    const { w, router } = await kur()
    await doldurVeGonder(w)
    expect(girisYap).toHaveBeenCalledWith('ayse@ornek.test', 'gizli-parola-123')
    expect(router.currentRoute.value.path).toBe('/')
  })

  it('oturum düşünce (oturum=bitti) kullanıcıya nedeni söylenir; ilk ziyarette söylenmez', async () => {
    const { w } = await kur('?git=/atama&oturum=bitti')
    expect(w.get('[role=status]').text()).toContain('Oturumunuz sona erdi')
    const ilk = await kur('?git=/atama')
    expect(ilk.w.find('[role=status]').exists()).toBe(false)
  })

  it('dönüş adresi (git) varsa oraya gider', async () => {
    vi.mocked(girisYap).mockResolvedValue()
    const { w, router } = await kur('?git=/atama')
    await doldurVeGonder(w)
    expect(router.currentRoute.value.path).toBe('/atama')
  })

  it('dış adres (//kotu.site) dönüş adresi olarak kabul edilmez', async () => {
    vi.mocked(girisYap).mockResolvedValue()
    const { w, router } = await kur('?git=//kotu.site')
    await doldurVeGonder(w)
    expect(router.currentRoute.value.path).toBe('/')
  })

  it('hatalı girişte sunucu mesajını gösterir ve parolayı temizler', async () => {
    vi.mocked(girisYap).mockRejectedValue(
      new ApiHata(401, 'GIRIS_HATALI', 'E-posta veya parola hatalı.'),
    )
    const { w, router } = await kur()
    await doldurVeGonder(w)
    expect(w.get('[role=alert]').text()).toBe('E-posta veya parola hatalı.')
    expect((w.get('#parola').element as HTMLInputElement).value).toBe('')
    expect(router.currentRoute.value.path).toBe('/giris')
  })
})

describe('GirisSayfasi: makinist girişi', () => {
  beforeEach(() => vi.resetAllMocks())

  it('alan "E-posta veya sicil" der ve sicil (e-posta olmayan) kabul eder: type=text', async () => {
    vi.mocked(girisYap).mockResolvedValue()
    const { w } = await kur()
    expect(w.get('label[for=eposta]').text()).toBe('E-posta veya sicil')
    expect(w.get('#eposta').attributes('type')).toBe('text')
    await w.get('#eposta').setValue(' D001 ')
    await w.get('#parola').setValue('gizli-parola-123')
    await w.get('form').trigger('submit')
    await flushPromises()
    expect(girisYap).toHaveBeenCalledWith('D001', 'gizli-parola-123')
  })

  it('parola değiştirdikten sonra (parola=degisti) kullanıcıya yeni parolayla girmesi söylenir', async () => {
    const { w } = await kur('?parola=degisti')
    expect(w.get('[role=status]').text()).toContain('Parolanız değiştirildi')
  })
})
