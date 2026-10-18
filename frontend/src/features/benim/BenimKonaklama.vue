<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ApiHata } from '@/api/istemci'
import Rozet from '@/bilesenler/Rozet.vue'
import SayfaBasligi from '@/bilesenler/SayfaBasligi.vue'
import Simge from '@/bilesenler/Simge.vue'
import VeriDurumu from '@/bilesenler/VeriDurumu.vue'
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
    <SayfaBasligi baslik="Konaklamam" />
    <p class="soluk">Önümüzdeki günlerde dış garda kalacağınız yerler.</p>
    <VeriDurumu v-if="yukleniyor" durum="yukleniyor" metin="Yükleniyor…" />
    <VeriDurumu v-else-if="hata" durum="hata" :metin="hata" />
    <VeriDurumu
      v-else-if="kayitlar.length === 0"
      durum="bos"
      metin="Bu günlerde konaklama kaydınız yok."
      data-test="bos"
    />
    <ul v-else class="benim-liste">
      <li v-for="k in kayitlar" :key="k.id" class="kart benim-kart" data-test="konaklama">
        <p class="benim-saat">{{ k.gar }}</p>
        <p class="benim-satir">
          <Rozet ton="notr" simge="yatak" :metin="YER_ETIKETLERI[k.yerTipi]" />
          {{ zamanBicimle(k.giris) }} – {{ zamanBicimle(k.cikis) }}
          <small>({{ k.gece }} gece)</small>
        </p>
      </li>
    </ul>
    <p class="benim-alt-eylem">
      <a href="#/benim/gorevler" class="btn"><Simge ad="ok-sol" /> Görevlerim</a>
    </p>
  </section>
</template>
