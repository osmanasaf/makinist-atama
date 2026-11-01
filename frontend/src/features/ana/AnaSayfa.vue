<script setup lang="ts">
import { computed, defineAsyncComponent, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { ApiHata } from '@/api/istemci'
import { kullanici, yoneticiMi } from '@/composables/oturum'
import { atamaApi, type GunSatiri } from '@/features/atama/atamaApi'
import { masaBaglantisi } from '@/features/atama/masaBaglantisi'
import { belgeApi } from '@/features/belge/belgeApi'
import { raporApi } from '@/features/rapor/raporApi'
import { takasApi } from '@/features/takas/takasApi'
import { bugun, gunKaydir, tarihBicimle } from '@/lib/tarih'
import { atanmamisMi, yapilacaklariOlustur } from './yapilacaklar'
import SayfaBasligi from '@/bilesenler/SayfaBasligi.vue'
import Simge from '@/bilesenler/Simge.vue'
import VeriDurumu from '@/bilesenler/VeriDurumu.vue'

const GUN_SONRASI = 7 // kırmızı sayacı: bugün + sonraki 7 gün (atama masası rozetiyle aynı)

const tarih = bugun()
const yukleniyor = ref(true)
const seferler = ref<{ sefer: number; atanmamis: number; iptal: number } | null>(null)
const kirmizi = ref<{
  sefer: number
  atama: number
  intikal: number
  ilkKirmiziGun?: string | null
} | null>(null)
const gunSatirlari = ref<GunSatiri[] | null>(null)
const gorulmeyen = ref<number | null>(null)
const takas = ref<number | null>(null)
const simdi = ref(Date.now())
const belge = ref<{ dolmus: number; yaklasan: number } | null>(null)
const izin = ref<number | null>(null)
/** Dört kaynak birbirinden bağımsız okunur: biri düşerse yalnız kendi kartı hata gösterir. */
const hatalar = ref<{
  sefer?: string
  kirmizi?: string
  belge?: string
  izin?: string
  takas?: string
  gorulmeyen?: string
}>({})

const hataMetni = (e: unknown, yedek: string) => (e instanceof ApiHata ? e.detail : yedek)

async function ozetYukle() {
  yukleniyor.value = true
  hatalar.value = {}
  const [gun, kr, bd, iz, tk, gd] = await Promise.allSettled([
    atamaApi.gun(tarih),
    atamaApi.kirmiziSayisi(tarih, gunKaydir(tarih, GUN_SONRASI)),
    belgeApi.durum(),
    raporApi.izinListe('TALEP'),
    takasApi.liste('ATAMACI_ONAYI_BEKLIYOR'),
    atamaApi.gordumOzeti(tarih, tarih),
  ])
  simdi.value = Date.now()
  gunSatirlari.value = gun.status === 'fulfilled' ? gun.value.satirlar : null
  if (tk.status === 'fulfilled') takas.value = tk.value.bekleyen
  else hatalar.value.takas = hataMetni(tk.reason, 'Takas talepleri yüklenemedi.')
  if (gd.status === 'fulfilled') gorulmeyen.value = gd.value.gorulmeyen
  else hatalar.value.gorulmeyen = hataMetni(gd.reason, 'Görülmeyen atamalar yüklenemedi.')
  if (gun.status === 'fulfilled') {
    const s = gun.value.satirlar
    seferler.value = {
      sefer: s.length,
      atanmamis: s.filter((x) => atanmamisMi(x, simdi.value)).length,
      iptal: s.filter((x) => x.durum === 'IPTAL').length,
    }
  } else hatalar.value.sefer = hataMetni(gun.reason, 'Bugünün seferleri yüklenemedi.')
  if (kr.status === 'fulfilled') {
    kirmizi.value = {
      sefer: kr.value.kirmiziSefer,
      atama: kr.value.kirmiziAtama,
      intikal: kr.value.kirmiziIntikal ?? 0,
      ilkKirmiziGun: kr.value.ilkKirmiziGun,
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

/** Aciliyete göre sıralı "bugün yapılacaklar" (saf üretici; yalnız okunabilen kaynaklar satır üretir). */
const yapilacaklar = computed(() =>
  yapilacaklariOlustur({
    simdi: simdi.value,
    tarih,
    satirlar: gunSatirlari.value,
    kirmizi: kirmizi.value,
    gorulmeyen: gorulmeyen.value,
    takas: takas.value,
    izin: izin.value,
    belge: belge.value,
  }),
)
const kuyrukHatalari = computed(() =>
  [hatalar.value.takas, hatalar.value.gorulmeyen].filter((m): m is string => !!m),
)
/** Hiç kaynak düşmediyse ve iş yoksa "bekleyen iş yok" denir (eksik veriyle yanlış rahatlık verilmez). */
const isYok = computed(
  () =>
    !yukleniyor.value &&
    !bos.value &&
    Object.keys(hatalar.value).length === 0 &&
    yapilacaklar.value.length === 0,
)

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

    <section class="yapilacaklar" aria-labelledby="yapilacaklar-baslik">
      <h2 id="yapilacaklar-baslik" class="ozet-baslik">
        Bugün yapılacaklar
        <span v-if="yapilacaklar.length" class="soluk">· {{ yapilacaklar.length }} kalem</span>
      </h2>
      <p class="soluk yapilacaklar-not">Aciliyete göre sıralı; her kalemin tek eylemi vardır.</p>
      <ol v-if="yapilacaklar.length" class="yapilacak-liste" data-test="yapilacaklar">
        <li
          v-for="i in yapilacaklar"
          :key="i.kimlik"
          :class="['yapilacak', `yapilacak-${i.ton}`]"
          :data-kimlik="i.kimlik"
        >
          <Simge :ad="i.simge" boyut="1.4em" class="yapilacak-simge" />
          <div class="yapilacak-metin">
            <p class="yapilacak-baslik">{{ i.baslik }}</p>
            <p class="soluk">{{ i.ayrinti }}</p>
          </div>
          <RouterLink
            :class="['btn', i.kimlik === yapilacaklar[0]?.kimlik ? 'btn-birincil' : '']"
            :to="i.hedef"
            :aria-label="`${i.eylem}: ${i.baslik}`"
            >{{ i.eylem }}</RouterLink
          >
        </li>
      </ol>
      <VeriDurumu
        v-else-if="isYok"
        durum="bos"
        metin="Şu an sizi bekleyen iş yok."
        data-test="yapilacak-yok"
      />
      <p
        v-for="m in kuyrukHatalari"
        :key="m"
        class="ozet-hata"
        role="alert"
        data-test="yapilacak-hata"
      >
        <Simge ad="engel" boyut="1.1em" />{{ m }}
      </p>
    </section>

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
        <RouterLink class="btn" :to="masaBaglantisi(tarih)"> Atama masasını aç </RouterLink>
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
        <RouterLink class="btn" :to="masaBaglantisi(kirmizi?.ilkKirmiziGun ?? tarih, 'kirmizi')">
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
.yapilacaklar {
  display: grid;
  gap: var(--aralik-3);
  align-content: start;
}
.yapilacaklar-not,
.yapilacaklar .ozet-baslik {
  margin: 0;
}
.yapilacak-liste {
  display: grid;
  margin: 0;
  padding: 0;
  list-style: none;
  background: var(--renk-yuzey);
  border: 1px solid var(--renk-cizgi);
  border-radius: var(--yaricap);
}
.yapilacak {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--aralik-4);
  padding: var(--aralik-4) var(--aralik-5);
  border-left: 0.25rem solid transparent;
}
.yapilacak + .yapilacak {
  border-top: 1px solid var(--renk-cizgi);
}
.yapilacak-engel {
  border-left-color: var(--renk-engel-dolu);
}
.yapilacak-uyari {
  border-left-color: var(--renk-uyari-cizgi);
}
.yapilacak-engel .yapilacak-simge {
  color: var(--renk-engel-metin);
}
.yapilacak-uyari .yapilacak-simge {
  color: var(--renk-uyari-metin);
}
.yapilacak-metin {
  flex: 1 1 16rem;
  min-width: 0;
}
.yapilacak-metin p {
  margin: 0;
}
.yapilacak-baslik {
  font-weight: var(--yazi-agirlik-orta);
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
