<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ApiHata } from '@/api/istemci'
import type { Ihlal, Tarih } from '@/api/tipler'
import { NEDEN_ETIKETLERI, type AtamaNedeni } from '@/features/atama/atamaApi'
import NobetNotu from '@/features/atama/NobetNotu.vue'
import { makinistApi, type MakinistOzet } from '@/features/makinist/makinistApi'
import { tanimApi, type TanimDto } from '@/features/tanim/tanimApi'
import {
  ARAC_ETIKETLERI,
  intikalApi,
  type AracTuru,
  type Intikal,
  type IntikalGovde,
  type IntikalSonucu,
} from './intikalApi'

/**
 * Serbest intikal ekle / düzelt / iptal (İ-1, MK-106). Kural kontrolü sunucudadır ve iki yönlüdür: ENGEL (422) diyalog açıkken ihlal listesiyle gösterilir, kayıt yazılmaz; kural UYARISI
 * varsa gerekçe zorunludur (sunucu 400 `gerekce` der, alan formda görünür). İptalde silme yok, neden zorunludur. Saatler İstanbul saatiyle girilir (Türkiye UTC+3 sabit).
 */
const props = defineProps<{
  kip: 'ekle' | 'duzelt' | 'iptal'
  /** Yeni kaydın varsayılan günü. */
  gun: Tarih
  /** Düzelt / iptal edilen kayıt. */
  intikal?: Intikal
}>()
const emit = defineEmits<{
  /** `yenile`: hata aldık, sunucu durumu bizim bildiğimizden farklı olabilir; çağıran yeniden okumalı. */
  kapat: [yenile: boolean]
  tamam: [mesaj: string]
}>()

const yerelden = (s: string) => (s.length === 16 ? `${s}:00+03:00` : s)
const yerele = (z: string) => z.slice(0, 16)

const duzelt = props.kip === 'duzelt' && props.intikal
const form = reactive({
  makinistId: duzelt ? props.intikal!.makinistId : 0,
  kalkisGarId: duzelt ? props.intikal!.kalkisGarId : 0,
  varisGarId: duzelt ? props.intikal!.varisGarId : 0,
  kalkis: duzelt ? yerele(props.intikal!.kalkis) : `${props.gun}T07:00`,
  varis: duzelt ? yerele(props.intikal!.varis) : `${props.gun}T08:00`,
  aracTuru: (duzelt ? props.intikal!.aracTuru : 'SERVIS') as AracTuru,
  notu: duzelt ? (props.intikal!.notu ?? '') : '',
  gerekce: '',
})
const neden = ref<AtamaNedeni | ''>('')
const nedenNotu = ref('')

const makinistler = ref<MakinistOzet[]>([])
const garlar = ref<TanimDto[]>([])
const mesgul = ref(false)
const yuklemeHatasi = ref('')
const genelHata = ref('')
const ihlaller = ref<Ihlal[]>([])
const alanHatalari = ref<Record<string, string>>({})
let hataOldu = false

const baslik = computed(
  () =>
    ({ ekle: 'İntikal ekle', duzelt: 'İntikali düzelt', iptal: 'İntikali iptal et' })[props.kip],
)
const nobetMetinleri = computed(() => [...ihlaller.value.map((i) => i.metin), genelHata.value])

const dialog = ref<HTMLDialogElement | null>(null)
onMounted(async () => {
  dialog.value?.showModal()
  if (props.kip === 'iptal') return
  try {
    ;[makinistler.value, garlar.value] = await Promise.all([
      makinistApi.liste('', 'AKTIF'),
      tanimApi.liste('GAR'),
    ])
  } catch (e) {
    yuklemeHatasi.value = e instanceof ApiHata ? e.detail : 'Personel ve gar listesi yüklenemedi.'
  }
})

function vazgec() {
  if (!mesgul.value) emit('kapat', hataOldu)
}

/** İptal / düzeltme başka atamayı yeni kırmızı yaptıysa atamacıya söylenir (kural gereği; hata değil). */
function etkiMesaji(temel: string, sonuc: IntikalSonucu): string {
  const yeni = sonuc.yeniKirmizi ?? []
  if (yeni.length === 0) return temel
  const ozet = yeni
    .slice(0, 3)
    .map((y) => `${y.trenNo} (${y.nedenler[0] ?? 'kural ihlali'})`)
    .join('; ')
  return `${temel} Dikkat: bu makinistin ${yeni.length} başka görevi kırmızı oldu: ${ozet}${yeni.length > 3 ? ' …' : ''}`
}

function dogrula(): Record<string, string> {
  const h: Record<string, string> = {}
  if (!form.makinistId) h.makinistId = 'Personel seçin.'
  if (!form.kalkisGarId) h.kalkisGarId = 'Kalkış garını seçin.'
  if (!form.varisGarId) h.varisGarId = 'Varış garını seçin.'
  else if (form.varisGarId === form.kalkisGarId)
    h.varisGarId = 'Varış garı kalkıştan farklı olmalı.'
  if (!form.kalkis) h.kalkis = 'Kalkış zamanı gerekli.'
  if (!form.varis) h.varis = 'Varış zamanı gerekli.'
  else if (form.kalkis && form.varis <= form.kalkis) h.varis = 'Varış, kalkıştan sonra olmalı.'
  return h
}

async function onayla() {
  if (mesgul.value) return
  genelHata.value = ''
  ihlaller.value = []
  if (props.kip === 'iptal') {
    if (neden.value === '' || (neden.value === 'DIGER' && nedenNotu.value.trim() === '')) {
      alanHatalari.value = {
        [neden.value === '' ? 'neden' : 'nedenNotu']:
          neden.value === '' ? 'Neden seçin.' : 'Diğer nedeni için açıklama yazın.',
      }
      return
    }
  } else {
    alanHatalari.value = dogrula()
    if (Object.keys(alanHatalari.value).length > 0) return
  }
  alanHatalari.value = {}
  mesgul.value = true
  try {
    if (props.kip === 'iptal') {
      const sonuc = await intikalApi.iptal(props.intikal!.id, {
        neden: neden.value as AtamaNedeni,
        nedenNotu: nedenNotu.value.trim() || undefined,
        versiyon: props.intikal!.versiyon,
      })
      emit('tamam', etkiMesaji('İntikal iptal edildi.', sonuc))
      return
    }
    const govde: IntikalGovde = {
      makinistId: form.makinistId,
      kalkisGarId: form.kalkisGarId,
      varisGarId: form.varisGarId,
      kalkis: yerelden(form.kalkis),
      varis: yerelden(form.varis),
      aracTuru: form.aracTuru,
      notu: form.notu.trim() || undefined,
      gerekce: form.gerekce.trim() || undefined,
    }
    if (props.kip === 'duzelt') {
      const sonuc = await intikalApi.guncelle(props.intikal!.id, {
        ...govde,
        versiyon: props.intikal!.versiyon,
      })
      emit('tamam', etkiMesaji('İntikal güncellendi.', sonuc))
    } else {
      await intikalApi.ekle(govde)
      emit('tamam', 'İntikal eklendi.')
    }
  } catch (e) {
    hataOldu = true
    if (e instanceof ApiHata && e.kod === 'KURAL_IHLALI') {
      // detail başlıktır; metni içinde geçen ihlali listede ikinci kez basma.
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
  <dialog
    ref="dialog"
    class="intikal-dialog"
    aria-labelledby="intikal-dlg-baslik"
    @cancel.prevent="vazgec"
  >
    <form @submit.prevent="onayla">
      <h2 id="intikal-dlg-baslik">{{ baslik }}</h2>

      <template v-if="kip === 'iptal' && intikal">
        <p>
          <strong>{{ intikal.adSoyad }} ({{ intikal.sicil }})</strong>: {{ intikal.kalkisGar }} →
          {{ intikal.varisGar }}
        </p>
        <p class="soluk">
          İntikal silinmez, "iptal" olarak kalır ve kurallara girmez. İptal sonrası komşu görevler
          kırmızı olabilir; sonuç ekranda bildirilir.
        </p>
        <div class="alan">
          <label for="intikal-neden">İptal nedeni</label>
          <select id="intikal-neden" v-model="neden" required :aria-invalid="!!alanHatalari.neden">
            <option value="">Seçin…</option>
            <option v-for="(etiket, k) in NEDEN_ETIKETLERI" :key="k" :value="k">
              {{ etiket }}
            </option>
          </select>
          <p v-if="alanHatalari.neden" class="alan-hata">{{ alanHatalari.neden }}</p>
        </div>
        <div v-if="neden === 'DIGER'" class="alan">
          <label for="intikal-neden-notu">Neden notu (zorunlu)</label>
          <textarea id="intikal-neden-notu" v-model="nedenNotu" required></textarea>
          <p v-if="alanHatalari.nedenNotu" class="alan-hata">{{ alanHatalari.nedenNotu }}</p>
        </div>
      </template>

      <template v-else>
        <p class="soluk">
          Servis, otobüs ya da taksi ile gar değiştirme. Kural motoru bunu yolculuk gibi görür:
          konum ve dinlenme kuralları işler, çalışma süresine
          <code>yolculuk.calisma_sayilir</code> ayarı kadar girer.
        </p>
        <p v-if="yuklemeHatasi" class="bildirim bildirim-hata" role="alert">{{ yuklemeHatasi }}</p>
        <div class="alan">
          <label for="intikal-makinist">Personel</label>
          <select
            id="intikal-makinist"
            v-model="form.makinistId"
            :disabled="kip === 'duzelt'"
            required
            :aria-invalid="!!alanHatalari.makinistId"
          >
            <option :value="0" disabled>Seçin…</option>
            <option v-for="m in makinistler" :key="m.id" :value="m.id">
              {{ m.sicil }} · {{ m.adSoyad }}
            </option>
            <option
              v-if="
                kip === 'duzelt' &&
                intikal &&
                !makinistler.some((m) => m.id === intikal!.makinistId)
              "
              :value="intikal.makinistId"
            >
              {{ intikal.sicil }} · {{ intikal.adSoyad }}
            </option>
          </select>
          <p v-if="alanHatalari.makinistId" class="alan-hata">{{ alanHatalari.makinistId }}</p>
        </div>
        <div class="satir-iki">
          <div class="alan">
            <label for="intikal-kalkis-gar">Kalkış garı</label>
            <select
              id="intikal-kalkis-gar"
              v-model="form.kalkisGarId"
              required
              :aria-invalid="!!alanHatalari.kalkisGarId"
            >
              <option :value="0" disabled>Seçin…</option>
              <option v-for="g in garlar" :key="g.id" :value="g.id">{{ g.ad }}</option>
            </select>
            <p v-if="alanHatalari.kalkisGarId" class="alan-hata">{{ alanHatalari.kalkisGarId }}</p>
          </div>
          <div class="alan">
            <label for="intikal-varis-gar">Varış garı</label>
            <select
              id="intikal-varis-gar"
              v-model="form.varisGarId"
              required
              :aria-invalid="!!alanHatalari.varisGarId"
            >
              <option :value="0" disabled>Seçin…</option>
              <option v-for="g in garlar" :key="g.id" :value="g.id">{{ g.ad }}</option>
            </select>
            <p v-if="alanHatalari.varisGarId" class="alan-hata">{{ alanHatalari.varisGarId }}</p>
          </div>
        </div>
        <div class="satir-iki">
          <div class="alan">
            <label for="intikal-kalkis">Kalkış</label>
            <input
              id="intikal-kalkis"
              v-model="form.kalkis"
              type="datetime-local"
              required
              :aria-invalid="!!alanHatalari.kalkis"
            />
            <p v-if="alanHatalari.kalkis" class="alan-hata">{{ alanHatalari.kalkis }}</p>
          </div>
          <div class="alan">
            <label for="intikal-varis">Varış</label>
            <input
              id="intikal-varis"
              v-model="form.varis"
              type="datetime-local"
              required
              :aria-invalid="!!alanHatalari.varis"
            />
            <p v-if="alanHatalari.varis" class="alan-hata">{{ alanHatalari.varis }}</p>
          </div>
        </div>
        <div class="satir-iki">
          <div class="alan">
            <label for="intikal-arac">Araç</label>
            <select id="intikal-arac" v-model="form.aracTuru">
              <option v-for="(etiket, k) in ARAC_ETIKETLERI" :key="k" :value="k">
                {{ etiket }}
              </option>
            </select>
          </div>
          <div class="alan">
            <label for="intikal-not">Not (isteğe bağlı; makinistte görünmez)</label>
            <input id="intikal-not" v-model="form.notu" type="text" maxlength="200" />
            <p v-if="alanHatalari.notu" class="alan-hata">{{ alanHatalari.notu }}</p>
          </div>
        </div>
        <div class="alan">
          <label for="intikal-gerekce">Gerekçe (kural uyarısı çıkarsa zorunlu)</label>
          <textarea
            id="intikal-gerekce"
            v-model="form.gerekce"
            :aria-invalid="!!alanHatalari.gerekce"
          ></textarea>
          <p v-if="alanHatalari.gerekce" class="alan-hata">{{ alanHatalari.gerekce }}</p>
        </div>
      </template>

      <div role="alert">
        <p v-if="genelHata" class="hata-mesaji">{{ genelHata }}</p>
        <ul v-if="ihlaller.length > 0" class="ihlal-listesi hata-mesaji">
          <li v-for="(ih, i) in ihlaller" :key="i">
            <span aria-hidden="true">{{ ih.siddet === 'ENGEL' ? '⛔' : '⚠' }}</span> {{ ih.metin }}
          </li>
        </ul>
      </div>
      <NobetNotu :metinler="nobetMetinleri" />

      <div class="eylemler">
        <button
          type="submit"
          class="btn"
          :class="kip === 'iptal' ? 'btn-tehlike' : 'btn-birincil'"
          :disabled="mesgul"
        >
          {{ kip === 'iptal' ? 'İntikali iptal et' : kip === 'duzelt' ? 'Güncelle' : 'Kaydet' }}
        </button>
        <button type="button" class="btn" :disabled="mesgul" @click="vazgec">Vazgeç</button>
      </div>
    </form>
  </dialog>
</template>

<style scoped>
.intikal-dialog {
  min-width: min(560px, 94vw);
}
.satir-iki {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(13rem, 1fr));
  gap: 0.75rem;
}
.ihlal-listesi {
  margin: 0.25rem 0 0;
  padding-left: 0;
  list-style: none;
}
</style>
