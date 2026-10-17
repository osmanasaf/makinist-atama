<script setup lang="ts">
import { tarihBicimle, zamanBicimle } from '@/lib/tarih'
import {
  NEDEN_ETIKETLERI,
  ROL_ETIKETLERI,
  type AtamaNedeni,
  type AtamaRolu,
} from '@/features/atama/atamaApi'
import type { DegisiklikYaniti } from './degisiklikApi'

/** Denetim raporunun bölümleri (başlık + sayı + tablo). `yazdir`: renksiz yazdırma tablosu; anlam metinde ("⚠ Uyarılı: ..."). */
defineProps<{ rapor: DegisiklikYaniti; yazdir?: boolean }>()

/** Sunucudaki kodlar ham gösterilmez (UX-12): "GECIKME" -> "Gecikme", "ASIL:D058" -> "D058 (asıl)". Tanınmayan metin olduğu gibi kalır. */
const nedenMetni = (n: string | null) =>
  n && n in NEDEN_ETIKETLERI ? NEDEN_ETIKETLERI[n as AtamaNedeni] : n
function degerMetni(v: string | null): string {
  const m = v?.match(/^(ASIL|YARDIMCI|KONDUKTOR|YOLCU):(.+)$/)
  return m ? `${m[2]} (${ROL_ETIKETLERI[m[1] as AtamaRolu].toLowerCase()})` : v || '—'
}
</script>

<template>
  <section v-for="b in rapor.bolumler" :key="b.kod" class="bolum">
    <h2>{{ b.ad }} ({{ b.sayi }})</h2>
    <p v-if="b.satirlar.length === 0" class="soluk">Kayıt yok.</p>
    <div v-else :class="yazdir ? 'yazdir-tablo-kap' : 'tablo-kap'">
      <table :class="yazdir ? 'yazdir-tablo' : 'tablo'" :aria-label="b.ad">
        <thead>
          <tr>
            <th>Zaman</th>
            <th>Kim</th>
            <th>Tür</th>
            <th>Sicil</th>
            <th>Tren no</th>
            <th>Değişiklik (eski → yeni)</th>
            <th>Neden</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="s in b.satirlar" :key="s.id">
            <td>{{ zamanBicimle(s.zaman) }}</td>
            <td>{{ s.kim }}</td>
            <td>
              {{ s.turAdi }}
              <small v-if="s.otomatik" class="otomatik">(otomatik)</small>
            </td>
            <td>{{ s.sicil }}</td>
            <td>
              {{ s.trenNo }}
              <small v-if="s.seferTarihi">{{ tarihBicimle(s.seferTarihi) }}</small>
            </td>
            <td class="sarar">
              <template v-if="(s.tur.startsWith('KURAL_') || s.tur === 'GORDUM') && !s.eski">{{
                degerMetni(s.yeni)
              }}</template>
              <template v-else>{{ degerMetni(s.eski) }} → {{ degerMetni(s.yeni) }}</template>
            </td>
            <td class="sarar">
              {{ nedenMetni(s.neden) }}
              <template v-if="s.nedenNotu"> ({{ s.nedenNotu }})</template>
              <div v-if="s.uyarili" class="uyarili">
                <span aria-hidden="true">⚠</span> Uyarılı: {{ s.uyariGerekcesi }}
                <ul v-if="s.uyarilar.length > 0" class="uyaranlar">
                  <li v-for="u in s.uyarilar" :key="u">Uyaran kural: {{ u }}</li>
                </ul>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>

<style scoped>
.bolum {
  margin-bottom: 1.25rem;
}
.uyarili {
  font-weight: 600;
}
.uyaranlar {
  margin: 0;
  padding-left: 1.25rem;
  font-weight: 400;
}
</style>
