<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ApiHata } from '@/api/istemci'
import OzetGovdesi from '@/features/rapor/OzetGovdesi.vue'
import { ozetApi, type YonetimOzeti } from '@/features/rapor/ozetApi'
import { ayGecerliMi } from '@/lib/tarih'
import YazdirSayfasi from './YazdirSayfasi.vue'

/**
 * Yönetim özetinin yazdırılabilir sayfası (F4, FR-4.4): menüsüz, A4 yatay (global kural), siyah-beyaz; başlıkta kurum adı (YazdirSayfasi), altbilgide sunucu zamanı ve yazdıran.
 * Gövde ekranla aynı bileşendir (`OzetGovdesi yazdir`): sayılar özdeştir. Ay rota sorgusundan gelir (?ay=YYYY-MM).
 */
const props = defineProps<{ ay?: string }>()

const ozet = ref<YonetimOzeti | null>(null)
const hata = ref('')
const filtre = computed(
  () => `Dönem: ${props.ay ?? ''} (takvim ayı)${ozet.value?.demo ? ' · DEMO – gerçek değil' : ''}`,
)

onMounted(async () => {
  if (!props.ay) {
    hata.value = 'Ay gerekli.'
    return
  }
  if (!ayGecerliMi(props.ay)) {
    hata.value = 'Ay geçersiz: YYYY-AA biçiminde olmalı (ör. 2026-10).'
    return
  }
  try {
    ozet.value = await ozetApi.getir(props.ay)
  } catch (e) {
    hata.value = e instanceof ApiHata ? e.detail : 'Özet yüklenemedi.'
  }
})
</script>

<template>
  <YazdirSayfasi
    baslik="Yönetim özeti"
    :filtre="filtre"
    :uretim-zamani="ozet?.uretimZamani ?? null"
    :kayit-sayisi="ozet?.degisiklik.kayitSayisi ?? 0"
    :geri="{ name: 'rapor-ozet' }"
    :yazdirilabilir="ozet != null && !hata"
  >
    <p v-if="hata" class="bildirim bildirim-hata no-print" role="alert">{{ hata }}</p>
    <OzetGovdesi v-if="ozet" :ozet="ozet" yazdir />
  </YazdirSayfasi>
</template>
