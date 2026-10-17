<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ApiHata } from '@/api/istemci'
import { sayiBicimle } from '@/lib/sayi'
import { bugun, tarihBicimle } from '@/lib/tarih'
import { tanimApi, type TanimDto } from '../tanim/tanimApi'
import {
  BIRIM_ADLARI,
  ozelKuralApi,
  SIDDET_ACIKLAMALARI,
  SIDDET_ETIKETLERI,
  TREN_TURU_ETIKETLERI,
  VARDIYA_ETIKETLERI,
  type DeneSonucu,
  type Karsilastirma,
  type OlcuDto,
  type OzelKuralDto,
  type OzelKuralIstegi,
  type Pencere,
  type PersonelTuru,
  type TrenTuru,
  type Vardiya,
} from './ozelKuralApi'
import type { KuralSiddeti } from './parametreApi'

/**
 * Kural kurucu: yeni özel kural tanımlar; `kural` verilirse onu düzenler (sürüm + değişiklik nedeni gider).
 * Şiddet ENGEL ise kaydetmeden önce etki ("Dene") hesaplanır; kırmızı atama varsa onay istenir.
 */
const props = defineProps<{ katalog: OlcuDto[]; kural?: OzelKuralDto | null }>()
const emit = defineEmits<{
  kaydedildi: [kural: OzelKuralDto]
  kapat: []
}>()

type SecimAnahtari = 'hatlar' | 'aracTipleri' | 'kalkisGarlari' | 'varisGarlari'

const k = props.kural ?? null
const duzenleme = k !== null

const ad = ref(k?.ad ?? '')
const olcu = ref(k?.olcu ?? props.katalog[0]?.kod ?? '')
const pencere = ref<Pencere>(k?.pencere ?? props.katalog[0]?.pencereler[0]?.kod ?? 'GUN')
const kayanGun = ref(k?.kayanGun != null ? String(k.kayanGun) : '')
const karsilastirma = ref<Karsilastirma>(
  k?.karsilastirma ?? props.katalog[0]?.karsilastirmalar[0]?.kod ?? 'EN_FAZLA',
)
const esik = ref(k ? sayiBicimle(k.esik) : '')
const kapsam = reactive({
  hatlar: [...(k?.kapsam.hatlar ?? [])],
  aracTipleri: [...(k?.kapsam.aracTipleri ?? [])],
  kalkisGarlari: [...(k?.kapsam.kalkisGarlari ?? [])],
  varisGarlari: [...(k?.kapsam.varisGarlari ?? [])],
})
const trenTurleri = ref<TrenTuru[]>([...(k?.kapsam.trenTurleri ?? [])])
const vardiya = ref<Vardiya>(k?.kapsam.vardiya ?? 'HEPSI')
/** F3b: kural hangi personel türüne uygulanır; boş = tümü. */
const personelTurleri = ref<PersonelTuru[]>([...(k?.kapsam.personelTurleri ?? [])])
const PERSONEL_TURU_ETIKETLERI: Record<PersonelTuru, string> = {
  MAKINIST: 'Makinist',
  KONDUKTOR: 'Kondüktör',
}
const siddet = ref<KuralSiddeti>(k?.siddet ?? 'ENGEL')
const gecerlilikBas = ref(k?.gecerlilikBas ?? bugun())
const gecerlilikBit = ref(k?.gecerlilikBit ?? '')
const gerekce = ref(k?.gerekce ?? '')
const kaynak = ref(k?.kaynak ?? '')
const neden = ref('')

const hatlar = ref<TanimDto[]>([])
const aracTipleri = ref<TanimDto[]>([])
const garlar = ref<TanimDto[]>([])
const tanimHatasi = ref('')

const gruplar = computed<
  { anahtar: SecimAnahtari; baslik: string; cogul: string; liste: TanimDto[] }[]
>(() => [
  { anahtar: 'hatlar', baslik: 'Hat', cogul: 'hatlar', liste: hatlar.value },
  { anahtar: 'aracTipleri', baslik: 'Araç tipi', cogul: 'araç tipleri', liste: aracTipleri.value },
  { anahtar: 'kalkisGarlari', baslik: 'Kalkış garı', cogul: 'kalkış garları', liste: garlar.value },
  { anahtar: 'varisGarlari', baslik: 'Varış garı', cogul: 'varış garları', liste: garlar.value },
])

/** Kapsamda en az bir seçim olan gruplarda seçilmemiş tanımlar (sonradan eklenenler dahil): yalnız bilgi notu. */
const disardakiler = computed(() =>
  gruplar.value
    .map((g) => ({
      cogul: g.cogul,
      adlar:
        kapsam[g.anahtar].length === 0
          ? []
          : g.liste.filter((t) => !kapsam[g.anahtar].includes(t.id)).map((t) => t.ad),
    }))
    .filter((g) => g.adlar.length > 0),
)

const olcuDto = computed(() => props.katalog.find((o) => o.kod === olcu.value))
const pencereDto = computed(() => olcuDto.value?.pencereler.find((p) => p.kod === pencere.value))
const karsilastirmaDto = computed(() =>
  olcuDto.value?.karsilastirmalar.find((c) => c.kod === karsilastirma.value),
)
const birim = computed(() => olcuDto.value?.birim ?? 's')
const kayanMi = computed(() => pencere.value === 'KAYAN')
const esikIpucu = computed(() => {
  const p = pencereDto.value
  if (!p) return ''
  const alt = sayiBicimle(p.esikAlt)
  const altMetni = p.esikAltDahil ? `en az ${alt}` : `${alt} değerinden büyük`
  return `${altMetni}, en çok ${sayiBicimle(p.esikUst)} ${birim.value}`
})

// Ölçü değişince eski seçim yeni ölçüde izinli değilse ilk izinli olana düşer (geçersiz birleşim kalmaz).
function uyumlandir() {
  const o = olcuDto.value
  if (!o) return
  if (!o.pencereler.some((p) => p.kod === pencere.value)) pencere.value = o.pencereler[0]!.kod
  if (!o.karsilastirmalar.some((c) => c.kod === karsilastirma.value))
    karsilastirma.value = o.karsilastirmalar[0]!.kod
}
watch(olcu, uyumlandir)
uyumlandir()

const esikSayisi = computed(() => {
  const s = esik.value.trim().replace(',', '.')
  return s === '' || !Number.isFinite(Number(s)) ? null : Number(s)
})

/** KAYAN pencerede 2–366 arası tamsayı; değilse null. */
const kayanSayisi = computed(() => {
  const s = kayanGun.value.trim()
  const n = Number(s)
  return s !== '' && Number.isInteger(n) && n >= 2 && n <= 366 ? n : null
})

const govde = computed<OzelKuralIstegi>(() => ({
  ad: ad.value.trim(),
  olcu: olcu.value,
  pencere: pencere.value,
  kayanGun: kayanMi.value ? kayanSayisi.value : null,
  karsilastirma: karsilastirma.value,
  esik: esikSayisi.value ?? 0,
  kapsam: {
    hatlar: [...kapsam.hatlar],
    aracTipleri: [...kapsam.aracTipleri],
    kalkisGarlari: [...kapsam.kalkisGarlari],
    varisGarlari: [...kapsam.varisGarlari],
    trenTurleri: [...trenTurleri.value],
    vardiya: vardiya.value,
    ...(personelTurleri.value.length > 0 ? { personelTurleri: [...personelTurleri.value] } : {}),
  },
  siddet: siddet.value,
  gecerlilikBas: gecerlilikBas.value || undefined,
  gecerlilikBit: gecerlilikBit.value || null,
  gerekce: gerekce.value.trim(),
  kaynak: kaynak.value.trim() || undefined,
}))

/** Dene için eksik olan ilk şey (yoksa null); pasif düğmenin yanında gösterilir. */
const denePasifNedeni = computed(() => {
  if (ad.value.trim() === '') return 'Kural adı boş olamaz'
  if (esikSayisi.value === null) return 'Koşul değeri sayı olmalı'
  if (kayanMi.value && kayanSayisi.value === null)
    return 'Kaç gün 2 ile 366 arasında tam sayı olmalı'
  if (gerekce.value.trim() === '') return 'Gerekçe yazılmalı'
  return null
})
const denenebilir = computed(() => denePasifNedeni.value === null)
const kaydetPasifNedeni = computed(
  () =>
    denePasifNedeni.value ??
    (duzenleme && neden.value.trim() === '' ? 'Değişiklik nedeni yazılmalı' : null),
)
const gecerli = computed(() => kaydetPasifNedeni.value === null)

/** Canlı önizleme: "<Ölçü> (<pencere>) <en fazla/en az> <eşik> <birim> · Hat: ..." (sunucudaki `ozet` ile aynı biçim). */
const ozetMetni = computed(() => {
  const pencereAd = kayanMi.value
    ? `kayan ${kayanSayisi.value ?? '?'} gün`
    : (pencereDto.value?.ad ?? pencere.value).toLocaleLowerCase('tr')
  const esikMetni = esikSayisi.value === null ? '?' : sayiBicimle(esikSayisi.value)
  const karsilastirmaAd = karsilastirmaDto.value?.ad ?? karsilastirma.value
  const parcalar = [
    `${olcuDto.value?.ad ?? olcu.value} (${pencereAd}) ${karsilastirmaAd} ${esikMetni} ${birim.value}`,
  ]
  for (const g of gruplar.value) {
    const adlar = g.liste.filter((t) => kapsam[g.anahtar].includes(t.id)).map((t) => t.ad)
    if (adlar.length > 0) parcalar.push(`${g.baslik}: ${adlar.join(', ')}`)
  }
  if (trenTurleri.value.length > 0)
    parcalar.push(`Tren türü: ${trenTurleri.value.map((t) => TREN_TURU_ETIKETLERI[t]).join(', ')}`)
  if (vardiya.value !== 'HEPSI') parcalar.push(`Vardiya: ${VARDIYA_ETIKETLERI[vardiya.value]}`)
  if (personelTurleri.value.length > 0)
    parcalar.push(
      `Personel: ${personelTurleri.value.map((t) => PERSONEL_TURU_ETIKETLERI[t]).join(', ')}`,
    )
  return parcalar.join(' · ')
})

const mesgul = ref(false)
const genelHata = ref('')
const alanHatalari = ref<Record<string, string>>({})
const deneSonucu = ref<DeneSonucu | null>(null)
const deneHatasi = ref('')
const onayBekliyor = ref(false)
let deneSurumu = 0

// Form değişti: eski etki hesabı bu forma ait değil.
watch(govde, () => {
  deneSurumu++
  deneSonucu.value = null
  deneHatasi.value = ''
  onayBekliyor.value = false
})

const dialog = ref<HTMLDialogElement | null>(null)
onMounted(async () => {
  dialog.value?.showModal()
  try {
    const [h, a, g] = await Promise.all([
      tanimApi.liste('HAT'),
      tanimApi.liste('ARAC_TIPI'),
      tanimApi.liste('GAR'),
    ])
    hatlar.value = h
    aracTipleri.value = a
    garlar.value = g
  } catch (e) {
    tanimHatasi.value =
      e instanceof ApiHata ? e.detail : 'Hat, araç tipi ve gar listeleri yüklenemedi.'
  }
})

function vazgec() {
  if (!mesgul.value) emit('kapat')
}

function hataIsle(e: unknown, varsayilan: string) {
  if (e instanceof ApiHata && e.kod === 'DOGRULAMA' && e.alanlar) {
    alanHatalari.value = e.alanlar
    // Ekranda alanı olmayan sürüm hatası da görünsün.
    if (e.alanlar.versiyon) genelHata.value = e.alanlar.versiyon
  } else {
    genelHata.value = e instanceof ApiHata ? e.detail : varsayilan
  }
}

/** Etkiyi hesaplar; yazmaz. Başarıda sonuç dolar. */
async function dene() {
  if (mesgul.value || !denenebilir.value) return
  mesgul.value = true
  genelHata.value = ''
  alanHatalari.value = {}
  deneHatasi.value = ''
  deneSonucu.value = null
  const surum = ++deneSurumu
  try {
    const s = await ozelKuralApi.dene(govde.value, k?.id)
    if (surum === deneSurumu) deneSonucu.value = s
  } catch (e) {
    if (surum !== deneSurumu) return
    if (e instanceof ApiHata && e.kod === 'DOGRULAMA' && e.alanlar) hataIsle(e, '')
    else deneHatasi.value = e instanceof ApiHata ? e.detail : 'Etki hesaplanamadı.'
  } finally {
    mesgul.value = false
  }
}

async function yaz() {
  if (mesgul.value || !gecerli.value) return
  mesgul.value = true
  genelHata.value = ''
  alanHatalari.value = {}
  try {
    const kayit = k
      ? await ozelKuralApi.guncelle(k.id, {
          ...govde.value,
          versiyon: k.versiyon,
          neden: neden.value.trim(),
        })
      : await ozelKuralApi.ekle(govde.value)
    emit('kaydedildi', kayit)
    emit('kapat')
  } catch (e) {
    hataIsle(e, 'Kural kaydedilemedi.')
    onayBekliyor.value = false
  } finally {
    mesgul.value = false
  }
}

async function kaydet() {
  if (mesgul.value || !gecerli.value) return
  if (siddet.value === 'ENGEL' && !onayBekliyor.value) {
    if (!deneSonucu.value) {
      await dene()
      if (!deneSonucu.value) return // hesaplanamadı: hata gösterildi, kaydedilmez
    }
    if (deneSonucu.value.kirmiziAtama > 0) {
      onayBekliyor.value = true
      return
    }
  }
  await yaz()
}

const sonucMetni = computed(() => {
  const s = deneSonucu.value
  if (!s) return ''
  const parcalar: string[] = []
  if (s.kirmiziAtama > 0)
    parcalar.push(
      `Bu kural kaydedilirse ${s.kirmiziAtama} atama kırmızı olur (${s.kirmiziMakinist} personel).`,
    )
  if (s.uyariAtama > 0) parcalar.push(`${s.uyariAtama} atama uyarı alır.`)
  return parcalar.length > 0 ? parcalar.join(' ') : 'Mevcut atamalardan hiçbiri etkilenmez.'
})
const DURUM_ETIKETLERI = { KIRMIZI: 'Kırmızı', UYARI: 'Uyarı' }
</script>

<template>
  <dialog ref="dialog" class="genis-dialog" aria-labelledby="kk-baslik" @cancel.prevent="vazgec">
    <form @submit.prevent="kaydet">
      <h2 id="kk-baslik">{{ duzenleme ? `Kuralı düzenle (${kural?.kod})` : 'Yeni kural' }}</h2>

      <div role="alert">
        <p v-if="genelHata" class="bildirim bildirim-hata">{{ genelHata }}</p>
        <p v-if="tanimHatasi" class="bildirim bildirim-hata">{{ tanimHatasi }}</p>
      </div>

      <div class="alan">
        <label for="kk-ad">Kural adı</label>
        <input
          id="kk-ad"
          v-model="ad"
          maxlength="80"
          required
          :aria-invalid="!!alanHatalari.ad"
          :aria-describedby="alanHatalari.ad ? 'kk-ad-hata' : undefined"
        />
        <p v-if="alanHatalari.ad" id="kk-ad-hata" class="alan-hata">{{ alanHatalari.ad }}</p>
      </div>

      <div class="alan">
        <label for="kk-olcu">Ölçü</label>
        <select
          id="kk-olcu"
          v-model="olcu"
          :aria-invalid="!!alanHatalari.olcu"
          :aria-describedby="alanHatalari.olcu ? 'kk-olcu-hata' : undefined"
        >
          <option v-if="!olcuDto && kural" :value="kural.olcu">{{ kural.olcuAd }}</option>
          <option v-for="o in katalog" :key="o.kod" :value="o.kod">{{ o.ad }}</option>
        </select>
        <p v-if="olcuDto?.aciklama" class="soluk yardim">{{ olcuDto.aciklama }}</p>
        <p v-if="olcu === 'IZIN_GUNU'" class="soluk yardim">
          Boş günler izin sayılır; ayın başında engellemez, eşiğin altına düşüren ilk atamada
          engeller.
        </p>
        <p v-if="alanHatalari.olcu" id="kk-olcu-hata" class="alan-hata">{{ alanHatalari.olcu }}</p>
      </div>

      <div class="alan">
        <label for="kk-pencere">Zaman aralığı</label>
        <select
          id="kk-pencere"
          v-model="pencere"
          :aria-invalid="!!alanHatalari.pencere"
          :aria-describedby="alanHatalari.pencere ? 'kk-pencere-hata' : undefined"
        >
          <option v-for="p in olcuDto?.pencereler ?? []" :key="p.kod" :value="p.kod">
            {{ p.ad }}
          </option>
        </select>
        <p v-if="alanHatalari.pencere" id="kk-pencere-hata" class="alan-hata">
          {{ alanHatalari.pencere }}
        </p>
        <p v-if="pencereDto?.aciklama" class="soluk yardim">{{ pencereDto.aciklama }}</p>
      </div>

      <div v-if="kayanMi" class="alan">
        <label for="kk-kayan">Kaç gün</label>
        <input
          id="kk-kayan"
          v-model="kayanGun"
          inputmode="numeric"
          required
          :aria-invalid="!!alanHatalari.kayanGun"
          aria-describedby="kk-kayan-ipucu"
        />
        <p id="kk-kayan-ipucu" class="soluk yardim">2 ile 366 arasında tam sayı.</p>
        <p v-if="alanHatalari.kayanGun" class="alan-hata">{{ alanHatalari.kayanGun }}</p>
      </div>

      <div class="alan">
        <label for="kk-esik">Koşul ({{ BIRIM_ADLARI[birim] }})</label>
        <div class="kosul">
          <select
            v-model="karsilastirma"
            aria-label="Koşul türü"
            :aria-invalid="!!alanHatalari.karsilastirma"
          >
            <option v-for="c in olcuDto?.karsilastirmalar ?? []" :key="c.kod" :value="c.kod">
              {{ c.ad }}
            </option>
          </select>
          <input
            id="kk-esik"
            v-model="esik"
            inputmode="decimal"
            required
            :aria-invalid="!!alanHatalari.esik"
            :aria-describedby="alanHatalari.esik ? 'kk-esik-ipucu kk-esik-hata' : 'kk-esik-ipucu'"
          />
          <span>{{ birim }}</span>
        </div>
        <p v-if="karsilastirmaDto?.aciklama" class="soluk yardim">
          {{ karsilastirmaDto.aciklama }}
        </p>
        <p id="kk-esik-ipucu" class="soluk yardim">Geçerli aralık: {{ esikIpucu }}</p>
        <p v-if="alanHatalari.esik" id="kk-esik-hata" class="alan-hata">{{ alanHatalari.esik }}</p>
        <p v-if="alanHatalari.karsilastirma" class="alan-hata">{{ alanHatalari.karsilastirma }}</p>
      </div>

      <fieldset class="grup">
        <legend>Kapsam</legend>
        <p class="soluk yardim">
          Hiçbiri seçilmezse kural hepsine uygulanır. Aynı alanda birden çok seçerseniz biri yeter
          ("veya"); farklı alanlarda seçtiklerinizin hepsi birlikte aranır ("ve"). Örnek: yalnız
          dizel hatlar için "Araç tipi" alanında dizel olanı seçin.
        </p>
        <fieldset v-for="g in gruplar" :key="g.anahtar" class="alt-grup">
          <legend>{{ g.baslik }}</legend>
          <p v-if="g.liste.length === 0" class="soluk yardim">Kayıt yok.</p>
          <label v-for="t in g.liste" :key="t.id" class="secenek">
            <input v-model="kapsam[g.anahtar]" type="checkbox" :value="t.id" />
            {{ t.ad }}
          </label>
        </fieldset>
        <p v-for="d in disardakiler" :key="d.cogul" class="soluk yardim">
          <small>Kapsama girmeyen {{ d.cogul }}: {{ d.adlar.join(', ') }}</small>
        </p>
        <fieldset class="alt-grup">
          <legend>Tren türü</legend>
          <label v-for="(etiket, t) in TREN_TURU_ETIKETLERI" :key="t" class="secenek">
            <input v-model="trenTurleri" type="checkbox" :value="t" />
            {{ etiket }}
          </label>
        </fieldset>
        <fieldset class="alt-grup">
          <legend>Personel türü</legend>
          <label v-for="(etiket, t) in PERSONEL_TURU_ETIKETLERI" :key="t" class="secenek">
            <input v-model="personelTurleri" type="checkbox" :value="t" />
            {{ etiket }}
          </label>
        </fieldset>
        <fieldset class="alt-grup">
          <legend>Vardiya</legend>
          <label v-for="(etiket, v) in VARDIYA_ETIKETLERI" :key="v" class="secenek">
            <input v-model="vardiya" type="radio" name="kk-vardiya" :value="v" />
            {{ etiket }}
          </label>
        </fieldset>
        <p v-if="alanHatalari.kapsam" class="alan-hata">{{ alanHatalari.kapsam }}</p>
      </fieldset>

      <fieldset class="grup">
        <legend>Şiddet</legend>
        <label v-for="(etiket, s) in SIDDET_ETIKETLERI" :key="s" class="secenek-satir">
          <input v-model="siddet" type="radio" name="kk-siddet" :value="s" />
          <strong>{{ etiket }}</strong>
          <span class="soluk yardim">{{ SIDDET_ACIKLAMALARI[s] }}</span>
        </label>
        <p v-if="alanHatalari.siddet" class="alan-hata">{{ alanHatalari.siddet }}</p>
      </fieldset>

      <div class="tarihler">
        <div class="alan">
          <label for="kk-bas">Geçerlilik başlangıcı</label>
          <input
            id="kk-bas"
            v-model="gecerlilikBas"
            type="date"
            aria-describedby="kk-tarih-ipucu"
          />
        </div>
        <div class="alan">
          <label for="kk-bit">Geçerlilik bitişi (isteğe bağlı)</label>
          <input
            id="kk-bit"
            v-model="gecerlilikBit"
            type="date"
            :aria-invalid="!!alanHatalari.gecerlilikBit"
            :aria-describedby="alanHatalari.gecerlilikBit ? 'kk-bit-hata' : undefined"
          />
          <p v-if="alanHatalari.gecerlilikBit" id="kk-bit-hata" class="alan-hata">
            {{ alanHatalari.gecerlilikBit }}
          </p>
        </div>
      </div>

      <p id="kk-tarih-ipucu" class="soluk yardim">
        Kural yalnız bu tarihler arasındaki görevleri sayar; başlangıçtan önceki (ve bitişten
        sonraki) görevler toplamlara ve üst üste günlere katılmaz. Aylık bir kuralın o ayın başından
        itibaren saymasını istiyorsanız başlangıcı ayın ilk gününe çekin.
      </p>

      <div class="alan">
        <label for="kk-gerekce">Gerekçe (zorunlu)</label>
        <textarea
          id="kk-gerekce"
          v-model="gerekce"
          maxlength="500"
          required
          :aria-invalid="!!alanHatalari.gerekce"
          :aria-describedby="alanHatalari.gerekce ? 'kk-gerekce-hata' : undefined"
        ></textarea>
        <p v-if="alanHatalari.gerekce" id="kk-gerekce-hata" class="alan-hata">
          {{ alanHatalari.gerekce }}
        </p>
      </div>

      <div class="alan">
        <label for="kk-kaynak">Kaynak (isteğe bağlı)</label>
        <input id="kk-kaynak" v-model="kaynak" maxlength="200" />
        <p v-if="alanHatalari.kaynak" class="alan-hata">{{ alanHatalari.kaynak }}</p>
      </div>

      <div v-if="duzenleme" class="alan">
        <label for="kk-neden">Değişiklik nedeni (zorunlu)</label>
        <input
          id="kk-neden"
          v-model="neden"
          maxlength="500"
          required
          :aria-invalid="!!alanHatalari.neden"
          :aria-describedby="alanHatalari.neden ? 'kk-neden-hata' : undefined"
        />
        <p v-if="alanHatalari.neden" id="kk-neden-hata" class="alan-hata">
          {{ alanHatalari.neden }}
        </p>
      </div>

      <p class="bildirim bildirim-bilgi" aria-label="Kural özeti">
        <strong>Kural özeti:</strong> {{ ozetMetni }}
      </p>

      <div class="dene-kutusu">
        <button
          type="button"
          class="btn"
          :disabled="mesgul || !denenebilir"
          :aria-describedby="denePasifNedeni ? 'kk-dene-ipucu' : undefined"
          @click="dene"
        >
          Dene
        </button>
        <span v-if="denePasifNedeni" id="kk-dene-ipucu" class="soluk yardim">
          {{ denePasifNedeni }}
        </span>
        <span v-else class="soluk yardim">
          Kaydetmeden önce mevcut atamalara etkisine bakar; hiçbir şey yazmaz.
        </span>
        <div role="status">
          <template v-if="deneSonucu">
            <p class="bildirim bildirim-bilgi">
              <strong>{{ sonucMetni }}</strong>
            </p>
            <p class="soluk yardim">
              {{ tarihBicimle(deneSonucu.bas) }} – {{ tarihBicimle(deneSonucu.bit) }} arasındaki
              {{ deneSonucu.toplamAtama }} atamaya bakıldı.
            </p>
            <p v-if="deneSonucu.ornekler.length > 0" class="soluk yardim">
              {{ deneSonucu.ornekler.length >= 10 ? 'İlk 10 örnek' : 'Örnekler' }} (aynı personel,
              gün ve neden tek satır olarak gösterilir):
            </p>
            <ul v-if="deneSonucu.ornekler.length > 0" class="ornekler" aria-label="Örnekler">
              <li v-for="(o, i) in deneSonucu.ornekler" :key="i">
                <strong>{{ DURUM_ETIKETLERI[o.durum] }}</strong> · {{ o.sicil }} ·
                {{ tarihBicimle(o.tarih) }} · {{ o.metin }}
              </li>
            </ul>
          </template>
        </div>
        <div role="alert">
          <p v-if="deneHatasi" class="bildirim bildirim-hata">{{ deneHatasi }}</p>
        </div>
      </div>

      <div v-if="onayBekliyor && deneSonucu" class="bildirim bildirim-uyari" role="alert">
        <span>{{ deneSonucu.kirmiziAtama }} atama kırmızı olacak, devam edilsin mi?</span>
        <span class="onay-eylem">
          <button type="button" class="btn btn-birincil" :disabled="mesgul" @click="yaz">
            Evet, kaydet
          </button>
          <button type="button" class="btn" :disabled="mesgul" @click="onayBekliyor = false">
            Vazgeç
          </button>
        </span>
      </div>

      <div class="eylemler">
        <button
          type="submit"
          class="btn btn-birincil"
          :disabled="!gecerli || mesgul"
          :aria-describedby="kaydetPasifNedeni ? 'kk-kaydet-ipucu' : undefined"
        >
          Kaydet
        </button>
        <button type="button" class="btn" :disabled="mesgul" @click="vazgec">İptal</button>
        <span v-if="kaydetPasifNedeni" id="kk-kaydet-ipucu" class="soluk yardim">
          {{ kaydetPasifNedeni }}
        </span>
      </div>
    </form>
  </dialog>
</template>

<style scoped>
.genis-dialog {
  min-width: min(720px, 94vw);
}
.yardim {
  margin: 0;
  font-size: 0.85rem;
}
.kosul {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.5rem;
}
.grup,
.alt-grup {
  border: 0;
  padding: 0;
  margin: 0 0 var(--bosluk);
}
.grup > legend {
  font-weight: 600;
  font-size: 0.9rem;
  padding: 0;
  margin-bottom: 0.25rem;
}
.alt-grup {
  margin-bottom: 0.5rem;
}
.alt-grup > legend {
  font-size: 0.85rem;
  padding: 0;
  color: var(--renk-soluk);
}
.secenek {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  margin-right: 1rem;
}
.secenek-satir {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 0.35rem;
  margin-bottom: 0.25rem;
}
.tarihler {
  display: flex;
  flex-wrap: wrap;
  gap: var(--bosluk);
}
.dene-kutusu {
  margin-top: var(--bosluk);
}
.ornekler {
  margin: 0;
  padding-left: 1.25rem;
}
.onay-eylem {
  display: flex;
  gap: 0.5rem;
}
</style>
