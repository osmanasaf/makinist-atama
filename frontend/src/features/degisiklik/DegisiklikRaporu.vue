<script setup lang="ts">
import AlanHatasi from '@/bilesenler/AlanHatasi.vue'
import { nextTick, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { ApiHata } from '@/api/istemci'
import { bugun, gunKaydir, zamanBicimle } from '@/lib/tarih'
import SayfaBasligi from '@/bilesenler/SayfaBasligi.vue'
import VeriDurumu from '@/bilesenler/VeriDurumu.vue'
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
const basHata = ref('')
const bitHata = ref('')
const form = ref<HTMLFormElement | null>(null)
const mesgul = ref(false)

async function getir() {
  if (mesgul.value) return
  hata.value = ''
  basHata.value = ''
  bitHata.value = ''
  if (!bas.value) basHata.value = 'Başlangıç tarihi gerekli.'
  else if (!bit.value) bitHata.value = 'Bitiş tarihi gerekli.'
  else if (bit.value < bas.value) bitHata.value = 'Bitiş tarihi başlangıçtan önce olamaz.'
  if (basHata.value || bitHata.value) {
    await nextTick()
    form.value?.querySelector<HTMLElement>('[aria-invalid="true"]')?.focus()
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
  <section class="rapor-sayfa">
    <SayfaBasligi baslik="Denetim raporu">
      <template v-if="rapor && uygulanan" #eylemler>
        <button type="button" class="btn" @click="excelIndir">Excel indir</button>
        <RouterLink
          class="btn"
          :to="{ name: 'yazdir-degisiklik', query: suzgecSorgusu(uygulanan) }"
          target="_blank"
          rel="noopener"
        >
          Yazdır
        </RouterLink>
      </template>
    </SayfaBasligi>

    <form ref="form" class="arac-cubugu" @submit.prevent="getir">
      <div class="alan alan-zorunlu">
        <label for="rapor-bas">Başlangıç</label>
        <input
          id="rapor-bas"
          v-model="bas"
          type="date"
          required
          :aria-invalid="basHata ? 'true' : undefined"
          :aria-describedby="basHata ? 'rapor-bas-hata' : undefined"
        />
        <AlanHatasi id="rapor-bas-hata" :metin="basHata" />
      </div>
      <div class="alan alan-zorunlu">
        <label for="rapor-bit">Bitiş</label>
        <input
          id="rapor-bit"
          v-model="bit"
          type="date"
          required
          :aria-invalid="bitHata ? 'true' : undefined"
          :aria-describedby="bitHata ? 'rapor-bit-hata' : undefined"
        />
        <AlanHatasi id="rapor-bit-hata" :metin="bitHata" />
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
        <label for="rapor-gordum" class="onay">
          <input id="rapor-gordum" v-model="gordumKayitlari" type="checkbox" />
          Yalnız “{{ GORDUM_ETIKETI }}” kayıtları
        </label>
      </div>
      <button type="submit" class="btn btn-birincil" :disabled="mesgul">Raporu getir</button>
    </form>

    <VeriDurumu v-if="mesgul" durum="yukleniyor" metin="Rapor hazırlanıyor…" />
    <VeriDurumu v-else-if="hata && !rapor" durum="hata" :metin="hata" />
    <p v-else-if="hata" class="bildirim bildirim-hata" role="alert">{{ hata }}</p>
    <VeriDurumu
      v-else-if="!rapor"
      durum="bos"
      metin="Rapor henüz alınmadı."
      ayrinti="Tarih aralığını ve süzgeçleri seçip Raporu getir'e basın."
      data-test="denetim-bos"
    />

    <template v-if="rapor && uygulanan">
      <div class="kart ozet" data-test="ozet">
        <p>
          Üretim zamanı: <strong>{{ zamanBicimle(rapor.uretimZamani) }}</strong> · Üreten:
          <strong>{{ rapor.ureten }}</strong>
        </p>
        <p>{{ suzgecOzeti(rapor.filtre) }} · Kayıt sayısı: {{ rapor.kayitSayisi }}</p>
      </div>
      <DegisiklikBolumleri :rapor="rapor" />
    </template>
  </section>
</template>

<style scoped>
.ozet p {
  margin: 0 0 var(--aralik-2);
}
.onay {
  display: flex;
  align-items: center;
  gap: var(--aralik-3);
  min-height: var(--kontrol-yukseklik);
  font-weight: var(--yazi-agirlik-normal);
}
</style>
