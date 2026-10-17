<script setup lang="ts">
import { computed } from 'vue'
import { sayiBicimle } from '@/lib/sayi'
import type { PuantajYaniti } from './puantajApi'

/** Puantaj tablosu + "ön hesap / demo" etiketleri (rapor ve yazdırma sayfası ortak). */
const props = defineProps<{ rapor: PuantajYaniti; yazdir?: boolean }>()

const paraBicimleyici = new Intl.NumberFormat('tr-TR', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
})
const tl = (n: number) => `${paraBicimleyici.format(n)} TL`
const kmBilinmeyenler = computed(() =>
  props.rapor.satirlar
    .filter((s) => s.kmBilinmeyenSefer > 0)
    .map((s) => `${s.sicil} (${s.kmBilinmeyenSefer} sefer)`)
    .join(', '),
)
</script>

<template>
  <div>
    <p v-if="rapor.demo" class="bildirim bildirim-uyari demo-bant" role="status">
      <span aria-hidden="true">⚠</span> DEMO – gerçek değil
    </p>
    <p class="etiket">{{ rapor.etiket }}</p>
    <p v-if="!rapor.saatUcretiGirildi || !rapor.kmBirimGirildi" class="bildirim bildirim-uyari">
      Saat ücreti / km birim tutarı girilmemiş: yalnız miktarlar (saat, km) gösteriliyor. Oranları
      Ayarlar ekranından girin.
    </p>

    <p v-if="rapor.satirlar.length === 0" class="soluk">Bu ay için kayıt yok.</p>
    <div v-else :class="yazdir ? 'yazdir-tablo-kap' : 'tablo-kap'">
      <table :class="yazdir ? 'yazdir-tablo' : 'tablo'" aria-label="Puantaj ön hesabı">
        <thead>
          <tr>
            <th>Sicil</th>
            <th>Ad soyad</th>
            <th>Çalışılan gün</th>
            <th>Fiili saat</th>
            <th>Ücretlendirilen saat</th>
            <th>Fazla mesai saati</th>
            <th>Gece saati</th>
            <th>Km</th>
            <th>Fazla mesai TL</th>
            <th>Gece TL</th>
            <th>Km TL</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="s in rapor.satirlar" :key="s.makinistId">
            <td>{{ s.sicil }}</td>
            <td>{{ s.adSoyad }}</td>
            <td class="sayisal">{{ s.calisilanGun }}</td>
            <td class="sayisal">{{ sayiBicimle(s.fiiliSaat) }}</td>
            <td class="sayisal">{{ sayiBicimle(s.ucretlendirilenSaat) }}</td>
            <td class="sayisal">{{ sayiBicimle(s.fazlaMesaiSaat) }}</td>
            <td class="sayisal">{{ sayiBicimle(s.geceSaat) }}</td>
            <td class="sayisal">
              {{ sayiBicimle(s.km) }}<span v-if="s.kmBilinmeyenSefer > 0"> *</span>
            </td>
            <td v-for="(t, i) in [s.fazlaMesaiTl, s.geceTl, s.kmTl]" :key="i" class="sayisal">
              <template v-if="t != null">{{ tl(t) }}</template>
              <span v-else class="soluk">oran girilmedi</span>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <p v-if="kmBilinmeyenler" class="soluk dipnot">
      * Km bilgisi olmayan sefer var, bu seferlerin km'si toplama eklenemedi: {{ kmBilinmeyenler }}
    </p>
  </div>
</template>

<style scoped>
.demo-bant {
  font-weight: 700;
}
.etiket {
  font-weight: 600;
  margin: 0 0 0.5rem;
}
.dipnot {
  margin-top: 0.5rem;
}
</style>
