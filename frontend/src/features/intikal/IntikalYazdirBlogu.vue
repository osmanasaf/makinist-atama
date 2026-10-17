<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import type { Tarih } from '@/api/tipler'
import { saatBicimle } from '@/lib/tarih'
import { ARAC_ETIKETLERI, intikalApi, type Intikal } from './intikalApi'

/**
 * Günlük atama listesinin yazdırma görünümüne günün serbest intikalleri (İ-1): yalnız PLANLI olanlar, renksiz metin (kırmızıysa "KONTROL: ilk neden"). Liste yüklenemezse ya da
 * boşsa hiçbir şey çizilmez: yazdırılan ana liste intikal yüzünden bozulmaz.
 */
const props = defineProps<{ tarih: Tarih }>()
const liste = ref<Intikal[]>([])

let sayac = 0
async function yukle(t: Tarih) {
  const sira = ++sayac
  try {
    const sonuc = await intikalApi.liste(t, t)
    if (sira === sayac) liste.value = sonuc
  } catch {
    if (sira === sayac) liste.value = []
  }
}
onMounted(() => void yukle(props.tarih))
watch(
  () => props.tarih,
  (t) => void yukle(t),
)

const planli = computed(() => liste.value.filter((i) => i.durum === 'PLANLI'))
const kontrol = (i: Intikal) =>
  i.kirmizi ? `(KONTROL: ${i.nedenler[0] ?? 'kuralları kontrol edin'})` : ''
</script>

<template>
  <div v-if="planli.length > 0" class="yazdir-tablo-kap" data-test="yazdir-intikal">
    <h2 class="yazdir-alt-baslik">İntikaller</h2>
    <table class="yazdir-tablo" aria-label="İntikaller">
      <thead>
        <tr>
          <th>Saat</th>
          <th>Personel</th>
          <th>Güzergâh</th>
          <th>Araç</th>
          <th>Durum</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="i in planli" :key="i.id">
          <td>{{ saatBicimle(i.kalkis) }}–{{ saatBicimle(i.varis) }}</td>
          <td>{{ i.adSoyad }} ({{ i.sicil }})</td>
          <td>{{ i.kalkisGar }} → {{ i.varisGar }}</td>
          <td>{{ ARAC_ETIKETLERI[i.aracTuru] }}</td>
          <td>{{ kontrol(i) }}</td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<style scoped>
.yazdir-alt-baslik {
  margin: 1rem 0 0.25rem;
  font-size: 1rem;
}
</style>
