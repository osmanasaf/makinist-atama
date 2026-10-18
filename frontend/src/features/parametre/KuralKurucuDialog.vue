<script setup lang="ts">
import AlanHatasi from '@/bilesenler/AlanHatasi.vue'
import { computed, nextTick, onMounted, reactive, ref, useTemplateRef, watch } from 'vue'
import { ApiHata } from '@/api/istemci'
import { kaydirmaDavranisi } from '@/lib/odak'
import { sayiBicimle } from '@/lib/sayi'
import { bugun, tarihBicimle } from '@/lib/tarih'
import Diyalog from '@/bilesenler/Diyalog.vue'
import Dugme from '@/bilesenler/Dugme.vue'
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
const onayKutusu = useTemplateRef<HTMLElement>('onayKutusu')
// QA BUG-3: onay kutusu gövdenin altında kalıyordu (Kaydet "ölü" görünüyordu): görünür alana kaydır ve odağı ver.
watch(onayBekliyor, async (acik) => {
  if (!acik) return
  await nextTick()
  onayKutusu.value?.scrollIntoView?.({ block: 'center', behavior: kaydirmaDavranisi() })
  onayKutusu.value?.focus({ preventScroll: true })
})
let deneSurumu = 0

// Form değişti: eski etki hesabı bu forma ait değil.
watch(govde, () => {
  deneSurumu++
  deneSonucu.value = null
  deneHatasi.value = ''
  onayBekliyor.value = false
})

onMounted(async () => {
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

const kok = useTemplateRef<HTMLElement>('kok')

/** Hata sonrası odak: ilk hatalı alana (aria-invalid) ya da hatalı grubun ilk seçeneğine; alan hatası yoksa odak değişmez (genel hata `role=alert` ile duyurulur). */
async function ilkHataliyaOdak() {
  await nextTick()
  kok.value
    ?.querySelector<HTMLElement>('[aria-invalid="true"], fieldset[aria-describedby] input')
    ?.focus()
}

function hataIsle(e: unknown, varsayilan: string) {
  if (e instanceof ApiHata && e.kod === 'DOGRULAMA' && e.alanlar) {
    alanHatalari.value = e.alanlar
    // Ekranda alanı olmayan sürüm hatası da görünsün.
    if (e.alanlar.versiyon) genelHata.value = e.alanlar.versiyon
    void ilkHataliyaOdak()
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
  <Diyalog
    :baslik="duzenleme ? `Kuralı düzenle (${kural?.kod})` : 'Yeni kural'"
    boyut="genis"
    :form="true"
    @kapat="vazgec"
    @gonder="kaydet"
  >
    <div ref="kok" class="kk-kok">
      <div role="alert">
        <p v-if="genelHata" class="bildirim bildirim-hata">{{ genelHata }}</p>
        <p v-if="tanimHatasi" class="bildirim bildirim-hata">{{ tanimHatasi }}</p>
      </div>
      <p class="alan-yardim kk-zorunlu-not">* ile işaretli alanlar zorunludur.</p>

      <section class="kk-bolum" aria-labelledby="kk-b-tanim">
        <h3 id="kk-b-tanim">1. Kuralın tanımı</h3>
        <div class="alan alan-zorunlu">
          <label for="kk-ad">Kural adı</label>
          <input
            id="kk-ad"
            v-model="ad"
            maxlength="80"
            required
            :aria-invalid="!!alanHatalari.ad"
            :aria-describedby="alanHatalari.ad ? 'kk-ad-hata' : undefined"
          />
          <AlanHatasi id="kk-ad-hata" :metin="alanHatalari.ad" />
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
          <AlanHatasi id="kk-olcu-hata" :metin="alanHatalari.olcu" />
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
          <AlanHatasi id="kk-pencere-hata" :metin="alanHatalari.pencere" />
          <p v-if="pencereDto?.aciklama" class="soluk yardim">{{ pencereDto.aciklama }}</p>
        </div>

        <div v-if="kayanMi" class="alan alan-zorunlu">
          <label for="kk-kayan">Kaç gün</label>
          <input
            id="kk-kayan"
            v-model="kayanGun"
            inputmode="numeric"
            required
            :aria-invalid="!!alanHatalari.kayanGun"
            :aria-describedby="
              alanHatalari.kayanGun ? 'kk-kayan-ipucu kk-kayan-hata' : 'kk-kayan-ipucu'
            "
          />
          <p id="kk-kayan-ipucu" class="soluk yardim">2 ile 366 arasında tam sayı.</p>
          <AlanHatasi id="kk-kayan-hata" :metin="alanHatalari.kayanGun" />
        </div>

        <div class="alan alan-zorunlu">
          <label for="kk-esik">Koşul ({{ BIRIM_ADLARI[birim] }})</label>
          <div class="kosul">
            <select
              v-model="karsilastirma"
              aria-label="Koşul türü"
              :aria-invalid="!!alanHatalari.karsilastirma"
              :aria-describedby="alanHatalari.karsilastirma ? 'kk-karsilastirma-hata' : undefined"
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
          <AlanHatasi id="kk-esik-hata" :metin="alanHatalari.esik" />
          <AlanHatasi id="kk-karsilastirma-hata" :metin="alanHatalari.karsilastirma" />
        </div>
      </section>

      <section class="kk-bolum" aria-labelledby="kk-b-kapsam">
        <h3 id="kk-b-kapsam">2. Kime uygulanır</h3>
        <fieldset
          class="grup"
          :aria-describedby="alanHatalari.kapsam ? 'kk-kapsam-hata' : undefined"
        >
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
          <AlanHatasi id="kk-kapsam-hata" :metin="alanHatalari.kapsam" />
        </fieldset>
      </section>

      <section class="kk-bolum" aria-labelledby="kk-b-siddet">
        <h3 id="kk-b-siddet">3. Şiddet ve geçerlilik</h3>
        <fieldset
          class="grup"
          :aria-describedby="alanHatalari.siddet ? 'kk-siddet-hata' : undefined"
        >
          <legend>Şiddet</legend>
          <label v-for="(etiket, s) in SIDDET_ETIKETLERI" :key="s" class="secenek-satir">
            <input v-model="siddet" type="radio" name="kk-siddet" :value="s" />
            <strong>{{ etiket }}</strong>
            <span class="soluk yardim">{{ SIDDET_ACIKLAMALARI[s] }}</span>
          </label>
          <AlanHatasi id="kk-siddet-hata" :metin="alanHatalari.siddet" />
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
            <AlanHatasi id="kk-bit-hata" :metin="alanHatalari.gecerlilikBit" />
          </div>
        </div>

        <p id="kk-tarih-ipucu" class="soluk yardim">
          Kural yalnız bu tarihler arasındaki görevleri sayar; başlangıçtan önceki (ve bitişten
          sonraki) görevler toplamlara ve üst üste günlere katılmaz. Aylık bir kuralın o ayın
          başından itibaren saymasını istiyorsanız başlangıcı ayın ilk gününe çekin.
        </p>
      </section>

      <section class="kk-bolum" aria-labelledby="kk-b-gerekce">
        <h3 id="kk-b-gerekce">4. Gerekçe</h3>
        <div class="alan alan-zorunlu">
          <label for="kk-gerekce">Gerekçe (zorunlu)</label>
          <textarea
            id="kk-gerekce"
            v-model="gerekce"
            maxlength="500"
            required
            :aria-invalid="!!alanHatalari.gerekce"
            :aria-describedby="alanHatalari.gerekce ? 'kk-gerekce-hata' : undefined"
          ></textarea>
          <AlanHatasi id="kk-gerekce-hata" :metin="alanHatalari.gerekce" />
        </div>

        <div class="alan">
          <label for="kk-kaynak">Kaynak (isteğe bağlı)</label>
          <input
            id="kk-kaynak"
            v-model="kaynak"
            maxlength="200"
            :aria-invalid="!!alanHatalari.kaynak"
            :aria-describedby="alanHatalari.kaynak ? 'kk-kaynak-hata' : undefined"
          />
          <AlanHatasi id="kk-kaynak-hata" :metin="alanHatalari.kaynak" />
        </div>

        <div v-if="duzenleme" class="alan alan-zorunlu">
          <label for="kk-neden">Değişiklik nedeni (zorunlu)</label>
          <input
            id="kk-neden"
            v-model="neden"
            maxlength="500"
            required
            :aria-invalid="!!alanHatalari.neden"
            :aria-describedby="alanHatalari.neden ? 'kk-neden-hata' : undefined"
          />
          <AlanHatasi id="kk-neden-hata" :metin="alanHatalari.neden" />
        </div>
      </section>

      <section class="kk-bolum" aria-labelledby="kk-b-etki">
        <h3 id="kk-b-etki">5. Özet ve etki</h3>
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

        <div
          v-if="onayBekliyor && deneSonucu"
          ref="onayKutusu"
          class="bildirim bildirim-uyari"
          role="alert"
          tabindex="-1"
          data-test="kk-onay"
        >
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
      </section>
    </div>

    <template #eylemler>
      <Dugme
        varyant="birincil"
        tip="submit"
        :disabled="!gecerli || mesgul"
        :aria-describedby="kaydetPasifNedeni ? 'kk-kaydet-ipucu' : undefined"
      >
        Kaydet
      </Dugme>
      <Dugme :disabled="mesgul" @click="vazgec">İptal</Dugme>
    </template>
    <template v-if="kaydetPasifNedeni" #neden>
      <span id="kk-kaydet-ipucu">{{ kaydetPasifNedeni }}</span>
    </template>
  </Diyalog>
</template>

<style scoped>
.yardim {
  margin: 0;
  font-size: var(--yazi-sm);
}
.kk-zorunlu-not {
  margin-bottom: var(--aralik-4);
}
/* bölümler: numaralı başlık + ince ayraç; hepsi DOM'da (sekme değil) kalır, alanlar her zaman erişilebilir */
.kk-bolum {
  margin-bottom: var(--aralik-6);
  padding-top: var(--aralik-4);
  border-top: 1px solid var(--renk-cizgi);
}
.kk-bolum > h3 {
  margin: 0 0 var(--aralik-4);
  font-size: var(--yazi-lg);
  color: var(--renk-metin-ikincil);
}
.kosul {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--aralik-3);
}
.grup,
.alt-grup {
  border: 0;
  padding: 0;
  margin: 0 0 var(--aralik-4);
}
.grup > legend {
  font-weight: var(--yazi-agirlik-orta);
  font-size: var(--yazi-md);
  padding: 0;
  margin-bottom: var(--aralik-2);
}
.alt-grup {
  margin-bottom: var(--aralik-3);
}
.alt-grup > legend {
  font-size: var(--yazi-sm);
  padding: 0;
  color: var(--renk-metin-soluk);
}
.secenek {
  display: inline-flex;
  align-items: center;
  gap: var(--aralik-2);
  margin-right: var(--aralik-5);
}
.secenek-satir {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: var(--aralik-2);
  margin-bottom: var(--aralik-2);
}
.tarihler {
  display: flex;
  flex-wrap: wrap;
  gap: var(--aralik-4);
}
.dene-kutusu {
  margin-top: var(--aralik-4);
}
.ornekler {
  margin: 0;
  padding-left: var(--aralik-5);
}
.onay-eylem {
  display: flex;
  gap: var(--aralik-3);
}
</style>
