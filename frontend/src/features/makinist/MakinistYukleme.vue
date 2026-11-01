<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { ApiHata } from '@/api/istemci'
import AlanHatasi from '@/bilesenler/AlanHatasi.vue'
import Rozet from '@/bilesenler/Rozet.vue'
import SayfaBasligi from '@/bilesenler/SayfaBasligi.vue'
import VeriDurumu from '@/bilesenler/VeriDurumu.vue'
import { makinistApi, TUR_ETIKETLERI, type MakinistYuklemeSonucu } from './makinistApi'

/** Sunucu sınırıyla aynı (multipart max-file-size). Büyük dosya hiç gönderilmez. */
const EN_COK_MB = 5
const TANIM_ETIKETLERI = { GAR: 'Gar', HAT: 'Hat', ARAC_TIPI: 'Araç tipi' } as const

const dosya = ref<File | null>(null)
const sonuc = ref<MakinistYuklemeSonucu | null>(null)
/** Dosya seçimi / okuma / yükleme hatası: dosya alanının altında gösterilir. */
const hata = ref('')
const sablonHata = ref('')
const mesgul = ref(false)

const yeniVar = computed(() => (sonuc.value?.eklenen ?? 0) > 0)
const eksikTanimlar = computed(() =>
  (sonuc.value?.eksikTanimlar ?? []).map((e) => `${TANIM_ETIKETLERI[e.tip]}: ${e.ad}`),
)

function hataYaz(e: unknown, varsayilan: string) {
  hata.value = e instanceof ApiHata ? e.detail : varsayilan
}

async function sablonIndir() {
  sablonHata.value = ''
  try {
    const { blob, ad } = await makinistApi.iceAktarSablon()
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = ad ?? 'makinist-sablonu.xlsx'
    a.click()
    URL.revokeObjectURL(url)
  } catch (e) {
    sablonHata.value = e instanceof ApiHata ? e.detail : 'Şablon indirilemedi.'
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
    sonuc.value = await makinistApi.iceAktar(secilen, true)
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
    sonuc.value = await makinistApi.iceAktar(dosya.value, false)
  } catch (e) {
    hataYaz(e, 'Yükleme başarısız oldu.')
  } finally {
    mesgul.value = false
  }
}
</script>

<template>
  <section>
    <SayfaBasligi baslik="Personel yükleme">
      <template #eylemler>
        <RouterLink class="btn" :to="{ name: 'makinistler' }">Personel listesine dön</RouterLink>
      </template>
    </SayfaBasligi>
    <p class="soluk">
      Şablonda üç sayfa vardır: Makinistler, Araç Tipi Bröveleri ve Hat Bröveleri. Gar, hat ve araç
      tipi adları Tanımlar'daki gibi yazılmalıdır. Sicili sistemde zaten olan personel güncellenmez,
      atlanır.
    </p>
    <div class="arac-cubugu">
      <button type="button" class="btn" @click="sablonIndir">Şablonu indir</button>
      <div class="alan alan-zorunlu">
        <label for="dosya">Excel dosyası (.xlsx)</label>
        <input
          id="dosya"
          type="file"
          accept=".xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
          :aria-invalid="!!hata"
          :aria-describedby="hata ? 'dosya-yardim dosya-hata' : 'dosya-yardim'"
          @change="dosyaSecildi"
        />
        <p id="dosya-yardim" class="alan-yardim">
          En çok {{ EN_COK_MB }} MB. Seçilen dosya önce denetlenir, onaylamadan hiçbir kayıt
          yazılmaz.
        </p>
        <AlanHatasi id="dosya-hata" :metin="hata" />
      </div>
    </div>
    <p v-if="sablonHata" class="bildirim bildirim-hata" role="alert">{{ sablonHata }}</p>
    <VeriDurumu v-if="mesgul && !sonuc" durum="yukleniyor" metin="Dosya okunuyor…" />

    <template v-if="sonuc">
      <p class="bildirim bildirim-bilgi" role="status">
        <template v-if="sonuc.onizleme">
          Toplam satır: {{ sonuc.toplamSatir }} · Eklenecek: {{ sonuc.eklenen }} · Atlanacak (zaten
          var): {{ sonuc.zatenVar }} · Hatalı: {{ sonuc.hataSayisi }} · Uyarı:
          {{ sonuc.uyariSayisi }}
        </template>
        <template v-else>
          Yükleme tamamlandı. Eklenen: {{ sonuc.eklenen }} · Atlanan (zaten var):
          {{ sonuc.zatenVar }} · Hatalı: {{ sonuc.hataSayisi }}
        </template>
      </p>

      <div
        v-if="eksikTanimlar.length"
        class="bildirim bildirim-uyari"
        role="note"
        data-test="eksik-tanimlar"
      >
        <p>
          Dosyada geçen şu adlar Tanımlar'da yok (ürün tanım üretmez):
          {{ eksikTanimlar.join('; ') }}. Önce Tanımlar sayfasında ekleyin, sonra dosyayı yeniden
          seçin.
        </p>
        <RouterLink class="btn" :to="{ name: 'tanimlar' }">Tanımlar'a git</RouterLink>
      </div>

      <div v-if="sonuc.hatalar.length" class="tablo-kap hata-tablosu">
        <table class="tablo" aria-label="Hatalı satırlar">
          <thead>
            <tr>
              <th>Sayfa</th>
              <th class="sayisal">Satır no</th>
              <th>Sicil</th>
              <th>Neden</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(h, i) in sonuc.hatalar" :key="i" class="gecikti">
              <td>{{ h.sayfa }}</td>
              <td class="sayisal">{{ h.satirNo }}</td>
              <td>{{ h.sicil ?? '' }}</td>
              <td>{{ h.neden }}</td>
            </tr>
          </tbody>
        </table>
      </div>

      <div v-if="sonuc.satirlar.length" class="tablo-kap">
        <table class="tablo" aria-label="Geçerli satırlar">
          <thead>
            <tr>
              <th class="sayisal">Satır no</th>
              <th>Sicil</th>
              <th>Ad soyad</th>
              <th>Tür</th>
              <th>Bağlı gar</th>
              <th class="sayisal">Araç tipi bröve</th>
              <th class="sayisal">Hat bröve</th>
              <th>Sonuç</th>
              <th>Uyarı</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="s in sonuc.satirlar" :key="s.satirNo">
              <td class="sayisal">{{ s.satirNo }}</td>
              <td>{{ s.sicil }}</td>
              <td>{{ s.adSoyad }}</td>
              <td>{{ TUR_ETIKETLERI[s.tur] }}</td>
              <td>{{ s.bagliGar }}</td>
              <td class="sayisal">{{ s.aracTipiSayisi }}</td>
              <td class="sayisal">{{ s.hatSayisi }}</td>
              <td>
                <Rozet v-if="s.durum === 'YENI'" ton="basari" simge="ekle" metin="Yeni" />
                <Rozet v-else ton="notr" metin="Atlandı: zaten var" />
              </td>
              <td>
                <ul v-if="s.uyarilar.length" class="uyari-liste">
                  <li v-for="(u, i) in s.uyarilar" :key="i">{{ u }}</li>
                </ul>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div v-if="sonuc.onizleme" class="eylemler">
        <button
          type="button"
          class="btn btn-birincil"
          :disabled="mesgul || !yeniVar"
          :aria-busy="mesgul ? 'true' : undefined"
          @click="onayla"
        >
          Onayla ve yükle ({{ sonuc.eklenen }} personel)
        </button>
        <small
          >Hatalı satırlar atlanır, geçerli satırlar yüklenir; atlanan satırı sonradan aynı dosyayla
          eklemek için dosyayı onaylamadan önce düzeltin. Zaten var olan personelin bröveleri
          güncellenmez.</small
        >
      </div>
      <div v-else class="eylemler">
        <RouterLink class="btn btn-birincil" :to="{ name: 'makinistler' }"
          >Personel listesine git</RouterLink
        >
      </div>
    </template>
  </section>
</template>

<style scoped>
.hata-tablosu {
  margin-bottom: var(--aralik-5);
}
.uyari-liste {
  margin: 0;
  padding-left: var(--aralik-4);
}
</style>
