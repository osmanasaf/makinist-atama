<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import { ApiHata } from '@/api/istemci'
import { sayiBicimle } from '@/lib/sayi'
import { bugun } from '@/lib/tarih'
import SayfaBasligi from '@/bilesenler/SayfaBasligi.vue'
import Simge from '@/bilesenler/Simge.vue'
import VeriDurumu from '@/bilesenler/VeriDurumu.vue'
import { masaBaglantisi } from '@/features/atama/masaBaglantisi'
import { TUR_ETIKETLERI, type PersonelTuru } from '@/features/makinist/makinistApi'
import { tanimApi, type TanimDto } from '@/features/tanim/tanimApi'
import { cizelgeApi, type CizelgeSatiri, type CizelgeYaniti } from './cizelgeApi'
import { gunBasligi, gunMetni, hucreGorunumu, IZIN_KODLARI, tarihKodu } from './cizelgeHucre'

/**
 * Aylık çizelge ızgarası (F5, MK-179): satır = personel, sütun = ayın günü; SALT OKUNUR (yalnız GET). Gerçek tablo (table): caption, `th scope`, sabit satır yüksekliği, yapışkan ilk sütun ve başlık,
 * kendi kaydırma kabı. Hücre anlamı yalnız renkle verilmez (kod, simge + metin, desen). Klavye: tek sekme durağı (yaylanan odak), oklar hücreler arasında gezer, Enter hücredeki atama masası
 * bağlantısını açar. Sanal kaydırma yok: 50 satırlık sayfalar + arama/gar/tür süzgeci (ölçüm 04-gelistirme.md "Tur 4").
 */
const ay = ref(bugun().slice(0, 7))
const tur = ref<PersonelTuru>('MAKINIST')
const garId = ref<number | null>(null)
const kontrol = ref(false)
const arama = ref('')
const yanit = ref<CizelgeYaniti | null>(null)
const yukleniyor = ref(true)
const hata = ref('')
const garlar = ref<TanimDto[]>([])
/** Görünen çizelgenin ayı/süzgeci: form sonradan değişse de yazdır bağlantısı bununla çalışır. */
const uygulanan = ref({ ay: ay.value, tur: tur.value, garId: garId.value })

let sayac = 0
async function getir() {
  const sira = ++sayac
  const secim = { ay: ay.value, tur: tur.value, garId: garId.value }
  if (!secim.ay) return
  yukleniyor.value = true
  hata.value = ''
  try {
    const sonuc = await cizelgeApi.getir(secim.ay, {
      tur: secim.tur,
      garId: secim.garId,
      kontrol: kontrol.value,
    })
    if (sira !== sayac) return
    yanit.value = sonuc
    uygulanan.value = secim
    odak.value = { r: 0, c: 0 }
  } catch (e) {
    if (sira !== sayac) return
    yanit.value = null
    hata.value = e instanceof ApiHata ? e.detail : 'Çizelge alınamadı.'
  } finally {
    if (sira === sayac) yukleniyor.value = false
  }
}

onMounted(async () => {
  void getir()
  try {
    garlar.value = await tanimApi.liste('GAR')
  } catch {
    // gar listesi gelmezse süzgeç "Tüm garlar" ile kalır
  }
})
watch([ay, tur, garId, kontrol], () => void getir())

const turler = Object.entries(TUR_ETIKETLERI) as [PersonelTuru, string][]
const garSec = (v: string) => (garId.value = v ? Number(v) : null)

const satirlar = computed<CizelgeSatiri[]>(() => {
  const q = arama.value.trim().toLocaleLowerCase('tr-TR')
  const hepsi = yanit.value?.satirlar ?? []
  return q
    ? hepsi.filter((s) => `${s.sicil} ${s.adSoyad}`.toLocaleLowerCase('tr-TR').includes(q))
    : hepsi
})
/** Sayfalama (50 satır): 300 × 31 hücrenin tek seferde çizimi ~1,1 sn sürdü (04-gelistirme.md "Tur 4"); sanal kaydırma yerine basit sayfa + arama/gar/tür süzgeci. */
const SAYFA_BOYUTU = 50
const sayfa = ref(0)
const sayfaSayisi = computed(() => Math.max(1, Math.ceil(satirlar.value.length / SAYFA_BOYUTU)))
const gorunen = computed(() =>
  satirlar.value.slice(sayfa.value * SAYFA_BOYUTU, (sayfa.value + 1) * SAYFA_BOYUTU),
)
const sayfaAraligi = computed(() => {
  const ilk = sayfa.value * SAYFA_BOYUTU + 1
  return `${ilk}–${ilk + gorunen.value.length - 1} / ${satirlar.value.length}`
})
// Süzgeç ya da yeni veri gelince ilk sayfaya dön (sayfa numarası liste kısalınca boşa düşmesin)
watch([arama, yanit], () => {
  sayfa.value = 0
  odak.value = { r: 0, c: 0 }
})
function sayfaGit(yeni: number) {
  sayfa.value = Math.min(Math.max(yeni, 0), sayfaSayisi.value - 1)
  odak.value = { r: 0, c: 0 }
}

const gunler = computed(() =>
  Array.from({ length: yanit.value?.gunSayisi ?? 0 }, (_, i) => {
    const gun = i + 1
    const tatil = yanit.value?.tatiller.find((t) => t.gun === gun)
    return { ...gunBasligi(yanit.value!.ay, gun), tatil: tatil?.ad ?? null }
  }),
)

/** Satırın gün sırasındaki hücre görünümleri (yoksa null); satır başına bir kez hesaplanır. */
function hucreler(s: CizelgeSatiri) {
  const a = yanit.value!.ay
  const harita = new Map(s.hucreler.map((h) => [h.gun, h]))
  return gunler.value.map((g) =>
    hucreGorunumu(harita.get(g.no), `${s.adSoyad}`, gunMetni(a, g.no), g.tatil ?? undefined),
  )
}
const hedef = (a: string, g: number, seferId: number) =>
  masaBaglantisi(tarihKodu(a, g), undefined, seferId)

// ---- klavye: yaylanan odak (tek sekme durağı), oklar hücreler arasında
const odak = ref({ r: 0, c: 0 })
const kap = ref<HTMLElement | null>(null)

function hucreOdakla(r: number, c: number) {
  const el = kap.value?.querySelector<HTMLElement>(`td.hucre[data-r="${r}"][data-c="${c}"]`)
  el?.focus()
}
function hucreOdaginda(e: Event) {
  const td = (e.target as HTMLElement).closest<HTMLElement>('td.hucre')
  if (td) odak.value = { r: Number(td.dataset.r), c: Number(td.dataset.c) }
}
function tus(e: KeyboardEvent) {
  const td = (e.target as HTMLElement).closest<HTMLElement>('td.hucre')
  if (!td) return
  const r = Number(td.dataset.r)
  const c = Number(td.dataset.c)
  const sonSatir = gorunen.value.length - 1
  const sonSutun = gunler.value.length - 1
  let hedefHucre: [number, number] | null = null
  switch (e.key) {
    case 'ArrowRight':
      hedefHucre = [r, Math.min(c + 1, sonSutun)]
      break
    case 'ArrowLeft':
      hedefHucre = [r, Math.max(c - 1, 0)]
      break
    case 'ArrowDown':
      hedefHucre = [Math.min(r + 1, sonSatir), c]
      break
    case 'ArrowUp':
      hedefHucre = [Math.max(r - 1, 0), c]
      break
    case 'Home':
      hedefHucre = [e.ctrlKey ? 0 : r, 0]
      break
    case 'End':
      hedefHucre = [e.ctrlKey ? sonSatir : r, sonSutun]
      break
    case 'Enter':
    case ' ':
      td.querySelector<HTMLElement>('a')?.click()
      e.preventDefault()
      return
    default:
      return
  }
  e.preventDefault()
  hucreOdakla(hedefHucre[0], hedefHucre[1])
}

const altYazi = computed(() =>
  yanit.value ? `Aylık çizelge ${yanit.value.ay} — ${TUR_ETIKETLERI[yanit.value.tur]}` : '',
)

const yazdirBaglantisi = (boyut: 'A4' | 'A3') => ({
  name: 'yazdir-cizelge',
  query: {
    ay: uygulanan.value.ay,
    boyut,
    ...(uygulanan.value.tur === 'KONDUKTOR' ? { tur: uygulanan.value.tur } : {}),
    ...(uygulanan.value.garId ? { garId: String(uygulanan.value.garId) } : {}),
  },
})
</script>

<template>
  <section class="rapor-sayfa cizelge-sayfa">
    <SayfaBasligi baslik="Aylık çizelge">
      <template v-if="yanit" #eylemler>
        <RouterLink
          v-for="boyut in ['A4', 'A3'] as const"
          :key="boyut"
          class="btn"
          :data-test="`yazdir-${boyut}`"
          :to="yazdirBaglantisi(boyut)"
          target="_blank"
          rel="noopener"
        >
          <Simge ad="yazdir" boyut="1.1em" />Yazdır ({{ boyut }})
        </RouterLink>
      </template>
    </SayfaBasligi>

    <p class="soluk cizelge-not">
      Salt okunur görünüm: hücreye tıklayınca atama masası o günün seferiyle açılır. Satır
      toplamları Adalet raporuyla aynıdır.
    </p>

    <form class="arac-cubugu no-print" @submit.prevent>
      <div class="alan">
        <label for="cizelge-ay">Ay</label>
        <input id="cizelge-ay" v-model="ay" type="month" required />
      </div>
      <div class="alan">
        <span id="cizelge-tur-etiketi">Personel türü</span>
        <div class="suzgec" role="group" aria-label="Personel türü">
          <button
            v-for="[k, etiket] in turler"
            :key="k"
            type="button"
            class="btn"
            :aria-pressed="tur === k"
            @click="tur = k"
          >
            {{ etiket }}
          </button>
        </div>
      </div>
      <div class="alan">
        <label for="cizelge-gar">Gar</label>
        <select
          id="cizelge-gar"
          :value="garId ?? ''"
          @change="garSec(($event.target as HTMLSelectElement).value)"
        >
          <option value="">Tüm garlar</option>
          <option v-for="g in garlar" :key="g.id" :value="g.id">{{ g.ad }}</option>
        </select>
      </div>
      <div class="alan">
        <label for="cizelge-ara">Sicil veya ad ara</label>
        <input id="cizelge-ara" v-model="arama" type="search" autocomplete="off" />
      </div>
      <div class="alan">
        <span id="cizelge-kontrol-etiketi">Kontrol bekleyen atamalar</span>
        <button
          type="button"
          class="btn"
          data-test="kontrol"
          :aria-pressed="kontrol"
          aria-describedby="cizelge-kontrol-not"
          @click="kontrol = !kontrol"
        >
          İşaretle
        </button>
        <small id="cizelge-kontrol-not" class="soluk">Hesap yavaştır; kapalıyken işaret yok.</small>
      </div>
    </form>

    <VeriDurumu v-if="yukleniyor && !yanit" durum="yukleniyor" metin="Çizelge hazırlanıyor…" />
    <VeriDurumu v-else-if="hata" durum="hata" :metin="hata" />
    <VeriDurumu
      v-else-if="yanit && yanit.satirlar.length === 0"
      durum="bos"
      metin="Bu süzgeçte personel yok."
    />

    <template v-if="yanit && yanit.satirlar.length > 0">
      <ul class="cizelge-gosterge" aria-label="Hücre göstergesi">
        <li>
          <b class="kod">T1</b> görev (tren no, altında kalkış saati); <b class="kod">+1</b> aynı
          günde bir görev daha
        </li>
        <li>
          <b class="kod">İ</b> yıllık izin · <b class="kod">R</b> rapor · <b class="kod">D</b> diğer
          izin (desenli hücre)
        </li>
        <li>
          <b class="kod">Y T4</b> yolculuk (kesik çizgili hücre) ·
          <Simge ad="ay" boyut="1em" /> gece görevi · <Simge ad="engel" boyut="1em" /> kontrol
          bekliyor
        </li>
        <li>Desenli sütun başlığı: hafta sonu; "Tatil": resmi tatil (başlıkta adı)</li>
      </ul>
      <p v-if="gunler.length > 0" class="soluk no-print" data-test="kaydirma-notu">
        Çizelge geniştir: tablonun içinde yatay kaydırın; ilk sütun (personel) yerinde kalır.
      </p>
      <nav class="cizelge-sayfalar no-print" aria-label="Çizelge sayfaları">
        <p class="soluk" data-test="sayfa-durumu" aria-live="polite">
          {{ sayfaAraligi }} personel<template v-if="sayfaSayisi > 1">
            · Sayfa {{ sayfa + 1 }} / {{ sayfaSayisi }}</template
          >
        </p>
        <template v-if="sayfaSayisi > 1">
          <button
            type="button"
            class="btn btn-kucuk"
            data-test="onceki-sayfa"
            :disabled="sayfa === 0"
            @click="sayfaGit(sayfa - 1)"
          >
            <Simge ad="ok-sol" boyut="1em" />Önceki sayfa
          </button>
          <button
            type="button"
            class="btn btn-kucuk"
            data-test="sonraki-sayfa"
            :disabled="sayfa >= sayfaSayisi - 1"
            @click="sayfaGit(sayfa + 1)"
          >
            Sonraki sayfa<Simge ad="ok-sag" boyut="1em" />
          </button>
        </template>
      </nav>
      <div ref="kap" class="cizelge-kap">
        <table class="cizelge-tablo" @keydown="tus" @focusin="hucreOdaginda">
          <caption class="gorunmez">
            {{
              altYazi
            }}
          </caption>
          <thead>
            <tr>
              <th scope="col" class="sutun-ad">Personel</th>
              <th
                v-for="g in gunler"
                :key="g.no"
                scope="col"
                class="gun"
                :class="{ 'hafta-sonu': g.haftaSonu, tatil: g.tatil }"
                :title="g.tatil ? `Resmi tatil: ${g.tatil}` : undefined"
              >
                <span class="gun-no">{{ g.no }}</span>
                <span class="gun-adi">{{ g.ad }}</span>
                <span v-if="g.tatil" class="gun-tatil">Tatil</span>
                <span v-if="g.haftaSonu" class="gorunmez"> hafta sonu</span>
                <span v-if="g.tatil" class="gorunmez"> resmi tatil: {{ g.tatil }}</span>
              </th>
              <th scope="col" class="toplam-baslik">Saat</th>
              <th scope="col" class="toplam-baslik">Gece</th>
              <th scope="col" class="toplam-baslik">Km</th>
              <th scope="col" class="toplam-baslik">Sefer</th>
              <th scope="col" class="toplam-baslik">İzin</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(s, r) in gorunen" :key="s.makinistId">
              <th scope="row" class="sutun-ad">
                <span class="personel-ad">{{ s.adSoyad }}</span>
                <small class="soluk">{{ s.sicil }} · {{ s.bagliGar }}</small>
              </th>
              <td
                v-for="(v, c) in hucreler(s)"
                :key="c"
                class="hucre"
                :class="{
                  'hafta-sonu': gunler[c]!.haftaSonu,
                  tatil: gunler[c]!.tatil,
                  izin: v?.tur === 'izin',
                  yolculuk: v?.tur === 'yolculuk',
                  kirmizi: v?.kirmizi,
                }"
                :data-r="r"
                :data-c="c"
                :tabindex="odak.r === r && odak.c === c ? 0 : -1"
              >
                <template v-if="v">
                  <RouterLink
                    v-if="v.seferId !== null"
                    class="hucre-ic"
                    tabindex="-1"
                    :to="hedef(yanit.ay, c + 1, v.seferId)"
                    :aria-label="v.ad"
                  >
                    <span class="hucre-kod"
                      >{{ v.kod }}<b v-if="v.ek" class="hucre-ek">+{{ v.ek }}</b></span
                    >
                    <span class="hucre-alt"
                      >{{ v.saat }}<Simge v-if="v.gece" ad="ay" boyut="0.9em" /><Simge
                        v-if="v.kirmizi"
                        ad="engel"
                        boyut="0.9em"
                    /></span>
                  </RouterLink>
                  <span v-else class="hucre-ic">
                    <span class="gorunmez">{{ v.ad }}</span>
                    <span class="hucre-kod" aria-hidden="true">{{ v.kod }}</span>
                    <span v-if="v.gece || v.kirmizi" class="hucre-alt" aria-hidden="true"
                      ><Simge v-if="v.gece" ad="ay" boyut="0.9em" /><Simge
                        v-if="v.kirmizi"
                        ad="engel"
                        boyut="0.9em"
                    /></span>
                  </span>
                  <span v-if="v.izinKodu" class="hucre-izin" aria-hidden="true">{{
                    v.izinKodu
                  }}</span>
                </template>
              </td>
              <td class="toplam sayisal">{{ sayiBicimle(s.gorevSaat) }}</td>
              <td class="toplam sayisal">{{ sayiBicimle(s.geceSaat) }}</td>
              <td class="toplam sayisal">{{ sayiBicimle(s.km) }}</td>
              <td class="toplam sayisal">{{ s.seferSayisi }}</td>
              <td class="toplam sayisal">{{ s.izinGunu }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <p class="soluk">
        İzin kodları:
        {{
          Object.values(IZIN_KODLARI)
            .map((i) => `${i.kod} ${i.ad}`)
            .join(' · ')
        }}.
      </p>
    </template>
  </section>
</template>

<style scoped>
.cizelge-not,
.cizelge-gosterge {
  margin: 0;
}
.cizelge-gosterge {
  display: flex;
  flex-wrap: wrap;
  gap: var(--aralik-1) var(--aralik-5);
  padding: 0;
  list-style: none;
  font-size: var(--yazi-xs);
  color: var(--renk-metin-ikincil);
}
.cizelge-sayfalar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--aralik-3);
}
.cizelge-sayfalar p {
  margin: 0;
}
.cizelge-gosterge .kod {
  color: var(--renk-metin);
}
/* Kendi kaydırma kabı: sayfa yatay kaymaz; başlık satırı ve ilk sütun yerinde kalır (MK-135 deseni). */
.cizelge-kap {
  overflow: auto;
  max-height: calc(100vh - 16rem);
  min-height: 12rem;
  background: var(--renk-yuzey);
  border: 1px solid var(--renk-cizgi);
  border-radius: var(--yaricap);
  scroll-padding-left: 11rem;
  scroll-padding-top: 3.5rem;
}
.cizelge-tablo {
  border-collapse: separate;
  border-spacing: 0;
  font-size: var(--yazi-xs);
  table-layout: fixed;
  width: max-content;
}
.cizelge-tablo th,
.cizelge-tablo td {
  padding: 0;
  border-right: 1px solid var(--renk-cizgi);
  border-bottom: 1px solid var(--renk-cizgi);
  background: var(--renk-yuzey);
  text-align: center;
  vertical-align: middle;
}
.cizelge-tablo thead th {
  position: sticky;
  top: 0;
  z-index: 2;
  height: 3.5rem;
  background: var(--renk-n-100);
  color: var(--renk-metin-ikincil);
  font-weight: var(--yazi-agirlik-orta);
}
.cizelge-tablo .sutun-ad {
  position: sticky;
  left: 0;
  z-index: 1;
  width: 11rem;
  min-width: 11rem;
  max-width: 11rem;
  padding: 0 var(--aralik-3);
  text-align: left;
  background: var(--renk-n-100);
  color: var(--renk-metin);
  overflow: hidden;
}
.cizelge-tablo thead .sutun-ad {
  z-index: 3;
}
.sutun-ad .personel-ad {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: var(--yazi-agirlik-orta);
}
.sutun-ad small {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.cizelge-tablo th.gun,
.cizelge-tablo td.hucre {
  width: 3.75rem;
  min-width: 3.75rem;
}
.cizelge-tablo td.hucre {
  height: 2.75rem; /* sabit satır yüksekliği: 300 satırda ölçülebilir yerleşim */
  overflow: hidden;
  cursor: default;
}
.cizelge-tablo th.gun .gun-no {
  display: block;
  font-weight: var(--yazi-agirlik-kalin);
  color: var(--renk-metin);
}
.cizelge-tablo th.gun .gun-adi,
.cizelge-tablo th.gun .gun-tatil {
  display: block;
}
.cizelge-tablo th.gun .gun-tatil {
  font-weight: var(--yazi-agirlik-kalin);
  color: var(--renk-metin);
}
/* Hafta sonu / tatil: desen + metin (renk tek başına değil) */
.cizelge-tablo th.hafta-sonu,
.cizelge-tablo td.hafta-sonu {
  background-image: repeating-linear-gradient(135deg, transparent 0 5px, var(--renk-n-200) 5px 6px);
}
.cizelge-tablo th.tatil,
.cizelge-tablo td.tatil {
  background-image: repeating-linear-gradient(45deg, transparent 0 4px, var(--renk-n-300) 4px 5px);
}
.cizelge-tablo td.izin {
  background-image: repeating-linear-gradient(
    135deg,
    var(--renk-bilgi-zemin) 0 4px,
    var(--renk-yuzey) 4px 8px
  );
  color: var(--renk-bilgi-metin);
  font-weight: var(--yazi-agirlik-kalin);
}
.cizelge-tablo td.yolculuk {
  outline: 1px dashed var(--renk-metin-ikincil);
  outline-offset: -3px;
}
.cizelge-tablo td.kirmizi {
  background: var(--renk-engel-zemin);
  color: var(--renk-engel-metin);
}
.hucre-ic {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  min-height: 2.75rem;
  color: inherit;
  text-decoration: none;
  line-height: 1.15;
}
a.hucre-ic:hover {
  background: var(--renk-n-100);
}
.hucre-kod {
  font-weight: var(--yazi-agirlik-kalin);
  white-space: nowrap;
}
.hucre-ek {
  margin-left: 2px;
}
.hucre-alt {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  color: var(--renk-metin-ikincil);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}
.hucre-izin {
  position: absolute;
  top: 0;
  right: 2px;
}
.cizelge-tablo td.hucre {
  position: relative;
}
.cizelge-tablo td.hucre:focus-visible {
  outline: 2px solid var(--renk-odak);
  outline-offset: -2px;
  z-index: 1;
}
.cizelge-tablo th.toplam-baslik,
.cizelge-tablo td.toplam {
  width: 4rem;
  min-width: 4rem;
  padding: 0 var(--aralik-2);
  text-align: right;
  font-variant-numeric: tabular-nums;
}
</style>
