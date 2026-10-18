<script setup lang="ts">
import AlanHatasi from '@/bilesenler/AlanHatasi.vue'
import { nextTick, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { ApiHata } from '@/api/istemci'
import { bugun, zamanBicimle } from '@/lib/tarih'
import SayfaBasligi from '@/bilesenler/SayfaBasligi.vue'
import VeriDurumu from '@/bilesenler/VeriDurumu.vue'
import AdaletTablosu from './AdaletTablosu.vue'
import { TUR_ETIKETLERI, type PersonelTuru } from '@/features/makinist/makinistApi'
import { raporApi, type AdaletRaporu } from './raporApi'

const ay = ref(bugun().slice(0, 7))
const tur = ref<PersonelTuru>('MAKINIST')
const rapor = ref<AdaletRaporu | null>(null)
/** Görünen raporun ayı ve türü: form sonradan değişse de Excel/Yazdır bununla çalışır. */
const uygulanan = ref('')
const uygulananTur = ref<PersonelTuru>('MAKINIST')
/** Makinist grubu varsayılandır: tür yalnız kondüktör için gönderilir (bugünkü istek birebir aynı). */
const turArgi = (t: PersonelTuru): [PersonelTuru?] => (t === 'KONDUKTOR' ? [t] : [])
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
  const secilenTur = tur.value
  mesgul.value = true
  rapor.value = null
  try {
    rapor.value = await raporApi.adalet(secilen, ...turArgi(secilenTur))
    uygulanan.value = secilen
    uygulananTur.value = secilenTur
  } catch (e) {
    hata.value = e instanceof ApiHata ? e.detail : 'Rapor alınamadı.'
  } finally {
    mesgul.value = false
  }
}

// Boş sayfa yerine varsayılan ayın raporu hemen gelir.
onMounted(getir)

async function excelIndir() {
  hata.value = ''
  try {
    const { blob, ad } = await raporApi.adaletExcel(uygulanan.value, ...turArgi(uygulananTur.value))
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = ad ?? `adalet-raporu-${uygulanan.value}.xlsx`
    a.click()
    URL.revokeObjectURL(url)
  } catch (e) {
    hata.value = e instanceof ApiHata ? e.detail : 'Excel indirilemedi.'
  }
}
</script>

<template>
  <section class="rapor-sayfa">
    <SayfaBasligi baslik="Adalet raporu">
      <template v-if="rapor" #eylemler>
        <button type="button" class="btn" @click="excelIndir">Excel indir</button>
        <RouterLink
          class="btn"
          :to="{
            name: 'yazdir-adalet',
            query: {
              ay: uygulanan,
              ...(uygulananTur === 'KONDUKTOR' ? { tur: uygulananTur } : {}),
            },
          }"
          target="_blank"
          rel="noopener"
        >
          Yazdır
        </RouterLink>
      </template>
    </SayfaBasligi>

    <form class="arac-cubugu" @submit.prevent="getir">
      <div class="alan alan-zorunlu">
        <label for="adalet-ay">Ay</label>
        <input
          id="adalet-ay"
          ref="ayAlani"
          v-model="ay"
          type="month"
          required
          :aria-invalid="ayHata ? 'true' : undefined"
          :aria-describedby="ayHata ? 'adalet-ay-hata' : undefined"
        />
        <AlanHatasi id="adalet-ay-hata" :metin="ayHata" />
      </div>
      <div class="alan">
        <label for="adalet-tur">Personel türü</label>
        <select id="adalet-tur" v-model="tur" @change="rapor = null">
          <option v-for="(etiket, k) in TUR_ETIKETLERI" :key="k" :value="k">{{ etiket }}</option>
        </select>
      </div>
      <button type="submit" class="btn btn-birincil" :disabled="mesgul">Raporu getir</button>
    </form>

    <VeriDurumu v-if="mesgul" durum="yukleniyor" metin="Rapor hazırlanıyor…" />
    <VeriDurumu v-else-if="hata && !rapor" durum="hata" :metin="hata" />
    <p v-else-if="hata" class="bildirim bildirim-hata" role="alert">{{ hata }}</p>

    <template v-if="rapor">
      <div class="kart ozet" data-test="ozet">
        <p>
          Üretim zamanı: <strong>{{ zamanBicimle(rapor.uretimZamani) }}</strong> · Üreten:
          <strong>{{ rapor.ureten }}</strong>
        </p>
      </div>
      <AdaletTablosu :rapor="rapor" />
    </template>
  </section>
</template>

<style scoped>
.ozet p {
  margin: 0;
}
</style>
