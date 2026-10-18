<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { kaydirmaDavranisi } from '@/lib/odak'
import { bugun, gunKaydir, saatBicimle, tarihBicimle } from '@/lib/tarih'
import { trenAdi } from '@/lib/trenAdi'
import { durumMetni, gecikmeMetni } from '@/features/yazdir/durumMetni'
import IntikalSeridi from '@/features/intikal/IntikalSeridi.vue'
import Dugme from '@/bilesenler/Dugme.vue'
import Rozet from '@/bilesenler/Rozet.vue'
import SayfaBasligi from '@/bilesenler/SayfaBasligi.vue'
import Simge from '@/bilesenler/Simge.vue'
import VeriDurumu from '@/bilesenler/VeriDurumu.vue'
import { ROL_METINLERI } from '@/bilesenler/durum'
import AdayPaneli from './AdayPaneli.vue'
import AtamaDialog from './AtamaDialog.vue'
import NobetNotu from './NobetNotu.vue'
import OtomatikAtamaDialog from './OtomatikAtamaDialog.vue'
import SeferBirlestirDialog from './SeferBirlestirDialog.vue'
import SeferBolDialog from './SeferBolDialog.vue'
import SeferDegisiklikDialog from './SeferDegisiklikDialog.vue'
import UygunSeferlerDialog from './UygunSeferlerDialog.vue'
import YerineAtaDialog from './YerineAtaDialog.vue'
import YolculukOneriDialog from './YolculukOneriDialog.vue'
import { geceAyariYukle, geceMi } from './gece'
import { ROL_ONEKLERI } from './atamaApi'
import type { Aday, AtamaRolu, GunMakinisti, GunSatiri, Oneri, UygunSefer } from './atamaApi'
import { useYoklama } from '@/features/benim/useYoklama'
import { useAtamaMasasi } from './useAtamaMasasi'

const route = useRoute()
const router = useRouter()

const TARIH_DESENI = /^\d{4}-\d{2}-\d{2}$/
const tarih = computed(() => {
  const t = route.query.tarih
  return typeof t === 'string' && TARIH_DESENI.test(t) ? t : bugun()
})

const masa = useAtamaMasasi(tarih)
const { satirlar, seciliId, seciliSatir, rol, adaylar } = masa
void masa.gunuYukle()
void masa.kirmiziYukle()
void masa.gordumYukle()
void masa.takasYukle()
void geceAyariYukle() // gece göstergesi ayardaki pencereyi (gece.baslangic/bitis) izler
// Makinistler "gördüm" dedikçe ve takas talepleri geldikçe masa kendiliğinden güncellensin (60 sn; sekme gizliyken durur).
useYoklama(() => masa.sessizYenile())

function gunDegistir(t: string) {
  if (TARIH_DESENI.test(t)) void router.replace({ query: { ...route.query, tarih: t } })
}

const TEK_SUTUN = '(max-width: 1100px)' // stildeki medya sorgusuyla aynı olmalı
const liste = ref<HTMLElement | null>(null)
const panel = ref<HTMLElement | null>(null)

function gorunurKil(el: HTMLElement | null) {
  if (el && typeof el.scrollIntoView === 'function')
    el.scrollIntoView({ block: 'start', behavior: kaydirmaDavranisi() })
}

/** Tek sütunda aday paneli listenin altında kalır; sefer seçilince panele kaydır. */
async function seferSec(id: number) {
  masa.sec(id)
  if (!window.matchMedia?.(TEK_SUTUN).matches) return
  await nextTick()
  gorunurKil(panel.value)
}

/** QA BUG-2: seçili sefer varken aday panelinin başlığına odak (satırdan → ya da "Adaylara geç"); ~300 Tab gerekmez. */
const panelBasligi = ref<HTMLElement | null>(null)
async function adaylaraGec(id?: number) {
  if (id !== undefined && id !== seciliId.value) masa.sec(id)
  await nextTick()
  panelBasligi.value?.focus()
}

/** Kırmızı nedenler ve uyarılar; "nöbet" geçiyorsa masada tek açıklama notu gösterilir. */
const nobetMetinleri = computed(() =>
  satirlar.value.flatMap((s) =>
    s.makinistler.flatMap((m) => [...m.kirmiziNedenler, ...m.uyarilar]),
  ),
)

const guzergah = (s: GunSatiri) => `${s.kalkisGar} – ${s.varisGar}`
const seferEtiketi = (s: GunSatiri) => `${trenAdi(s)} ${guzergah(s)} ${saatBicimle(s.guncelKalkis)}`
const asil = (s: GunSatiri) => s.makinistler.find((m) => m.rol === 'ASIL')
/** Asıl önce, yardımcı, kondüktör, sonra yolcular. */
const ROL_SIRASI: Record<AtamaRolu, number> = { ASIL: 0, YARDIMCI: 1, KONDUKTOR: 2, YOLCU: 3 }
const sirali = (s: GunSatiri) =>
  [...s.makinistler].sort((a, b) => ROL_SIRASI[a.rol] - ROL_SIRASI[b.rol])
const kisi = (m: GunMakinisti) => `${m.ad} (${m.sicil})`
/** Kondüktörü atanmamış yolcu treni sayısı (F3b, MK-87; ayar kapalıyken sunucu hiçbirini işaretlemez): bilgi, kırmızı değil. */
const kondukturEksikSayisi = computed(() => satirlar.value.filter((s) => s.kondukturEksik).length)

/** Satır süzgeci (yalnız ön yüz, MK-139): sayaç düğmesi aç/kapa; aynı düğme tekrar basılınca süzgeç kalkar. */
type SatirSuzgeci = 'atanmamis' | 'kirmizi' | 'konduktor'
const suzgec = ref<SatirSuzgeci | null>(null)
const suzgecDugme = (k: SatirSuzgeci) => (suzgec.value = suzgec.value === k ? null : k)
const SUZGEC_KOSULU: Record<SatirSuzgeci, (s: GunSatiri) => boolean> = {
  atanmamis: (s) => s.durum !== 'IPTAL' && !asil(s),
  kirmizi: (s) => s.kirmizi,
  konduktor: (s) => s.durum !== 'IPTAL' && !!s.kondukturEksik,
}
const gorunenSatirlar = computed(() =>
  suzgec.value ? satirlar.value.filter(SUZGEC_KOSULU[suzgec.value]) : satirlar.value,
)
/** Satırda uyarı notu olan (kırmızı değil) görevli: sol şeritte uyarı rengi. */
const uyarili = (s: GunSatiri) => !s.kirmizi && s.makinistler.some((m) => m.uyarilar.length > 0)

/**
 * Satırlar tek Tab durağıdır (yaylanan odak): yalnız etkin satır ve eylem düğmeleri sekme sırasındadır, ↑/↓ satır
 * değiştirir. Etkin satır = odaktaki satır; yoksa seçili satır; yoksa ilk görünen (QA BUG-2: önce 100 satır × 3 durak).
 */
const aktifId = ref<number | null>(null)
const etkinSatirId = computed(() => {
  const g = gorunenSatirlar.value
  for (const aday of [aktifId.value, seciliId.value])
    if (g.some((s) => s.seferId === aday)) return aday
  return g[0]?.seferId ?? null
})
const tb = (s: GunSatiri) => (s.seferId === etkinSatirId.value ? 0 : -1)

/** Satırlar ↑/↓ ile gezilir (Enter seçer, → aday paneline geçer). */
function satirGez(e: KeyboardEvent, yon: 1 | -1) {
  const tr = e.currentTarget as HTMLElement
  const hedef = (
    yon === 1 ? tr.nextElementSibling : tr.previousElementSibling
  ) as HTMLElement | null
  if (hedef?.classList.contains('sefer-satiri')) hedef.focus()
}

/** "Böl": yalnız parçasız, planlı, kalkışı geçmemiş sefer (sunucu da denetler). */
const bolunebilir = (s: GunSatiri) =>
  s.durum === 'PLANLI' && s.parcaNo == null && new Date(s.guncelKalkis).getTime() > Date.now()

/** "Birleştir": yalnız parçalı, planlı sefer (koşulları sunucu denetler; gerekçe diyalogda gösterilir). */
const birlestirilebilir = (s: GunSatiri) => s.durum === 'PLANLI' && s.parcaNo != null

/** Atama onayı: sefer kimliği açıkça tutulur (Uygun seferler akışında seçili satırdan farklı olabilir). */
type DiyalogDurumu = {
  seferId: number
  etiket: string
  aday?: Aday
  mevcut: GunMakinisti | null
  rol: AtamaRolu
}
type DigerDiyalog =
  | { tur: 'degisiklik'; kip: 'iptal' | 'gecikme'; satir: GunSatiri }
  | { tur: 'yerine'; satir: GunSatiri }
  | { tur: 'bol'; satir: GunSatiri }
  | { tur: 'birlestir'; satir: GunSatiri }
  | { tur: 'uygun'; makinist: GunMakinisti }
  | { tur: 'otomatik' }
  | { tur: 'yolculuk'; satir: GunSatiri; aday: Aday }

const diyalog = ref<DiyalogDurumu | null>(null)
const diger = ref<DigerDiyalog | null>(null)
const bilgi = ref('')

// BUG-12: gün değişince (adres, geri tuşu, ok düğmeleri) önceki günün seferine ait açık diyalog kalmasın.
watch(tarih, () => {
  diyalog.value = null
  diger.value = null
  bilgi.value = ''
})

function adayAta(aday: Aday) {
  const s = seciliSatir.value
  if (!s) return
  bilgi.value = ''
  diyalog.value = {
    seferId: s.seferId,
    etiket: seferEtiketi(s),
    aday,
    rol: rol.value,
    // yolcuda "değiştir" yoktur: bir seferde birden çok yolcu olabilir (F2b)
    mevcut: rol.value === 'YOLCU' ? null : (s.makinistler.find((m) => m.rol === rol.value) ?? null),
  }
}

function kaldirAc(s: GunSatiri, m: GunMakinisti) {
  masa.sec(s.seferId)
  bilgi.value = ''
  diyalog.value = { seferId: s.seferId, etiket: seferEtiketi(s), mevcut: m, rol: m.rol }
}

function digerAc(d: DigerDiyalog) {
  bilgi.value = ''
  diger.value = d
}

/** Öneri → mevcut AtamaDialog: neden ve UYARI gerekçesi zorunlulukları aynen çalışır. */
function yerineSec(s: GunSatiri, o: Oneri) {
  diger.value = null
  diyalog.value = {
    seferId: s.seferId,
    etiket: seferEtiketi(s),
    rol: 'ASIL',
    mevcut: asil(s) ?? null,
    aday: {
      makinistId: o.makinistId,
      sicil: o.sicil,
      adSoyad: o.adSoyad,
      bagliGar: o.bagliGar,
      uygun: true,
      ihlaller: o.uyarilar,
      buAySaat: o.buAySaat,
      buAyKm: o.buAyKm,
      buSeferdeRol: null,
    },
  }
}

/** Kırmızı makinistin yeni seferi: aday olarak aynı makinist, sefer makinistsiz olduğundan "değiştir" yok. */
function uygunSec(m: GunMakinisti, u: UygunSefer) {
  diger.value = null
  diyalog.value = {
    seferId: u.seferId,
    etiket: `${u.trenNo} ${u.kalkisGar} – ${u.varisGar} ${saatBicimle(u.guncelKalkis)}`,
    rol: 'ASIL',
    mevcut: null,
    aday: {
      makinistId: m.makinistId,
      sicil: m.sicil,
      adSoyad: m.ad,
      bagliGar: '',
      uygun: true,
      ihlaller: u.uyarilar,
      buAySaat: 0,
      buAyKm: 0,
      buSeferdeRol: null,
    },
  }
}

async function tamam(mesaj: string) {
  diyalog.value = null
  diger.value = null
  bilgi.value = mesaj
  await masa.yenile()
}

async function kapat(yenile: boolean) {
  diyalog.value = null
  diger.value = null
  if (yenile) await masa.yenile()
}
</script>

<template>
  <section class="atama-masa" data-yogunluk="yogun">
    <SayfaBasligi baslik="Atama masası">
      <template #eylemler>
        <div class="arac-cubugu">
          <button type="button" class="btn" @click="gunDegistir(gunKaydir(tarih, -1))">
            <Simge ad="ok-sol" boyut="1.1em" />Önceki gün
          </button>
          <div class="alan gun-alani">
            <label for="atama-gun">Gün</label>
            <input
              id="atama-gun"
              type="date"
              :value="tarih"
              @change="gunDegistir(($event.target as HTMLInputElement).value)"
            />
          </div>
          <button type="button" class="btn" @click="gunDegistir(gunKaydir(tarih, 1))">
            Sonraki gün<Simge ad="ok-sag" boyut="1.1em" />
          </button>
        </div>
        <Dugme varyant="birincil" simge="kisi" @click="digerAc({ tur: 'otomatik' })">
          Otomatik ata
        </Dugme>
        <RouterLink
          class="btn"
          :to="{ name: 'yazdir-gun', params: { tarih } }"
          target="_blank"
          rel="noopener"
        >
          <Simge ad="yazdir" boyut="1.1em" />Yazdır
        </RouterLink>
      </template>
    </SayfaBasligi>

    <div class="sayac-seridi">
      <button
        v-if="masa.kirmizi.value"
        type="button"
        class="cip cip-engel"
        :class="{ dolu: masa.kirmizi.value.kirmiziSefer > 0 }"
        :aria-pressed="suzgec === 'kirmizi'"
        data-test="kirmizi-sayac"
        :title="`Seçili gün + sonraki 7 gün: ${masa.kirmizi.value.kirmiziSefer} sefer, ${masa.kirmizi.value.kirmiziAtama} atama kontrol bekliyor. Düğme: yalnız bu günün kırmızı seferlerini listeler.`"
        @click="suzgecDugme('kirmizi')"
      >
        <Simge ad="engel" boyut="1.1em" />Kırmızı:
        <strong>{{ masa.kirmizi.value.kirmiziSefer }}</strong> sefer
      </button>
      <p
        v-if="(masa.kirmizi.value?.kirmiziIntikal ?? 0) > 0"
        class="cip cip-engel dolu"
        data-test="kirmizi-intikal-sayac"
        title="Seçili gün + sonraki 7 gün: kural motoruna göre kontrol bekleyen serbest intikaller (atama sayacına dahil değildir)"
      >
        <Simge ad="engel" boyut="1.1em" />Kırmızı intikal:
        <strong>{{ masa.kirmizi.value?.kirmiziIntikal }}</strong>
      </p>
      <button
        v-if="masa.gun.value"
        type="button"
        class="cip cip-uyari"
        :class="{ dolu: masa.atanmamisSayisi.value > 0 }"
        :aria-pressed="suzgec === 'atanmamis'"
        data-test="atanmamis-sayac"
        title="Asıl makinisti olmayan seferler (iptaller hariç). Düğme: yalnız bunları listeler."
        @click="suzgecDugme('atanmamis')"
      >
        <Simge ad="daire" boyut="1.1em" />Atanmamış:
        <strong>{{ masa.atanmamisSayisi.value }}</strong> sefer
      </button>
      <button
        v-if="kondukturEksikSayisi > 0"
        type="button"
        class="cip cip-bilgi dolu"
        :aria-pressed="suzgec === 'konduktor'"
        data-test="konduktor-sayac"
        title="Kondüktörü atanmamış yolcu treni. Düğme: yalnız bunları listeler."
        @click="suzgecDugme('konduktor')"
      >
        <span class="gosterge gosterge-konduktor" aria-hidden="true"
          ><Simge ad="kisi" boyut="0.9em"
        /></span>
        Kondüktör eksik: <strong>{{ kondukturEksikSayisi }}</strong> sefer
      </button>
      <p
        v-if="masa.gordum.value"
        class="cip cip-uyari"
        :class="{ dolu: masa.gordum.value.gorulmeyen > 0 }"
        data-test="gordum-sayac"
        :title="`Seçili gün + sonraki 7 gün, kalkışı geçmemiş atamalar ve intikaller: ${masa.gordum.value.gorulen} gördü, ${masa.gordum.value.gorulmeyen} görmedi, ${masa.gordum.value.hesapsiz} personelin hesabı yok`"
      >
        <Simge ad="daire" boyut="1.1em" />Görülmeyen:
        <strong>{{ masa.gordum.value.gorulmeyen }}</strong>
        <span class="cip-alt"
          >telefon hesabı yok: <strong>{{ masa.gordum.value.hesapsiz }}</strong></span
        >
        <span v-if="masa.gordum.value.uyari > 0" class="cip-alt gordum-uyari">
          <Simge ad="uyari" boyut="1em" />{{ masa.gordum.value.uyari }} kalkışa yakın
        </span>
      </p>
      <p
        v-if="masa.takasBekleyen.value !== null"
        class="cip cip-bilgi"
        :class="{ dolu: masa.takasBekleyen.value > 0 }"
        data-test="takas-sayac"
      >
        <RouterLink to="/takas">
          <Simge ad="takas" boyut="1.1em" />Bekleyen talep:
          <strong>{{ masa.takasBekleyen.value }}</strong>
        </RouterLink>
      </p>
      <p v-if="!seciliSatir" class="ipucu">
        Aday görmek için bir sefer satırını seçin; satırdayken → tuşu aday paneline geçer.
      </p>
      <button
        v-else
        type="button"
        class="btn btn-kucuk"
        data-test="adaylara-gec"
        @click="adaylaraGec()"
      >
        Adaylara geç
      </button>
      <span v-if="seciliSatir" class="ipucu" data-test="sag-ok-ipucu">ya da satırda → tuşu</span>
    </div>

    <div role="status">
      <p v-if="bilgi" class="bildirim bildirim-bilgi">{{ bilgi }}</p>
    </div>
    <p v-if="masa.gunHatasi.value" class="bildirim bildirim-hata" role="alert">
      {{ masa.gunHatasi.value }}
    </p>

    <IntikalSeridi :tarih="tarih" @degisti="masa.yenile()" />

    <div class="atama-izgara" :class="{ secimsiz: !seciliSatir }">
      <div ref="liste" class="tablo-kap masa-kap">
        <table class="tablo masa-tablo" :aria-label="`${tarihBicimle(tarih)} seferleri`">
          <thead>
            <tr>
              <th class="kol-saat">Saat</th>
              <th>Tren no</th>
              <th class="kol-guzergah">Güzergâh</th>
              <th class="kol-ayrinti">Araç · Hat</th>
              <th class="makinist-sutun">Personel</th>
              <th class="kol-durum">Durum</th>
              <th class="kol-islem">İşlem</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="s in gorunenSatirlar"
              :key="s.seferId"
              class="sefer-satiri"
              :class="{
                iptal: s.durum === 'IPTAL',
                secili: s.seferId === seciliId,
                kirmizi: s.kirmizi,
                uyari: uyarili(s),
              }"
              :tabindex="tb(s)"
              :aria-selected="s.seferId === seciliId"
              aria-keyshortcuts="ArrowRight"
              @focusin="aktifId = s.seferId"
              @click="seferSec(s.seferId)"
              @keydown.right.self.prevent="adaylaraGec(s.seferId)"
              @keydown.enter.self.prevent="seferSec(s.seferId)"
              @keydown.down.self.prevent="satirGez($event, 1)"
              @keydown.up.self.prevent="satirGez($event, -1)"
            >
              <td class="kol-saat">
                <span class="saat"
                  >{{ saatBicimle(s.guncelKalkis) }}–{{ saatBicimle(s.guncelVaris) }}</span
                >
                <span
                  v-if="geceMi(s.guncelKalkis, s.guncelVaris)"
                  class="gosterge gosterge-gece"
                  data-test="gece"
                  title="Gece görevi"
                  ><Simge ad="ay" boyut="0.9em" /><span class="gorunmez">gece</span></span
                >
              </td>
              <td class="kol-tren" :data-guzergah="guzergah(s)">
                <span class="tren-no">{{ trenAdi(s) }}</span>
              </td>
              <td class="kol-guzergah">
                <span class="guzergah">{{ guzergah(s) }}</span>
              </td>
              <td class="kol-ayrinti">
                <span>{{ s.aracTipi }}</span> <span class="soluk">{{ s.hat }}</span>
              </td>
              <td class="makinist-sutun">
                <div class="personel-icerik">
                  <div
                    v-for="m in sirali(s)"
                    :key="m.atamaId"
                    class="gorevli"
                    :class="[`gorevli-${m.rol.toLowerCase()}`, { 'gordum-uyarili': m.gorduUyari }]"
                    :title="ROL_METINLERI[m.rol]"
                  >
                    <span class="gorevli-ad"
                      ><b
                        v-if="ROL_ONEKLERI[m.rol]"
                        class="rozet rozet-sm"
                        :class="`rozet-rol-${m.rol.toLowerCase()}`"
                        >{{ ROL_ONEKLERI[m.rol].trim() }}</b
                      >
                      {{ kisi(m) }}</span
                    >
                    <span v-if="m.gordu === 'GORDU'" class="gordum goruldu" data-test="gordu">
                      <Simge ad="basari" boyut="1em" />gördü {{ saatBicimle(m.gorduZamani) }}
                    </span>
                    <span
                      v-else-if="m.gordu === 'GORMEDI'"
                      class="gordum gormedi"
                      data-test="gormedi"
                      title="Makinist görevi henüz görmedi"
                    >
                      <Simge :ad="m.gorduUyari ? 'uyari' : 'daire'" boyut="1em" /><span
                        :class="{ gorunmez: !m.gorduUyari }"
                        >görmedi</span
                      >
                    </span>
                    <span
                      v-else-if="m.gordu === 'HESAP_YOK'"
                      class="gosterge gosterge-hesap"
                      data-test="hesap-yok"
                      title="Telefon hesabı yok"
                      ><Simge ad="telefon-yok" boyut="1em" /><span class="gorunmez"
                        >hesap yok</span
                      ></span
                    >
                    <button
                      type="button"
                      :tabindex="tb(s)"
                      class="btn btn-kucuk btn-kare kaldir-dugme"
                      :aria-label="`${kisi(m)} atamasını kaldır`"
                      title="Kaldır"
                      @click.stop="kaldirAc(s, m)"
                    >
                      <Simge ad="sil" boyut="1.1em" /><span class="gorunmez">Kaldır</span>
                    </button>
                    <button
                      v-if="
                        s.durum === 'IPTAL' &&
                        m.kirmizi &&
                        m.rol !== 'YOLCU' &&
                        m.rol !== 'KONDUKTOR'
                      "
                      type="button"
                      :tabindex="tb(s)"
                      class="btn btn-kucuk btn-birincil"
                      :aria-label="`${kisi(m)} için uygun seferler`"
                      @click.stop="digerAc({ tur: 'uygun', makinist: m })"
                    >
                      <Simge ad="ara" boyut="1.1em" />Uygun seferler
                    </button>
                    <div v-if="m.kirmizi" class="kirmizi-neden">
                      <Simge ad="engel" boyut="1em" />{{ m.kirmiziNedenler.join('; ') }}
                    </div>
                    <div v-if="m.uyarilar.length > 0" class="uyari-notu">
                      <Simge ad="uyari" boyut="1em" />Uyarı: {{ m.uyarilar.join('; ') }}
                    </div>
                    <div v-if="s.durum === 'IPTAL'" class="soluk iptal-notu">
                      Eski atama iptal seferde duruyor; "Kaldır" ile kaldırın.
                    </div>
                  </div>
                  <Rozet
                    v-if="s.durum !== 'IPTAL' && !asil(s)"
                    ton="uyari"
                    simge="daire"
                    metin="BOŞ"
                    boyut="sm"
                  />
                  <span
                    v-if="s.durum !== 'IPTAL' && !asil(s) && s.bosNeden"
                    class="bos-neden"
                    :title="s.bosNeden"
                    >{{ s.bosNeden }}</span
                  >
                  <span
                    v-if="s.durum !== 'IPTAL' && s.kondukturEksik"
                    class="gosterge gosterge-konduktor"
                    data-test="konduktor-eksik"
                    title="Kondüktör eksik"
                    ><Simge ad="kisi" boyut="0.9em" /><span class="gorunmez"
                      >Kondüktör eksik</span
                    ></span
                  >
                </div>
              </td>
              <td class="kol-durum">
                <Rozet
                  v-if="s.durum === 'IPTAL'"
                  ton="engel"
                  simge="iptal"
                  :metin="durumMetni(s)"
                  boyut="sm"
                />
                <template v-else>
                  <Rozet v-if="s.kirmizi" ton="engel" simge="engel" metin="KONTROL" boyut="sm" />
                  <span v-if="s.kalkisGecikmeDk > 0" class="gecikme">
                    <Simge ad="gecikme" boyut="1em" />{{ gecikmeMetni(s) }}
                  </span>
                </template>
              </td>
              <td class="kol-islem">
                <div class="islem-dugmeleri">
                  <template v-if="s.durum !== 'IPTAL'">
                    <button
                      type="button"
                      :tabindex="tb(s)"
                      class="btn btn-kucuk btn-kare"
                      :aria-label="`${trenAdi(s)} seferine gecikme gir`"
                      title="Gecikme"
                      @click.stop="digerAc({ tur: 'degisiklik', kip: 'gecikme', satir: s })"
                    >
                      <Simge ad="gecikme" boyut="1.1em" /><span class="eylem-metin">Gecikme</span>
                    </button>
                    <button
                      type="button"
                      :tabindex="tb(s)"
                      class="btn btn-kucuk btn-kare"
                      :aria-label="`${trenAdi(s)} seferini iptal et`"
                      title="İptal"
                      @click.stop="digerAc({ tur: 'degisiklik', kip: 'iptal', satir: s })"
                    >
                      <Simge ad="iptal" boyut="1.1em" /><span class="eylem-metin">İptal</span>
                    </button>
                    <button
                      v-if="bolunebilir(s)"
                      type="button"
                      :tabindex="tb(s)"
                      class="btn btn-kucuk btn-kare"
                      :aria-label="`${trenAdi(s)} seferini böl`"
                      title="Böl"
                      @click.stop="digerAc({ tur: 'bol', satir: s })"
                    >
                      <Simge ad="bol" boyut="1.1em" /><span class="eylem-metin">Böl</span>
                    </button>
                    <button
                      v-if="birlestirilebilir(s)"
                      type="button"
                      :tabindex="tb(s)"
                      class="btn btn-kucuk btn-kare"
                      :aria-label="`${trenAdi(s)} seferini birleştir`"
                      title="Birleştir"
                      @click.stop="digerAc({ tur: 'birlestir', satir: s })"
                    >
                      <Simge ad="birlestir" boyut="1.1em" /><span class="eylem-metin"
                        >Birleştir</span
                      >
                    </button>
                    <button
                      v-if="asil(s)"
                      type="button"
                      :tabindex="tb(s)"
                      class="btn btn-kucuk btn-kare"
                      :class="{ 'btn-birincil': s.kirmizi }"
                      :aria-label="`${trenAdi(s)} seferinde yerine ata`"
                      title="Yerine ata"
                      @click.stop="digerAc({ tur: 'yerine', satir: s })"
                    >
                      <Simge ad="takas" boyut="1.1em" /><span class="eylem-metin">Yerine ata</span>
                    </button>
                  </template>
                </div>
              </td>
            </tr>
            <tr v-if="masa.gun.value === null && !masa.gunHatasi.value">
              <td colspan="7">
                <VeriDurumu durum="yukleniyor" metin="Yükleniyor…" />
              </td>
            </tr>
            <tr v-else-if="satirlar.length === 0 && !masa.gunHatasi.value">
              <td colspan="7">
                <VeriDurumu durum="bos" metin="Bu gün için sefer yok." />
              </td>
            </tr>
            <tr v-else-if="satirlar.length > 0 && gorunenSatirlar.length === 0">
              <td colspan="7">
                <VeriDurumu durum="bos" metin="Süzgece uyan sefer yok.">
                  <template #eylem>
                    <button type="button" class="btn btn-kucuk" @click="suzgec = null">
                      Süzgeci kaldır
                    </button>
                  </template>
                </VeriDurumu>
              </td>
            </tr>
          </tbody>
        </table>
        <NobetNotu class="masa-nobet" :metinler="nobetMetinleri" />
      </div>

      <aside ref="panel" class="kart aday-kap" aria-label="Aday personel">
        <template v-if="seciliSatir">
          <button type="button" class="btn listeye-don" @click="gorunurKil(liste)">
            <Simge ad="ok-sol" boyut="1.1em" />Sefer listesine dön
          </button>
          <h2 ref="panelBasligi" tabindex="-1">Adaylar: {{ seferEtiketi(seciliSatir) }}</h2>
          <AdayPaneli
            v-model:rol="rol"
            :adaylar="adaylar"
            :yukleniyor="masa.adayYukleniyor.value"
            :hata="masa.adayHatasi.value"
            @sec="adayAta"
            @yolculuk-oner="
              (a) => seciliSatir && digerAc({ tur: 'yolculuk', satir: seciliSatir, aday: a })
            "
          />
        </template>
        <p v-else class="soluk">Aday görmek için soldan bir sefer seçin.</p>
      </aside>
    </div>

    <AtamaDialog
      v-if="diyalog"
      :sefer-id="diyalog.seferId"
      :sefer-etiketi="diyalog.etiket"
      :rol="diyalog.rol"
      :aday="diyalog.aday"
      :mevcut="diyalog.mevcut"
      @tamam="tamam"
      @kapat="kapat"
    />
    <SeferDegisiklikDialog
      v-if="diger?.tur === 'degisiklik'"
      :kip="diger.kip"
      :sefer-id="diger.satir.seferId"
      :sefer-etiketi="seferEtiketi(diger.satir)"
      :kalkis-gecikme-dk="diger.satir.kalkisGecikmeDk"
      :varis-gecikme-dk="diger.satir.varisGecikmeDk"
      :parca-no="diger.satir.parcaNo"
      :parca-sayisi="diger.satir.parcaSayisi"
      @tamam="tamam"
      @kapat="kapat"
    />
    <SeferBolDialog
      v-if="diger?.tur === 'bol'"
      :sefer-id="diger.satir.seferId"
      :sefer-etiketi="`${seferEtiketi(diger.satir)} · ${tarihBicimle(tarih)}`"
      @tamam="tamam"
      @kapat="kapat"
    />
    <SeferBirlestirDialog
      v-if="diger?.tur === 'birlestir'"
      :sefer-id="diger.satir.seferId"
      :sefer-etiketi="`${seferEtiketi(diger.satir)} · ${tarihBicimle(tarih)}`"
      @tamam="tamam"
      @kapat="kapat"
    />
    <YerineAtaDialog
      v-if="diger?.tur === 'yerine'"
      :sefer-id="diger.satir.seferId"
      :sefer-etiketi="seferEtiketi(diger.satir)"
      @sec="(o) => diger?.tur === 'yerine' && yerineSec(diger.satir, o)"
      @kapat="kapat(false)"
    />
    <UygunSeferlerDialog
      v-if="diger?.tur === 'uygun'"
      :makinist-id="diger.makinist.makinistId"
      :makinist-adi="kisi(diger.makinist)"
      :gun="tarih"
      @sec="(u) => diger?.tur === 'uygun' && uygunSec(diger.makinist, u)"
      @kapat="kapat(false)"
    />
    <YolculukOneriDialog
      v-if="diger?.tur === 'yolculuk'"
      :sefer-id="diger.satir.seferId"
      :sefer-etiketi="seferEtiketi(diger.satir)"
      :aday="diger.aday"
      :mevcut-asil="asil(diger.satir) ?? null"
      @tamam="tamam"
      @kapat="kapat"
    />
    <OtomatikAtamaDialog
      v-if="diger?.tur === 'otomatik'"
      :varsayilan-bas="tarih"
      @uygulandi="masa.yenile()"
      @kapat="kapat(false)"
    />
  </section>
</template>

<style scoped>
/* 1101 px üstü: sayfa viewport'a oturur; yalnız masa kabı kayar (başlık sabit, MK-135). Altı: tek sütun, normal sayfa kaydırması. */
.atama-masa {
  display: flex;
  flex-direction: column;
}
.atama-izgara {
  display: grid;
  /* Aday paneli genişliğe göre büyür (1366 → 24rem, 1920 → 26,4rem): ad ve ihlal metni 1–2 satırda kalır. */
  grid-template-columns: minmax(0, 1fr) clamp(24rem, 22vw, 27rem);
  gap: var(--aralik-4);
  align-items: stretch;
}
.atama-izgara.secimsiz {
  grid-template-columns: minmax(0, 1fr) 15rem;
}
/* position: görünmez metinler (mutlak konumlu) kaydırma kabına bağlansın; yoksa belge yüksekliğini şişirir. */
.masa-kap {
  position: relative;
  overflow: auto;
  min-height: 0;
}
.aday-kap {
  min-height: 0;
  padding: var(--aralik-4);
  overflow: auto;
}
.aday-kap h2 {
  margin: 0 0 var(--aralik-3);
  font-size: var(--yazi-lg);
}
.listeye-don {
  display: none;
  margin-bottom: var(--aralik-3);
}
@media (min-width: 1101px) {
  .atama-masa {
    height: calc(100dvh - var(--ust-yukseklik) - 2 * var(--aralik-5));
    min-height: 28rem;
  }
  .atama-izgara {
    flex: 1 1 auto;
    min-height: 0;
    grid-template-rows: minmax(0, 1fr);
  }
}
/* Seçim yokken 1500 px altında aday paneli yer kaplamasın (ipucu sayaç satırında): masa tam genişlik. */
@media (min-width: 1101px) and (max-width: 1499px) {
  .atama-izgara.secimsiz {
    grid-template-columns: minmax(0, 1fr);
  }
  .atama-izgara.secimsiz > .aday-kap {
    display: none;
  }
}
@media (max-width: 1100px) {
  .atama-izgara,
  .atama-izgara.secimsiz {
    grid-template-columns: minmax(0, 1fr);
  }
  .masa-kap {
    max-height: 70dvh;
  }
  .listeye-don {
    display: inline-flex;
  }
}

/* ---- üst: gün gezgini ve sayaç çipleri ---- */
.arac-cubugu {
  margin: 0;
  align-items: center;
}
.gun-alani {
  flex-direction: row;
  align-items: center;
  gap: var(--aralik-3);
}
.gun-alani label {
  margin: 0;
}
.sayac-seridi {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--aralik-2) var(--aralik-3);
  margin-bottom: var(--aralik-3);
}
.cip {
  display: inline-flex;
  align-items: center;
  gap: var(--aralik-2);
  min-height: 1.75rem;
  margin: 0;
  padding: 0 var(--aralik-3);
  border: 1px solid var(--renk-cizgi);
  border-radius: var(--yaricap-pill);
  background: var(--renk-yuzey);
  color: var(--renk-metin);
  font: inherit;
  font-size: var(--yazi-sm);
  white-space: nowrap;
}
.cip strong {
  font-variant-numeric: tabular-nums;
}
.cip a {
  display: inline-flex;
  align-items: center;
  gap: var(--aralik-2);
  min-height: 1.5rem; /* hedef >= 24 px (WCAG 2.5.8) */
}
button.cip {
  cursor: pointer;
}
button.cip:hover {
  background: var(--renk-n-100);
}
.cip.dolu.cip-engel {
  background: var(--renk-engel-zemin);
  border-color: var(--renk-engel-dolu);
  color: var(--renk-engel-metin);
}
.cip.dolu.cip-uyari {
  background: var(--renk-uyari-zemin);
  border-color: var(--renk-uyari-cizgi);
  color: var(--renk-uyari-metin);
}
.cip.dolu.cip-bilgi {
  background: var(--renk-bilgi-zemin);
  border-color: var(--renk-birincil);
  color: var(--renk-bilgi-metin);
}
.cip[aria-pressed='true'] {
  box-shadow: 0 0 0 2px var(--renk-birincil);
  font-weight: var(--yazi-agirlik-kalin);
}
.cip-alt {
  padding-left: var(--aralik-3);
  border-left: 1px solid currentcolor;
  display: inline-flex;
  align-items: center;
  gap: var(--aralik-2);
}
.ipucu {
  margin: 0 0 0 auto;
  color: var(--renk-metin-soluk);
  font-size: var(--yazi-sm);
}

/* ---- tablo: tek satır, sabit başlık ---- */
.masa-tablo th {
  position: sticky;
  top: 0;
  z-index: 2;
  box-shadow: 0 1px 0 var(--renk-cizgi);
}
.masa-tablo th,
.masa-tablo td {
  padding: var(--hucre-dikey) var(--aralik-2) var(--hucre-dikey) var(--aralik-3);
  vertical-align: middle;
}
.sefer-satiri td {
  height: var(--satir-min);
}
/* Durum çoğu satırda boş: sütun içeriği kadar (rozet / gecikme) yer alır, artan genişlik Personel'e gider. */
.masa-tablo .kol-durum,
.masa-tablo .kol-islem {
  width: 1%;
  white-space: nowrap;
}
.kol-saat,
.tren-no {
  white-space: nowrap;
}
.saat {
  font-variant-numeric: tabular-nums;
}
.tren-no {
  font-family: var(--yazi-mono);
  font-weight: var(--yazi-agirlik-kalin);
}
.guzergah {
  color: var(--renk-metin-ikincil);
}
.kol-ayrinti {
  display: none;
  white-space: nowrap;
}
@media (min-width: 1500px) {
  .atama-izgara.secimsiz .kol-ayrinti {
    display: table-cell;
  }
}
@media (min-width: 1800px) {
  .kol-ayrinti {
    display: table-cell;
  }
}
/* Aday paneli açıkken (1800 px altı) tablo dar: güzergâh sütunu gizlenir, tren numarasının altında (CSS içeriği, aynı veri) görünür. */
@media (max-width: 1799px) {
  .atama-izgara:not(.secimsiz) .kol-guzergah {
    display: none;
  }
  .atama-izgara:not(.secimsiz) .kol-tren::after {
    content: attr(data-guzergah);
    display: block;
    font-family: var(--yazi-ailesi);
    font-size: var(--yazi-xs);
    font-weight: var(--yazi-agirlik-normal);
    color: var(--renk-metin-ikincil);
    white-space: nowrap;
  }
}
/* Aday paneli açıkken tablo daralır: güzergâh ve personel adı sarabilir; tren no ve saat sarmaz. Geniş ekranda ve seçim yokken hepsi tek satır. */
.kol-guzergah {
  min-width: 6rem;
}
@media (min-width: 1101px) {
  .atama-izgara.secimsiz .kol-guzergah {
    white-space: nowrap;
  }
}
@media (min-width: 1500px) {
  .atama-izgara.secimsiz .gorevli-ad {
    white-space: nowrap;
  }
}
@media (min-width: 1800px) {
  .kol-guzergah,
  .gorevli-ad {
    white-space: nowrap;
  }
}
.makinist-sutun {
  min-width: 9rem;
}
.personel-icerik {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--aralik-1) var(--aralik-4);
}
.gorevli {
  display: inline-flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0 var(--aralik-3);
  padding-left: var(--aralik-3);
  border-left: 3px solid var(--renk-cizgi);
}
.gorevli-asil {
  border-left-color: var(--renk-rol-asil-metin);
}
.gorevli-yardimci {
  border-left-color: var(--renk-rol-yardimci-metin);
}
.gorevli-konduktor {
  border-left-color: var(--renk-rol-konduktor-metin);
}
.gorevli-yolcu {
  border-left-color: var(--renk-rol-yolcu-metin);
  border-left-style: dashed;
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
.gordum-uyarili {
  background: var(--renk-uyari-zemin);
}
.gordum-uyarili .gordum.gormedi {
  color: var(--renk-uyari-metin);
  font-weight: var(--yazi-agirlik-kalin);
}
.gosterge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: none;
  width: 1.4rem;
  height: 1.4rem;
  border: 1px solid currentcolor;
  border-radius: 50%;
}
.gosterge-gece {
  margin-left: var(--aralik-2);
  background: var(--renk-gece-zemin);
  color: var(--renk-gece-metin);
}
.gosterge-konduktor {
  background: var(--renk-rol-konduktor-zemin);
  color: var(--renk-rol-konduktor-metin);
  border-style: dashed;
}
.gosterge-hesap {
  border-color: transparent;
  color: var(--renk-metin-soluk);
}
.kirmizi-neden,
.uyari-notu,
.iptal-notu {
  flex-basis: 100%;
  font-size: var(--yazi-xs);
}
.kirmizi-neden {
  color: var(--renk-engel-metin);
  font-weight: var(--yazi-agirlik-orta);
}
.uyari-notu {
  color: var(--renk-uyari-metin);
}
/* Aday paneli açıkken tablo dar: BOŞ nedeni gizlenir (panel zaten aday gerekçelerini gösterir); metin DOM'da kalır. */
.atama-izgara:not(.secimsiz) .bos-neden {
  display: none;
}
.bos-neden {
  max-width: 22rem;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--renk-metin-soluk);
  font-size: var(--yazi-xs);
}
/* Her görevlide tekrar eden "Kaldır": kenarlık yalnız üzerine gelince / odakta (UI kenarlığı düğme olduğunu zaten simgeyle söyler). */
.kaldir-dugme:not(:hover, :focus-visible) {
  border-color: transparent;
  background: transparent;
}
.gecikme {
  display: inline-flex;
  align-items: center;
  gap: var(--aralik-2);
  font-size: var(--yazi-xs);
}
.islem-dugmeleri {
  display: flex;
  gap: var(--aralik-1) var(--aralik-2);
}
.islem-dugmeleri .btn {
  position: relative;
}
/* Eylem düğmeleri simgedir; metin DOM'da (görünmez). Geniş ekranda ve seçim yokken metin görünür (MK-134). */
.eylem-metin {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip-path: inset(50%);
  white-space: nowrap;
}
@media (min-width: 1600px) {
  .atama-izgara.secimsiz .eylem-metin {
    position: static;
    width: auto;
    height: auto;
    overflow: visible;
    clip-path: none;
  }
}
@media (min-width: 1800px) {
  .eylem-metin {
    position: static;
    width: auto;
    height: auto;
    overflow: visible;
    clip-path: none;
  }
}
.masa-nobet {
  padding: 0 var(--aralik-3) var(--aralik-3);
}

/* ---- satır durumu (zemin + sol şerit) ve seçim (iç halka) ayrı katmanlardır ---- */
.sefer-satiri {
  cursor: pointer;
}
.sefer-satiri td:first-child {
  border-left: 4px solid transparent;
}
.sefer-satiri:hover td {
  background: var(--renk-n-100);
}
.sefer-satiri.uyari td:first-child {
  border-left-color: var(--renk-uyari-cizgi);
}
.sefer-satiri.iptal td {
  background: var(--renk-n-50);
}
.sefer-satiri.iptal td:first-child {
  border-left-color: var(--renk-kontrol-kenar);
}
.sefer-satiri.iptal .saat,
.sefer-satiri.iptal .tren-no,
.sefer-satiri.iptal .guzergah,
.sefer-satiri.iptal .gorevli-ad {
  color: var(--renk-metin-soluk);
  text-decoration: line-through;
}
.sefer-satiri.kirmizi td {
  background: var(--renk-engel-zemin);
}
.sefer-satiri.kirmizi td:first-child {
  border-left-color: var(--renk-engel-dolu);
}
.sefer-satiri[aria-selected='true']:not(.kirmizi, .iptal) td {
  background: var(--renk-bilgi-zemin);
}
.sefer-satiri[aria-selected='true'] td {
  box-shadow:
    inset 0 2px 0 var(--renk-birincil),
    inset 0 -2px 0 var(--renk-birincil);
}
.sefer-satiri[aria-selected='true'] td:first-child {
  box-shadow:
    inset 2px 0 0 var(--renk-birincil),
    inset 0 2px 0 var(--renk-birincil),
    inset 0 -2px 0 var(--renk-birincil);
}
.sefer-satiri[aria-selected='true'] td:last-child {
  box-shadow:
    inset -2px 0 0 var(--renk-birincil),
    inset 0 2px 0 var(--renk-birincil),
    inset 0 -2px 0 var(--renk-birincil);
}
.sefer-satiri:focus-visible {
  outline-offset: -3px;
}
/*
 * QA BUG-13: masada tarayıcının Ctrl+P'si ENGELLENMEZ (kısayolu ele geçirmek beklentiyi bozar; gerçek biçimlendirilmiş çıktı "Yazdır" düğmesinin
 * /yazdir/gun sayfasıdır). Ctrl+P yine de kullanışlı çıksın diye yazdırmada: tüm seferler akar (kaydırma kabı kesmez), etkileşim öğeleri
 * ve aday paneli gizlenir, KONTROL gibi anlamlar metinle korunur.
 */
@media print {
  .atama-masa {
    height: auto;
    min-height: 0;
  }
  .atama-izgara,
  .atama-izgara.secimsiz {
    display: block;
  }
  .masa-kap {
    overflow: visible;
    max-height: none;
  }
  .aday-kap,
  .listeye-don,
  .arac-cubugu,
  .sayac-seridi,
  .kol-islem,
  .kaldir-dugme,
  .btn {
    display: none;
  }
  .sefer-satiri,
  .sefer-satiri td {
    background: none;
    color: CanvasText;
    box-shadow: none;
  }
}
</style>
