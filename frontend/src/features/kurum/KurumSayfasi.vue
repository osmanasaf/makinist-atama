<script setup lang="ts">
import { RouterLink } from 'vue-router'
import SayfaBasligi from '@/bilesenler/SayfaBasligi.vue'
import KurumAyari from './KurumAyari.vue'
</script>

<template>
  <section>
    <SayfaBasligi baslik="Kurum bilgisi">
      <template #eylemler>
        <RouterLink class="btn" to="/ayarlar">Ayarlara dön</RouterLink>
      </template>
    </SayfaBasligi>
    <KurumAyari />
  </section>
</template>
