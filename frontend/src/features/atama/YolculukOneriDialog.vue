<script setup lang="ts">
import AlanHatasi from '@/bilesenler/AlanHatasi.vue'
import { computed, onMounted, ref } from 'vue'
import { ApiHata } from '@/api/istemci'
import type { Ihlal } from '@/api/tipler'
import { saatBicimle } from '@/lib/tarih'
import Diyalog from '@/bilesenler/Diyalog.vue'
import VeriDurumu from '@/bilesenler/VeriDurumu.vue'
import Dugme from '@/bilesenler/Dugme.vue'
import Simge from '@/bilesenler/Simge.vue'
import NobetNotu from './NobetNotu.vue'
import {
  atamaApi,
  NEDEN_ETIKETLERI,
  type Aday,
  type AtamaNedeni,
  type GunMakinisti,
  type IntikalOnerisi,
  type IntikalleGovde,
  type YolculukOnerisi,
} from './atamaApi'

/**
 * "Yolculuk öner" (F2b): konumu seferin kalkış garına uymayan (K-20) aday için, seferden ÖNCE o gara varan en çok 3 yolculuk. Seçilince "Yolculukla ata" tek işlemde
 * yolcu + asıl yazar (herhangi bir ayak reddedilirse hiçbiri yazılmaz). UYARI varsa gerekçe, sefer doluysa değiştirme nedeni zorunludur.
 * İ-2 (MK-110): gar süre tablosu tanımlıysa listenin sonunda TEK "servis ile intikal" satırı da gelir; "İntikalle ata" tek işlemde serbest intikal + asıl yazar (saatler sunucuda hesaplanır).
 */
const props = defineProps<{
  seferId: number
  seferEtiketi: string
  aday: Aday
  /** Seferde şu an ASIL olan (varsa "değiştir": neden zorunlu). */
  mevcutAsil?: GunMakinisti | null
}>()
const emit = defineEmits<{ kapat: [yenile: boolean]; tamam: [mesaj: string] }>()

const oneriler = ref<YolculukOnerisi[]>([])
const yukleniyor = ref(true)
const yuklemeHatasi = ref('')
const secili = ref<number | 'intikal' | null>(null)
const intikalOneri = ref<IntikalOnerisi | null>(null)
const aracTuru = ref<IntikalleGovde['aracTuru']>('SERVIS')
const intikalNotu = ref('')
const gerekce = ref('')
const neden = ref<AtamaNedeni | ''>('')
const nedenNotu = ref('')
const mesgul = ref(false)
const genelHata = ref('')
const ihlaller = ref<Ihlal[]>([])
const alanHatalari = ref<Record<string, string>>({})
let hataOldu = false

const seciliOneri = computed(() => oneriler.value.find((o) => o.seferId === secili.value) ?? null)
const intikalSecili = computed(() => secili.value === 'intikal' && !!intikalOneri.value)
const uyarilar = computed(() =>
  intikalSecili.value ? (intikalOneri.value?.uyarilar ?? []) : (seciliOneri.value?.uyarilar ?? []),
)
const nedenGerekli = computed(() => !!props.mevcutAsil)
const gerekceEksik = computed(() => uyarilar.value.length > 0 && gerekce.value.trim() === '')
const nedenEksik = computed(() => nedenGerekli.value && neden.value === '')
const notEksik = computed(
  () => nedenGerekli.value && neden.value === 'DIGER' && nedenNotu.value.trim() === '',
)
const gecerli = computed(
  () =>
    (!!seciliOneri.value || intikalSecili.value) &&
    !gerekceEksik.value &&
    !nedenEksik.value &&
    !notEksik.value,
)
const nobetMetinleri = computed(() => [
  ...oneriler.value.flatMap((o) => o.uyarilar.map((u) => u.metin)),
  ...(intikalOneri.value?.uyarilar ?? []).map((u) => u.metin),
  ...ihlaller.value.map((i) => i.metin),
  genelHata.value,
])

const saatler = (o: YolculukOnerisi) => `${saatBicimle(o.kalkis)}–${saatBicimle(o.varis)}`

// Birincil eylem kapalıyken nedeni (Diyalog `neden` slotu)
const eylemNedeni = computed(() => {
  if (gecerli.value) return ''
  if (!seciliOneri.value && !intikalSecili.value) return 'Önce bir yolculuk seçin.'
  if (gerekceEksik.value) return 'Uyarılar için gerekçe yazın.'
  if (nedenEksik.value) return 'Değiştirme nedenini seçin.'
  return 'Neden notunu yazın.'
})
// sunucu/kural hatası ya da yükleme hatası: tek bantta
const hataMetni = computed(() => genelHata.value || yuklemeHatasi.value)

onMounted(async () => {
  try {
    const sonuc = await atamaApi.yolculukOner(props.seferId, props.aday.makinistId)
    oneriler.value = sonuc.oneriler
    intikalOneri.value = sonuc.intikal ?? null
  } catch (e) {
    yuklemeHatasi.value = e instanceof ApiHata ? e.detail : 'Öneriler yüklenemedi.'
  } finally {
    yukleniyor.value = false
  }
})

function vazgec() {
  if (!mesgul.value) emit('kapat', hataOldu)
}

async function onayla() {
  if (mesgul.value || !gecerli.value || (!seciliOneri.value && !intikalSecili.value)) return
  mesgul.value = true
  genelHata.value = ''
  ihlaller.value = []
  alanHatalari.value = {}
  const nedenAlanlari = nedenGerekli.value
    ? { neden: neden.value as AtamaNedeni, nedenNotu: nedenNotu.value.trim() || undefined }
    : {}
  try {
    if (intikalSecili.value) {
      await atamaApi.intikalle(props.seferId, {
        makinistId: props.aday.makinistId,
        aracTuru: aracTuru.value,
        notu: intikalNotu.value.trim() || undefined,
        gerekce: gerekce.value.trim() || undefined,
        ...nedenAlanlari,
      })
      emit('tamam', 'İntikal ve atama yapıldı.')
      return
    }
    await atamaApi.yolculukla(props.seferId, {
      makinistId: props.aday.makinistId,
      yolculukSeferId: seciliOneri.value!.seferId,
      gerekce: gerekce.value.trim() || undefined,
      ...nedenAlanlari,
    })
    emit('tamam', 'Yolculuk ve atama yapıldı.')
  } catch (e) {
    hataOldu = true
    if (e instanceof ApiHata && e.kod === 'KURAL_IHLALI') {
      // hangi ayağın (yolculuk / asıl) reddedildiği ihlal metinlerindeki sefer saatinden okunur
      ihlaller.value = (e.ihlaller ?? []).filter((i) => !e.detail.includes(i.metin))
      genelHata.value = e.detail
    } else if (e instanceof ApiHata && e.kod === 'DOGRULAMA' && e.alanlar) {
      alanHatalari.value = e.alanlar
    } else {
      genelHata.value = e instanceof ApiHata ? e.detail : 'İşlem başarısız oldu.'
    }
  } finally {
    mesgul.value = false
  }
}
</script>

<template>
  <Diyalog baslik="Yolculuk öner" boyut="genis" :form="true" @kapat="vazgec" @gonder="onayla">
    <p>
      {{ seferEtiketi }} · <strong>{{ aday.adSoyad }} ({{ aday.sicil }})</strong>
    </p>
    <p class="soluk">
      Makinistin bu seferin kalkış garına yolcu olarak gidebileceği seferler. Seçince önce yolculuk,
      sonra bu sefer atanır; biri reddedilirse hiçbiri yazılmaz.
    </p>

    <VeriDurumu v-if="yukleniyor" durum="yukleniyor" metin="Öneriler yükleniyor…" />
    <div role="status">
      <p
        v-if="!yukleniyor && !yuklemeHatasi && oneriler.length === 0 && !intikalOneri"
        class="bildirim bildirim-uyari"
      >
        <span><Simge ad="daire" boyut="1em" /> Uygun yolculuk bulunamadı.</span>
      </p>
    </div>

    <fieldset v-if="oneriler.length > 0 || intikalOneri" class="oneri-listesi">
      <legend>Yolculuk seferi</legend>
      <label v-for="o in oneriler" :key="o.seferId" class="oneri">
        <input v-model="secili" type="radio" name="yolculuk-sefer" :value="o.seferId" />
        <span>
          <strong>{{ o.trenNo }}</strong> {{ o.kalkisGar }} → {{ o.varisGar }} {{ saatler(o) }}
          <small class="alt-bilgi">{{ o.gerekce }}</small>
          <ul v-if="o.uyarilar.length > 0" class="ihlal-listesi">
            <li v-for="(u, i) in o.uyarilar" :key="i">
              <Simge ad="uyari" boyut="1em" /> {{ u.metin }}
            </li>
          </ul>
        </span>
      </label>
      <label v-if="intikalOneri" class="oneri" data-test="intikal-oneri">
        <input v-model="secili" type="radio" name="yolculuk-sefer" value="intikal" />
        <span>
          <strong>Servis ile intikal ({{ intikalOneri.sureDk }} dk)</strong>
          {{ intikalOneri.kalkisGar }} {{ saatBicimle(intikalOneri.kalkis) }} →
          {{ intikalOneri.varisGar }}
          {{ saatBicimle(intikalOneri.varis) }}
          <small class="alt-bilgi">{{ intikalOneri.gerekce }}</small>
          <ul v-if="intikalOneri.uyarilar.length > 0" class="ihlal-listesi">
            <li v-for="(u, i) in intikalOneri.uyarilar" :key="i">
              <Simge ad="uyari" boyut="1em" /> {{ u.metin }}
            </li>
          </ul>
        </span>
      </label>
    </fieldset>

    <div v-if="intikalSecili" class="satir-iki">
      <div class="alan">
        <label for="yolculuk-arac">Araç</label>
        <select id="yolculuk-arac" v-model="aracTuru">
          <option value="SERVIS">Servis</option>
          <option value="OTOBUS">Otobüs</option>
          <option value="TAKSI">Taksi</option>
          <option value="DIGER">Diğer</option>
        </select>
      </div>
      <div class="alan">
        <label for="yolculuk-intikal-not">Not (isteğe bağlı)</label>
        <input id="yolculuk-intikal-not" v-model="intikalNotu" type="text" maxlength="200" />
      </div>
    </div>

    <template v-if="(seciliOneri || intikalSecili) && uyarilar.length > 0">
      <div class="alan">
        <label for="yolculuk-gerekce">Gerekçe (zorunlu)</label>
        <textarea
          id="yolculuk-gerekce"
          v-model="gerekce"
          required
          :aria-invalid="!!alanHatalari.gerekce"
          :aria-describedby="alanHatalari.gerekce ? 'yolculuk-gerekce-hata' : undefined"
        ></textarea>
        <AlanHatasi id="yolculuk-gerekce-hata" :metin="alanHatalari.gerekce" />
      </div>
    </template>

    <template v-if="(seciliOneri || intikalSecili) && nedenGerekli">
      <p>
        Görevden çıkacak: <strong>{{ mevcutAsil!.ad }} ({{ mevcutAsil!.sicil }})</strong>
      </p>
      <div class="alan">
        <label for="yolculuk-neden">Değiştirme nedeni</label>
        <select
          id="yolculuk-neden"
          v-model="neden"
          required
          :aria-invalid="!!alanHatalari.neden"
          :aria-describedby="alanHatalari.neden ? 'yolculuk-neden-hata' : undefined"
        >
          <option value="">Seçin…</option>
          <option v-for="(etiket, k) in NEDEN_ETIKETLERI" :key="k" :value="k">
            {{ etiket }}
          </option>
        </select>
        <AlanHatasi id="yolculuk-neden-hata" :metin="alanHatalari.neden" />
      </div>
      <div v-if="neden === 'DIGER'" class="alan">
        <label for="yolculuk-neden-notu">Neden notu (zorunlu)</label>
        <textarea
          id="yolculuk-neden-notu"
          v-model="nedenNotu"
          required
          :aria-invalid="!!alanHatalari.nedenNotu"
          :aria-describedby="alanHatalari.nedenNotu ? 'yolculuk-neden-notu-hata' : undefined"
        ></textarea>
        <AlanHatasi id="yolculuk-neden-notu-hata" :metin="alanHatalari.nedenNotu" />
      </div>
    </template>

    <div role="alert">
      <div v-if="hataMetni || ihlaller.length > 0" class="bildirim bildirim-hata">
        <p v-if="hataMetni">{{ hataMetni }}</p>
        <ul v-if="ihlaller.length > 0" class="ihlal-listesi">
          <li v-for="(ih, i) in ihlaller" :key="i">
            <Simge :ad="ih.siddet === 'ENGEL' ? 'engel' : 'uyari'" boyut="1em" /> {{ ih.metin }}
          </li>
        </ul>
      </div>
    </div>
    <NobetNotu :metinler="nobetMetinleri" />

    <template #eylemler>
      <Dugme varyant="birincil" tip="submit" :disabled="!gecerli || mesgul">
        {{ intikalSecili ? 'İntikalle ata' : 'Yolculukla ata' }}
      </Dugme>
      <Dugme :disabled="mesgul" @click="vazgec">Vazgeç</Dugme>
    </template>
    <template v-if="eylemNedeni" #neden>{{ eylemNedeni }}</template>
  </Diyalog>
</template>

<style scoped>
.satir-iki {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(12rem, 1fr));
  gap: var(--aralik-4);
}
.oneri-listesi {
  border: 0;
  padding: 0;
  margin: var(--aralik-3) 0;
}
.oneri {
  display: flex;
  gap: var(--aralik-3);
  align-items: flex-start;
  padding: var(--aralik-2) 0;
}
.alt-bilgi {
  display: block;
  color: var(--renk-metin-soluk);
}
.ihlal-listesi {
  margin: var(--aralik-2) 0 0;
  padding-left: 0;
  list-style: none;
}
</style>
