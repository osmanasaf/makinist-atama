<script setup lang="ts">
/**
 * İlk kurulum sihirbazı (F1, MK-171). Adımlar sunucudan, veriden türetilir (bayrak saklanmaz): sayfa yenilenince kaldığı yerden devam eder. Mevcut sayfalar yeniden yazılmaz;
 * her adım ilgili sayfaya bağlanır, yalnız kurum adı/logo formu satır içidir. "Sonraki" düğmesi yoktur.
 */
import { computed, onMounted, ref } from 'vue'
import { RouterLink, type RouteLocationRaw } from 'vue-router'
import { ApiHata } from '@/api/istemci'
import Dugme from '@/bilesenler/Dugme.vue'
import Rozet from '@/bilesenler/Rozet.vue'
import SayfaBasligi from '@/bilesenler/SayfaBasligi.vue'
import VeriDurumu from '@/bilesenler/VeriDurumu.vue'
import KurumAyari from '@/features/kurum/KurumAyari.vue'
import { kurulumApi, type KurulumAdimKodu, type KurulumDurumu } from './kurulumApi'

const durum = ref<KurulumDurumu | null>(null)
const hata = ref('')
const yukleniyor = ref(true)
const gizleMesgul = ref(false)

/** Adım -> ilgili mevcut sayfa ve eylem metni. KURUM adımı satır içi form kullanır (bağlantı yok). */
const EYLEMLER: Record<
  Exclude<KurulumAdimKodu, 'KURUM'>,
  { hedef: RouteLocationRaw; metin: string; not: string }
> = {
  TANIM: {
    hedef: { name: 'tanimlar' },
    metin: 'Tanımlara git',
    not: 'En az iki gar, bir hat ve bir araç tipi gerekir.',
  },
  MAKINIST: {
    hedef: { name: 'makinist-yukle' },
    metin: "Personeli Excel'den yükle",
    not: 'Şablonu indirip doldurun; önizlemeyi görmeden hiçbir kayıt yazılmaz. Tek tek de eklenebilir.',
  },
  PROFIL: {
    hedef: { name: 'profil' },
    metin: 'Kural profiline git',
    not: 'Hazır profiller örnek başlangıçtır ve kurum mevzuatıyla doğrulanmalıdır. Varsayılan kurallarla devam edecekseniz bu adımı atlayıp hatırlatmayı gizleyebilirsiniz.',
  },
  SEFER: {
    hedef: { name: 'sefer-yukle' },
    metin: 'Sefer listesi yükle',
    not: 'Excel şablonuyla sefer listesini yükleyin.',
  },
}

const yapilan = computed(() => durum.value?.adimlar.filter((a) => a.yapildi).length ?? 0)
const toplam = computed(() => durum.value?.adimlar.length ?? 0)

async function yukle() {
  yukleniyor.value = true
  hata.value = ''
  try {
    durum.value = await kurulumApi.durum()
  } catch (e) {
    durum.value = null
    hata.value = e instanceof ApiHata ? e.detail : 'Kurulum durumu yüklenemedi.'
  } finally {
    yukleniyor.value = false
  }
}

async function gizleDegistir() {
  if (!durum.value || gizleMesgul.value) return
  gizleMesgul.value = true
  try {
    durum.value = await kurulumApi.gizle(!durum.value.gizli)
  } catch (e) {
    hata.value = e instanceof ApiHata ? e.detail : 'Tercih kaydedilemedi.'
  } finally {
    gizleMesgul.value = false
  }
}

onMounted(yukle)
</script>

<template>
  <section>
    <SayfaBasligi baslik="İlk kurulum">
      <template #sayaclar>
        <p v-if="durum" class="soluk" data-test="ilerleme">
          {{ yapilan }} / {{ toplam }} adım tamam
        </p>
      </template>
    </SayfaBasligi>
    <p class="soluk">
      Beş adımda ürünü kendi kurumunuza hazırlarsınız. Adımların durumu verinizden hesaplanır;
      sayfayı yenilediğinizde kaldığınız yerden devam edersiniz.
    </p>

    <VeriDurumu v-if="yukleniyor && !durum" durum="yukleniyor" metin="Kurulum durumu okunuyor…" />
    <VeriDurumu v-else-if="hata && !durum" durum="hata" :metin="hata">
      <template #eylem>
        <button type="button" class="btn" @click="yukle">Yeniden dene</button>
      </template>
    </VeriDurumu>

    <template v-if="durum">
      <p v-if="hata" class="bildirim bildirim-hata" role="alert">{{ hata }}</p>
      <p v-if="durum.tamam" class="bildirim bildirim-bilgi" role="status" data-test="tamam">
        Kurulum tamamlandı. Tüm adımlar yapıldı; ayarlara istediğiniz zaman geri dönebilirsiniz.
      </p>

      <ol class="kurulum-adimlar">
        <li
          v-for="(a, i) in durum.adimlar"
          :key="a.kod"
          class="kurulum-adim kart"
          :data-test="`adim-${a.kod}`"
        >
          <div class="kurulum-adim-ust">
            <span class="kurulum-no" aria-hidden="true">{{ i + 1 }}</span>
            <h2>{{ a.ad }}</h2>
            <Rozet v-if="a.yapildi" ton="basari" simge="basari" metin="Tamam" />
            <Rozet v-else ton="uyari" simge="saat" metin="Bekliyor" />
          </div>
          <p class="soluk">{{ a.ayrinti }}</p>
          <KurumAyari v-if="a.kod === 'KURUM'" @degisti="yukle" />
          <template v-else>
            <p class="soluk">{{ EYLEMLER[a.kod].not }}</p>
            <RouterLink
              class="btn"
              :class="{ 'btn-birincil': !a.yapildi }"
              :to="EYLEMLER[a.kod].hedef"
              >{{ EYLEMLER[a.kod].metin }}</RouterLink
            >
          </template>
        </li>
      </ol>

      <div v-if="!durum.tamam" class="eylemler">
        <Dugme data-test="gizle" :yukleniyor="gizleMesgul" @click="gizleDegistir">{{
          durum.gizli ? 'Hatırlatmayı yeniden göster' : 'Hatırlatmayı gizle'
        }}</Dugme>
        <small>Ana Sayfadaki "Kurulumu tamamlayın" kartı için geçerlidir; adımlar silinmez.</small>
      </div>
    </template>
  </section>
</template>

<style scoped>
.kurulum-adimlar {
  display: grid;
  gap: var(--aralik-4);
  margin: var(--aralik-5) 0;
  padding: 0;
  list-style: none;
}
.kurulum-adim {
  display: grid;
  gap: var(--aralik-3);
  justify-items: start;
  padding: var(--aralik-5);
}
.kurulum-adim-ust {
  display: flex;
  align-items: center;
  gap: var(--aralik-3);
}
.kurulum-adim-ust h2 {
  margin: 0;
  font-size: var(--yazi-lg);
}
.kurulum-no {
  display: inline-grid;
  place-items: center;
  width: 2rem;
  height: 2rem;
  border-radius: 50%;
  background: var(--renk-n-100);
  color: var(--renk-metin-ikincil);
  font-weight: var(--yazi-agirlik-kalin);
}
</style>
