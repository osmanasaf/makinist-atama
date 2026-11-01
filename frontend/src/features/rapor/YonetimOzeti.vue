<script setup lang="ts">
import AlanHatasi from '@/bilesenler/AlanHatasi.vue'
import { nextTick, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { ApiHata } from '@/api/istemci'
import SayfaBasligi from '@/bilesenler/SayfaBasligi.vue'
import VeriDurumu from '@/bilesenler/VeriDurumu.vue'
import { blobIndir } from '@/lib/indir'
import { bugun, zamanBicimle } from '@/lib/tarih'
import OzetGovdesi from './OzetGovdesi.vue'
import { bosDonem, ozetApi, type YonetimOzeti } from './ozetApi'

/**
 * Yönetim özeti (F4, MK-180): yalnız yönetici; tek ay, tek sayfada mevcut raporların sayıları. Yeni hesap yok. Yazdırılabilir sayfa ve Excel denetim paketi (7 sayfa) buradan alınır.
 */
const ay = ref(bugun().slice(0, 7))
const ozet = ref<YonetimOzeti | null>(null)
/** Görünen özetin ayı: form sonradan değişse de Excel/Yazdır bununla çalışır. */
const uygulanan = ref('')
const hata = ref('')
const ayHata = ref('')
const ayAlani = ref<HTMLInputElement | null>(null)
const mesgul = ref(false)
const indiriliyor = ref(false)

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
  ozet.value = null
  try {
    ozet.value = await ozetApi.getir(secilen)
    uygulanan.value = secilen
  } catch (e) {
    hata.value = e instanceof ApiHata ? e.detail : 'Özet alınamadı.'
  } finally {
    mesgul.value = false
  }
}

onMounted(getir)

async function paketIndir() {
  if (indiriliyor.value) return
  hata.value = ''
  indiriliyor.value = true
  try {
    const { blob, ad } = await ozetApi.denetimPaketi(uygulanan.value)
    blobIndir(blob, ad ?? `denetim-paketi-${uygulanan.value}.xlsx`)
  } catch (e) {
    hata.value = e instanceof ApiHata ? e.detail : 'Denetim paketi indirilemedi.'
  } finally {
    indiriliyor.value = false
  }
}
</script>

<template>
  <section class="rapor-sayfa">
    <SayfaBasligi baslik="Yönetim özeti">
      <template v-if="ozet" #eylemler>
        <button
          type="button"
          class="btn"
          :aria-disabled="indiriliyor ? 'true' : undefined"
          @click="paketIndir"
        >
          Denetim paketi (Excel)
        </button>
        <RouterLink
          class="btn"
          :to="{ name: 'yazdir-ozet', query: { ay: uygulanan } }"
          target="_blank"
          rel="noopener"
        >
          Yazdırılabilir sayfa
        </RouterLink>
      </template>
    </SayfaBasligi>

    <form class="arac-cubugu" novalidate @submit.prevent="getir">
      <div class="alan alan-zorunlu">
        <label for="ozet-ay">Ay</label>
        <input
          id="ozet-ay"
          ref="ayAlani"
          v-model="ay"
          type="month"
          required
          :aria-invalid="ayHata ? 'true' : undefined"
          :aria-describedby="ayHata ? 'ozet-ay-hata' : undefined"
        />
        <AlanHatasi id="ozet-ay-hata" :metin="ayHata" />
      </div>
      <button type="submit" class="btn btn-birincil" :aria-disabled="mesgul ? 'true' : undefined">
        Özeti getir
      </button>
    </form>

    <VeriDurumu v-if="mesgul" durum="yukleniyor" metin="Özet hazırlanıyor…" />
    <VeriDurumu v-else-if="hata && !ozet" durum="hata" :metin="hata" />
    <p v-else-if="hata" class="bildirim bildirim-hata" role="alert">{{ hata }}</p>

    <template v-if="ozet">
      <p v-if="ozet.demo" class="bildirim bildirim-uyari" data-test="demo-etiketi">
        DEMO – gerçek değil: veritabanında demo makinistler var.
      </p>
      <div class="kart ozet-baslik" data-test="ozet-bilgi">
        <p>
          Dönem: <strong>{{ ozet.ay }}</strong> (takvim ayı) · Üretim zamanı:
          <strong>{{ zamanBicimle(ozet.uretimZamani) }}</strong> · Üreten:
          <strong>{{ ozet.ureten }}</strong>
        </p>
      </div>
      <VeriDurumu v-if="bosDonem(ozet)" durum="bos" metin="Bu ay için veri yok." />
      <OzetGovdesi :ozet="ozet" />
    </template>
  </section>
</template>

<style scoped>
.ozet-baslik p {
  margin: 0;
}
.ozet-baslik {
  margin-bottom: var(--aralik-4);
}
</style>
