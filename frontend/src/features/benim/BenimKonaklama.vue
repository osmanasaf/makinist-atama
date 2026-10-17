<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ApiHata } from '@/api/istemci'
import { zamanBicimle } from '@/lib/tarih'
import { benimApi, type BenimKonaklama } from './benimApi'
import { rozetYaz } from './rozet'

/** Makinistin kendi konaklaması (F2b OK-10): nerede kalacağını bilir. Yalnız okuma; atamacının iç notu burada yoktur. */
const YER_ETIKETLERI: Record<BenimKonaklama['yerTipi'], string> = {
  YATAKHANE: 'Yatakhane',
  OTEL: 'Otel',
  DIGER: 'Konaklama',
}

const kayitlar = ref<BenimKonaklama[]>([])
const yukleniyor = ref(true)
const hata = ref('')

onMounted(async () => {
  try {
    kayitlar.value = await benimApi.konaklama()
    rozetYaz(
      kayitlar.value.length === 0 ? 'Konaklama kaydınız yok' : `${kayitlar.value.length} konaklama`,
    )
  } catch (e) {
    hata.value = e instanceof ApiHata ? e.detail : 'Konaklama bilgisi yüklenemedi.'
  } finally {
    yukleniyor.value = false
  }
})
</script>

<template>
  <section>
    <h1>Konaklamam</h1>
    <p class="soluk">Önümüzdeki günlerde dış garda kalacağınız yerler.</p>
    <p v-if="yukleniyor" role="status">Yükleniyor…</p>
    <div v-else-if="hata" class="bildirim bildirim-hata" role="alert">{{ hata }}</div>
    <p v-else-if="kayitlar.length === 0" class="soluk" data-test="bos">
      Bu günlerde konaklama kaydınız yok.
    </p>
    <ul v-else class="benim-liste">
      <li v-for="k in kayitlar" :key="k.id" class="benim-kart" data-test="konaklama">
        <p class="benim-saat">{{ k.gar }}</p>
        <p>
          <span class="benim-etiket">{{ YER_ETIKETLERI[k.yerTipi] }}</span>
          {{ zamanBicimle(k.giris) }} – {{ zamanBicimle(k.cikis) }}
          <small class="soluk">({{ k.gece }} gece)</small>
        </p>
      </li>
    </ul>
    <p><a href="#/benim/gorevler">← Görevlerim</a></p>
  </section>
</template>
