<script setup lang="ts">
import AlanHatasi from '@/bilesenler/AlanHatasi.vue'
import { computed, nextTick, onMounted, ref } from 'vue'
import { ApiHata } from '@/api/istemci'
import Dugme from '@/bilesenler/Dugme.vue'
import type { Ton } from '@/bilesenler/durum'
import Rozet from '@/bilesenler/Rozet.vue'
import SayfaBasligi from '@/bilesenler/SayfaBasligi.vue'
import type { SimgeAdi } from '@/bilesenler/simgeler'
import VeriDurumu from '@/bilesenler/VeriDurumu.vue'
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
const yukleniyor = ref(true)
/** Açık diyalog: `kural` null ise yeni kural. */
const diyalog = ref<{ kural: OzelKuralDto | null } | null>(null)

const sutunSayisi = computed(() => (yoneticiMi.value ? 7 : 6))

/** Şiddet rozeti: ton + simge + metin (renk tek başına anlam taşımaz). */
const SIDDET_GORUNUMU: Record<string, { ton: Ton; simge: SimgeAdi }> = {
  ENGEL: { ton: 'engel', simge: 'engel' },
  UYARI: { ton: 'uyari', simge: 'uyari' },
  KAPALI: { ton: 'notr', simge: 'daire' },
}

async function yukle() {
  yuklemeHatasi.value = ''
  yukleniyor.value = true
  try {
    const [l, k] = await Promise.all([ozelKuralApi.liste(), ozelKuralApi.katalog()])
    kurallar.value = l
    katalog.value = k
  } catch (e) {
    yuklemeHatasi.value = e instanceof ApiHata ? e.detail : 'Özel kurallar yüklenemedi.'
  } finally {
    yukleniyor.value = false
  }
}

onMounted(yukle)

/** Kayıt/güncelleme sonrası görünür ve duyurulan bildirim (QA BUG-3; diyalog kapanınca odak düğmeye döner). */
const bilgi = ref('')

function listeyeIsle(k: OzelKuralDto) {
  const i = kurallar.value.findIndex((x) => x.id === k.id)
  if (i >= 0) kurallar.value[i] = k
  else kurallar.value.push(k)
}

function kaydedildi(k: OzelKuralDto) {
  listeyeIsle(k)
  bilgi.value = `Kural kaydedildi: ${k.kod} ${k.ad}.`
}

const gecerlilik = (k: OzelKuralDto) =>
  `${tarihBicimle(k.gecerlilikBas)} – ${k.gecerlilikBit ? tarihBicimle(k.gecerlilikBit) : 'süresiz'}`

// --- Kapatma: form olmayan küçük alan, PUT ile şiddet KAPALI ---
const kapatId = ref<number | null>(null)
const kapatNeden = ref('')
const kapatMesgul = ref(false)
const kapatHata = ref('')

/** Kapatma nedeni kutusuna odak (açılınca ve hata sonrası). */
async function kapatOdagi() {
  await nextTick()
  document.getElementById(`ozel-kapat-neden-${kapatId.value}`)?.focus()
}

function kapatAc(k: OzelKuralDto) {
  kapatId.value = k.id
  kapatNeden.value = ''
  kapatHata.value = ''
  void kapatOdagi()
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
    listeyeIsle(g)
    bilgi.value = `Kural kapatıldı: ${g.kod}.`
    kapatId.value = null
  } catch (e) {
    kapatHata.value = e instanceof ApiHata ? e.detail : 'Kural kapatılamadı.'
    // Sürüm çakıştı: güncel sürümle tekrar denenebilsin.
    if (e instanceof ApiHata && e.cakismaMi) await yukle()
    void kapatOdagi()
  } finally {
    kapatMesgul.value = false
  }
}
</script>

<template>
  <section>
    <SayfaBasligi baslik="Özel kurallar">
      <template v-if="yoneticiMi && kurallar.length > 0" #eylemler>
        <Dugme
          varyant="birincil"
          simge="ekle"
          :disabled="katalog.length === 0"
          @click="diyalog = { kural: null }"
        >
          Yeni kural
        </Dugme>
      </template>
    </SayfaBasligi>
    <p class="soluk">
      Ayarlar sayfasındaki kurallarla bu sayfadaki kuralların hepsine birlikte uyulur. Kapatılan
      kural silinmez, yalnız uygulanmaz.
    </p>
    <p class="soluk">
      Yeni ya da gevşetilen bir kural daha önce yapılmış atamaları kırmızı yapabilir; kaydetmeden
      önce "Dene" ile bakın. Kırmızı olan bir atamayı masada "Yerine ata" ile değiştirebilir ya da
      kaldırabilirsiniz.
    </p>

    <div role="status">
      <p v-if="bilgi" class="bildirim bildirim-basari">{{ bilgi }}</p>
    </div>

    <VeriDurumu v-if="yukleniyor" durum="yukleniyor" metin="Özel kurallar yükleniyor…" />
    <VeriDurumu v-else-if="yuklemeHatasi" durum="hata" :metin="yuklemeHatasi">
      <template #eylem>
        <Dugme @click="yukle">Yeniden dene</Dugme>
      </template>
    </VeriDurumu>
    <VeriDurumu v-else-if="kurallar.length === 0" durum="bos" metin="Henüz özel kural yok.">
      <template v-if="yoneticiMi" #eylem>
        <Dugme
          varyant="birincil"
          simge="ekle"
          :disabled="katalog.length === 0"
          @click="diyalog = { kural: null }"
        >
          Yeni kural
        </Dugme>
      </template>
    </VeriDurumu>

    <div v-else class="tablo-kap">
      <table class="tablo" aria-label="Özel kurallar">
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
              <td class="kod-hucre">{{ k.kod }}</td>
              <td>{{ k.ad }}</td>
              <td>{{ k.ozet }}</td>
              <td>
                <Rozet
                  boyut="sm"
                  :ton="SIDDET_GORUNUMU[k.siddet]?.ton"
                  :simge="SIDDET_GORUNUMU[k.siddet]?.simge"
                  :metin="SIDDET_ETIKETLERI[k.siddet]"
                />
              </td>
              <td class="tarih-hucre">{{ gecerlilik(k) }}</td>
              <td>{{ k.gerekce }}{{ k.kaynak ? ` · ${k.kaynak}` : '' }}</td>
              <td v-if="yoneticiMi" class="eylem-hucre">
                <button
                  type="button"
                  class="btn btn-kucuk"
                  :aria-label="`${k.kod} kuralını düzenle`"
                  @click="diyalog = { kural: k }"
                >
                  Düzenle
                </button>
                <button
                  v-if="k.siddet !== 'KAPALI'"
                  type="button"
                  class="btn btn-kucuk"
                  :aria-label="`${k.kod} kuralını kapat`"
                  @click="kapatAc(k)"
                >
                  Kapat
                </button>
              </td>
            </tr>
            <tr v-if="kapatId === k.id">
              <td :colspan="sutunSayisi">
                <div class="kapat-alani alan-zorunlu">
                  <label :for="`ozel-kapat-neden-${k.id}`"
                    >{{ k.kod }} kapatma nedeni (zorunlu)</label
                  >
                  <input
                    :id="`ozel-kapat-neden-${k.id}`"
                    v-model="kapatNeden"
                    maxlength="500"
                    :aria-invalid="kapatHata !== ''"
                    :aria-describedby="kapatHata ? `ozel-kapat-hata-${k.id}` : undefined"
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
                  <AlanHatasi :id="`ozel-kapat-hata-${k.id}`" :metin="kapatHata" />
                </div>
              </td>
            </tr>
          </template>
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
  margin-left: var(--aralik-3);
}
.kod-hucre,
.tarih-hucre {
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}
.kapat-alani {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--aralik-3);
}
.kapat-alani label {
  font-weight: var(--yazi-agirlik-orta);
  font-size: var(--yazi-md);
}
.kapat-alani input {
  flex: 1;
  min-width: 14rem;
}
.kapat-alani p {
  flex-basis: 100%;
}
</style>
