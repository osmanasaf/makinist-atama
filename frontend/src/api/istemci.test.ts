import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  ApiHata,
  api,
  dosyaAdiOku,
  indir,
  istek,
  istekDurumlu,
  sorguMetni,
  UZUN_ISLEM_ZAMAN_ASIMI_MS,
  yetkisizIsleyiciKaydet,
} from './istemci'

function yanit(durum: number, govde?: unknown, tur = 'application/json') {
  return new Response(govde === undefined ? null : JSON.stringify(govde), {
    status: durum,
    headers: { 'Content-Type': tur },
  })
}

describe('istemci', () => {
  const fetchSahte = vi.fn()

  beforeEach(() => {
    vi.stubGlobal('fetch', fetchSahte)
    fetchSahte.mockReset()
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
  })
  afterEach(() => {
    vi.unstubAllGlobals()
    yetkisizIsleyiciKaydet(null)
  })

  it('GET başarılıysa JSON gövdeyi döner ve CSRF başlığı eklemez', async () => {
    document.cookie = 'XSRF-TOKEN=abc'
    fetchSahte.mockResolvedValue(yanit(200, { id: 1 }))
    expect(await api.get('/api/makinistler/1')).toEqual({ id: 1 })
    const [yol, secenek] = fetchSahte.mock.calls[0]!
    expect(yol).toBe('/api/makinistler/1')
    expect(secenek.method).toBe('GET')
    expect(secenek.headers['X-XSRF-TOKEN']).toBeUndefined()
  })

  it('değiştiren isteklerde X-XSRF-TOKEN başlığını çerezden gönderir', async () => {
    document.cookie = 'XSRF-TOKEN=a%2Fb%3D'
    fetchSahte.mockResolvedValue(yanit(201, { id: 5 }))
    await api.post('/api/makinistler', { ad: 'Demo Makinist 01' })
    const secenek = fetchSahte.mock.calls[0]![1]
    expect(secenek.headers['X-XSRF-TOKEN']).toBe('a/b=')
    expect(secenek.headers['Content-Type']).toBe('application/json')
    expect(secenek.body).toBe(JSON.stringify({ ad: 'Demo Makinist 01' }))
  })

  it('FormData (dosya yükleme) olduğu gibi gider; Content-Type başlığını tarayıcı sınırla birlikte kendisi koyar', async () => {
    document.cookie = 'XSRF-TOKEN=t2'
    fetchSahte.mockResolvedValue(yanit(201, { id: 9 }))
    const form = new FormData()
    form.append('dosya', '3')
    await api.post('/api/seferler/yukle', form)
    const secenek = fetchSahte.mock.calls[0]![1]
    expect(secenek.body).toBe(form)
    expect(secenek.headers['Content-Type']).toBeUndefined()
    expect(secenek.headers['X-XSRF-TOKEN']).toBe('t2')
  })

  it('PUT, PATCH ve DELETE de CSRF başlığı taşır', async () => {
    document.cookie = 'XSRF-TOKEN=t1'
    fetchSahte.mockImplementation(() => Promise.resolve(yanit(204)))
    await api.put('/api/x', {})
    await api.patch('/api/x', [])
    await api.sil('/api/x')
    for (const cagri of fetchSahte.mock.calls) {
      expect(cagri[1].headers['X-XSRF-TOKEN']).toBe('t1')
    }
  })

  it('istekDurumlu: gövdeyle birlikte HTTP durumunu döner (201 yeni, 200 mevcut kayıt ayrımı)', async () => {
    document.cookie = 'XSRF-TOKEN=t9'
    fetchSahte.mockResolvedValueOnce(yanit(201, { id: 5 }))
    expect(await istekDurumlu('POST', '/api/makinistler', { ad: 'A' })).toEqual({
      durum: 201,
      veri: { id: 5 },
    })
    fetchSahte.mockResolvedValueOnce(yanit(200, { id: 5 }))
    expect((await istekDurumlu('POST', '/api/makinistler', { ad: 'A' })).durum).toBe(200)
    const secenek = fetchSahte.mock.calls[0]![1]
    expect(secenek.method).toBe('POST')
    expect(secenek.headers['X-XSRF-TOKEN']).toBe('t9')
    expect(secenek.body).toBe(JSON.stringify({ ad: 'A' }))
  })

  it('istekDurumlu: hata yanıtı istek ile aynı biçimde ApiHata fırlatır', async () => {
    fetchSahte.mockResolvedValue(
      yanit(400, { title: 'x', status: 400, detail: 'Girdi geçersiz.', kod: 'DOGRULAMA' }),
    )
    const h = (await istekDurumlu('POST', '/api/makinistler', {}).catch(
      (e: unknown) => e,
    )) as ApiHata
    expect(h).toBeInstanceOf(ApiHata)
    expect(h.kod).toBe('DOGRULAMA')
  })

  it('204 yanıtında undefined döner', async () => {
    fetchSahte.mockResolvedValue(yanit(204))
    expect(await api.post('/api/auth/cikis')).toBeUndefined()
  })

  it('ProblemDetail gövdesini ApiHata alanlarına eşler', async () => {
    fetchSahte.mockResolvedValue(
      yanit(
        400,
        {
          type: 'about:blank',
          title: 'Bad Request',
          status: 400,
          detail: 'Girdi geçersiz.',
          kod: 'DOGRULAMA',
          alanlar: { ad: 'boş olamaz' },
        },
        'application/problem+json',
      ),
    )
    const hata = await api.post('/api/makinistler', {}).catch((e: unknown) => e)
    expect(hata).toBeInstanceOf(ApiHata)
    const h = hata as ApiHata
    expect(h.status).toBe(400)
    expect(h.kod).toBe('DOGRULAMA')
    expect(h.detail).toBe('Girdi geçersiz.')
    expect(h.alanlar).toEqual({ ad: 'boş olamaz' })
    expect(h.message).toBe('Girdi geçersiz.')
  })

  it('422 KURAL_IHLALI gövdesindeki ihlaller ApiHata.ihlaller olarak taşınır', async () => {
    const ihlal = {
      kod: 'K-11',
      siddet: 'ENGEL',
      olculen: 8,
      esik: 11,
      birim: 's',
      metin: 'Dinlenme 8 s < 11 s',
    }
    fetchSahte.mockResolvedValue(
      yanit(422, { status: 422, detail: 'Kural ihlali.', kod: 'KURAL_IHLALI', ihlaller: [ihlal] }),
    )
    const h = (await api.post('/api/atama/sefer/1', {}).catch((e: unknown) => e)) as ApiHata
    expect(h.ihlaller).toEqual([ihlal])
    fetchSahte.mockResolvedValue(yanit(400, { status: 400, detail: 'x', kod: 'DOGRULAMA' }))
    const d = (await api.post('/api/x', {}).catch((e: unknown) => e)) as ApiHata
    expect(d.ihlaller).toBeUndefined()
  })

  it('409 VERSIYON_CAKISMASI için cakismaMi doğrudur', async () => {
    fetchSahte.mockResolvedValue(
      yanit(409, {
        type: 'x',
        title: 'Conflict',
        status: 409,
        detail: 'Kayıt değişmiş.',
        kod: 'VERSIYON_CAKISMASI',
      }),
    )
    const h = (await api.patch('/api/x', []).catch((e: unknown) => e)) as ApiHata
    expect(h.cakismaMi).toBe(true)
  })

  it('JSON olmayan hata gövdesinde genel SUNUCU_HATASI üretir', async () => {
    fetchSahte.mockResolvedValue(new Response('Bad gateway', { status: 502 }))
    const h = (await api.get('/api/makinistler').catch((e: unknown) => e)) as ApiHata
    expect(h.kod).toBe('SUNUCU_HATASI')
    expect(h.status).toBe(502)
    expect(h.detail).toMatch(/hata/i)
  })

  it('ağ hatasında AG_HATASI fırlatır', async () => {
    fetchSahte.mockRejectedValue(new TypeError('Failed to fetch'))
    const h = (await api.get('/api/makinistler').catch((e: unknown) => e)) as ApiHata
    expect(h.kod).toBe('AG_HATASI')
    expect(h.status).toBe(0)
  })

  // Review INFO: ağ yarı açık/vekil takılırsa yanıt hiç gelmez; istek sonsuza dek askıda kalıp kayıt kuyruğunu kilitlemesin.
  describe('zaman aşımı (AbortController)', () => {
    beforeEach(() => vi.useFakeTimers())
    afterEach(() => vi.useRealTimers())
    /** Yanıt hiç gelmeyen ama iptal sinyaline uyan fetch. */
    const askida = () =>
      fetchSahte.mockImplementation(
        (_yol: string, secenek: { signal: AbortSignal }) =>
          new Promise((_coz, red) =>
            secenek.signal.addEventListener('abort', () =>
              red(new DOMException('iptal edildi', 'AbortError')),
            ),
          ),
      )
    async function bitisSuresi(istekBaslat: () => Promise<unknown>, ms: number) {
      let sonuc: unknown = 'bekliyor'
      void istekBaslat().catch((e: unknown) => (sonuc = e))
      await vi.advanceTimersByTimeAsync(ms - 1)
      const once = sonuc
      await vi.advanceTimersByTimeAsync(1)
      return { once, sonra: sonuc }
    }

    it('yanıt gelmeyen JSON isteği 30 sn sonra AG_HATASI ile biter', async () => {
      askida()
      const { once, sonra } = await bitisSuresi(() => api.patch('/api/x', {}), 30_000)
      expect(once).toBe('bekliyor')
      expect(sonra).toBeInstanceOf(ApiHata)
      expect(sonra).toMatchObject({ kod: 'AG_HATASI', status: 0 })
      expect((sonra as ApiHata).detail).toContain('zamanında yanıt vermedi')
    })

    // zaman aşımından sonra sunucu yazma işlemini uygulamış olabilir; otomatik tekrar çift kayıt yaratır.
    it.each(['POST', 'PUT', 'PATCH', 'DELETE'])(
      '%s zaman aşımında OTOMATİK TEKRAR YOK (tek istek) ve ileti işlemin uygulanmış olabileceğini söyler',
      async (yontem) => {
        askida()
        const { sonra } = await bitisSuresi(() => istek(yontem, '/api/x', {}), 30_000)
        expect(sonra).toMatchObject({ kod: 'AG_HATASI', status: 0 })
        expect((sonra as ApiHata).detail).toContain('zamanında yanıt vermedi')
        expect((sonra as ApiHata).detail).toContain('uygulanmış olabilir')
        await vi.advanceTimersByTimeAsync(600_000)
        expect(fetchSahte).toHaveBeenCalledTimes(1)
      },
    )

    it('GET zaman aşımı iletisi "uygulanmış olabilir" demez (okuma güvenle tekrarlanır); otomatik tekrar yine yok', async () => {
      askida()
      const { sonra } = await bitisSuresi(() => api.get('/api/x'), 30_000)
      expect((sonra as ApiHata).detail).not.toContain('uygulanmış')
      await vi.advanceTimersByTimeAsync(600_000)
      expect(fetchSahte).toHaveBeenCalledTimes(1)
    })

    // QA BUG-5: otomatik atama / kuralı dene sunucuda 30 sn tavanına yaklaşabilir; arayüz bundan önce vazgeçmesin.
    it('uzun işlem (post + zamanAsimiMs): 30 sn sonra iptal edilmez, 120 sn sonra AG_HATASI olur', async () => {
      askida()
      const uzun = await bitisSuresi(
        () => api.post('/api/x', {}, UZUN_ISLEM_ZAMAN_ASIMI_MS),
        120_000,
      )
      expect(uzun.once).toBe('bekliyor')
      expect(uzun.sonra).toMatchObject({ kod: 'AG_HATASI', status: 0 })
      expect(UZUN_ISLEM_ZAMAN_ASIMI_MS).toBeGreaterThanOrEqual(120_000)
    })

    it('dosya yükleme (FormData) 5 dakika, indirme 2 dakika tanınır', async () => {
      askida()
      const yukle = await bitisSuresi(() => api.post('/api/x', new FormData()), 300_000)
      expect(yukle.once).toBe('bekliyor')
      expect(yukle.sonra).toMatchObject({ kod: 'AG_HATASI' })
      const indirme = await bitisSuresi(() => indir('/api/x'), 120_000)
      expect(indirme.once).toBe('bekliyor')
      expect(indirme.sonra).toMatchObject({ kod: 'AG_HATASI' })
    })

    it('yanıt zamanında gelirse iptal sinyali tetiklenmez ve zamanlayıcı kalmaz', async () => {
      fetchSahte.mockResolvedValue(yanit(200, { id: 1 }))
      expect(await api.get('/api/makinistler/1')).toEqual({ id: 1 })
      const sinyal = fetchSahte.mock.calls[0]![1].signal as AbortSignal
      expect(sinyal).toBeInstanceOf(AbortSignal)
      expect(vi.getTimerCount()).toBe(0)
      await vi.advanceTimersByTimeAsync(60_000)
      expect(sinyal.aborted).toBe(false)
    })
  })

  it('401 yanıtında kayıtlı yetkisiz işleyiciyi çağırır ve yine hata fırlatır', async () => {
    const isleyici = vi.fn()
    yetkisizIsleyiciKaydet(isleyici)
    fetchSahte.mockResolvedValue(
      yanit(401, { kod: 'OTURUM_YOK', detail: 'Oturum yok.', status: 401 }),
    )
    await expect(api.get('/api/makinistler')).rejects.toMatchObject({
      kod: 'OTURUM_YOK',
    })
    expect(isleyici).toHaveBeenCalledTimes(1)
  })

  it('/api/auth/ben ve /api/auth/giris 401 yanıtında işleyiciyi çağırmaz', async () => {
    const isleyici = vi.fn()
    yetkisizIsleyiciKaydet(isleyici)
    fetchSahte.mockImplementation(() =>
      Promise.resolve(yanit(401, { kod: 'GIRIS_HATALI', detail: 'Hatalı.', status: 401 })),
    )
    await expect(api.get('/api/auth/ben')).rejects.toBeInstanceOf(ApiHata)
    await expect(istek('POST', '/api/auth/giris', {})).rejects.toBeInstanceOf(ApiHata)
    expect(isleyici).not.toHaveBeenCalled()
  })

  it('sorguMetni boş değerleri atar ve değerleri kodlar', () => {
    expect(
      sorguMetni({
        durum: 'AKTIF',
        ara: 'İş ık',
        bos: '',
        yok: undefined,
        sayfa: 0,
      }),
    ).toBe('?durum=AKTIF&ara=%C4%B0%C5%9F+%C4%B1k&sayfa=0')
    expect(sorguMetni({ ara: '' })).toBe('')
  })

  describe('indir (dosya indirme)', () => {
    const XLSX = 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'

    it('baytları ve Content-Disposition dosya adını (Türkçe karakterli filename*) döner; Accept */*, GET', async () => {
      fetchSahte.mockResolvedValue(
        new Response(new Uint8Array([80, 75, 3, 4]), {
          status: 200,
          headers: {
            'Content-Type': XLSX,
            'Content-Disposition':
              'attachment; filename="Gun_Cizelgesi.xlsx"; filename*=UTF-8\'\'Gun_%C5%9Eubat_2026-10-05.xlsx',
          },
        }),
      )
      const d = await indir('/api/cikti/gun.xlsx')
      expect(d.ad).toBe('Gun_Şubat_2026-10-05.xlsx')
      expect(new Uint8Array(await d.blob.arrayBuffer())).toEqual(new Uint8Array([80, 75, 3, 4]))
      const [yol, secenek] = fetchSahte.mock.calls[0]!
      expect(yol).toBe('/api/cikti/gun.xlsx')
      expect(secenek.method).toBe('GET')
      expect(secenek.headers.Accept).toBe('*/*')
    })

    it('başlık yoksa ad null döner', async () => {
      fetchSahte.mockResolvedValue(new Response('x', { status: 200 }))
      expect((await indir('/api/x')).ad).toBeNull()
    })

    it('hata gövdesi JSON ProblemDetail: ApiHata (kod, detail) fırlatır, dosya dönmez', async () => {
      fetchSahte.mockResolvedValue(
        yanit(422, {
          kod: 'KURAL_IHLALI',
          detail: 'Kural ihlali.',
          status: 422,
        }),
      )
      await expect(indir('/api/cikti/gun.xlsx')).rejects.toMatchObject({
        status: 422,
        kod: 'KURAL_IHLALI',
        detail: 'Kural ihlali.',
      })
    })

    it('401 yetkisiz işleyiciyi çağırır; ağ hatası AG_HATASI olur', async () => {
      const isleyici = vi.fn()
      yetkisizIsleyiciKaydet(isleyici)
      fetchSahte.mockResolvedValueOnce(
        yanit(401, { kod: 'OTURUM_YOK', detail: 'Oturum yok.', status: 401 }),
      )
      await expect(indir('/api/x')).rejects.toMatchObject({
        kod: 'OTURUM_YOK',
      })
      expect(isleyici).toHaveBeenCalledTimes(1)
      fetchSahte.mockRejectedValueOnce(new TypeError('Failed to fetch'))
      await expect(indir('/api/x')).rejects.toMatchObject({ kod: 'AG_HATASI' })
    })
  })

  describe('dosyaAdiOku', () => {
    it('filename* (UTF-8) önceliklidir; yoksa tırnaklı ya da tırnaksız filename', () => {
      expect(dosyaAdiOku("attachment; filename*=UTF-8''Ek%20dosya.xlsx")).toBe('Ek dosya.xlsx')
      expect(dosyaAdiOku('attachment; filename="a b.xlsx"')).toBe('a b.xlsx')
      expect(dosyaAdiOku('attachment; filename=gun.xlsx; size=3')).toBe('gun.xlsx')
    })
    it('boş / bozuk başlıkta null ya da düz ada düşer', () => {
      expect(dosyaAdiOku(null)).toBeNull()
      expect(dosyaAdiOku('attachment')).toBeNull()
      expect(dosyaAdiOku('attachment; filename=""')).toBeNull()
      expect(dosyaAdiOku('attachment; filename*=UTF-8\'\'%E0%A4%A; filename="duz.xlsx"')).toBe(
        'duz.xlsx',
      )
    })
  })
})
