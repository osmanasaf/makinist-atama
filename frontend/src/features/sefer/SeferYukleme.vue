<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { ApiHata } from '@/api/istemci'
import { tarihBicimle, zamanBicimle } from '@/lib/tarih'
import { trenAdi } from '@/lib/trenAdi'
import { seferApi, type YuklemeSonucu } from './seferApi'

/** Sunucu sınırıyla aynı (multipart max-file-size). Büyük dosya hiç gönderilmez: sunucu yanıtı erken verip bağlantıyı kapatınca tarayıcı/vekil bağlantıyı sıfırlayıp "Sunucuya ulaşılamadı" gösteriyordu. */
const EN_COK_MB = 5

const dosya = ref<File | null>(null)
const sonuc = ref<YuklemeSonucu | null>(null)
const hata = ref('')
const mesgul = ref(false)

const yeniVar = computed(() => sonuc.value?.satirlar.some((s) => s.durum === 'YENI') ?? false)
/** Parçalı tren (F2c): ilk parçaların sayısı = tren sayısı; parçasız yüklemede 0 (özet metni değişmez). */
const parcaSayisi = computed(
  () => sonuc.value?.satirlar.filter((s) => s.parcaNo != null).length ?? 0,
)
const parcaliTren = computed(() => sonuc.value?.satirlar.filter((s) => s.parcaNo === 1).length ?? 0)

function hataYaz(e: unknown, varsayilan: string) {
  hata.value = e instanceof ApiHata ? e.detail : varsayilan
}

async function sablonIndir() {
  hata.value = ''
  try {
    const { blob, ad } = await seferApi.sablon()
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = ad ?? 'sefer-sablon.xlsx'
    a.click()
    URL.revokeObjectURL(url)
  } catch (e) {
    hataYaz(e, 'Şablon indirilemedi.')
  }
}

async function dosyaSecildi(olay: Event) {
  const secilen = (olay.target as HTMLInputElement).files?.[0] ?? null
  dosya.value = secilen
  sonuc.value = null
  hata.value = ''
  if (!secilen) return
  if (secilen.size > EN_COK_MB * 1024 * 1024) {
    dosya.value = null
    hata.value = `Dosya çok büyük. En çok ${EN_COK_MB} MB yüklenebilir.`
    return
  }
  mesgul.value = true
  try {
    sonuc.value = await seferApi.yukle(secilen, true)
  } catch (e) {
    hataYaz(e, 'Dosya okunamadı.')
  } finally {
    mesgul.value = false
  }
}

async function onayla() {
  if (!dosya.value || mesgul.value) return
  mesgul.value = true
  hata.value = ''
  try {
    sonuc.value = await seferApi.yukle(dosya.value, false)
  } catch (e) {
    hataYaz(e, 'Yükleme başarısız oldu.')
  } finally {
    mesgul.value = false
  }
}
</script>

<template>
  <section>
    <div class="sayfa-baslik">
      <h1>Sefer yükleme</h1>
      <RouterLink class="btn" :to="{ name: 'seferler' }">Seferlere dön</RouterLink>
    </div>
    <div class="arac-cubugu">
      <button type="button" class="btn" @click="sablonIndir">Şablonu indir</button>
      <div class="alan">
        <label for="dosya">Excel dosyası (.xlsx)</label>
        <input
          id="dosya"
          type="file"
          accept=".xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
          @change="dosyaSecildi"
        />
      </div>
    </div>
    <p v-if="hata" class="bildirim bildirim-hata" role="alert">{{ hata }}</p>

    <template v-if="sonuc">
      <p class="bildirim bildirim-bilgi" role="status">
        <template v-if="sonuc.onizleme">
          Toplam satır: {{ sonuc.toplamSatir
          }}<template v-if="!sonuc.engelli"> · Eklenecek: {{ sonuc.eklenen }}</template> · Zaten
          var: {{ sonuc.zatenVar }} · Hatalı: {{ sonuc.hataSayisi }}
          <template v-if="parcaliTren > 0">
            · Parçalı tren: {{ parcaliTren }} ({{ parcaSayisi }} parça)</template
          >
        </template>
        <template v-else>
          Yükleme tamamlandı. Eklenen: {{ sonuc.eklenen }} · Zaten var: {{ sonuc.zatenVar }} ·
          Hatalı: {{ sonuc.hataSayisi }}
          <template v-if="parcaliTren > 0">
            · Parçalı tren: {{ parcaliTren }} ({{ parcaSayisi }} parça)</template
          >
        </template>
      </p>

      <p v-if="sonuc.engelli" class="bildirim bildirim-hata" role="alert" data-test="engelli">
        Dosya onaylanamaz: aşağıdaki engelleyici hatayı düzeltip dosyayı yeniden seçin (Seferler
        sayfasında karşılığı olmayan değişim noktası, çok uzun parça ya da yalnız harf farkıyla
        ayrışan tren no'lar olabilir). Hiçbir sefer yazılmaz.
      </p>

      <div v-if="sonuc.hatalar.length" class="tablo-kap" style="margin-bottom: 1rem">
        <table class="tablo" aria-label="Hatalı satırlar">
          <thead>
            <tr>
              <th>Sayfa</th>
              <th>Satır no</th>
              <th>Tren no</th>
              <th>Neden</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(h, i) in sonuc.hatalar" :key="i" class="gecikti">
              <td>{{ h.sayfa ?? 'Seferler' }}</td>
              <td>{{ h.satirNo }}</td>
              <td>{{ h.trenNo ?? '' }}</td>
              <td>{{ h.neden }}</td>
            </tr>
          </tbody>
        </table>
      </div>

      <div v-if="sonuc.satirlar.length" class="tablo-kap">
        <table class="tablo" aria-label="Geçerli satırlar">
          <thead>
            <tr>
              <th>Satır no</th>
              <th>Tren no</th>
              <th>Tarih</th>
              <th>Kalkış gar</th>
              <th>Varış gar</th>
              <th>Kalkış</th>
              <th>Varış</th>
              <th>Araç tipi</th>
              <th>Hat</th>
              <th>Durum</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="s in sonuc.satirlar" :key="`${s.satirNo}-${s.parcaNo ?? 0}`">
              <td>{{ s.satirNo }}</td>
              <td>{{ trenAdi(s) }}</td>
              <td>{{ tarihBicimle(s.tarih) }}</td>
              <td>{{ s.kalkisGar }}</td>
              <td>{{ s.varisGar }}</td>
              <td>{{ zamanBicimle(s.kalkis) }}</td>
              <td>{{ zamanBicimle(s.varis) }}</td>
              <td>{{ s.aracTipi }}</td>
              <td>{{ s.hat }}</td>
              <td>{{ s.durum === 'YENI' ? 'Yeni' : 'Zaten var' }}</td>
            </tr>
          </tbody>
        </table>
      </div>

      <div v-if="sonuc.onizleme" class="eylemler">
        <button
          type="button"
          class="btn btn-birincil"
          :disabled="mesgul || !yeniVar || sonuc.engelli"
          @click="onayla"
        >
          Onayla ve yükle
        </button>
        <small v-if="sonuc.hataSayisi && !sonuc.engelli"
          >Hatalı satırlar atlanır, geçerli satırlar yüklenir.</small
        >
      </div>
    </template>
  </section>
</template>
