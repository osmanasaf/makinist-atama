import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { ApiHata } from '@/api/istemci'

vi.mock('./puantajApi', async (orijinal) => ({
  ...(await orijinal<typeof import('./puantajApi')>()),
  puantajApi: { rapor: vi.fn() },
}))

import { kullanici } from '@/composables/oturum'
import { puantajApi, type PuantajYaniti } from './puantajApi'
import PuantajYazdir from './PuantajYazdir.vue'

const bos = { template: '<div />' }

const yanit: PuantajYaniti = {
  ay: '2026-10',
  uretimZamani: '2026-10-12T07:20:00Z',
  ureten: 'Mehmet Demir',
  etiket: 'Puantaj ön hesabı — ücret bordrosu değildir',
  demo: true,
  saatUcretiGirildi: false,
  kmBirimGirildi: false,
  oranlar: {
    saatUcreti: null,
    fazlaMesaiCarpani: 1.5,
    geceTazminatOrani: 0.25,
    kmBirimTutari: null,
    asgariGunlukSaat: 8,
    normalHaftalikSaat: 45,
    gecePenceresi: '20:00–07:00',
  },
  satirlar: [
    {
      makinistId: 1,
      sicil: 'D001',
      adSoyad: 'Ali Veli',
      calisilanGun: 20,
      fiiliSaat: 160,
      ucretlendirilenSaat: 168,
      fazlaMesaiSaat: 12,
      geceSaat: 30,
      km: 4200,
      kmBilinmeyenSefer: 0,
      fazlaMesaiTl: null,
      geceTl: null,
      kmTl: null,
    },
  ],
}

async function kur(props: Record<string, string> = { ay: '2026-10', makinist: '1' }) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/rapor/puantaj', name: 'rapor-puantaj', component: bos }],
  })
  await router.push('/rapor/puantaj')
  const w = mount(PuantajYazdir, { props, global: { plugins: [router] } })
  await flushPromises()
  return w
}

describe('PuantajYazdir', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(puantajApi.rapor).mockResolvedValue(yanit)
    kullanici.value = { id: 1, eposta: 'a@b.c', adSoyad: 'Ayşe Kaya', rol: 'ATAMACI', aktif: true }
  })

  it('sorgudan gelen süzgeçle ister; başlık, süzgeç, DEMO ve ön hesap etiketi, altbilgi', async () => {
    const w = await kur()
    expect(puantajApi.rapor).toHaveBeenCalledWith({ ay: '2026-10', makinist: '1' })
    expect(w.get('h1').text()).toBe('Puantaj ön hesabı')
    expect(w.get('.yazdir-filtre').text()).toBe('Ay: 2026-10 · Makinist: D001 — Ali Veli')
    expect(w.text()).toContain('DEMO – gerçek değil')
    expect(w.text()).toContain('ücret bordrosu değildir')
    expect(w.get('table').classes()).toContain('yazdir-tablo')
    expect(w.get('footer').text().replace(/\s+/g, ' ')).toContain('Kayıt sayısı: 1')
  })

  it('hata: gösterilir, Yazdır kapalı; ay eksikse istek atılmaz', async () => {
    vi.mocked(puantajApi.rapor).mockRejectedValue(new ApiHata(400, 'DOGRULAMA', 'Geçersiz ay.'))
    const w = await kur()
    expect(w.get('[role="alert"]').text()).toBe('Geçersiz ay.')
    expect(w.get('.no-print button').attributes('disabled')).toBeDefined()

    vi.mocked(puantajApi.rapor).mockClear()
    const eksik = await kur({ ay: '' })
    expect(puantajApi.rapor).not.toHaveBeenCalled()
    expect(eksik.get('[role="alert"]').text()).toContain('Ay')
  })
})
