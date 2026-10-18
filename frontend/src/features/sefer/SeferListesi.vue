<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { ApiHata } from '@/api/istemci'
import Rozet from '@/bilesenler/Rozet.vue'
import SayfaBasligi from '@/bilesenler/SayfaBasligi.vue'
import VeriDurumu from '@/bilesenler/VeriDurumu.vue'
import { bugun, zamanBicimle } from '@/lib/tarih'
import { trenAdi } from '@/lib/trenAdi'
import { tanimApi, type TanimDto } from '@/features/tanim/tanimApi'
import SeferFormu from './SeferFormu.vue'
import { seferApi, type Sefer } from './seferApi'

const gun = ref(bugun())
const liste = ref<Sefer[]>([])
const hata = ref('')
const yukleniyor = ref(true)
const garlar = ref<TanimDto[]>([])
const hatlar = ref<TanimDto[]>([])
const aracTipleri = ref<TanimDto[]>([])
/** undefined = form kapalı, null = yeni sefer, Sefer = düzenleme */
const form = ref<Sefer | null | undefined>(undefined)

async function yukle() {
  hata.value = ''
  if (!gun.value) {
    yukleniyor.value = false
    return
  }
  yukleniyor.value = true
  try {
    liste.value = await seferApi.liste(gun.value)
  } catch (e) {
    liste.value = []
    hata.value = e instanceof ApiHata ? e.detail : 'Seferler yüklenemedi.'
  } finally {
    yukleniyor.value = false
  }
}

async function kaydedildi() {
  form.value = undefined
  await yukle()
}

onMounted(async () => {
  try {
    ;[garlar.value, hatlar.value, aracTipleri.value] = await Promise.all([
      tanimApi.liste('GAR'),
      tanimApi.liste('HAT'),
      tanimApi.liste('ARAC_TIPI'),
    ])
  } catch (e) {
    hata.value = e instanceof ApiHata ? e.detail : 'Tanımlar yüklenemedi.'
  }
  await yukle()
})
</script>

<template>
  <section>
    <SayfaBasligi baslik="Seferler">
      <template #eylemler>
        <RouterLink class="btn" :to="{ name: 'sefer-yukle' }">Excel ile yükle</RouterLink>
        <button type="button" class="btn btn-birincil" @click="form = null">Sefer ekle</button>
      </template>
    </SayfaBasligi>
    <div class="arac-cubugu">
      <div class="alan">
        <label for="gun">Gün</label>
        <input id="gun" v-model="gun" type="date" @change="yukle" />
      </div>
    </div>
    <VeriDurumu v-if="hata" durum="hata" :metin="hata">
      <template #eylem>
        <button type="button" class="btn" @click="yukle">Yeniden dene</button>
      </template>
    </VeriDurumu>
    <SeferFormu
      v-if="form !== undefined"
      :key="form?.id ?? 'yeni'"
      :sefer="form"
      :gun="gun"
      :garlar="garlar"
      :hatlar="hatlar"
      :arac-tipleri="aracTipleri"
      @kaydedildi="kaydedildi"
      @iptal="form = undefined"
    />
    <div class="tablo-kap">
      <table class="tablo" aria-label="Seferler">
        <thead>
          <tr>
            <th>Tren no</th>
            <th>Kalkış gar</th>
            <th>Kalkış</th>
            <th>Varış gar</th>
            <th>Varış</th>
            <th>Araç tipi</th>
            <th>Hat</th>
            <th class="sayisal">Km</th>
            <th>Durum</th>
            <th><span class="gorunmez">İşlem</span></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="s in liste" :key="s.id">
            <td>{{ trenAdi(s) }}</td>
            <td>{{ s.kalkisGar }}</td>
            <td>
              {{ zamanBicimle(s.guncelKalkis) }}
              <small v-if="s.kalkisGecikmeDk">(+{{ s.kalkisGecikmeDk }} dk)</small>
            </td>
            <td>{{ s.varisGar }}</td>
            <td>
              {{ zamanBicimle(s.guncelVaris) }}
              <small v-if="s.varisGecikmeDk">(+{{ s.varisGecikmeDk }} dk)</small>
            </td>
            <td>{{ s.aracTipi }}</td>
            <td>{{ s.hat }}</td>
            <td class="sayisal">{{ s.km ?? '' }}</td>
            <td>
              <Rozet v-if="s.durum === 'IPTAL'" ton="engel" simge="iptal" metin="İptal" />
              <Rozet v-else ton="notr" metin="Planlı" />
            </td>
            <td>
              <button
                type="button"
                class="btn"
                :aria-label="`Düzenle: ${trenAdi(s)} ${zamanBicimle(s.guncelKalkis)}`"
                @click="form = s"
              >
                Düzenle
              </button>
            </td>
          </tr>
          <tr v-if="yukleniyor">
            <td colspan="10"><VeriDurumu durum="yukleniyor" metin="Yükleniyor…" /></td>
          </tr>
          <tr v-else-if="liste.length === 0 && !hata">
            <td colspan="10"><VeriDurumu durum="bos" metin="Bu gün için sefer yok." /></td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>
