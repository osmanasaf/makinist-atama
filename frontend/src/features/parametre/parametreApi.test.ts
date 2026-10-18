import { describe, expect, it } from 'vitest'
import {
  ayarUyarilari,
  birlestir,
  degerEsit,
  gruplaYap,
  kaynakMetni,
  type AyarDto,
  type AyarSatiri,
  type KuralSiddetDto,
  type ParametreDto,
} from './parametreApi'

const kural = (kod: string, grup: string, sira: number, siddet = 'ENGEL'): KuralSiddetDto => ({
  kod,
  ad: `${kod} adı`,
  siddet: siddet as KuralSiddetDto['siddet'],
  aciklama: `${kod} açıklama`,
  kaynak: '[Y] test',
  guvenilirlik: 'YUKSEK',
  varsayilan: 'ENGEL',
  grup,
  sira,
})
const parametre = (
  anahtar: string,
  deger: string,
  grup: string,
  sira: number,
  birim = 'saat',
): ParametreDto => ({
  anahtar,
  ad: `${anahtar} adı`,
  deger,
  tip: 'SAYI',
  altSinir: 0,
  ustSinir: 100,
  birim,
  aciklama: `${anahtar} açıklama`,
  kaynak: '[V] test',
  guvenilirlik: 'ORTA',
  varsayilan: deger,
  grup,
  sira,
})
const ayar = (anahtar: string, deger: number, grup: string, sira: number): AyarDto => ({
  anahtar,
  ad: `${anahtar} adı`,
  deger,
  birim: 'gün',
  alt: 1,
  ust: 60,
  aciklama: `${anahtar} açıklama`,
  varsayilan: 7,
  grup,
  sira,
})

describe('birlestir / gruplaYap', () => {
  const satirlar = birlestir(
    [kural('K-14', 'CALISMA', 40), kural('K-16', 'GECE', 10)],
    [
      parametre('gunluk.max_saat', '11', 'CALISMA', 60),
      parametre('dinlenme.ev_saat', '11', 'CALISMA', 10),
      parametre('gece.bitis', '06:00', 'GECE', 20, 'sa:dk'),
      parametre('yeni.deger', '1', 'YENI_GRUP', 10),
    ],
    [ayar('gordum.kalem_gun', 7, 'MAKINIST', 30), ayar('takas.kapanmis_gun', 7, 'CALISMA', 5)],
  )

  it('üç kaynağı türüyle birleştirir; uygulama ayarında kaynak "Uygulama ayarı", güvenilirlik yok, okunur ad taşınır', () => {
    const g = satirlar.find((s) => s.anahtar === 'gordum.kalem_gun')!
    expect(g).toMatchObject({
      tur: 'AYAR',
      deger: '7',
      varsayilan: '7',
      alt: 1,
      ust: 60,
      birim: 'gün',
      kaynak: 'Uygulama ayarı',
      ad: 'gordum.kalem_gun adı',
      guvenilirlik: null,
    })
    expect(satirlar.find((s) => s.anahtar === 'K-14')!.tur).toBe('SIDDET')
    expect(satirlar.find((s) => s.anahtar === 'dinlenme.ev_saat')).toMatchObject({
      tur: 'PARAMETRE',
      kaynak: '[V] test',
      guvenilirlik: 'ORTA',
    })
  })

  it("birim '0/1' olan parametre ve ayar bayraktır, diğerleri değil", () => {
    const b = birlestir(
      [],
      [parametre('a.b', '1', 'CALISMA', 1, '0/1'), parametre('a.c', '1', 'CALISMA', 2)],
      [{ ...ayar('x.y', 1, 'MAKINIST', 1), birim: '0/1' }],
    )
    expect(b.map((s) => s.bayrak)).toEqual([true, false, true])
  })

  it('grup sırası sabit, her grupta şiddetler önce, sonra sira; bilinmeyen grup sonda ve kodu başlık', () => {
    expect(satirlar.map((s) => s.anahtar)).toEqual([
      'K-14',
      'takas.kapanmis_gun',
      'dinlenme.ev_saat',
      'gunluk.max_saat',
      'K-16',
      'gece.bitis',
      'gordum.kalem_gun',
      'yeni.deger',
    ])
    const gruplar = gruplaYap(satirlar)
    expect(gruplar.map((g) => g.baslik)).toEqual([
      'Çalışma ve dinlenme',
      'Gece çalışması',
      'Makinist görünümü',
      'YENI_GRUP',
    ])
    expect(gruplar[0]!.siddetler.map((s) => s.anahtar)).toEqual(['K-14'])
    expect(gruplar[0]!.degerler.map((s) => s.anahtar)).toEqual([
      'takas.kapanmis_gun',
      'dinlenme.ev_saat',
      'gunluk.max_saat',
    ])
  })
})

describe('ekran sırası: şiddet, ilgili ayarının yanında', () => {
  it('K-23 ve K-14 kendi ayarlarının hemen üstünde; ilgisiz şiddetler grubun başında', () => {
    const [g] = gruplaYap(
      birlestir(
        [kural('K-01', 'CALISMA', 10), kural('K-23', 'CALISMA', 30), kural('K-14', 'CALISMA', 50)],
        [
          parametre('dinlenme.ev_saat', '11', 'CALISMA', 10),
          parametre('nobet.asgari_aktarma_dk', '20', 'CALISMA', 50),
          parametre('haftalik.normal_saat', '45', 'CALISMA', 70),
          parametre('haftalik.max_saat', '45', 'CALISMA', 80),
        ],
        [],
      ),
    )
    expect(g!.satirlar.map((s) => s.anahtar)).toEqual([
      'K-01',
      'dinlenme.ev_saat',
      'K-23',
      'nobet.asgari_aktarma_dk',
      'K-14',
      'haftalik.normal_saat',
      'haftalik.max_saat',
    ])
  })

  it('ilgili ayar grupta yoksa şiddet başta kalır (satır kaybolmaz)', () => {
    const [g] = gruplaYap(birlestir([kural('K-23', 'CALISMA', 30)], [], []))
    expect(g!.satirlar.map((s) => s.anahtar)).toEqual(['K-23'])
  })
})

describe('degerEsit (tipine göre karşılaştırma)', () => {
  it('sayısal satırda 0.1 == 0.10 == 0,1; metinde kırpılmış eşitlik', () => {
    expect(degerEsit(true, '0.1', '0.10')).toBe(true)
    expect(degerEsit(true, '0,1', '0.10')).toBe(true)
    expect(degerEsit(true, '7', '7.0')).toBe(true)
    expect(degerEsit(true, '0.2', '0.10')).toBe(false)
    expect(degerEsit(true, '', '')).toBe(true)
    expect(degerEsit(true, '', '0')).toBe(false)
    expect(degerEsit(false, '20:00', '20:00 ')).toBe(true)
    expect(degerEsit(false, '1', '1.0')).toBe(false)
    expect(degerEsit(false, 'ENGEL', 'UYARI')).toBe(false)
  })
})

describe('kaynakMetni', () => {
  it('köşeli kısaltmaları açık yazar, iç kod göndermesi eklemez', () => {
    expect(kaynakMetni('[Y] 4857 md. 63; [T] md. 31')).toBe(
      'Yasa/yönetmelik: 4857 md. 63; Toplu iş sözleşmesi: md. 31',
    )
    expect(kaynakMetni('[İ] TCDD emri 08.11.2023')).toBe(
      'TCDD genel müdürlük emri: TCDD emri 08.11.2023',
    )
    expect(kaynakMetni('[V] kaynaksız varsayım (işletme kararı)')).toBe(
      'Kaynaksız varsayım (işletme kararı)',
    )
    expect(kaynakMetni('[V] kullanıcı: "en az çalışmış"')).toBe(
      'Varsayım: kullanıcı: "en az çalışmış"',
    )
    expect(kaynakMetni('bilinmiyor')).toBe('bilinmiyor')
  })
})

describe('ayarUyarilari', () => {
  const satir = (tur: AyarSatiri['tur'], anahtar: string, deger: string): AyarSatiri =>
    ({
      tur,
      anahtar,
      deger,
      varsayilan: deger,
      aciklama: '',
      birim: '',
      alt: null,
      ust: null,
      kaynak: '',
      guvenilirlik: null,
      bayrak: false,
      sayisal: true,
      grup: 'CALISMA',
      sira: 1,
    }) as AyarSatiri
  const kodlar = (s: AyarSatiri[]) => ayarUyarilari(s).map((u) => u.kod)

  it('tutarlı değerlerde uyarı yok', () => {
    expect(
      kodlar([
        satir('PARAMETRE', 'gece.bitis', '06:00'),
        satir('PARAMETRE', 'puantaj.gece_bitis', '07:00'),
        satir('SIDDET', 'K-23', 'ENGEL'),
        satir('PARAMETRE', 'yolculuk.aktarma_dk', '15'),
        satir('PARAMETRE', 'nobet.asgari_aktarma_dk', '20'),
      ]),
    ).toEqual([])
  })

  it('puantaj gece bitişi kural penceresinden erkense uyarır (eşitse uyarmaz)', () => {
    const u = ayarUyarilari([
      satir('PARAMETRE', 'gece.bitis', '06:00'),
      satir('PARAMETRE', 'puantaj.gece_bitis', '05:30'),
    ])
    expect(u).toHaveLength(1)
    expect(u[0]).toMatchObject({ kod: 'PUANTAJ_GECE_BITIS', duzey: 'UYARI' })
    expect(u[0]!.metin).toContain('05:30')
    expect(
      kodlar([
        satir('PARAMETRE', 'gece.bitis', '06:00'),
        satir('PARAMETRE', 'puantaj.gece_bitis', '06:00'),
      ]),
    ).toEqual([])
  })

  it('yolculuk aktarma süresi asgari aktarmadan büyük ve K-23 açıkken "yolculuk geçişlerine uygulanmaz" der', () => {
    const girdi = (k23: string) => [
      satir('PARAMETRE', 'yolculuk.aktarma_dk', '30'),
      satir('PARAMETRE', 'nobet.asgari_aktarma_dk', '20'),
      satir('SIDDET', 'K-23', k23),
    ]
    const u = ayarUyarilari(girdi('ENGEL'))
    expect(u).toHaveLength(1)
    expect(u[0]!.metin).toContain('K-23 yolculuk geçişlerine uygulanmaz')
    expect(kodlar(girdi('UYARI'))).toEqual(['YOLCULUK_AKTARMA'])
    expect(kodlar(girdi('KAPALI'))).toEqual([])
  })

  it('K-14 kapalıyken normal > üst sınır ise bilgi notu verir, açıkken vermez', () => {
    const girdi = (k14: string) => [
      satir('PARAMETRE', 'haftalik.normal_saat', '48'),
      satir('PARAMETRE', 'haftalik.max_saat', '45'),
      satir('SIDDET', 'K-14', k14),
    ]
    const u = ayarUyarilari(girdi('KAPALI'))
    expect(u).toHaveLength(1)
    expect(u[0]).toMatchObject({ kod: 'HAFTALIK_ASGARI', duzey: 'BILGI' })
    expect(u[0]!.metin).toContain('Haftalık üst sınır')
    expect(u[0]!.metin).toContain('K-14 kapalıyken kullanılmaz')
    expect(kodlar(girdi('ENGEL'))).toEqual([])
  })

  it('eksik ya da sayı olmayan değerlerde sessiz kalır', () => {
    expect(kodlar([])).toEqual([])
    expect(
      kodlar([
        satir('PARAMETRE', 'haftalik.normal_saat', 'abc'),
        satir('PARAMETRE', 'haftalik.max_saat', '45'),
        satir('SIDDET', 'K-14', 'KAPALI'),
      ]),
    ).toEqual([])
  })
})

describe('grup düzeltmesi', () => {
  it('Excel parça süresi sunucuda "Çalışma" grubunda gelse de ekranda "Seferler ve Excel" grubunda görünür', () => {
    const gruplar = gruplaYap(
      birlestir(
        [],
        [
          parametre('dinlenme.ev_saat', '11', 'CALISMA', 10),
          parametre('sefer.parca_azami_saat', '16', 'CALISMA', 10),
        ],
        [],
      ),
    )
    expect(gruplar.map((g) => g.baslik)).toEqual(['Çalışma ve dinlenme', 'Seferler ve Excel'])
    expect(gruplar[1]!.degerler.map((s) => s.anahtar)).toEqual(['sefer.parca_azami_saat'])
  })

  // QA BUG-7 (04 madde 9 yanlışlıkla TAMAM yazılmıştı): sunucuda bu anahtar /api/ayar (AyarDto) kaynağından gelir, /api/parametre'den değil.
  it('Excel parça süresi gerçek kaynaktan (/api/ayar → ayarSatiri) gelince de "Seferler ve Excel" grubundadır', () => {
    const gruplar = gruplaYap(
      birlestir(
        [],
        [parametre('dinlenme.ev_saat', '11', 'CALISMA', 10)],
        [ayar('sefer.parca_azami_saat', 16, 'CALISMA', 10)],
      ),
    )
    expect(gruplar.map((g) => g.baslik)).toEqual(['Çalışma ve dinlenme', 'Seferler ve Excel'])
    expect(gruplar[1]!.degerler.map((x) => x.anahtar)).toEqual(['sefer.parca_azami_saat'])
    expect(gruplar[0]!.degerler.map((x) => x.anahtar)).toEqual(['dinlenme.ev_saat'])
  })
})
