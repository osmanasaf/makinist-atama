<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ApiHata } from '@/api/istemci'
import { saatBicimle } from '@/lib/tarih'
import { sayiBicimle } from '@/lib/sayi'
import { seferApi } from '@/features/sefer/seferApi'
import Diyalog from '@/bilesenler/Diyalog.vue'
import Simge from '@/bilesenler/Simge.vue'
import { atamaApi, type BirlestirSonucu } from './atamaApi'

/**
 * Parçalı seferi tek satıra birleştir (MK-126/128/129). Açılışta sunucu önizlemesi (`onizle:true`): koşullar
 * sağlanmıyorsa (409/422) sunucu gerekçesi gösterilir ve "Birleştir" kapalı kalır; sağlanıyorsa onayla -> uygula.
 * Birleşik satır istemcide HESAPLANMAZ; gösterilen sunucunun döndürdüğüdür.
 */
const props = defineProps<{
  seferId: number
  seferEtiketi: string
}>()
const emit = defineEmits<{
  /** `yenile`: hata aldık, sunucu durumu bizim bildiğimizden farklı olabilir; masa yeniden okumalı. */
  kapat: [yenile: boolean]
  tamam: [mesaj: string]
}>()

const versiyon = ref<number | null>(null)
const onizleme = ref<BirlestirSonucu | null>(null)
const mesgul = ref(true)
const genelHata = ref('')
let hataOldu = false

onMounted(async () => {
  try {
    versiyon.value = (await seferApi.getir(props.seferId)).versiyon
    onizleme.value = await atamaApi.birlestir(props.seferId, {
      versiyon: versiyon.value,
      onizle: true,
    })
  } catch (e) {
    hataOldu = true
    genelHata.value = e instanceof ApiHata ? e.detail : 'Birleştirme önizlemesi alınamadı.'
  } finally {
    mesgul.value = false
  }
})

// Birincil eylem kapalıyken nedeni söylenir (Diyalog neden slotu).
const nedenMetni = computed(() => {
  if (onizleme.value) return ''
  return mesgul.value ? 'Önizleme yükleniyor.' : 'Önizleme alınamadığı için birleştirilemez.'
})

function vazgec() {
  if (!mesgul.value) emit('kapat', hataOldu)
}

const yeniKirmizi = computed(() => onizleme.value?.yeniKirmizi ?? [])

async function birlestir() {
  if (mesgul.value || !onizleme.value || versiyon.value === null) return
  mesgul.value = true
  genelHata.value = ''
  try {
    const s = await atamaApi.birlestir(props.seferId, { versiyon: versiyon.value, onizle: false })
    const uyari =
      s.yeniKirmizi.length === 0
        ? ''
        : ` Dikkat, ${s.yeniKirmizi.length} atama kontrol bekliyor: ${s.yeniKirmizi
            .map((k) => `${k.trenNo} (${k.nedenler.join('; ')})`)
            .join(', ')}.`
    emit(
      'tamam',
      `Sefer birleştirildi (${s.silinenSeferIdler.length} parça satırı silindi).${uyari}`,
    )
  } catch (e) {
    hataOldu = true
    genelHata.value = e instanceof ApiHata ? e.detail : 'İşlem başarısız oldu.'
  } finally {
    mesgul.value = false
  }
}

/** "5s30dk" */
function sureMetni(s: BirlestirSonucu['sefer']): string {
  const dk = Math.round((Date.parse(s.guncelVaris) - Date.parse(s.guncelKalkis)) / 60000)
  return `${Math.floor(dk / 60)}s${dk % 60}dk`
}
</script>

<template>
  <Diyalog baslik="Seferi birleştir" boyut="orta" form @kapat="vazgec" @gonder="birlestir">
    <p>{{ seferEtiketi }}</p>
    <p class="bildirim bildirim-uyari">
      <span>
        <Simge ad="uyari" boyut="1em" /> Parça 2..N satırları ve atamaları silinir; geri alınamaz.
        Bölünmüş mü Excel'den mi gelen tren olduğu ayrılmaz: yalnız bölme hatasını geri almak için
        kullanın.
      </span>
    </p>

    <template v-if="onizleme">
      <div class="tablo-kap onizleme-kap">
        <table class="tablo onizleme-tablo" aria-label="Önizleme: birleşik sefer">
          <thead>
            <tr>
              <th>Güzergâh</th>
              <th>Saat</th>
              <th>Süre</th>
              <th>KM</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td>{{ onizleme.sefer.kalkisGar }}→{{ onizleme.sefer.varisGar }}</td>
              <td>
                {{ saatBicimle(onizleme.sefer.guncelKalkis) }}–{{
                  saatBicimle(onizleme.sefer.guncelVaris)
                }}
              </td>
              <td>{{ sureMetni(onizleme.sefer) }}</td>
              <td>
                {{ onizleme.sefer.km != null ? `${sayiBicimle(onizleme.sefer.km)} km` : '' }}
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <p>{{ onizleme.silinenSeferIdler.length }} parça satırı silinecek.</p>
      <div v-if="yeniKirmizi.length > 0" data-test="yeni-kirmizi" class="bildirim bildirim-uyari">
        <p>
          <Simge ad="engel" boyut="1em" /> Birleştirme şu atamaları kontrol bekler hâle getirir:
        </p>
        <ul>
          <li v-for="k in yeniKirmizi" :key="k.atamaId">
            {{ k.trenNo }}: {{ k.nedenler.join('; ') }}
          </li>
        </ul>
      </div>
    </template>

    <div role="alert">
      <div v-if="genelHata" class="bildirim bildirim-hata">
        <p>{{ genelHata }}</p>
      </div>
    </div>

    <template v-if="nedenMetni" #neden>{{ nedenMetni }}</template>
    <template #eylemler>
      <button type="submit" class="btn btn-birincil" :disabled="!onizleme || mesgul">
        Birleştir
      </button>
      <button type="button" class="btn" :disabled="mesgul" @click="vazgec">Vazgeç</button>
    </template>
  </Diyalog>
</template>

<style scoped>
.onizleme-tablo th,
.onizleme-tablo td {
  padding: 0.25rem 0.4rem;
}
/* 375 px: KM sütunu kırpılmasın (tablo-kap yatay kaydırır, hücreler sıkılaşır). */
@media (max-width: 480px) {
  .onizleme-tablo {
    font-size: 0.85rem;
  }
}
</style>
