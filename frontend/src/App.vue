<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import { ayrilabilirMi } from '@/composables/cikis-korumasi'
import { cikisYap, kullanici, yoneticiMi } from '@/composables/oturum'
import { ROL_ETIKETLERI } from '@/api/tipler'
import BenimKabuk from '@/features/benim/BenimKabuk.vue'

const router = useRouter()
const route = useRoute()

const menu = computed(() =>
  router
    .getRoutes()
    .filter((r) => r.meta.menu && (r.meta.rol !== 'Y' || yoneticiMi.value))
    .sort((a, b) => a.meta.menu!.sira - b.meta.menu!.sira),
)

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
  <template v-else>
    <header v-if="kullanici && !route.meta.yazdir" class="ust-cubuk">
      <span class="marka">Makinist Atama</span>
      <nav aria-label="Ana menü" class="menu">
        <RouterLink v-for="m in menu" :key="m.path" :to="m.path">{{ m.meta.menu!.ad }}</RouterLink>
      </nav>
      <span class="kullanici-bilgisi">
        {{ kullanici.adSoyad }} <small>({{ ROL_ETIKETLERI[kullanici.rol] }})</small>
      </span>
      <button type="button" class="btn" @click="cik">Çıkış</button>
    </header>
    <main :class="{ sayfa: !route.meta.yazdir }">
      <RouterView />
    </main>
  </template>
</template>
