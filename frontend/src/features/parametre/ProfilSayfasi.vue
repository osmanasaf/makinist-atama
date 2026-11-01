<script setup lang="ts">
/**
 * Kural profili (F2, MK-163): hazır başlangıç profili ya da dosya seç -> fark tablosu -> uygulanacak satırları seç -> neden -> uygula.
 * Önizleme hiçbir şey yazmaz; uygulama tek işlemde ve tüm-ya-hiç çalışır (sunucu paketi yeniden doğrular). Etiket her zaman görünür:
 * değerler örnek başlangıçtır, kurum mevzuatıyla doğrulanmalıdır.
 */
import { computed, nextTick, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { ApiHata } from '@/api/istemci'
import AlanHatasi from '@/bilesenler/AlanHatasi.vue'
import Diyalog from '@/bilesenler/Diyalog.vue'
import Dugme from '@/bilesenler/Dugme.vue'
import Rozet from '@/bilesenler/Rozet.vue'
import SayfaBasligi from '@/bilesenler/SayfaBasligi.vue'
import VeriDurumu from '@/bilesenler/VeriDurumu.vue'
import {
  degerMetni,
  durumGorunumu,
  gorunenSatirlar,
  gruplaSatirlar,
  secilebilir,
  secimGecersizMi,
  varsayilanSecim,
} from './profilFark'
import {
  EN_COK_PROFIL_BAYT,
  VARSAYILAN_ETIKET,
  profilApi,
  type HazirProfilOzeti,
  type ProfilOnizleme,
  type ProfilPaketi,
  type SatirDurumu,
  type UygulamaSonucu,
} from './profilApi'

const hazirlar = ref<HazirProfilOzeti[]>([])
const hazirDurumu = ref<'yukleniyor' | 'tamam' | 'hata'>('yukleniyor')
const onizleme = ref<ProfilOnizleme | null>(null)
const kaynakAdi = ref('')
const mesgul = ref(false)
const dosyaHatasi = ref('')
const genelHata = ref('')
const hepsiniGoster = ref(false)
const secili = ref<Set<string>>(new Set())
const neden = ref('')
const nedenHatasi = ref('')
const onayAcik = ref(false)
const sonuc = ref<UygulamaSonucu | null>(null)
const farkBasligi = ref<HTMLElement | null>(null)

const satirlar = computed(() => onizleme.value?.satirlar ?? [])
const gorunenler = computed(() => gorunenSatirlar(satirlar.value, hepsiniGoster.value))
const gruplar = computed(() => gruplaSatirlar(gorunenler.value))
const seciliSayisi = computed(() => secili.value.size)
const secimGecersiz = computed(() => secimGecersizMi(satirlar.value, secili.value))
const etiket = computed(() => onizleme.value?.paket.etiket || VARSAYILAN_ETIKET)
const ozetRozetleri = computed(() => {
  const o = onizleme.value?.ozet
  if (!o) return []
  return (
    [
      { kod: 'DEGISIR', sayi: o.degisir },
      { kod: 'AYNI', sayi: o.ayni },
      { kod: 'GECERSIZ', sayi: o.gecersiz },
      { kod: 'BILINMEYEN', sayi: o.bilinmeyen },
      { kod: 'PROFILDE_YOK', sayi: o.profildeYok },
    ] satisfies { kod: SatirDurumu; sayi: number }[]
  ).filter((r) => r.sayi > 0)
})

function hataMetni(e: unknown, varsayilan: string): string {
  return e instanceof ApiHata ? e.detail : varsayilan
}

async function hazirlariYukle() {
  hazirDurumu.value = 'yukleniyor'
  try {
    hazirlar.value = await profilApi.hazirListe()
    hazirDurumu.value = 'tamam'
  } catch {
    hazirDurumu.value = 'hata'
  }
}
onMounted(hazirlariYukle)

async function onizlemeKur(paket: ProfilPaketi, ad: string) {
  const o = await profilApi.onizle(paket)
  onizleme.value = o
  kaynakAdi.value = ad
  secili.value = varsayilanSecim(o.satirlar)
  hepsiniGoster.value = false
  sonuc.value = null
  neden.value = ''
  nedenHatasi.value = ''
  await nextTick()
  farkBasligi.value?.focus()
}

async function hazirAc(h: HazirProfilOzeti) {
  if (mesgul.value) return
  mesgul.value = true
  genelHata.value = ''
  dosyaHatasi.value = ''
  try {
    await onizlemeKur(await profilApi.hazir(h.kod), h.ad)
  } catch (e) {
    genelHata.value = hataMetni(e, 'Profil açılamadı.')
  } finally {
    mesgul.value = false
  }
}

async function dosyaSecildi(olay: Event) {
  const girdi = olay.target as HTMLInputElement
  const dosya = girdi.files?.[0] ?? null
  dosyaHatasi.value = ''
  genelHata.value = ''
  if (!dosya) return
  if (dosya.size > EN_COK_PROFIL_BAYT) {
    dosyaHatasi.value = 'Dosya çok büyük. En çok 1 MB yüklenebilir.'
    girdi.value = ''
    return
  }
  mesgul.value = true
  try {
    let paket: unknown
    try {
      paket = JSON.parse(await dosya.text())
    } catch {
      dosyaHatasi.value = 'Dosya okunamadı: geçerli bir JSON profil dosyası değil.'
      return
    }
    if (paket === null || typeof paket !== 'object' || Array.isArray(paket)) {
      dosyaHatasi.value = 'Bu dosya bir profil dosyası değil.'
      return
    }
    await onizlemeKur(paket as ProfilPaketi, `Dosya: ${dosya.name}`)
  } catch (e) {
    dosyaHatasi.value = hataMetni(e, 'Dosya denetlenemedi.')
  } finally {
    mesgul.value = false
    girdi.value = '' // aynı dosya yeniden seçilebilsin
  }
}

async function disaAktar() {
  genelHata.value = ''
  try {
    const { blob, ad } = await profilApi.disaAktar()
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = ad ?? 'makinist-atama-profil.json'
    a.click()
    URL.revokeObjectURL(url)
  } catch (e) {
    genelHata.value = hataMetni(e, 'Ayarlar dışa aktarılamadı.')
  }
}

function sec(kimlik: string, isaretli: boolean) {
  const yeni = new Set(secili.value)
  if (isaretli) yeni.add(kimlik)
  else yeni.delete(kimlik)
  secili.value = yeni
}

const tumunuSec = () => (secili.value = varsayilanSecim(satirlar.value))
const hicbirini = () => (secili.value = new Set())

function onayIste() {
  nedenHatasi.value = ''
  if (neden.value.trim() === '') {
    nedenHatasi.value = 'Değişiklik nedeni yazılmalı.'
    return
  }
  if (seciliSayisi.value === 0 || secimGecersiz.value) return
  genelHata.value = ''
  onayAcik.value = true
}

async function uygula() {
  if (!onizleme.value || mesgul.value) return
  mesgul.value = true
  try {
    const paket = onizleme.value.paket
    const s = await profilApi.uygula(paket, [...secili.value], neden.value.trim())
    onayAcik.value = false
    await onizlemeKur(paket, kaynakAdi.value) // yazılanlar artık "Aynı" görünür
    sonuc.value = s
  } catch (e) {
    onayAcik.value = false
    genelHata.value = hataMetni(e, 'Profil uygulanamadı. Hiçbir değişiklik yapılmadı.')
  } finally {
    mesgul.value = false
  }
}
</script>

<template>
  <section class="profil-sayfasi">
    <SayfaBasligi baslik="Kural profili">
      <template #eylemler>
        <Dugme simge="indir" data-test="disa-aktar" @click="disaAktar"
          >Mevcut ayarları dışa aktar</Dugme
        >
        <RouterLink class="btn" to="/ayarlar">Ayarlara dön</RouterLink>
      </template>
    </SayfaBasligi>

    <p class="bildirim bildirim-bilgi" role="note" data-test="etiket">{{ VARSAYILAN_ETIKET }}</p>
    <p class="soluk">
      Profil, ayarları bir dosya olarak taşımanızı ya da hazır bir başlangıç değerleri kümesini
      denemenizi sağlar. Önce farkları görürsünüz, sonra uygulanacak satırları seçersiniz; hiçbir
      şey onayınız olmadan yazılmaz.
    </p>

    <p v-if="genelHata" class="bildirim bildirim-hata" role="alert" data-test="genel-hata">
      {{ genelHata }}
    </p>

    <h2 id="hazir-baslik">Hazır profiller</h2>
    <VeriDurumu
      v-if="hazirDurumu === 'yukleniyor'"
      durum="yukleniyor"
      metin="Profiller yükleniyor…"
    />
    <VeriDurumu v-else-if="hazirDurumu === 'hata'" durum="hata" metin="Hazır profiller alınamadı.">
      <template #eylem>
        <Dugme @click="hazirlariYukle">Yeniden dene</Dugme>
      </template>
    </VeriDurumu>
    <ul v-else class="hazir-liste" aria-labelledby="hazir-baslik">
      <li v-for="h in hazirlar" :key="h.kod" class="hazir-kart" :data-test="`hazir-${h.kod}`">
        <div>
          <strong>{{ h.ad }}</strong>
          <p class="soluk">{{ h.aciklama }}</p>
        </div>
        <Dugme
          :disabled="mesgul"
          :aria-label="`${h.ad} profilinin farklarını göster`"
          @click="hazirAc(h)"
          >Farkları göster</Dugme
        >
      </li>
    </ul>

    <h2 id="dosya-baslik">Dosyadan yükle</h2>
    <div class="alan">
      <label for="profil-dosya">Profil dosyası (.json)</label>
      <input
        id="profil-dosya"
        type="file"
        accept=".json,application/json"
        :disabled="mesgul"
        :aria-invalid="!!dosyaHatasi"
        :aria-describedby="
          dosyaHatasi ? 'profil-dosya-yardim profil-dosya-hata' : 'profil-dosya-yardim'
        "
        data-test="dosya"
        @change="dosyaSecildi"
      />
      <p id="profil-dosya-yardim" class="alan-yardim">
        En çok 1 MB. Daha önce bu ekrandan dışa aktardığınız bir dosya olabilir. Seçilen dosya önce
        denetlenir; onaylamadan hiçbir ayar değişmez.
      </p>
      <AlanHatasi id="profil-dosya-hata" :metin="dosyaHatasi" />
    </div>
    <VeriDurumu v-if="mesgul && !onizleme" durum="yukleniyor" metin="Profil denetleniyor…" />

    <section v-if="onizleme" class="fark-bolumu" aria-labelledby="fark-baslik">
      <h2 id="fark-baslik" ref="farkBasligi" tabindex="-1">{{ kaynakAdi }} — farklar</h2>
      <p class="bildirim bildirim-uyari" role="note" data-test="paket-etiketi">{{ etiket }}</p>
      <p v-if="onizleme.paket.aciklama" class="soluk">{{ onizleme.paket.aciklama }}</p>

      <ul class="ozet-rozetleri" aria-label="Fark özeti" data-test="ozet">
        <li v-for="r in ozetRozetleri" :key="r.kod">
          <Rozet
            :ton="durumGorunumu(r.kod).ton"
            :simge="durumGorunumu(r.kod).simge"
            :metin="`${durumGorunumu(r.kod).metin}: ${r.sayi}`"
          />
        </li>
      </ul>

      <ul v-if="onizleme.uyarilar.length" class="bildirim bildirim-uyari" data-test="uyarilar">
        <li v-for="(u, i) in onizleme.uyarilar" :key="i">{{ u }}</li>
      </ul>

      <div class="arac-cubugu">
        <label class="bayrak">
          <input v-model="hepsiniGoster" type="checkbox" data-test="hepsini-goster" />
          Hepsini göster ({{ satirlar.length }} satır)
        </label>
        <Dugme boyut="sm" data-test="tumunu-sec" @click="tumunuSec">Değişenlerin hepsini seç</Dugme>
        <Dugme boyut="sm" data-test="hicbirini-secme" @click="hicbirini">Seçimi temizle</Dugme>
      </div>

      <VeriDurumu
        v-if="gorunenler.length === 0"
        durum="bos"
        metin="Bu profil mevcut ayarlarınızdan farklı bir şey önermiyor."
        ayrinti="Tüm satırları görmek için “Hepsini göster”i işaretleyin."
      />

      <div v-for="g in gruplar" :key="g.tur" class="tablo-kap">
        <table class="tablo" :data-test="`tablo-${g.tur}`">
          <caption>
            {{
              g.baslik
            }}
          </caption>
          <thead>
            <tr>
              <th scope="col">Uygula</th>
              <th scope="col">Ayar</th>
              <th scope="col">Mevcut</th>
              <th scope="col">Profilde</th>
              <th scope="col">Durum</th>
              <th scope="col">Dayanak</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="s in g.satirlar" :key="s.kimlik" :data-test="`satir-${s.kimlik}`">
              <td>
                <input
                  type="checkbox"
                  class="secim-kutusu"
                  :checked="secili.has(s.kimlik)"
                  :disabled="!secilebilir(s)"
                  :aria-label="`${s.ad} satırını uygula`"
                  @change="sec(s.kimlik, ($event.target as HTMLInputElement).checked)"
                />
              </td>
              <th scope="row" class="ad-hucre">
                {{ s.ad }}
                <span class="soluk anahtar">{{ s.anahtar }}</span>
                <span
                  v-if="s.neden"
                  class="alan-hata"
                  :role="s.durum === 'GECERSIZ' ? 'alert' : undefined"
                  >{{ s.neden }}</span
                >
              </th>
              <td class="sayisal">{{ degerMetni(s.mevcut) }}</td>
              <td class="sayisal">{{ degerMetni(s.yeni) }}</td>
              <td>
                <Rozet
                  :ton="durumGorunumu(s.durum).ton"
                  :simge="durumGorunumu(s.durum).simge"
                  :metin="durumGorunumu(s.durum).metin"
                  boyut="sm"
                />
              </td>
              <td class="dayanak-hucre">
                <template v-if="s.dayanak">{{ s.dayanak }}</template>
                <span v-else-if="s.kaynak" class="soluk">{{ s.kaynak }}</span>
                <template v-else>—</template>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <form class="uygula-formu" novalidate @submit.prevent="onayIste">
        <div class="alan alan-zorunlu">
          <label for="profil-neden">Değişiklik nedeni</label>
          <input
            id="profil-neden"
            v-model="neden"
            type="text"
            maxlength="500"
            autocomplete="off"
            required
            :aria-invalid="!!nedenHatasi"
            :aria-describedby="nedenHatasi ? 'profil-neden-hata' : undefined"
            data-test="neden"
          />
          <AlanHatasi id="profil-neden-hata" :metin="nedenHatasi" />
        </div>
        <p class="soluk" role="status" data-test="secili-sayi">{{ seciliSayisi }} satır seçili</p>
        <p v-if="secimGecersiz" class="alan-hata" role="alert">
          Seçilen satırlardan bazıları uygulanamaz; işaretlerini kaldırın.
        </p>
        <Dugme
          varyant="birincil"
          tip="submit"
          :disabled="seciliSayisi === 0 || secimGecersiz || mesgul"
          data-test="uygula"
          >Seçilenleri uygula</Dugme
        >
      </form>

      <p v-if="sonuc" class="bildirim bildirim-bilgi" role="status" data-test="sonuc">
        {{ sonuc.yazilan }} ayar yazıldı<template v-if="sonuc.atlanan > 0">
          · {{ sonuc.atlanan }} satır zaten aynıydı</template
        >. Değişiklikler hemen geçerlidir; Denetim raporunda “Kural profili uygulandı” kaydı
        görünür.
      </p>
    </section>

    <Diyalog
      v-if="onayAcik"
      baslik="Profil uygulansın mı?"
      boyut="orta"
      form
      @kapat="onayAcik = false"
      @gonder="uygula"
    >
      <p class="bildirim bildirim-uyari" role="note">{{ etiket }}</p>
      <p>
        <strong>{{ seciliSayisi }}</strong> satır uygulanacak: {{ kaynakAdi }}. Mevcut atamalar yeni
        kurallara göre yeniden değerlendirilir; kırmızı olanlar Atama masasında görünür.
      </p>
      <p>
        Neden: <em>{{ neden.trim() }}</em>
      </p>
      <template #eylemler>
        <Dugme varyant="hayalet" @click="onayAcik = false">Vazgeç</Dugme>
        <Dugme varyant="birincil" tip="submit" :yukleniyor="mesgul" data-test="onayla"
          >Uygula</Dugme
        >
      </template>
    </Diyalog>
  </section>
</template>

<style scoped>
.hazir-liste {
  display: grid;
  gap: var(--aralik-3);
  margin: 0 0 var(--aralik-5);
  padding: 0;
  list-style: none;
}
.hazir-kart {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--aralik-4);
  padding: var(--aralik-3) var(--aralik-4);
  border: 1px solid var(--renk-cizgi);
  border-radius: var(--yaricap);
  background: var(--renk-yuzey);
}
.hazir-kart p {
  margin: var(--aralik-1) 0 0;
}
.ozet-rozetleri {
  display: flex;
  flex-wrap: wrap;
  gap: var(--aralik-2);
  margin: 0 0 var(--aralik-4);
  padding: 0;
  list-style: none;
}
.secim-kutusu {
  width: 1.5rem;
  height: 1.5rem;
  margin: 0;
}
.hazir-kart .btn {
  flex: none;
  white-space: nowrap;
}
.bayrak {
  display: inline-flex;
  align-items: center;
  gap: var(--aralik-2);
}
.fark-bolumu .tablo-kap {
  margin-bottom: var(--aralik-5);
}
.tablo caption {
  padding: var(--aralik-2) 0;
  font-weight: 600;
  text-align: left;
}
.tablo tbody th.ad-hucre {
  background: transparent;
  color: var(--renk-metin);
  font-size: var(--tablo-yazi);
  font-weight: 400;
  white-space: normal;
}
.ad-hucre > span {
  display: block;
}
.anahtar {
  font-family: var(--yazi-mono);
  font-size: var(--yazi-sm);
}
.dayanak-hucre {
  max-width: 22rem;
}
.uygula-formu {
  display: grid;
  gap: var(--aralik-3);
  max-width: 40rem;
}
</style>
