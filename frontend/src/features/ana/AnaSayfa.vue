<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { ApiHata } from '@/api/istemci'
import { kullanici, yoneticiMi } from '@/composables/oturum'
import { atamaApi, type GunSatiri } from '@/features/atama/atamaApi'
import { belgeApi } from '@/features/belge/belgeApi'
import { raporApi } from '@/features/rapor/raporApi'
import { bugun, gunKaydir, tarihBicimle } from '@/lib/tarih'
import { demoApi } from './demoApi'

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

// ---- Demo veri (yalnız yönetici ve yalnız dev profili; uç yoksa bölüm hiç görünmez)
const demoYuklu = ref<boolean | null>(null)
const demoMesgul = ref(false)
const demoHata = ref('')
const demoSifirlaOnay = ref(false)
const demoMesaj = ref('')
/** Demo makinist hesapları (uydurma): geçici parola yalnız yükleme yanıtında gösterilir. */
const demoHesaplar = ref<{ sicil: string; adSoyad: string; geciciParola: string }[]>([])

async function demoDurumuOku() {
  try {
    demoYuklu.value = (await demoApi.durum()).yuklu
  } catch {
    demoYuklu.value = null // üretim ya da yetkisiz: bölüm gizli
  }
}

async function demoYukle() {
  if (demoMesgul.value) return
  demoMesgul.value = true
  demoHata.value = ''
  demoMesaj.value = ''
  try {
    const s = await demoApi.yukle()
    demoHesaplar.value = s.hesaplar ?? []
    demoMesaj.value = `Demo veri yüklendi: ${s.makinist} makinist, ${s.sefer} sefer, ${s.izin} izin. Seferler bugünden başlar.`
    await Promise.all([demoDurumuOku(), ozetYukle()])
  } catch (e) {
    demoHata.value = hataMetni(e, 'Demo veri yüklenemedi.')
    await demoDurumuOku()
  } finally {
    demoMesgul.value = false
  }
}

async function demoYolculukOrnekleri() {
  if (demoMesgul.value) return
  demoMesgul.value = true
  demoHata.value = ''
  demoMesaj.value = ''
  try {
    const s = await demoApi.yolculukOrnekleri()
    demoMesaj.value = `Yolculuk örnekleri eklendi: ${s.yolculuk} yolculuk ve bir konaklama.`
  } catch (e) {
    demoHata.value = hataMetni(e, 'Yolculuk örnekleri eklenemedi.')
  } finally {
    demoMesgul.value = false
  }
}

async function demoParcaliOrnek() {
  if (demoMesgul.value) return
  demoMesgul.value = true
  demoHata.value = ''
  demoMesaj.value = ''
  try {
    const s = await demoApi.parcaliOrnek()
    demoMesaj.value = `Parçalı tren örneği eklendi (DEMO-P1): ${s.parca} parça, demonun 2. günü.`
  } catch (e) {
    demoHata.value = hataMetni(e, 'Parçalı tren örneği eklenemedi.')
  } finally {
    demoMesgul.value = false
  }
}

async function demoIntikalOrnek() {
  if (demoMesgul.value) return
  demoMesgul.value = true
  demoHata.value = ''
  demoMesaj.value = ''
  try {
    const s = await demoApi.intikalOrnek()
    demoMesaj.value = `İntikal örneği eklendi: ${s.sure} gar çifti süresi (Tanımlar > Gar süreleri) ve ${s.sicil} için bir servis intikali (demonun 2. günü).`
  } catch (e) {
    demoHata.value = hataMetni(e, 'İntikal örneği eklenemedi.')
  } finally {
    demoMesgul.value = false
  }
}

async function demoKonduktorOrnegi() {
  if (demoMesgul.value) return
  demoMesgul.value = true
  demoHata.value = ''
  demoMesaj.value = ''
  try {
    const s = await demoApi.konduktorOrnegi()
    demoHesaplar.value = [...demoHesaplar.value, s.hesap]
    demoMesaj.value = `Kondüktör örneği eklendi: ${s.konduktor} kondüktör (K001..), ${s.atama} yolcu trenine atandı; yolcu trenlerinde kondüktör gereksinimi açıldı.`
  } catch (e) {
    demoHata.value = hataMetni(e, 'Kondüktör örneği eklenemedi.')
  } finally {
    demoMesgul.value = false
  }
}

async function demoSifirla() {
  if (demoMesgul.value) return
  demoMesgul.value = true
  demoHata.value = ''
  demoMesaj.value = ''
  try {
    await demoApi.sifirla()
    demoSifirlaOnay.value = false
    demoMesaj.value = 'Demo veri silindi.'
    demoHesaplar.value = []
    await Promise.all([demoDurumuOku(), ozetYukle()])
  } catch (e) {
    demoHata.value = hataMetni(e, 'Demo veri silinemedi.')
  } finally {
    demoMesgul.value = false
  }
}

onMounted(() => {
  void ozetYukle()
  if (yoneticiMi.value) void demoDurumuOku()
})
</script>

<template>
  <section class="ana">
    <div class="kart karsilama">
      <h1>Hoş geldiniz</h1>
      <p v-if="kullanici">{{ kullanici.adSoyad }}, makinist atama masasına hoş geldiniz.</p>
    </div>

    <h2 class="ozet-baslik">Bugün · {{ tarihBicimle(tarih) }}</h2>
    <p v-if="yukleniyor && !seferler" class="soluk" data-test="yukleniyor">Yükleniyor…</p>

    <div v-if="bos" class="bildirim bildirim-bilgi" data-test="bos-durum">
      <p>
        Bugün için sefer yok.
        <RouterLink :to="{ name: 'sefer-yukle' }">Sefer listesi yükleyin</RouterLink>
        <template v-if="demoYuklu === false"> ya da aşağıdan demo veriyi yükleyin.</template>
      </p>
    </div>

    <div class="ozet-izgara">
      <article class="kart ozet" data-test="kart-sefer">
        <h3>Bugünün seferleri</h3>
        <p v-if="hatalar.sefer" class="hata-metni" role="alert">{{ hatalar.sefer }}</p>
        <template v-else-if="seferler">
          <p class="buyuk">{{ seferler.sefer }}</p>
          <p :class="{ uyari: seferler.atanmamis > 0 }" data-test="atanmamis">
            Atanmamış (henüz bitmeyen): <strong>{{ seferler.atanmamis }}</strong>
          </p>
          <p v-if="seferler.iptal > 0" class="soluk">İptal: {{ seferler.iptal }}</p>
        </template>
        <RouterLink class="btn" :to="{ name: 'atama', query: { tarih } }">
          Atama masasını aç
        </RouterLink>
      </article>

      <article class="kart ozet" data-test="kart-kirmizi">
        <h3>Kontrol bekleyen atamalar</h3>
        <p class="soluk">Bugün ve sonraki {{ GUN_SONRASI }} gün</p>
        <p v-if="hatalar.kirmizi" class="hata-metni" role="alert">{{ hatalar.kirmizi }}</p>
        <template v-else-if="kirmizi">
          <p class="buyuk" :class="{ tehlike: kirmizi.sefer > 0 }">{{ kirmizi.sefer }}</p>
          <p>sefer ({{ kirmizi.atama }} atama)</p>
          <p v-if="kirmizi.intikal > 0" class="tehlike" data-test="kirmizi-intikal">
            + {{ kirmizi.intikal }} kırmızı intikal
          </p>
        </template>
        <RouterLink class="btn" :to="{ name: 'atama', query: { tarih } }">
          Kırmızıları gör
        </RouterLink>
      </article>

      <article class="kart ozet" data-test="kart-belge">
        <h3>Belgeler</h3>
        <p class="soluk">Aktif personel</p>
        <p v-if="hatalar.belge" class="hata-metni" role="alert">{{ hatalar.belge }}</p>
        <template v-else-if="belge">
          <p :class="{ tehlike: belge.dolmus > 0 }">
            Süresi dolmuş: <strong>{{ belge.dolmus }}</strong>
          </p>
          <p :class="{ uyari: belge.yaklasan > 0 }">
            Süresi yaklaşan: <strong>{{ belge.yaklasan }}</strong>
          </p>
        </template>
        <RouterLink class="btn" :to="{ name: 'belgeler' }">Belge durumu</RouterLink>
      </article>

      <article class="kart ozet" data-test="kart-izin">
        <h3>İzin talepleri</h3>
        <p v-if="hatalar.izin" class="hata-metni" role="alert">{{ hatalar.izin }}</p>
        <template v-else-if="izin !== null">
          <p class="buyuk" :class="{ uyari: izin > 0 }">{{ izin }}</p>
          <p>karar bekliyor</p>
        </template>
        <RouterLink class="btn" :to="{ name: 'izin' }">İzinler</RouterLink>
      </article>
    </div>

    <section v-if="yoneticiMi && demoYuklu !== null" class="kart demo" data-test="demo">
      <h2>Demo veri</h2>
      <p class="soluk">
        Geliştirme ortamına özel: uydurma personel, sefer ve izinler. Gerçek veri değildir.
      </p>
      <p v-if="demoMesaj" class="bildirim bildirim-bilgi" role="status">{{ demoMesaj }}</p>
      <div v-if="demoHesaplar.length > 0" class="bildirim bildirim-uyari" role="status">
        <div>
          <p>
            <strong>Demo personel hesapları</strong> (telefonda personel olarak girmek için; geçici
            parola yalnız şimdi gösterilir, ilk girişte değiştirilir):
          </p>
          <ul>
            <li v-for="h in demoHesaplar" :key="h.sicil">
              {{ h.sicil }} · {{ h.adSoyad }} · <code>{{ h.geciciParola }}</code>
            </li>
          </ul>
        </div>
      </div>
      <p v-if="demoHata" class="bildirim bildirim-hata" role="alert">{{ demoHata }}</p>
      <template v-if="!demoYuklu">
        <button
          type="button"
          class="btn btn-birincil"
          data-test="demo-yukle"
          :disabled="demoMesgul"
          @click="demoYukle"
        >
          {{ demoMesgul ? 'Yükleniyor… (yaklaşık 15 sn sürer)' : 'Demo veriyi yükle' }}
        </button>
      </template>
      <template v-else>
        <p>Demo veri yüklü.</p>
        <button
          type="button"
          class="btn"
          data-test="demo-yolculuk"
          :disabled="demoMesgul"
          @click="demoYolculukOrnekleri"
        >
          Yolculuk örneklerini ekle
        </button>
        <button
          type="button"
          class="btn"
          data-test="demo-parcali"
          :disabled="demoMesgul"
          @click="demoParcaliOrnek"
        >
          Parçalı örnek ekle
        </button>
        <button
          type="button"
          class="btn"
          data-test="demo-intikal"
          :disabled="demoMesgul"
          @click="demoIntikalOrnek"
        >
          İntikal örneği ekle
        </button>
        <button
          type="button"
          class="btn"
          data-test="demo-konduktor"
          :disabled="demoMesgul"
          @click="demoKonduktorOrnegi"
        >
          Kondüktör örneği ekle
        </button>
        <button
          v-if="!demoSifirlaOnay"
          type="button"
          class="btn"
          data-test="demo-sifirla"
          @click="demoSifirlaOnay = true"
        >
          Demo veriyi sıfırla
        </button>
        <div v-else class="bildirim bildirim-uyari" role="alert">
          <p>
            Tüm personel (makinist ve kondüktör), sefer, atama ve izin kayıtları silinir
            (kullanıcılar ve değişiklik kaydı kalır). Emin misiniz?
          </p>
          <span>
            <button
              type="button"
              class="btn btn-tehlike"
              data-test="demo-sifirla-onay"
              :disabled="demoMesgul"
              @click="demoSifirla"
            >
              Evet, sıfırla
            </button>
            <button
              type="button"
              class="btn"
              :disabled="demoMesgul"
              @click="demoSifirlaOnay = false"
            >
              Vazgeç
            </button>
          </span>
        </div>
      </template>
    </section>
  </section>
</template>

<style scoped>
.ana {
  display: grid;
  gap: var(--bosluk);
}
.ozet-baslik {
  margin: 0.5rem 0 0;
}
.ozet-izgara {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(14rem, 1fr));
  gap: var(--bosluk);
}
.ozet {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  align-items: flex-start;
}
.ozet h3 {
  margin: 0;
  font-size: 1rem;
}
.ozet p {
  margin: 0;
}
.ozet .btn {
  margin-top: auto;
}
.buyuk {
  font-size: 2rem;
  font-weight: 600;
  line-height: 1.1;
}
.tehlike {
  color: var(--renk-tehlike);
}
.uyari {
  color: #8a5a00;
}
.hata-metni {
  color: var(--renk-tehlike);
}
.demo {
  display: grid;
  gap: 0.5rem;
  justify-items: start;
}
.demo p,
.demo h2 {
  margin: 0;
}
</style>
