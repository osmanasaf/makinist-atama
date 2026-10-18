<script setup lang="ts">
/**
 * Masaüstü kabuğunun sol menüsü (MK-133). Rotalar `meta.menu.grup` ile gruplanır, rol süzgeci `gruplaMenu`'dadır.
 * ≥900 px: dikey; açık (14,5 rem) ya da daraltılmış (yalnız simge; metin erişilebilirlik için DOM'da kalır). <900 px: içerik üstünde
 * "Menü" düğmesiyle açılan liste. Daraltma tercihi `localStorage`'dadır (erişilemezse genişliğe göre varsayılan).
 */
import { computed, onBeforeUnmount, onMounted, ref, useId, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { yoneticiMi } from '@/composables/oturum'
import Dugme from './Dugme.vue'
import Simge from './Simge.vue'
import { gruplaMenu } from './menu'

const ANAHTAR = 'yanmenu-daraltilmis'
const router = useRouter()
const route = useRoute()
const on = useId()

/** Kullanıcının açık tercihi (localStorage ya da bu oturumdaki düğme); yoksa `null` ve genişlik varsayılanı geçerlidir. */
function kayitliTercih(): boolean | null {
  try {
    const kayit = localStorage.getItem(ANAHTAR)
    if (kayit === '1' || kayit === '0') return kayit === '1'
  } catch {
    // depolama kapalı/erişilemez: varsayılana düş
  }
  return null
}
const genislikVarsayilani = () => window.innerWidth < 1200
let tercih = kayitliTercih()
const daraltilmis = ref(tercih ?? genislikVarsayilani())

// QA BUG-14: tercih yokken pencere küçülüp büyüyünce menü varsayılanı izler (küçükken daralıp büyüyünce kapalı kalmaz).
function yenidenBoyutlandi() {
  if (tercih === null) daraltilmis.value = genislikVarsayilani()
}
onMounted(() => window.addEventListener('resize', yenidenBoyutlandi))
onBeforeUnmount(() => window.removeEventListener('resize', yenidenBoyutlandi))
const mobilAcik = ref(false)

function daraltTersle() {
  daraltilmis.value = !daraltilmis.value
  tercih = daraltilmis.value
  try {
    localStorage.setItem(ANAHTAR, daraltilmis.value ? '1' : '0')
  } catch {
    // tercih kaydedilemedi: yalnız bu oturumda geçerli
  }
}
watch(
  () => route.path,
  () => (mobilAcik.value = false),
)

const gruplar = computed(() => gruplaMenu(router.getRoutes(), yoneticiMi.value))
</script>

<template>
  <nav :class="['yan-menu', { daraltilmis, 'mobil-acik': mobilAcik }]" aria-label="Ana menü">
    <Dugme
      class="menu-ac"
      varyant="hayalet"
      simge="menu"
      :aria-expanded="mobilAcik"
      :aria-controls="`${on}-liste`"
      @click="mobilAcik = !mobilAcik"
    >
      Menü
    </Dugme>
    <div :id="`${on}-liste`" class="menu-liste">
      <template v-for="g in gruplar" :key="g.grup ?? ''">
        <p v-if="g.grup" :id="`${on}-g-${g.grup}`" class="menu-grup-baslik">{{ g.grup }}</p>
        <ul class="menu-oge-listesi" :aria-labelledby="g.grup ? `${on}-g-${g.grup}` : undefined">
          <li v-for="o in g.ogeler" :key="o.yol">
            <RouterLink class="menu-oge" :to="o.yol" :title="daraltilmis ? o.ad : undefined">
              <Simge :ad="o.simge ?? 'daire'" boyut="1.25rem" />
              <span class="menu-metin">{{ o.ad }}</span>
            </RouterLink>
          </li>
        </ul>
      </template>
    </div>
    <Dugme
      class="menu-daralt"
      varyant="hayalet"
      :simge="daraltilmis ? 'ok-sag' : 'ok-sol'"
      :etiket="daraltilmis ? 'Menüyü genişlet' : 'Menüyü daralt'"
      :aria-expanded="!daraltilmis"
      @click="daraltTersle"
    />
  </nav>
</template>

<style scoped>
.yan-menu {
  background: var(--renk-menu-zemin);
  color: var(--renk-menu-metin);
  display: flex;
  flex-direction: column;
}
.menu-liste {
  display: none;
  flex-direction: column;
  padding: var(--aralik-3);
  gap: var(--aralik-2);
}
.mobil-acik .menu-liste {
  display: flex;
}
.menu-grup-baslik {
  margin: var(--aralik-4) var(--aralik-3) var(--aralik-2);
  font-size: var(--yazi-xs);
  font-weight: var(--yazi-agirlik-kalin);
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--renk-menu-baslik);
}
.menu-oge-listesi {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: var(--aralik-1);
}
.menu-oge {
  display: flex;
  align-items: center;
  gap: var(--aralik-4);
  min-height: 2rem;
  padding: var(--aralik-1) var(--aralik-3);
  border-radius: var(--yaricap);
  color: var(--renk-menu-metin);
  text-decoration: none;
}
.menu-oge:hover {
  background: var(--renk-menu-hover);
  color: var(--renk-n-0);
}
/* aktif: dolgu + beyaz kalın metin (renk tek başına değil) */
.menu-oge.router-link-active {
  background: var(--renk-menu-aktif);
  color: var(--renk-n-0);
  font-weight: var(--yazi-agirlik-orta);
}
.menu-oge :deep(.simge) {
  flex: none;
}
.yan-menu :focus-visible {
  outline-color: var(--renk-odak-koyu);
  outline-offset: -2px;
}
.menu-ac,
.menu-daralt {
  border-color: transparent;
  background: transparent;
  color: var(--renk-menu-metin);
  margin: var(--aralik-2) var(--aralik-3);
  align-self: flex-start;
}
.menu-ac:hover:not(:disabled),
.menu-daralt:hover:not(:disabled) {
  background: var(--renk-menu-hover);
  color: var(--renk-n-0);
}
.menu-daralt {
  display: none;
}

@media (min-width: 900px) {
  .yan-menu {
    width: var(--menu-genislik);
    position: sticky;
    top: var(--ust-yukseklik, 3rem);
    height: calc(100dvh - var(--ust-yukseklik, 3rem));
    overflow: hidden;
  }
  .menu-ac {
    display: none;
  }
  /* Liste kendi içinde kayar; daralt düğmesi 768 px gibi alçak ekranda da altta görünür kalır. */
  .menu-liste {
    display: flex;
    flex: 1;
    gap: 0;
    min-height: 0;
    overflow-y: auto;
    scrollbar-width: thin;
    scrollbar-color: var(--renk-menu-hover) transparent;
  }
  .menu-daralt {
    display: inline-flex;
  }
  /* QA BUG-4: 768 px gibi alçak ekranda 17 öğe + 5 grup başlığı sığsın (önce 42 px kaydırma, son öğe alt çubuğun altında kalıyordu). */
  @media (max-height: 800px) {
    .menu-grup-baslik {
      margin: var(--aralik-2) var(--aralik-3) var(--aralik-1);
    }
    .menu-oge {
      min-height: 1.75rem;
    }
  }
  .yan-menu.daraltilmis {
    width: var(--menu-dar);
  }
  /* daraltılmış: metin yalnız ekran okuyucuya görünür */
  .daraltilmis .menu-metin,
  .daraltilmis .menu-grup-baslik {
    position: absolute;
    width: 1px;
    height: 1px;
    overflow: hidden;
    clip-path: inset(50%);
    white-space: nowrap;
  }
  .daraltilmis .menu-oge {
    justify-content: center;
  }
  .daraltilmis .menu-grup-baslik + .menu-oge-listesi {
    border-top: 1px solid var(--renk-menu-hover);
    padding-top: var(--aralik-2);
    margin-top: var(--aralik-2);
  }
  .daraltilmis .menu-daralt {
    align-self: center;
  }
}

@media print {
  .yan-menu {
    display: none;
  }
}
</style>
