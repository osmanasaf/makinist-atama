<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ApiHata } from '@/api/istemci'
import type { Tarih } from '@/api/tipler'
import { useYoklama } from '@/features/benim/useYoklama'
import { saatBicimle, tarihBicimle } from '@/lib/tarih'
import IntikalDialog from './IntikalDialog.vue'
import { ARAC_ETIKETLERI, intikalApi, type Intikal } from './intikalApi'

/**
 * Atama masasında günün serbest intikalleri (İ-1, MK-106/107): sefer kaydı olmayan yolla (servis, otobüs, taksi) gar değiştirme. Kırmızı / uyarı durumu sunucuda canlı hesaplanır;
 * burada renksiz metinle de okunur. Ekle / düzelt / iptal diyalogları; iptal silmez. Atama kırmızı sayacına girmez (OK-28: masada ayrı "Kırmızı intikal" sayısı).
 */
const props = defineProps<{ tarih: Tarih }>()
const emit = defineEmits<{ degisti: [] }>()

const liste = ref<Intikal[]>([])
const yuklendi = ref(false)
const hata = ref('')
const bilgi = ref('')
type Diyalog = { kip: 'ekle' } | { kip: 'duzelt' | 'iptal'; intikal: Intikal }
const diyalog = ref<Diyalog | null>(null)

// Geç dönen eski gün yanıtı yeni günü ezmesin.
let sayac = 0
async function yukle(sessiz = false) {
  const sira = ++sayac
  if (!sessiz) hata.value = ''
  try {
    const sonuc = await intikalApi.liste(props.tarih, props.tarih)
    if (sira === sayac) {
      liste.value = sonuc
      hata.value = ''
    }
  } catch (e) {
    if (sira === sayac && !sessiz) {
      liste.value = []
      hata.value = e instanceof ApiHata ? e.detail : 'İntikaller yüklenemedi.'
    }
  } finally {
    if (sira === sayac) yuklendi.value = true
  }
}

onMounted(() => void yukle())
watch(
  () => props.tarih,
  () => {
    diyalog.value = null
    bilgi.value = ''
    void yukle()
  },
)
// Makinistler "gördüm" dedikçe şerit kendiliğinden güncellensin (masayla aynı 60 sn yoklama).
useYoklama(() => yukle(true))

const sayi = computed(() => liste.value.filter((i) => i.durum === 'PLANLI').length)
const ertesiGun = (i: Intikal) => i.varis.slice(0, 10) !== i.kalkis.slice(0, 10)

function kapat(yenile: boolean) {
  diyalog.value = null
  if (yenile) {
    void yukle()
    emit('degisti')
  }
}

function tamam(mesaj: string) {
  diyalog.value = null
  bilgi.value = mesaj
  void yukle()
  emit('degisti')
}
</script>

<template>
  <section class="intikal-seridi" aria-labelledby="intikal-baslik" data-test="intikal-seridi">
    <div class="serit-baslik">
      <h2 id="intikal-baslik">
        İntikaller <small class="soluk">({{ sayi }})</small>
      </h2>
      <button
        type="button"
        class="btn btn-kucuk"
        data-test="intikal-ekle"
        @click="diyalog = { kip: 'ekle' }"
      >
        İntikal ekle
      </button>
    </div>
    <div role="status">
      <p v-if="bilgi" class="bildirim bildirim-bilgi" data-test="intikal-bilgi">{{ bilgi }}</p>
    </div>
    <p v-if="hata" class="bildirim bildirim-hata" role="alert" data-test="intikal-hata">
      {{ hata }}
    </p>
    <p v-else-if="yuklendi && liste.length === 0" class="soluk" data-test="intikal-bos">
      {{ tarihBicimle(tarih) }} için intikal yok.
    </p>
    <ul v-else class="intikal-listesi">
      <li
        v-for="i in liste"
        :key="i.id"
        class="intikal"
        :class="{ iptal: i.durum === 'IPTAL', kirmizi: i.kirmizi }"
        data-test="intikal"
      >
        <div class="ana">
          <span class="metin">
            <strong>{{ i.adSoyad }} ({{ i.sicil }})</strong> · {{ i.kalkisGar }}
            {{ saatBicimle(i.kalkis) }} → {{ i.varisGar }} {{ saatBicimle(i.varis)
            }}<template v-if="ertesiGun(i)"> (ertesi gün)</template> ·
            {{ ARAC_ETIKETLERI[i.aracTuru] }}<template v-if="i.notu"> · {{ i.notu }}</template>
          </span>
          <span v-if="i.durum === 'IPTAL'" class="rozet rozet-iptal" data-test="intikal-iptal">
            <span aria-hidden="true">⛔</span> İPTAL
          </span>
          <span v-if="i.kirmizi" class="rozet rozet-kontrol" data-test="intikal-kirmizi">
            <span aria-hidden="true">⛔</span> KONTROL
          </span>
          <span v-if="i.gordu === 'GORULDU'" class="gordum goruldu" data-test="intikal-gordu">
            <span aria-hidden="true">✓</span> gördü {{ saatBicimle(i.gorduZamani) }}
          </span>
          <span
            v-else-if="i.gordu === 'DEGISTI'"
            class="gordum gormedi"
            data-test="intikal-degisti"
          >
            <span aria-hidden="true">↻</span> değişti, yeniden onay bekliyor
          </span>
          <span v-else class="gordum gormedi" data-test="intikal-gormedi">
            <span aria-hidden="true">○</span> görmedi
          </span>
          <span v-if="i.durum === 'PLANLI'" class="islem">
            <button
              type="button"
              class="btn btn-kucuk"
              :aria-label="`${i.adSoyad} intikalini düzelt`"
              data-test="intikal-duzelt"
              @click="diyalog = { kip: 'duzelt', intikal: i }"
            >
              Düzelt
            </button>
            <button
              type="button"
              class="btn btn-kucuk"
              :aria-label="`${i.adSoyad} intikalini iptal et`"
              data-test="intikal-iptal-et"
              @click="diyalog = { kip: 'iptal', intikal: i }"
            >
              İptal
            </button>
          </span>
        </div>
        <div v-if="i.kirmizi" class="kirmizi-neden">
          <span aria-hidden="true">⛔</span> {{ i.nedenler.join('; ') }}
        </div>
        <div v-if="i.uyarilar.length > 0" class="uyari-notu">
          <span aria-hidden="true">⚠</span> Uyarı: {{ i.uyarilar.join('; ') }}
        </div>
      </li>
    </ul>

    <IntikalDialog
      v-if="diyalog"
      :kip="diyalog.kip"
      :gun="tarih"
      :intikal="diyalog.kip === 'ekle' ? undefined : diyalog.intikal"
      @tamam="tamam"
      @kapat="kapat"
    />
  </section>
</template>

<style scoped>
.intikal-seridi {
  margin: 0 0 var(--bosluk);
}
.serit-baslik {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}
.serit-baslik h2 {
  margin: 0;
  font-size: 1.05rem;
}
.intikal-listesi {
  list-style: none;
  margin: 0.25rem 0 0;
  padding: 0;
}
.intikal {
  padding: 0.3rem 0.5rem;
  border-bottom: 1px solid var(--renk-cizgi, #ddd);
}
.intikal.kirmizi {
  background: var(--renk-hata-zemin);
  box-shadow: inset 4px 0 0 var(--renk-tehlike);
}
.intikal.iptal .metin {
  text-decoration: line-through;
  color: var(--renk-soluk);
}
.ana {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 0.25rem 0.6rem;
}
.rozet {
  display: inline-block;
  padding: 0.05rem 0.5rem;
  border: 1px solid currentcolor;
  border-radius: 999px;
  font-weight: 700;
  font-size: 0.85rem;
}
.rozet-iptal,
.rozet-kontrol {
  color: var(--renk-tehlike);
  background: var(--renk-hata-zemin);
}
.gordum {
  font-size: 0.85rem;
  white-space: nowrap;
}
.gordum.gormedi {
  color: var(--renk-soluk);
}
.islem {
  margin-left: auto;
  display: inline-flex;
  gap: 0.25rem;
}
.kirmizi-neden {
  color: var(--renk-tehlike);
  font-weight: 600;
  font-size: 0.9rem;
}
.uyari-notu {
  color: #8a5a00;
  font-size: 0.9rem;
}
.btn-kucuk {
  padding: 0.1rem 0.5rem;
  font-size: 0.85rem;
}
</style>
