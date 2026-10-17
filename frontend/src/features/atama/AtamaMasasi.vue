<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { bugun, gunKaydir, saatBicimle, tarihBicimle } from '@/lib/tarih'
import { trenAdi } from '@/lib/trenAdi'
import { durumMetni, gecikmeMetni } from '@/features/yazdir/durumMetni'
import IntikalSeridi from '@/features/intikal/IntikalSeridi.vue'
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
    el.scrollIntoView({ block: 'start', behavior: 'smooth' })
}

/** Tek sütunda aday paneli listenin altında kalır; sefer seçilince panele kaydır. */
async function seferSec(id: number) {
  masa.sec(id)
  if (!window.matchMedia?.(TEK_SUTUN).matches) return
  await nextTick()
  gorunurKil(panel.value)
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
  <section>
    <div class="sayfa-baslik">
      <h1>Atama masası</h1>
      <div class="baslik-dugmeleri">
        <button type="button" class="btn" @click="digerAc({ tur: 'otomatik' })">
          Otomatik ata
        </button>
        <RouterLink
          class="btn"
          :to="{ name: 'yazdir-gun', params: { tarih } }"
          target="_blank"
          rel="noopener"
        >
          Yazdır
        </RouterLink>
      </div>
    </div>

    <div class="arac-cubugu">
      <button type="button" class="btn" @click="gunDegistir(gunKaydir(tarih, -1))">
        ‹ Önceki gün
      </button>
      <div class="alan">
        <label for="atama-gun">Gün</label>
        <input
          id="atama-gun"
          type="date"
          :value="tarih"
          @change="gunDegistir(($event.target as HTMLInputElement).value)"
        />
      </div>
      <button type="button" class="btn" @click="gunDegistir(gunKaydir(tarih, 1))">
        Sonraki gün ›
      </button>
      <div class="sayaclar">
        <p
          v-if="masa.kirmizi.value"
          class="kirmizi-sayac"
          :class="{ dolu: masa.kirmizi.value.kirmiziSefer > 0 }"
          data-test="kirmizi-sayac"
          :title="`Seçili gün + sonraki 7 gün: ${masa.kirmizi.value.kirmiziSefer} sefer, ${masa.kirmizi.value.kirmiziAtama} atama kontrol bekliyor`"
        >
          <span aria-hidden="true">⛔</span> Kırmızı:
          <strong>{{ masa.kirmizi.value.kirmiziSefer }}</strong> sefer
        </p>
        <p
          v-if="(masa.kirmizi.value?.kirmiziIntikal ?? 0) > 0"
          class="kirmizi-sayac dolu"
          data-test="kirmizi-intikal-sayac"
          title="Seçili gün + sonraki 7 gün: kural motoruna göre kontrol bekleyen serbest intikaller (atama sayacına dahil değildir)"
        >
          <span aria-hidden="true">⛔</span> Kırmızı intikal:
          <strong>{{ masa.kirmizi.value?.kirmiziIntikal }}</strong>
        </p>
        <p
          v-if="masa.gordum.value"
          class="gordum-sayac"
          :class="{ dolu: masa.gordum.value.gorulmeyen > 0 }"
          data-test="gordum-sayac"
          :title="`Seçili gün + sonraki 7 gün, kalkışı geçmemiş atamalar ve intikaller: ${masa.gordum.value.gorulen} gördü, ${masa.gordum.value.gorulmeyen} görmedi, ${masa.gordum.value.hesapsiz} personelin hesabı yok`"
        >
          <span aria-hidden="true">👁</span> Görülmeyen:
          <strong>{{ masa.gordum.value.gorulmeyen }}</strong>
          · telefon hesabı yok: <strong>{{ masa.gordum.value.hesapsiz }}</strong>
          <span v-if="masa.gordum.value.uyari > 0" class="gordum-uyari">
            · <span aria-hidden="true">⚠</span> {{ masa.gordum.value.uyari }} kalkışa yakın
          </span>
        </p>
        <p
          v-if="masa.takasBekleyen.value !== null"
          class="takas-sayac"
          :class="{ dolu: masa.takasBekleyen.value > 0 }"
          data-test="takas-sayac"
        >
          <RouterLink to="/takas">
            <span aria-hidden="true">🔁</span> Bekleyen talep:
            <strong>{{ masa.takasBekleyen.value }}</strong>
          </RouterLink>
        </p>
        <p v-if="masa.gun.value" class="atanmamis" data-test="atanmamis-sayac">
          Atanmamış: <strong>{{ masa.atanmamisSayisi.value }}</strong> sefer
        </p>
        <p v-if="kondukturEksikSayisi > 0" class="konduktor-sayac" data-test="konduktor-sayac">
          <span aria-hidden="true">ℹ</span> Kondüktör eksik:
          <strong>{{ kondukturEksikSayisi }}</strong> sefer
        </p>
      </div>
    </div>

    <div role="status">
      <p v-if="bilgi" class="bildirim bildirim-bilgi">{{ bilgi }}</p>
    </div>
    <p v-if="masa.gunHatasi.value" class="bildirim bildirim-hata" role="alert">
      {{ masa.gunHatasi.value }}
    </p>

    <IntikalSeridi :tarih="tarih" @degisti="masa.yenile()" />

    <div class="atama-izgara" :class="{ secimsiz: !seciliSatir }">
      <div ref="liste" class="tablo-kap">
        <table class="tablo" :aria-label="`${tarihBicimle(tarih)} seferleri`">
          <thead>
            <tr>
              <th>Saat</th>
              <th>Tren no</th>
              <th>Güzergâh</th>
              <th class="kol-ayrinti">Araç tipi</th>
              <th class="kol-ayrinti">Hat</th>
              <th class="makinist-sutun">Personel</th>
              <th>Durum</th>
              <th>İşlem</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="s in satirlar"
              :key="s.seferId"
              class="sefer-satiri"
              :class="{
                iptal: s.durum === 'IPTAL',
                secili: s.seferId === seciliId,
                kirmizi: s.kirmizi,
              }"
              tabindex="0"
              :aria-selected="s.seferId === seciliId"
              @click="seferSec(s.seferId)"
              @keydown.enter.self.prevent="seferSec(s.seferId)"
            >
              <td>{{ saatBicimle(s.guncelKalkis) }}–{{ saatBicimle(s.guncelVaris) }}</td>
              <td>{{ trenAdi(s) }}</td>
              <td>{{ guzergah(s) }}</td>
              <td class="kol-ayrinti">{{ s.aracTipi }}</td>
              <td class="kol-ayrinti">{{ s.hat }}</td>
              <td class="makinist-sutun">
                <div
                  v-for="m in sirali(s)"
                  :key="m.atamaId"
                  class="gorevli"
                  :class="{ 'gordum-uyarili': m.gorduUyari }"
                >
                  <span>{{ ROL_ONEKLERI[m.rol] }}{{ kisi(m) }}</span>
                  <span v-if="m.gordu === 'GORDU'" class="gordum goruldu" data-test="gordu">
                    <span aria-hidden="true">✓</span> gördü {{ saatBicimle(m.gorduZamani) }}
                  </span>
                  <span
                    v-else-if="m.gordu === 'GORMEDI'"
                    class="gordum gormedi"
                    data-test="gormedi"
                  >
                    <span aria-hidden="true">{{ m.gorduUyari ? '⚠' : '○' }}</span> görmedi
                  </span>
                  <span
                    v-else-if="m.gordu === 'HESAP_YOK'"
                    class="gordum hesapsiz"
                    data-test="hesap-yok"
                  >
                    <span aria-hidden="true">–</span> hesap yok
                  </span>
                  <button
                    type="button"
                    class="btn btn-kucuk"
                    :aria-label="`${kisi(m)} atamasını kaldır`"
                    @click.stop="kaldirAc(s, m)"
                  >
                    Kaldır
                  </button>
                  <button
                    v-if="
                      s.durum === 'IPTAL' && m.kirmizi && m.rol !== 'YOLCU' && m.rol !== 'KONDUKTOR'
                    "
                    type="button"
                    class="btn btn-kucuk btn-birincil"
                    :aria-label="`${kisi(m)} için uygun seferler`"
                    @click.stop="digerAc({ tur: 'uygun', makinist: m })"
                  >
                    Uygun seferler
                  </button>
                  <div v-if="m.kirmizi" class="kirmizi-neden">
                    <span aria-hidden="true">⛔</span> {{ m.kirmiziNedenler.join('; ') }}
                  </div>
                  <div v-if="m.uyarilar.length > 0" class="uyari-notu">
                    <span aria-hidden="true">⚠</span> Uyarı: {{ m.uyarilar.join('; ') }}
                  </div>
                  <div v-if="s.durum === 'IPTAL'" class="soluk iptal-notu">
                    Eski atama iptal seferde duruyor; "Kaldır" ile kaldırın.
                  </div>
                </div>
                <span v-if="s.durum !== 'IPTAL' && !asil(s)" class="rozet rozet-bos">
                  <span aria-hidden="true">◌</span> BOŞ
                </span>
                <div v-if="s.durum !== 'IPTAL' && !asil(s) && s.bosNeden" class="soluk bos-neden">
                  {{ s.bosNeden }}
                </div>
                <span
                  v-if="s.durum !== 'IPTAL' && s.kondukturEksik"
                  class="rozet rozet-konduktor"
                  data-test="konduktor-eksik"
                >
                  <span aria-hidden="true">ℹ</span> Kondüktör eksik
                </span>
              </td>
              <td>
                <span v-if="s.durum === 'IPTAL'" class="rozet rozet-iptal">
                  <span aria-hidden="true">⛔</span> {{ durumMetni(s) }}
                </span>
                <template v-else>
                  <span v-if="s.kirmizi" class="rozet rozet-kontrol">
                    <span aria-hidden="true">⛔</span> KONTROL
                  </span>
                  <span v-if="s.kalkisGecikmeDk > 0">
                    <span aria-hidden="true">⏱</span> {{ gecikmeMetni(s) }}
                  </span>
                </template>
              </td>
              <td>
                <div class="islem-dugmeleri">
                  <template v-if="s.durum !== 'IPTAL'">
                    <button
                      type="button"
                      class="btn btn-kucuk"
                      :aria-label="`${trenAdi(s)} seferine gecikme gir`"
                      @click.stop="digerAc({ tur: 'degisiklik', kip: 'gecikme', satir: s })"
                    >
                      Gecikme
                    </button>
                    <button
                      type="button"
                      class="btn btn-kucuk"
                      :aria-label="`${trenAdi(s)} seferini iptal et`"
                      @click.stop="digerAc({ tur: 'degisiklik', kip: 'iptal', satir: s })"
                    >
                      İptal
                    </button>
                    <button
                      v-if="bolunebilir(s)"
                      type="button"
                      class="btn btn-kucuk"
                      :aria-label="`${trenAdi(s)} seferini böl`"
                      @click.stop="digerAc({ tur: 'bol', satir: s })"
                    >
                      Böl
                    </button>
                    <button
                      v-if="birlestirilebilir(s)"
                      type="button"
                      class="btn btn-kucuk"
                      :aria-label="`${trenAdi(s)} seferini birleştir`"
                      @click.stop="digerAc({ tur: 'birlestir', satir: s })"
                    >
                      Birleştir
                    </button>
                    <button
                      v-if="asil(s)"
                      type="button"
                      class="btn btn-kucuk"
                      :class="{ 'btn-birincil': s.kirmizi }"
                      :aria-label="`${trenAdi(s)} seferinde yerine ata`"
                      @click.stop="digerAc({ tur: 'yerine', satir: s })"
                    >
                      Yerine ata
                    </button>
                  </template>
                </div>
              </td>
            </tr>
            <tr v-if="masa.gun.value === null && !masa.gunHatasi.value">
              <td colspan="8" class="bos-durum">Yükleniyor…</td>
            </tr>
            <tr v-else-if="satirlar.length === 0 && !masa.gunHatasi.value">
              <td colspan="8" class="bos-durum">Bu gün için sefer yok.</td>
            </tr>
          </tbody>
        </table>
        <NobetNotu class="masa-nobet" :metinler="nobetMetinleri" />
      </div>

      <aside ref="panel" class="kart" aria-label="Aday personel">
        <template v-if="seciliSatir">
          <button type="button" class="btn listeye-don" @click="gorunurKil(liste)">
            ↑ Sefer listesine dön
          </button>
          <h2>Adaylar: {{ seferEtiketi(seciliSatir) }}</h2>
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
.atama-izgara {
  display: grid;
  grid-template-columns: minmax(0, 11fr) minmax(0, 9fr);
  gap: 1rem;
  align-items: start;
}
.listeye-don {
  display: none;
  margin-bottom: var(--bosluk);
}
/* Sefer seçilmediyse yardım paneli dar kalsın; liste genişlesin. */
.atama-izgara.secimsiz {
  grid-template-columns: minmax(0, 1fr) minmax(0, 16rem);
}
.sefer-satiri td,
.atama-izgara > .tablo-kap th {
  padding-left: 0.5rem;
  padding-right: 0.5rem;
}
/* BUG-16: sefer seçilince tablo ~670 px'e iner; ikincil sütunlar (araç tipi, hat) gizlenir, "İşlem" düğmeleri kesilmez. Geniş ekranda ve sefer seçilmeyince görünürler.
   F2c UX-5: gizli sütunlarla da tablo en az ~545 px ister (11fr/20fr pay); 1001-1100 px'te 514 px kalıp "İşlem" kesiliyordu, tek sütun eşiği 1100 px. */
@media (min-width: 1101px) {
  .atama-izgara:not(.secimsiz) .kol-ayrinti {
    display: none;
  }
}
/* 1366 px'te "Ad Soyad (Sicil)" 4 satıra sarılmasın. */
.makinist-sutun {
  min-width: 12rem;
}
.masa-nobet {
  padding: 0 0.5rem 0.5rem;
}
@media (max-width: 1100px) {
  .atama-izgara,
  .atama-izgara.secimsiz {
    grid-template-columns: minmax(0, 1fr);
  }
  .listeye-don {
    display: inline-block;
  }
}
.baslik-dugmeleri {
  display: flex;
  gap: 0.5rem;
}
.sayaclar {
  display: flex;
  flex-wrap: wrap;
  gap: 0 1.25rem;
  margin-left: auto;
}
.sayaclar p {
  margin: 0 0 var(--bosluk);
}
.kirmizi-sayac.dolu {
  color: var(--renk-tehlike);
}
.konduktor-sayac {
  color: #8a5a00;
}
.takas-sayac.dolu {
  font-weight: 700;
}
.gordum-sayac.dolu {
  color: #8a5a00;
}
.gordum {
  display: inline-block;
  margin-left: 0.4rem;
  font-size: 0.85rem;
  white-space: nowrap;
}
.gordum.gormedi,
.gordum.hesapsiz {
  color: var(--renk-soluk);
}
.gordum-uyarili {
  background: var(--renk-uyari-zemin);
  border-left: 3px solid var(--renk-uyari-cizgi);
  padding-left: 0.4rem;
}
.gordum-uyarili .gordum.gormedi {
  color: #8a5a00;
  font-weight: 700;
}
.sefer-satiri {
  cursor: pointer;
}
.sefer-satiri:hover td {
  background: var(--renk-bilgi-zemin);
}
.sefer-satiri.kirmizi td {
  background: var(--renk-hata-zemin);
}
.sefer-satiri.kirmizi td:first-child {
  box-shadow: inset 4px 0 0 var(--renk-tehlike);
}
.sefer-satiri[aria-selected='true'] td {
  background: var(--renk-bilgi-zemin);
  border-bottom-color: var(--renk-birincil);
}
.sefer-satiri[aria-selected='true'] td:first-child {
  box-shadow: inset 4px 0 0 var(--renk-birincil);
}
.sefer-satiri.iptal td {
  text-decoration: line-through;
  color: var(--renk-soluk);
}
.sefer-satiri.iptal td:last-child,
.sefer-satiri.iptal td:nth-last-child(2) {
  text-decoration: none;
}
.btn-kucuk {
  padding: 0.1rem 0.5rem;
  margin-left: 0.5rem;
  font-size: 0.85rem;
}
.islem-dugmeleri {
  display: flex;
  flex-wrap: wrap;
  gap: 0.25rem;
}
.islem-dugmeleri .btn-kucuk {
  margin-left: 0;
}
.gorevli + .gorevli {
  margin-top: 0.4rem;
}
.kirmizi-neden {
  display: inline-block; /* iptal satırındaki üstü çizgiyi devralmasın */
  color: var(--renk-tehlike);
  font-weight: 600;
  font-size: 0.9rem;
}
.iptal-notu {
  display: inline-block;
  font-size: 0.9rem;
}
.uyari-notu {
  display: inline-block;
  color: #8a5a00;
  font-size: 0.9rem;
}
.rozet {
  display: inline-block;
  padding: 0.05rem 0.5rem;
  border: 1px solid currentcolor;
  border-radius: 999px;
  font-weight: 700;
  font-size: 0.85rem;
}
.bos-neden {
  margin-top: 0.25rem;
  font-size: 0.85rem;
}
.rozet-bos,
.rozet-konduktor {
  color: #8a5a00;
  background: var(--renk-uyari-zemin);
}
.rozet-iptal,
.rozet-kontrol {
  color: var(--renk-tehlike);
  background: var(--renk-hata-zemin);
}
</style>
