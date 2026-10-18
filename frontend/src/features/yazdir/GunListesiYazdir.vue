<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ApiHata } from '@/api/istemci'
import { saatBicimle, tarihBicimle } from '@/lib/tarih'
import { trenAdi } from '@/lib/trenAdi'
import {
  atamaApi,
  type GunListesi,
  type GunMakinisti,
  type GunSatiri,
} from '@/features/atama/atamaApi'
import NobetNotu from '@/features/atama/NobetNotu.vue'
import { durumMetni } from './durumMetni'
import IntikalYazdirBlogu from '@/features/intikal/IntikalYazdirBlogu.vue'
import YazdirSayfasi from './YazdirSayfasi.vue'

const props = defineProps<{ tarih: string }>()

const veri = ref<GunListesi | null>(null)
const hata = ref('')

const asil = (s: GunSatiri) => s.makinistler.find((m) => m.rol === 'ASIL')
const yardimci = (s: GunSatiri) => s.makinistler.find((m) => m.rol === 'YARDIMCI')
const konduktor = (s: GunSatiri) => s.makinistler.find((m) => m.rol === 'KONDUKTOR')
const yolcular = (s: GunSatiri) => s.makinistler.filter((m) => m.rol === 'YOLCU')

/** Yazdırmada renksiz de okunur: kırmızı görevlinin yanına ilk neden. */
const kontrol = (m: GunMakinisti) =>
  m.kirmizi ? `(KONTROL: ${m.kirmiziNedenler[0] ?? 'kuralları kontrol edin'})` : ''

/** Kâğıtta görünen nedenler (her görevlinin ilk kırmızı nedeni); not sayfa başına bir kez basılır. */
const nobetMetinleri = computed(() =>
  (veri.value?.satirlar ?? []).flatMap((s) =>
    s.makinistler.filter((m) => m.kirmizi).map((m) => m.kirmiziNedenler[0]),
  ),
)

// Adresteki tarih değişince (geri tuşu, elle düzenleme) yeniden yüklenir; geç dönen eski gün yanıtı yeni günü ezmez.
let sayac = 0
async function yukle(tarih: string) {
  const sira = ++sayac
  hata.value = ''
  veri.value = null
  try {
    const sonuc = await atamaApi.gun(tarih)
    if (sira === sayac) veri.value = sonuc
  } catch (e) {
    if (sira === sayac) hata.value = e instanceof ApiHata ? e.detail : 'Liste yüklenemedi.'
  }
}
onMounted(() => yukle(props.tarih))
watch(
  () => props.tarih,
  (t) => void yukle(t),
)
</script>

<template>
  <YazdirSayfasi
    baslik="Günlük atama listesi"
    :filtre="`Tarih: ${tarihBicimle(tarih)}`"
    :uretim-zamani="veri?.uretimZamani ?? null"
    :kayit-sayisi="veri?.satirlar.length ?? 0"
    :geri="{ name: 'atama', query: { tarih } }"
    :yazdirilabilir="veri != null && !hata"
  >
    <p v-if="hata" class="bildirim bildirim-hata no-print" role="alert">{{ hata }}</p>
    <div class="yazdir-tablo-kap">
      <table class="yazdir-tablo" aria-label="Günlük atama listesi">
        <thead>
          <tr>
            <th>Saat</th>
            <th>Tren no</th>
            <th>Güzergâh</th>
            <th>Araç tipi</th>
            <th>Hat</th>
            <th>Personel</th>
            <th>Durum</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="s in veri?.satirlar ?? []" :key="s.seferId">
            <td>{{ saatBicimle(s.guncelKalkis) }}–{{ saatBicimle(s.guncelVaris) }}</td>
            <td>{{ trenAdi(s) }}</td>
            <td>{{ s.kalkisGar }} – {{ s.varisGar }}</td>
            <td>{{ s.aracTipi }}</td>
            <td>{{ s.hat }}</td>
            <td class="makinist">
              <template v-if="asil(s)">
                {{ asil(s)!.ad }} ({{ asil(s)!.sicil }}) {{ kontrol(asil(s)!) }}
              </template>
              <template v-else-if="s.durum === 'IPTAL'">—</template>
              <strong v-else>BOŞ</strong>
              <div v-if="yardimci(s)">Yrd: {{ yardimci(s)!.ad }} {{ kontrol(yardimci(s)!) }}</div>
              <div v-if="konduktor(s)">
                Kond: {{ konduktor(s)!.ad }} {{ kontrol(konduktor(s)!) }}
              </div>
              <div v-for="y in yolcular(s)" :key="y.atamaId">
                Yolcu: {{ y.sicil }} {{ kontrol(y) }}
              </div>
            </td>
            <td class="durum">{{ durumMetni(s) }}</td>
          </tr>
        </tbody>
      </table>
    </div>
    <IntikalYazdirBlogu :tarih="tarih" />
    <NobetNotu :metinler="nobetMetinleri" />
  </YazdirSayfasi>
</template>
