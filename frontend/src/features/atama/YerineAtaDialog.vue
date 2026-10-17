<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ApiHata } from '@/api/istemci'
import { sayiBicimle } from '@/lib/sayi'
import NobetNotu from './NobetNotu.vue'
import { atamaApi, ROL_ETIKETLERI, type AtamaRolu, type Oneri, type OneriSonucu } from './atamaApi'

/** "Yerine ata": sunucunun önerdiği en çok 3 aday. Seçim üst bileşende AtamaDialog'u açar (neden/gerekçe orada alınır). */
const props = withDefaults(
  defineProps<{ seferId: number; seferEtiketi: string; rol?: AtamaRolu }>(),
  { rol: 'ASIL' },
)
/** Liste başlığı role göre: Asıl = makinist, Kondüktör = kondüktör (F3b BUG-2). */
const kisi = computed(() => (props.rol === 'KONDUKTOR' ? ROL_ETIKETLERI.KONDUKTOR : 'Makinist'))
const emit = defineEmits<{ sec: [oneri: Oneri]; kapat: [] }>()

const sonuc = ref<OneriSonucu | null>(null)
const hata = ref('')
const yukleniyor = ref(true)

const nobetMetinleri = computed(() =>
  (sonuc.value?.oneriler ?? []).flatMap((o) => o.uyarilar.map((u) => u.metin)),
)

/** Sunucu gerekçeye "; uyarı: …" ekliyor; uyarılar zaten madde madde gösterildiği için kuyruğu atılır. */
const gerekceMetni = (o: Oneri) => o.gerekce.split('; uyarı: ')[0]

const dialog = ref<HTMLDialogElement | null>(null)
onMounted(async () => {
  dialog.value?.showModal()
  try {
    sonuc.value = await atamaApi.oneri(props.seferId)
  } catch (e) {
    hata.value = e instanceof ApiHata ? e.detail : 'Öneriler yüklenemedi.'
  } finally {
    yukleniyor.value = false
  }
})
</script>

<template>
  <dialog
    ref="dialog"
    class="genis-dialog"
    aria-labelledby="yerine-dlg-baslik"
    @cancel.prevent="emit('kapat')"
  >
    <h2 id="yerine-dlg-baslik">Yerine ata: öneriler</h2>
    <p>{{ seferEtiketi }}</p>

    <p v-if="yukleniyor" class="soluk">Öneriler yükleniyor…</p>
    <p v-if="hata" class="bildirim bildirim-hata" role="alert">{{ hata }}</p>
    <div role="status">
      <p v-if="sonuc?.uygunAdayYok" class="bildirim bildirim-uyari">
        <span><span aria-hidden="true">◌</span> {{ sonuc.mesaj ?? 'Uygun aday yok.' }}</span>
      </p>
    </div>

    <div v-if="sonuc && sonuc.oneriler.length > 0" class="tablo-kap">
      <table class="tablo" :aria-label="`Önerilen ${kisi.toLocaleLowerCase('tr-TR')}ler`">
        <thead>
          <tr>
            <th>{{ kisi }}</th>
            <th class="sayisal">Bu ay saat</th>
            <th class="sayisal">Bu ay km</th>
            <th>Gerekçe</th>
            <th><span class="gorunmez">Seç</span></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="o in sonuc.oneriler" :key="o.makinistId">
            <td>
              {{ o.adSoyad }}
              <small class="alt-bilgi">{{ o.sicil }} · {{ o.bagliGar }}</small>
            </td>
            <td class="sayisal">{{ sayiBicimle(o.buAySaat) }}</td>
            <td class="sayisal">{{ sayiBicimle(o.buAyKm) }}</td>
            <td>
              {{ gerekceMetni(o) }}
              <ul v-if="o.uyarilar.length > 0" class="ihlal-listesi">
                <li v-for="(u, i) in o.uyarilar" :key="i">
                  <span aria-hidden="true">⚠</span> {{ u.metin }}
                </li>
              </ul>
            </td>
            <td>
              <button
                type="button"
                class="btn btn-birincil"
                :aria-label="`${o.adSoyad} (${o.sicil}) seç`"
                @click="emit('sec', o)"
              >
                Seç
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <NobetNotu :metinler="nobetMetinleri" />

    <div class="eylemler">
      <button type="button" class="btn" @click="emit('kapat')">Kapat</button>
    </div>
  </dialog>
</template>

<style scoped>
.genis-dialog {
  min-width: min(760px, 94vw);
}
.alt-bilgi {
  display: block;
  color: var(--renk-soluk);
}
.ihlal-listesi {
  margin: 0.25rem 0 0;
  padding-left: 0;
  list-style: none;
}
</style>
