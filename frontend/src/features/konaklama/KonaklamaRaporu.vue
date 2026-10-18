<script setup lang="ts">
import AlanHatasi from '@/bilesenler/AlanHatasi.vue'
import { nextTick, onMounted, ref } from 'vue'
import { ApiHata } from '@/api/istemci'
import { bugun, zamanBicimle } from '@/lib/tarih'
import SayfaBasligi from '@/bilesenler/SayfaBasligi.vue'
import VeriDurumu from '@/bilesenler/VeriDurumu.vue'
import { konaklamaApi, type KonaklamaRaporu } from './konaklamaApi'

/** Aylık konaklama gece raporu (F2b, MK-44): makinist x gar; ay = giriş günü; "kaydı eksik öneri" = konaklama gerektiren ama kaydı girilmemiş boşluklar. */
const ay = ref(bugun().slice(0, 7))
const rapor = ref<KonaklamaRaporu | null>(null)
/** Görünen raporun ayı: form sonradan değişse de Excel bununla çalışır. */
const uygulanan = ref('')
const hata = ref('')
const ayHata = ref('')
const ayAlani = ref<HTMLInputElement | null>(null)
const mesgul = ref(false)

async function getir() {
  if (mesgul.value) return
  hata.value = ''
  ayHata.value = ''
  if (!ay.value) {
    ayHata.value = 'Ay seçin.'
    await nextTick()
    ayAlani.value?.focus()
    return
  }
  const secilen = ay.value
  mesgul.value = true
  rapor.value = null
  try {
    rapor.value = await konaklamaApi.rapor(secilen)
    uygulanan.value = secilen
  } catch (e) {
    hata.value = e instanceof ApiHata ? e.detail : 'Rapor alınamadı.'
  } finally {
    mesgul.value = false
  }
}
onMounted(getir)

async function excelIndir() {
  hata.value = ''
  try {
    const { blob, ad } = await konaklamaApi.raporExcel(uygulanan.value)
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = ad ?? `konaklama-raporu-${uygulanan.value}.xlsx`
    a.click()
    URL.revokeObjectURL(url)
  } catch (e) {
    hata.value = e instanceof ApiHata ? e.detail : 'Excel indirilemedi.'
  }
}
</script>

<template>
  <section class="rapor-sayfa">
    <SayfaBasligi baslik="Konaklama raporu">
      <template v-if="rapor" #eylemler>
        <button type="button" class="btn" @click="excelIndir">Excel indir</button>
      </template>
    </SayfaBasligi>

    <form class="arac-cubugu" @submit.prevent="getir">
      <div class="alan alan-zorunlu">
        <label for="kr-ay">Ay</label>
        <input
          id="kr-ay"
          ref="ayAlani"
          v-model="ay"
          type="month"
          required
          :aria-invalid="ayHata ? 'true' : undefined"
          :aria-describedby="ayHata ? 'kr-ay-hata' : undefined"
        />
        <AlanHatasi id="kr-ay-hata" :metin="ayHata" />
      </div>
      <button type="submit" class="btn btn-birincil" :disabled="mesgul">Raporu getir</button>
    </form>

    <VeriDurumu v-if="mesgul" durum="yukleniyor" metin="Rapor hazırlanıyor…" />
    <VeriDurumu v-else-if="hata && !rapor" durum="hata" :metin="hata" />
    <p v-else-if="hata" class="bildirim bildirim-hata" role="alert">{{ hata }}</p>

    <template v-if="rapor">
      <div class="kart ozet" data-test="ozet">
        <p>
          Toplam <strong class="sayisal">{{ rapor.toplamGece }}</strong> gece · kaydı eksik öneri
          <strong class="sayisal">{{ rapor.toplamEksikOneri }}</strong>
        </p>
        <p class="soluk">
          Üretim zamanı: {{ zamanBicimle(rapor.uretimZamani) }} · Üreten: {{ rapor.ureten }}. Gece =
          süre / 24 saat yukarı yuvarlanır (en az 1); konaklama giriş gününün ayına yazılır.
        </p>
      </div>

      <VeriDurumu
        v-if="rapor.makinistler.length === 0"
        durum="bos"
        metin="Bu ay konaklama kaydı ve öneri yok."
      />
      <div v-else class="tablo-kap">
        <table class="tablo" aria-label="Aylık konaklama">
          <thead>
            <tr>
              <th>Personel</th>
              <th>Gar</th>
              <th class="sayisal">Konaklama</th>
              <th class="sayisal">Gece</th>
              <th class="sayisal">Kaydı eksik öneri</th>
            </tr>
          </thead>
          <tbody>
            <template v-for="m in rapor.makinistler" :key="m.makinistId">
              <tr v-for="(g, i) in m.garlar" :key="`${m.makinistId}-${g.garId}`" data-test="satir">
                <td>
                  <template v-if="i === 0"
                    >{{ m.adSoyad }} <small class="soluk">{{ m.sicil }}</small></template
                  >
                </td>
                <td>{{ g.gar }}</td>
                <td class="sayisal">{{ g.konaklamaSayisi }}</td>
                <td class="sayisal">{{ g.gece }}</td>
                <td class="sayisal">
                  <template v-if="i === 0">{{ m.eksikOneri }}</template>
                </td>
              </tr>
              <tr v-if="m.garlar.length === 0" data-test="satir">
                <td>
                  {{ m.adSoyad }} <small class="soluk">{{ m.sicil }}</small>
                </td>
                <td class="soluk">—</td>
                <td class="sayisal">0</td>
                <td class="sayisal">0</td>
                <td class="sayisal">{{ m.eksikOneri }}</td>
              </tr>
              <tr v-if="m.garlar.length > 1" class="toplam" data-test="toplam">
                <td></td>
                <td><strong>Toplam</strong></td>
                <td class="sayisal">{{ m.konaklamaSayisi }}</td>
                <td class="sayisal">
                  <strong>{{ m.gece }}</strong>
                </td>
                <td></td>
              </tr>
            </template>
          </tbody>
        </table>
      </div>
    </template>
  </section>
</template>

<style scoped>
.ozet p {
  margin: 0 0 var(--aralik-2);
}
.toplam td {
  background: var(--renk-n-50);
}
</style>
