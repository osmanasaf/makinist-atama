<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ApiHata } from '@/api/istemci'
import { useCikisKorumasi } from '@/composables/cikis-korumasi'
import { tarihBicimle, zamanBicimle } from '@/lib/tarih'
import { ROL_ETIKETLERI } from '@/features/atama/atamaApi'
import { tanimApi, type TanimDto } from '@/features/tanim/tanimApi'
import MakinistHesap from './MakinistHesap.vue'
import {
  DURUM_ETIKETLERI,
  IZIN_DURUM_ETIKETLERI,
  IZIN_ETIKETLERI,
  TUR_ETIKETLERI,
  makinistApi,
  type Izin,
  type IzinEtkisiSatiri,
  type IzinTuru,
  type MakinistDetay,
  type MakinistDurumu,
  type PersonelTuru,
} from './makinistApi'

interface AracSatiri {
  aracTipiId: number | ''
  sinavTarihi: string
  gecerlilikBitis: string | null
}
interface HatSatiri {
  hatId: number | ''
  sinavTarihi: string
  sonSurus: string
  gecerlilikBitis: string | null
}

const route = useRoute()
const router = useRouter()
const id = computed(() => (route.params.id ? Number(route.params.id) : null))

const garlar = ref<TanimDto[]>([])
const hatlar = ref<TanimDto[]>([])
const aracTipleri = ref<TanimDto[]>([])

const form = reactive({
  sicil: '',
  adSoyad: '',
  tur: 'MAKINIST' as PersonelTuru,
  bagliGarId: '' as number | '',
  durum: 'AKTIF' as MakinistDurumu,
  ehliyetBitis: '',
  saglikBitis: '',
  psikoteknikBitis: '',
  sonSurus: '',
  versiyon: undefined as number | undefined,
  aracTipleri: [] as AracSatiri[],
  hatlar: [] as HatSatiri[],
})
const izinler = ref<Izin[]>([])
const izinForm = reactive({ baslangic: '', bitis: '', tur: 'YILLIK' as IzinTuru })
/** Yeni izinle çakışan atanmış seferler (izin ekleme sonrası; boşsa blok görünmez). */
const izinEtkisi = ref<IzinEtkisiSatiri[]>([])

/** Kondüktör (F3b): bröve ve son sürüş yok, ehliyet yerine kişisel emniyet belgesi. */
const konduktor = computed(() => form.tur === 'KONDUKTOR')

const alanHatalari = ref<Record<string, string>>({})
const hata = ref('')
const cakisma = ref(false)
const bilgi = ref('')
const mesgul = ref(false)
const yuklendi = ref(false)
let kaydedilmis = ''

const anlik = () => JSON.stringify(form)
useCikisKorumasi(() => yuklendi.value && anlik() !== kaydedilmis)

function doldur(m: MakinistDetay) {
  form.sicil = m.sicil
  form.adSoyad = m.adSoyad
  form.tur = m.tur ?? 'MAKINIST'
  form.bagliGarId = m.bagliGarId
  form.durum = m.durum
  form.ehliyetBitis = m.ehliyetBitis ?? ''
  form.saglikBitis = m.saglikBitis ?? ''
  form.psikoteknikBitis = m.psikoteknikBitis ?? ''
  form.sonSurus = m.sonSurus ?? ''
  form.versiyon = m.versiyon
  form.aracTipleri = m.broveAracTipleri.map((b) => ({
    aracTipiId: b.aracTipiId,
    sinavTarihi: b.sinavTarihi,
    gecerlilikBitis: b.gecerlilikBitis ?? null,
  }))
  form.hatlar = m.broveHatlar.map((b) => ({
    hatId: b.hatId,
    sinavTarihi: b.sinavTarihi,
    sonSurus: b.sonSurus ?? '',
    gecerlilikBitis: b.gecerlilikBitis ?? null,
  }))
  izinler.value = m.izinler
  kaydedilmis = anlik()
}

function hataYaz(e: unknown) {
  if (!(e instanceof ApiHata)) {
    hata.value = 'İşlem başarısız oldu.'
    return
  }
  alanHatalari.value = e.alanlar ?? {}
  if (e.kod === 'SICIL_TEKRARI') alanHatalari.value = { sicil: e.detail }
  else hata.value = e.detail
  cakisma.value = e.cakismaMi
}

async function yukle() {
  hata.value = ''
  cakisma.value = false
  try {
    if (id.value !== null) doldur(await makinistApi.getir(id.value))
    else kaydedilmis = anlik()
    yuklendi.value = true
  } catch (e) {
    hataYaz(e)
  }
}

onMounted(async () => {
  try {
    ;[garlar.value, hatlar.value, aracTipleri.value] = await Promise.all([
      tanimApi.liste('GAR'),
      tanimApi.liste('HAT'),
      tanimApi.liste('ARAC_TIPI'),
    ])
  } catch (e) {
    hataYaz(e)
  }
  await yukle()
})

/** İstemci doğrulaması: sunucu da doğrular, ama boş zorunlu alanlar için gidiş-dönüş gerekmez. */
function dogrula(): Record<string, string> {
  const h: Record<string, string> = {}
  if (!form.sicil.trim()) h.sicil = 'Sicil zorunludur.'
  if (!form.adSoyad.trim()) h.adSoyad = 'Ad soyad zorunludur.'
  if (form.bagliGarId === '') h.bagliGarId = 'Bağlı gar seçin.'
  if (form.aracTipleri.some((s) => s.aracTipiId === '' || !s.sinavTarihi))
    h.broveAracTipleri = 'Araç tipi bröve satırlarında araç tipi ve sınav tarihi zorunludur.'
  if (form.hatlar.some((s) => s.hatId === '' || !s.sinavTarihi))
    h.broveHatlar = 'Hat bröve satırlarında hat ve sınav tarihi zorunludur.'
  return h
}

async function kaydet() {
  if (mesgul.value) return
  hata.value = ''
  bilgi.value = ''
  cakisma.value = false
  alanHatalari.value = dogrula()
  if (Object.keys(alanHatalari.value).length > 0) return
  const govde = {
    sicil: form.sicil.trim(),
    adSoyad: form.adSoyad.trim(),
    bagliGarId: form.bagliGarId as number,
    durum: form.durum,
    tur: form.tur,
    ehliyetBitis: form.ehliyetBitis || null,
    saglikBitis: form.saglikBitis || null,
    psikoteknikBitis: form.psikoteknikBitis || null,
    sonSurus: konduktor.value ? null : form.sonSurus || null,
    versiyon: form.versiyon,
    broveAracTipleri: (konduktor.value ? [] : form.aracTipleri).map((s) => ({
      aracTipiId: s.aracTipiId as number,
      sinavTarihi: s.sinavTarihi,
    })),
    broveHatlar: (konduktor.value ? [] : form.hatlar).map((s) => ({
      hatId: s.hatId as number,
      sinavTarihi: s.sinavTarihi,
      sonSurus: s.sonSurus || null,
    })),
  }
  mesgul.value = true
  try {
    if (id.value === null) {
      const yeni = await makinistApi.olustur(govde)
      // Yeni ve kart rotaları aynı bileşendir: gezinme bileşeni yeniden kurmaz, bu yüzden sunucudaki kayıt (sicil, geçerlilik bitişi) forma burada yazılır.
      doldur(yeni) // kaydedilmis de güncellenir: kayıttan sonra gezinme korumaya takılmaz
      await router.replace({ name: 'makinist-kart', params: { id: yeni.id } })
      bilgi.value = 'Kaydedildi.'
    } else {
      doldur(await makinistApi.guncelle(id.value, govde))
      bilgi.value = 'Kaydedildi.'
    }
  } catch (e) {
    hataYaz(e)
  } finally {
    mesgul.value = false
  }
}

async function izinEkle() {
  if (id.value === null || mesgul.value) return
  hata.value = ''
  if (!izinForm.baslangic || !izinForm.bitis) {
    hata.value = 'İzin başlangıç ve bitiş tarihi zorunludur.'
    return
  }
  mesgul.value = true
  try {
    const { baslangic, bitis } = izinForm
    izinler.value = [...izinler.value, await makinistApi.izinEkle(id.value, { ...izinForm })]
    izinForm.baslangic = izinForm.bitis = ''
    izinEtkisi.value = []
    try {
      izinEtkisi.value = (await makinistApi.izinEtkisi(id.value, baslangic, bitis)) ?? []
    } catch {
      // izin zaten kaydedildi; etki sorgusu başarısızsa uyarı gösterilmez
    }
  } catch (e) {
    hataYaz(e)
  } finally {
    mesgul.value = false
  }
}

/** "HH:mm"; varış başka güne düşerse tarihle birlikte. */
const saat = (z: string) => zamanBicimle(z).slice(-5)
const varisSaati = (s: IzinEtkisiSatiri) =>
  zamanBicimle(s.varis).slice(0, 10) === zamanBicimle(s.kalkis).slice(0, 10)
    ? saat(s.varis)
    : zamanBicimle(s.varis)
</script>

<template>
  <section>
    <div class="sayfa-baslik">
      <h1>{{ id === null ? 'Yeni personel' : `${TUR_ETIKETLERI[form.tur]}: ${form.adSoyad}` }}</h1>
      <button type="button" class="btn" @click="router.push({ name: 'makinistler' })">
        Listeye dön
      </button>
    </div>
    <div v-if="hata" class="bildirim bildirim-hata" role="alert">
      <p>{{ hata }}</p>
      <button v-if="cakisma" type="button" class="btn" @click="yukle">Yeniden yükle</button>
    </div>
    <p v-if="bilgi" class="bildirim bildirim-bilgi" role="status">{{ bilgi }}</p>

    <form class="kart" novalidate @submit.prevent="kaydet">
      <div class="form-izgara">
        <div class="alan">
          <label for="sicil">Sicil</label>
          <input
            id="sicil"
            v-model="form.sicil"
            :readonly="id !== null"
            :aria-invalid="!!alanHatalari.sicil"
          />
          <p v-if="alanHatalari.sicil" class="alan-hata">{{ alanHatalari.sicil }}</p>
        </div>
        <div class="alan">
          <label for="tur">Personel türü</label>
          <select id="tur" v-model="form.tur" :disabled="id !== null">
            <option v-for="(etiket, k) in TUR_ETIKETLERI" :key="k" :value="k">{{ etiket }}</option>
          </select>
        </div>
        <div class="alan">
          <label for="adSoyad">Ad soyad</label>
          <input id="adSoyad" v-model="form.adSoyad" :aria-invalid="!!alanHatalari.adSoyad" />
          <p v-if="alanHatalari.adSoyad" class="alan-hata">{{ alanHatalari.adSoyad }}</p>
        </div>
        <div class="alan">
          <label for="bagliGar">Bağlı gar</label>
          <select id="bagliGar" v-model="form.bagliGarId" :aria-invalid="!!alanHatalari.bagliGarId">
            <option value="">Seçin</option>
            <option v-for="g in garlar" :key="g.id" :value="g.id">{{ g.ad }}</option>
          </select>
          <p v-if="alanHatalari.bagliGarId" class="alan-hata">{{ alanHatalari.bagliGarId }}</p>
        </div>
        <div class="alan">
          <label for="durum">Durum</label>
          <select id="durum" v-model="form.durum">
            <option v-for="(etiket, k) in DURUM_ETIKETLERI" :key="k" :value="k">
              {{ etiket }}
            </option>
          </select>
        </div>
        <div class="alan">
          <label for="ehliyetBitis">{{
            konduktor ? 'Kişisel emniyet belgesi bitişi' : 'Ehliyet bitişi'
          }}</label>
          <input
            id="ehliyetBitis"
            v-model="form.ehliyetBitis"
            type="date"
            :aria-invalid="!!alanHatalari.ehliyetBitis"
          />
          <p v-if="alanHatalari.ehliyetBitis" class="alan-hata">{{ alanHatalari.ehliyetBitis }}</p>
        </div>
        <div class="alan">
          <label for="saglikBitis">Sağlık bitişi</label>
          <input
            id="saglikBitis"
            v-model="form.saglikBitis"
            type="date"
            :aria-invalid="!!alanHatalari.saglikBitis"
          />
          <p v-if="alanHatalari.saglikBitis" class="alan-hata">{{ alanHatalari.saglikBitis }}</p>
        </div>
        <div class="alan">
          <label for="psikoteknikBitis">Psikoteknik bitişi</label>
          <input
            id="psikoteknikBitis"
            v-model="form.psikoteknikBitis"
            type="date"
            :aria-invalid="!!alanHatalari.psikoteknikBitis"
          />
          <p v-if="alanHatalari.psikoteknikBitis" class="alan-hata">
            {{ alanHatalari.psikoteknikBitis }}
          </p>
        </div>
        <div v-if="!konduktor" class="alan">
          <label for="sonSurus">Son sürüş</label>
          <input id="sonSurus" v-model="form.sonSurus" type="date" />
        </div>
      </div>

      <template v-if="!konduktor">
        <h2>Araç tipi bröveleri</h2>
        <p v-if="alanHatalari.broveAracTipleri" class="alan-hata">
          {{ alanHatalari.broveAracTipleri }}
        </p>
        <div class="tablo-kap">
          <table class="tablo">
            <thead>
              <tr>
                <th>Araç tipi</th>
                <th>Sınav tarihi</th>
                <th>Geçerlilik</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(s, i) in form.aracTipleri" :key="i">
                <td>
                  <select v-model="s.aracTipiId" :aria-label="`Araç tipi ${i + 1}`">
                    <option value="">Seçin</option>
                    <option v-for="a in aracTipleri" :key="a.id" :value="a.id">{{ a.ad }}</option>
                  </select>
                </td>
                <td>
                  <input
                    v-model="s.sinavTarihi"
                    type="date"
                    :aria-label="`Araç tipi sınav tarihi ${i + 1}`"
                    @input="s.gecerlilikBitis = null"
                  />
                </td>
                <td>
                  <span v-if="s.gecerlilikBitis"
                    >Geçerlilik bitişi: {{ tarihBicimle(s.gecerlilikBitis) }}</span
                  >
                  <small v-else>Kayıttan sonra hesaplanır</small>
                </td>
                <td>
                  <button type="button" class="btn" @click="form.aracTipleri.splice(i, 1)">
                    Sil
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <button
          type="button"
          class="btn"
          @click="form.aracTipleri.push({ aracTipiId: '', sinavTarihi: '', gecerlilikBitis: null })"
        >
          Araç tipi ekle
        </button>

        <h2>Hat bröveleri</h2>
        <p v-if="alanHatalari.broveHatlar" class="alan-hata">{{ alanHatalari.broveHatlar }}</p>
        <div class="tablo-kap">
          <table class="tablo">
            <thead>
              <tr>
                <th>Hat</th>
                <th>Sınav tarihi</th>
                <th>Son sürüş</th>
                <th>Geçerlilik</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(s, i) in form.hatlar" :key="i">
                <td>
                  <select v-model="s.hatId" :aria-label="`Hat ${i + 1}`">
                    <option value="">Seçin</option>
                    <option v-for="h in hatlar" :key="h.id" :value="h.id">{{ h.ad }}</option>
                  </select>
                </td>
                <td>
                  <input
                    v-model="s.sinavTarihi"
                    type="date"
                    :aria-label="`Hat sınav tarihi ${i + 1}`"
                    @input="s.gecerlilikBitis = null"
                  />
                </td>
                <td>
                  <input
                    v-model="s.sonSurus"
                    type="date"
                    :aria-label="`Hat son sürüş ${i + 1}`"
                    @input="s.gecerlilikBitis = null"
                  />
                </td>
                <td>
                  <span v-if="s.gecerlilikBitis"
                    >Geçerlilik bitişi: {{ tarihBicimle(s.gecerlilikBitis) }}</span
                  >
                  <small v-else>Kayıttan sonra hesaplanır</small>
                </td>
                <td>
                  <button type="button" class="btn" @click="form.hatlar.splice(i, 1)">Sil</button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <button
          type="button"
          class="btn"
          @click="
            form.hatlar.push({ hatId: '', sinavTarihi: '', sonSurus: '', gecerlilikBitis: null })
          "
        >
          Hat ekle
        </button>
      </template>

      <div class="eylemler">
        <button type="submit" class="btn btn-birincil" :disabled="mesgul">Kaydet</button>
      </div>
    </form>

    <MakinistHesap v-if="id !== null && yuklendi" :makinist-id="id" :durum="form.durum" />

    <section v-if="id !== null" class="kart" style="margin-top: 1rem">
      <h2>İzinler</h2>
      <div class="tablo-kap">
        <table class="tablo">
          <thead>
            <tr>
              <th>Başlangıç</th>
              <th>Bitiş</th>
              <th>Tür</th>
              <th>Durum</th>
              <th>Oluşturan</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="iz in izinler" :key="iz.id">
              <td>{{ tarihBicimle(iz.baslangic) }}</td>
              <td>{{ tarihBicimle(iz.bitis) }}</td>
              <td>{{ IZIN_ETIKETLERI[iz.tur] }}</td>
              <td>{{ IZIN_DURUM_ETIKETLERI[iz.durum] ?? iz.durum }}</td>
              <td>{{ iz.olusturan }}</td>
            </tr>
            <tr v-if="izinler.length === 0">
              <td colspan="5" class="bos-durum">İzin kaydı yok.</td>
            </tr>
          </tbody>
        </table>
      </div>
      <form class="arac-cubugu" style="margin-top: 0.75rem" @submit.prevent="izinEkle">
        <div class="alan">
          <label for="izin-baslangic">İzin başlangıç</label>
          <input id="izin-baslangic" v-model="izinForm.baslangic" type="date" />
        </div>
        <div class="alan">
          <label for="izin-bitis">İzin bitiş</label>
          <input id="izin-bitis" v-model="izinForm.bitis" type="date" />
        </div>
        <div class="alan">
          <label for="izin-tur">İzin türü</label>
          <select id="izin-tur" v-model="izinForm.tur">
            <option v-for="(etiket, k) in IZIN_ETIKETLERI" :key="k" :value="k">{{ etiket }}</option>
          </select>
        </div>
        <button type="submit" class="btn" :disabled="mesgul">İzin ekle</button>
      </form>
      <div v-if="izinEtkisi.length > 0" class="bildirim bildirim-uyari izin-etkisi" role="alert">
        <div>
          <p>
            <strong
              ><span aria-hidden="true">⚠</span> Bu izin aralığında atanmış
              {{ izinEtkisi.length }} sefer var — atamaları gözden geçirin</strong
            >
          </p>
          <div class="tablo-kap">
            <table class="tablo">
              <thead>
                <tr>
                  <th>Tarih</th>
                  <th>Tren no</th>
                  <th>Güzergâh</th>
                  <th>Kalkış–varış</th>
                  <th>Rol</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="e in izinEtkisi" :key="e.atamaId ?? `i${e.seferId}`">
                  <td>{{ tarihBicimle(e.tarih) }}</td>
                  <td>{{ e.trenNo }}</td>
                  <td>{{ e.kalkisGar }} → {{ e.varisGar }}</td>
                  <td>{{ saat(e.kalkis) }}–{{ varisSaati(e) }}</td>
                  <td>{{ ROL_ETIKETLERI[e.rol] }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </section>
  </section>
</template>
