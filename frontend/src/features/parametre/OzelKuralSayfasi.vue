<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ApiHata } from '@/api/istemci'
import { yoneticiMi } from '@/composables/oturum'
import { tarihBicimle } from '@/lib/tarih'
import KuralKurucuDialog from './KuralKurucuDialog.vue'
import {
  kuralIstegi,
  ozelKuralApi,
  SIDDET_ETIKETLERI,
  type OlcuDto,
  type OzelKuralDto,
} from './ozelKuralApi'

/** "Özel kurallar" sayfası: herkes okur; yalnız yönetici ekler, düzenler, kapatır (silme yok). */
const kurallar = ref<OzelKuralDto[]>([])
const katalog = ref<OlcuDto[]>([])
const yuklemeHatasi = ref('')
/** Açık diyalog: `kural` null ise yeni kural. */
const diyalog = ref<{ kural: OzelKuralDto | null } | null>(null)

const sutunSayisi = computed(() => (yoneticiMi.value ? 7 : 6))

async function yukle() {
  yuklemeHatasi.value = ''
  try {
    const [l, k] = await Promise.all([ozelKuralApi.liste(), ozelKuralApi.katalog()])
    kurallar.value = l
    katalog.value = k
  } catch (e) {
    yuklemeHatasi.value = e instanceof ApiHata ? e.detail : 'Özel kurallar yüklenemedi.'
  }
}

onMounted(yukle)

function kaydedildi(k: OzelKuralDto) {
  const i = kurallar.value.findIndex((x) => x.id === k.id)
  if (i >= 0) kurallar.value[i] = k
  else kurallar.value.push(k)
}

const gecerlilik = (k: OzelKuralDto) =>
  `${tarihBicimle(k.gecerlilikBas)} – ${k.gecerlilikBit ? tarihBicimle(k.gecerlilikBit) : 'süresiz'}`

// --- Kapatma: form olmayan küçük alan, PUT ile şiddet KAPALI ---
const kapatId = ref<number | null>(null)
const kapatNeden = ref('')
const kapatMesgul = ref(false)
const kapatHata = ref('')

function kapatAc(k: OzelKuralDto) {
  kapatId.value = k.id
  kapatNeden.value = ''
  kapatHata.value = ''
}

async function kapat() {
  const k = kurallar.value.find((x) => x.id === kapatId.value)
  if (!k || kapatMesgul.value || kapatNeden.value.trim() === '') return
  kapatMesgul.value = true
  kapatHata.value = ''
  try {
    const g = await ozelKuralApi.guncelle(k.id, {
      ...kuralIstegi(k),
      siddet: 'KAPALI',
      versiyon: k.versiyon,
      neden: kapatNeden.value.trim(),
    })
    kaydedildi(g)
    kapatId.value = null
  } catch (e) {
    kapatHata.value = e instanceof ApiHata ? e.detail : 'Kural kapatılamadı.'
    // Sürüm çakıştı: güncel sürümle tekrar denenebilsin.
    if (e instanceof ApiHata && e.cakismaMi) await yukle()
  } finally {
    kapatMesgul.value = false
  }
}
</script>

<template>
  <section>
    <div class="sayfa-baslik">
      <h1 id="ozel-kural-baslik">Özel kurallar</h1>
    </div>
    <p class="soluk">
      Ayarlar sayfasındaki kurallarla bu sayfadaki kuralların hepsine birlikte uyulur. Kapatılan
      kural silinmez, yalnız uygulanmaz.
    </p>
    <p class="soluk">
      Yeni ya da gevşetilen bir kural daha önce yapılmış atamaları kırmızı yapabilir; kaydetmeden
      önce "Dene" ile bakın. Kırmızı olan bir atamayı masada "Yerine ata" ile değiştirebilir ya da
      kaldırabilirsiniz.
    </p>
    <p v-if="yuklemeHatasi" class="bildirim bildirim-hata" role="alert">
      {{ yuklemeHatasi }}
    </p>
    <p v-if="yoneticiMi && kurallar.length > 0">
      <button
        type="button"
        class="btn btn-birincil"
        :disabled="katalog.length === 0"
        @click="diyalog = { kural: null }"
      >
        Yeni kural
      </button>
    </p>

    <div class="tablo-kap">
      <table class="tablo" aria-labelledby="ozel-kural-baslik">
        <thead>
          <tr>
            <th>Kod</th>
            <th>Kural adı</th>
            <th>Kural</th>
            <th>Şiddet</th>
            <th>Geçerlilik</th>
            <th>Gerekçe</th>
            <th v-if="yoneticiMi">Eylemler</th>
          </tr>
        </thead>
        <tbody>
          <template v-for="k in kurallar" :key="k.id">
            <tr>
              <td>{{ k.kod }}</td>
              <td>{{ k.ad }}</td>
              <td>{{ k.ozet }}</td>
              <td>{{ SIDDET_ETIKETLERI[k.siddet] }}</td>
              <td>{{ gecerlilik(k) }}</td>
              <td>{{ k.gerekce }}{{ k.kaynak ? ` · ${k.kaynak}` : '' }}</td>
              <td v-if="yoneticiMi" class="eylem-hucre">
                <button
                  type="button"
                  class="btn"
                  :aria-label="`${k.kod} kuralını düzenle`"
                  @click="diyalog = { kural: k }"
                >
                  Düzenle
                </button>
                <button
                  v-if="k.siddet !== 'KAPALI'"
                  type="button"
                  class="btn"
                  :aria-label="`${k.kod} kuralını kapat`"
                  @click="kapatAc(k)"
                >
                  Kapat
                </button>
              </td>
            </tr>
            <tr v-if="kapatId === k.id">
              <td :colspan="sutunSayisi">
                <div class="kapat-alani">
                  <label :for="`ozel-kapat-neden-${k.id}`"
                    >{{ k.kod }} kapatma nedeni (zorunlu)</label
                  >
                  <input
                    :id="`ozel-kapat-neden-${k.id}`"
                    v-model="kapatNeden"
                    maxlength="500"
                    @keydown.enter.prevent="kapat"
                  />
                  <button
                    type="button"
                    class="btn btn-tehlike"
                    :disabled="kapatMesgul || kapatNeden.trim() === ''"
                    @click="kapat"
                  >
                    Kapatmayı onayla
                  </button>
                  <button type="button" class="btn" :disabled="kapatMesgul" @click="kapatId = null">
                    Vazgeç
                  </button>
                  <p v-if="kapatHata" class="alan-hata" role="alert">
                    {{ kapatHata }}
                  </p>
                </div>
              </td>
            </tr>
          </template>
          <tr v-if="kurallar.length === 0">
            <td :colspan="sutunSayisi" class="bos-durum">
              Henüz özel kural yok.
              <button
                v-if="yoneticiMi"
                type="button"
                class="btn btn-birincil"
                :disabled="katalog.length === 0"
                @click="diyalog = { kural: null }"
              >
                Yeni kural
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <KuralKurucuDialog
      v-if="diyalog"
      :katalog="katalog"
      :kural="diyalog.kural"
      @kaydedildi="kaydedildi"
      @kapat="diyalog = null"
    />
  </section>
</template>

<style scoped>
.eylem-hucre {
  white-space: nowrap;
}
.eylem-hucre .btn + .btn {
  margin-left: 0.5rem;
}
.kapat-alani {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.5rem;
}
.kapat-alani label {
  font-weight: 600;
  font-size: 0.9rem;
}
.kapat-alani input {
  flex: 1;
  min-width: 14rem;
}
.kapat-alani p {
  flex-basis: 100%;
}
</style>
