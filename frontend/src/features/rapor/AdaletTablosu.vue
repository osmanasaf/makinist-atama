<script setup lang="ts">
import { sayiBicimle } from '@/lib/sayi'
import type { AdaletRaporu } from './raporApi'

/** Adalet raporu gövdesi (ekran ve yazdırma ortak). `yazdir`: renksiz yazdırma tablosu. */
defineProps<{ rapor: AdaletRaporu; yazdir?: boolean }>()

/** +%60 / −%100; ortalama 0 ise "—". */
function fark(f: number | null): string {
  if (f === null) return '—'
  return `${f > 0 ? '+' : f < 0 ? '−' : ''}%${sayiBicimle(Math.abs(f))}`
}
</script>

<template>
  <div class="adalet-govde">
    <p data-test="ortalama">
      {{ rapor.tur === 'KONDUKTOR' ? 'Kondüktör' : 'Makinist' }} sayısı:
      <strong>{{ rapor.makinistSayisi }}</strong> · Ortalama görev saati:
      <strong>{{ sayiBicimle(rapor.ortalamaSaat) }}</strong> · Ortalama km:
      <strong>{{ sayiBicimle(rapor.ortalamaKm) }}</strong>
    </p>
    <p class="soluk" data-test="olcut">
      Ölçüt: {{ rapor.olcut }} · Gece saati penceresi: {{ rapor.geceSaatiPenceresi }}
    </p>
    <div :class="yazdir ? 'yazdir-tablo-kap' : 'tablo-kap'">
      <table :class="yazdir ? 'yazdir-tablo' : 'tablo'" aria-label="Adalet raporu">
        <thead>
          <tr>
            <th>Sicil</th>
            <th>Ad soyad</th>
            <th class="sayisal">Sefer sayısı</th>
            <th class="sayisal">Görev saati</th>
            <th class="sayisal">Km</th>
            <th class="sayisal">Gece saati</th>
            <th class="sayisal">İzin günü</th>
            <th class="sayisal">Ortalamadan fark</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="s in rapor.satirlar" :key="s.makinistId">
            <td>{{ s.sicil }}</td>
            <td>
              {{ s.adSoyad }}
              <strong v-if="s.sifirSaat" class="sifir"
                ><span aria-hidden="true">⚠</span> 0 saat</strong
              >
            </td>
            <td class="sayisal">
              {{ s.seferSayisi }}
              <small v-if="s.yolculukSayisi" class="soluk" data-test="yolculuk-sayisi"
                >(+{{ s.yolculukSayisi }} yolculuk)</small
              >
            </td>
            <td class="sayisal">{{ sayiBicimle(s.gorevSaat) }}</td>
            <td class="sayisal">{{ sayiBicimle(s.km) }}</td>
            <td class="sayisal">{{ sayiBicimle(s.geceSaat) }}</td>
            <td class="sayisal">{{ s.izinGunu }}</td>
            <td class="sayisal">{{ fark(s.farkYuzde) }}</td>
          </tr>
          <tr v-if="rapor.satirlar.length === 0">
            <td colspan="8" class="bos-durum">Kayıt yok.</td>
          </tr>
        </tbody>
      </table>
    </div>
    <p v-if="rapor.kmBilinmeyenSefer > 0" class="soluk" data-test="km-dipnot">
      * {{ rapor.kmBilinmeyenSefer }} seferin km bilgisi girilmemiş; km toplamlarına dahil değil.
    </p>
  </div>
</template>

<style scoped>
.sifir {
  margin-left: 0.4rem;
  padding: 0 0.4rem;
  border: 1px solid currentcolor;
  border-radius: 999px;
  font-size: 0.85rem;
}
</style>
