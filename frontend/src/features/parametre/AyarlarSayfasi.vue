<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { ApiHata } from '@/api/istemci'
import Rozet from '@/bilesenler/Rozet.vue'
import SayfaBasligi from '@/bilesenler/SayfaBasligi.vue'
import type { SimgeAdi } from '@/bilesenler/simgeler'
import VeriDurumu from '@/bilesenler/VeriDurumu.vue'
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
const yukleniyor = ref(true)
const sadeceFarkli = ref(false)

/** Güvenilirlik rozeti NÖTR tonda kalır (hata/başarı renkleriyle karışmasın); düzey metinde, simgede ve kenar çizgisinde (düşük: kesikli) taşınır. */
const GUVEN_SIMGELERI: Record<Guvenilirlik, SimgeAdi> = {
  YUKSEK: 'basari',
  ORTA: 'daire',
  DUSUK: 'bilgi',
}
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
  yukleniyor.value = true
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
  } finally {
    yukleniyor.value = false
  }
}

onMounted(yukle)

const gruplar = computed(() => gruplaYap(satirlar.value))
const uyarilar = computed(() => ayarUyarilari(satirlar.value))
const uyariVar = computed(() => uyarilar.value.some((u) => u.duzey === 'UYARI'))

const kaydedilebilir = (t: Taslak) => t.neden.trim() !== '' && !t.mesgul
// Karşılaştırma tipine göre (sayıda 0.1 == 0.10): metin farkı kanonik olmayan tohumda sahte "farklı" üretirdi.
const degisti = (s: AyarSatiri) => !degerEsit(s.sayisal, taslak(s).deger, s.deger)
const farkli = (s: AyarSatiri) => !degerEsit(s.sayisal, s.deger, s.varsayilan)

const farkliSayisi = computed(() => satirlar.value.filter(farkli).length)
/** Süzgeç açıkken yalnız varsayılandan farklı satırlar (kayıt/hata iletisi bekleyen satır kaybolmaz). */
const gorunenGruplar = computed(() =>
  sadeceFarkli.value
    ? gruplar.value
        .map((g) => ({
          ...g,
          satirlar: g.satirlar.filter((s) => farkli(s) || taslak(s).bilgi || taslak(s).hata),
        }))
        .filter((g) => g.satirlar.length > 0)
    : gruplar.value,
)

/**
 * Hata metinleri TEK kez ve ait olduğu alanın ALTINDA: `neden` hatası neden kutusunun, diğer alan hataları değer alanının altında;
 * alan hatası yoksa genel hata satır altı mesajındadır ("Girilen bilgilerde hata var." alan mesajı varken gösterilmez).
 */
const nedenHatasi = (t: Taslak) => t.alanlar.neden ?? ''
const degerHatalari = (t: Taslak): string[] => [
  ...new Set(
    Object.entries(t.alanlar)
      .filter(([k, v]) => k !== 'neden' && v)
      .map(([, v]) => v),
  ),
]
const genelHata = (t: Taslak) => (Object.values(t.alanlar).some(Boolean) ? '' : t.hata)

/** Elle bağlanan `aria-describedby` / odak kimlikleri (anahtarda `:` ve `.` olabilir; `getElementById` ile sorun yok). */
const kimlik = (s: AyarSatiri, ek: string) => `ayar-${ek}-${s.tur}-${s.anahtar}`
const bagla = (...kimlikler: (string | false)[]) => kimlikler.filter(Boolean).join(' ') || undefined
const degerBaglantisi = (s: AyarSatiri) =>
  bagla(
    sinirMetni(s) !== '' && kimlik(s, 'sinir'),
    degerHatalari(taslak(s)).length > 0 && kimlik(s, 'deger-hata'),
  )
const nedenBaglantisi = (s: AyarSatiri) =>
  bagla(
    nedenHatasi(taslak(s)) !== '' && kimlik(s, 'neden-hata'),
    kaydetPasifNedeni(taslak(s)) !== '' && kimlik(s, 'pasif'),
  )

/** Hata sonrası odak: hatalı değer alanına, değer hatası yoksa neden kutusuna. */
async function hataOdagi(s: AyarSatiri) {
  await nextTick()
  const t = taslak(s)
  document.getElementById(kimlik(s, degerHatalari(t).length > 0 ? 'deger' : 'neden'))?.focus()
}

/** Grup gezgini: bağlantı çalışır (href); SPA adresini bozmadan başlığa kaydırıp odağı oraya alır. */
function git(hedef: string) {
  const el = document.getElementById(hedef)
  if (!el) return
  el.scrollIntoView?.({ block: 'start' })
  el.focus({ preventScroll: true })
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
    void hataOdagi(s)
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
    <SayfaBasligi baslik="Ayarlar" />
    <p class="bildirim bildirim-uyari" role="note">
      <strong>
        Bu uygulama karar destek aracıdır; mevzuata uyum garantisi vermez. Eşikler ve şiddetler
        sizin sorumluluğunuzdadır.
      </strong>
    </p>
    <details class="ayar-not">
      <summary>Nasıl okunur? Güvenilirlik ve özel kurallar</summary>
      <p class="soluk">
        Kurallar ve değerler gruplar hâlinde listelenir. Kendi kurallarınızı eklemek için
        <RouterLink to="/ozel-kurallar">Özel kurallar</RouterLink> sayfasını kullanın.
      </p>
      <p class="soluk">
        Güvenilirlik, kaynağın ne kadar kesin olduğunu gösterir: Yüksek (yasa ya da yönetmelik
        metni), Orta (kurum emri, sözleşme ya da yoruma açık madde), Düşük (varsayım ya da
        doğrulanmamış bilgi).
      </p>
    </details>
    <p v-if="!yoneticiMi" class="soluk">Değerleri yalnız yönetici değiştirebilir.</p>

    <VeriDurumu v-if="yukleniyor" durum="yukleniyor" metin="Ayarlar yükleniyor…" />
    <VeriDurumu v-else-if="yuklemeHatasi" durum="hata" :metin="yuklemeHatasi">
      <template #eylem>
        <button type="button" class="btn" @click="yukle">Yeniden dene</button>
      </template>
    </VeriDurumu>

    <template v-if="!yukleniyor">
      <details v-if="uyarilar.length" class="uyarilar" :open="uyariVar">
        <summary>Ayar uyarıları ({{ uyarilar.length }})</summary>
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
      </details>

      <div v-if="satirlar.length" class="ayar-arac">
        <div class="suzgec">
          <button
            type="button"
            class="btn"
            :aria-pressed="sadeceFarkli"
            @click="sadeceFarkli = !sadeceFarkli"
          >
            Varsayılandan farklı ({{ farkliSayisi }})
          </button>
        </div>
      </div>

      <div class="ayar-duzen">
        <nav class="grup-gezgin" aria-label="Ayar grupları">
          <ul>
            <li v-for="g in gorunenGruplar" :key="g.kod">
              <a :href="`#grup-${g.kod}`" @click.prevent="git(`grup-${g.kod}`)">
                {{ g.baslik }}<span class="grup-sayac sayisal">{{ g.satirlar.length }}</span>
              </a>
            </li>
            <li>
              <a href="#sistem-baslik" @click.prevent="git('sistem-baslik')">Sistem</a>
            </li>
          </ul>
        </nav>

        <div class="ayar-icerik">
          <section v-for="g in gorunenGruplar" :key="g.kod" :aria-labelledby="`grup-${g.kod}`">
            <h2 :id="`grup-${g.kod}`" tabindex="-1">{{ g.baslik }}</h2>
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
                      <td class="ad-hucre">
                        <strong>{{ s.ad }}</strong>
                        <span v-if="s.tur === 'SIDDET'" class="tur"> Kural {{ s.anahtar }}</span>
                        <Rozet
                          v-if="farkli(s)"
                          class="farkli-etiket"
                          ton="bilgi"
                          boyut="sm"
                          metin="Varsayılandan farklı"
                        />
                        <div class="ayar-aciklama">{{ s.aciklama }}</div>
                      </td>
                      <td class="deger-td">
                        <div class="deger-hucre">
                          <select
                            v-if="s.tur === 'SIDDET'"
                            :id="kimlik(s, 'deger')"
                            v-model="taslak(s).deger"
                            :aria-label="`${s.ad} şiddeti`"
                            :aria-invalid="degerHatalari(taslak(s)).length > 0"
                            :aria-describedby="degerBaglantisi(s)"
                            :disabled="!yoneticiMi"
                          >
                            <option v-for="(etiket, k) in SIDDET_ETIKETLERI" :key="k" :value="k">
                              {{ etiket }}
                            </option>
                          </select>
                          <label v-else-if="s.bayrak" class="bayrak">
                            <input
                              :id="kimlik(s, 'deger')"
                              type="checkbox"
                              :checked="taslak(s).deger === '1'"
                              :aria-label="`${s.ad} değeri`"
                              :aria-invalid="degerHatalari(taslak(s)).length > 0"
                              :aria-describedby="degerBaglantisi(s)"
                              :disabled="!yoneticiMi"
                              @change="
                                taslak(s).deger = ($event.target as HTMLInputElement).checked
                                  ? '1'
                                  : '0'
                              "
                            />
                            {{ taslak(s).deger === '1' ? 'Açık' : 'Kapalı' }}
                          </label>
                          <template v-else>
                            <input
                              :id="kimlik(s, 'deger')"
                              v-model="taslak(s).deger"
                              :class="{ sayisal: s.sayisal, 'deger-sayi': s.sayisal }"
                              :aria-label="`${s.ad} değeri`"
                              :aria-invalid="degerHatalari(taslak(s)).length > 0"
                              :aria-describedby="degerBaglantisi(s)"
                              :inputmode="s.sayisal ? 'decimal' : undefined"
                              :readonly="!yoneticiMi"
                              maxlength="100"
                            />
                            <span v-if="s.birim">{{ s.birim }}</span>
                          </template>
                        </div>
                        <small v-if="sinirMetni(s)" :id="kimlik(s, 'sinir')">{{
                          sinirMetni(s)
                        }}</small>
                        <div v-if="degerHatalari(taslak(s)).length" :id="kimlik(s, 'deger-hata')">
                          <p
                            v-for="m in degerHatalari(taslak(s))"
                            :key="m"
                            class="alan-hata"
                            role="alert"
                          >
                            {{ m }}
                          </p>
                        </div>
                      </td>
                      <td class="varsayilan-td">
                        <span class="sayisal">{{ degerMetni(s, s.varsayilan) }}</span>
                        <button
                          v-if="yoneticiMi"
                          type="button"
                          class="btn btn-kucuk don-dugme"
                          :aria-label="`${s.ad} varsayılana dön`"
                          :disabled="!farkli(s) || taslak(s).mesgul"
                          @click="varsayilanaDon(s)"
                        >
                          Varsayılana dön
                        </button>
                      </td>
                      <td class="kaynak-td">
                        <div class="kaynak-metin">{{ kaynakMetni(s.kaynak) }}</div>
                        <Rozet
                          v-if="s.guvenilirlik"
                          class="guven"
                          :class="`guven-${s.guvenilirlik.toLowerCase()}`"
                          ton="notr"
                          boyut="sm"
                          :simge="GUVEN_SIMGELERI[s.guvenilirlik]"
                          :metin="`Güvenilirlik: ${GUVENILIRLIK_ETIKETLERI[s.guvenilirlik]}`"
                        />
                      </td>
                    </tr>
                    <tr v-if="yoneticiMi && degisti(s)" class="neden-satiri">
                      <td colspan="4">
                        <form class="neden-formu" @submit.prevent="satirKaydet(s)">
                          <input
                            :id="kimlik(s, 'neden')"
                            v-model="taslak(s).neden"
                            class="neden-girdi"
                            :aria-label="`${s.ad} değişiklik nedeni`"
                            :aria-invalid="nedenHatasi(taslak(s)) !== ''"
                            :aria-describedby="nedenBaglantisi(s)"
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
                          <small
                            v-if="kaydetPasifNedeni(taslak(s))"
                            :id="kimlik(s, 'pasif')"
                            class="pasif-neden"
                          >
                            {{ kaydetPasifNedeni(taslak(s)) }}
                          </small>
                          <p
                            v-if="nedenHatasi(taslak(s))"
                            :id="kimlik(s, 'neden-hata')"
                            class="alan-hata neden-hata"
                            role="alert"
                          >
                            {{ nedenHatasi(taslak(s)) }}
                          </p>
                        </form>
                      </td>
                    </tr>
                    <tr v-if="genelHata(taslak(s)) || taslak(s).bilgi">
                      <td colspan="4" class="satir-mesaj">
                        <p v-if="genelHata(taslak(s))" class="alan-hata" role="alert">
                          {{ genelHata(taslak(s)) }}
                        </p>
                        <p v-else-if="!taslak(s).hata" class="bilgi" role="status">
                          {{ taslak(s).bilgi }}
                        </p>
                      </td>
                    </tr>
                  </template>
                </tbody>
              </table>
            </div>
          </section>

          <VeriDurumu
            v-if="satirlar.length === 0 && !yuklemeHatasi"
            durum="bos"
            metin="Kayıt yok."
          />
          <VeriDurumu
            v-else-if="sadeceFarkli && gorunenGruplar.length === 0"
            durum="bos"
            metin="Varsayılandan farklı ayar yok."
          >
            <template #eylem>
              <button type="button" class="btn" @click="sadeceFarkli = false">
                Süzgeci kaldır
              </button>
            </template>
          </VeriDurumu>
          <p><small>K-10 sabit olarak ENGEL'dir ve değiştirilemez.</small></p>

          <section aria-labelledby="sistem-baslik">
            <h2 id="sistem-baslik" tabindex="-1">Sistem</h2>
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
        </div>
      </div>
    </template>
  </section>
</template>

<style scoped>
h2 {
  margin-top: var(--aralik-6);
  scroll-margin-top: var(--aralik-5);
}
h2:focus {
  outline: none;
}
h2:focus-visible {
  outline: 2px solid var(--renk-odak);
  outline-offset: 2px;
}
.ayar-not,
.uyarilar {
  margin-bottom: var(--aralik-4);
}
.ayar-not summary,
.uyarilar summary {
  cursor: pointer;
  color: var(--renk-metin-ikincil);
  font-weight: var(--yazi-agirlik-orta);
}
.uyarilar .bildirim {
  margin-top: var(--aralik-3);
}
.ayar-arac {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--aralik-4);
  margin-bottom: var(--aralik-4);
}
.ayar-duzen {
  display: block;
}
.grup-gezgin ul {
  display: flex;
  flex-wrap: wrap;
  gap: var(--aralik-2) var(--aralik-4);
  margin: 0 0 var(--aralik-4);
  padding: 0;
  list-style: none;
}
.grup-gezgin a {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--aralik-3);
  padding: var(--aralik-2) var(--aralik-3);
  border-radius: var(--yaricap-sm);
  color: var(--renk-metin-ikincil);
  text-decoration: none;
}
.grup-gezgin a:hover {
  background: var(--renk-n-100);
  color: var(--renk-metin);
}
.grup-sayac {
  color: var(--renk-metin-soluk);
  font-size: var(--yazi-sm);
}
.ayar-icerik {
  min-width: 0;
}
.ayar-icerik > section:first-child h2 {
  margin-top: 0;
}
@media (min-width: 64rem) {
  .ayar-duzen {
    display: grid;
    grid-template-columns: 14rem minmax(0, 1fr);
    gap: var(--aralik-6);
    align-items: start;
  }
  .grup-gezgin {
    position: sticky;
    top: var(--aralik-5);
  }
  .grup-gezgin ul {
    display: block;
    margin: 0;
  }
}
.ad-hucre {
  min-width: 14rem;
}
.ayar-aciklama {
  color: var(--renk-metin-soluk);
  font-size: var(--yazi-sm);
}
.deger-td {
  min-width: 11rem;
}
.deger-hucre {
  display: flex;
  align-items: center;
  gap: var(--aralik-3);
}
.deger-sayi {
  width: 7rem;
}
.bayrak {
  display: inline-flex;
  align-items: center;
  gap: var(--aralik-3);
}
.tur {
  color: var(--renk-metin-soluk);
  font-size: var(--yazi-sm);
}
.farkli-etiket {
  margin-left: var(--aralik-3);
  white-space: nowrap;
}
.farkli td {
  background: var(--renk-bilgi-zemin);
}
.varsayilan-td {
  white-space: nowrap;
}
.don-dugme {
  margin-left: var(--aralik-3);
  white-space: nowrap;
}
.kaynak-td {
  min-width: 11rem;
}
.kaynak-metin {
  margin-bottom: var(--aralik-2);
}
.guven {
  white-space: nowrap;
}
.guven-dusuk {
  border-style: dashed;
}
.neden-satiri td {
  background: var(--renk-n-50);
}
.neden-formu {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--aralik-3);
}
.neden-girdi {
  flex: 1 1 18rem;
}
.pasif-neden {
  flex-basis: 100%;
  color: var(--renk-metin-soluk);
}
.neden-hata {
  flex-basis: 100%;
}
.satir-mesaj {
  padding-top: 0;
}
.bilgi {
  margin: 0;
  color: var(--renk-basari-metin);
}
</style>
