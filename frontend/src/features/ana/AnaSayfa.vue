<script setup lang="ts">
import { computed, defineAsyncComponent, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { ApiHata } from '@/api/istemci'
import { kullanici, yoneticiMi } from '@/composables/oturum'
import { atamaApi, type GunSatiri } from '@/features/atama/atamaApi'
import { belgeApi } from '@/features/belge/belgeApi'
import { raporApi } from '@/features/rapor/raporApi'
import { bugun, gunKaydir, tarihBicimle } from '@/lib/tarih'
import SayfaBasligi from '@/bilesenler/SayfaBasligi.vue'
import Simge from '@/bilesenler/Simge.vue'
import VeriDurumu from '@/bilesenler/VeriDurumu.vue'

const GUN_SONRASI = 7 // kırmızı sayacı: bugün + sonraki 7 gün (atama masası rozetiyle aynı)

const tarih = bugun()
const yukleniyor = ref(true)
const seferler = ref<{ sefer: number; atanmamis: number; iptal: number } | null>(null)
const kirmizi = ref<{ sefer: number; atama: number; intikal: number } | null>(null)
const belge = ref<{ dolmus: number; yaklasan: number } | null>(null)
const izin = ref<number | null>(null)
/** Dört kaynak birbirinden bağımsız okunur: biri düşerse yalnız kendi kartı hata gösterir. */
const hatalar = ref<{ sefer?: string; kirmizi?: string; belge?: string; izin?: string }>({})

const hataMetni = (e: unknown, yedek: string) => (e instanceof ApiHata ? e.detail : yedek)

/** Henüz bitmemiş ve asıl makinisti olmayan seferler: sabah listesinde asıl iş. Bitmiş seferler atanmamış sayılmaz. */
function atanmamisMi(s: GunSatiri, simdi: number): boolean {
  return (
    s.durum === 'PLANLI' &&
    !s.makinistler.some((m) => m.rol === 'ASIL') &&
    new Date(s.guncelVaris).getTime() > simdi
  )
}

async function ozetYukle() {
  yukleniyor.value = true
  hatalar.value = {}
  const [gun, kr, bd, iz] = await Promise.allSettled([
    atamaApi.gun(tarih),
    atamaApi.kirmiziSayisi(tarih, gunKaydir(tarih, GUN_SONRASI)),
    belgeApi.durum(),
    raporApi.izinListe('TALEP'),
  ])
  const simdi = Date.now()
  if (gun.status === 'fulfilled') {
    const s = gun.value.satirlar
    seferler.value = {
      sefer: s.length,
      atanmamis: s.filter((x) => atanmamisMi(x, simdi)).length,
      iptal: s.filter((x) => x.durum === 'IPTAL').length,
    }
  } else hatalar.value.sefer = hataMetni(gun.reason, 'Bugünün seferleri yüklenemedi.')
  if (kr.status === 'fulfilled') {
    kirmizi.value = {
      sefer: kr.value.kirmiziSefer,
      atama: kr.value.kirmiziAtama,
      intikal: kr.value.kirmiziIntikal ?? 0,
    }
  } else hatalar.value.kirmizi = hataMetni(kr.reason, 'Kontrol bekleyen atamalar yüklenemedi.')
  if (bd.status === 'fulfilled') {
    const m = bd.value.makinistler.filter((x) => x.durum === 'AKTIF')
    belge.value = {
      dolmus: m.filter((x) => x.enKotu === 'DOLDU').length,
      yaklasan: m.filter((x) => x.enKotu === 'YAKLASIYOR').length,
    }
  } else hatalar.value.belge = hataMetni(bd.reason, 'Belge durumu yüklenemedi.')
  if (iz.status === 'fulfilled') izin.value = (iz.value ?? []).length
  else hatalar.value.izin = hataMetni(iz.reason, 'İzin talepleri yüklenemedi.')
  yukleniyor.value = false
}

const bos = computed(() => seferler.value?.sefer === 0)

// Geliştirme yardımcıları (demo bölümü): yalnız geliştirme derlemesinde ve ayrı dosyada (INFO: üretim paketinde ölü kod kalmasın). `import.meta.env.DEV` üretimde sabit false olur;
// Rollup bu dalı ve içindeki dinamik içe aktarmayı atar, DemoAraclari chunk'ı hiç üretilmez.
const DemoAraclari = import.meta.env.DEV
  ? defineAsyncComponent(() => import('./DemoAraclari.vue'))
  : null

onMounted(() => void ozetYukle())
</script>

<template>
  <section class="ana">
    <SayfaBasligi baslik="Hoş geldiniz">
      <template #sayaclar>
        <p v-if="kullanici" class="karsilama">
          {{ kullanici.adSoyad }}, makinist atama masasına hoş geldiniz.
        </p>
      </template>
    </SayfaBasligi>

    <h2 class="ozet-baslik">Bugünün durumu · {{ tarihBicimle(tarih) }}</h2>
    <VeriDurumu
      v-if="yukleniyor && !seferler"
      durum="yukleniyor"
      metin="Yükleniyor…"
      data-test="yukleniyor"
    />

    <VeriDurumu v-if="bos" durum="bos" metin="Bugün için sefer yok." data-test="bos-durum">
      <template #eylem>
        <RouterLink class="btn btn-birincil" :to="{ name: 'sefer-yukle' }"
          >Sefer listesi yükleyin</RouterLink
        >
      </template>
    </VeriDurumu>

    <div class="ozet-izgara">
      <article class="ozet-kart" data-test="kart-sefer">
        <h3 class="ozet-etiket"><Simge ad="tren" boyut="1.1em" />Bugünün seferleri</h3>
        <p v-if="hatalar.sefer" class="ozet-hata" role="alert">
          <Simge ad="engel" boyut="1.1em" />{{ hatalar.sefer }}
        </p>
        <template v-else-if="seferler">
          <p class="ozet-deger">{{ seferler.sefer }}</p>
          <p :class="{ 'ozet-vurgu-uyari': seferler.atanmamis > 0 }" data-test="atanmamis">
            Atanmamış (henüz bitmeyen): <strong>{{ seferler.atanmamis }}</strong>
          </p>
          <p v-if="seferler.iptal > 0" class="soluk">İptal: {{ seferler.iptal }}</p>
        </template>
        <RouterLink class="btn" :to="{ name: 'atama', query: { tarih } }">
          Atama masasını aç
        </RouterLink>
      </article>

      <article class="ozet-kart" data-test="kart-kirmizi">
        <h3 class="ozet-etiket"><Simge ad="engel" boyut="1.1em" />Kontrol bekleyen atamalar</h3>
        <p class="soluk">Bugün ve sonraki {{ GUN_SONRASI }} gün</p>
        <p v-if="hatalar.kirmizi" class="ozet-hata" role="alert">
          <Simge ad="engel" boyut="1.1em" />{{ hatalar.kirmizi }}
        </p>
        <template v-else-if="kirmizi">
          <p class="ozet-deger" :class="{ 'ozet-engel': kirmizi.sefer > 0 }">{{ kirmizi.sefer }}</p>
          <p>sefer ({{ kirmizi.atama }} atama)</p>
          <p v-if="kirmizi.intikal > 0" class="ozet-vurgu-engel" data-test="kirmizi-intikal">
            + {{ kirmizi.intikal }} kırmızı intikal
          </p>
        </template>
        <RouterLink class="btn" :to="{ name: 'atama', query: { tarih } }">
          Kırmızıları gör
        </RouterLink>
      </article>

      <article class="ozet-kart" data-test="kart-belge">
        <h3 class="ozet-etiket"><Simge ad="belge" boyut="1.1em" />Belgeler</h3>
        <p class="soluk">Aktif personel</p>
        <p v-if="hatalar.belge" class="ozet-hata" role="alert">
          <Simge ad="engel" boyut="1.1em" />{{ hatalar.belge }}
        </p>
        <template v-else-if="belge">
          <p :class="{ 'ozet-vurgu-engel': belge.dolmus > 0 }">
            Süresi dolmuş: <strong>{{ belge.dolmus }}</strong>
          </p>
          <p :class="{ 'ozet-vurgu-uyari': belge.yaklasan > 0 }">
            Süresi yaklaşan: <strong>{{ belge.yaklasan }}</strong>
          </p>
        </template>
        <RouterLink class="btn" :to="{ name: 'belgeler' }">Belge durumu</RouterLink>
      </article>

      <article class="ozet-kart" data-test="kart-izin">
        <h3 class="ozet-etiket"><Simge ad="izin" boyut="1.1em" />İzin talepleri</h3>
        <p v-if="hatalar.izin" class="ozet-hata" role="alert">
          <Simge ad="engel" boyut="1.1em" />{{ hatalar.izin }}
        </p>
        <template v-else-if="izin !== null">
          <p class="ozet-deger" :class="{ 'ozet-uyari': izin > 0 }">{{ izin }}</p>
          <p>karar bekliyor</p>
        </template>
        <RouterLink class="btn" :to="{ name: 'izin' }">İzinler</RouterLink>
      </article>
    </div>

    <component
      :is="DemoAraclari"
      v-if="DemoAraclari && yoneticiMi"
      :bos-gun="bos"
      @degisti="ozetYukle"
    />
  </section>
</template>

<style scoped>
.ana {
  display: grid;
  gap: var(--aralik-4);
  align-content: start;
}
.ana :deep(.sayfa-baslik) {
  margin-bottom: 0;
}
.karsilama {
  margin: 0;
  color: var(--renk-metin-ikincil);
}
.ozet-baslik {
  margin: var(--aralik-3) 0 0;
  font-size: var(--yazi-xl);
}
.ozet-izgara {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(15rem, 1fr));
  gap: var(--aralik-4);
}
.ozet-kart p,
.ozet-kart h3 {
  margin: 0;
}
.ozet-etiket {
  display: flex;
  align-items: center;
  gap: var(--aralik-3);
  font-size: var(--yazi-md);
}
.ozet-kart .btn {
  align-self: flex-start;
  margin-top: auto;
}
.ozet-hata,
.ozet-vurgu-engel {
  display: flex;
  align-items: center;
  gap: var(--aralik-2);
  color: var(--renk-engel-metin);
}
.ozet-vurgu-uyari {
  color: var(--renk-uyari-metin);
}
.ozet-vurgu-engel,
.ozet-vurgu-uyari {
  font-weight: var(--yazi-agirlik-orta);
}
</style>
