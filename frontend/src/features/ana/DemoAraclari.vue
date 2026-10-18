<script setup lang="ts">
/**
 * Demo araçları (yalnız geliştirme derlemesi, yalnız yönetici): AnaSayfa bunu `import.meta.env.DEV` koşuluyla tembel yükler. Üretimde
 * koşul sabit false olur, bu dosyanın parçası (chunk) hiç üretilmez ve "Demo araçları" metni pakete girmez (OK-31; `vite build` çıktısıyla doğrulanır).
 * Uç yoksa (üretim profili / yetkisiz) bölüm hiç görünmez.
 */
import { onMounted, ref } from 'vue'
import { ApiHata } from '@/api/istemci'
import { demoApi } from './demoApi'

/** Bugün için sefer yoksa ve demo yüklü değilse bölüm açık gelir ve demo yüklemeyi önerir (boş durumun yönlendirmesi). */
const props = defineProps<{ bosGun?: boolean }>()
/** Veri değişti (yükleme / sıfırlama): ana sayfa özetini yenilesin. */
const emit = defineEmits<{ degisti: [] }>()

const hataMetni = (e: unknown, yedek: string) => (e instanceof ApiHata ? e.detail : yedek)

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
    await demoDurumuOku()
    emit('degisti')
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
    await demoDurumuOku()
    emit('degisti')
  } catch (e) {
    demoHata.value = hataMetni(e, 'Demo veri silinemedi.')
  } finally {
    demoMesgul.value = false
  }
}

onMounted(() => void demoDurumuOku())
</script>

<template>
  <details
    v-if="demoYuklu !== null"
    class="demo"
    data-test="demo"
    :open="props.bosGun && demoYuklu === false"
  >
    <summary>Demo araçları</summary>
    <div class="demo-icerik">
      <p v-if="props.bosGun && demoYuklu === false" class="soluk">
        Bugün için sefer yok: aşağıdaki düğmeyle demo veriyi yükleyebilirsiniz.
      </p>
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
        <div class="demo-dugmeler">
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
        </div>
        <div v-if="demoSifirlaOnay" class="bildirim bildirim-uyari" role="alert">
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
    </div>
  </details>
</template>

<style scoped>
.demo {
  border: 1px dashed var(--renk-kontrol-kenar);
  border-radius: var(--yaricap);
  padding: var(--aralik-3) var(--aralik-4);
  background: var(--renk-yuzey);
  color: var(--renk-metin-ikincil);
}
.demo summary {
  cursor: pointer;
  font-weight: var(--yazi-agirlik-orta);
}
.demo-icerik {
  display: grid;
  gap: var(--aralik-3);
  justify-items: start;
  margin-top: var(--aralik-3);
}
.demo-icerik p {
  margin: 0;
}
.demo-dugmeler {
  display: flex;
  flex-wrap: wrap;
  gap: var(--aralik-3);
}
</style>
