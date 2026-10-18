<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ApiHata } from '@/api/istemci'
import Rozet from '@/bilesenler/Rozet.vue'
import SayfaBasligi from '@/bilesenler/SayfaBasligi.vue'
import Simge from '@/bilesenler/Simge.vue'
import VeriDurumu from '@/bilesenler/VeriDurumu.vue'
import type { SimgeAdi } from '@/bilesenler/simgeler'
import type { Ton } from '@/bilesenler/durum'
import { tarihBicimle } from '@/lib/tarih'
import { DURUM_ETIKETLERI, TUR_ETIKETLERI } from '@/features/makinist/makinistApi'
import { belgeApi, type BelgeDto, type BelgeDurumu, type MakinistBelgeDto } from './belgeApi'

type Suzgec = 'TUMU' | 'DOLDU' | 'YAKLASIYOR' | 'GIRILMEMIS'

const SUZGECLER: { kod: Suzgec; ad: string }[] = [
  { kod: 'TUMU', ad: 'Tümü' },
  { kod: 'DOLDU', ad: 'Dolmuş' },
  { kod: 'YAKLASIYOR', ad: 'Yaklaşan' },
  { kod: 'GIRILMEMIS', ad: 'Girilmemiş' },
]
/** Belge durumu -> ton + simge: anlam renk, simge ve METİNle birlikte taşınır (WCAG 1.4.1). "Girilmemiş" kesikli kenarlıklı nötr. */
const GORUNUM: Record<BelgeDurumu, { ton: Ton; simge: SimgeAdi }> = {
  DOLDU: { ton: 'engel', simge: 'engel' },
  YAKLASIYOR: { ton: 'uyari', simge: 'uyari' },
  GIRILMEMIS: { ton: 'notr', simge: 'daire' },
  GECERLI: { ton: 'basari', simge: 'basari' },
}

const makinistler = ref<MakinistBelgeDto[]>([])
const hata = ref('')
const yuklendi = ref(false)
const suzgec = ref<Suzgec>('TUMU')
const arama = ref('')

onMounted(async () => {
  try {
    makinistler.value = (await belgeApi.durum()).makinistler
  } catch (e) {
    hata.value = e instanceof ApiHata ? e.detail : 'Belge durumu yüklenemedi.'
  } finally {
    yuklendi.value = true
  }
})

const sayac = (d: BelgeDurumu) => makinistler.value.filter((m) => m.enKotu === d).length

const gorunen = computed(() => {
  const q = arama.value.trim().toLocaleLowerCase('tr-TR')
  return makinistler.value.filter(
    (m) =>
      (suzgec.value === 'TUMU' || m.enKotu === suzgec.value) &&
      (q === '' ||
        m.sicil.toLocaleLowerCase('tr-TR').includes(q) ||
        m.adSoyad.toLocaleLowerCase('tr-TR').includes(q)),
  )
})

const sorunlu = (m: MakinistBelgeDto) => m.belgeler.filter((b) => b.durum !== 'GECERLI')

function kalanMetni(kalan: number | null): string {
  if (kalan === null) return ''
  if (kalan > 0) return `${kalan} gün kaldı`
  if (kalan === 0) return 'bugün son gün'
  return `${-kalan} gün geçti`
}

function belgeMetni(b: BelgeDto): string {
  if (b.durum === 'GIRILMEMIS' || !b.bitis) return `${b.ad} · girilmemiş`
  const parcalar = [b.ad, tarihBicimle(b.bitis), kalanMetni(b.kalanGun)]
  return parcalar.join(' · ') + (b.durum === 'DOLDU' ? ' (DOLDU)' : '')
}
</script>

<template>
  <section class="belge-panosu">
    <SayfaBasligi baslik="Belge durumu" />
    <VeriDurumu v-if="hata" durum="hata" :metin="hata" />
    <VeriDurumu v-else-if="!yuklendi" durum="yukleniyor" metin="Yükleniyor…" />

    <ul class="sayaclar" aria-label="Özet">
      <li class="sayac sayac-DOLDU">
        <Simge :ad="GORUNUM.DOLDU.simge" boyut="1em" /> Dolmuş:
        <strong class="sayisal">{{ sayac('DOLDU') }}</strong>
      </li>
      <li class="sayac sayac-YAKLASIYOR">
        <Simge :ad="GORUNUM.YAKLASIYOR.simge" boyut="1em" /> Yaklaşan:
        <strong class="sayisal">{{ sayac('YAKLASIYOR') }}</strong>
      </li>
      <li class="sayac sayac-GECERLI">
        <Simge :ad="GORUNUM.GECERLI.simge" boyut="1em" /> Geçerli:
        <strong class="sayisal">{{ sayac('GECERLI') }}</strong>
      </li>
    </ul>

    <div class="arac-cubugu no-print">
      <div role="group" aria-label="Durum süzgeci" class="suzgec">
        <button
          v-for="s in SUZGECLER"
          :key="s.kod"
          type="button"
          class="btn"
          :aria-pressed="suzgec === s.kod"
          @click="suzgec = s.kod"
        >
          {{ s.ad }}
        </button>
      </div>
      <div class="alan">
        <label for="belge-arama">Sicil veya ad ara</label>
        <input id="belge-arama" v-model="arama" type="search" autocomplete="off" />
      </div>
    </div>

    <div class="tablo-kap">
      <table class="tablo" aria-label="Belge durumu">
        <thead>
          <tr>
            <th>Sicil</th>
            <th>Ad soyad</th>
            <th>Tür</th>
            <th>Sorunlu belgeler</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="m in gorunen" :key="m.makinistId">
            <td>{{ m.sicil }}</td>
            <td>
              <RouterLink :to="{ name: 'makinist-kart', params: { id: m.makinistId } }">
                {{ m.adSoyad }}
              </RouterLink>
              <small v-if="m.durum !== 'AKTIF'"> ({{ DURUM_ETIKETLERI[m.durum] }})</small>
            </td>
            <td>{{ TUR_ETIKETLERI[m.tur ?? 'MAKINIST'] }}</td>
            <td>
              <ul v-if="sorunlu(m).length > 0" class="rozetler">
                <li v-for="b in sorunlu(m)" :key="b.tur + b.ad">
                  <Rozet
                    :class="{ 'rozet-girilmemis': b.durum === 'GIRILMEMIS' }"
                    :ton="GORUNUM[b.durum].ton"
                    :simge="GORUNUM[b.durum].simge"
                    :metin="belgeMetni(b)"
                  />
                </li>
              </ul>
              <Rozet
                v-else
                ton="basari"
                :simge="GORUNUM.GECERLI.simge"
                metin="Tüm belgeler geçerli"
              />
              <details class="ayrinti">
                <summary>Tüm belgeler</summary>
                <ul>
                  <li v-for="b in m.belgeler" :key="b.tur + b.ad">
                    <Simge :ad="GORUNUM[b.durum].simge" boyut="1em" /> {{ belgeMetni(b) }}
                  </li>
                </ul>
              </details>
            </td>
          </tr>
          <tr v-if="yuklendi && !hata && gorunen.length === 0">
            <td colspan="4"><VeriDurumu durum="bos" metin="Kayıt bulunamadı." /></td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>

<style scoped>
.sayaclar {
  display: flex;
  flex-wrap: wrap;
  gap: var(--aralik-4);
  list-style: none;
  padding: 0;
  margin: 0 0 var(--aralik-4);
}
.sayac {
  display: inline-flex;
  align-items: center;
  gap: var(--aralik-2);
  padding: var(--aralik-2) var(--aralik-4);
  border: 2px solid currentcolor;
  border-radius: var(--yaricap);
  background: var(--renk-yuzey);
}
.rozetler {
  display: flex;
  flex-wrap: wrap;
  gap: var(--aralik-2);
  list-style: none;
  padding: 0;
  margin: 0;
}
.sayac-DOLDU {
  color: var(--renk-engel-metin);
  background: var(--renk-hata-zemin);
  font-weight: var(--yazi-agirlik-orta);
}
.sayac-YAKLASIYOR {
  color: var(--renk-uyari-metin);
  background: var(--renk-uyari-zemin);
}
.rozet-girilmemis {
  border-style: dashed;
}
.sayac-GECERLI {
  color: var(--renk-basari-metin);
}
.ayrinti {
  margin-top: var(--aralik-2);
  font-size: var(--yazi-md);
}
.ayrinti ul {
  margin: var(--aralik-2) 0 0;
  padding-left: var(--aralik-5);
}
@media print {
  .no-print {
    display: none;
  }
  .sayac,
  .rozet {
    background: none;
    color: CanvasText;
    border-color: CanvasText;
  }
  .ayrinti {
    display: none;
  }
}
</style>
