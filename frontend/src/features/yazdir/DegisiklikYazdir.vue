<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ApiHata } from '@/api/istemci'
import { tarihBicimle } from '@/lib/tarih'
import DegisiklikBolumleri from '@/features/degisiklik/DegisiklikBolumleri.vue'
import {
  degisiklikApi,
  suzgecOzeti,
  type DegisiklikTuru,
  type DegisiklikYaniti,
} from '@/features/degisiklik/degisiklikApi'
import YazdirSayfasi from './YazdirSayfasi.vue'

/** Denetim raporunun yazdırma sayfası; süzgeç rota sorgusundan gelir (rapor sayfasındaki "Yazdır" bağlantısı). */
const props = defineProps<{
  bas?: string
  bit?: string
  trenNo?: string
  kullanici?: string
  tur?: string
}>()

const rapor = ref<DegisiklikYaniti | null>(null)
const hata = ref('')

const filtre = computed(() =>
  rapor.value
    ? suzgecOzeti(rapor.value.filtre)
    : `Tarih: ${tarihBicimle(props.bas)} – ${tarihBicimle(props.bit)}`,
)

onMounted(async () => {
  if (!props.bas || !props.bit) {
    hata.value = 'Başlangıç ve bitiş tarihi gerekli.'
    return
  }
  try {
    rapor.value = await degisiklikApi.rapor({
      bas: props.bas,
      bit: props.bit,
      trenNo: props.trenNo,
      kullanici: props.kullanici,
      tur: props.tur as DegisiklikTuru | undefined,
    })
  } catch (e) {
    hata.value = e instanceof ApiHata ? e.detail : 'Rapor yüklenemedi.'
  }
})
</script>

<template>
  <YazdirSayfasi
    baslik="Değişiklik (denetim) raporu"
    :filtre="filtre"
    :uretim-zamani="rapor?.uretimZamani ?? null"
    :kayit-sayisi="rapor?.kayitSayisi ?? 0"
    :geri="{ name: 'degisiklik' }"
    :yazdirilabilir="rapor != null && !hata"
  >
    <p v-if="hata" class="bildirim bildirim-hata no-print" role="alert">{{ hata }}</p>
    <DegisiklikBolumleri v-if="rapor" :rapor="rapor" yazdir />
  </YazdirSayfasi>
</template>
