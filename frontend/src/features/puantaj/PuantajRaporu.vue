<script setup lang="ts">
import AlanHatasi from '@/bilesenler/AlanHatasi.vue'
import { computed, nextTick, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { ApiHata } from '@/api/istemci'
import { bugun, zamanBicimle } from '@/lib/tarih'
import { sayiBicimle } from '@/lib/sayi'
import { TUR_ETIKETLERI, makinistApi, type MakinistOzet } from '@/features/makinist/makinistApi'
import SayfaBasligi from '@/bilesenler/SayfaBasligi.vue'
import VeriDurumu from '@/bilesenler/VeriDurumu.vue'
import PuantajTablo from './PuantajTablo.vue'
import { puantajApi, suzgecSorgusu, type PuantajSuzgeci, type PuantajYaniti } from './puantajApi'

const ay = ref(bugun().slice(0, 7))
const makinist = ref('')
const tur = ref<'MAKINIST' | 'KONDUKTOR'>('MAKINIST')
const makinistler = ref<MakinistOzet[]>([])

/** Seçici yalnız seçili türü gösterir (kondüktör makinist listesine karışmaz). */
const seciliTurPersoneli = computed(() =>
  makinistler.value.filter((m) => (m.tur ?? 'MAKINIST') === tur.value),
)

const rapor = ref<PuantajYaniti | null>(null)
/** Görünen raporun süzgeci: form sonradan değişse de Excel/Yazdır bununla çalışır. */
const uygulanan = ref<PuantajSuzgeci | null>(null)
const hata = ref('')
const ayHata = ref('')
const ayAlani = ref<HTMLInputElement | null>(null)
const mesgul = ref(false)

onMounted(async () => {
  try {
    makinistler.value = await makinistApi.liste('', '')
  } catch (e) {
    hata.value = e instanceof ApiHata ? e.detail : 'Makinist listesi alınamadı.'
  }
})
// Boş sayfa yerine varsayılan süzgeçle (bu ay, tüm makinistler) rapor hemen gelir.
onMounted(getir)

async function getir() {
  if (mesgul.value) return
  hata.value = ''
  ayHata.value = ''
  if (!ay.value) {
    ayHata.value = 'Ay seçimi gerekli.'
    await nextTick()
    ayAlani.value?.focus()
    return
  }
  const suzgec: PuantajSuzgeci = {
    ay: ay.value,
    makinist: makinist.value,
    ...(tur.value === 'KONDUKTOR' ? { tur: tur.value } : {}),
  }
  mesgul.value = true
  rapor.value = null
  try {
    rapor.value = await puantajApi.rapor(suzgec)
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
    const { blob, ad } = await puantajApi.excel(uygulanan.value)
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = ad ?? 'puantaj.xlsx'
    a.click()
    URL.revokeObjectURL(url)
  } catch (e) {
    hata.value = e instanceof ApiHata ? e.detail : 'Excel indirilemedi.'
  }
}

const girilmedi = (n: number | null | undefined, birim = '') =>
  n == null ? 'girilmedi' : `${sayiBicimle(n)}${birim}`

const oranlar = computed<[string, string][]>(() => {
  const o = rapor.value?.oranlar
  if (!o) return []
  return [
    ['Saat ücreti', girilmedi(o.saatUcreti, ' TL')],
    ['Fazla mesai çarpanı', sayiBicimle(o.fazlaMesaiCarpani)],
    ['Gece tazminat oranı', sayiBicimle(o.geceTazminatOrani)],
    ['Km birim tutarı', girilmedi(o.kmBirimTutari, ' TL')],
    ['Asgari günlük süre', `${sayiBicimle(o.asgariGunlukSaat)} saat`],
    ['Normal haftalık süre', `${sayiBicimle(o.normalHaftalikSaat)} saat`],
    ['Gece penceresi', o.gecePenceresi],
  ]
})
</script>

<template>
  <section class="rapor-sayfa">
    <SayfaBasligi baslik="Puantaj ön hesabı">
      <template v-if="rapor && uygulanan" #eylemler>
        <button type="button" class="btn" @click="excelIndir">Excel indir</button>
        <RouterLink
          class="btn"
          :to="{ name: 'yazdir-puantaj', query: suzgecSorgusu(uygulanan) }"
          target="_blank"
          rel="noopener"
        >
          Yazdır
        </RouterLink>
      </template>
    </SayfaBasligi>

    <form class="arac-cubugu" @submit.prevent="getir">
      <div class="alan alan-zorunlu">
        <label for="puantaj-ay">Ay</label>
        <input
          id="puantaj-ay"
          ref="ayAlani"
          v-model="ay"
          type="month"
          required
          :aria-invalid="ayHata ? 'true' : undefined"
          :aria-describedby="ayHata ? 'puantaj-ay-hata' : undefined"
        />
        <AlanHatasi id="puantaj-ay-hata" :metin="ayHata" />
      </div>
      <div class="alan">
        <label for="puantaj-tur">Personel türü</label>
        <select id="puantaj-tur" v-model="tur" @change="((makinist = ''), (rapor = null))">
          <option v-for="(etiket, k) in TUR_ETIKETLERI" :key="k" :value="k">{{ etiket }}</option>
        </select>
      </div>
      <div class="alan">
        <label for="puantaj-makinist">Personel</label>
        <select id="puantaj-makinist" v-model="makinist">
          <option value="">Tüm {{ tur === 'KONDUKTOR' ? 'kondüktörler' : 'makinistler' }}</option>
          <option v-for="m in seciliTurPersoneli" :key="m.id" :value="String(m.id)">
            {{ m.sicil }} — {{ m.adSoyad }}
          </option>
        </select>
      </div>
      <button type="submit" class="btn btn-birincil" :disabled="mesgul">Raporu getir</button>
    </form>

    <VeriDurumu v-if="mesgul" durum="yukleniyor" metin="Rapor hazırlanıyor…" />
    <VeriDurumu v-else-if="hata && !rapor" durum="hata" :metin="hata" />
    <p v-else-if="hata" class="bildirim bildirim-hata" role="alert">{{ hata }}</p>

    <template v-if="rapor && uygulanan">
      <div class="kart ozet" data-test="ozet">
        <p>
          Üretim zamanı: <strong>{{ zamanBicimle(rapor.uretimZamani) }}</strong> · Üreten:
          <strong>{{ rapor.ureten }}</strong>
        </p>
        <dl class="oranlar" data-test="oranlar">
          <div v-for="[ad, deger] in oranlar" :key="ad">
            <dt>{{ ad }}:</dt>
            <dd class="sayisal">{{ deger }}</dd>
          </div>
        </dl>
      </div>
      <PuantajTablo :rapor="rapor" />
    </template>
  </section>
</template>

<style scoped>
.ozet p {
  margin: 0 0 var(--aralik-2);
}
.oranlar {
  display: flex;
  flex-wrap: wrap;
  gap: var(--aralik-2) var(--aralik-6);
  margin: var(--aralik-3) 0 0;
}
.oranlar div {
  display: flex;
  gap: var(--aralik-2);
}
.oranlar dt {
  color: var(--renk-metin-ikincil);
}
.oranlar dd {
  margin: 0;
  font-weight: var(--yazi-agirlik-orta);
}
</style>
