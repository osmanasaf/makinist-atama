<script setup lang="ts">
import { computed, onBeforeUnmount, nextTick, onMounted, ref } from 'vue'
import { ApiHata } from '@/api/istemci'
import Rozet from '@/bilesenler/Rozet.vue'
import SayfaBasligi from '@/bilesenler/SayfaBasligi.vue'
import Simge from '@/bilesenler/Simge.vue'
import VeriDurumu from '@/bilesenler/VeriDurumu.vue'
import { NEDEN_ETIKETLERI, ROL_ETIKETLERI, type AtamaRolu } from '@/features/atama/atamaApi'
import { ARAC_ETIKETLERI } from '@/features/intikal/intikalApi'
import { gunKaydir, kalanMetni, saatBicimle, tarihBicimle, zamanBicimle } from '@/lib/tarih'
import { trenAdi } from '@/lib/trenAdi'
import type { TakasTuru } from '@/features/takas/takasTipleri'
import { benimApi, type BenimGorev, type BenimGorevler, type BenimIntikal } from './benimApi'
import { onayBekleyen, rozetMetni, rozetYaz } from './rozet'
import TakasFormu from './TakasFormu.vue'
import { useYoklama } from './useYoklama'

const kok = ref<HTMLElement | null>(null)
const veri = ref<BenimGorevler | null>(null)
/** "Sonraki görev" ve kalan süre bu ana göre hesaplanır; yoklama (60 sn) her yüklemede tazeler. */
const simdi = ref(Date.now())
const yukleniyor = ref(true)
const hata = ref('')
const islemHatasi = ref('')
/** "Gördüm" gönderilen atama / kapatılan kalem anahtarları: çift basış engellenir. */
const gonderilen = ref(new Set<string>())

/** Açık takas / bırakma formu (hangi görev, hangi tür). */
const takasFormu = ref<{ gorev: BenimGorev; tur: TakasTuru } | null>(null)
const takasBilgisi = ref('')
const takasAcilabilir = (g: BenimGorev) =>
  g.durum !== 'IPTAL' && new Date(g.guncelKalkis).getTime() > Date.now()
/** Bu görev zaten açık bir takas / bırakma talebinde (UX-3): yeni talep düğmesi yerine işaret. */
const takasAcik = (g: BenimGorev) => veri.value?.takasAcikAtamalar?.includes(g.atamaId) ?? false

let bilgiZamanlayici: ReturnType<typeof setTimeout> | undefined
/** Bilgi metni kalıcı kalmaz (UX-5): 10 sn sonra ya da sayfadan çıkınca silinir. */
onBeforeUnmount(() => clearTimeout(bilgiZamanlayici))

function takasOlustu() {
  takasFormu.value = null
  takasBilgisi.value =
    'Talebiniz gönderildi. Durumunu "Takas taleplerim" sayfasında izleyebilirsiniz.'
  clearTimeout(bilgiZamanlayici)
  bilgiZamanlayici = setTimeout(() => (takasBilgisi.value = ''), 10_000)
  rozetYaz('Takas talebi gönderildi')
  void yukle(true)
}

/** Rozet bölgesi (aria-live): görev sayısı; yeni / değişen / kaldırılan varsa sayıları da okunur. */
function rozetGuncelle(v: BenimGorevler) {
  const tumu = [...v.gorevler, ...(v.intikaller ?? [])]
  const n = tumu.filter((g) => g.durum !== 'IPTAL').length // intikal de görevdir (UX-2)
  const yeni = tumu.filter((g) => g.gordu === 'YENI').length
  const degisti = tumu.filter((g) => g.gordu === 'DEGISTI').length
  const kaldirildi = v.kaldirilanlar.length
  onayBekleyen.value = tumu.filter((g) => g.gordu !== 'GORULDU').length + kaldirildi // alt sekme rozeti
  const takas = v.bekleyenTakas ?? 0
  rozetYaz(
    `${n} görev listelendi` +
      (yeni + degisti + kaldirildi + takas > 0
        ? `. Yeni: ${yeni}, Değişti: ${degisti}, Kaldırıldı: ${kaldirildi}` +
          (takas > 0 ? `, Takas: ${takas}` : '')
        : ''),
  )
}

/** sessiz: yoklama yenilemesi; yükleniyor/hata ekranına dönmez, geçici hata mevcut listeyi bozmaz. */
async function yukle(sessiz = false) {
  if (!sessiz) {
    yukleniyor.value = true
    hata.value = ''
  }
  try {
    const yeni = await benimApi.gorevler()
    simdi.value = Date.now()
    veri.value = yeni
    rozetGuncelle(yeni)
  } catch (e) {
    if (!sessiz) hata.value = e instanceof ApiHata ? e.detail : 'Görevler yüklenemedi.'
  } finally {
    if (!sessiz) yukleniyor.value = false
  }
}
onMounted(() => yukle())
useYoklama(() => yukle(true))

/**
 * `mesaj`: başarıda canlı bölgeye yazılır ("Gördünüz: T1" gibi; QA BUG-8). Eylem düğmesi başarıdan sonra kalkar: odak BODY'ye
 * düşmesin diye kartın kendisine (tabindex -1) verilir.
 */
async function gonder(anahtar: string, is: () => Promise<unknown>, mesaj = '') {
  if (gonderilen.value.has(anahtar)) return
  gonderilen.value.add(anahtar)
  islemHatasi.value = ''
  const kart =
    document.activeElement?.closest<HTMLElement>('article, section[data-test="sonraki-gorev"]') ??
    null
  try {
    await is()
    await yukle(true) // sunucudaki güncel durum (başka bir değişiklik de gelmiş olabilir)
    if (mesaj) rozetYaz(`${mesaj}. ${rozetMetni.value}`)
    await nextTick()
    if (kart?.isConnected && !document.activeElement?.closest('article')) {
      kart.tabIndex = -1
      kart.focus()
    } else if (kart && !kart.isConnected) odakYedegi() // kart başka bölüme geçti ya da kalktı (WCAG 2.4.3)
  } catch (e) {
    islemHatasi.value = e instanceof ApiHata ? e.detail : 'İşlem tamamlanamadı.'
    if (e instanceof ApiHata && e.status === 404) await yukle(true) // görev artık sizin değil ya da değişti
    if (e instanceof ApiHata && e.kod === 'OZET_DEGISTI') await bayatEkran(e) // ekrandaki özet eskidi: onay yazılmadı, yeni hâli gösterilir
  } finally {
    gonderilen.value.delete(anahtar)
  }
}

/** Eylemi yapılan kart kalkınca odak kaybolmasın: sıradaki onay kartı, o da yoksa sayfa başlığı. */
function odakYedegi() {
  const hedef =
    kok.value?.querySelector<HTMLElement>('section[data-onay] article') ??
    kok.value?.querySelector<HTMLElement>('h1')
  if (!hedef) return
  hedef.tabIndex = -1
  hedef.focus()
}

/** "Gördüm" ekranda GÖRDÜĞÜNÜZ özetle gider (bayat ekran uyuşmazlıkta 409): sunucunun döndüğü güncel görev kartı yerinde değiştirilir, kullanıcıdan yeniden onay istenir. */
const gordum = (g: BenimGorev) =>
  gonder(`g${g.atamaId}`, () => benimApi.gordum(g.atamaId, g.ozet), `Gördünüz: ${trenAdi(g)}`)

async function bayatEkran(e: ApiHata) {
  const guncel = e.gorev as (BenimGorev & BenimIntikal) | undefined
  if (guncel && veri.value && guncel.atamaId === undefined && guncel.id !== undefined) {
    veri.value = {
      ...veri.value,
      intikaller: (veri.value.intikaller ?? []).map((x) => (x.id === guncel.id ? guncel : x)),
    }
    rozetGuncelle(veri.value)
  } else if (guncel && veri.value && guncel.atamaId !== undefined) {
    veri.value = {
      ...veri.value,
      gorevler: veri.value.gorevler.map((x) => (x.atamaId === guncel.atamaId ? guncel : x)),
    }
    rozetGuncelle(veri.value)
  }
  await yukle(true)
}
/** Serbest intikal "gördüm" (MK-108): ekranda GÖRDÜĞÜNÜZ özetle; bayat ekran 409 OZET_DEGISTI aynı yoldan güncel kaydı getirir. */
const intikalGordum = (i: BenimIntikal) =>
  gonder(
    `i${i.id}`,
    () => benimApi.intikalGordum(i.id, i.ozet),
    `Gördünüz: ${i.kalkisGar} → ${i.varisGar} intikali`,
  )
const kalemKapat = (olayId: number) =>
  gonder(`o${olayId}`, () => benimApi.olayiKapat(olayId), 'Kalem kapatıldı')

/** Kalkış günü (İstanbul): intikal zamanı +03:00 ofsetli gelir, ilk 10 karakter yerel gündür. */
const intikalGunu = (i: BenimIntikal) => i.kalkis.slice(0, 10)

interface Oge {
  anahtar: string
  an: number
  gorev?: BenimGorev
  intikal?: BenimIntikal
}

const onayBekliyor = (o: Oge) => (o.gorev ?? o.intikal)?.gordu !== 'GORULDU'

/** Bugün / Yarın / sonraki günler (tarih başlığıyla) için sefer görevleri ve serbest intikaller kalkış sırasıyla KARIŞIK. Sunucu sırası (sefer görevleri) korunur; her intikal, kalkışı ilk daha geç görevin önüne girer (kararlı birleştirme). */
function gunOgeleri(t: (gun: string) => boolean): Oge[] {
  if (!veri.value) return []
  const sonuc: Oge[] = veri.value.gorevler
    .filter((g) => t(g.tarih))
    .map((g) => ({ anahtar: `g${g.atamaId}`, an: new Date(g.guncelKalkis).getTime(), gorev: g }))
  const yeniler = (veri.value.intikaller ?? [])
    .filter((i) => t(intikalGunu(i)))
    .map((i) => ({ anahtar: `i${i.id}`, an: new Date(i.kalkis).getTime(), intikal: i }))
    .sort((a, b) => a.an - b.an)
  for (const y of yeniler) {
    const yer = sonuc.findIndex((o) => o.an > y.an)
    sonuc.splice(yer < 0 ? sonuc.length : yer, 0, y)
  }
  return sonuc
}

interface Bolum {
  tur: 'onay' | 'kaldirilan' | 'sonraki' | 'gun'
  anahtar: string
  ad: string
  tarih?: string
  ogeler: Oge[]
}

/** Sıradaki iptal olmayan, bitmemiş görev ya da intikal (MK-157); sürüyorsa "Şu an görevde". */
const sonraki = computed(() => {
  if (!veri.value) return null
  const adaylar = [
    ...veri.value.gorevler
      .filter((g) => g.durum !== 'IPTAL' && new Date(g.guncelVaris).getTime() > simdi.value)
      .map((g) => ({
        anahtar: `g${g.atamaId}`,
        gorev: g,
        intikal: undefined as BenimIntikal | undefined,
        gordu: g.gordu,
        an: new Date(g.guncelKalkis).getTime(),
        saat: saatler(g),
        tren: trenAdi(g),
        guzergah: `${g.kalkisGar} → ${g.varisGar}`,
        rol: g.rol as string,
        onay: g.gordu !== 'GORULDU',
      })),
    ...(veri.value.intikaller ?? [])
      .filter((i) => i.durum !== 'IPTAL' && new Date(i.varis).getTime() > simdi.value)
      .map((i) => ({
        anahtar: `i${i.id}`,
        gorev: undefined as BenimGorev | undefined,
        intikal: i,
        gordu: i.gordu,
        an: new Date(i.kalkis).getTime(),
        saat: `${saatBicimle(i.kalkis)} – ${saatBicimle(i.varis)}`,
        tren: 'İntikal',
        guzergah: `${i.kalkisGar} → ${i.varisGar}`,
        rol: 'INTIKAL',
        onay: i.gordu !== 'GORULDU',
      })),
  ].sort((a, b) => a.an - b.an)
  return adaylar[0] ?? null
})

/**
 * Ekran sırası: DEĞİŞTİ (yeniden onay; acil) → sonraki görev → YENİ (onay bekliyor) → elinden alınanlar → Bugün/Yarın/… Onay bekleyenler gün
 * gruplarında TEKRAR çizilmez. Sonraki görev, çok sayıda YENİ kartın altına itilmesin diye YENİ'lerden ÖNCE gelir (375×812 ölçümü: 2 YENİ
 * kartla sonraki görev ilk ekranın dışına düşüyordu); DEĞİŞTİ nadirdir ve güvenlik gereği en üstte kalır.
 */
const bolumler = computed<Bolum[]>(() => {
  if (!veri.value) return []
  const { bugun, gorevler } = veri.value
  const intikaller = veri.value.intikaller ?? []
  const yarin = gunKaydir(bugun, 1)
  const liste: Bolum[] = []
  const bekleyenler = gunOgeleri(() => true).filter(onayBekliyor)
  const degisenler = bekleyenler.filter((o) => (o.gorev ?? o.intikal)?.gordu === 'DEGISTI')
  // Görülmemiş (YENİ) sonraki görevin Gördüm düğmesi Sonraki görev kartındadır; Yeni listesinde tekrar etmez (OK-41b).
  const kartta = sonraki.value?.gordu === 'YENI' ? sonraki.value.anahtar : null
  const yeniler = bekleyenler.filter(
    (o) => (o.gorev ?? o.intikal)?.gordu !== 'DEGISTI' && o.anahtar !== kartta,
  )
  if (degisenler.length > 0)
    liste.push({
      tur: 'onay',
      anahtar: 'degisti',
      ad: 'Değişti: yeniden onaylayın',
      ogeler: degisenler,
    })
  if (sonraki.value)
    liste.push({ tur: 'sonraki', anahtar: 'sonraki', ad: 'Sonraki görev', ogeler: [] })
  if (yeniler.length > 0)
    liste.push({ tur: 'onay', anahtar: 'yeni', ad: 'Yeni: onayınızı bekliyor', ogeler: yeniler })
  if (veri.value.kaldirilanlar.length > 0)
    liste.push({
      tur: 'kaldirilan',
      anahtar: 'kaldirilan',
      ad: 'Elinizden alınan görevler',
      ogeler: [],
    })
  const gunler = [
    { ad: 'Bugün', tarih: bugun },
    { ad: 'Yarın', tarih: yarin },
  ]
  const digerleri = new Set([...gorevler.map((g) => g.tarih), ...intikaller.map(intikalGunu)])
  for (const t of [...digerleri].filter((g) => g !== bugun && g !== yarin).sort())
    gunler.push({ ad: tarihBicimle(t), tarih: t })
  const hicGorevYok = gorevler.length + intikaller.length === 0
  for (const g of gunler) {
    const hepsi = gunOgeleri((x) => x === g.tarih)
    const ogeler = hepsi.filter((o) => !onayBekliyor(o))
    // Bugün boşsa (başka gün dolu) başlık + "Bugün göreviniz yok." satırı; onay bekleyen bugün görevi varsa "yok" denmez.
    const bugunBos = g.tarih === bugun && hepsi.length === 0 && !hicGorevYok
    if (ogeler.length > 0 || bugunBos) liste.push({ tur: 'gun', anahtar: g.tarih, ...g, ogeler })
  }
  return liste
})

/** Onay bekleyen kart gün gruplarında çizilmez; gününü kartın kendisi yazar (QA BUG-1). Gün başlığıyla aynı biçim. */
function gunEtiketi(tarih: string): string {
  const bugun = veri.value?.bugun ?? ''
  if (tarih === bugun) return `Bugün (${tarihBicimle(tarih)})`
  if (tarih === gunKaydir(bugun, 1)) return `Yarın (${tarihBicimle(tarih)})`
  return tarihBicimle(tarih)
}

function saatler(g: BenimGorev): string {
  return `${saatBicimle(g.guncelKalkis)} – ${saatBicimle(g.guncelVaris)}${g.varisErtesiGun ? ' (ertesi gün)' : ''}`
}
</script>

<template>
  <section ref="kok">
    <SayfaBasligi baslik="Görevlerim" />
    <VeriDurumu v-if="yukleniyor" durum="yukleniyor" metin="Yükleniyor…" />
    <VeriDurumu v-else-if="hata" durum="hata" :metin="hata">
      <template #eylem>
        <button type="button" class="btn" @click="yukle()">Yeniden dene</button>
      </template>
    </VeriDurumu>
    <template v-else-if="veri">
      <p v-if="islemHatasi" class="bildirim bildirim-hata" role="alert">{{ islemHatasi }}</p>
      <p v-if="takasBilgisi" class="bildirim bildirim-bilgi" role="status">{{ takasBilgisi }}</p>
      <VeriDurumu
        v-if="veri.gorevler.length + (veri.intikaller?.length ?? 0) === 0"
        durum="bos"
        :metin="`Önümüzdeki ${veri.ufukGun} gün için göreviniz yok.`"
      />
      <template v-for="grup in bolumler" :key="grup.anahtar">
        <section v-if="grup.tur === 'kaldirilan'" aria-label="Elinizden alınan görevler">
          <h2 class="benim-gun">Elinizden alınan görevler</h2>
          <article v-for="k in veri.kaldirilanlar" :key="k.olayId" class="kart benim-kart kalem">
            <p class="benim-satir">
              <Rozet
                ton="engel"
                simge="iptal"
                :metin="k.tur === 'DEGISTI_BASKASINA' ? 'Başkasına verildi' : 'Kaldırıldı'"
              />
            </p>
            <p>
              <strong v-if="k.trenNo">{{ k.trenNo }}</strong>
              <template v-if="k.tarih"> · {{ tarihBicimle(k.tarih) }}</template>
            </p>
            <p>{{ k.ozet }}</p>
            <p v-if="k.nedenEtiketi" class="soluk">Neden: {{ NEDEN_ETIKETLERI[k.nedenEtiketi] }}</p>
            <button
              type="button"
              class="btn btn-birincil"
              :disabled="gonderilen.has('o' + k.olayId)"
              :aria-label="`${k.trenNo ?? 'Görev'} kalemini gördüm`"
              @click="kalemKapat(k.olayId)"
            >
              Gördüm
            </button>
          </article>
        </section>
        <section
          v-else-if="grup.tur === 'sonraki' && sonraki"
          aria-label="Sonraki görev"
          data-test="sonraki-gorev"
        >
          <h2 class="benim-gun">Sonraki görev</h2>
          <div class="kart benim-kart sonraki-kart">
            <p class="benim-satir">
              <Rozet
                ton="bilgi"
                simge="saat"
                :metin="sonraki.an <= simdi ? 'Şu an görevde' : kalanMetni(sonraki.an, simdi)"
              />
              <Rozet v-if="sonraki.onay" ton="uyari" simge="uyari" metin="Onay bekliyor" />
            </p>
            <p class="benim-saat">{{ sonraki.saat }}</p>
            <p class="benim-guzergah">
              <strong>{{ sonraki.tren }}</strong> · {{ sonraki.guzergah }}
            </p>
            <p class="benim-satir">
              <Rozet v-if="sonraki.rol === 'INTIKAL'" rol="YOLCU" metin="İntikal" />
              <Rozet
                v-else
                :rol="sonraki.rol as AtamaRolu"
                :metin="ROL_ETIKETLERI[sonraki.rol as AtamaRolu]"
              />
            </p>
            <button
              v-if="sonraki.gordu === 'YENI'"
              type="button"
              class="btn btn-birincil"
              :disabled="gonderilen.has(sonraki.anahtar)"
              :aria-label="
                sonraki.gorev
                  ? `${sonraki.tren} görevini gördüm`
                  : `${sonraki.guzergah} intikalini gördüm`
              "
              @click="sonraki.gorev ? gordum(sonraki.gorev) : intikalGordum(sonraki.intikal!)"
            >
              Gördüm
            </button>
          </div>
        </section>
        <section v-else :aria-label="grup.ad" :data-onay="grup.tur === 'onay' || undefined">
          <h2 class="benim-gun">
            {{ grup.ad }}
            <small v-if="grup.tarih && grup.ad !== tarihBicimle(grup.tarih)"
              >({{ tarihBicimle(grup.tarih) }})</small
            >
          </h2>
          <p v-if="grup.ogeler.length === 0" class="soluk" data-test="bugun-bos">
            Bugün göreviniz yok.
          </p>
          <template v-for="o in grup.ogeler" :key="o.anahtar">
            <template v-if="o.gorev">
              <article
                v-for="g in [o.gorev]"
                :key="g.atamaId"
                class="kart benim-kart"
                :class="[
                  { iptal: g.durum === 'IPTAL' },
                  g.durum !== 'IPTAL' && g.gordu === 'YENI' && 'yeni',
                  g.durum !== 'IPTAL' && g.gordu === 'DEGISTI' && 'degisti',
                ]"
              >
                <p v-if="g.durum === 'IPTAL'" class="benim-satir">
                  <Rozet ton="engel" simge="iptal" metin="İPTAL" />
                </p>
                <p v-if="grup.tur === 'onay'" class="benim-satir" data-test="kart-gunu">
                  <strong>{{ gunEtiketi(g.tarih) }}</strong>
                </p>
                <p class="benim-saat">{{ saatler(g) }}</p>
                <p class="benim-guzergah">
                  <strong>{{ trenAdi(g) }}</strong> · {{ g.kalkisGar }} → {{ g.varisGar }}
                </p>
                <p class="benim-satir">
                  <Rozet :rol="g.rol" :metin="ROL_ETIKETLERI[g.rol]" />
                  <Rozet
                    v-if="g.kalkisGecikmeDk > 0"
                    ton="uyari"
                    simge="gecikme"
                    :metin="`${g.kalkisGecikmeDk} dk gecikme (planlanan ${saatBicimle(g.planlananKalkis)})`"
                  />
                </p>
                <p v-if="g.rol === 'YOLCU'" class="soluk" data-test="yolcu-notu">
                  Yolcu olarak gidiyorsunuz: bu sefer sürüş göreviniz değildir.
                </p>
                <p v-if="g.esMakinist" class="soluk">Yanınızdaki görevli: {{ g.esMakinist }}</p>
                <p v-if="g.gordu === 'GORULDU'" class="benim-satir">
                  <Rozet ton="basari" simge="basari" metin="Gördünüz" />
                  <small v-if="g.gorduZamani">({{ zamanBicimle(g.gorduZamani) }})</small>
                </p>
                <template v-else>
                  <p class="benim-satir">
                    <Rozet
                      v-if="g.gordu === 'DEGISTI'"
                      ton="uyari"
                      simge="uyari"
                      metin="DEĞİŞTİ — yeniden onaylayın"
                    />
                    <Rozet v-else ton="bilgi" simge="bilgi" metin="YENİ" />
                  </p>
                  <p v-if="g.gordu === 'DEGISTI' && g.eskiOzet" class="degisiklik-ozeti">
                    Eski: {{ g.eskiOzet }}<br />
                    Yeni: {{ g.ozet }}
                  </p>
                  <button
                    type="button"
                    class="btn btn-birincil"
                    :disabled="gonderilen.has('g' + g.atamaId)"
                    :aria-label="`${trenAdi(g)} görevini gördüm`"
                    @click="gordum(g)"
                  >
                    Gördüm
                  </button>
                </template>
                <p v-if="takasAcik(g)" class="benim-satir">
                  <Rozet ton="notr" simge="saat" metin="Takas talebi açık" data-test="takas-acik" />
                </p>
                <div
                  v-else-if="g.rol !== 'YOLCU' && takasAcilabilir(g)"
                  class="benim-takas-dugmeleri"
                >
                  <button
                    type="button"
                    class="btn"
                    :aria-label="`${trenAdi(g)} görevi için takas iste`"
                    @click="takasFormu = { gorev: g, tur: 'TAKAS' }"
                  >
                    Takas iste
                  </button>
                  <button
                    type="button"
                    class="btn btn-hayalet"
                    :aria-label="`${trenAdi(g)} görevini bırak`"
                    @click="takasFormu = { gorev: g, tur: 'BIRAK' }"
                  >
                    Bırak
                  </button>
                </div>
              </article>
            </template>
            <article
              v-else-if="o.intikal"
              class="kart benim-kart intikal-karti"
              :class="[
                { iptal: o.intikal.durum === 'IPTAL' },
                o.intikal.durum !== 'IPTAL' && o.intikal.gordu === 'YENI' && 'yeni',
                o.intikal.durum !== 'IPTAL' && o.intikal.gordu === 'DEGISTI' && 'degisti',
              ]"
              data-test="intikal-karti"
            >
              <p v-if="o.intikal.durum === 'IPTAL'" class="benim-satir">
                <Rozet ton="engel" simge="iptal" metin="İPTAL" />
              </p>
              <p v-if="grup.tur === 'onay'" class="benim-satir" data-test="kart-gunu">
                <strong>{{ gunEtiketi(intikalGunu(o.intikal)) }}</strong>
              </p>
              <p class="benim-saat">
                {{ saatBicimle(o.intikal.kalkis) }} – {{ saatBicimle(o.intikal.varis) }}
              </p>
              <p class="benim-guzergah">
                <strong>İntikal</strong> · {{ o.intikal.kalkisGar }} → {{ o.intikal.varisGar }}
              </p>
              <p class="benim-satir">
                <Rozet rol="YOLCU" metin="İntikal" />
                <Rozet ton="notr" :metin="ARAC_ETIKETLERI[o.intikal.aracTuru]" />
              </p>
              <p class="soluk" data-test="intikal-notu">
                Sefer değil, servis / otobüs / taksi ile gar değiştirme: sürüş göreviniz değildir.
              </p>
              <p v-if="o.intikal.gordu === 'GORULDU'" class="benim-satir">
                <Rozet ton="basari" simge="basari" metin="Gördünüz" />
                <small v-if="o.intikal.gorduZamani"
                  >({{ zamanBicimle(o.intikal.gorduZamani) }})</small
                >
              </p>
              <template v-else>
                <p class="benim-satir">
                  <Rozet
                    v-if="o.intikal.gordu === 'DEGISTI'"
                    ton="uyari"
                    simge="uyari"
                    metin="DEĞİŞTİ — yeniden onaylayın"
                  />
                  <Rozet v-else ton="bilgi" simge="bilgi" metin="YENİ" />
                </p>
                <p
                  v-if="o.intikal.gordu === 'DEGISTI' && o.intikal.eskiOzet"
                  class="degisiklik-ozeti"
                >
                  Eski: {{ o.intikal.eskiOzet }}<br />
                  Yeni: {{ o.intikal.ozet }}
                </p>
                <button
                  type="button"
                  class="btn btn-birincil"
                  :disabled="gonderilen.has('i' + o.intikal.id)"
                  :aria-label="`${o.intikal.kalkisGar} → ${o.intikal.varisGar} intikalini gördüm`"
                  @click="intikalGordum(o.intikal)"
                >
                  Gördüm
                </button>
              </template>
            </article>
          </template>
        </section>
      </template>
      <div class="benim-kisayollar">
        <a href="#/benim/konaklama" class="benim-kisayol" data-test="konaklama-baglanti">
          <Simge ad="yatak" boyut="1.5rem" /> Konaklamam
        </a>
        <a href="#/benim/takas" class="benim-kisayol" data-test="takas-baglanti">
          <Simge ad="takas" boyut="1.5rem" /> Takas taleplerim
          <Rozet
            v-if="(veri.bekleyenTakas ?? 0) > 0"
            ton="uyari"
            boyut="sm"
            :metin="`Takas: ${veri.bekleyenTakas}`"
          />
        </a>
      </div>
      <TakasFormu
        v-if="takasFormu"
        :gorev="takasFormu.gorev"
        :tur="takasFormu.tur"
        @kapat="takasFormu = null"
        @olustu="takasOlustu"
      />
    </template>
  </section>
</template>
