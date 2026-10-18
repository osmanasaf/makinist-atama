<script setup lang="ts">
import AlanHatasi from '@/bilesenler/AlanHatasi.vue'
import { reactive, ref } from 'vue'
import { ilkHataliyaOdaklan } from '@/lib/odak'
import { useRouter } from 'vue-router'
import { ApiHata } from '@/api/istemci'
import SayfaBasligi from '@/bilesenler/SayfaBasligi.vue'
import { kullanici, oturumuTemizle } from '@/composables/oturum'
import { benimApi } from './benimApi'

const router = useRouter()
const form = reactive({ eski: '', yeni: '', tekrar: '' })
const alanHatalari = ref<Record<string, string>>({})
const hata = ref('')
const mesgul = ref(false)
const formKok = ref<HTMLElement | null>(null)

async function gonder() {
  if (mesgul.value) return
  hata.value = ''
  const h: Record<string, string> = {}
  if (!form.eski) h.eskiParola = 'Eski parolanızı yazın.'
  if (form.yeni.length < 10) h.yeniParola = 'Yeni parola en az 10 karakter olmalı.'
  else if (form.yeni === form.eski) h.yeniParola = 'Yeni parola eskisiyle aynı olamaz.'
  if (form.tekrar !== form.yeni) h.tekrar = 'Parolalar aynı değil.'
  alanHatalari.value = h
  if (Object.keys(h).length > 0) return ilkHataliyaOdaklan(formKok.value)
  mesgul.value = true
  try {
    await benimApi.parolaDegistir(form.eski, form.yeni)
    // sunucu parola izi değiştiği için oturumu düşürür: yeni parolayla yeniden giriş
    oturumuTemizle()
    await router.replace({ name: 'giris', query: { parola: 'degisti' } })
  } catch (e) {
    if (e instanceof ApiHata) {
      alanHatalari.value = e.alanlar ?? {}
      hata.value = Object.keys(alanHatalari.value).length > 0 ? '' : e.detail
    } else {
      hata.value = 'Parola değiştirilemedi.'
    }
    // yalnız eski parola yanlışsa alan boşalır; başka bir hatada (yeni parola kuralı vb.) yeniden yazdırılmaz
    if (alanHatalari.value.eskiParola) form.eski = ''
    await ilkHataliyaOdaklan(formKok.value)
  } finally {
    mesgul.value = false
  }
}
</script>

<template>
  <section>
    <SayfaBasligi baslik="Parolam" />
    <p v-if="kullanici?.parolaDegistirmeli" class="bildirim bildirim-uyari" role="status">
      Atamacının verdiği geçici parolayı kullanıyorsunuz. Devam etmek için kendi parolanızı
      belirleyin.
    </p>
    <p v-if="hata" class="bildirim bildirim-hata" role="alert">{{ hata }}</p>
    <form ref="formKok" class="kart" novalidate @submit.prevent="gonder">
      <div class="alan">
        <label for="eski-parola">Eski (geçici) parola</label>
        <input
          id="eski-parola"
          v-model="form.eski"
          type="password"
          autocomplete="current-password"
          :aria-invalid="!!alanHatalari.eskiParola"
          :aria-describedby="alanHatalari.eskiParola ? 'eski-parola-hata' : undefined"
        />
        <AlanHatasi id="eski-parola-hata" :metin="alanHatalari.eskiParola" />
      </div>
      <div class="alan">
        <label for="yeni-parola">Yeni parola (en az 10 karakter)</label>
        <input
          id="yeni-parola"
          v-model="form.yeni"
          type="password"
          autocomplete="new-password"
          :aria-invalid="!!alanHatalari.yeniParola"
          :aria-describedby="alanHatalari.yeniParola ? 'yeni-parola-hata' : undefined"
        />
        <AlanHatasi id="yeni-parola-hata" :metin="alanHatalari.yeniParola" />
      </div>
      <div class="alan">
        <label for="tekrar-parola">Yeni parola (tekrar)</label>
        <input
          id="tekrar-parola"
          v-model="form.tekrar"
          type="password"
          autocomplete="new-password"
          :aria-invalid="!!alanHatalari.tekrar"
          :aria-describedby="alanHatalari.tekrar ? 'tekrar-parola-hata' : undefined"
        />
        <AlanHatasi id="tekrar-parola-hata" :metin="alanHatalari.tekrar" />
      </div>
      <p class="soluk">
        Parolanız sicilinizi içeremez. Değiştirince yeni parolanızla yeniden giriş yaparsınız.
      </p>
      <button type="submit" class="btn btn-birincil benim-tam" :disabled="mesgul">
        {{ mesgul ? 'Kaydediliyor…' : 'Parolayı değiştir' }}
      </button>
    </form>
  </section>
</template>
