import { describe, expect, it } from 'vitest'
import { gruplaMenu, type MenuRotasi } from './menu'

const r = (path: string, ad: string, sira: number, grup?: string, rol?: string): MenuRotasi => ({
  path,
  meta: { rol, menu: { ad, sira, grup } },
})

const ROTALAR: MenuRotasi[] = [
  r('/ayarlar', 'Ayarlar', 40, 'Ayarlar', 'Y'),
  r('/rapor/adalet', 'Adalet Raporu', 42, 'Raporlar'),
  r('/', 'Ana Sayfa', 0),
  r('/atama', 'Atama Masası', 25, 'Atama'),
  r('/sefer', 'Seferler', 20, 'Atama'),
  r('/makinistler', 'Personel', 10, 'Personel'),
  r('/kadro', 'Kadro İhtiyacı', 49, 'Raporlar', 'Y'),
  { path: '/gizli', meta: {} }, // menüsüz rota
]
const adlar = (y: boolean) => gruplaMenu(ROTALAR, y).map((g) => [g.grup, g.ogeler.map((o) => o.ad)])

describe('gruplaMenu', () => {
  it('grup sırası sabit (başlıksız üstte), grup içinde sira; menüsüz rota yok', () => {
    expect(adlar(true)).toEqual([
      [null, ['Ana Sayfa']],
      ['Atama', ['Seferler', 'Atama Masası']],
      ['Personel', ['Personel']],
      ['Raporlar', ['Adalet Raporu', 'Kadro İhtiyacı']],
      ['Ayarlar', ['Ayarlar']],
    ])
  })

  it('atamacı: rol Y öğeleri çıkar, boşalan grup düşer', () => {
    const g = adlar(false)
    expect(g.map((x) => x[0])).toEqual([null, 'Atama', 'Personel', 'Raporlar'])
    expect(g[3]![1]).toEqual(['Adalet Raporu'])
  })

  it('aynı sira: yola göre sıralanır', () => {
    const g = gruplaMenu([r('/b', 'B', 1, 'Atama'), r('/a', 'A', 1, 'Atama')], true)
    expect(g[0]!.ogeler.map((o) => o.yol)).toEqual(['/a', '/b'])
  })

  it('bilinmeyen grup sona eklenir (öğe kaybolmaz)', () => {
    const g = gruplaMenu([r('/x', 'X', 1, 'Yeni'), r('/', 'Ana', 0)], true)
    expect(g.map((x) => x.grup)).toEqual([null, 'Yeni'])
  })

  it('gerçek rotalar: 19 öğe, hepsi gruplanmış (Ana Sayfa hariç) ve yönetici 19, atamacı 15 görür', async () => {
    const modul = import.meta.glob<{ default: MenuRotasi[] & { path: string }[] }>(
      '../features/*/routes.ts',
      { eager: true },
    )
    // yönlendirici normalleştirmesi gibi: metası olmayan kayıt (yönlendirme) boş meta alır
    const rotalar = Object.values(modul)
      .flatMap((m) => m.default as unknown as Partial<MenuRotasi>[])
      .map((r) => ({ path: r.path!, meta: r.meta ?? {} }))
    const yonetici = gruplaMenu(rotalar, true)
    expect(yonetici.flatMap((g) => g.ogeler)).toHaveLength(19)
    expect(
      yonetici.filter((g) => g.grup === null).flatMap((g) => g.ogeler.map((o) => o.ad)),
    ).toEqual(['Ana Sayfa'])
    expect(yonetici.flatMap((g) => g.ogeler).every((o) => o.simge)).toBe(true)
    // her menü öğesinin simgesi benzersiz (iki öğe aynı simgeyi taşımaz)
    const simgeler = yonetici.flatMap((g) => g.ogeler.map((o) => o.simge))
    expect(new Set(simgeler).size).toBe(simgeler.length)
    expect(gruplaMenu(rotalar, false).flatMap((g) => g.ogeler)).toHaveLength(15)
  })
})
