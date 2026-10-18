<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ApiHata } from '@/api/istemci'
import type { Tarih } from '@/api/tipler'
import { saatBicimle } from '@/lib/tarih'
import Diyalog from '@/bilesenler/Diyalog.vue'
import VeriDurumu from '@/bilesenler/VeriDurumu.vue'
import Simge from '@/bilesenler/Simge.vue'
import NobetNotu from './NobetNotu.vue'
import { atamaApi, type UygunSefer } from './atamaApi'

/** Kırmızı (iptal sefer) makinistin o gün atanabileceği makinistsiz seferler. Seçim üst bileşende AtamaDialog'u açar. */
const props = defineProps<{ makinistId: number; makinistAdi: string; gun: Tarih }>()
const emit = defineEmits<{ sec: [sefer: UygunSefer]; kapat: [] }>()

const seferler = ref<UygunSefer[] | null>(null)
const hata = ref('')
const yukleniyor = ref(true)

const nobetMetinleri = computed(() =>
  (seferler.value ?? []).flatMap((s) => s.uyarilar.map((u) => u.metin)),
)

onMounted(async () => {
  try {
    seferler.value = await atamaApi.uygunSeferler(props.makinistId, props.gun)
  } catch (e) {
    hata.value = e instanceof ApiHata ? e.detail : 'Uygun seferler yüklenemedi.'
  } finally {
    yukleniyor.value = false
  }
})
</script>

<template>
  <Diyalog baslik="Uygun seferler" boyut="genis" @kapat="emit('kapat')">
    <p>
      <strong>{{ makinistAdi }}</strong> bu gün şu makinistsiz seferlere atanabilir.
    </p>
    <p class="soluk">
      İptal seferdeki eski atama olduğu gibi kalır; gerekirse masadaki "Kaldır" düğmesiyle kaldırın.
    </p>

    <VeriDurumu v-if="yukleniyor" durum="yukleniyor" metin="Seferler yükleniyor…" />
    <div role="alert">
      <p v-if="hata" class="bildirim bildirim-hata">{{ hata }}</p>
    </div>
    <VeriDurumu
      v-if="seferler && seferler.length === 0"
      durum="bos"
      metin="Bu gün için bu makinistin atanabileceği sefer yok: seferlerin tümü dolu ya da kurallar (dinlenme, izin, yetki vb.) uygun görmüyor."
    />

    <div v-if="seferler && seferler.length > 0" class="tablo-kap">
      <table class="tablo" aria-label="Uygun seferler">
        <thead>
          <tr>
            <th>Saat</th>
            <th>Tren no</th>
            <th>Güzergâh</th>
            <th>Uyarılar</th>
            <th><span class="gorunmez">Seç</span></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="s in seferler" :key="s.seferId">
            <td>{{ saatBicimle(s.guncelKalkis) }}–{{ saatBicimle(s.guncelVaris) }}</td>
            <td>{{ s.trenNo }}</td>
            <td>{{ s.kalkisGar }} – {{ s.varisGar }}</td>
            <td>
              <ul v-if="s.uyarilar.length > 0" class="ihlal-listesi">
                <li v-for="(u, i) in s.uyarilar" :key="i">
                  <Simge ad="uyari" boyut="1em" /> {{ u.metin }}
                </li>
              </ul>
            </td>
            <td>
              <button
                type="button"
                class="btn btn-birincil"
                :aria-label="`${s.trenNo} seferine ata`"
                @click="emit('sec', s)"
              >
                Seç
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <NobetNotu :metinler="nobetMetinleri" />

    <template #eylemler>
      <button type="button" class="btn" @click="emit('kapat')">Kapat</button>
    </template>
  </Diyalog>
</template>

<style scoped>
.ihlal-listesi {
  margin: 0;
  padding-left: 0;
  list-style: none;
}
</style>
