<script setup lang="ts">
import AlanHatasi from '@/bilesenler/AlanHatasi.vue'
import { onMounted, reactive, ref } from 'vue'
import { ApiHata } from '@/api/istemci'
import Rozet from '@/bilesenler/Rozet.vue'
import SayfaBasligi from '@/bilesenler/SayfaBasligi.vue'
import VeriDurumu from '@/bilesenler/VeriDurumu.vue'
import {
  IZIN_DURUM_GORUNUMU,
  IZIN_ETIKETLERI,
  type IzinTuru,
} from '@/features/makinist/makinistApi'
import { bugun, tarihBicimle } from '@/lib/tarih'
import { benimApi, type BenimIzin } from './benimApi'
import { rozetYaz } from './rozet'

/** Makinistin gözüyle durum: "Talep" personel dilidir; makinist için "Onay bekliyor". */
const DURUM_ETIKETLERI: Record<string, string> = {
  TALEP: 'Onay bekliyor',
  ONAY: 'Onaylı',
  RED: 'Reddedildi',
}

const izinler = ref<BenimIzin[]>([])
const yukleniyor = ref(true)
const hata = ref('')
const bilgi = ref('')
const mesgul = ref(false)
const alanHatalari = ref<Record<string, string>>({})
const form = reactive({ baslangic: '', bitis: '', tur: 'YILLIK' as IzinTuru })
const enErken = bugun()

async function yukle() {
  yukleniyor.value = true
  hata.value = ''
  try {
    izinler.value = await benimApi.izinler()
  } catch (e) {
    hata.value = e instanceof ApiHata ? e.detail : 'İzinler yüklenemedi.'
  } finally {
    yukleniyor.value = false
  }
}
onMounted(yukle)

async function gonder() {
  if (mesgul.value) return
  hata.value = ''
  bilgi.value = ''
  const h: Record<string, string> = {}
  if (!form.baslangic) h.baslangic = 'Başlangıç tarihi zorunludur.'
  if (!form.bitis) h.bitis = 'Bitiş tarihi zorunludur.'
  else if (form.baslangic && form.bitis < form.baslangic)
    h.bitis = 'Bitiş tarihi başlangıçtan önce olamaz.'
  alanHatalari.value = h
  if (Object.keys(h).length > 0) return
  mesgul.value = true
  try {
    const yeni = await benimApi.izinTalepEt({ ...form })
    izinler.value = [yeni, ...izinler.value]
    form.baslangic = form.bitis = ''
    bilgi.value = 'Talebiniz atamacıya iletildi. Onaylanınca durumu burada görünür.'
    rozetYaz('İzin talebi gönderildi')
  } catch (e) {
    if (e instanceof ApiHata) {
      alanHatalari.value = e.alanlar ?? {}
      hata.value = Object.keys(alanHatalari.value).length > 0 ? '' : e.detail
    } else {
      hata.value = 'Talep gönderilemedi.'
    }
  } finally {
    mesgul.value = false
  }
}
</script>

<template>
  <section>
    <SayfaBasligi baslik="İzin talebi" />
    <p v-if="bilgi" class="bildirim bildirim-bilgi" role="status">{{ bilgi }}</p>
    <p v-if="hata" class="bildirim bildirim-hata" role="alert">{{ hata }}</p>
    <form class="kart" novalidate @submit.prevent="gonder">
      <div class="alan">
        <label for="izin-baslangic">Başlangıç</label>
        <input
          id="izin-baslangic"
          v-model="form.baslangic"
          type="date"
          :min="enErken"
          :aria-invalid="!!alanHatalari.baslangic"
          :aria-describedby="alanHatalari.baslangic ? 'izin-baslangic-hata' : undefined"
        />
        <AlanHatasi id="izin-baslangic-hata" :metin="alanHatalari.baslangic" />
      </div>
      <div class="alan">
        <label for="izin-bitis">Bitiş</label>
        <input
          id="izin-bitis"
          v-model="form.bitis"
          type="date"
          :min="form.baslangic || enErken"
          :aria-invalid="!!alanHatalari.bitis"
          :aria-describedby="alanHatalari.bitis ? 'izin-bitis-hata' : undefined"
        />
        <AlanHatasi id="izin-bitis-hata" :metin="alanHatalari.bitis" />
      </div>
      <div class="alan">
        <label for="izin-tur">Tür</label>
        <select id="izin-tur" v-model="form.tur">
          <option v-for="(etiket, k) in IZIN_ETIKETLERI" :key="k" :value="k">{{ etiket }}</option>
        </select>
      </div>
      <button type="submit" class="btn btn-birincil benim-tam" :disabled="mesgul">
        {{ mesgul ? 'Gönderiliyor…' : 'Talep gönder' }}
      </button>
    </form>

    <h2 class="benim-gun">Taleplerim</h2>
    <VeriDurumu v-if="yukleniyor" durum="yukleniyor" metin="Yükleniyor…" />
    <VeriDurumu v-else-if="izinler.length === 0" durum="bos" metin="Henüz izin talebiniz yok." />
    <ul v-else class="benim-liste">
      <li v-for="i in izinler" :key="i.id" class="kart">
        <strong>{{ tarihBicimle(i.baslangic) }} – {{ tarihBicimle(i.bitis) }}</strong>
        <span class="benim-satir">
          {{ IZIN_ETIKETLERI[i.tur] }} ·
          <Rozet
            :ton="IZIN_DURUM_GORUNUMU[i.durum]?.ton ?? 'notr'"
            :simge="IZIN_DURUM_GORUNUMU[i.durum]?.simge"
            :metin="DURUM_ETIKETLERI[i.durum] ?? i.durum"
          />
        </span>
      </li>
    </ul>
  </section>
</template>
