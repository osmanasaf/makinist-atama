<script setup lang="ts">
import { RouterView, useRoute, useRouter } from 'vue-router'
import { ayrilabilirMi } from '@/composables/cikis-korumasi'
import { kurumAdi, logoAdresi } from '@/composables/kurum'
import { cikisYap, kullanici } from '@/composables/oturum'
import { ROL_ETIKETLERI } from '@/api/tipler'
import BenimKabuk from '@/features/benim/BenimKabuk.vue'
import Dugme from '@/bilesenler/Dugme.vue'
import Simge from '@/bilesenler/Simge.vue'
import YanMenu from '@/bilesenler/YanMenu.vue'

const router = useRouter()
const route = useRoute()

/** Kabuk (üst çubuk + yan menü) oturum varken ve yazdırma sayfası dışında görünür. */
const kabukVar = () => !!kullanici.value && !route.meta.yazdir

/** "İçeriğe geç": hash yönlendirici bağlantı adresini kullandığı için href izlenmez; odak ana içeriğe taşınır (WCAG 2.4.1). */
function icerigeGec() {
  document.getElementById('icerik')?.focus()
}

/**
 * Çıkış da bir ayrılıştır: önce kaydedilmemiş veri korumasından geçer (bekleyen kayıtlar OTURUM AÇIKKEN kaydedilir, kaydedilemeyen
 * veri varsa onay sorulur); oturum ondan sonra kapanır. Sunucu çıkışı başarısız olsa da yerel oturum kapanır ve giriş sayfasına gidilir.
 */
async function cik() {
  if (!(await ayrilabilirMi())) return
  try {
    await cikisYap()
  } catch {
    // sunucuya ulaşılamadı: yerel oturum yine de kapandı (cikisYap finally); giriş ekranı gösterilir
  } finally {
    await router.replace({ name: 'giris' })
  }
}
</script>

<template>
  <!-- Makinist telefon görünümü (MK-31): masaüstü menüsü yerine ayrı kabuk; gerçek koruma sunucuda -->
  <BenimKabuk v-if="kullanici?.rol === 'MAKINIST'" />
  <!-- main her durumda aynı öğedir: oturum kapanırken sayfa yeniden bağlanıp (oturumsuz) istek atmasın -->
  <div v-else :class="{ kabuk: kabukVar() }">
    <a v-if="kabukVar()" class="atla-baglanti" href="#icerik" @click.prevent="icerigeGec"
      >İçeriğe geç</a
    >
    <header v-if="kullanici && kabukVar()" class="ust-cubuk">
      <span class="marka">
        <img v-if="logoAdresi" class="marka-logo" :src="logoAdresi" :alt="kurumAdi" />
        <span v-else class="marka-isaret"><Simge ad="tren" /></span>
        {{ kurumAdi }}
      </span>
      <span class="kullanici-bilgisi">
        {{ kullanici.adSoyad }} <small>({{ ROL_ETIKETLERI[kullanici.rol] }})</small>
      </span>
      <Dugme @click="cik">Çıkış</Dugme>
    </header>
    <YanMenu v-if="kabukVar()" />
    <main
      id="icerik"
      tabindex="-1"
      :class="{ sayfa: !route.meta.yazdir, 'sayfa--tam': route.meta.tam }"
    >
      <RouterView />
    </main>
  </div>
</template>
