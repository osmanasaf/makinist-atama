<script setup lang="ts">
import { RouterLink, type RouteLocationRaw } from 'vue-router'
import { kullanici } from '@/composables/oturum'
import { zamanBicimle } from '@/lib/tarih'

/** Yazdırma kabuğu: başlık, filtre özeti, içerik (slot) ve altbilgi. Menüsüz düzen App.vue'de (meta.yazdir). */
function yazdir() {
  window.print()
}

withDefaults(
  defineProps<{
    baslik: string
    /** örn. "Tarih: 12.10.2026" */
    filtre: string
    /** Sunucunun veriyi ürettiği an (istemci saati DEĞİL). */
    uretimZamani: string | null
    kayitSayisi: number
    geri?: RouteLocationRaw
    /** Veri hazır değilken (yükleniyor/hata) boş çıktı basılmasın. */
    yazdirilabilir?: boolean
  }>(),
  { geri: () => ({ name: 'atama' }), yazdirilabilir: true },
)
</script>

<template>
  <section class="yazdir-sayfa">
    <div class="no-print yazdir-araclar">
      <button type="button" class="btn btn-birincil" :disabled="!yazdirilabilir" @click="yazdir">
        Yazdır
      </button>
      <RouterLink class="btn" :to="geri">Geri</RouterLink>
    </div>
    <h1>{{ baslik }}</h1>
    <p class="yazdir-filtre">{{ filtre }}</p>
    <slot />
    <footer class="yazdir-altbilgi">
      Son güncelleme: {{ zamanBicimle(uretimZamani) }} · Yazdıran: {{ kullanici?.adSoyad ?? '' }} ·
      Kayıt sayısı: {{ kayitSayisi }}
    </footer>
  </section>
</template>
