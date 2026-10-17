import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import DegisiklikBolumleri from './DegisiklikBolumleri.vue'
import type { DegisiklikSatiri, DegisiklikYaniti } from './degisiklikApi'

const satir = (id: number, ek: Partial<DegisiklikSatiri>): DegisiklikSatiri => ({
  id,
  zaman: '2026-10-12T07:15:00Z',
  kim: 'Ayşe Kaya',
  tur: 'ATAMA',
  turAdi: 'Atama',
  sicil: 'D007',
  trenNo: 'T1001',
  seferTarihi: '2026-10-13',
  eski: null,
  yeni: null,
  neden: null,
  nedenNotu: null,
  uyarili: false,
  uyariGerekcesi: null,
  uyarilar: [],
  otomatik: false,
  ...ek,
})

describe('DegisiklikBolumleri: yolcu etiketi (F2b)', () => {
  it('"YOLCU:D007" kaydı "D007 (yolcu)" diye okunur (ham kod gösterilmez)', () => {
    const rapor: DegisiklikYaniti = {
      uretimZamani: '2026-10-12T07:20:00Z',
      ureten: 'Mehmet Demir',
      filtre: { bas: '2026-10-06', bit: '2026-10-12', trenNo: null, tur: null },
      kayitSayisi: 2,
      bolumler: [
        {
          kod: 'ATAMA',
          ad: 'Atamalar',
          sayi: 1,
          satirlar: [satir(1, { yeni: 'YOLCU:D007' })],
        },
        {
          kod: 'ATAMA_KALDIR',
          ad: 'Kaldırmalar',
          sayi: 1,
          satirlar: [
            satir(2, { tur: 'ATAMA_KALDIR', turAdi: 'Atama kaldırıldı', eski: 'YOLCU:D007' }),
          ],
        },
      ],
    }

    const w = mount(DegisiklikBolumleri, { props: { rapor } })

    expect(w.text()).toContain('D007 (yolcu)')
    expect(w.text()).not.toContain('YOLCU:D007')
  })
})
