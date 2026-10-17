<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { ApiHata } from '@/api/istemci'
import { bugun } from '@/lib/tarih'
import { benimApi, type BenimOzet } from './benimApi'
import { rozetYaz } from './rozet'

const buAy = bugun().slice(0, 7)
const ay = ref(buAy)
const veri = ref<BenimOzet | null>(null)
const yukleniyor = ref(true)
const hata = ref('')

const ayBicimleyici = new Intl.DateTimeFormat('tr-TR', {
  month: 'long',
  year: 'numeric',
  timeZone: 'UTC',
})

/** Seçilebilir aylar: sunucu sınırıyla aynı (son 12 ay .. gelecek ay), yeni ay önce. */
const aylar = computed(() => {
  const [y, m] = buAy.split('-').map(Number) as [number, number]
  const liste: { deger: string; ad: string }[] = []
  for (let k = 1; k >= -12; k--) {
    const d = new Date(Date.UTC(y, m - 1 + k, 1))
    liste.push({ deger: d.toISOString().slice(0, 7), ad: ayBicimleyici.format(d) })
  }
  return liste
})

const sayi = new Intl.NumberFormat('tr-TR', { maximumFractionDigits: 1 })

async function yukle() {
  yukleniyor.value = true
  hata.value = ''
  try {
    veri.value = await benimApi.ozet(ay.value)
    rozetYaz(`${ay.value} özeti yüklendi`)
  } catch (e) {
    hata.value = e instanceof ApiHata ? e.detail : 'Özet yüklenemedi.'
  } finally {
    yukleniyor.value = false
  }
}
onMounted(yukle)

const fark = computed(() => {
  const f = veri.value?.farkYuzde
  if (f === null || f === undefined) return 'Ortalama yok'
  return `${f > 0 ? '+' : ''}${sayi.format(f)} %`
})
</script>

<template>
  <section>
    <h1>Aylık özetim</h1>
    <div class="alan">
      <label for="ozet-ay">Ay</label>
      <select id="ozet-ay" v-model="ay" @change="yukle">
        <option v-for="a in aylar" :key="a.deger" :value="a.deger">{{ a.ad }}</option>
      </select>
    </div>
    <p v-if="yukleniyor" role="status">Yükleniyor…</p>
    <div v-else-if="hata" class="bildirim bildirim-hata" role="alert">
      <p>{{ hata }}</p>
      <button type="button" class="btn" @click="yukle">Yeniden dene</button>
    </div>
    <template v-else-if="veri">
      <p v-if="!veri.adaleteDahil" class="bildirim bildirim-bilgi">
        Bu ay özet dışındasınız (hesabınız aktif değil).
      </p>
      <dl class="benim-ozet-izgara">
        <div>
          <dt>Sefer</dt>
          <dd>
            {{ veri.seferSayisi }}
            <small v-if="veri.yolculukSayisi" class="soluk" data-test="yolculuk-sayisi"
              >(+{{ veri.yolculukSayisi }} yolculuk)</small
            >
          </dd>
        </div>
        <div>
          <dt>Görev saati</dt>
          <dd>{{ sayi.format(veri.gorevSaat) }}</dd>
        </div>
        <div>
          <dt>Km</dt>
          <dd>{{ sayi.format(veri.km) }}</dd>
        </div>
        <div>
          <dt>Gece saati</dt>
          <dd>
            {{ sayi.format(veri.geceSaat) }}
            <small class="soluk">Görevlerinizin gece vardiyası saatlerine denk gelen kısmı.</small>
          </dd>
        </div>
        <div>
          <dt>İzin günü</dt>
          <dd>{{ veri.izinGunu }}</dd>
        </div>
        <div>
          <dt>Ortalamaya fark</dt>
          <dd>
            {{ fark }}
            <small class="soluk"
              >Görev saatinizin aynı türdeki personelin ortalamasına göre farkı.</small
            >
          </dd>
        </div>
      </dl>
      <p class="soluk">
        Planlanan seferler dahildir (gerçekleşen değil).
        <template v-if="veri.yolculukSayisi">
          Yolcu olarak gittiğiniz {{ veri.yolculukSayisi }} yolculuk sefer sayısına girmez; süresi
          görev saatinize dahildir, km toplamına eklenmez.
        </template>
        <template v-if="veri.ortalamaSaat > 0">
          Ortalama görev saati: {{ sayi.format(veri.ortalamaSaat) }}.
        </template>
        <template v-if="veri.kmBilinmeyenSefer > 0">
          {{ veri.kmBilinmeyenSefer }} seferin km bilgisi girilmediği için km toplamına dahil
          değildir.
        </template>
      </p>
    </template>
    <p style="margin-top: 1.5rem">
      <RouterLink class="btn" to="/benim/parola">Parolamı değiştir</RouterLink>
    </p>
  </section>
</template>
