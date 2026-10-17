import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, type VueWrapper } from '@vue/test-utils'
import { defineComponent, h, ref } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'
import {
  ayrilabilirMi,
  cikisKorumasiniBagla,
  CIKIS_SORUSU,
  useCikisKorumasi,
} from './cikis-korumasi'

const acik: VueWrapper[] = []
afterEach(() => {
  acik.splice(0).forEach((w) => w.unmount())
  vi.restoreAllMocks()
})

/** Kirli bayrağı dışarıdan oynatılan sahte form. */
function formKur(kirli = ref(false), kaydet?: () => Promise<void>) {
  const w = mount(
    defineComponent({
      setup() {
        useCikisKorumasi(() => kirli.value, kaydet)
        return () => h('div')
      },
    }),
  )
  acik.push(w)
  return { w, kirli }
}

async function yonlendirici() {
  const bos = { render: () => null }
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', name: 'ana', component: bos },
      { path: '/form', name: 'form', component: bos },
      { path: '/giris', name: 'giris', component: bos },
    ],
  })
  cikisKorumasiniBagla(router)
  await router.push('/form')
  return router
}

describe('useCikisKorumasi (kaydedilmemiş veri için çıkış koruması)', () => {
  let onay: ReturnType<typeof vi.spyOn>
  beforeEach(() => {
    onay = vi.spyOn(window, 'confirm').mockReturnValue(false)
  })

  it('beforeunload: kirliyken tarayıcı uyarısı istenir (varsayılan engellenir), temizken istenmez', () => {
    const { kirli } = formKur()
    const olay = () => {
      const e = new Event('beforeunload', { cancelable: true })
      window.dispatchEvent(e)
      return e.defaultPrevented
    }
    expect(olay()).toBe(false)
    kirli.value = true
    expect(olay()).toBe(true)
  })

  it('bileşen sökülünce beforeunload dinleyicisi ve koruma kalkar', async () => {
    const { w, kirli } = formKur(ref(true))
    w.unmount()
    acik.pop()
    const e = new Event('beforeunload', { cancelable: true })
    window.dispatchEvent(e)
    expect(e.defaultPrevented).toBe(false)
    expect(kirli.value).toBe(true)
    expect(await ayrilabilirMi()).toBe(true)
  })

  it('uygulama içi gezinme: kirliyken onay sorulur; Vazgeç gezinmeyi İPTAL eder, onay geçirir', async () => {
    const router = await yonlendirici()
    formKur(ref(true))
    await router.push('/')
    expect(onay).toHaveBeenCalledWith(CIKIS_SORUSU)
    expect(router.currentRoute.value.path).toBe('/form') // kalındı

    onay.mockReturnValue(true)
    await router.push('/')
    expect(router.currentRoute.value.path).toBe('/')
  })

  it('temizken hiç sorulmaz', async () => {
    const router = await yonlendirici()
    formKur()
    await router.push('/')
    expect(onay).not.toHaveBeenCalled()
    expect(router.currentRoute.value.path).toBe('/')
  })

  it('giriş sayfasına gidiş (oturum düştü / çıkış) korumaya takılmaz', async () => {
    const router = await yonlendirici()
    formKur(ref(true))
    await router.push('/giris')
    expect(onay).not.toHaveBeenCalled()
    expect(router.currentRoute.value.path).toBe('/giris')
  })

  it('kaydet verilmişse önce kaydı dener; kayıt kirliyi temizlerse hiç sorulmaz', async () => {
    const router = await yonlendirici()
    const kirli = ref(true)
    const kaydet = vi.fn(async () => {
      kirli.value = false
    })
    formKur(kirli, kaydet)
    await router.push('/')
    expect(kaydet).toHaveBeenCalledTimes(1)
    expect(onay).not.toHaveBeenCalled()
    expect(router.currentRoute.value.path).toBe('/')
  })

  it('kayıt başarısız olup hâlâ kirliyse onay sorulur (sessiz kayıp yok)', async () => {
    const router = await yonlendirici()
    const kaydet = vi.fn(async () => {}) // 409/ağ hatası: kirli kalır
    formKur(ref(true), kaydet)
    await router.push('/')
    expect(kaydet).toHaveBeenCalledTimes(1)
    expect(onay).toHaveBeenCalledTimes(1)
    expect(router.currentRoute.value.path).toBe('/form')
  })

  it('birden çok kirli birimde tek onay sorulur', async () => {
    const router = await yonlendirici()
    formKur(ref(true))
    formKur(ref(true))
    await router.push('/')
    expect(onay).toHaveBeenCalledTimes(1)
  })
})
