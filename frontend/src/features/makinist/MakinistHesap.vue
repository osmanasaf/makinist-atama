<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ApiHata } from '@/api/istemci'
import Rozet from '@/bilesenler/Rozet.vue'
import { makinistApi, type HesapDurumu, type MakinistDurumu } from './makinistApi'

const props = defineProps<{ makinistId: number; durum: MakinistDurumu }>()

const hesap = ref<HesapDurumu | null>(null)
/** Geçici parola yalnız açma / sıfırlama yanıtından sonra gösterilir; sayfa yenilenince (bileşen yeniden kurulunca) bir daha çıkmaz. */
const geciciParola = ref('')
const hata = ref('')
/** Okuma hatası kritik değil (kart kullanılabilir kalır): uyarı değil durum metni. */
const yukHatasi = ref('')
const bilgi = ref('')
const mesgul = ref(false)

async function yukle() {
  try {
    hesap.value = await makinistApi.hesap(props.makinistId)
  } catch (e) {
    yukHatasi.value = e instanceof ApiHata ? e.detail : 'Hesap bilgisi alınamadı.'
  }
}
onMounted(yukle)

async function calistir(islem: () => Promise<void>) {
  if (mesgul.value) return
  mesgul.value = true
  hata.value = ''
  bilgi.value = ''
  try {
    await islem()
  } catch (e) {
    hata.value = e instanceof ApiHata ? e.detail : 'İşlem başarısız oldu.'
  } finally {
    mesgul.value = false
  }
}

const hesapAc = () =>
  calistir(async () => {
    const y = await makinistApi.hesapAc(props.makinistId)
    hesap.value = y.hesap
    geciciParola.value = y.geciciParola
  })

const parolaSifirla = () =>
  calistir(async () => {
    const y = await makinistApi.hesapParolaSifirla(props.makinistId)
    hesap.value = y.hesap
    geciciParola.value = y.geciciParola
  })

const kilidiAc = () =>
  calistir(async () => {
    hesap.value = await makinistApi.hesapKilitAc(props.makinistId)
    bilgi.value = 'Kilit açıldı.'
  })

async function kopyala() {
  try {
    await navigator.clipboard.writeText(geciciParola.value)
    bilgi.value = 'Geçici parola panoya kopyalandı.'
  } catch {
    bilgi.value = 'Kopyalanamadı: parolayı elle yazın.'
  }
}

const pasif = () => props.durum === 'PASIF'
</script>

<template>
  <section class="kart hesap-kutusu" aria-labelledby="hesap-baslik">
    <h2 id="hesap-baslik">Hesap (telefon girişi)</h2>
    <p v-if="hata" class="bildirim bildirim-hata" role="alert">{{ hata }}</p>
    <p v-if="yukHatasi" class="bildirim bildirim-uyari" role="status">{{ yukHatasi }}</p>
    <p v-if="bilgi" class="bildirim bildirim-bilgi" role="status">{{ bilgi }}</p>

    <div v-if="geciciParola" class="bildirim bildirim-uyari" role="alert">
      <div>
        <p>
          <strong>Geçici parola (bir kez gösterilir):</strong>
          <code data-test="gecici-parola">{{ geciciParola }}</code>
        </p>
        <p class="soluk">
          Personel ilk girişte kendi parolasını belirler. Bu parola kaydedilmez; kaybolursa “Parola
          sıfırla” ile yenisi verilir. Hesap oturumuna bağlı işlemler imza değildir; asıl tebliğ
          yolu panodaki listedir.
        </p>
        <button type="button" class="btn" @click="kopyala">Kopyala</button>
      </div>
    </div>

    <template v-if="hesap">
      <p v-if="!hesap.var">Bu personelin henüz hesabı yok.</p>
      <p v-else>
        Giriş adı (sicil): <strong>{{ hesap.girisAdi }}</strong>
        <span v-if="hesap.parolaDegistirmeli"> · ilk giriş bekleniyor (geçici parola)</span>
        <span v-if="hesap.kilitli">
          ·
          <Rozet ton="uyari" simge="uyari" metin="hesap kilitli (çok fazla hatalı deneme)" />
        </span>
        <span v-if="!hesap.aktif"> · hesap pasif</span>
      </p>
      <div class="eylemler">
        <button
          v-if="!hesap.var"
          type="button"
          class="btn btn-birincil"
          :disabled="mesgul || pasif()"
          @click="hesapAc"
        >
          Hesap aç
        </button>
        <template v-else>
          <button type="button" class="btn" :disabled="mesgul || pasif()" @click="parolaSifirla">
            Parola sıfırla
          </button>
          <button type="button" class="btn" :disabled="mesgul || !hesap.kilitli" @click="kilidiAc">
            Kilidi aç
          </button>
        </template>
      </div>
      <p v-if="pasif()" class="soluk">
        Pasif personel için hesap açılamaz ya da parolası sıfırlanamaz.
      </p>
    </template>
  </section>
</template>

<style scoped>
.hesap-kutusu {
  margin-top: var(--aralik-5);
}
</style>
