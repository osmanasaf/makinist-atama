<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ApiHata } from '@/api/istemci'
import YazdirSayfasi from '@/features/yazdir/YazdirSayfasi.vue'
import PuantajTablo from './PuantajTablo.vue'
import { puantajApi, type PuantajYaniti } from './puantajApi'

/** Puantaj ön hesabının yazdırma sayfası; süzgeç rota sorgusundan gelir (?ay=&makinist=). */
const props = defineProps<{ ay?: string; makinist?: string; tur?: string }>()

const rapor = ref<PuantajYaniti | null>(null)
const hata = ref('')

const filtre = computed(() => {
  const m = rapor.value?.satirlar.find((s) => String(s.makinistId) === props.makinist)
  const parcalar = [`Ay: ${props.ay ?? ''}`]
  if (props.tur === 'KONDUKTOR') parcalar.push('Personel: Kondüktör')
  if (props.makinist) parcalar.push(`Makinist: ${m ? `${m.sicil} — ${m.adSoyad}` : props.makinist}`)
  return parcalar.join(' · ')
})

onMounted(async () => {
  if (!props.ay) {
    hata.value = 'Ay gerekli.'
    return
  }
  try {
    rapor.value = await puantajApi.rapor({ ay: props.ay, makinist: props.makinist, tur: props.tur })
  } catch (e) {
    hata.value = e instanceof ApiHata ? e.detail : 'Rapor yüklenemedi.'
  }
})
</script>

<template>
  <YazdirSayfasi
    baslik="Puantaj ön hesabı"
    :filtre="filtre"
    :uretim-zamani="rapor?.uretimZamani ?? null"
    :kayit-sayisi="rapor?.satirlar.length ?? 0"
    :geri="{ name: 'rapor-puantaj' }"
    :yazdirilabilir="rapor != null && !hata"
  >
    <p v-if="hata" class="bildirim bildirim-hata no-print" role="alert">{{ hata }}</p>
    <PuantajTablo v-if="rapor" :rapor="rapor" yazdir />
  </YazdirSayfasi>
</template>
