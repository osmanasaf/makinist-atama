<script setup lang="ts">
import { computed, onBeforeUnmount, nextTick, onMounted, ref } from 'vue'
import { ApiHata } from '@/api/istemci'
import Rozet from '@/bilesenler/Rozet.vue'
import SayfaBasligi from '@/bilesenler/SayfaBasligi.vue'
import Simge from '@/bilesenler/Simge.vue'
import VeriDurumu from '@/bilesenler/VeriDurumu.vue'
import { NEDEN_ETIKETLERI, ROL_ETIKETLERI } from '@/features/atama/atamaApi'
import { ARAC_ETIKETLERI } from '@/features/intikal/intikalApi'
import { gunKaydir, saatBicimle, tarihBicimle, zamanBicimle } from '@/lib/tarih'
import { trenAdi } from '@/lib/trenAdi'
import type { TakasTuru } from '@/features/takas/takasTipleri'
import { benimApi, type BenimGorev, type BenimGorevler, type BenimIntikal } from './benimApi'
import { rozetMetni, rozetYaz } from './rozet'
import TakasFormu from './TakasFormu.vue'
import { useYoklama } from './useYoklama'

const veri = ref<BenimGorevler | null>(null)
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
    veri.value = await benimApi.gorevler()
    rozetGuncelle(veri.value)
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
  const kart = document.activeElement?.closest('article') ?? null
  try {
    await is()
    await yukle(true) // sunucudaki güncel durum (başka bir değişiklik de gelmiş olabilir)
    if (mesaj) rozetYaz(`${mesaj}. ${rozetMetni.value}`)
    await nextTick()
    if (kart?.isConnected && !document.activeElement?.closest('article')) {
      kart.tabIndex = -1
      kart.focus()
    }
  } catch (e) {
    islemHatasi.value = e instanceof ApiHata ? e.detail : 'İşlem tamamlanamadı.'
    if (e instanceof ApiHata && e.status === 404) await yukle(true) // görev artık sizin değil ya da değişti
    if (e instanceof ApiHata && e.kod === 'OZET_DEGISTI') await bayatEkran(e) // ekrandaki özet eskidi: onay yazılmadı, yeni hâli gösterilir
  } finally {
    gonderilen.value.delete(anahtar)
  }
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

/** Bugün / Yarın / sonraki günler (tarih başlığıyla) grupları; sefer görevleri ve serbest intikaller kalkış sırasıyla KARIŞIK; boş grup çizilmez. */
const gruplar = computed(() => {
  if (!veri.value) return []
  const { bugun, gorevler } = veri.value
  const intikaller = veri.value.intikaller ?? []
  const yarin = gunKaydir(bugun, 1)
  const ogeler = (t: (gun: string) => boolean): Oge[] => {
    // Sunucu sırası (sefer görevleri) korunur; her intikal, kalkışı ilk daha geç görevin önüne girer (kararlı birleştirme).
    const sonuc: Oge[] = gorevler
      .filter((g) => t(g.tarih))
      .map((g) => ({ anahtar: `g${g.atamaId}`, an: new Date(g.guncelKalkis).getTime(), gorev: g }))
    const yeniler = intikaller
      .filter((i) => t(intikalGunu(i)))
      .map((i) => ({ anahtar: `i${i.id}`, an: new Date(i.kalkis).getTime(), intikal: i }))
      .sort((a, b) => a.an - b.an)
    for (const y of yeniler) {
      const yer = sonuc.findIndex((o) => o.an > y.an)
      sonuc.splice(yer < 0 ? sonuc.length : yer, 0, y)
    }
    return sonuc
  }
  const liste = [
    { ad: 'Bugün', tarih: bugun, ogeler: ogeler((g) => g === bugun) },
    { ad: 'Yarın', tarih: yarin, ogeler: ogeler((g) => g === yarin) },
  ]
  const gunler = new Set([...gorevler.map((g) => g.tarih), ...intikaller.map(intikalGunu)])
  for (const t of [...gunler].filter((g) => g !== bugun && g !== yarin).sort()) {
    liste.push({ ad: tarihBicimle(t), tarih: t, ogeler: ogeler((g) => g === t) })
  }
  return liste.filter((g) => g.ogeler.length > 0)
})

function saatler(g: BenimGorev): string {
  return `${saatBicimle(g.guncelKalkis)} – ${saatBicimle(g.guncelVaris)}${g.varisErtesiGun ? ' (ertesi gün)' : ''}`
}
</script>

<template>
  <section>
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
      <section v-if="veri.kaldirilanlar.length > 0" aria-label="Elinizden alınan görevler">
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
      <VeriDurumu
        v-if="gruplar.length === 0"
        durum="bos"
        :metin="`Önümüzdeki ${veri.ufukGun} gün için göreviniz yok.`"
      />
      <section v-for="grup in gruplar" :key="grup.tarih" :aria-label="grup.ad">
        <h2 class="benim-gun">
          {{ grup.ad }}
          <small v-if="grup.ad !== tarihBicimle(grup.tarih)"
            >({{ tarihBicimle(grup.tarih) }})</small
          >
        </h2>
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
                  class="btn"
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
