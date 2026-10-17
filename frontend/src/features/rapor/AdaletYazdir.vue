<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ApiHata } from '@/api/istemci'
import YazdirSayfasi from '@/features/yazdir/YazdirSayfasi.vue'
import AdaletTablosu from './AdaletTablosu.vue'
import { raporApi, type AdaletRaporu } from './raporApi'

/** Adalet raporunun yazdırma sayfası; ay rota sorgusundan gelir (?ay=YYYY-MM). */
const props = defineProps<{ ay?: string; tur?: string }>()

const rapor = ref<AdaletRaporu | null>(null)
const hata = ref('')
/** Tür yalnız kondüktör için gelir; başka değer makinist grubu sayılır. */
const kondukturMu = computed(() => props.tur === 'KONDUKTOR')
const filtre = computed(
  () => `Ay: ${props.ay ?? ''}` + (kondukturMu.value ? ' · Personel: Kondüktör' : ''),
)

onMounted(async () => {
  if (!props.ay) {
    hata.value = 'Ay gerekli.'
    return
  }
  try {
    rapor.value = await (kondukturMu.value
      ? raporApi.adalet(props.ay, 'KONDUKTOR')
      : raporApi.adalet(props.ay))
  } catch (e) {
    hata.value = e instanceof ApiHata ? e.detail : 'Rapor yüklenemedi.'
  }
})
</script>

<template>
  <YazdirSayfasi
    baslik="Adalet raporu"
    :filtre="filtre"
    :uretim-zamani="rapor?.uretimZamani ?? null"
    :kayit-sayisi="rapor?.satirlar.length ?? 0"
    :geri="{ name: 'rapor-adalet' }"
    :yazdirilabilir="rapor != null && !hata"
  >
    <p v-if="hata" class="bildirim bildirim-hata no-print" role="alert">{{ hata }}</p>
    <AdaletTablosu v-if="rapor" :rapor="rapor" yazdir />
  </YazdirSayfasi>
</template>
