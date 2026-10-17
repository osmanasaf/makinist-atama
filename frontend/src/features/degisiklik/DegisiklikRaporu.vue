<script setup lang="ts">
import { ref } from 'vue'
import { RouterLink } from 'vue-router'
import { ApiHata } from '@/api/istemci'
import { bugun, gunKaydir, zamanBicimle } from '@/lib/tarih'
import DegisiklikBolumleri from './DegisiklikBolumleri.vue'
import {
  degisiklikApi,
  suzgecOzeti,
  suzgecSorgusu,
  GORDUM_ETIKETI,
  TUR_ETIKETLERI,
  type DegisiklikSuzgeci,
  type DegisiklikYaniti,
  type ListelenenTur,
} from './degisiklikApi'

const bas = ref(gunKaydir(bugun(), -6)) // bugün dahil son 7 gün
const bit = ref(bugun())
const trenNo = ref('')
const kullanici = ref('')
const tur = ref<ListelenenTur | ''>('')
/** "Gördüm" kayıtları varsayılan raporda gizlidir (günde yüzlerce); işaretlenince yalnız onlar istenir (tur=GORDUM). */
const gordumKayitlari = ref(false)

const rapor = ref<DegisiklikYaniti | null>(null)
/** Görünen raporun süzgeci: form sonradan değişse de Excel/Yazdır bununla çalışır. */
const uygulanan = ref<DegisiklikSuzgeci | null>(null)
const hata = ref('')
const mesgul = ref(false)

async function getir() {
  if (mesgul.value) return
  hata.value = ''
  if (!bas.value || !bit.value) {
    hata.value = 'Başlangıç ve bitiş tarihi gerekli.'
    return
  }
  if (bit.value < bas.value) {
    hata.value = 'Bitiş tarihi başlangıçtan önce olamaz.'
    return
  }
  const suzgec: DegisiklikSuzgeci = {
    bas: bas.value,
    bit: bit.value,
    trenNo: trenNo.value,
    kullanici: kullanici.value,
    tur: gordumKayitlari.value ? 'GORDUM' : tur.value,
  }
  mesgul.value = true
  rapor.value = null
  try {
    rapor.value = await degisiklikApi.rapor(suzgec)
    uygulanan.value = suzgec
  } catch (e) {
    hata.value = e instanceof ApiHata ? e.detail : 'Rapor alınamadı.'
  } finally {
    mesgul.value = false
  }
}

async function excelIndir() {
  if (!uygulanan.value) return
  hata.value = ''
  try {
    const { blob, ad } = await degisiklikApi.excel(uygulanan.value)
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = ad ?? 'denetim-raporu.xlsx'
    a.click()
    URL.revokeObjectURL(url)
  } catch (e) {
    hata.value = e instanceof ApiHata ? e.detail : 'Excel indirilemedi.'
  }
}
</script>

<template>
  <section>
    <div class="sayfa-baslik">
      <h1>Denetim raporu</h1>
    </div>

    <form class="arac-cubugu" @submit.prevent="getir">
      <div class="alan">
        <label for="rapor-bas">Başlangıç</label>
        <input id="rapor-bas" v-model="bas" type="date" required />
      </div>
      <div class="alan">
        <label for="rapor-bit">Bitiş</label>
        <input id="rapor-bit" v-model="bit" type="date" required />
      </div>
      <div class="alan">
        <label for="rapor-tren">Tren no</label>
        <input id="rapor-tren" v-model="trenNo" type="text" />
      </div>
      <div class="alan">
        <label for="rapor-kullanici">Kullanıcı</label>
        <input id="rapor-kullanici" v-model="kullanici" type="text" />
      </div>
      <div class="alan">
        <label for="rapor-tur">Tür</label>
        <select id="rapor-tur" v-model="tur" :disabled="gordumKayitlari">
          <option value="">Tümü</option>
          <option v-for="(etiket, k) in TUR_ETIKETLERI" :key="k" :value="k">{{ etiket }}</option>
        </select>
      </div>
      <div class="alan">
        <label for="rapor-gordum">
          <input id="rapor-gordum" v-model="gordumKayitlari" type="checkbox" />
          Yalnız “{{ GORDUM_ETIKETI }}” kayıtları
        </label>
      </div>
      <button type="submit" class="btn btn-birincil" :disabled="mesgul">Raporu getir</button>
    </form>

    <p v-if="hata" class="bildirim bildirim-hata" role="alert">{{ hata }}</p>
    <p v-if="mesgul" class="soluk">Rapor hazırlanıyor…</p>

    <template v-if="rapor && uygulanan">
      <div class="kart ozet" data-test="ozet">
        <p>
          Üretim zamanı: <strong>{{ zamanBicimle(rapor.uretimZamani) }}</strong> · Üreten:
          <strong>{{ rapor.ureten }}</strong>
        </p>
        <p>{{ suzgecOzeti(rapor.filtre) }} · Kayıt sayısı: {{ rapor.kayitSayisi }}</p>
        <div class="eylemler">
          <button type="button" class="btn" @click="excelIndir">Excel indir</button>
          <RouterLink
            class="btn"
            :to="{ name: 'yazdir-degisiklik', query: suzgecSorgusu(uygulanan) }"
            target="_blank"
            rel="noopener"
          >
            Yazdır
          </RouterLink>
        </div>
      </div>
      <DegisiklikBolumleri :rapor="rapor" />
    </template>
  </section>
</template>

<style scoped>
.ozet {
  margin-bottom: 1rem;
}
.ozet p {
  margin: 0 0 0.25rem;
}
</style>
