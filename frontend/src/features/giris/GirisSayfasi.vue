<script setup lang="ts">
import { nextTick, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ApiHata } from '@/api/istemci'
import { girisYap } from '@/composables/oturum'
import Simge from '@/bilesenler/Simge.vue'

const route = useRoute()
const router = useRouter()

const eposta = ref('')
const parola = ref('')
const hata = ref('')
const gonderiliyor = ref(false)
/** 401 işleyicisi (oturum düştü) oturum=bitti ekler; ilk ziyarette ya da çıkıştan sonra not çıkmaz. */
const oturumBitti = route.query.oturum === 'bitti'
/** Parola değiştirme sonrası (sunucu oturumu düşürür): kullanıcıya yeni parolayla girmesi söylenir. */
const parolaDegisti = route.query.parola === 'degisti'

/** Yalnız uygulama içi yol: "//dis-site" gibi adreslere yönlendirme yok. */
function donusAdresi(): string {
  const git = route.query.git
  return typeof git === 'string' && git.startsWith('/') && !git.startsWith('//') ? git : '/'
}

async function gonder() {
  if (gonderiliyor.value) return
  gonderiliyor.value = true
  hata.value = ''
  try {
    await girisYap(eposta.value.trim(), parola.value)
    await router.replace(donusAdresi())
  } catch (e) {
    hata.value = e instanceof ApiHata ? e.detail : 'Giriş yapılamadı.'
    parola.value = ''
    // QA BUG-6: hata bandı alanlara bağlanır; odak yeniden denenecek parola alanına gider
    await nextTick()
    document.getElementById('parola')?.focus()
  } finally {
    gonderiliyor.value = false
  }
}
</script>

<template>
  <section class="giris-kutusu" data-yogunluk="dokunma">
    <div class="giris-marka">
      <span class="marka-isaret"><Simge ad="tren" boyut="1.5rem" /></span>
      <h1>Makinist Atama</h1>
    </div>
    <p v-if="oturumBitti" class="bildirim bildirim-bilgi" role="status">
      Oturumunuz sona erdi. Kaldığınız yere dönmek için yeniden giriş yapın.
    </p>
    <p v-if="parolaDegisti" class="bildirim bildirim-bilgi" role="status">
      Parolanız değiştirildi. Yeni parolanızla giriş yapın.
    </p>
    <p class="soluk">Devam etmek için giriş yapın.</p>
    <form @submit.prevent="gonder">
      <div class="alan">
        <label for="eposta">E-posta veya sicil</label>
        <input
          id="eposta"
          v-model="eposta"
          type="text"
          autocapitalize="none"
          autocorrect="off"
          spellcheck="false"
          autocomplete="username"
          required
          autofocus
          :aria-invalid="!!hata"
          :aria-describedby="hata ? 'giris-hata' : undefined"
        />
      </div>
      <div class="alan">
        <label for="parola">Parola</label>
        <input
          id="parola"
          v-model="parola"
          type="password"
          autocomplete="current-password"
          required
          :aria-invalid="!!hata"
          :aria-describedby="hata ? 'giris-hata' : undefined"
        />
      </div>
      <p v-if="hata" id="giris-hata" class="bildirim bildirim-hata" role="alert">{{ hata }}</p>
      <button type="submit" class="btn btn-birincil giris-gonder" :disabled="gonderiliyor">
        {{ gonderiliyor ? 'Giriş yapılıyor…' : 'Giriş yap' }}
      </button>
    </form>
  </section>
</template>
