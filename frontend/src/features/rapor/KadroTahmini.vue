<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { ApiHata } from '@/api/istemci'
import { bugun } from '@/lib/tarih'
import { sayiBicimle } from '@/lib/sayi'
import { tanimApi, type TanimDto } from '@/features/tanim/tanimApi'
import SayfaBasligi from '@/bilesenler/SayfaBasligi.vue'
import Simge from '@/bilesenler/Simge.vue'
import VeriDurumu from '@/bilesenler/VeriDurumu.vue'
import DolulukEgrisi from './DolulukEgrisi.vue'
import { raporApi, type KadroIstegi, type KadroRaporu, type KadroSanalSefer } from './raporApi'

/**
 * Kadro ihtiyacı tahmini (F3a, yalnız yönetici): "bu tarife ve bu kurallarla kaç makinist, hangi gar/bröve dağılımıyla" ve "yeni sefer açılırsa kaç ek makinist".
 * Başlık: mevcut kadroyla doluluk + %98 / %99 / %100 için gereken ek (genel) makinist (doluluk eğrisi, süreden bağımsız, onlarca saniye sürebilir). Süre sınırı yalnız ek hesaplar içindir. İptal sonucu yok sayar (sunucu hesabı bitirir, veri yazmaz; ikinci hesap bitene dek 429 alır).
 */
const GUNLER = [
  ['MONDAY', 'Pzt'],
  ['TUESDAY', 'Sal'],
  ['WEDNESDAY', 'Çar'],
  ['THURSDAY', 'Per'],
  ['FRIDAY', 'Cum'],
  ['SATURDAY', 'Cmt'],
  ['SUNDAY', 'Paz'],
] as const

interface SatirFormu {
  hatId: number | null
  aracTipiId: number | null
  kalkisGarId: number | null
  varisGarId: number | null
  saatler: string
  sureDk: number | null
  gunler: boolean[]
  km: string
  trenTuru: '' | 'YUK' | 'YOLCU'
}
type SatirAlani = 'hat' | 'arac' | 'kalkis' | 'varis' | 'saat' | 'sure'

const bas = ref(bugun())
const hafta = ref(4)
const devamsizlik = ref(13)
const yedekDahil = ref(true)
const yalnizAnalitik = ref(false)
const yolculuk = ref(false)
const sureSn = ref(25)
const satirlar = ref<SatirFormu[]>([])
const hatlar = ref<TanimDto[]>([])
const aracTipleri = ref<TanimDto[]>([])
const garlar = ref<TanimDto[]>([])

const rapor = ref<KadroRaporu | null>(null)
const hata = ref('')
/** Sanal sefer satırı doğrulama hatası: metin satırın altında, geçersiz alanlar `aria-invalid`. */
const satirHatasi = ref<{ i: number; metin: string; alanlar: SatirAlani[] } | null>(null)
const mesgul = ref(false)
const gecen = ref(0)
const senaryoKap = ref<HTMLElement | null>(null)
let istekNo = 0
let sayac: ReturnType<typeof setInterval> | null = null

function sayaciBaslat() {
  gecen.value = 0
  sayaciDurdur()
  sayac = setInterval(() => gecen.value++, 1000)
}
function sayaciDurdur() {
  if (sayac) clearInterval(sayac)
  sayac = null
}
onBeforeUnmount(sayaciDurdur)

onMounted(async () => {
  try {
    ;[hatlar.value, aracTipleri.value, garlar.value] = await Promise.all([
      tanimApi.liste('HAT'),
      tanimApi.liste('ARAC_TIPI'),
      tanimApi.liste('GAR'),
    ])
  } catch {
    // tanımlar yüklenemezse yalnız sanal sefer satırı eklenemez; hesap yine çalışır
  }
})

function satirEkle() {
  if (satirlar.value.length >= 10) return
  satirlar.value.push({
    hatId: null,
    aracTipiId: null,
    kalkisGarId: null,
    varisGarId: null,
    saatler: '',
    sureDk: null,
    gunler: GUNLER.map(() => false),
    km: '',
    trenTuru: '',
  })
}

function satirSil(i: number) {
  satirlar.value.splice(i, 1)
  satirHatasi.value = null
}

/** "06:00, 14:30" -> ["06:00", "14:30"]; geçersiz parça varsa null. */
function saatleriOku(metin: string): string[] | null {
  const parcalar = metin.split(/[\s,;]+/).filter((p) => p !== '')
  if (parcalar.length === 0 || parcalar.some((p) => !/^([01]?\d|2[0-3]):[0-5]\d$/.test(p)))
    return null
  return parcalar.map((p) => (p.length === 4 ? `0${p}` : p))
}

const gecersiz = (i: number, alan: SatirAlani) =>
  satirHatasi.value?.i === i && satirHatasi.value.alanlar.includes(alan) ? 'true' : undefined
const satirHataKimligi = (i: number) => `kt-satir-hata-${i + 1}`
const tanim = (i: number, alan: SatirAlani) => (gecersiz(i, alan) ? satirHataKimligi(i) : undefined)

/** Hata sonrası odak ilk geçersiz alana gider. */
async function ilkHataliyaOdaklan() {
  await nextTick()
  senaryoKap.value?.querySelector<HTMLElement>('[aria-invalid="true"]')?.focus()
}

function sanalSeferler(): KadroSanalSefer[] | null {
  const sonuc: KadroSanalSefer[] = []
  for (const [i, s] of satirlar.value.entries()) {
    const saatler = saatleriOku(s.saatler)
    const alanlar: SatirAlani[] = []
    if (s.hatId == null) alanlar.push('hat')
    if (s.aracTipiId == null) alanlar.push('arac')
    if (s.kalkisGarId == null) alanlar.push('kalkis')
    if (s.varisGarId == null) alanlar.push('varis')
    if (saatler == null) alanlar.push('saat')
    if (s.sureDk == null) alanlar.push('sure')
    if (alanlar.length > 0 || saatler == null) {
      satirHatasi.value = {
        i,
        alanlar,
        metin: `Sanal sefer satırı ${i + 1}: hat, araç tipi, garlar, süre ve geçerli kalkış saatleri (SS:DD) gerekli.`,
      }
      void ilkHataliyaOdaklan()
      return null
    }
    const secili = GUNLER.filter((_, k) => s.gunler[k]).map(([ad]) => ad)
    sonuc.push({
      hatId: s.hatId!,
      aracTipiId: s.aracTipiId!,
      kalkisGarId: s.kalkisGarId!,
      varisGarId: s.varisGarId!,
      saatler,
      sureDk: s.sureDk!,
      ...(secili.length > 0 && secili.length < 7 ? { gunler: [...secili] } : {}),
      ...(s.km !== '' ? { km: Number(s.km) } : {}),
      ...(s.trenTuru !== '' ? { trenTuru: s.trenTuru } : {}),
    })
  }
  return sonuc
}

async function hesapla() {
  if (mesgul.value) return // çift tıklama kapısı
  hata.value = ''
  satirHatasi.value = null
  const seferler = sanalSeferler()
  if (seferler == null) return
  const g: KadroIstegi = {
    bas: bas.value,
    hafta: hafta.value,
    devamsizlikYuzde: devamsizlik.value,
    yedekDahil: yedekDahil.value,
    yalnizAnalitik: yalnizAnalitik.value,
    sureSn: sureSn.value,
    yolculuk: yolculuk.value,
    seferler,
  }
  const no = ++istekNo
  mesgul.value = true
  sayaciBaslat()
  rapor.value = null
  try {
    const r = await raporApi.kadro(g)
    if (no === istekNo) rapor.value = r
  } catch (e) {
    if (no === istekNo) hata.value = e instanceof ApiHata ? e.detail : 'Hesap yapılamadı.'
  } finally {
    if (no === istekNo) {
      mesgul.value = false
      sayaciDurdur()
    }
  }
}

/** İptal: sonucu yok sayar (sunucu en geç süre bütçesi kadar sonra biter; veri yazmaz). */
function iptal() {
  istekNo++
  mesgul.value = false
  sayaciDurdur()
}

async function excelIndir() {
  if (!rapor.value) return
  hata.value = ''
  try {
    const { blob, ad } = await raporApi.kadroExcel(rapor.value)
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = ad ?? `kadro-ihtiyaci-${rapor.value.varsayim.bas}.xlsx`
    a.click()
    URL.revokeObjectURL(url)
  } catch (e) {
    hata.value = e instanceof ApiHata ? e.detail : 'Excel indirilemedi.'
  }
}

const sim = computed(() => rapor.value?.simulasyon ?? null)
/** Süre bütçesi yalnız EK hesapları etkiler (iyileştirmeli/yolculuklu plan, senaryo eğri farkı); doluluk eğrisi süreden bağımsızdır. */
const ekHesapAtlandi = computed(
  () => !!sim.value && (!sim.value.tamamlandi || rapor.value?.senaryo?.tamamlandi === false),
)
const durumEtiketi = { BOL: 'Bol', NORMAL: 'Normal', SIKISIK: 'Sıkışık' } as const
const sn = (ms: number) => sayiBicimle(ms / 1000)
const yuz = (n: number) => `%${sayiBicimle(n)}`
const enIyiNokta = computed(() => sim.value?.egri[sim.value.egri.length - 1] ?? null)
</script>

<template>
  <section class="kadro">
    <SayfaBasligi baslik="Kadro ihtiyacı tahmini">
      <template #sayaclar>
        <p class="soluk kadro-aciklama">
          Bu tarife ve kurallarla kaç makinist gerektiğini, hangi garda ve bröve dağılımıyla
          gerektiğini hesaplar; isteğe bağlı olarak yeni sefer açılırsa kaç ek makinist gerektiğini
          gösterir. Sanal makinistler ve seferler yalnız bellekte tutulur, veri değişmez.
        </p>
      </template>
    </SayfaBasligi>

    <form class="arac-cubugu" @submit.prevent="hesapla">
      <div class="alan alan-zorunlu">
        <label for="kt-bas">Başlangıç</label>
        <input id="kt-bas" v-model="bas" type="date" required />
      </div>
      <div class="alan alan-zorunlu">
        <label for="kt-hafta">Ufuk (hafta)</label>
        <input id="kt-hafta" v-model.number="hafta" type="number" min="1" max="8" required />
      </div>
      <div class="alan alan-zorunlu">
        <label for="kt-devamsizlik">Devamsızlık (%)</label>
        <input
          id="kt-devamsizlik"
          v-model.number="devamsizlik"
          type="number"
          min="0"
          max="50"
          required
        />
      </div>
      <div class="alan alan-zorunlu">
        <label
          for="kt-sure"
          title="Yalnız iyileştirmeli/yolculuklu plan ve senaryo eğri farkı için; doluluk eğrisi bu süreden bağımsızdır"
          >Ek hesap süresi (sn)</label
        >
        <input id="kt-sure" v-model.number="sureSn" type="number" min="5" max="30" required />
      </div>
      <div class="alan onaylar">
        <label for="kt-yedek"
          ><input id="kt-yedek" v-model="yedekDahil" type="checkbox" /> Yedek ihtiyacını
          göster</label
        >
        <label for="kt-analitik"
          ><input id="kt-analitik" v-model="yalnizAnalitik" type="checkbox" /> Yalnız analitik
          (hızlı, simülasyonsuz)</label
        >
        <label for="kt-yolculuk"
          ><input id="kt-yolculuk" v-model="yolculuk" type="checkbox" :disabled="yalnizAnalitik" />
          Yolculuklu satırı da hesapla (daha yavaş)</label
        >
      </div>
    </form>

    <fieldset ref="senaryoKap" class="sanal">
      <legend>Yeni sefer senaryosu (isteğe bağlı, "ne olurdu")</legend>
      <p v-if="satirlar.length === 0" class="soluk">
        Sanal sefer eklemezseniz yalnız mevcut tarife hesaplanır.
      </p>
      <template v-for="(s, i) in satirlar" :key="i">
        <div class="sanal-satir" data-test="sanal-satir">
          <select
            v-model="s.hatId"
            :aria-label="`Hat ${i + 1}`"
            :aria-invalid="gecersiz(i, 'hat')"
            :aria-describedby="tanim(i, 'hat')"
          >
            <option :value="null">Hat</option>
            <option v-for="h in hatlar" :key="h.id" :value="h.id">{{ h.ad }}</option>
          </select>
          <select
            v-model="s.aracTipiId"
            :aria-label="`Araç tipi ${i + 1}`"
            :aria-invalid="gecersiz(i, 'arac')"
            :aria-describedby="tanim(i, 'arac')"
          >
            <option :value="null">Araç tipi</option>
            <option v-for="a in aracTipleri" :key="a.id" :value="a.id">{{ a.ad }}</option>
          </select>
          <select
            v-model="s.kalkisGarId"
            :aria-label="`Kalkış garı ${i + 1}`"
            :aria-invalid="gecersiz(i, 'kalkis')"
            :aria-describedby="tanim(i, 'kalkis')"
          >
            <option :value="null">Kalkış garı</option>
            <option v-for="g in garlar" :key="g.id" :value="g.id">{{ g.ad }}</option>
          </select>
          <select
            v-model="s.varisGarId"
            :aria-label="`Varış garı ${i + 1}`"
            :aria-invalid="gecersiz(i, 'varis')"
            :aria-describedby="tanim(i, 'varis')"
          >
            <option :value="null">Varış garı</option>
            <option v-for="g in garlar" :key="g.id" :value="g.id">{{ g.ad }}</option>
          </select>
          <input
            v-model="s.saatler"
            :aria-label="`Kalkış saatleri ${i + 1}`"
            :aria-invalid="gecersiz(i, 'saat')"
            :aria-describedby="tanim(i, 'saat')"
            placeholder="06:00, 14:00"
            size="12"
          />
          <input
            v-model.number="s.sureDk"
            :aria-label="`Süre dakika ${i + 1}`"
            :aria-invalid="gecersiz(i, 'sure')"
            :aria-describedby="tanim(i, 'sure')"
            type="number"
            min="1"
            max="1440"
            placeholder="Süre dk"
          />
          <span class="gunler">
            <label v-for="([ad, kisa], k) in GUNLER" :key="ad" :title="ad">
              <input v-model="s.gunler[k]" type="checkbox" :aria-label="`${kisa} ${i + 1}`" />{{
                kisa
              }}
            </label>
          </span>
          <button type="button" class="btn" :aria-label="`Satır ${i + 1} sil`" @click="satirSil(i)">
            Sil
          </button>
        </div>
        <p
          v-if="satirHatasi?.i === i"
          :id="satirHataKimligi(i)"
          class="alan-hata sanal-hata"
          role="alert"
          data-test="satir-hata"
        >
          {{ satirHatasi.metin }}
        </p>
      </template>
      <button
        type="button"
        class="btn"
        :disabled="satirlar.length >= 10"
        data-test="satir-ekle"
        @click="satirEkle"
      >
        Sanal sefer satırı ekle
      </button>
      <p class="alan-yardim">
        Hat, araç tipi, iki gar, kalkış saatleri (SS:DD, virgülle ayrılır) ve süre zorunludur. Gün
        seçilmezse her gün; en çok 10 satır.
      </p>
    </fieldset>

    <div class="eylemler">
      <button
        type="button"
        class="btn btn-birincil"
        :disabled="mesgul"
        data-test="hesapla"
        @click="hesapla"
      >
        Hesapla
      </button>
      <button v-if="mesgul" type="button" class="btn" data-test="iptal" @click="iptal">
        İptal
      </button>
      <button
        v-if="rapor"
        type="button"
        class="btn"
        data-test="excel"
        :disabled="mesgul"
        @click="excelIndir"
      >
        Excel indir
      </button>
    </div>

    <VeriDurumu
      v-if="mesgul"
      durum="yukleniyor"
      metin="Hesaplanıyor… (doluluk eğrisi için onlarca saniye sürebilir). Beklemek istemezseniz İptal'e basabilirsiniz."
      data-test="hesaplaniyor"
    >
      <template #eylem>
        <p class="soluk gecen-sure" aria-hidden="true" data-test="gecen-sure">
          Geçen süre: {{ gecen }} sn
        </p>
      </template>
    </VeriDurumu>
    <VeriDurumu v-else-if="hata && !rapor" durum="hata" :metin="hata" />
    <p v-else-if="hata" class="bildirim bildirim-hata" role="alert">{{ hata }}</p>
    <VeriDurumu
      v-else-if="!rapor"
      durum="bos"
      metin="Henüz hesap yapılmadı."
      ayrinti="Parametreleri seçip Hesapla'ya basın; sonuç burada görünür."
      data-test="kadro-bos"
    />

    <template v-if="rapor">
      <div v-if="sim" class="karar" data-test="baslik">
        <div class="karar-izgara">
          <article class="ozet-kart" data-test="mevcut-doluluk">
            <h2 class="ozet-etiket"><Simge ad="kadro" boyut="1.1em" />Mevcut doluluk</h2>
            <p class="ozet-deger">
              <strong data-test="mevcut-yuzde">{{ yuz(sim.mevcut.dolulukYuzde) }}</strong>
            </p>
            <p class="karar-altyazi">
              Mevcut <strong class="sayisal">{{ sim.mevcutAsil }}</strong> makinistle (%{{
                rapor.varsayim.devamsizlikYuzde
              }}
              devamsızlık varsayımıyla)
              <span class="soluk sayisal"
                >{{ sim.mevcut.bos }} boş sefer / {{ sim.mevcut.seferSayisi }}</span
              >
            </p>
          </article>

          <article
            v-for="h in sim.hedefler"
            :key="h.yuzde"
            class="ozet-kart"
            :class="{ 'ozet-uyari': !h.ulasildi }"
            :data-test="`hedef-${h.yuzde}`"
          >
            <h2 class="ozet-etiket">
              <Simge :ad="h.ulasildi ? 'basari' : 'uyari'" boyut="1.1em" />{{ yuz(h.yuzde) }}
              doluluk için ek makinist
            </h2>
            <template v-if="h.ulasildi">
              <p class="ozet-deger" :class="{ 'karar-metin': h.ek === 0 }">
                {{ h.ek === 0 ? 'ek gerekmiyor' : `+${h.ek}` }}
              </p>
              <p class="karar-altyazi soluk sayisal">Kalan boş: {{ h.bos }}</p>
            </template>
            <template v-else>
              <p class="ozet-deger karar-metin">Ulaşılamadı</p>
              <p class="karar-altyazi soluk sayisal">
                En iyi: {{ yuz(h.dolulukYuzde) }}, {{ h.bos }} boş; en çok +{{ enIyiNokta?.ek }}
                denendi; nedenler aşağıda
              </p>
            </template>
          </article>
        </div>

        <p class="soluk" data-test="baslik-varsayim">
          Bu sayı temiz sayfadan, devamsızlıklı bir plandır; masadaki gerçek plan mevcut atamalarla
          ve gerçek izinlerle doldurulduğu için doluluğu bundan farklı çıkar.
        </p>
        <p class="soluk" data-test="pencere-notu">
          Sonuç seçilen {{ rapor.varsayim.hafta }} haftalık pencereye ({{ rapor.varsayim.bas }} –
          {{ rapor.varsayim.bit }}) bağlıdır; başka bir başlangıç tarihi ya da ufukta değişebilir.
          Ek makinist sayıları, tarifedeki tüm bröveleri taşıyan GENEL makinist varsayımıyla kişi
          sayısıdır; hangi bröve kümelerine gidecekleri aşağıdaki dağılım tablosundadır. Plan,
          iyileştirmesiz atamadır (atamacının "daha çok sefer doldur" seçeneği ek kazanç
          sağlayabilir).
        </p>
        <ul class="alt-degerler" data-test="alt-degerler">
          <li v-if="sim.konumsuz">
            Aynı kadro, konum kuralı (K-20) kapalı:
            <strong class="sayisal">{{ yuz(sim.konumsuz.dolulukYuzde) }}</strong>
            <span class="soluk">
              ({{ sim.konumsuz.bos }} boş): aradaki fark işe alım değil, konum / yerleşim
              kaynaklıdır (yolculuk, yer değiştirme ya da takasla çözülebilir)</span
            >
          </li>
          <li v-if="sim.iyilestirmeli">
            "Daha çok sefer doldur" ile mevcut kadro:
            <strong class="sayisal">{{ yuz(sim.iyilestirmeli.dolulukYuzde) }}</strong>
            <span class="soluk"> ({{ sim.iyilestirmeli.bos }} boş)</span>
          </li>
          <li v-if="sim.yolculuk">
            Yolculuk aşamasıyla mevcut kadro:
            <strong class="sayisal">{{ yuz(sim.yolculuk.dolulukYuzde) }}</strong>
            <span class="soluk"> ({{ sim.yolculuk.bos }} boş)</span>
          </li>
        </ul>
        <p class="soluk">
          Hesap süresi {{ sn(sim.sureMs) }} sn ({{ sim.egriKosuSayisi }} plan koşusu).
        </p>
        <p v-if="ekHesapAtlandi" class="kadro-not" data-test="sure-uyarisi">
          <Simge ad="bilgi" boyut="1.1em" />
          <span>
            Süre sınırı nedeniyle bazı EK hesaplar yapılmadı
            <template v-if="sim.atlananlar.length > 0">({{ sim.atlananlar.join('; ') }})</template>.
            Doluluk eğrisi süre sınırından etkilenmez, yukarıdaki sonuç geçerlidir. İsterseniz "Ek
            hesap süresi"ni yükseltip yeniden hesaplayın.
          </span>
        </p>
      </div>

      <div v-if="sim" class="kart" data-test="egri">
        <h2>Doluluk eğrisi (ek genel makinist sayısına göre)</h2>
        <DolulukEgrisi :noktalar="sim.egri" :hedefler="sim.hedefler" />
        <p class="soluk">
          Her satır, o kadar ek makinistle bulunan en az boş sayısıdır (kurallar tekdüze
          olmadığından "en az makinist" kanıtlanmaz). Eklenen kişi başına kapanan sefer hızla
          azalır: son yüzdeler çok pahalıdır.
        </p>
      </div>

      <div v-if="rapor.senaryo" class="kart" data-test="senaryo">
        <h2>Yeni sefer senaryosu</h2>
        <p>
          {{ rapor.senaryo.ekSeferSayisi }} ek sefer (+{{
            sayiBicimle(rapor.senaryo.ekHaftalikSaat)
          }}
          sa/hafta), ek talep {{ sayiBicimle(rapor.senaryo.ekTalepKisiEsdegeri) }} kişi-eşdeğeri.
          <span v-if="rapor.senaryo.taban && rapor.senaryo.senaryo">
            Aynı kadroyla toplam boş farkı:
            <strong data-test="senaryo-fark">{{ rapor.senaryo.bosFarki }}</strong>
            ({{ rapor.senaryo.taban.bos }} → {{ rapor.senaryo.senaryo.bos }}; doluluk
            {{ yuz(rapor.senaryo.taban.dolulukYuzde) }} →
            {{ yuz(rapor.senaryo.senaryo.dolulukYuzde) }}).
          </span>
        </p>
        <p data-test="senaryo-gerekce">{{ rapor.senaryo.gerekce }}</p>
        <div v-if="rapor.senaryo.egriFarki" class="tablo-kap">
          <table class="tablo" aria-label="Eğri farkı" data-test="senaryo-egri">
            <thead>
              <tr>
                <th>Doluluk hedefi</th>
                <th class="sayisal">Taban (ek makinist)</th>
                <th class="sayisal">Senaryo (ek makinist)</th>
                <th class="sayisal">Fark</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="h in rapor.senaryo.egriFarki" :key="h.yuzde">
                <td>{{ yuz(h.yuzde) }}</td>
                <td class="sayisal">{{ h.tabanEk ?? 'ulaşılamadı' }}</td>
                <td class="sayisal">{{ h.senaryoEk ?? 'ulaşılamadı' }}</td>
                <td class="sayisal">
                  {{ h.fark == null ? '—' : `${h.fark > 0 ? '+' : ''}${h.fark}` }}
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <p v-if="rapor.senaryo.uyari" class="bildirim bildirim-uyari">{{ rapor.senaryo.uyari }}</p>
      </div>

      <details v-if="sim && sim.dagilim.length > 0" class="ayrinti" open data-test="dagilim">
        <summary><h2>Eklenen makinistlerin dağılımı</h2></summary>
        <p class="soluk">{{ sim.dagilimEtiketi }}; plandaki baskın kümelerine göre.</p>
        <div class="tablo-kap">
          <table class="tablo" aria-label="Eklenen makinist dağılımı">
            <thead>
              <tr>
                <th>Bağlı gar</th>
                <th>Araç tipi</th>
                <th>Hat</th>
                <th class="sayisal">Kişi</th>
                <th class="sayisal">Sefer</th>
                <th class="sayisal">Haftalık saat</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(d, i) in sim.dagilim" :key="i">
                <td>{{ d.gar }}</td>
                <td>{{ d.aracTipi }}</td>
                <td>{{ d.hat }}</td>
                <td class="sayisal">{{ d.kisi }}</td>
                <td class="sayisal">{{ d.seferSayisi }}</td>
                <td class="sayisal">{{ sayiBicimle(d.haftalikSaat) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </details>

      <details class="ayrinti" data-test="analitik">
        <summary><h2>Analitik alt sınır (saat ve yetki)</h2></summary>
        <p>
          Gerekli kadro (her bröveyle): <strong>{{ rapor.analitik.gerekliAltSinir }}</strong> · tek
          bröveli sanal makinistle: <strong>{{ rapor.analitik.tekBroveliGerekli }}</strong> ·
          mevcut: <strong>{{ rapor.analitik.mevcutAsil }}</strong> · mevcut kadroya eksik (alt
          sınır):
          <strong>{{ rapor.analitik.eksikAltSinir }}</strong>
        </p>
        <p class="soluk">
          Kişi başı haftalık {{ sayiBicimle(rapor.varsayim.kisiBasinaSaat) }} saat (devamsızlık %{{
            rapor.varsayim.devamsizlikYuzde
          }}
          dahil); haftalık talep {{ sayiBicimle(rapor.varsayim.haftalikTalepSaat) }} saat. Konum,
          dinlenme ve nöbet kuralları bu hesapta yoktur; karar için yukarıdaki doluluk eğrisi
          esastır.
        </p>
      </details>

      <div v-if="rapor.yedek" class="kart" data-test="yedek">
        <p>
          Yedek (asıl kadrodan ayrı, operasyonel tampon): günlük ihtiyaç
          <strong>{{ rapor.yedek.ihtiyac }}</strong>
          <span class="soluk">
            (oran {{ sayiBicimle(rapor.varsayim.yedekOran) }}, en az {{ rapor.varsayim.yedekMin }};
            kaynaksız yer tutucu; devamsızlık yalnız yukarıdaki simülasyonda sayılır)</span
          >
        </p>
      </div>

      <details class="ayrinti">
        <summary><h2>Bröve kümeleri (mevcut kadro)</h2></summary>
        <p class="soluk">
          Kullanım,
          {{
            rapor.kumeKaynagi === 'PLAN'
              ? 'mevcut kadronun simülasyon planında kişilerin işlediği saatten'
              : 'analitik akış tahsisinden (yalnız analitik istek; gerçek plan değildir)'
          }}
          hesaplanır.
        </p>
        <div class="tablo-kap">
          <table class="tablo" aria-label="Bröve kümeleri">
            <thead>
              <tr>
                <th>Araç tipleri</th>
                <th>Hatlar</th>
                <th class="sayisal">Kişi</th>
                <th class="sayisal">Kullanılan sa/hafta</th>
                <th class="sayisal">Kullanım %</th>
                <th>Durum</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(k, i) in rapor.kumeler" :key="i">
                <td>{{ k.aracTipleri.join(', ') }}</td>
                <td>{{ k.hatlar.join(', ') }}</td>
                <td class="sayisal">{{ k.mevcut }}</td>
                <td class="sayisal">{{ sayiBicimle(k.kullanilanSaat) }}</td>
                <td class="sayisal">{{ sayiBicimle(k.kullanimYuzde) }}</td>
                <td>{{ durumEtiketi[k.durum] }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </details>

      <details class="ayrinti">
        <summary><h2>Garlar</h2></summary>
        <div class="tablo-kap">
          <table class="tablo" aria-label="Garlar">
            <thead>
              <tr>
                <th>Gar</th>
                <th class="sayisal">Haftalık talep (sa)</th>
                <th class="sayisal">Mevcut makinist</th>
                <th class="sayisal">Eklenen (dağılım noktası)</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="g in rapor.garlar" :key="g.gar">
                <td>{{ g.gar }}</td>
                <td class="sayisal">{{ sayiBicimle(g.haftalikTalepSaat) }}</td>
                <td class="sayisal">{{ g.mevcutMakinist }}</td>
                <td class="sayisal">{{ g.eklenen }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </details>

      <details class="ayrinti">
        <summary><h2>Talep (hat × araç tipi)</h2></summary>
        <div class="tablo-kap">
          <table class="tablo" aria-label="Talep">
            <thead>
              <tr>
                <th>Hat</th>
                <th>Araç tipi</th>
                <th class="sayisal">Sefer</th>
                <th class="sayisal">Haftalık saat</th>
                <th class="sayisal">Gerekli kişi</th>
                <th class="sayisal">Yetkili</th>
                <th class="sayisal">Eksik kişi</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(t, i) in rapor.talep" :key="i">
                <td>{{ t.hat }}</td>
                <td>{{ t.aracTipi }}</td>
                <td class="sayisal">{{ t.seferSayisi }}</td>
                <td class="sayisal">{{ sayiBicimle(t.haftalikSaat) }}</td>
                <td class="sayisal">{{ rapor.analitik.siniflar[i]?.gerekliKisi }}</td>
                <td class="sayisal">{{ rapor.analitik.siniflar[i]?.mevcutYetkili }}</td>
                <td class="sayisal">{{ rapor.analitik.siniflar[i]?.eksikKisi }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </details>

      <details v-if="sim" class="ayrinti">
        <summary>
          <h2>Kalan boş seferler ({{ sim.kalanBosToplam }})</h2>
        </summary>
        <p class="soluk" data-test="kalan-aciklama">
          Eğrinin en iyi noktasında (+{{ enIyiNokta?.ek }} makinist) kalan boşlar.
        </p>
        <p v-if="sim.kalanBosToplam === 0" class="soluk">Boş kalan sefer yok.</p>
        <template v-else>
          <ul v-if="sim.kalanNedenler.length > 0" class="alt-degerler" data-test="kalan-nedenler">
            <li v-for="n in sim.kalanNedenler" :key="n.neden">
              {{ n.neden }}: <strong class="sayisal">{{ n.adet }}</strong>
            </li>
          </ul>
          <div class="tablo-kap otomatik-kaydir">
            <table class="tablo" aria-label="Kalan boş seferler">
              <thead>
                <tr>
                  <th>Tarih</th>
                  <th>Tren no</th>
                  <th>Güzergâh</th>
                  <th>Neden</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="b in sim.kalanBoslar" :key="b.seferId">
                  <td>{{ b.tarih }}</td>
                  <td>{{ b.trenNo }}</td>
                  <td>{{ b.kalkisGar }} – {{ b.varisGar }}</td>
                  <td>{{ b.neden }}</td>
                </tr>
              </tbody>
            </table>
          </div>
          <p v-if="sim.kalanBosToplam > sim.kalanBoslar.length" class="soluk">
            İlk {{ sim.kalanBoslar.length }} sefer gösteriliyor.
          </p>
        </template>
      </details>
    </template>
  </section>
</template>

<style scoped>
.kadro {
  display: grid;
  gap: var(--aralik-4);
  align-content: start;
}
.kadro :deep(.sayfa-baslik) {
  margin-bottom: 0;
}
.kadro-aciklama {
  margin: 0;
  max-width: 60rem;
}
.kadro .arac-cubugu {
  margin-bottom: 0;
}
.kadro .alan {
  margin-bottom: 0;
}
.onaylar label {
  font-weight: var(--yazi-agirlik-normal);
}
.karar {
  display: grid;
  gap: var(--aralik-3);
}
.karar > p,
.karar > ul {
  margin: 0;
}
.karar-izgara {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(14rem, 1fr));
  gap: var(--aralik-4);
}
.karar-izgara .ozet-kart h2,
.karar-izgara .ozet-kart p {
  margin: 0;
}
.ozet-etiket {
  display: flex;
  align-items: center;
  gap: var(--aralik-3);
  font-size: var(--yazi-md);
}
.karar-metin {
  font-size: var(--yazi-xl);
  line-height: var(--satir-baslik);
}
.kadro-not {
  display: flex;
  align-items: flex-start;
  gap: var(--aralik-3);
  font-size: var(--yazi-sm);
  color: var(--renk-metin-ikincil);
}
.karar-altyazi {
  font-size: var(--yazi-sm);
}
.gecen-sure {
  margin: 0;
  font-variant-numeric: tabular-nums;
}
.alt-degerler {
  margin: var(--aralik-2) 0;
  padding-left: 1.1rem;
}
.sanal {
  display: grid;
  gap: var(--aralik-3);
  margin: 0;
  border: 1px solid var(--renk-cizgi);
  border-radius: var(--yaricap);
  background: var(--renk-yuzey);
}
.sanal > button {
  justify-self: start;
}
.sanal > p {
  margin: 0;
}
.sanal-satir {
  display: flex;
  flex-wrap: wrap;
  gap: var(--aralik-3);
  align-items: center;
}
.sanal-hata {
  margin-top: calc(-1 * var(--aralik-2));
}
.gunler {
  display: inline-flex;
  gap: var(--aralik-3);
  font-size: var(--yazi-sm);
}
.ayrinti {
  background: var(--renk-yuzey);
  border: 1px solid var(--renk-cizgi);
  border-radius: var(--yaricap);
  padding: var(--aralik-3) var(--aralik-5);
}
.ayrinti[open] > summary {
  margin-bottom: var(--aralik-3);
}
.ayrinti > summary {
  cursor: pointer;
}
.ayrinti > summary h2 {
  display: inline;
  margin: 0;
  font-size: var(--yazi-xl);
}
.ayrinti > p {
  margin: 0 0 var(--aralik-3);
}
.kadro .kart {
  margin: 0;
}
.kadro .kart h2 {
  margin-bottom: var(--aralik-3);
}
.otomatik-kaydir {
  max-height: 400px;
  overflow: auto;
}
</style>
