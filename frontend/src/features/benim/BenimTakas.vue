<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ApiHata } from '@/api/istemci'
import type { Ton } from '@/bilesenler/durum'
import type { SimgeAdi } from '@/bilesenler/simgeler'
import Rozet from '@/bilesenler/Rozet.vue'
import SayfaBasligi from '@/bilesenler/SayfaBasligi.vue'
import VeriDurumu from '@/bilesenler/VeriDurumu.vue'
import { NEDEN_ETIKETLERI } from '@/features/atama/atamaApi'
import { TAKAS_DURUM_ETIKETLERI, TAKAS_TUR_ETIKETLERI } from '@/features/takas/takasTipleri'
import { zamanBicimle } from '@/lib/tarih'
import { benimApi, type BenimTakas } from './benimApi'
import { rozetYaz } from './rozet'
import { useYoklama } from './useYoklama'

/** Takas / bırakma taleplerim: gelen talepte kabul / ret, kendi talebimde geri çekme. Durum metin + simgeyle; karşı tarafın yalnız sicili ve görev özeti görünür. */
const talepler = ref<BenimTakas[]>([])
const yukleniyor = ref(true)
const hata = ref('')
const islemHatasi = ref('')
const mesgul = ref(new Set<number>())

const bekleyen = computed(() => talepler.value.filter((t) => t.yanitBekliyor).length)

function rozetGuncelle() {
  rozetYaz(
    bekleyen.value > 0
      ? `${bekleyen.value} takas talebi yanıtınızı bekliyor`
      : `${talepler.value.length} takas talebi listelendi`,
  )
}

async function yukle(sessiz = false) {
  if (!sessiz) {
    yukleniyor.value = true
    hata.value = ''
  }
  try {
    talepler.value = await benimApi.takaslar()
    rozetGuncelle()
  } catch (e) {
    if (!sessiz) hata.value = e instanceof ApiHata ? e.detail : 'Talepler yüklenemedi.'
  } finally {
    if (!sessiz) yukleniyor.value = false
  }
}
onMounted(() => yukle())
useYoklama(() => yukle(true))

async function islem(t: BenimTakas, is: (id: number) => Promise<unknown>) {
  if (mesgul.value.has(t.id)) return
  mesgul.value.add(t.id)
  islemHatasi.value = ''
  try {
    await is(t.id)
  } catch (e) {
    islemHatasi.value = e instanceof ApiHata ? e.detail : 'İşlem tamamlanamadı.'
  } finally {
    mesgul.value.delete(t.id)
    await yukle(true) // sunucudaki güncel durum (talep bu arada bayatlamış ya da kapanmış olabilir)
  }
}

const kabul = (t: BenimTakas) => islem(t, (id) => benimApi.takasKabul(id))
const red = (t: BenimTakas) => islem(t, (id) => benimApi.takasRed(id))
const geriCek = (t: BenimTakas) => islem(t, (id) => benimApi.takasIptal(id))

/** Durum etiketi okuyan kişiye göre (UX-1: gelen talepte yanıtı bekleyen benim; UX-4: reddi kimin verdiği). */
function etiket(t: BenimTakas): { simge: SimgeAdi; ad: string } {
  const e = TAKAS_DURUM_ETIKETLERI[t.durum]
  if (t.gelen && t.durum === 'KARSI_ONAY_BEKLIYOR') return { ...e, ad: 'Yanıtınız bekleniyor' }
  if (t.durum === 'REDDEDILDI' && t.reddeden)
    return { ...e, ad: e.ad + (t.reddeden === 'ATAMACI' ? ' (atamacı)' : ' (karşı makinist)') }
  return e
}

/** Rozet tonu: yanıt bekleyen benim → uyarı; onay → başarı; ret → engel; geri kalanı nötr (simge + metin zaten anlamı taşır). */
function tonu(t: BenimTakas): Ton {
  if (t.yanitBekliyor) return 'uyari'
  if (t.durum === 'ONAYLANDI') return 'basari'
  if (t.durum === 'REDDEDILDI') return 'engel'
  return 'notr'
}

/** Karşı yanıt ya da atamacı kararı beklenen (kapanmamış) talep: geri çekilebilir. */
const acikMi = (t: BenimTakas) =>
  t.durum === 'KARSI_ONAY_BEKLIYOR' || t.durum === 'ATAMACI_ONAYI_BEKLIYOR'
</script>

<template>
  <section>
    <SayfaBasligi baslik="Takas taleplerim" />
    <p class="soluk">
      Talepler önce karşı personelin, sonra atamacının onayından geçer; onaylanmadıkça görevleriniz
      yerinde kalır. Panodaki liste asıl tebliğ yoludur.
    </p>
    <VeriDurumu v-if="yukleniyor" durum="yukleniyor" metin="Yükleniyor…" />
    <VeriDurumu v-else-if="hata" durum="hata" :metin="hata">
      <template #eylem>
        <button type="button" class="btn" @click="yukle()">Yeniden dene</button>
      </template>
    </VeriDurumu>
    <template v-else>
      <p v-if="islemHatasi" class="bildirim bildirim-hata" role="alert">{{ islemHatasi }}</p>
      <VeriDurumu
        v-if="talepler.length === 0"
        durum="bos"
        metin="Takas ya da bırakma talebiniz yok."
      />
      <article
        v-for="t in talepler"
        :key="t.id"
        class="kart benim-kart"
        :class="{ 'yanit-bekliyor': t.yanitBekliyor }"
        :data-test="'takas-' + t.id"
      >
        <p class="benim-satir">
          <Rozet :ton="tonu(t)" :simge="etiket(t).simge" :metin="etiket(t).ad" />
        </p>
        <p>
          <strong>{{ TAKAS_TUR_ETIKETLERI[t.tur] }}</strong>
          <template v-if="t.karsiSicil">
            · {{ t.gelen ? 'Size gelen talep' : 'Sizin talebiniz' }} · {{ t.karsiSicil }}
          </template>
        </p>
        <p class="soluk">{{ t.gelen ? 'Verirsiniz' : 'Verdiğiniz' }}: {{ t.benimGorev }}</p>
        <p v-if="t.karsiGorev" class="soluk">
          {{ t.gelen ? 'Alırsınız' : 'Almak istediğiniz' }}: {{ t.karsiGorev }}
        </p>
        <p v-if="t.neden" class="soluk">
          Neden: {{ NEDEN_ETIKETLERI[t.neden]
          }}<template v-if="t.nedenNotu"> · {{ t.nedenNotu }}</template>
        </p>
        <p v-if="t.sonKabul && t.durum === 'KARSI_ONAY_BEKLIYOR'" class="soluk">
          Son yanıt zamanı: {{ zamanBicimle(t.sonKabul) }}
        </p>
        <div v-if="t.yanitBekliyor" class="benim-takas-dugmeleri">
          <button
            type="button"
            class="btn btn-birincil"
            :disabled="mesgul.has(t.id)"
            :aria-label="`${t.karsiSicil} takas talebini kabul et`"
            @click="kabul(t)"
          >
            Kabul et
          </button>
          <button
            type="button"
            class="btn"
            :disabled="mesgul.has(t.id)"
            :aria-label="`${t.karsiSicil} takas talebini reddet`"
            @click="red(t)"
          >
            Reddet
          </button>
        </div>
        <button
          v-else-if="!t.gelen && acikMi(t)"
          type="button"
          class="btn"
          :disabled="mesgul.has(t.id)"
          :aria-label="`${TAKAS_TUR_ETIKETLERI[t.tur]} talebini geri çek`"
          @click="geriCek(t)"
        >
          Geri çek
        </button>
      </article>
    </template>
  </section>
</template>
