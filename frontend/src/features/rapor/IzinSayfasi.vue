<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { ApiHata } from '@/api/istemci'
import {
  IZIN_DURUM_ETIKETLERI,
  IZIN_ETIKETLERI,
  makinistApi,
  type IzinEtkisiSatiri,
  type IzinTuru,
  type MakinistOzet,
} from '@/features/makinist/makinistApi'
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
const DURUM_SIMGELERI: Record<IzinDurumu, string> = { TALEP: '⏳', ONAY: '✔', RED: '✖' }

const makinistler = ref<MakinistOzet[]>([])
const form = reactive({ makinistId: 0, baslangic: '', bitis: '', tur: 'YILLIK' as IzinTuru })
const alanHatalari = ref<Record<string, string>>({})
const formHata = ref('')

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
  if (!form.makinistId || !form.baslangic || !form.bitis) {
    formHata.value = 'Makinist, başlangıç ve bitiş tarihi gerekli.'
    return
  }
  if (form.bitis < form.baslangic) {
    alanHatalari.value = { bitis: 'Bitiş tarihi başlangıçtan önce olamaz.' }
    return
  }
  mesgul.value = true
  try {
    await raporApi.izinTalep({ ...form })
    form.baslangic = form.bitis = ''
    await listeYukle()
  } catch (e) {
    if (e instanceof ApiHata) {
      alanHatalari.value = e.alanlar ?? {}
      formHata.value = e.detail
    } else formHata.value = 'Talep kaydedilemedi.'
  } finally {
    mesgul.value = false
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
  }
}

const saat = (z: string) => zamanBicimle(z).slice(-5)
</script>

<template>
  <section>
    <div class="sayfa-baslik"><h1>İzinler</h1></div>

    <form class="kart talep-formu" aria-labelledby="izin-talep-baslik" @submit.prevent="talepEt">
      <h2 id="izin-talep-baslik">İzin talebi</h2>
      <div class="form-izgara">
        <div class="alan">
          <label for="izin-makinist">Personel</label>
          <select id="izin-makinist" v-model.number="form.makinistId" required>
            <option :value="0" disabled>Seçin</option>
            <option v-for="m in makinistler" :key="m.id" :value="m.id">
              {{ m.sicil }} — {{ m.adSoyad }}
            </option>
          </select>
          <small v-if="alanHatalari.makinistId" class="alan-hata">{{
            alanHatalari.makinistId
          }}</small>
        </div>
        <div class="alan">
          <label for="izin-bas">Başlangıç</label>
          <input id="izin-bas" v-model="form.baslangic" type="date" required />
          <small v-if="alanHatalari.baslangic" class="alan-hata">{{
            alanHatalari.baslangic
          }}</small>
        </div>
        <div class="alan">
          <label for="izin-bit">Bitiş</label>
          <input id="izin-bit" v-model="form.bitis" type="date" required />
          <small v-if="alanHatalari.bitis" class="alan-hata">{{ alanHatalari.bitis }}</small>
        </div>
        <div class="alan">
          <label for="izin-tur">Tür</label>
          <select id="izin-tur" v-model="form.tur">
            <option v-for="(ad, k) in IZIN_ETIKETLERI" :key="k" :value="k">{{ ad }}</option>
          </select>
          <small v-if="alanHatalari.tur" class="alan-hata">{{ alanHatalari.tur }}</small>
        </div>
      </div>
      <p v-if="formHata" class="bildirim bildirim-hata" role="alert">{{ formHata }}</p>
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
          :class="{ 'btn-birincil': suzgec === s.kod }"
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
            <span aria-hidden="true">⚠</span> Bu izin {{ etki.seferler.length }} atanmış seferi
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
          <span aria-hidden="true">✔</span> İzin aralığında atanmış sefer yok.
        </p>

        <p v-if="etki.yedekHata" class="bildirim bildirim-hata">{{ etki.yedekHata }}</p>
        <template v-else-if="etki.acikGunler && etki.acikGunler.length > 0">
          <p data-test="yedek-acigi"><span aria-hidden="true">⚠</span> Yedek açığı olan günler:</p>
          <ul>
            <li v-for="g in etki.acikGunler" :key="g.tarih">
              {{ tarihBicimle(g.tarih) }} — {{ g.acik }} eksik
            </li>
          </ul>
        </template>
        <p v-else-if="etki.acikGunler" data-test="yedek-yok">Yedek açığı yok.</p>
      </div>
    </div>

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
            <th><span class="sr-only">İşlemler</span></th>
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
              <span aria-hidden="true">{{ DURUM_SIMGELERI[i.durum] }}</span>
              {{ IZIN_DURUM_ETIKETLERI[i.durum] ?? i.durum }}
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
            <td colspan="9" class="bos-durum">İzin kaydı yok.</td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>

<style scoped>
.talep-formu {
  margin-bottom: 1rem;
}
.talep-formu h2 {
  margin-top: 0;
  font-size: 1.1rem;
}
.suzgec {
  display: flex;
  flex-wrap: wrap;
  gap: 0.25rem;
}
.etki-paneli {
  margin-bottom: 1rem;
}
.satir-eylem {
  margin-top: 0;
}
.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
}
</style>
