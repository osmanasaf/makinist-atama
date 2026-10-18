<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ApiHata } from '@/api/istemci'
import type { Tarih } from '@/api/tipler'
import { useYoklama } from '@/features/benim/useYoklama'
import Dugme from '@/bilesenler/Dugme.vue'
import Rozet from '@/bilesenler/Rozet.vue'
import Simge from '@/bilesenler/Simge.vue'
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
      <Dugme boyut="sm" simge="ekle" data-test="intikal-ekle" @click="diyalog = { kip: 'ekle' }">
        İntikal ekle
      </Dugme>
      <p
        v-if="!hata && yuklendi && liste.length === 0"
        class="soluk intikal-bos"
        data-test="intikal-bos"
      >
        {{ tarihBicimle(tarih) }} için intikal yok.
      </p>
    </div>
    <div role="status">
      <p v-if="bilgi" class="bildirim bildirim-bilgi" data-test="intikal-bilgi">{{ bilgi }}</p>
    </div>
    <p v-if="hata" class="bildirim bildirim-hata" role="alert" data-test="intikal-hata">
      {{ hata }}
    </p>
    <ul v-else-if="liste.length > 0" class="intikal-listesi">
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
          <Rozet
            v-if="i.durum === 'IPTAL'"
            ton="engel"
            simge="iptal"
            metin="İPTAL"
            boyut="sm"
            data-test="intikal-iptal"
          />
          <Rozet
            v-if="i.kirmizi"
            ton="engel"
            simge="engel"
            metin="KONTROL"
            boyut="sm"
            data-test="intikal-kirmizi"
          />
          <span v-if="i.gordu === 'GORULDU'" class="gordum goruldu" data-test="intikal-gordu">
            <Simge ad="basari" boyut="1em" />gördü {{ saatBicimle(i.gorduZamani) }}
          </span>
          <span
            v-else-if="i.gordu === 'DEGISTI'"
            class="gordum gormedi"
            data-test="intikal-degisti"
          >
            <Simge ad="takas" boyut="1em" />değişti, yeniden onay bekliyor
          </span>
          <span v-else class="gordum gormedi" data-test="intikal-gormedi">
            <Simge ad="daire" boyut="1em" />görmedi
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
          <Simge ad="engel" boyut="1em" />{{ i.nedenler.join('; ') }}
        </div>
        <div v-if="i.uyarilar.length > 0" class="uyari-notu">
          <Simge ad="uyari" boyut="1em" />Uyarı: {{ i.uyarilar.join('; ') }}
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
  margin: 0 0 var(--aralik-3);
}
.serit-baslik {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--aralik-2) var(--aralik-4);
}
.serit-baslik h2 {
  margin: 0;
  font-size: var(--yazi-lg);
}
.intikal-bos {
  margin: 0;
  font-size: var(--yazi-sm);
}
.intikal-listesi {
  list-style: none;
  margin: var(--aralik-2) 0 0;
  padding: 0;
  max-height: 9rem;
  overflow: auto;
  background: var(--renk-yuzey);
  border: 1px solid var(--renk-cizgi);
  border-radius: var(--yaricap);
}
.intikal {
  padding: var(--aralik-2) var(--aralik-3);
  border-bottom: 1px solid var(--renk-cizgi);
  border-left: 4px solid transparent;
  font-size: var(--yazi-sm);
}
.intikal:last-child {
  border-bottom: 0;
}
.intikal.kirmizi {
  background: var(--renk-engel-zemin);
  border-left-color: var(--renk-engel-dolu);
}
.intikal.iptal .metin {
  text-decoration: line-through;
  color: var(--renk-metin-soluk);
}
.ana {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--aralik-2) var(--aralik-4);
}
.gordum {
  display: inline-flex;
  align-items: center;
  gap: var(--aralik-1);
  font-size: var(--yazi-xs);
  white-space: nowrap;
}
.gordum.goruldu {
  color: var(--renk-basari-metin);
}
.gordum.gormedi {
  color: var(--renk-metin-soluk);
}
.islem {
  margin-left: auto;
  display: inline-flex;
  gap: var(--aralik-2);
}
.kirmizi-neden,
.uyari-notu {
  font-size: var(--yazi-xs);
}
.kirmizi-neden {
  color: var(--renk-engel-metin);
  font-weight: var(--yazi-agirlik-orta);
}
.uyari-notu {
  color: var(--renk-uyari-metin);
}
</style>
