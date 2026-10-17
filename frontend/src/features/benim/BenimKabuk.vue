<script setup lang="ts">
import { computed, watch } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import { ayrilabilirMi } from '@/composables/cikis-korumasi'
import { cikisYap, kullanici } from '@/composables/oturum'
import { rozetMetni, rozetYaz } from './rozet'
import './benim.css'

const router = useRouter()
const route = useRoute()
/** Sayfa değişince önceki sayfanın durum metni (aria-live bölgesi) bayat kalmasın (UX-5); yeni sayfa kendi metnini yükleme sonrası yazar. */
watch(
  () => route.path,
  () => rozetYaz(''),
)

const sekmeler = [
  { ad: 'Görevler', yol: '/benim/gorevler', simge: '🚆' },
  { ad: 'Belgeler', yol: '/benim/belgeler', simge: '📄' },
  { ad: 'İzin', yol: '/benim/izin', simge: '🌴' },
  { ad: 'Özet', yol: '/benim/ozet', simge: '📊' },
]
/** Geçici parola bayrağı açıkken yalnız parola ekranı vardır: sekmeler çizilmez. */
const sekmeGoster = computed(() => !kullanici.value?.parolaDegistirmeli)

async function cik() {
  if (!(await ayrilabilirMi())) return
  try {
    await cikisYap()
  } catch {
    // sunucuya ulaşılamadı: yerel oturum yine de kapandı; giriş ekranı gösterilir
  } finally {
    await router.replace({ name: 'giris' })
  }
}
</script>

<template>
  <div class="benim">
    <header class="benim-ust">
      <div>
        <span class="benim-marka">Makinist Atama</span>
        <span class="benim-ad">{{ kullanici?.adSoyad }}</span>
      </div>
      <button type="button" class="btn" @click="cik">Çıkış</button>
    </header>
    <main class="benim-icerik">
      <RouterView />
    </main>
    <p class="benim-rozet" role="status" aria-live="polite" aria-atomic="true">{{ rozetMetni }}</p>
    <nav v-if="sekmeGoster" class="benim-sekmeler" aria-label="Personel menüsü">
      <RouterLink v-for="s in sekmeler" :key="s.yol" :to="s.yol">
        <span aria-hidden="true">{{ s.simge }}</span>
        {{ s.ad }}
      </RouterLink>
    </nav>
  </div>
</template>
