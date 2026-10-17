<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { ApiHata } from '@/api/istemci'
import { makinistApi, type MakinistOzet } from '@/features/makinist/makinistApi'
import { tanimApi, type TanimDto } from '@/features/tanim/tanimApi'
import { bugun, gunKaydir, tarihBicimle, zamanBicimle } from '@/lib/tarih'
import {
  konaklamaApi,
  YER_ETIKETLERI,
  type Konaklama,
  type KonaklamaOnerisi,
  type YerTipi,
} from './konaklamaApi'

/**
 * Konaklama (F2b, MK-43): dış garda yatakhane / otel kaydı. Öneri tablosu otomatik YAZMAZ: "Kaydet" öneriyi forma doldurur. Kayıt kuralları etkilemez.
 * Saatler İstanbul saatiyle girilir (datetime-local); Türkiye 2016'dan beri UTC+3 sabittir.
 */
const bas = ref(bugun())
const bit = ref(gunKaydir(bugun(), 6))
const suzgecMakinist = ref<number | ''>('')
const makinistler = ref<MakinistOzet[]>([])
const garlar = ref<TanimDto[]>([])
const oneriler = ref<KonaklamaOnerisi[]>([])
const kayitlar = ref<Konaklama[]>([])
const hata = ref('') // liste yükleme hatası (sayfa başı)
const islemHata = ref('') // kaydet / sil hatası: formun yanında gösterilir (UX-1)
const bilgi = ref('')
const formBasligi = ref<HTMLElement | null>(null)
const bildirimAlani = ref<HTMLElement | null>(null)
const mesgul = ref(false)
const yuklendi = ref(false)

const form = reactive({
  id: 0,
  versiyon: 0,
  makinistId: 0,
  garId: 0,
  yerTipi: 'YATAKHANE' as YerTipi,
  giris: '',
  cikis: '',
  notu: '',
})
const alanHatalari = ref<Record<string, string>>({})
const duzenleniyor = computed(() => form.id > 0)

const yerelden = (s: string) => (s.length === 16 ? `${s}:00+03:00` : s)
const yerele = (z: string) => z.slice(0, 16)
const sureMetni = (dk: number) => `${Math.floor(dk / 60)} s ${dk % 60} dk`
const aralikMetni = (a: string, b: string) => `${zamanBicimle(a)} – ${zamanBicimle(b)}`

async function yukle() {
  hata.value = ''
  mesgul.value = true
  try {
    const mid = suzgecMakinist.value === '' ? undefined : suzgecMakinist.value
    ;[oneriler.value, kayitlar.value] = await Promise.all([
      konaklamaApi.oneri(bas.value, bit.value, mid),
      konaklamaApi.liste(bas.value, bit.value, mid),
    ])
  } catch (e) {
    hata.value = e instanceof ApiHata ? e.detail : 'Konaklama verisi yüklenemedi.'
  } finally {
    mesgul.value = false
    yuklendi.value = true
  }
}

onMounted(async () => {
  try {
    ;[makinistler.value, garlar.value] = await Promise.all([
      makinistApi.liste('', 'AKTIF'),
      tanimApi.liste('GAR'),
    ])
  } catch (e) {
    hata.value = e instanceof ApiHata ? e.detail : 'Listeler yüklenemedi.'
  }
  await yukle()
})

function temizle() {
  Object.assign(form, {
    id: 0,
    versiyon: 0,
    makinistId: 0,
    garId: 0,
    yerTipi: 'YATAKHANE',
    giris: '',
    cikis: '',
    notu: '',
  })
  alanHatalari.value = {}
}

/** Öneriyi forma doldurur (yazmaz): atamacı yer tipini seçip kaydeder. */
function oneriyiKullan(o: KonaklamaOnerisi) {
  temizle()
  Object.assign(form, {
    makinistId: o.makinistId,
    garId: o.garId,
    giris: yerele(o.bas),
    cikis: yerele(o.bit),
  })
  bilgi.value = `${o.adSoyad} (${o.sicil}) için öneri forma dolduruldu: yer tipini seçip kaydedin.`
  void nextTick(() => {
    // öneri tablosu ~200 satır olabilir: formu görünür kıl ve yer tipine odaklan (UX-1)
    formBasligi.value?.scrollIntoView?.({ behavior: 'smooth', block: 'start' })
    document.getElementById('kon-yer')?.focus({ preventScroll: true })
  })
}

/** Başarı / hata mesajı formun hemen üstündedir; kayıt sonrası ekranda kalsın diye görünür kılınır (UX-1). */
function bildirimeGit() {
  bildirimAlani.value?.scrollIntoView?.({ behavior: 'smooth', block: 'nearest' })
}

function duzelt(k: Konaklama) {
  Object.assign(form, {
    id: k.id,
    versiyon: k.versiyon,
    makinistId: k.makinistId,
    garId: k.garId,
    yerTipi: k.yerTipi,
    giris: yerele(k.giris),
    cikis: yerele(k.cikis),
    notu: k.notu ?? '',
  })
  alanHatalari.value = {}
  bilgi.value = ''
}

async function kaydet() {
  if (mesgul.value) return
  islemHata.value = ''
  bilgi.value = ''
  const h: Record<string, string> = {}
  if (!form.makinistId) h.makinistId = 'Makinist seçin.'
  if (!form.garId) h.garId = 'Gar seçin.'
  if (!form.giris) h.giris = 'Giriş zamanı gerekli.'
  if (!form.cikis) h.cikis = 'Çıkış zamanı gerekli.'
  else if (form.giris && form.cikis <= form.giris) h.cikis = 'Çıkış, girişten sonra olmalı.'
  alanHatalari.value = h
  if (Object.keys(h).length > 0) return
  mesgul.value = true
  try {
    const govde = {
      makinistId: form.makinistId,
      garId: form.garId,
      yerTipi: form.yerTipi,
      giris: yerelden(form.giris),
      cikis: yerelden(form.cikis),
      notu: form.notu.trim() || undefined,
    }
    if (duzenleniyor.value)
      await konaklamaApi.guncelle(form.id, { ...govde, versiyon: form.versiyon })
    else await konaklamaApi.ekle(govde)
    bilgi.value = duzenleniyor.value ? 'Kayıt güncellendi.' : 'Konaklama kaydedildi.'
    temizle()
    await yukle()
    await nextTick()
    bildirimeGit()
  } catch (e) {
    if (e instanceof ApiHata) {
      alanHatalari.value = e.alanlar ?? {}
      islemHata.value = Object.keys(alanHatalari.value).length > 0 ? '' : e.detail
    } else {
      islemHata.value = 'İşlem başarısız oldu.'
    }
    void nextTick(bildirimeGit)
  } finally {
    mesgul.value = false
  }
}

async function sil(k: Konaklama) {
  if (
    mesgul.value ||
    !window.confirm(`${k.adSoyad} (${k.sicil}) ${k.gar} konaklama kaydı silinsin mi?`)
  )
    return
  islemHata.value = ''
  mesgul.value = true
  try {
    await konaklamaApi.sil(k.id)
    bilgi.value = 'Kayıt silindi.'
    if (form.id === k.id) temizle()
    await yukle()
    await nextTick()
    bildirimeGit()
  } catch (e) {
    islemHata.value = e instanceof ApiHata ? e.detail : 'Silinemedi.'
  } finally {
    mesgul.value = false
  }
}
</script>

<template>
  <section>
    <div class="sayfa-baslik"><h1>Konaklama</h1></div>
    <p class="soluk">
      Dış garda iki nöbet arası uzun bekleyen personel için konaklama önerilir; öneriler
      kaydedilmedikçe hiçbir şey yazılmaz. Konaklama atama kurallarını etkilemez.
    </p>

    <form class="arac-cubugu" @submit.prevent="yukle">
      <div class="alan">
        <label for="kon-bas">Başlangıç</label>
        <input id="kon-bas" v-model="bas" type="date" required />
      </div>
      <div class="alan">
        <label for="kon-bit">Bitiş</label>
        <input id="kon-bit" v-model="bit" type="date" required />
      </div>
      <div class="alan">
        <label for="kon-suzgec">Personel</label>
        <select id="kon-suzgec" v-model="suzgecMakinist">
          <option value="">Hepsi</option>
          <option v-for="m in makinistler" :key="m.id" :value="m.id">
            {{ m.sicil }} · {{ m.adSoyad }}
          </option>
        </select>
      </div>
      <button type="submit" class="btn btn-birincil" :disabled="mesgul">Getir</button>
    </form>

    <p v-if="hata" class="bildirim bildirim-hata" role="alert">{{ hata }}</p>

    <h2>Öneriler ({{ oneriler.length }})</h2>
    <p v-if="yuklendi && oneriler.length === 0" class="soluk">Bu aralıkta konaklama önerisi yok.</p>
    <div v-else-if="oneriler.length > 0" class="tablo-kap">
      <table class="tablo" aria-label="Konaklama önerileri">
        <thead>
          <tr>
            <th>Personel</th>
            <th>Gar</th>
            <th>Boşluk</th>
            <th class="sayisal">Süre</th>
            <th class="sayisal">Gece</th>
            <th>Durum</th>
            <th><span class="gorunmez">İşlem</span></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(o, i) in oneriler" :key="i" data-test="oneri">
            <td>
              {{ o.adSoyad }} <small class="soluk">{{ o.sicil }}</small>
            </td>
            <td>{{ o.gar }}</td>
            <td>{{ aralikMetni(o.bas, o.bit) }}</td>
            <td class="sayisal">{{ sureMetni(o.sureDk) }}</td>
            <td class="sayisal">{{ o.gece }}</td>
            <td>
              <span v-if="o.kayitli" class="rozet" data-test="kayitli"
                ><span aria-hidden="true">✓</span> Kayıtlı</span
              >
              <span v-else class="soluk">Kayıt yok</span>
            </td>
            <td>
              <button
                v-if="!o.kayitli"
                type="button"
                class="btn btn-kucuk"
                :aria-label="`${o.adSoyad} için öneriyi forma al`"
                @click="oneriyiKullan(o)"
              >
                Kaydet
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <h2 ref="formBasligi">{{ duzenleniyor ? 'Kaydı düzelt' : 'Yeni konaklama' }}</h2>
    <div ref="bildirimAlani">
      <p v-if="islemHata" class="bildirim bildirim-hata" role="alert">{{ islemHata }}</p>
      <p v-if="bilgi" class="bildirim bildirim-bilgi" role="status">{{ bilgi }}</p>
    </div>
    <form class="kart kon-form" @submit.prevent="kaydet">
      <div class="alan">
        <label for="kon-makinist">Personel</label>
        <select id="kon-makinist" v-model="form.makinistId" :disabled="duzenleniyor" required>
          <option :value="0" disabled>Seçin…</option>
          <option v-for="m in makinistler" :key="m.id" :value="m.id">
            {{ m.sicil }} · {{ m.adSoyad }}
          </option>
        </select>
        <p v-if="alanHatalari.makinistId" class="alan-hata">{{ alanHatalari.makinistId }}</p>
      </div>
      <div class="alan">
        <label for="kon-gar">Gar</label>
        <select id="kon-gar" v-model="form.garId" required>
          <option :value="0" disabled>Seçin…</option>
          <option v-for="g in garlar" :key="g.id" :value="g.id">{{ g.ad }}</option>
        </select>
        <p v-if="alanHatalari.garId" class="alan-hata">{{ alanHatalari.garId }}</p>
      </div>
      <div class="alan">
        <label for="kon-yer">Yer tipi</label>
        <select id="kon-yer" v-model="form.yerTipi">
          <option v-for="(etiket, k) in YER_ETIKETLERI" :key="k" :value="k">{{ etiket }}</option>
        </select>
      </div>
      <div class="alan">
        <label for="kon-giris">Giriş</label>
        <input id="kon-giris" v-model="form.giris" type="datetime-local" required />
        <p v-if="alanHatalari.giris" class="alan-hata">{{ alanHatalari.giris }}</p>
      </div>
      <div class="alan">
        <label for="kon-cikis">Çıkış</label>
        <input id="kon-cikis" v-model="form.cikis" type="datetime-local" required />
        <p v-if="alanHatalari.cikis" class="alan-hata">{{ alanHatalari.cikis }}</p>
      </div>
      <div class="alan">
        <label for="kon-not">Not (isteğe bağlı)</label>
        <input id="kon-not" v-model="form.notu" type="text" maxlength="200" />
      </div>
      <div class="eylemler">
        <button type="submit" class="btn btn-birincil" :disabled="mesgul">
          {{ duzenleniyor ? 'Güncelle' : 'Kaydet' }}
        </button>
        <button v-if="duzenleniyor" type="button" class="btn" @click="temizle">Vazgeç</button>
      </div>
    </form>

    <h2>Kayıtlar ({{ kayitlar.length }})</h2>
    <p v-if="yuklendi && kayitlar.length === 0" class="soluk">Bu aralıkta konaklama kaydı yok.</p>
    <div v-else-if="kayitlar.length > 0" class="tablo-kap">
      <table class="tablo" aria-label="Konaklama kayıtları">
        <thead>
          <tr>
            <th>Personel</th>
            <th>Gar</th>
            <th>Yer</th>
            <th>Giriş – çıkış</th>
            <th class="sayisal">Gece</th>
            <th>Not</th>
            <th><span class="gorunmez">İşlem</span></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="k in kayitlar" :key="k.id" data-test="kayit">
            <td>
              {{ k.adSoyad }} <small class="soluk">{{ k.sicil }}</small>
            </td>
            <td>{{ k.gar }}</td>
            <td>{{ YER_ETIKETLERI[k.yerTipi] }}</td>
            <td>{{ aralikMetni(k.giris, k.cikis) }}</td>
            <td class="sayisal">{{ k.gece }}</td>
            <td>{{ k.notu }}</td>
            <td class="islem-dugmeleri">
              <button
                type="button"
                class="btn btn-kucuk"
                :aria-label="`${k.sicil} ${k.gar} kaydını düzelt`"
                @click="duzelt(k)"
              >
                Düzelt
              </button>
              <button
                type="button"
                class="btn btn-kucuk btn-tehlike"
                :aria-label="`${k.sicil} ${k.gar} kaydını sil`"
                @click="sil(k)"
              >
                Sil
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <p class="soluk">{{ tarihBicimle(bas) }} – {{ tarihBicimle(bit) }} aralığındaki kayıtlar.</p>
  </section>
</template>

<style scoped>
.kon-form {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(14rem, 1fr));
  gap: 0.75rem;
  margin-bottom: 1.5rem;
}
.kon-form .eylemler {
  grid-column: 1 / -1;
}
</style>
