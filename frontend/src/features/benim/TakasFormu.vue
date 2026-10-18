<script setup lang="ts">
import AlanHatasi from '@/bilesenler/AlanHatasi.vue'
import { computed, onMounted, reactive, ref } from 'vue'
import { ilkHataliyaOdaklan } from '@/lib/odak'
import { ApiHata } from '@/api/istemci'
import { saatBicimle, tarihBicimle } from '@/lib/tarih'
import { TAKAS_NEDENLERI, type TakasNedeni, type TakasTuru } from '@/features/takas/takasTipleri'
import Diyalog from '@/bilesenler/Diyalog.vue'
import VeriDurumu from '@/bilesenler/VeriDurumu.vue'
import Dugme from '@/bilesenler/Dugme.vue'
import { benimApi, type BenimGorev, type BenimTakas, type TakasHedef } from './benimApi'

/**
 * Takas / bırakma talebi formu. Takasta karşı görev listesi sunucudan gelir: kural motoru çalışmaz, listeden süzme yoktur; kurala uymayan seçim gönderilince tek genel mesajla reddedilir
 * (karşı makinistin belge / dinlenme durumu burada görünmez). Not tanı içermemelidir.
 */
const props = defineProps<{ gorev: BenimGorev; tur: TakasTuru }>()
const emit = defineEmits<{ kapat: []; olustu: [talep: BenimTakas] }>()

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

/** Birincil eylem kapalıyken nedeni (Diyalog `neden` slotu). */
const eylemNedeni = computed(() => {
  if (!gonderilemez.value) return ''
  if (yukleniyor.value) return 'Takas edilecek görevler yükleniyor.'
  return yuklemeHatasi.value ? 'Takas edilecek görevler yüklenemedi.' : 'Seçilebilecek görev yok.'
})

onMounted(async () => {
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

/** Diyalog kökü (odak ilk geçersiz alana taşınır) */
const diyalogKok = ref<{ $el: HTMLElement } | null>(null)

/** × / Esc istek sürerken kapatmaz (sökülen bileşenden `olustu` yayılmaz, liste yenilenmezdi). */
function vazgec() {
  if (!mesgul.value) emit('kapat')
}

async function gonder() {
  if (mesgul.value) return
  genelHata.value = ''
  const h: Record<string, string> = {}
  if (props.tur === 'TAKAS' && !form.hedefAtamaId) h.hedefAtamaId = 'Takas edilecek görevi seçin.'
  if (!form.neden) h.neden = 'Neden seçin.'
  else if (form.neden === 'DIGER' && !form.nedenNotu.trim())
    h.nedenNotu = 'Diğer nedeni için kısa bir açıklama yazın.'
  alanHatalari.value = h
  if (Object.keys(h).length > 0) return ilkHataliyaOdaklan(diyalogKok.value?.$el)
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
      await ilkHataliyaOdaklan(diyalogKok.value?.$el)
    } else {
      genelHata.value = 'Talep gönderilemedi.'
    }
  } finally {
    mesgul.value = false
  }
}
</script>

<template>
  <Diyalog
    ref="diyalogKok"
    :baslik="baslik"
    boyut="orta"
    :form="true"
    @kapat="vazgec"
    @gonder="gonder"
  >
    <p class="soluk">{{ gorev.trenNo }} · {{ gorev.ozet }}</p>
    <p v-if="tur === 'BIRAK'" class="soluk">
      Bırakma talebi atamacınıza gider; onaylanmadıkça göreviniz sizde kalır.
    </p>
    <p v-else class="soluk">
      Seçtiğiniz personel kabul ederse talep atamacıya gider; atamacı onaylamadıkça göreviniz sizde
      kalır.
    </p>

    <div v-if="tur === 'TAKAS'" class="alan">
      <label for="takas-hedef">Takas edilecek görev</label>
      <VeriDurumu v-if="yukleniyor" durum="yukleniyor" metin="Görevler yükleniyor…" />
      <p v-else-if="yuklemeHatasi" class="bildirim bildirim-hata" role="alert">
        {{ yuklemeHatasi }}
      </p>
      <VeriDurumu
        v-else-if="hedefler.length === 0"
        durum="bos"
        metin="Şu an takas edilebilecek görev yok: kalkışına yeterli süre kalan, size uygun başka görev bulunamadı."
      />
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
      <AlanHatasi id="takas-hedef-hata" :metin="alanHatalari.hedefAtamaId" />
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
      <AlanHatasi id="takas-neden-hata" :metin="alanHatalari.neden" />
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
      <AlanHatasi id="takas-not-hata" :metin="alanHatalari.nedenNotu" />
    </div>

    <div role="alert">
      <p v-if="genelHata" class="bildirim bildirim-hata">{{ genelHata }}</p>
    </div>

    <template #eylemler>
      <Dugme varyant="birincil" boyut="dokunma" tip="submit" :disabled="mesgul || gonderilemez">
        {{ mesgul ? 'Gönderiliyor…' : 'Talebi gönder' }}
      </Dugme>
      <Dugme boyut="dokunma" :disabled="mesgul" @click="vazgec">Vazgeç</Dugme>
    </template>
    <template v-if="eylemNedeni" #neden>{{ eylemNedeni }}</template>
  </Diyalog>
</template>
