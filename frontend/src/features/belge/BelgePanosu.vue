<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ApiHata } from '@/api/istemci'
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
const SIMGELER: Record<BelgeDurumu, string> = {
  DOLDU: '✖',
  YAKLASIYOR: '⚠',
  GIRILMEMIS: '?',
  GECERLI: '✔',
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
    <div class="sayfa-baslik"><h1>Belge durumu</h1></div>
    <p v-if="hata" class="bildirim bildirim-hata" role="alert">{{ hata }}</p>

    <ul class="sayaclar" aria-label="Özet">
      <li class="sayac sayac-DOLDU">
        <span aria-hidden="true">{{ SIMGELER.DOLDU }}</span> Dolmuş:
        <strong>{{ sayac('DOLDU') }}</strong>
      </li>
      <li class="sayac sayac-YAKLASIYOR">
        <span aria-hidden="true">{{ SIMGELER.YAKLASIYOR }}</span> Yaklaşan:
        <strong>{{ sayac('YAKLASIYOR') }}</strong>
      </li>
      <li class="sayac sayac-GECERLI">
        <span aria-hidden="true">{{ SIMGELER.GECERLI }}</span> Geçerli:
        <strong>{{ sayac('GECERLI') }}</strong>
      </li>
    </ul>

    <div class="arac-cubugu no-print">
      <div role="group" aria-label="Durum süzgeci" class="suzgec">
        <button
          v-for="s in SUZGECLER"
          :key="s.kod"
          type="button"
          class="btn"
          :class="{ 'btn-birincil': suzgec === s.kod }"
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
      <table class="tablo">
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
                <li
                  v-for="b in sorunlu(m)"
                  :key="b.tur + b.ad"
                  class="rozet"
                  :class="`rozet-${b.durum}`"
                >
                  <span aria-hidden="true">{{ SIMGELER[b.durum] }}</span> {{ belgeMetni(b) }}
                </li>
              </ul>
              <span v-else class="rozet rozet-GECERLI"
                ><span aria-hidden="true">{{ SIMGELER.GECERLI }}</span> Tüm belgeler geçerli</span
              >
              <details class="ayrinti">
                <summary>Tüm belgeler</summary>
                <ul>
                  <li v-for="b in m.belgeler" :key="b.tur + b.ad">
                    <span aria-hidden="true">{{ SIMGELER[b.durum] }}</span> {{ belgeMetni(b) }}
                  </li>
                </ul>
              </details>
            </td>
          </tr>
          <tr v-if="yuklendi && gorunen.length === 0">
            <td colspan="4" class="bos-durum">Kayıt bulunamadı.</td>
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
  gap: 0.75rem;
  list-style: none;
  padding: 0;
  margin: 0 0 var(--bosluk);
}
.sayac {
  padding: 0.4rem 0.9rem;
  border: 2px solid currentcolor;
  border-radius: var(--yaricap);
  background: var(--renk-yuzey);
}
.suzgec {
  display: flex;
  flex-wrap: wrap;
  gap: 0.25rem;
}
.rozetler {
  display: flex;
  flex-wrap: wrap;
  gap: 0.35rem;
  list-style: none;
  padding: 0;
  margin: 0;
}
.rozet {
  display: inline-block;
  padding: 0.1rem 0.55rem;
  border: 1px solid currentcolor;
  border-radius: 999px;
  font-size: 0.88rem;
}
.sayac-DOLDU,
.rozet-DOLDU {
  color: #b3261e;
  background: var(--renk-hata-zemin);
  font-weight: 600;
}
.sayac-YAKLASIYOR,
.rozet-YAKLASIYOR {
  color: #7a5200;
  background: var(--renk-uyari-zemin);
}
.rozet-GIRILMEMIS {
  color: #4a4f5c;
  border-style: dashed;
}
.sayac-GECERLI,
.rozet-GECERLI {
  color: #1b6e2f;
}
.ayrinti {
  margin-top: 0.35rem;
  font-size: 0.9rem;
}
.ayrinti ul {
  margin: 0.25rem 0 0;
  padding-left: 1.1rem;
}
@media print {
  .no-print {
    display: none;
  }
  .sayac,
  .rozet {
    background: none;
    color: #000;
    border-color: #000;
  }
  .ayrinti {
    display: none;
  }
}
</style>
