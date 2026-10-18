<script setup lang="ts">
import AlanHatasi from '@/bilesenler/AlanHatasi.vue'
import { nextTick, onMounted, reactive, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { ApiHata } from '@/api/istemci'
import Rozet from '@/bilesenler/Rozet.vue'
import SayfaBasligi from '@/bilesenler/SayfaBasligi.vue'
import Simge from '@/bilesenler/Simge.vue'
import VeriDurumu from '@/bilesenler/VeriDurumu.vue'
import {
  IZIN_DURUM_ETIKETLERI,
  IZIN_DURUM_GORUNUMU,
  IZIN_ETIKETLERI,
  makinistApi,
  type IzinEtkisiSatiri,
  type IzinTuru,
  type MakinistOzet,
} from '@/features/makinist/makinistApi'
import { ilkHataliyaOdaklan, odakYedegi } from '@/lib/odak'
import { tarihBicimle, zamanBicimle } from '@/lib/tarih'
import { ROL_ETIKETLERI } from '@/features/atama/atamaApi'
import { raporApi, type IzinDurumu, type IzinKaydi, type YedekGunu } from './raporApi'

type Suzgec = IzinDurumu | ''
const SUZGECLER: { kod: Suzgec; ad: string }[] = [
  { kod: '', ad: 'Hepsi' },
  { kod: 'TALEP', ad: 'Talep' },
  { kod: 'ONAY', ad: 'Onaylı' },
  { kod: 'RED', ad: 'Reddedildi' },
]
const makinistler = ref<MakinistOzet[]>([])
const form = reactive({ makinistId: 0, baslangic: '', bitis: '', tur: 'YILLIK' as IzinTuru })
const alanHatalari = ref<Record<string, string>>({})
const formHata = ref('')
const bilgi = ref('')
const formKok = ref<HTMLFormElement | null>(null)

const suzgec = ref<Suzgec>('TALEP')
const liste = ref<IzinKaydi[]>([])
const yuklendi = ref(false)
const hata = ref('')
const mesgul = ref(false)

/** Son onaylanan iznin etkisi: atanmış seferler ve yedek açığı (ayrı GET'lerle). */
interface Etki {
  izin: IzinKaydi
  seferler: IzinEtkisiSatiri[] | null
  seferHata: string
  acikGunler: YedekGunu[] | null
  yedekHata: string
}
const etki = ref<Etki | null>(null)

const hataMetni = (e: unknown, yedek: string) => (e instanceof ApiHata ? e.detail : yedek)

async function listeYukle() {
  hata.value = ''
  try {
    liste.value = (await raporApi.izinListe(suzgec.value || undefined)) ?? []
  } catch (e) {
    hata.value = hataMetni(e, 'İzinler yüklenemedi.')
  } finally {
    yuklendi.value = true
  }
}

onMounted(async () => {
  await Promise.all([
    listeYukle(),
    makinistApi
      .liste('', 'AKTIF')
      .then((m) => (makinistler.value = m ?? []))
      .catch((e) => (formHata.value = hataMetni(e, 'Personel listesi yüklenemedi.'))),
  ])
})

async function suzgecSec(s: Suzgec) {
  suzgec.value = s
  await listeYukle()
}

async function talepEt() {
  if (mesgul.value) return
  alanHatalari.value = {}
  formHata.value = ''
  bilgi.value = ''
  const eksik: Record<string, string> = {}
  if (!form.makinistId) eksik.makinistId = 'Personel seçin.'
  if (!form.baslangic) eksik.baslangic = 'Başlangıç tarihi gerekli.'
  if (!form.bitis) eksik.bitis = 'Bitiş tarihi gerekli.'
  else if (form.baslangic && form.bitis < form.baslangic)
    eksik.bitis = 'Bitiş tarihi başlangıçtan önce olamaz.'
  if (Object.keys(eksik).length > 0) {
    alanHatalari.value = eksik
    await ilkHataliyaOdaklan(formKok.value)
    return
  }
  mesgul.value = true
  try {
    await raporApi.izinTalep({ ...form })
    form.baslangic = form.bitis = ''
    bilgi.value = 'İzin talebi kaydedildi.'
    await listeYukle()
  } catch (e) {
    if (e instanceof ApiHata) {
      alanHatalari.value = e.alanlar ?? {}
      formHata.value = e.detail
    } else formHata.value = 'Talep kaydedilemedi.'
    await ilkHataliyaOdaklan(formKok.value)
  } finally {
    mesgul.value = false
    // Gönder düğmesi meşgulken devre dışıydı: odak BODY'ye düşmüş olabilir (QA YENİ-1 d), düğmeye döndür.
    await nextTick()
    const a = document.activeElement
    if (!a || a === document.body)
      formKok.value?.querySelector<HTMLElement>('[type="submit"]')?.focus()
  }
}

const aylar = (i: IzinKaydi) => [...new Set([i.baslangic.slice(0, 7), i.bitis.slice(0, 7)])]

/** Onaylanan iznin etkisi: izin ayının yedek raporundan, izin günlerindeki açıklar. */
async function etkiYukle(izin: IzinKaydi) {
  etki.value = { izin, seferler: null, seferHata: '', acikGunler: null, yedekHata: '' }
  const e = etki.value // reaktif vekil: alanlar sonradan dolunca ekran güncellenir
  const [sefer, yedek] = await Promise.allSettled([
    makinistApi.izinEtkisi(izin.makinistId, izin.baslangic, izin.bitis),
    Promise.all(aylar(izin).map((a) => raporApi.yedek(a))),
  ])
  if (etki.value !== e) return // arada başka karar verildi
  if (sefer.status === 'fulfilled') e.seferler = sefer.value ?? []
  else e.seferHata = hataMetni(sefer.reason, 'Atama etkisi alınamadı.')
  if (yedek.status === 'fulfilled') {
    e.acikGunler = yedek.value
      .flatMap((r) => r.gunler)
      .filter((g) => g.tarih >= izin.baslangic && g.tarih <= izin.bitis && g.acik > 0)
  } else e.yedekHata = hataMetni(yedek.reason, 'Yedek açığı alınamadı.')
}

async function karar(izin: IzinKaydi, onay: boolean) {
  if (mesgul.value) return
  mesgul.value = true
  hata.value = ''
  try {
    const guncel = onay ? await raporApi.izinOnay(izin.id) : await raporApi.izinRed(izin.id)
    etki.value = null
    await listeYukle()
    if (onay) await etkiYukle(guncel)
  } catch (e) {
    // Karar zaten verilmişse liste bayat: önce yenile (listeYukle hatayı sıfırlar), sonra mesajı göster.
    if (e instanceof ApiHata && e.kod === 'IZIN_KARARLI') await listeYukle()
    hata.value = hataMetni(e, 'İşlem başarısız oldu.')
  } finally {
    mesgul.value = false
    await nextTick()
    odakYedegi() // karar verilen satır listeden düşmüş olabilir (QA YENİ-1)
  }
}

const saat = (z: string) => zamanBicimle(z).slice(-5)
</script>

<template>
  <section>
    <SayfaBasligi baslik="İzinler" />

    <form
      ref="formKok"
      class="kart talep-formu"
      aria-labelledby="izin-talep-baslik"
      novalidate
      @submit.prevent="talepEt"
    >
      <h2 id="izin-talep-baslik">İzin talebi</h2>
      <div class="form-izgara">
        <div class="alan alan-zorunlu">
          <label for="izin-makinist">Personel</label>
          <select
            id="izin-makinist"
            v-model.number="form.makinistId"
            required
            :aria-invalid="!!alanHatalari.makinistId"
            :aria-describedby="alanHatalari.makinistId ? 'izin-makinist-hata' : undefined"
          >
            <option :value="0" disabled>Seçin</option>
            <option v-for="m in makinistler" :key="m.id" :value="m.id">
              {{ m.sicil }} — {{ m.adSoyad }}
            </option>
          </select>
          <AlanHatasi id="izin-makinist-hata" :metin="alanHatalari.makinistId" />
        </div>
        <div class="alan alan-zorunlu">
          <label for="izin-bas">Başlangıç</label>
          <input
            id="izin-bas"
            v-model="form.baslangic"
            type="date"
            required
            :aria-invalid="!!alanHatalari.baslangic"
            :aria-describedby="alanHatalari.baslangic ? 'izin-bas-hata' : undefined"
          />
          <AlanHatasi id="izin-bas-hata" :metin="alanHatalari.baslangic" />
        </div>
        <div class="alan alan-zorunlu">
          <label for="izin-bit">Bitiş</label>
          <input
            id="izin-bit"
            v-model="form.bitis"
            type="date"
            required
            :aria-invalid="!!alanHatalari.bitis"
            :aria-describedby="alanHatalari.bitis ? 'izin-bit-hata' : undefined"
          />
          <AlanHatasi id="izin-bit-hata" :metin="alanHatalari.bitis" />
        </div>
        <div class="alan alan-zorunlu">
          <label for="izin-tur">Tür</label>
          <select
            id="izin-tur"
            v-model="form.tur"
            :aria-invalid="!!alanHatalari.tur"
            :aria-describedby="alanHatalari.tur ? 'izin-tur-hata' : undefined"
          >
            <option v-for="(ad, k) in IZIN_ETIKETLERI" :key="k" :value="k">{{ ad }}</option>
          </select>
          <AlanHatasi id="izin-tur-hata" :metin="alanHatalari.tur" />
        </div>
      </div>
      <p v-if="formHata" class="bildirim bildirim-hata" role="alert">{{ formHata }}</p>
      <div role="status">
        <p v-if="bilgi" class="bildirim bildirim-bilgi" data-test="izin-bilgi">{{ bilgi }}</p>
      </div>
      <div class="eylemler">
        <button type="submit" class="btn btn-birincil" :disabled="mesgul">Talep et</button>
      </div>
    </form>

    <div class="arac-cubugu liste-ust">
      <div role="group" aria-label="Durum süzgeci" class="suzgec">
        <button
          v-for="s in SUZGECLER"
          :key="s.kod"
          type="button"
          class="btn"
          :aria-pressed="suzgec === s.kod"
          @click="suzgecSec(s.kod)"
        >
          {{ s.ad }}
        </button>
      </div>
    </div>

    <p v-if="hata" class="bildirim bildirim-hata" role="alert" data-test="liste-hata">
      {{ hata }}
    </p>

    <div v-if="etki" class="bildirim bildirim-uyari etki-paneli" data-test="etki" role="status">
      <div>
        <p>
          <strong
            >{{ etki.izin.sicil }} — {{ etki.izin.adSoyad }}:
            {{ tarihBicimle(etki.izin.baslangic) }} – {{ tarihBicimle(etki.izin.bitis) }} izni
            onaylandı.</strong
          >
        </p>

        <p v-if="etki.seferHata" class="bildirim bildirim-hata">{{ etki.seferHata }}</p>
        <template v-else-if="etki.seferler && etki.seferler.length > 0">
          <p data-test="sefer-etkisi">
            <Simge ad="uyari" boyut="1em" /> Bu izin {{ etki.seferler.length }} atanmış seferi
            kırmızı yaptı → Atama Masası'ndan düzeltin
          </p>
          <ul>
            <li v-for="s in etki.seferler" :key="s.atamaId ?? `i${s.seferId}`">
              <RouterLink :to="{ name: 'atama', query: { tarih: s.tarih } }">
                {{ tarihBicimle(s.tarih) }} · {{ s.trenNo }}
              </RouterLink>
              · {{ s.kalkisGar }} → {{ s.varisGar }} · {{ saat(s.kalkis) }}–{{ saat(s.varis) }} ·
              {{ ROL_ETIKETLERI[s.rol] }}
            </li>
          </ul>
        </template>
        <p v-else-if="etki.seferler" data-test="sefer-yok">
          <Simge ad="basari" boyut="1em" /> İzin aralığında atanmış sefer yok.
        </p>

        <p v-if="etki.yedekHata" class="bildirim bildirim-hata">{{ etki.yedekHata }}</p>
        <template v-else-if="etki.acikGunler && etki.acikGunler.length > 0">
          <p data-test="yedek-acigi"><Simge ad="uyari" boyut="1em" /> Yedek açığı olan günler:</p>
          <ul>
            <li v-for="g in etki.acikGunler" :key="g.tarih">
              {{ tarihBicimle(g.tarih) }} — {{ g.acik }} eksik
            </li>
          </ul>
        </template>
        <p v-else-if="etki.acikGunler" data-test="yedek-yok">Yedek açığı yok.</p>
      </div>
    </div>

    <VeriDurumu v-if="!yuklendi" durum="yukleniyor" metin="Yükleniyor…" />
    <div class="tablo-kap">
      <table class="tablo" aria-label="İzin listesi">
        <thead>
          <tr>
            <th>Sicil</th>
            <th>Ad soyad</th>
            <th>Başlangıç</th>
            <th>Bitiş</th>
            <th>Tür</th>
            <th>Durum</th>
            <th>Talep eden</th>
            <th>Karar veren</th>
            <th><span class="gorunmez">İşlemler</span></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="i in liste" :key="i.id">
            <td>{{ i.sicil }}</td>
            <td>{{ i.adSoyad }}</td>
            <td>{{ tarihBicimle(i.baslangic) }}</td>
            <td>{{ tarihBicimle(i.bitis) }}</td>
            <td>{{ IZIN_ETIKETLERI[i.tur] }}</td>
            <td>
              <Rozet
                :ton="IZIN_DURUM_GORUNUMU[i.durum]?.ton ?? 'notr'"
                :simge="IZIN_DURUM_GORUNUMU[i.durum]?.simge"
                :metin="IZIN_DURUM_ETIKETLERI[i.durum] ?? i.durum"
              />
            </td>
            <td>{{ i.olusturan }}</td>
            <td>{{ i.kararVeren ?? '—' }}</td>
            <td>
              <div v-if="i.durum === 'TALEP'" class="eylemler satir-eylem">
                <button
                  type="button"
                  class="btn btn-birincil"
                  :disabled="mesgul"
                  :aria-label="`${i.sicil} iznini onayla`"
                  @click="karar(i, true)"
                >
                  Onayla
                </button>
                <button
                  type="button"
                  class="btn"
                  :disabled="mesgul"
                  :aria-label="`${i.sicil} iznini reddet`"
                  @click="karar(i, false)"
                >
                  Reddet
                </button>
              </div>
            </td>
          </tr>
          <tr v-if="yuklendi && liste.length === 0">
            <td colspan="9"><VeriDurumu durum="bos" metin="İzin kaydı yok." /></td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>

<style scoped>
.talep-formu {
  margin-bottom: var(--aralik-5);
}
.talep-formu h2 {
  margin-top: 0;
  font-size: var(--yazi-xl);
}
.etki-paneli {
  margin-bottom: var(--aralik-5);
}
.satir-eylem {
  margin-top: 0;
}
</style>
