<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ApiHata } from '@/api/istemci'
import { yoneticiMi } from '@/composables/oturum'
import {
  GUVENILIRLIK_ETIKETLERI,
  SIDDET_ETIKETLERI,
  ayarSatiri,
  ayarUyarilari,
  birlestir,
  degerEsit,
  gruplaYap,
  kaynakMetni,
  kuralSatiri,
  parametreApi,
  parametreSatiri,
  satirAnahtari,
  type AyarSatiri,
  type Guvenilirlik,
  type KuralSiddeti,
} from './parametreApi'

/** Satır başına düzenleme durumu (anahtar: `satirAnahtari`). */
interface Taslak {
  deger: string
  neden: string
  mesgul: boolean
  hata: string
  alanlar: Record<string, string>
  bilgi: string
}

const satirlar = ref<AyarSatiri[]>([])
const taslaklar = reactive<Record<string, Taslak>>({})
const yuklemeHatasi = ref('')

const SIMGELER: Record<Guvenilirlik, string> = { YUKSEK: '✔', ORTA: '◐', DUSUK: '▲' }
const VARSAYILANA_DONUS_NEDENI = 'Varsayılana döndürüldü'
const GECERLILIK_NOTU =
  'Geçerli: hemen. Mevcut atamalar yeni kurallara göre yeniden değerlendirilir; kırmızı olanlar Atama masasında görünür.'

/** Güvenlik ve performans için koda gömülü, bu ekrandan değişmeyen sınırlar (değerler kodda doğrulandı). */
const SISTEM_SINIRLARI = [
  { ad: 'Giriş kilidi', deger: '5 hatalı denemede 15 dakika' },
  { ad: 'Parola uzunluğu', deger: 'En az 10 karakter' },
  { ad: 'Oturum süresi', deger: '8 saat' },
  { ad: 'Excel dosyası', deger: 'En çok 5 MB ve 20.000 satır' },
  { ad: 'Otomatik atama aralığı', deger: 'En çok 62 gün' },
  { ad: 'Özel kural sayısı', deger: 'En çok 50 aktif kural' },
]

const yeniTaslak = (deger: string): Taslak => ({
  deger,
  neden: '',
  mesgul: false,
  hata: '',
  alanlar: {},
  bilgi: '',
})

const taslak = (s: AyarSatiri): Taslak => taslaklar[satirAnahtari(s)]!

async function yukle() {
  yuklemeHatasi.value = ''
  try {
    // Uygulama ayarlarının listesi yalnız yöneticiye açıktır.
    const [k, p, a] = await Promise.all([
      parametreApi.kurallar(),
      parametreApi.parametreler(),
      yoneticiMi.value ? parametreApi.ayarlar() : Promise.resolve([]),
    ])
    satirlar.value = birlestir(k, p, a)
    for (const s of satirlar.value) taslaklar[satirAnahtari(s)] = yeniTaslak(s.deger)
  } catch (e) {
    yuklemeHatasi.value = e instanceof ApiHata ? e.detail : 'Veriler yüklenemedi.'
  }
}

onMounted(yukle)

const gruplar = computed(() => gruplaYap(satirlar.value))
const uyarilar = computed(() => ayarUyarilari(satirlar.value))

const kaydedilebilir = (t: Taslak) => t.neden.trim() !== '' && !t.mesgul
// Karşılaştırma tipine göre (sayıda 0.1 == 0.10): metin farkı kanonik olmayan tohumda sahte "farklı" üretirdi.
const degisti = (s: AyarSatiri) => !degerEsit(s.sayisal, taslak(s).deger, s.deger)
const farkli = (s: AyarSatiri) => !degerEsit(s.sayisal, s.deger, s.varsayilan)

/** Satırın hata metinleri TEK kez: alan hataları varsa onlar (genel "Girilen bilgilerde hata var." gereksiz), yoksa genel hata; aynı metin tekrarlanmaz. */
const hataMesajlari = (t: Taslak): string[] => {
  const alan = Object.values(t.alanlar).filter(Boolean)
  return [...new Set(alan.length ? alan : t.hata ? [t.hata] : [])]
}

/** Kaydet pasifken nedeni. */
const kaydetPasifNedeni = (t: Taslak) =>
  t.mesgul
    ? 'Kaydediliyor…'
    : t.neden.trim() === ''
      ? 'Kaydetmek için değişiklik nedenini yazın.'
      : ''

/** Ortak kayıt akışı: başarıda satır sunucunun güncel değeriyle yenilenir. */
async function kaydet(s: AyarSatiri, cagri: (neden: string) => Promise<AyarSatiri>) {
  const t = taslak(s)
  if (!kaydedilebilir(t)) return
  t.mesgul = true
  t.hata = ''
  t.alanlar = {}
  t.bilgi = ''
  try {
    const guncel = await cagri(t.neden.trim())
    const anahtar = satirAnahtari(s)
    satirlar.value = satirlar.value.map((x) => (satirAnahtari(x) === anahtar ? guncel : x))
    taslaklar[anahtar] = { ...yeniTaslak(guncel.deger), bilgi: `Kaydedildi. ${GECERLILIK_NOTU}` }
  } catch (e) {
    t.hata = e instanceof ApiHata ? e.detail : 'İşlem başarısız oldu.'
    t.alanlar = e instanceof ApiHata ? (e.alanlar ?? {}) : {}
    t.mesgul = false
  }
}

function satirKaydet(s: AyarSatiri) {
  const t = taslak(s)
  return kaydet(s, async (neden) => {
    if (s.tur === 'SIDDET') {
      return kuralSatiri(
        await parametreApi.kuralKaydet(s.anahtar, { siddet: t.deger as KuralSiddeti, neden }),
      )
    }
    if (s.tur === 'PARAMETRE') {
      return parametreSatiri(
        await parametreApi.parametreKaydet(s.anahtar, { deger: t.deger.trim(), neden }),
      )
    }
    const sayi = Number(t.deger)
    if (t.deger.trim() === '' || !Number.isInteger(sayi)) {
      throw new ApiHata(400, 'DOGRULAMA', 'Girilen bilgilerde hata var.', {
        alanlar: { deger: 'Değer tam sayı olmalı.' },
      })
    }
    return ayarSatiri(await parametreApi.ayarKaydet(s.anahtar, { deger: sayi, neden }))
  })
}

/** Taslak değeri varsayılana çeker ve nedeni önceden doldurur (düzenlenebilir); kayıt normal akışla yapılır. */
function varsayilanaDon(s: AyarSatiri) {
  const t = taslak(s)
  t.deger = s.varsayilan
  t.neden = VARSAYILANA_DONUS_NEDENI
  t.hata = ''
  t.alanlar = {}
  t.bilgi = ''
}

function sinirMetni(s: AyarSatiri): string {
  if (s.alt === null && s.ust === null) return ''
  return `Sınır: ${s.alt ?? '…'} – ${s.ust ?? '…'}`
}

const degerMetni = (s: AyarSatiri, deger: string): string => {
  if (s.tur === 'SIDDET') return SIDDET_ETIKETLERI[deger as KuralSiddeti] ?? deger
  if (s.bayrak) return deger === '1' ? 'Açık' : 'Kapalı'
  return s.birim ? `${deger} ${s.birim}` : deger
}
</script>

<template>
  <section>
    <div class="sayfa-baslik"><h1>Ayarlar</h1></div>
    <p class="bildirim bildirim-uyari" role="note">
      <strong>
        Bu uygulama karar destek aracıdır; mevzuata uyum garantisi vermez. Eşikler ve şiddetler
        sizin sorumluluğunuzdadır.
      </strong>
    </p>
    <p class="soluk">
      Kurallar ve değerler gruplar hâlinde listelenir. Kendi kurallarınızı eklemek için
      <RouterLink to="/ozel-kurallar">Özel kurallar</RouterLink> sayfasını kullanın.
    </p>
    <p class="soluk">
      Güvenilirlik, kaynağın ne kadar kesin olduğunu gösterir: Yüksek (yasa ya da yönetmelik metni),
      Orta (kurum emri, sözleşme ya da yoruma açık madde), Düşük (varsayım ya da doğrulanmamış
      bilgi).
    </p>
    <p v-if="yuklemeHatasi" class="bildirim bildirim-hata" role="alert">{{ yuklemeHatasi }}</p>
    <p v-if="!yoneticiMi" class="soluk">Değerleri yalnız yönetici değiştirebilir.</p>

    <div v-if="uyarilar.length" class="uyarilar" aria-label="Ayar uyarıları">
      <p
        v-for="u in uyarilar"
        :key="u.kod"
        class="bildirim"
        :class="u.duzey === 'UYARI' ? 'bildirim-uyari' : 'bildirim-bilgi'"
        role="note"
        :data-uyari="u.kod"
      >
        <span>
          <strong>{{ u.duzey === 'UYARI' ? 'Uyarı:' : 'Bilgi:' }}</strong> {{ u.metin }}
        </span>
      </p>
    </div>

    <section v-for="g in gruplar" :key="g.kod" :aria-labelledby="`grup-${g.kod}`">
      <h2 :id="`grup-${g.kod}`">{{ g.baslik }}</h2>
      <div class="tablo-kap">
        <table class="tablo" :aria-labelledby="`grup-${g.kod}`">
          <thead>
            <tr>
              <th>Ad ve açıklama</th>
              <th>Değer</th>
              <th>Varsayılan</th>
              <th>Kaynak</th>
            </tr>
          </thead>
          <tbody>
            <template v-for="s in g.satirlar" :key="satirAnahtari(s)">
              <tr :class="{ farkli: farkli(s) }">
                <td>
                  <strong>{{ s.ad }}</strong>
                  <span v-if="s.tur === 'SIDDET'" class="tur"> Kural {{ s.anahtar }}</span>
                  <span v-if="farkli(s)" class="farkli-etiket">Varsayılandan farklı</span>
                  <div>{{ s.aciklama }}</div>
                </td>
                <td>
                  <div class="deger-hucre">
                    <select
                      v-if="s.tur === 'SIDDET'"
                      v-model="taslak(s).deger"
                      :aria-label="`${s.ad} şiddeti`"
                      :disabled="!yoneticiMi"
                    >
                      <option v-for="(etiket, k) in SIDDET_ETIKETLERI" :key="k" :value="k">
                        {{ etiket }}
                      </option>
                    </select>
                    <label v-else-if="s.bayrak" class="bayrak">
                      <input
                        type="checkbox"
                        :checked="taslak(s).deger === '1'"
                        :aria-label="`${s.ad} değeri`"
                        :disabled="!yoneticiMi"
                        @change="
                          taslak(s).deger = ($event.target as HTMLInputElement).checked ? '1' : '0'
                        "
                      />
                      {{ taslak(s).deger === '1' ? 'Açık' : 'Kapalı' }}
                    </label>
                    <template v-else>
                      <input
                        v-model="taslak(s).deger"
                        :aria-label="`${s.ad} değeri`"
                        :aria-invalid="!!taslak(s).alanlar.deger"
                        :inputmode="s.sayisal ? 'decimal' : undefined"
                        :readonly="!yoneticiMi"
                        maxlength="100"
                      />
                      <span v-if="s.birim">{{ s.birim }}</span>
                    </template>
                  </div>
                  <small v-if="sinirMetni(s)">{{ sinirMetni(s) }}</small>
                  <form
                    v-if="yoneticiMi && degisti(s)"
                    class="neden-formu"
                    @submit.prevent="satirKaydet(s)"
                  >
                    <input
                      v-model="taslak(s).neden"
                      :aria-label="`${s.ad} değişiklik nedeni`"
                      :aria-invalid="!!taslak(s).alanlar.neden"
                      placeholder="Değişiklik nedeni (zorunlu)"
                      maxlength="500"
                    />
                    <button
                      type="submit"
                      class="btn btn-birincil"
                      :disabled="!kaydedilebilir(taslak(s))"
                    >
                      Kaydet
                    </button>
                    <small v-if="kaydetPasifNedeni(taslak(s))" class="pasif-neden">
                      {{ kaydetPasifNedeni(taslak(s)) }}
                    </small>
                  </form>
                </td>
                <td>
                  <div>{{ degerMetni(s, s.varsayilan) }}</div>
                  <button
                    v-if="yoneticiMi"
                    type="button"
                    class="btn"
                    :aria-label="`${s.ad} varsayılana dön`"
                    :disabled="!farkli(s) || taslak(s).mesgul"
                    @click="varsayilanaDon(s)"
                  >
                    Varsayılana dön
                  </button>
                </td>
                <td>
                  <div>{{ kaynakMetni(s.kaynak) }}</div>
                  <span
                    v-if="s.guvenilirlik"
                    class="guven"
                    :class="`guven-${s.guvenilirlik.toLowerCase()}`"
                  >
                    <span aria-hidden="true">{{ SIMGELER[s.guvenilirlik] }}</span>
                    Güvenilirlik: {{ GUVENILIRLIK_ETIKETLERI[s.guvenilirlik] }}
                  </span>
                </td>
              </tr>
              <tr v-if="taslak(s).hata || taslak(s).bilgi">
                <td colspan="4" class="satir-mesaj">
                  <template v-if="taslak(s).hata">
                    <p
                      v-for="m in hataMesajlari(taslak(s))"
                      :key="m"
                      class="alan-hata"
                      role="alert"
                    >
                      {{ m }}
                    </p>
                  </template>
                  <p v-else class="bilgi" role="status">{{ taslak(s).bilgi }}</p>
                </td>
              </tr>
            </template>
          </tbody>
        </table>
      </div>
    </section>
    <p v-if="satirlar.length === 0 && !yuklemeHatasi" class="bos-durum">Kayıt yok.</p>
    <p><small>K-10 sabit olarak ENGEL'dir ve değiştirilemez.</small></p>

    <section aria-labelledby="sistem-baslik">
      <h2 id="sistem-baslik">Sistem</h2>
      <p class="soluk">
        Bu sınırlar yalnız bilgi içindir, bu ekrandan değiştirilemez. Güvenlik ve performans
        içindir; değiştirmek için geliştirici gerekir.
      </p>
      <div class="tablo-kap">
        <table class="tablo" aria-labelledby="sistem-baslik">
          <thead>
            <tr>
              <th>Sınır</th>
              <th>Değer</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="x in SISTEM_SINIRLARI" :key="x.ad">
              <td>{{ x.ad }}</td>
              <td>{{ x.deger }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  </section>
</template>

<style scoped>
h2 {
  margin-top: 1.5rem;
}
.neden-formu {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  margin-top: 0.5rem;
}
.deger-hucre {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}
.bayrak {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
}
.tur {
  color: var(--renk-soluk);
  font-size: 0.85rem;
}
.farkli td {
  background: var(--renk-bilgi-zemin);
}
.farkli-etiket {
  display: inline-block;
  margin-left: 0.5rem;
  padding: 0.05rem 0.5rem;
  border: 1px solid currentcolor;
  border-radius: 999px;
  color: var(--renk-birincil-koyu);
  font-size: 0.8rem;
  font-weight: 600;
  white-space: nowrap;
}
.guven {
  display: inline-block;
  padding: 0.1rem 0.5rem;
  border: 1px solid currentcolor;
  border-radius: 999px;
  font-size: 0.85rem;
  white-space: nowrap;
}
.guven-yuksek {
  color: #1b6e2f;
}
.guven-orta {
  color: #8a5a00;
}
.guven-dusuk {
  color: #b3261e;
  font-weight: 600;
}
.pasif-neden {
  flex-basis: 100%;
  color: var(--renk-soluk);
}
.satir-mesaj {
  padding-top: 0;
}
.bilgi {
  margin: 0;
  color: #1b6e2f;
}
</style>
