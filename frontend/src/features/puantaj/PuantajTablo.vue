<script setup lang="ts">
import { computed } from 'vue'
import { sayiBicimle } from '@/lib/sayi'
import Simge from '@/bilesenler/Simge.vue'
import VeriDurumu from '@/bilesenler/VeriDurumu.vue'
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
      <Simge ad="uyari" boyut="1.1em" />DEMO – gerçek değil
    </p>
    <p class="etiket">{{ rapor.etiket }}</p>
    <p v-if="!rapor.saatUcretiGirildi || !rapor.kmBirimGirildi" class="bildirim bildirim-uyari">
      Saat ücreti / km birim tutarı girilmemiş: yalnız miktarlar (saat, km) gösteriliyor. Oranları
      Ayarlar ekranından girin.
    </p>

    <p v-if="rapor.satirlar.length === 0 && yazdir" class="soluk">Bu ay için kayıt yok.</p>
    <VeriDurumu v-else-if="rapor.satirlar.length === 0" durum="bos" metin="Bu ay için kayıt yok." />
    <div v-else :class="yazdir ? 'yazdir-tablo-kap' : 'tablo-kap'">
      <table :class="yazdir ? 'yazdir-tablo' : 'tablo'" aria-label="Puantaj ön hesabı">
        <thead>
          <tr>
            <th>Sicil</th>
            <th>Ad soyad</th>
            <th class="sayisal">Çalışılan gün</th>
            <th class="sayisal">Fiili saat</th>
            <th class="sayisal">Ücretlendirilen saat</th>
            <th class="sayisal">Fazla mesai saati</th>
            <th class="sayisal">Gece saati</th>
            <th class="sayisal">Km</th>
            <th class="sayisal">Fazla mesai TL</th>
            <th class="sayisal">Gece TL</th>
            <th class="sayisal">Km TL</th>
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
  display: flex;
  align-items: center;
  gap: var(--aralik-3);
  font-weight: var(--yazi-agirlik-kalin);
}
.etiket {
  font-weight: var(--yazi-agirlik-orta);
  margin: 0 0 var(--aralik-3);
}
.dipnot {
  margin-top: var(--aralik-3);
}
</style>
