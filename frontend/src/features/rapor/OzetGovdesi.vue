<script setup lang="ts">
import { computed } from 'vue'
import { NEDEN_ETIKETLERI, type AtamaNedeni } from '@/features/atama/atamaApi'
import { sayiBicimle } from '@/lib/sayi'
import { tarihBicimle } from '@/lib/tarih'
import type { OzetAdet, YonetimOzeti } from './ozetApi'

/**
 * Yönetim özeti gövdesi (F4, MK-180; ekran ve yazdırma ortak). Her sayı sunucudan gelir (mevcut raporların aynı sayısı); burada hesap YOK (yalnız çubuk genişliği için oran).
 * Dağılımlar tek seri satır içi çubuktur: değer her zaman yanında sayı olarak yazılır (renk dışı kodlama, çubuk `aria-hidden`). `yazdir`: renksiz yazdırma tabloları, çubuk yok.
 */
const props = defineProps<{ ozet: YonetimOzeti; yazdir?: boolean }>()

const tablo = computed(() => (props.yazdir ? 'yazdir-tablo' : 'tablo'))
const yuzde = (v: number | null) => (v === null ? '—' : `%${sayiBicimle(v)}`)
const saat = (v: number | null) => (v === null ? '—' : `${sayiBicimle(v)} sa`)
/** Çubuk genişliği: değerin en büyük değere oranı (yalnız görsel; sayı yanında yazılıdır). */
const genislik = (adet: number, en: number) => `${en > 0 ? Math.round((adet / en) * 100) : 0}%`
const enCok = (l: OzetAdet[]) => Math.max(0, ...l.map((x) => x.adet))
const nedenAdi = (ad: string) => NEDEN_ETIKETLERI[ad as AtamaNedeni] ?? ad
const belgeSatirlari = computed(() => {
  const b = props.ozet.belge
  return [
    { ad: 'Geçerli', adet: b.gecerli },
    { ad: 'Yaklaşan', adet: b.yaklasan },
    { ad: 'Süresi dolmuş', adet: b.dolmus },
    { ad: 'Girilmemiş', adet: b.girilmemis },
  ]
})
const belgeEnCok = computed(() => enCok(belgeSatirlari.value))
</script>

<template>
  <div class="ozet-govde" :class="{ 'ozet-yazdir': yazdir }">
    <section class="ozet-kart" data-test="doluluk" aria-labelledby="oz-doluluk">
      <h2 id="oz-doluluk">Doluluk</h2>
      <p class="ozet-sayi">
        <strong>{{ ozet.doluluk.dolu }}</strong> / {{ ozet.doluluk.toplamSefer }} sefer dolu
        <span v-if="ozet.doluluk.yuzde !== null" class="soluk" data-test="doluluk-yuzde"
          >({{ yuzde(ozet.doluluk.yuzde) }})</span
        >
      </p>
      <span v-if="!yazdir" class="cubuk" aria-hidden="true"
        ><span
          class="cubuk-dolgu"
          :style="{ width: genislik(ozet.doluluk.dolu, ozet.doluluk.toplamSefer) }"
        ></span
      ></span>
      <p class="soluk">
        Boş (asıl makinisti olmayan):
        <strong data-test="doluluk-bos">{{ ozet.doluluk.bos }}</strong>
        · İptal edilen seferler sayılmaz.
      </p>
    </section>

    <section class="ozet-kart" data-test="kirmizi" aria-labelledby="oz-kirmizi">
      <h2 id="oz-kirmizi">Kural dışı (kırmızı) atama</h2>
      <p class="ozet-sayi">
        <strong data-test="kirmizi-atama">{{ ozet.kirmizi.atama }}</strong> atama
      </p>
      <p class="soluk">
        {{ ozet.kirmizi.sefer }} sefer · {{ ozet.kirmizi.intikal }} serbest intikal (Atama
        Masası'ndaki kontrol sayısıyla aynı).
      </p>
    </section>

    <section class="ozet-kart" data-test="uyarili" aria-labelledby="oz-uyarili">
      <h2 id="oz-uyarili">Uyarılı atama</h2>
      <p class="ozet-sayi">
        <strong data-test="uyarili-atama">{{ ozet.uyarili.atama }}</strong> atama
      </p>
      <table
        v-if="ozet.uyarili.gerekceler.length"
        :class="tablo"
        aria-label="Uyarılı atama gerekçeleri"
      >
        <thead>
          <tr>
            <th scope="col">Gerekçe (atamacının yazdığı)</th>
            <th scope="col" class="sayisal">Adet</th>
            <th v-if="!yazdir" scope="col" class="gorunmez">Oran</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="g in ozet.uyarili.gerekceler" :key="g.ad">
            <td>{{ g.ad || '(gerekçe yok)' }}</td>
            <td class="sayisal">{{ g.adet }}</td>
            <td v-if="!yazdir" class="cubuk-hucre">
              <span class="cubuk" aria-hidden="true"
                ><span
                  class="cubuk-dolgu"
                  :style="{ width: genislik(g.adet, enCok(ozet.uyarili.gerekceler)) }"
                ></span
              ></span>
            </td>
          </tr>
        </tbody>
      </table>
      <p v-else class="soluk">Bu ay gerekçeyle yapılmış uyarılı atama yok.</p>
    </section>

    <section class="ozet-kart" data-test="adalet" aria-labelledby="oz-adalet">
      <h2 id="oz-adalet">Adalet dağılımı (görev saati)</h2>
      <dl v-if="ozet.adalet.n > 0" class="ozet-liste">
        <div>
          <dt>Makinist</dt>
          <dd data-test="adalet-n">{{ ozet.adalet.n }}</dd>
        </div>
        <div>
          <dt>Ortalama</dt>
          <dd data-test="adalet-ortalama">{{ saat(ozet.adalet.ortalamaSaat) }}</dd>
        </div>
        <div>
          <dt>Standart sapma</dt>
          <dd data-test="adalet-std">{{ saat(ozet.adalet.standartSapmaSaat) }}</dd>
        </div>
        <div>
          <dt>En az</dt>
          <dd data-test="adalet-min">{{ saat(ozet.adalet.minSaat) }}</dd>
        </div>
        <div>
          <dt>En çok</dt>
          <dd data-test="adalet-max">{{ saat(ozet.adalet.maxSaat) }}</dd>
        </div>
      </dl>
      <p v-else class="soluk">Aktif makinist yok.</p>
      <p class="soluk" data-test="std-notu">
        Standart sapma popülasyon sapmasıdır (n'e bölünür): √( Σ (saatᵢ − ortalama)² / n ). Adalet
        raporundaki aynı AKTIF makinist grubunun görev saatlerinden hesaplanır.
      </p>
    </section>

    <section class="ozet-kart" data-test="fazla-mesai" aria-labelledby="oz-fm">
      <h2 id="oz-fm">Fazla mesai</h2>
      <p class="ozet-sayi">
        <strong data-test="fm-toplam">{{ sayiBicimle(ozet.fazlaMesai.toplamSaat) }}</strong> sa
      </p>
      <p class="soluk">
        {{ ozet.fazlaMesai.makinistSayisi }} makinist (Puantaj raporunun toplamıyla aynı; bordro
        değildir).
      </p>
    </section>

    <section class="ozet-kart" data-test="belge" aria-labelledby="oz-belge">
      <h2 id="oz-belge">Belge uyumu</h2>
      <p class="soluk" data-test="belge-etiket">
        {{ ozet.belge.etiket }} ({{ tarihBicimle(ozet.belge.bugun) }}): dönem bilgisi değildir.
      </p>
      <table :class="tablo" aria-label="Belge durumu">
        <thead>
          <tr>
            <th scope="col">Durum (en kötü belge)</th>
            <th scope="col" class="sayisal">Makinist</th>
            <th v-if="!yazdir" scope="col" class="gorunmez">Oran</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="b in belgeSatirlari" :key="b.ad">
            <td>{{ b.ad }}</td>
            <td class="sayisal">{{ b.adet }}</td>
            <td v-if="!yazdir" class="cubuk-hucre">
              <span class="cubuk" aria-hidden="true"
                ><span class="cubuk-dolgu" :style="{ width: genislik(b.adet, belgeEnCok) }"></span
              ></span>
            </td>
          </tr>
        </tbody>
      </table>
    </section>

    <section class="ozet-kart" data-test="degisiklik" aria-labelledby="oz-degisiklik">
      <h2 id="oz-degisiklik">Değişiklik kayıtları</h2>
      <p class="ozet-sayi">
        <strong data-test="degisiklik-sayi">{{ ozet.degisiklik.kayitSayisi }}</strong> kayıt
      </p>
      <table
        v-if="ozet.degisiklik.nedenler.length"
        :class="tablo"
        aria-label="Değişiklik nedenleri"
      >
        <thead>
          <tr>
            <th scope="col">Neden</th>
            <th scope="col" class="sayisal">Adet</th>
            <th v-if="!yazdir" scope="col" class="gorunmez">Oran</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="n in ozet.degisiklik.nedenler" :key="n.ad">
            <td>{{ nedenAdi(n.ad) }}</td>
            <td class="sayisal">{{ n.adet }}</td>
            <td v-if="!yazdir" class="cubuk-hucre">
              <span class="cubuk" aria-hidden="true"
                ><span
                  class="cubuk-dolgu"
                  :style="{ width: genislik(n.adet, enCok(ozet.degisiklik.nedenler)) }"
                ></span
              ></span>
            </td>
          </tr>
        </tbody>
      </table>
      <p v-else class="soluk">Nedenli değişiklik kaydı yok.</p>
    </section>

    <section class="ozet-kart" data-test="yedek" aria-labelledby="oz-yedek">
      <h2 id="oz-yedek">Yedek açığı</h2>
      <p class="ozet-sayi">
        <strong data-test="yedek-gun">{{ ozet.yedek.acikGunSayisi }}</strong> gün
      </p>
      <p class="soluk">
        Yedek makinist ihtiyacının karşılanmadığı gün (toplam açık {{ ozet.yedek.toplamAcik }}).
      </p>
    </section>
  </div>
</template>

<style scoped>
.ozet-govde {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(20rem, 1fr));
  gap: var(--aralik-4);
}
.ozet-kart {
  background: var(--renk-yuzey);
  border: 1px solid var(--renk-cizgi);
  border-radius: var(--yaricap);
  padding: var(--aralik-4) var(--aralik-5);
}
.ozet-kart h2 {
  margin: 0 0 var(--aralik-2);
  font-size: var(--yazi-lg);
}
.ozet-kart p {
  margin: var(--aralik-2) 0 0;
}
.ozet-sayi {
  font-size: var(--yazi-xl);
}
.ozet-sayi strong {
  font-size: var(--yazi-2xl);
}
.ozet-liste {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(7rem, 1fr));
  gap: var(--aralik-3);
  margin: 0;
}
.ozet-liste div {
  min-width: 0;
}
.ozet-liste dt {
  font-size: var(--yazi-sm);
  color: var(--renk-metin-ikincil);
}
.ozet-liste dd {
  margin: 0;
  font-weight: var(--yazi-agirlik-orta);
}
/* Tek seri satır içi çubuk: yalnız süs, sayı her zaman yanında yazılıdır. */
.cubuk {
  display: block;
  height: 0.5rem;
  background: var(--renk-cubuk-izgara);
  border-radius: var(--yaricap-pill);
  overflow: hidden;
}
.cubuk-dolgu {
  display: block;
  height: 100%;
  background: var(--renk-cubuk-dolu);
}
.cubuk-hucre {
  width: 35%;
}
.ozet-yazdir {
  display: block;
}
/* Yazdırma siyah-beyazdır: soluk gri metin de siyah basılır. */
.ozet-yazdir .soluk,
.ozet-yazdir .ozet-liste dt {
  color: inherit;
}
.ozet-yazdir .ozet-kart {
  margin-bottom: var(--aralik-3);
  border: 0;
  padding: 0;
  break-inside: avoid;
}
</style>
