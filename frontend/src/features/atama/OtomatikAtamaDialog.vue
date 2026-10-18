<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ApiHata } from '@/api/istemci'
import type { Tarih } from '@/api/tipler'
import { gunKaydir, saatBicimle, tarihBicimle } from '@/lib/tarih'
import Diyalog from '@/bilesenler/Diyalog.vue'
import Dugme from '@/bilesenler/Dugme.vue'
import Rozet from '@/bilesenler/Rozet.vue'
import NobetNotu from './NobetNotu.vue'
import { atamaApi, type OtomatikAtamaSonucu } from './atamaApi'

/**
 * Otomatik atama: önce önizleme (`uygula=false`, yazmaz), sonra aynı aralık için `uygula=true`.
 * Tarih değişince önizleme geçersiz olur; Uygula yalnız taze önizlemeden sonra açıktır.
 */
const props = defineProps<{ varsayilanBas: Tarih }>()
const emit = defineEmits<{
  /** Atamalar yazıldı (ya da yazılmış olabilir): masa yenilenmeli. Diyalog sonucu göstermeye devam eder. */
  uygulandi: []
  kapat: []
}>()

const bas = ref<Tarih>(props.varsayilanBas)
const bit = ref<Tarih>(gunKaydir(props.varsayilanBas, 6))
/** F2b (MK-42): garda aday yoksa yolculukla da ara; varsayılan kapalı (yolculuk süresi ücreti etkiler, sessizce üretilmez). */
/** F3b (MK-88): hangi personel türü atanır; KONDUKTOR: yalnız kondüktörü olmayan yolcu trenleri, yolculuk ve iyileştirme yok. Varsayılan makinist (istekte alan gönderilmez). */
const personelTuru = ref<'MAKINIST' | 'KONDUKTOR'>('MAKINIST')
const konduktor = computed(() => personelTuru.value === 'KONDUKTOR')
const yolculuk = ref(false)
/** F2d (MK-68): daha çok sefer dolduran plan ara; varsayılan kapalı (daha yavaş). Yolculukla birlikte kullanılamaz (MK-70). */
const iyilestir = ref(false)
const sonuc = ref<OtomatikAtamaSonucu | null>(null)
const hata = ref('')
/** Uygula sırasında yanıt gelmediyse: yeniden hesaplanan gerçek durumun açıklaması. */
const gercekDurum = ref('')
const mesgul = ref(false)
/** Önizlemenin bugünkü plana göre boş sayısı: Uygula yanıtında yoktur, kazanç satırı uygulamadan sonra da kalsın (QA UX-4). */
const onizlemeTabanBos = ref<number | null>(null)
/** Kapat ile atılan önizlemenin geç gelen yanıtını yok saymak için artan istek numarası (QA UX-2). */
let istekNo = 0

const aralikGecerli = computed(() => bas.value !== '' && bit.value !== '' && bit.value >= bas.value)
const uygulanabilir = computed(() => !!sonuc.value && !sonuc.value.uygulandi && !mesgul.value)
/** Uygula yazma işlemidir: sürerken kapatılamaz; önizleme sürerken Kapat sonucu atar. */
const uygulaSuruyor = ref(false)
const ozet = computed(() => {
  const s = sonuc.value
  if (!s) return ''
  const gecmis = s.gecmisSayisi > 0 ? ` (${s.gecmisSayisi} geçmiş sefer atlandı)` : ''
  const yolculuklu = s.atananlar.filter((a) => a.yolculuk).length
  const yolcu =
    yolculuklu > 0
      ? ` · ${yolculuklu} atama yolculuklu: her biri için iki satır yazılır (yolcu + asıl)`
      : ''
  return s.uygulandi
    ? `${s.atananSayisi} atandı, ${s.bosSayisi} boş kaldı${gecmis}${yolcu}`
    : `${s.atananSayisi} sefer atanacak, ${s.bosSayisi} sefer boş kalacak${gecmis}${yolcu}`
})

/**
 * İyileştirme özeti (W3/QA UX-1): aramanın neden bittiği dürüstçe söylenir. "Kazanç bulamadı" yalnız gerçekten ek deneme yapıldıysa; boş sefer yoksa ya da tur 0 bütçeyi
 * doldurduysa ayrı metin.
 */
const iyilestirmeOzeti = computed(() => {
  const iy = sonuc.value?.iyilestirme
  if (!sonuc.value || !iy) return ''
  const taban = iy.tabanBosSayisi ?? onizlemeTabanBos.value
  if (taban == null) return ''
  const kazanc = taban - sonuc.value.bosSayisi
  if (kazanc > 0) return `${kazanc} sefer daha dolduruldu (${taban} → ${sonuc.value.bosSayisi} boş)`
  if (taban === 0 || iy.durma === 'BOS_YOK') return 'Boş sefer yok; iyileştirmeye gerek kalmadı'
  if (iy.durma === 'SURE' && iy.tur <= 1)
    return 'Süre bütçesi bugünkü planı kurmaya yetti; iyileştirme denenemedi (yönetici iyilestir.sure_sn ayarını artırabilir)'
  if (iy.durma === 'TUR_SINIRI' && iy.tur <= 1)
    return 'İyileştirme tur sayısı ayarı 1 olduğundan ek deneme yapılmadı (yönetici iyilestir.tur ayarını artırabilir)'
  return `${iy.tur} tur denendi, daha iyi plan bulunamadı; bugünkü plan korundu`
})

const nobetMetinleri = computed(() =>
  (sonuc.value?.bosKalanlar ?? []).flatMap((b) => [b.neden, ...b.ornekler]),
)

// Tarih değişti: eski önizleme/sonuç artık bu aralığa ait değil.
watch([bas, bit, yolculuk, iyilestir, personelTuru], () => {
  if (konduktor.value) yolculuk.value = iyilestir.value = false
  sonuc.value = null
  onizlemeTabanBos.value = null
  hata.value = ''
  gercekDurum.value = ''
})

/** Bayraklar kapalıyken gövdede `yolculuk`/`iyilestir` alanı yoktur (bugünkü istek birebir aynı). Uygula, önizlemenin anahtarını geri gönderir. */
const govde = (uygula: boolean) => ({
  bas: bas.value,
  bit: bit.value,
  uygula,
  ...(konduktor.value ? { personelTuru: 'KONDUKTOR' } : {}),
  ...(yolculuk.value ? { yolculuk: true } : {}),
  ...(iyilestir.value ? { iyilestir: true } : {}),
  ...(uygula && iyilestir.value && sonuc.value?.iyilestirme
    ? { anahtar: sonuc.value.iyilestirme.anahtar }
    : {}),
})

async function calistir(uygula: boolean) {
  if (mesgul.value || !aralikGecerli.value || (uygula && !uygulanabilir.value)) return
  const no = ++istekNo
  mesgul.value = true
  uygulaSuruyor.value = uygula
  hata.value = ''
  gercekDurum.value = ''
  try {
    const s = await atamaApi.otomatik(govde(uygula))
    if (no !== istekNo) return // Kapat ile atılan önizleme
    sonuc.value = s
    if (!uygula) onizlemeTabanBos.value = s.iyilestirme?.tabanBosSayisi ?? null
    if (uygula) emit('uygulandi')
  } catch (e) {
    if (no !== istekNo) return
    sonuc.value = null
    if (uygula && e instanceof ApiHata && e.kod === 'AG_HATASI') await gercekDurumuOku()
    else hata.value = e instanceof ApiHata ? e.detail : 'Otomatik atama başarısız oldu.'
  } finally {
    if (no === istekNo) {
      mesgul.value = false
      uygulaSuruyor.value = false
    }
  }
}

/**
 * Uygula'ya yanıt gelmedi (zaman aşımı/bağlantı): sunucu atamaları yazmış olabilir. Masa yenilenir ve aynı aralık yeniden önizlenir; bu önizleme gerçek durumu söyler
 * (atanacak sefer kalmadıysa uygulama tamamlanmıştır). Uygulama tek işlemdir: ya hepsi yazılır ya hiçbiri.
 */
async function gercekDurumuOku() {
  emit('uygulandi')
  try {
    const s = await atamaApi.otomatik(govde(false))
    sonuc.value = s
    gercekDurum.value =
      s.atananSayisi === 0
        ? 'Sunucudan yanıt gelmedi, ama atamalar uygulanmış görünüyor: bu aralıkta atanabilir boş sefer kalmadı. Aşağıdaki liste kalan boş seferlerdir.'
        : `Sunucudan yanıt gelmedi ve atamalar henüz yazılmamış görünüyor (${s.atananSayisi} sefer hâlâ atanabilir). İşlem sunucuda sürüyor olabilir: biraz bekleyip Önizle ile kontrol edin; gerekirse Uygula'yı yeniden çalıştırabilirsiniz (çift atama olmaz).`
  } catch {
    hata.value =
      'Sunucudan yanıt gelmedi ve güncel durum da okunamadı. Atamalar yazılmış olabilir: masayı kontrol edin, sonra Önizle ile tekrar bakın.'
  }
}

function kapat() {
  if (uygulaSuruyor.value) return // yazma sürerken kapatılamaz
  istekNo++ // süren önizleme varsa sonucu atılır (sunucu en geç bütçesi kadar sonra biter, yazmaz)
  emit('kapat')
}

const saatAraligi = (a: { kalkis: string; varis: string }) =>
  `${saatBicimle(a.kalkis)}–${saatBicimle(a.varis)}`
</script>

<template>
  <Diyalog baslik="Otomatik atama" boyut="genis" @kapat="kapat">
    <p class="soluk">
      {{
        konduktor
          ? 'Yalnız kondüktörü olmayan yolcu trenleri doldurulur'
          : 'Yalnız boş ASIL görevler doldurulur'
      }}; elle atanmış ve iptal seferlere dokunulmaz. Önce önizleyin, sonra uygulayın.
    </p>

    <div class="arac-cubugu">
      <div class="alan">
        <label for="oto-tur">Personel türü</label>
        <select id="oto-tur" v-model="personelTuru">
          <option value="MAKINIST">Makinist</option>
          <option value="KONDUKTOR">Kondüktör</option>
        </select>
      </div>
      <div class="alan">
        <label for="oto-bas">Başlangıç</label>
        <input id="oto-bas" v-model="bas" type="date" required />
      </div>
      <div class="alan">
        <label for="oto-bit">Bitiş</label>
        <input id="oto-bit" v-model="bit" type="date" required />
      </div>
      <div class="alan alan-onay">
        <label for="oto-yolculuk">
          <input
            id="oto-yolculuk"
            v-model="yolculuk"
            type="checkbox"
            :disabled="iyilestir || konduktor"
          />
          Garda aday yoksa yolculukla ara
        </label>
        <small class="soluk"
          >Makinist önce başka bir seferde yolcu olarak gider (iki satır yazılır); yolculuk süresi
          çalışma süresine yönetici ayarına göre eklenir; km eklenmez.</small
        >
      </div>
      <div class="alan alan-onay">
        <label for="oto-iyilestir">
          <input
            id="oto-iyilestir"
            v-model="iyilestir"
            type="checkbox"
            :disabled="yolculuk || konduktor"
          />
          Daha çok sefer doldur (daha yavaş; önizleme yirmi saniyeye kadar sürebilir)
        </label>
        <small class="soluk"
          >Boşta kalan seferler için sıralamayı değiştirip planı yeniden dener; bugünkü plandan kötü
          sonuç vermez.<template v-if="yolculuk || iyilestir">
            İyileştirme şimdilik yolculuk aşamasıyla birlikte kullanılamaz.</template
          ></small
        >
      </div>
    </div>

    <div role="alert">
      <p v-if="hata" class="bildirim bildirim-hata">{{ hata }}</p>
    </div>
    <div role="status">
      <p v-if="mesgul" class="bildirim bildirim-bilgi" data-test="hesaplaniyor">
        {{ uygulaSuruyor ? 'Atamalar yazılıyor…' : 'Hesaplanıyor…' }}
        <template v-if="iyilestir && !uygulaSuruyor">
          İyileştirmeli önizleme 10-20 sn sürebilir; beklemek istemezseniz Kapat'a
          basabilirsiniz.</template
        >
      </p>
      <p v-if="gercekDurum" class="bildirim bildirim-uyari">{{ gercekDurum }}</p>
      <p v-if="sonuc" class="bildirim bildirim-bilgi">
        <strong>{{ ozet }}</strong>
      </p>
      <p v-if="iyilestirmeOzeti" class="bildirim bildirim-bilgi" data-test="iyilestirme-ozet">
        {{ iyilestirmeOzeti }}
      </p>
    </div>

    <template v-if="sonuc">
      <h3>{{ sonuc.uygulandi ? 'Atananlar' : 'Atanacaklar' }} ({{ sonuc.atananlar.length }})</h3>
      <p v-if="sonuc.atananlar.length === 0" class="soluk">Atanacak sefer yok.</p>
      <div v-else class="tablo-kap otomatik-kaydir">
        <table class="tablo" aria-label="Atanacak seferler">
          <thead>
            <tr>
              <th>Tarih / saat</th>
              <th>Tren no</th>
              <th>Güzergâh</th>
              <th>Personel</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="a in sonuc.atananlar" :key="a.seferId">
              <td>{{ tarihBicimle(a.tarih) }} {{ saatAraligi(a) }}</td>
              <td>{{ a.trenNo }}</td>
              <td>{{ a.kalkisGar }} – {{ a.varisGar }}</td>
              <td>
                {{ a.ad }} ({{ a.sicil }})
                <div v-if="a.yolculuk" class="soluk" data-test="yolculuk-satiri">
                  + Yolcu: {{ a.yolculuk.trenNo }} {{ a.yolculuk.kalkisGar }}
                  {{ saatBicimle(a.yolculuk.kalkis) }} → {{ a.yolculuk.varisGar }}
                  {{ saatBicimle(a.yolculuk.varis) }}
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <h3>
        {{ sonuc.uygulandi ? 'Boş kalanlar' : 'Boş kalacaklar' }} ({{ sonuc.bosKalanlar.length }})
      </h3>
      <p v-if="sonuc.bosKalanlar.length === 0" class="soluk">Boş kalacak sefer yok.</p>
      <div v-else class="tablo-kap otomatik-kaydir">
        <table class="tablo" aria-label="Boş kalacak seferler">
          <thead>
            <tr>
              <th>Tarih / saat</th>
              <th>Tren no</th>
              <th>Güzergâh</th>
              <th>Durum ve neden</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="b in sonuc.bosKalanlar" :key="b.seferId">
              <td>{{ tarihBicimle(b.tarih) }} {{ saatAraligi(b) }}</td>
              <td>{{ b.trenNo }}</td>
              <td>{{ b.kalkisGar }} – {{ b.varisGar }}</td>
              <td>
                <Rozet ton="uyari" simge="daire" metin="BOŞ" />
                <div class="bos-neden">{{ b.neden }}</div>
                <ul v-if="b.ornekler.length > 0" class="ornekler">
                  <li v-for="(o, i) in b.ornekler" :key="i">{{ o }}</li>
                </ul>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <NobetNotu :metinler="nobetMetinleri" />
    </template>

    <template #eylemler>
      <Dugme :disabled="!aralikGecerli || mesgul" @click="calistir(false)">Önizle</Dugme>
      <Dugme varyant="birincil" :disabled="!uygulanabilir" @click="calistir(true)"> Uygula </Dugme>
      <Dugme :disabled="uygulaSuruyor" @click="kapat">Kapat</Dugme>
    </template>
    <template v-if="uygulaSuruyor" #neden>Atamalar yazılırken pencere kapatılamaz.</template>
  </Diyalog>
</template>

<style scoped>
h3 {
  font-size: var(--yazi-lg);
  margin: var(--aralik-4) 0 var(--aralik-2);
}
.otomatik-kaydir {
  max-height: 25rem;
  overflow: auto;
}
.bos-neden {
  color: var(--renk-tehlike);
  font-weight: 600;
}
.ornekler {
  margin: var(--aralik-2) 0 0;
  padding-left: var(--aralik-5);
  color: var(--renk-metin-soluk);
  font-size: var(--yazi-md);
}
</style>
