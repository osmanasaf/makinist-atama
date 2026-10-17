<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ApiHata } from '@/api/istemci'
import type { BelgeDto, BelgeDurumu } from '@/features/belge/belgeApi'
import { tarihBicimle } from '@/lib/tarih'
import { benimApi, type BenimBelgeler } from './benimApi'
import { rozetYaz } from './rozet'

const veri = ref<BenimBelgeler | null>(null)
const yukleniyor = ref(true)
const hata = ref('')

const DURUM_ETIKETLERI: Record<BelgeDurumu, string> = {
  DOLDU: '⛔ Süresi doldu',
  YAKLASIYOR: '⚠ Yaklaşıyor',
  GECERLI: '✓ Geçerli',
  GIRILMEMIS: '— Girilmemiş',
}

async function yukle() {
  yukleniyor.value = true
  hata.value = ''
  try {
    veri.value = await benimApi.belgeler()
    const sorunlu = veri.value.belgeler.filter(
      (b) => b.durum === 'DOLDU' || b.durum === 'YAKLASIYOR',
    ).length
    rozetYaz(
      sorunlu > 0 ? `${sorunlu} belgenizin süresi dolmuş ya da yaklaşıyor` : 'Belgeleriniz geçerli',
    )
  } catch (e) {
    hata.value = e instanceof ApiHata ? e.detail : 'Belgeler yüklenemedi.'
  } finally {
    yukleniyor.value = false
  }
}
onMounted(yukle)

/** Kalan gün metni: 0 = bugün son gün; negatif = geçen gün. */
function kalanMetni(b: BelgeDto): string {
  if (b.kalanGun === null) return ''
  if (b.kalanGun === 0) return 'Bugün son gün'
  return b.kalanGun > 0 ? `${b.kalanGun} gün kaldı` : `${-b.kalanGun} gün geçti`
}
</script>

<template>
  <section>
    <h1>Belgelerim</h1>
    <p v-if="yukleniyor" role="status">Yükleniyor…</p>
    <div v-else-if="hata" class="bildirim bildirim-hata" role="alert">
      <p>{{ hata }}</p>
      <button type="button" class="btn" @click="yukle">Yeniden dene</button>
    </div>
    <template v-else-if="veri">
      <p v-if="veri.belgeler.length === 0" class="benim-bos">Kayıtlı belgeniz yok.</p>
      <ul v-else class="benim-liste">
        <li
          v-for="b in veri.belgeler"
          :key="b.tur + b.ad"
          :class="{ yaklasiyor: b.durum === 'YAKLASIYOR', doldu: b.durum === 'DOLDU' }"
        >
          <strong>{{ b.ad }}</strong>
          <br />
          {{ DURUM_ETIKETLERI[b.durum] }}
          <template v-if="b.bitis"> · {{ tarihBicimle(b.bitis) }} · {{ kalanMetni(b) }} </template>
          <template v-if="b.durum === 'DOLDU' || b.durum === 'YAKLASIYOR'">
            <br />
            <small>Yenileme için atamacınıza ya da eğitim birimine başvurun.</small>
          </template>
        </li>
      </ul>
    </template>
  </section>
</template>
