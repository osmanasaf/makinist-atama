<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ApiHata } from '@/api/istemci'
import Rozet from '@/bilesenler/Rozet.vue'
import SayfaBasligi from '@/bilesenler/SayfaBasligi.vue'
import VeriDurumu from '@/bilesenler/VeriDurumu.vue'
import { durumOzellikleri, type Ton } from '@/bilesenler/durum'
import type { SimgeAdi } from '@/bilesenler/simgeler'
import type { BelgeDto, BelgeDurumu } from '@/features/belge/belgeApi'
import { tarihBicimle } from '@/lib/tarih'
import { benimApi, type BenimBelgeler } from './benimApi'
import { rozetYaz } from './rozet'

const veri = ref<BenimBelgeler | null>(null)
const yukleniyor = ref(true)
const hata = ref('')

/** Belge durumu → rozet (ton + simge `durum.ts` tek kaynağından, metin belgeye özgü). */
const DURUM_ROZETLERI: Record<BelgeDurumu, { ton: Ton; simge: SimgeAdi; metin: string }> = {
  DOLDU: { ...durumOzellikleri('ENGEL'), metin: 'Süresi doldu' },
  YAKLASIYOR: { ...durumOzellikleri('UYARI'), metin: 'Yaklaşıyor' },
  GECERLI: { ...durumOzellikleri('GECERLI'), metin: 'Geçerli' },
  GIRILMEMIS: { ton: 'notr', simge: 'daire', metin: 'Girilmemiş' },
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
    <SayfaBasligi baslik="Belgelerim" />
    <VeriDurumu v-if="yukleniyor" durum="yukleniyor" metin="Yükleniyor…" />
    <VeriDurumu v-else-if="hata" durum="hata" :metin="hata">
      <template #eylem>
        <button type="button" class="btn" @click="yukle">Yeniden dene</button>
      </template>
    </VeriDurumu>
    <template v-else-if="veri">
      <VeriDurumu v-if="veri.belgeler.length === 0" durum="bos" metin="Kayıtlı belgeniz yok." />
      <ul v-else class="benim-liste">
        <li
          v-for="b in veri.belgeler"
          :key="b.tur + b.ad"
          class="kart"
          :class="{ yaklasiyor: b.durum === 'YAKLASIYOR', doldu: b.durum === 'DOLDU' }"
        >
          <strong>{{ b.ad }}</strong>
          <span class="benim-satir">
            <Rozet v-bind="DURUM_ROZETLERI[b.durum]" />
            <template v-if="b.bitis">
              · {{ tarihBicimle(b.bitis) }} · {{ kalanMetni(b) }}
            </template>
          </span>
          <small v-if="b.durum === 'DOLDU' || b.durum === 'YAKLASIYOR'" class="benim-not">
            Yenileme için atamacınıza ya da eğitim birimine başvurun.
          </small>
        </li>
      </ul>
    </template>
  </section>
</template>
