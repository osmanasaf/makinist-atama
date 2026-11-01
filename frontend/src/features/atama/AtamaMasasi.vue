<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { ApiHata } from '@/api/istemci'
import { kaydirmaDavranisi } from '@/lib/odak'
import { bugun, gunKaydir, saatBicimle, tarihBicimle } from '@/lib/tarih'
import { trenAdi } from '@/lib/trenAdi'
import { durumMetni, gecikmeMetni } from '@/features/yazdir/durumMetni'
import IntikalSeridi from '@/features/intikal/IntikalSeridi.vue'
import Dugme from '@/bilesenler/Dugme.vue'
import EylemMenusu, { type EylemOgesi } from '@/bilesenler/EylemMenusu.vue'
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
import { geceAraligi, geceAyariYukle, geceMi } from './gece'
import { atamaApi, ROL_ETIKETLERI, ROL_ONEKLERI } from './atamaApi'
import type { Aday, AtamaRolu, GunMakinisti, GunSatiri, Oneri, UygunSefer } from './atamaApi'
import { useYoklama } from '@/features/benim/useYoklama'
import { atanmamisMi } from '@/features/ana/yapilacaklar'
import { hizliAtanabilir, sonrakiBos } from './hizliDoldur'
import { suzgecOku, type MasaSuzgeci } from './masaBaglantisi'
import { useAtamaMasasi } from './useAtamaMasasi'
import { atamaHaritasi, isaretlerMetni } from './yakinlik'
import { GERI_AL_SURESI_MS, useGeriAl } from './useGeriAl'
import ZamanSeridi, { type CubukDurumu } from './ZamanSeridi.vue'
import {
  cubuk as cubukHesapla,
  geceBantlari,
  gunDakikasi,
  izgara as izgaraHesapla,
  pencere as pencereHesapla,
  saatEtiketleri,
  simdiYuzde,
} from './zamanCizelgesi'

const route = useRoute()
const router = useRouter()

const TARIH_DESENI = /^\d{4}-\d{2}-\d{2}$/
const tarih = computed(() => {
  const t = route.query.tarih
  return typeof t === 'string' && TARIH_DESENI.test(t) ? t : bugun()
})

const masa = useAtamaMasasi(tarih)
const { satirlar, seciliId, seciliSatir, rol, adaylar } = masa
/** F3: atanan makinistin "sınıra yakın" bilgi işareti metni (yoksa boş). Satırın kırmızı/uyarı durumunu ETKİLEMEZ. */
const yakinlikAtamalari = computed(() => atamaHaritasi(masa.gunYakinlik.value))
const yakinlikMetni = (m: GunMakinisti) =>
  isaretlerMetni(yakinlikAtamalari.value.get(m.atamaId)?.yakinlik ?? [])
void masa.gunuYukle()
void masa.kirmiziYukle()
void masa.gordumYukle()
void masa.takasYukle()
void geceAyariYukle() // gece göstergesi ayardaki pencereyi (gece.baslangic/bitis) izler
// Makinistler "gördüm" dedikçe ve takas talepleri geldikçe masa kendiliğinden güncellensin (60 sn; sekme gizliyken durur).
const simdi = ref(Date.now()) // "şimdi" çizgisi yoklamayla (60 sn) ilerler
useYoklama(() => {
  simdi.value = Date.now()
  return masa.sessizYenile()
})

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
type SatirSuzgeci = MasaSuzgeci
// Adres sorgusu (`?suzgec=`) süzgeci açar; çip tıklaması yerel kalır, sorguyu yeniden yazmaz (MK-156).
const suzgec = ref<SatirSuzgeci | null>(suzgecOku(route.query.suzgec))
watch(
  () => route.query.suzgec,
  (v) => (suzgec.value = suzgecOku(v)),
)
const suzgecDugme = (k: SatirSuzgeci) => (suzgec.value = suzgec.value === k ? null : k)
const SUZGEC_KOSULU: Record<SatirSuzgeci, (s: GunSatiri) => boolean> = {
  atanmamis: (s) => s.durum !== 'IPTAL' && !asil(s),
  kirmizi: (s) => s.kirmizi,
  konduktor: (s) => s.durum !== 'IPTAL' && !!s.kondukturEksik,
  gormeyen: (s) => s.makinistler.some((m) => m.gordu === 'GORMEDI'),
  bitmeyen: (s) => atanmamisMi(s, simdi.value),
}
const gorunenSatirlar = computed(() =>
  suzgec.value ? satirlar.value.filter(SUZGEC_KOSULU[suzgec.value]) : satirlar.value,
)
/** Görünüm anahtarı (B1, MK-159): Liste varsayılan; seçim tarayıcıda hatırlanır (erişilemezse yalnız bu oturum). */
type Gorunum = 'liste' | 'cizelge'
const GORUNUM_ANAHTARI = 'atama.masa.gorunum'
function gorunumOku(): Gorunum {
  try {
    return localStorage.getItem(GORUNUM_ANAHTARI) === 'cizelge' ? 'cizelge' : 'liste'
  } catch {
    return 'liste'
  }
}
const gorunum = ref<Gorunum>(gorunumOku())
const cizelgede = computed(() => gorunum.value === 'cizelge')
function gorunumSec(g: Gorunum) {
  gorunum.value = g
  try {
    localStorage.setItem(GORUNUM_ANAHTARI, g)
  } catch {
    // tercih kalıcı olmaz; masa çalışmaya devam eder
  }
}
/** Çizelge geometrisi yalnız Çizelge görünümünde hesaplanır (Liste'ye maliyeti yok). Pencere tüm günden, süzgeçten bağımsız: süzgeç değişince eksen oynamaz. */
const cizelge = computed(() => {
  if (!cizelgede.value) return null
  const p = pencereHesapla(satirlar.value, tarih.value)
  const etiketler = saatEtiketleri(p)
  return {
    p,
    etiketler,
    bantlar: geceBantlari(geceAraligi.value, p),
    simdi: simdiYuzde(simdi.value, tarih.value, p),
    izgara: izgaraHesapla(p),
  }
})
const cubukDurumu = (s: GunSatiri): CubukDurumu =>
  s.durum === 'IPTAL' ? 'iptal' : s.kirmizi ? 'kirmizi' : asil(s) ? 'atanmis' : 'bos'
const CUBUK_DURUM_METNI: Record<CubukDurumu, string> = {
  atanmis: 'atanmış',
  bos: 'boş, asıl makinist yok',
  kirmizi: 'kontrol bekliyor',
  iptal: 'iptal',
}
/** Çubuğun erişilebilir adı: sefer, güzergâh, saat (gece yarısını geçiyorsa belirtilir) ve durum metni. */
const cubukEtiketi = (s: GunSatiri) =>
  `${trenAdi(s)} ${guzergah(s)}, ${saatBicimle(s.guncelKalkis)}–${saatBicimle(s.guncelVaris)}` +
  (gunDakikasi(s.guncelVaris, tarih.value) > 1440 ? ' (ertesi güne sürüyor)' : '') +
  `, ${CUBUK_DURUM_METNI[cubukDurumu(s)]}`

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

/** Satırın ⋯ menüsü (MK-148): Gecikme, Böl, Birleştir; ayırıcıdan sonra yıkıcı İptal. `etiket`ler eski düğme adlarıdır. */
function menuOgeleri(s: GunSatiri): EylemOgesi[] {
  const ad = trenAdi(s)
  return [
    { kimlik: 'gecikme', ad: 'Gecikme', etiket: `${ad} seferine gecikme gir`, simge: 'gecikme' },
    {
      kimlik: 'bol',
      ad: 'Böl',
      etiket: `${ad} seferini böl`,
      simge: 'bol',
      gizli: !bolunebilir(s),
    },
    {
      kimlik: 'birlestir',
      ad: 'Birleştir',
      etiket: `${ad} seferini birleştir`,
      simge: 'birlestir',
      gizli: !birlestirilebilir(s),
    },
    {
      kimlik: 'iptal',
      ad: 'İptal',
      etiket: `${ad} seferini iptal et`,
      simge: 'iptal',
      tehlike: true,
    },
  ]
}

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
// N-1 (MK-182): bilgi, Geri al şeridiyle aynı sabit konumlu kapta görünür (akışta yer tutmaz, tablo kaymaz); şerit gibi 10 sn sonra kalkar.
let bilgiZamanlayici: ReturnType<typeof setTimeout> | undefined
watch(bilgi, (b) => {
  clearTimeout(bilgiZamanlayici)
  if (b) bilgiZamanlayici = setTimeout(() => (bilgi.value = ''), GERI_AL_SURESI_MS)
})
onBeforeUnmount(() => clearTimeout(bilgiZamanlayici))

const geriAl = useGeriAl()
/** Hızlı atama sürerken ikinci tetik (çift Enter) yok sayılır; sunucu `ZATEN_ATANMIS` ikinci savunma. */
const hizliKilit = ref(false)
const hizliHata = ref('')
const adayPaneli = ref<InstanceType<typeof AdayPaneli> | null>(null)

// BUG-12: gün değişince (adres, geri tuşu, ok düğmeleri) önceki günün seferine ait açık diyalog kalmasın.
watch(tarih, () => {
  diyalog.value = null
  diger.value = null
  bilgi.value = ''
  hizliHata.value = ''
  geriAl.temizle()
})

function adayAta(aday: Aday) {
  const s = seciliSatir.value
  if (!s || hizliKilit.value) return
  // D1/B-01: liste yüklenirken ya da başka sefer/role aitken (bayat) hiçbir atama başlamaz
  if (masa.adayYukleniyor.value || masa.adayAnahtari.value !== `${s.seferId}:${rol.value}`) return
  bilgi.value = ''
  hizliHata.value = ''
  // yolcuda "değiştir" yoktur: bir seferde birden çok yolcu olabilir (F2b)
  const mevcut =
    rol.value === 'YOLCU' ? null : (s.makinistler.find((m) => m.rol === rol.value) ?? null)
  // OK-34 (MK-150): temiz aday + boş rol → onaysız ata + Geri al; uyarı/engel/değiştir/yolcu mevcut diyalogla
  if (hizliAtanabilir(aday, rol.value, mevcut)) {
    void hizliAta(s, aday)
    return
  }
  diyalog.value = { seferId: s.seferId, etiket: seferEtiketi(s), aday, rol: rol.value, mevcut }
}

const seferKisa = (s: GunSatiri) => `${trenAdi(s)} ${saatBicimle(s.guncelKalkis)}`

/** Odak gövdede kaldıysa (Geri al düğmesi/aday satırı DOM'dan kalktı) seçili satıra, yoksa panel başlığına taşı (WCAG 2.4.3). */
async function odakOnar() {
  await nextTick()
  const a = document.activeElement
  if (a && a !== document.body && a.isConnected) return
  const hedef = liste.value?.querySelector<HTMLElement>('tr.secili') ?? panelBasligi.value
  hedef?.focus()
}
watch(
  () => geriAl.bekleyen.value,
  (b) => {
    if (!b) void odakOnar() // süre doldu ya da kullanıldı
  },
)

/** Odak aday panelindeyse yeni seferin adayları yüklenince Önerilen satırına taşınır (MK-154: odak çalınmaz). */
let odakBekliyor = false
watch(masa.adayYukleniyor, (yukleniyor) => {
  if (yukleniyor || !odakBekliyor) return
  odakBekliyor = false
  void nextTick(() => {
    if (!adayPaneli.value?.odakla()) panelBasligi.value?.focus()
  })
})
const panelOdakta = () => !!panel.value?.contains(document.activeElement)

/** OK-34/O1: temiz adayı onaysız ata, ASIL'da seçimi sıradaki boş sefere ilerlet, Geri al şeridini başlat. */
async function hizliAta(s: GunSatiri, aday: Aday) {
  hizliKilit.value = true
  const rolu = rol.value
  const paneldeydi = panelOdakta()
  try {
    const sonuc = await atamaApi.ata(s.seferId, { makinistId: aday.makinistId, rol: rolu })
    // Sıradaki sefer eski listeden bulunur (şu anki sefer zaten dışlanır); yeniden okuma sonra yapılır.
    const sonraki = rolu === 'ASIL' ? sonrakiBos(gorunenSatirlar.value, s.seferId) : null
    const sonrakiSatir = satirlar.value.find((x) => x.seferId === sonraki)
    const rolEki = rolu === 'ASIL' ? '' : ` (${ROL_ETIKETLERI[rolu]})`
    let mesaj = `Atandı${rolEki}: ${sonuc.ad} (${sonuc.sicil}) → ${seferKisa(s)}.`
    if (rolu === 'ASIL')
      mesaj += sonrakiSatir ? ` Sıradaki: ${seferKisa(sonrakiSatir)}.` : ' Boş sefer kalmadı.'
    mesaj += ' Geri almak için Ctrl+Z.'
    geriAl.baslat({ atamaId: sonuc.atamaId, seferId: s.seferId, rol: rolu, mesaj })
    if (sonraki !== null) {
      odakBekliyor = paneldeydi
      masa.sec(sonraki) // adaylar izleyici ile yüklenir
      await Promise.all([
        masa.gunuYukle(),
        masa.kirmiziYukle(),
        masa.gordumYukle(),
        masa.takasYukle(),
      ])
    } else {
      await masa.yenile()
    }
  } catch (e) {
    const ihlaller =
      e instanceof ApiHata ? (e.ihlaller ?? []).filter((i) => !e.detail.includes(i.metin)) : []
    hizliHata.value = [
      e instanceof ApiHata ? e.detail : 'Atama yapılamadı.',
      ...ihlaller.map((i) => i.metin),
    ].join(' ')
    await masa.yenile() // sunucu durumu bizim bildiğimizden farklı olabilir
  } finally {
    hizliKilit.value = false
  }
}

async function geriAlTetik() {
  const sonuc = await geriAl.geriAl()
  if (!sonuc) return
  if (sonuc.tur === 'hata') hizliHata.value = sonuc.mesaj
  else bilgi.value = sonuc.mesaj
  await masa.yenile()
  await odakOnar()
}

/** "Sıradaki boş" (düğme ve N): sonraki atanmamış sefere seçim + aday paneline odak; kalmadıysa bildirir. */
function siradakiBos() {
  const sonraki = sonrakiBos(gorunenSatirlar.value, seciliId.value)
  hizliHata.value = ''
  if (sonraki === null) {
    bilgi.value = 'Boş sefer kalmadı.'
    return
  }
  bilgi.value = ''
  odakBekliyor = true
  masa.sec(sonraki)
}

/** Tek harf kısayolu yalnız masa/panel odaktayken ve yazı alanı dışında (WCAG 2.1.4). Ctrl+Z modifiyelidir. */
function izgaraTus(e: KeyboardEvent) {
  const hedef = e.target as HTMLElement
  if (hedef.closest('input, select, textarea, [contenteditable], [role="menu"]')) return
  if (e.altKey || e.shiftKey) return
  if (!e.ctrlKey && !e.metaKey && e.key.toLowerCase() === 'n') {
    e.preventDefault()
    siradakiBos()
  } else if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'z' && geriAl.bekleyen.value) {
    e.preventDefault()
    void geriAlTetik()
  }
}

function menuSec(s: GunSatiri, kimlik: string) {
  if (kimlik === 'gecikme') digerAc({ tur: 'degisiklik', kip: 'gecikme', satir: s })
  else if (kimlik === 'iptal') digerAc({ tur: 'degisiklik', kip: 'iptal', satir: s })
  else if (kimlik === 'bol') digerAc({ tur: 'bol', satir: s })
  else if (kimlik === 'birlestir') digerAc({ tur: 'birlestir', satir: s })
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
      <div class="suzgec gorunum-anahtari" role="group" aria-label="Görünüm">
        <button
          type="button"
          class="btn btn-kucuk"
          :aria-pressed="gorunum === 'liste'"
          @click="gorunumSec('liste')"
        >
          Liste
        </button>
        <button
          type="button"
          class="btn btn-kucuk"
          :aria-pressed="gorunum === 'cizelge'"
          @click="gorunumSec('cizelge')"
        >
          Çizelge
        </button>
      </div>
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
        :aria-pressed="suzgec === 'atanmamis' || suzgec === 'bitmeyen'"
        data-test="atanmamis-sayac"
        title="Asıl makinisti olmayan seferler (iptaller hariç). Düğme: yalnız bunları listeler."
        @click="suzgecDugme(suzgec === 'bitmeyen' ? 'bitmeyen' : 'atanmamis')"
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
      <button
        v-if="masa.gordum.value"
        type="button"
        class="cip cip-uyari"
        :class="{ dolu: masa.gordum.value.gorulmeyen > 0 }"
        :aria-pressed="suzgec === 'gormeyen'"
        data-test="gordum-sayac"
        :title="`Seçili gün + sonraki 7 gün, kalkışı geçmemiş atamalar ve intikaller: ${masa.gordum.value.gorulen} gördü, ${masa.gordum.value.gorulmeyen} görmedi, ${masa.gordum.value.hesapsiz} personelin hesabı yok. Düğme: bu günün görmeyen satırlarını listeler.`"
        @click="suzgecDugme('gormeyen')"
      >
        <Simge ad="daire" boyut="1.1em" />Görülmeyen:
        <strong>{{ masa.gordum.value.gorulmeyen }}</strong>
        <span class="cip-alt"
          >telefon hesabı yok: <strong>{{ masa.gordum.value.hesapsiz }}</strong></span
        >
        <span v-if="masa.gordum.value.uyari > 0" class="cip-alt gordum-uyari">
          <Simge ad="uyari" boyut="1em" />{{ masa.gordum.value.uyari }} kalkışa yakın
        </span>
      </button>
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
      <button
        v-if="masa.gun.value && masa.atanmamisSayisi.value > 0"
        type="button"
        class="btn btn-kucuk"
        data-test="siradaki-bos"
        aria-keyshortcuts="N"
        title="Sıradaki atanmamış sefere geç (masa ya da panel odaktayken N)"
        @click="siradakiBos"
      >
        Sıradaki boş (N)
      </button>
      <span v-if="seciliSatir" class="ipucu" data-test="sag-ok-ipucu">ya da satırda → tuşu</span>
    </div>
    <p v-if="suzgec === 'bitmeyen'" class="soluk bitmeyen-notu" data-test="bitmeyen-notu">
      Yalnız henüz bitmeyen atanmamış seferler listeleniyor (ana sayfadaki sayı).
      <button
        type="button"
        class="btn btn-kucuk"
        data-test="bitenleri-goster"
        @click="suzgec = 'atanmamis'"
      >
        Bitenleri de göster
      </button>
    </p>

    <ul v-if="cizelgede" class="cizelge-aciklama" aria-hidden="true">
      <li><span class="cubuk cubuk-ornek cubuk-atanmis"></span>Atanmış</li>
      <li><span class="cubuk cubuk-ornek cubuk-bos"></span>Boş</li>
      <li><span class="cubuk cubuk-ornek cubuk-kirmizi"></span>Kontrol bekliyor</li>
      <li><span class="cubuk cubuk-ornek cubuk-iptal"></span>İptal</li>
      <li><span class="aciklama-gece"></span>Gece</li>
      <li><span class="aciklama-simdi"></span>Şimdi</li>
      <li><span class="aciklama-plan"></span>Planlanan kalkış (gecikmede)</li>
    </ul>

    <div role="status">
      <div
        v-if="geriAl.bekleyen.value"
        class="bildirim bildirim-bilgi geri-al-seridi"
        data-test="geri-al-seridi"
        @pointerenter="geriAl.duraklat()"
        @pointerleave="geriAl.surdur()"
        @focusin="geriAl.duraklat()"
        @focusout="geriAl.surdur()"
      >
        <span>{{ geriAl.bekleyen.value.mesaj }}</span>
        <button
          type="button"
          class="btn btn-kucuk"
          data-test="geri-al"
          aria-keyshortcuts="Control+Z"
          @click="geriAlTetik"
        >
          <Simge ad="geri-al" boyut="1.1em" />Geri al
        </button>
      </div>
      <p v-else-if="bilgi" class="bildirim bildirim-bilgi geri-al-seridi">{{ bilgi }}</p>
    </div>
    <p v-if="hizliHata" class="bildirim bildirim-hata" role="alert">{{ hizliHata }}</p>
    <p v-if="masa.gunHatasi.value" class="bildirim bildirim-hata" role="alert">
      {{ masa.gunHatasi.value }}
    </p>

    <IntikalSeridi :tarih="tarih" @degisti="masa.yenile()" />

    <div
      class="atama-izgara"
      :class="{ secimsiz: !seciliSatir }"
      :aria-busy="hizliKilit"
      @keydown="izgaraTus"
    >
      <div ref="liste" class="tablo-kap masa-kap">
        <table
          class="tablo masa-tablo"
          :class="{ 'masa-tablo--cizelge': cizelgede }"
          :aria-label="`${tarihBicimle(tarih)} seferleri`"
        >
          <thead>
            <tr>
              <th class="kol-saat">Saat</th>
              <th class="kol-tren">Tren no</th>
              <th class="kol-guzergah">Güzergâh</th>
              <th class="kol-ayrinti">Araç · Hat</th>
              <th v-if="cizelge" class="kol-zaman">
                <span class="gorunmez">Zaman</span>
                <div class="zaman-ekseni" aria-hidden="true">
                  <span
                    v-for="e in cizelge.etiketler"
                    :key="e.etiket"
                    :style="{ left: e.yuzde + '%' }"
                    >{{ e.etiket }}</span
                  >
                </div>
              </th>
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
              <td
                class="kol-tren"
                :data-guzergah="guzergah(s)"
                :data-saat="saatBicimle(s.guncelKalkis)"
              >
                <span class="tren-no">{{ trenAdi(s) }}</span>
              </td>
              <td class="kol-guzergah">
                <span class="guzergah">{{ guzergah(s) }}</span>
              </td>
              <td class="kol-ayrinti">
                <span>{{ s.aracTipi }}</span> <span class="soluk">{{ s.hat }}</span>
              </td>
              <td v-if="cizelge" class="kol-zaman">
                <ZamanSeridi
                  :cubuk="cubukHesapla(s, tarih, cizelge.p)"
                  :durum="cubukDurumu(s)"
                  :etiket="cubukEtiketi(s)"
                  :yazi="guzergah(s)"
                  :bantlar="cizelge.bantlar"
                  :simdi="cizelge.simdi"
                  :izgara-sol="cizelge.izgara.solYuzde"
                  :izgara-adim="cizelge.izgara.adimYuzde"
                />
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
                    <span class="gorevli-ad" :title="cizelgede ? kisi(m) : undefined"
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
                    <span
                      v-if="yakinlikMetni(m)"
                      class="gosterge gosterge-yakinlik"
                      data-test="yakinlik"
                      :title="yakinlikMetni(m)"
                      ><Simge ad="bilgi" boyut="1em" /><span class="gorunmez">{{
                        yakinlikMetni(m)
                      }}</span></span
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
                    <div
                      v-if="m.kirmizi"
                      class="kirmizi-neden"
                      :title="cizelgede ? m.kirmiziNedenler.join('; ') : undefined"
                    >
                      <Simge ad="engel" boyut="1em" />{{ m.kirmiziNedenler.join('; ') }}
                    </div>
                    <div
                      v-if="m.uyarilar.length > 0"
                      class="uyari-notu"
                      :title="cizelgede ? `Uyarı: ${m.uyarilar.join('; ')}` : undefined"
                    >
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
                  :title="cizelgede ? durumMetni(s) : undefined"
                  boyut="sm"
                />
                <template v-else>
                  <Rozet
                    v-if="s.kirmizi"
                    ton="engel"
                    simge="engel"
                    metin="KONTROL"
                    :title="cizelgede ? 'KONTROL' : undefined"
                    boyut="sm"
                  />
                  <span
                    v-if="s.kalkisGecikmeDk > 0"
                    class="gecikme"
                    :title="cizelgede ? gecikmeMetni(s) : undefined"
                  >
                    <Simge ad="gecikme" boyut="1em" /><span class="gecikme-metin">{{
                      gecikmeMetni(s)
                    }}</span>
                  </span>
                </template>
              </td>
              <td class="kol-islem">
                <div class="islem-dugmeleri">
                  <template v-if="s.durum !== 'IPTAL'">
                    <button
                      v-if="!asil(s)"
                      type="button"
                      :tabindex="tb(s)"
                      class="btn btn-kucuk btn-birincil"
                      :aria-label="`${trenAdi(s)} seferine ata`"
                      @click.stop="adaylaraGec(s.seferId)"
                    >
                      <Simge ad="kisi" boyut="1.1em" /><span class="btn-metin">Ata</span>
                    </button>
                    <button
                      v-else
                      type="button"
                      :tabindex="tb(s)"
                      class="btn btn-kucuk"
                      :class="{ 'btn-birincil': s.kirmizi }"
                      :aria-label="`${trenAdi(s)} seferinde yerine ata`"
                      @click.stop="digerAc({ tur: 'yerine', satir: s })"
                    >
                      <Simge ad="kisi-degistir" boyut="1.1em" /><span class="btn-metin"
                        >Yerine ata</span
                      >
                    </button>
                    <EylemMenusu
                      :etiket="`${trenAdi(s)} seferi için diğer işlemler`"
                      :ogeler="menuOgeleri(s)"
                      :sekme-sirasi="tb(s)"
                      @sec="menuSec(s, $event)"
                    />
                  </template>
                </div>
              </td>
            </tr>
            <tr v-if="masa.gun.value === null && !masa.gunHatasi.value">
              <td colspan="8">
                <VeriDurumu durum="yukleniyor" metin="Yükleniyor…" />
              </td>
            </tr>
            <tr v-else-if="satirlar.length === 0 && !masa.gunHatasi.value">
              <td colspan="8">
                <VeriDurumu durum="bos" metin="Bu gün için sefer yok." />
              </td>
            </tr>
            <tr v-else-if="satirlar.length > 0 && gorunenSatirlar.length === 0">
              <td colspan="8">
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
            ref="adayPaneli"
            v-model:rol="rol"
            :adaylar="adaylar"
            :yakinlik="masa.adayYakinlik.value"
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
/*
 * Çizelge görünümü (yalnız 1101 px üstü): Güzergâh ve Araç · Hat sütunlarının yerini Zaman alır (DOM'da kalırlar; yazdırmada geri gelir).
 * Güzergâh çubuğun yanında yazılıdır; tren numarasının altında Güzergâh yerine kalkış saati görünür (Saat sütunu gizlenir). 1100 px altında Liste düzeni geçerlidir.
 */
.kol-zaman {
  width: 40%;
  min-width: 12rem;
}
@media (min-width: 1101px) {
  .atama-izgara .masa-tablo.masa-tablo--cizelge .kol-guzergah,
  .atama-izgara .masa-tablo.masa-tablo--cizelge .kol-ayrinti,
  .atama-izgara .masa-tablo.masa-tablo--cizelge .kol-saat {
    display: none;
  }
  /* OK-42c: Saat sütunu Tren no hücresinin ikinci satırına iner (yalnız kalkış; tam aralık çubuğun adında): sütun payı Zaman'a gider. */
  .atama-izgara .masa-tablo.masa-tablo--cizelge .kol-tren::after {
    content: attr(data-saat);
    display: block;
    font-family: var(--yazi-ailesi);
    font-size: var(--yazi-xs);
    font-weight: var(--yazi-agirlik-normal);
    font-variant-numeric: tabular-nums;
    color: var(--renk-metin-ikincil);
  }
}
/*
 * OK-42: Çizelgede Zaman sütunu geniş olsun (1366 + aday paneli: ≥ 360 px) ve satır alçak kalsın (≤ 48 px; kırmızı satır dahil).
 * İşlem düğmeleri simge (adı aria-label'da, metin görünmez), Durum rozeti simge (metin title'da), gecikme metni simgeye iner (title'da), hücre dolgusu dar;
 * ihlal / uyarı metinleri tek satır, kesilir (tam metin title'da, aday panelinde). Liste görünümü etkilenmez.
 */
@media (min-width: 1101px) {
  /* Sabit düzen: dar sütunlar belli, Zaman kalan genişliği alır; Personel adı kesilir (tam ad title'da). */
  .masa-tablo--cizelge {
    table-layout: fixed;
  }
  .masa-tablo--cizelge th {
    box-sizing: border-box;
  }
  .masa-tablo--cizelge th.kol-zaman {
    width: auto;
    min-width: 0;
  }
  .masa-tablo--cizelge th.kol-tren {
    width: 3.5rem;
  }
  .masa-tablo--cizelge th.makinist-sutun {
    width: 8.5rem;
    min-width: 0;
  }
  .atama-izgara.secimsiz .masa-tablo--cizelge th.makinist-sutun {
    width: 22%;
  }
  .masa-tablo--cizelge th.kol-durum {
    width: 2.75rem;
  }
  .masa-tablo--cizelge th.kol-islem {
    width: 4.25rem;
  }
  .masa-tablo--cizelge th,
  .masa-tablo--cizelge td {
    padding-left: var(--aralik-2);
    padding-right: var(--aralik-2);
  }
  .masa-tablo--cizelge td {
    padding-top: var(--aralik-1);
    padding-bottom: var(--aralik-1);
  }
  .masa-tablo--cizelge .kol-islem .btn-metin,
  .masa-tablo--cizelge .kol-durum :deep(.rozet-metin),
  .masa-tablo--cizelge .gecikme-metin {
    position: absolute;
    width: 1px;
    height: 1px;
    overflow: hidden;
    clip-path: inset(50%);
    white-space: nowrap;
  }
  .masa-tablo--cizelge .kol-islem .btn-kucuk {
    padding: var(--aralik-1);
    min-width: 1.75rem;
  }
  .masa-tablo--cizelge .gorevli {
    display: flex;
    max-width: 100%;
    gap: 0 var(--aralik-1); /* dar Personel hücresinde ad daha çok yer bulsun */
    padding-left: var(--aralik-2);
  }
  .masa-tablo--cizelge .gorevli-ad {
    flex: 1 1 0; /* kalan yeri alır, kesilir; gördü simgesi ve kaldır düğmesi aynı satırda kalır */
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .masa-tablo--cizelge .kaldir-dugme {
    min-width: 1.5rem;
    min-height: 1.5rem;
    padding: 0;
  }
  .masa-tablo--cizelge .kirmizi-neden,
  .masa-tablo--cizelge .uyari-notu,
  .masa-tablo--cizelge .iptal-notu {
    flex: 1 1 100%; /* notlar kendi satırında */
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}
@media (min-width: 1500px) {
  .atama-izgara .masa-tablo--cizelge th.makinist-sutun {
    width: 20%;
  }
}
@media (max-width: 1100px) {
  .kol-zaman,
  .gorunum-anahtari,
  .cizelge-aciklama {
    display: none;
  }
}
/* D1/QA-5: Geri al şeridi akışta yer tutmaz; açılıp kapanırken tablo ve aday paneli kaymaz (yanlış satıra tık olmasın). */
.geri-al-seridi {
  position: fixed;
  inset-inline: 0;
  bottom: var(--aralik-5);
  z-index: 20;
  width: fit-content;
  max-width: min(46rem, calc(100vw - 2rem));
  margin: 0 auto;
  box-shadow: var(--golge-2);
}
.bitmeyen-notu {
  margin: 0 0 var(--aralik-3);
}
.zaman-ekseni {
  container-type: inline-size;
  position: relative;
  height: 1rem;
  margin: 0 0.75rem;
  font-family: var(--yazi-mono);
  font-size: var(--yazi-xs);
  font-weight: var(--yazi-agirlik-orta);
  color: var(--renk-metin-ikincil);
}
.zaman-ekseni span {
  position: absolute;
  top: 0;
  transform: translateX(-50%);
}
/* Dar eksende (aday paneli açık, 1366) etiketler dört saatte bire iner; ızgara çizgileri iki saatte bir kalır. */
@container (max-width: 17rem) {
  .zaman-ekseni span:nth-child(even) {
    display: none;
  }
}
.kol-zaman .zaman-seridi {
  margin: 0 0.5rem;
}
.zaman-ekseni {
  margin: 0 0.5rem;
}
.cizelge-aciklama {
  display: flex;
  flex-wrap: wrap;
  gap: var(--aralik-2) var(--aralik-5);
  margin: 0 0 var(--aralik-3);
  padding: 0;
  list-style: none;
  color: var(--renk-metin-ikincil);
  font-size: var(--yazi-xs);
}
.cizelge-aciklama li {
  display: inline-flex;
  align-items: center;
  gap: var(--aralik-2);
}
.aciklama-gece {
  width: 1.5rem;
  height: 0.75rem;
  background: var(--renk-cubuk-gece);
  border: 1px solid var(--renk-cizgi);
}
.aciklama-simdi {
  width: 2px;
  height: 0.9rem;
  background: var(--renk-cubuk-simdi);
}
.aciklama-plan {
  height: 0.9rem;
  border-left: 2px dotted var(--renk-metin-ikincil);
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
  /* Yazdırmada Liste kullanılır: çizelge sütunu çıkar, Güzergâh geri gelir. */
  .kol-zaman,
  .cizelge-aciklama {
    display: none;
  }
  .atama-izgara .masa-tablo.masa-tablo--cizelge .kol-guzergah,
  .atama-izgara .masa-tablo.masa-tablo--cizelge .kol-saat {
    display: table-cell;
  }
  .atama-izgara .masa-tablo.masa-tablo--cizelge .kol-tren::after {
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
