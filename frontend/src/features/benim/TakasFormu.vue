<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ApiHata } from '@/api/istemci'
import { saatBicimle, tarihBicimle } from '@/lib/tarih'
import { TAKAS_NEDENLERI, type TakasNedeni, type TakasTuru } from '@/features/takas/takasTipleri'
import { benimApi, type BenimGorev, type BenimTakas, type TakasHedef } from './benimApi'

/**
 * Takas / bırakma talebi formu. Takasta karşı görev listesi sunucudan gelir: kural motoru çalışmaz, listeden süzme yoktur; kurala uymayan seçim gönderilince tek genel mesajla reddedilir
 * (karşı makinistin belge / dinlenme durumu burada görünmez). Not tanı içermemelidir.
 */
const props = defineProps<{ gorev: BenimGorev; tur: TakasTuru }>()
const emit = defineEmits<{ kapat: []; olustu: [talep: BenimTakas] }>()

const dialog = ref<HTMLDialogElement | null>(null)
const hedefler = ref<TakasHedef[]>([])
const yukleniyor = ref(props.tur === 'TAKAS')
const yuklemeHatasi = ref('')
const mesgul = ref(false)
const genelHata = ref('')
const alanHatalari = ref<Record<string, string>>({})
const form = reactive({ hedefAtamaId: '', neden: '' as TakasNedeni | '', nedenNotu: '' })

/** Takasta seçilecek görev yok ya da hâlâ yükleniyor: gönder anlamsız (UX-2). */
const gonderilemez = computed(
  () => props.tur === 'TAKAS' && (yukleniyor.value || hedefler.value.length === 0),
)

const baslik = computed(() => (props.tur === 'TAKAS' ? 'Takas iste' : 'Görevi bırak'))

onMounted(async () => {
  dialog.value?.showModal()
  if (props.tur !== 'TAKAS') return
  try {
    hedefler.value = await benimApi.takasHedefler(props.gorev.atamaId)
  } catch (e) {
    yuklemeHatasi.value = e instanceof ApiHata ? e.detail : 'Seçilebilir görevler yüklenemedi.'
  } finally {
    yukleniyor.value = false
  }
})

const hedefMetni = (h: TakasHedef) =>
  `${h.sicil} · ${h.trenNo} · ${tarihBicimle(h.tarih)} ${saatBicimle(h.guncelKalkis)} · ${h.kalkisGar} → ${h.varisGar}`

async function gonder() {
  if (mesgul.value) return
  genelHata.value = ''
  const h: Record<string, string> = {}
  if (props.tur === 'TAKAS' && !form.hedefAtamaId) h.hedefAtamaId = 'Takas edilecek görevi seçin.'
  if (!form.neden) h.neden = 'Neden seçin.'
  else if (form.neden === 'DIGER' && !form.nedenNotu.trim())
    h.nedenNotu = 'Diğer nedeni için kısa bir açıklama yazın.'
  alanHatalari.value = h
  if (Object.keys(h).length > 0) return
  mesgul.value = true
  try {
    const talep = await benimApi.takasAc({
      atamaId: props.gorev.atamaId,
      hedefAtamaId: props.tur === 'TAKAS' ? Number(form.hedefAtamaId) : undefined,
      neden: form.neden as TakasNedeni,
      nedenNotu: form.nedenNotu.trim() || undefined,
    })
    emit('olustu', talep)
  } catch (e) {
    if (e instanceof ApiHata) {
      alanHatalari.value = e.alanlar ?? {}
      genelHata.value = Object.keys(alanHatalari.value).length > 0 ? '' : e.detail
    } else {
      genelHata.value = 'Talep gönderilemedi.'
    }
  } finally {
    mesgul.value = false
  }
}
</script>

<template>
  <dialog ref="dialog" aria-labelledby="takas-formu-baslik" @cancel.prevent="emit('kapat')">
    <h2 id="takas-formu-baslik">{{ baslik }}</h2>
    <p class="soluk">{{ gorev.trenNo }} · {{ gorev.ozet }}</p>
    <p v-if="tur === 'BIRAK'" class="soluk">
      Bırakma talebi atamacınıza gider; onaylanmadıkça göreviniz sizde kalır.
    </p>
    <p v-else class="soluk">
      Seçtiğiniz personel kabul ederse talep atamacıya gider; atamacı onaylamadıkça göreviniz sizde
      kalır.
    </p>

    <form novalidate @submit.prevent="gonder">
      <div v-if="tur === 'TAKAS'" class="alan">
        <label for="takas-hedef">Takas edilecek görev</label>
        <p v-if="yukleniyor" class="soluk" role="status">Görevler yükleniyor…</p>
        <p v-else-if="yuklemeHatasi" class="bildirim bildirim-hata" role="alert">
          {{ yuklemeHatasi }}
        </p>
        <p v-else-if="hedefler.length === 0" class="soluk" role="status">
          Şu an takas edilebilecek görev yok: kalkışına yeterli süre kalan, size uygun başka görev
          bulunamadı.
        </p>
        <select
          v-else
          id="takas-hedef"
          v-model="form.hedefAtamaId"
          :aria-invalid="!!alanHatalari.hedefAtamaId"
          :aria-describedby="alanHatalari.hedefAtamaId ? 'takas-hedef-hata' : undefined"
        >
          <option value="">Seçin…</option>
          <option v-for="h in hedefler" :key="h.hedefAtamaId" :value="String(h.hedefAtamaId)">
            {{ hedefMetni(h) }}
          </option>
        </select>
        <p v-if="alanHatalari.hedefAtamaId" id="takas-hedef-hata" class="alan-hata">
          {{ alanHatalari.hedefAtamaId }}
        </p>
      </div>

      <div class="alan">
        <label for="takas-neden">Neden</label>
        <select
          id="takas-neden"
          v-model="form.neden"
          :aria-invalid="!!alanHatalari.neden"
          :aria-describedby="alanHatalari.neden ? 'takas-neden-hata' : undefined"
        >
          <option value="">Seçin…</option>
          <option v-for="n in TAKAS_NEDENLERI" :key="n.deger" :value="n.deger">{{ n.ad }}</option>
        </select>
        <p v-if="alanHatalari.neden" id="takas-neden-hata" class="alan-hata">
          {{ alanHatalari.neden }}
        </p>
      </div>

      <div class="alan">
        <label for="takas-not">Not (isteğe bağlı)</label>
        <input
          id="takas-not"
          v-model="form.nedenNotu"
          type="text"
          maxlength="120"
          autocomplete="off"
          :aria-invalid="!!alanHatalari.nedenNotu"
          :aria-describedby="'takas-not-uyari' + (alanHatalari.nedenNotu ? ' takas-not-hata' : '')"
        />
        <p id="takas-not-uyari" class="soluk">
          En çok 120 karakter. Hastalık tanısı ya da sağlık ayrıntısı yazmayın.
        </p>
        <p v-if="alanHatalari.nedenNotu" id="takas-not-hata" class="alan-hata">
          {{ alanHatalari.nedenNotu }}
        </p>
      </div>

      <p v-if="genelHata" class="bildirim bildirim-hata" role="alert">{{ genelHata }}</p>

      <div class="eylemler">
        <button type="button" class="btn" @click="emit('kapat')">Vazgeç</button>
        <button type="submit" class="btn btn-birincil" :disabled="mesgul || gonderilemez">
          {{ mesgul ? 'Gönderiliyor…' : 'Talebi gönder' }}
        </button>
      </div>
    </form>
  </dialog>
</template>
