<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { ApiHata } from '@/api/istemci'
import { bugun, zamanBicimle } from '@/lib/tarih'
import { trenAdi } from '@/lib/trenAdi'
import { tanimApi, type TanimDto } from '@/features/tanim/tanimApi'
import SeferFormu from './SeferFormu.vue'
import { seferApi, type Sefer } from './seferApi'

const gun = ref(bugun())
const liste = ref<Sefer[]>([])
const hata = ref('')
const garlar = ref<TanimDto[]>([])
const hatlar = ref<TanimDto[]>([])
const aracTipleri = ref<TanimDto[]>([])
/** undefined = form kapalı, null = yeni sefer, Sefer = düzenleme */
const form = ref<Sefer | null | undefined>(undefined)

async function yukle() {
  hata.value = ''
  if (!gun.value) return
  try {
    liste.value = await seferApi.liste(gun.value)
  } catch (e) {
    liste.value = []
    hata.value = e instanceof ApiHata ? e.detail : 'Seferler yüklenemedi.'
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
    <div class="sayfa-baslik">
      <h1>Seferler</h1>
      <div class="satir-ic">
        <RouterLink class="btn" :to="{ name: 'sefer-yukle' }">Excel ile yükle</RouterLink>
        <button type="button" class="btn btn-birincil" @click="form = null">Sefer ekle</button>
      </div>
    </div>
    <div class="arac-cubugu">
      <div class="alan">
        <label for="gun">Gün</label>
        <input id="gun" v-model="gun" type="date" @change="yukle" />
      </div>
    </div>
    <p v-if="hata" class="bildirim bildirim-hata" role="alert">{{ hata }}</p>
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
      <table class="tablo">
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
            <th></th>
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
            <td>{{ s.durum === 'IPTAL' ? 'İptal' : 'Planlı' }}</td>
            <td><button type="button" class="btn" @click="form = s">Düzenle</button></td>
          </tr>
          <tr v-if="liste.length === 0">
            <td colspan="10" class="bos-durum">Bu gün için sefer yok.</td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>
