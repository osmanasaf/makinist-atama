<script setup lang="ts">
/**
 * Kurum adı + logo formu (F1, MK-167). Hem `/kurum` sayfasında hem kurulum sihirbazının ilk adımında kullanılır. Ad ve logo müşterinin kendi verisidir; ürün hiçbir kurumun markasını
 * taşımaz. İstemci ön kontrolü yalnız hızlı geri bildirimdir (tür/boyut); gerçek doğrulama sunucudadır (imza, 256 KB, 1024 piksel).
 */
import { ref, watch } from 'vue'
import { ApiHata } from '@/api/istemci'
import AlanHatasi from '@/bilesenler/AlanHatasi.vue'
import Dugme from '@/bilesenler/Dugme.vue'
import {
  kurumAdi,
  kurumAdiGirildi,
  kurumGuncelle,
  logoAdresi,
  URUN_ADI,
  type KurumAcik,
} from '@/composables/kurum'
import { kurumApi } from './kurumApi'

const emit = defineEmits<{ degisti: [KurumAcik] }>()

const EN_COK_KB = 256
const IZINLI_TURLER = ['image/png', 'image/jpeg']

const ad = ref(kurumAdiGirildi.value ? kurumAdi.value : '')
const adHata = ref('')
const logoHata = ref('')
const bilgi = ref('')
const adMesgul = ref(false)
const logoMesgul = ref(false)
const dosyaGirdisi = ref<HTMLInputElement | null>(null)

// Ad dışarıdan değişirse (başka ekran) ve kullanıcı yazmıyorsa forma yansır
watch(kurumAdi, (yeni) => {
  if (!adMesgul.value && ad.value.trim() === '') ad.value = kurumAdiGirildi.value ? yeni : ''
})

function sonuc(k: KurumAcik, mesaj: string) {
  kurumGuncelle(k)
  emit('degisti', k)
  bilgi.value = mesaj
}

const hataMetni = (e: unknown, yedek: string) => (e instanceof ApiHata ? e.detail : yedek)

async function adKaydet() {
  if (adMesgul.value) return
  adHata.value = ''
  bilgi.value = ''
  if (ad.value.trim() === '') {
    adHata.value = 'Kurum adı boş olamaz.'
    return
  }
  adMesgul.value = true
  try {
    const k = await kurumApi.adYaz(ad.value.trim())
    ad.value = k.ad ?? ''
    sonuc(k, 'Kurum adı kaydedildi.')
  } catch (e) {
    adHata.value = hataMetni(e, 'Kurum adı kaydedilemedi.')
  } finally {
    adMesgul.value = false
  }
}

async function logoSecildi(olay: Event) {
  const girdi = olay.target as HTMLInputElement
  const dosya = girdi.files?.[0]
  logoHata.value = ''
  bilgi.value = ''
  if (!dosya) return
  if (!IZINLI_TURLER.includes(dosya.type)) {
    logoHata.value = 'Logo yalnız PNG veya JPEG olabilir.'
    girdi.value = ''
    return
  }
  if (dosya.size > EN_COK_KB * 1024) {
    logoHata.value = `Logo en fazla ${EN_COK_KB} KB olabilir.`
    girdi.value = ''
    return
  }
  logoMesgul.value = true
  try {
    sonuc(await kurumApi.logoYaz(dosya), 'Logo yüklendi.')
  } catch (e) {
    logoHata.value = hataMetni(e, 'Logo yüklenemedi.')
  } finally {
    logoMesgul.value = false
    girdi.value = ''
  }
}

async function logoKaldir() {
  if (logoMesgul.value) return
  logoHata.value = ''
  bilgi.value = ''
  logoMesgul.value = true
  try {
    sonuc(await kurumApi.logoSil(), 'Logo kaldırıldı.')
  } catch (e) {
    logoHata.value = hataMetni(e, 'Logo kaldırılamadı.')
  } finally {
    logoMesgul.value = false
  }
}
</script>

<template>
  <div class="kurum-ayari" data-test="kurum-ayari">
    <form class="kurum-form" @submit.prevent="adKaydet">
      <div class="alan alan-zorunlu">
        <label for="kurum-ad">Kurum adı</label>
        <input
          id="kurum-ad"
          v-model="ad"
          type="text"
          maxlength="120"
          autocomplete="organization"
          :aria-invalid="!!adHata"
          :aria-describedby="adHata ? 'kurum-ad-yardim kurum-ad-hata' : 'kurum-ad-yardim'"
        />
        <p id="kurum-ad-yardim" class="alan-yardim">
          Giriş sayfasında, üst çubukta, personel ekranında ve yazdırılan listelerin başlığında
          görünür. Hiç ad girilmediyse ürün adı ({{ URUN_ADI }}) gösterilir; ad bir kez
          kaydedildikten sonra boş bırakılamaz, yalnız değiştirilebilir.
        </p>
        <AlanHatasi id="kurum-ad-hata" :metin="adHata" />
      </div>
      <Dugme tip="submit" varyant="birincil" :yukleniyor="adMesgul">Adı kaydet</Dugme>
    </form>

    <div class="alan kurum-logo">
      <label for="kurum-logo">Logo (PNG veya JPEG)</label>
      <div v-if="logoAdresi" class="kurum-logo-onizleme">
        <img :src="logoAdresi" :alt="`${kurumAdi} logosu`" />
        <Dugme :disabled="logoMesgul" @click="logoKaldir">Logoyu kaldır</Dugme>
      </div>
      <p v-else class="soluk" data-test="logo-yok">Logo yüklenmedi.</p>
      <input
        id="kurum-logo"
        ref="dosyaGirdisi"
        type="file"
        accept="image/png,image/jpeg"
        :disabled="logoMesgul"
        :aria-invalid="!!logoHata"
        :aria-describedby="logoHata ? 'kurum-logo-yardim kurum-logo-hata' : 'kurum-logo-yardim'"
        @change="logoSecildi"
      />
      <p id="kurum-logo-yardim" class="alan-yardim">
        En fazla {{ EN_COK_KB }} KB ve 1024 × 1024 piksel. Yalnız kendi kurumunuzun logosunu
        yükleyin; SVG, GIF ve WebP desteklenmez.
      </p>
      <AlanHatasi id="kurum-logo-hata" :metin="logoHata" />
    </div>

    <p v-if="bilgi" class="bildirim bildirim-bilgi" role="status">{{ bilgi }}</p>
  </div>
</template>

<style scoped>
.kurum-ayari {
  display: grid;
  gap: var(--aralik-5);
  max-width: 40rem;
}
.kurum-form {
  display: grid;
  gap: var(--aralik-3);
  justify-items: start;
}
.kurum-logo-onizleme {
  display: flex;
  align-items: center;
  gap: var(--aralik-4);
  margin-bottom: var(--aralik-3);
}
.kurum-logo-onizleme img {
  height: 3rem;
  width: auto;
  max-width: 10rem;
  object-fit: contain;
  border: 1px solid var(--renk-cizgi);
  border-radius: var(--yaricap);
  padding: var(--aralik-1);
  background: var(--renk-yuzey);
}
</style>
