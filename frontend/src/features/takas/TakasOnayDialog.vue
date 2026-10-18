<script setup lang="ts">
import AlanHatasi from '@/bilesenler/AlanHatasi.vue'
import { computed, onMounted, ref } from 'vue'
import { ApiHata } from '@/api/istemci'
import { NEDEN_ETIKETLERI } from '@/features/atama/atamaApi'
import NobetNotu from '@/features/atama/NobetNotu.vue'
import Diyalog from '@/bilesenler/Diyalog.vue'
import VeriDurumu from '@/bilesenler/VeriDurumu.vue'
import Dugme from '@/bilesenler/Dugme.vue'
import Simge from '@/bilesenler/Simge.vue'
import { sayiBicimle } from '@/lib/sayi'
import { takasApi, type TakasDetay } from './takasApi'
import {
  TAKAS_DURUM_ETIKETLERI,
  TAKAS_TUR_ETIKETLERI,
  type TakasDurumu,
  type TakasIhlali,
} from './takasTipleri'

/**
 * Atamacı onay ekranı: iki yönlü kural kontrolünün SONUCU burada (hangi makinist hangi kural, ENGEL / UYARI). Karşılıklı takasta ENGEL varsa onay sunucuda 422 verir ve talep açık kalır; bırakmada
 * "yerine ata" önerisinden biri seçilir ya da görev yalnız kaldırılır. UYARI varsa gerekçe zorunludur (mevcut atama davranışı).
 */
const props = defineProps<{ talepId: number }>()
const emit = defineEmits<{ kapat: []; tamam: [mesaj: string] }>()

const detay = ref<TakasDetay | null>(null)
const yukleniyor = ref(true)
const yuklemeHatasi = ref('')
const mesgul = ref(false)
const genelHata = ref('')
const alanHatalari = ref<Record<string, string>>({})
/** Onayda sunucunun geri çevirdiği ihlaller (422); talep açık kalır. */
const reddedilenIhlaller = ref<TakasIhlali[]>([])
const gerekce = ref('')
/** BIRAK: 'kaldir' ya da öneri makinist kimliği (metin). */
const secim = ref('')

async function yukle() {
  try {
    detay.value = await takasApi.detay(props.talepId)
    const oneriler = detay.value.oneri?.oneriler ?? []
    secim.value = oneriler.length > 0 ? String(oneriler[0]!.makinistId) : 'kaldir'
  } catch (e) {
    yuklemeHatasi.value = e instanceof ApiHata ? e.detail : 'Talep yüklenemedi.'
  } finally {
    yukleniyor.value = false
  }
}
onMounted(() => {
  void yukle()
})

const t = computed(() => detay.value?.talep ?? null)
/** Kapanışı yazılmamış (veritabanında açık) talep, etkin durumu BAYAT / SURESI_DOLDU olsa da reddedilerek kapatılabilir. */
const ACIK_KAYIT: TakasDurumu[] = ['ATAMACI_ONAYI_BEKLIYOR', 'KARSI_ONAY_BEKLIYOR']
const onayAcik = computed(() => t.value?.durum === 'ATAMACI_ONAYI_BEKLIYOR')
const birakma = computed(() => t.value?.tur === 'BIRAK')
const secilenOneri = computed(() =>
  (detay.value?.oneri?.oneriler ?? []).find((o) => String(o.makinistId) === secim.value),
)
/** Ayrıntıdaki UYARI ihlalleri (takas) ya da seçilen önerinin uyarıları (bırakma) varsa gerekçe gerekir. */
const uyariVar = computed(() =>
  birakma.value
    ? (secilenOneri.value?.uyarilar.length ?? 0) > 0
    : (detay.value?.ihlaller ?? []).some((i) => i.ihlal.siddet === 'UYARI'),
)
const gorunenIhlaller = computed(() =>
  reddedilenIhlaller.value.length > 0 ? reddedilenIhlaller.value : (detay.value?.ihlaller ?? []),
)
/** Kural kontrolü grubunda gösterilen ihlaller; sunucunun reddettikleri hata bandında gösterilir (tek yerde). */
const kontrolIhlalleri = computed(() =>
  reddedilenIhlaller.value.length > 0 ? [] : (detay.value?.ihlaller ?? []),
)
// yükleme hatası ya da onay/red hatası: tek bantta
const hataMetni = computed(() => genelHata.value || yuklemeHatasi.value)
const engelVar = computed(() => gorunenIhlaller.value.some((i) => i.ihlal.siddet === 'ENGEL'))
const nobetMetinleri = computed(() => gorunenIhlaller.value.map((i) => i.ihlal.metin))

/** Takas 422'si `{sicil, ihlal}` döner; bırakmadaki (mevcut `ata`) düz ihlal listesidir: ikisi aynı biçime getirilir. */
function ihlalleriDuzenle(ham: unknown): TakasIhlali[] {
  if (!Array.isArray(ham)) return []
  return ham.map((x) =>
    x && typeof x === 'object' && 'ihlal' in x
      ? (x as TakasIhlali)
      : { sicil: secilenOneri.value?.sicil ?? '', ihlal: x as TakasIhlali['ihlal'] },
  )
}

/** × / Esc istek sürerken kapatmaz (diğer diyaloglarla tutarlı; sökülen bileşenden `tamam` yayılmaz). */
function vazgec() {
  if (!mesgul.value) emit('kapat')
}

async function onayla() {
  if (mesgul.value || !onayAcik.value) return
  genelHata.value = ''
  reddedilenIhlaller.value = []
  const h: Record<string, string> = {}
  if (uyariVar.value && !gerekce.value.trim()) h.gerekce = 'Kural uyarısı var: gerekçe yazın.'
  alanHatalari.value = h
  if (Object.keys(h).length > 0) return
  mesgul.value = true
  try {
    await takasApi.onay(props.talepId, {
      yeniMakinistId: birakma.value && secim.value !== 'kaldir' ? Number(secim.value) : undefined,
      gerekce: gerekce.value.trim() || undefined,
    })
    emit('tamam', 'Talep onaylandı; atamalar güncellendi.')
  } catch (e) {
    if (e instanceof ApiHata) {
      alanHatalari.value = e.alanlar ?? {}
      if (e.status === 422 && e.ihlaller) {
        reddedilenIhlaller.value = ihlalleriDuzenle(e.ihlaller)
        genelHata.value = 'Kural ihlali: onaylanamadı, talep açık kaldı. Hiçbir atama değişmedi.'
      } else if (Object.keys(alanHatalari.value).length === 0) {
        genelHata.value = e.detail
      }
      if (e.status === 409) await yukle() // bayat / kapanmış: güncel durumu göster
    } else {
      genelHata.value = 'Talep onaylanamadı.'
    }
  } finally {
    mesgul.value = false
  }
}

async function reddet() {
  if (mesgul.value) return
  genelHata.value = ''
  mesgul.value = true
  try {
    await takasApi.red(props.talepId)
    emit('tamam', 'Talep reddedildi; atamalar değişmedi.')
  } catch (e) {
    genelHata.value = e instanceof ApiHata ? e.detail : 'Talep reddedilemedi.'
    if (e instanceof ApiHata && e.status === 409) await yukle()
  } finally {
    mesgul.value = false
  }
}
</script>

<template>
  <Diyalog baslik="Takas / bırakma talebi" boyut="genis" @kapat="vazgec">
    <VeriDurumu v-if="yukleniyor" durum="yukleniyor" metin="Yükleniyor…" />

    <template v-if="t">
      <p>
        <strong>{{ TAKAS_TUR_ETIKETLERI[t.tur] }}</strong> ·
        <Simge :ad="TAKAS_DURUM_ETIKETLERI[t.durum].simge" boyut="1em" />
        {{ TAKAS_DURUM_ETIKETLERI[t.durum].ad }}
      </p>
      <p>{{ t.kaynakSicil }} ({{ t.kaynakAd }}): {{ t.kaynakGorev }}</p>
      <p v-if="t.hedefSicil">{{ t.hedefSicil }} ({{ t.hedefAd }}): {{ t.hedefGorev }}</p>
      <p class="soluk">
        Neden: {{ NEDEN_ETIKETLERI[t.neden]
        }}<template v-if="t.nedenNotu"> · {{ t.nedenNotu }}</template>
      </p>

      <p v-if="!onayAcik" class="bildirim bildirim-uyari" role="status">
        Bu talep onaylanamaz: {{ TAKAS_DURUM_ETIKETLERI[t.durum].ad.toLowerCase() }}.
      </p>

      <div v-if="kontrolIhlalleri.length > 0" role="group" aria-label="Kural ihlalleri">
        <h3>Kural kontrolü</h3>
        <ul class="ihlal-listesi">
          <li v-for="(i, n) in kontrolIhlalleri" :key="n">
            <Simge :ad="i.ihlal.siddet === 'ENGEL' ? 'engel' : 'uyari'" boyut="1em" />
            <strong>{{ i.sicil }}</strong
            >: {{ i.ihlal.metin }}
          </li>
        </ul>
      </div>
      <p v-else-if="onayAcik && !birakma && reddedilenIhlaller.length === 0" class="soluk">
        İki yönlü kural kontrolünde ihlal yok.
      </p>
      <NobetNotu :metinler="nobetMetinleri" />

      <fieldset v-if="birakma && onayAcik" class="takas-secim">
        <legend>Görev kime verilsin?</legend>
        <p v-if="detay?.oneri?.uygunAdayYok" class="bildirim bildirim-uyari">
          {{ detay.oneri.mesaj ?? 'Uygun aday yok.' }}
        </p>
        <label v-for="o in detay?.oneri?.oneriler ?? []" :key="o.makinistId" class="takas-secenek">
          <input v-model="secim" type="radio" name="takas-yeni" :value="String(o.makinistId)" />
          {{ o.sicil }} ({{ o.adSoyad }}) · bu ay {{ sayiBicimle(o.buAySaat) }} saat
          <span v-if="o.uyarilar.length > 0"> · <Simge ad="uyari" boyut="1em" /> uyarı var</span>
        </label>
        <label class="takas-secenek">
          <input v-model="secim" type="radio" name="takas-yeni" value="kaldir" />
          Yalnız kaldır (yerine kimse atanmasın)
        </label>
      </fieldset>

      <div v-if="onayAcik && (uyariVar || alanHatalari.gerekce)" class="alan">
        <label for="takas-gerekce">Gerekçe (uyarı var, zorunlu)</label>
        <textarea
          id="takas-gerekce"
          v-model="gerekce"
          :aria-invalid="!!alanHatalari.gerekce"
          :aria-describedby="alanHatalari.gerekce ? 'takas-gerekce-hata' : undefined"
        ></textarea>
        <AlanHatasi id="takas-gerekce-hata" :metin="alanHatalari.gerekce" />
      </div>
    </template>

    <div role="alert">
      <div v-if="hataMetni || reddedilenIhlaller.length > 0" class="bildirim bildirim-hata">
        <p v-if="hataMetni">{{ hataMetni }}</p>
        <ul v-if="reddedilenIhlaller.length > 0" class="ihlal-listesi">
          <li v-for="(i, n) in reddedilenIhlaller" :key="n">
            <Simge :ad="i.ihlal.siddet === 'ENGEL' ? 'engel' : 'uyari'" boyut="1em" />
            <strong>{{ i.sicil }}</strong
            >: {{ i.ihlal.metin }}
          </li>
        </ul>
      </div>
    </div>

    <template #eylemler>
      <Dugme
        v-if="onayAcik"
        varyant="birincil"
        :disabled="mesgul || (!birakma && engelVar)"
        @click="onayla"
      >
        Onayla
      </Dugme>
      <Dugme
        v-if="t && ACIK_KAYIT.includes(t.kayitliDurum)"
        varyant="tehlike"
        :disabled="mesgul"
        @click="reddet"
      >
        Reddet
      </Dugme>
      <Dugme v-if="t || !yukleniyor" :disabled="mesgul" @click="vazgec">Kapat</Dugme>
    </template>
    <template v-if="engelVar" #neden>ENGEL varken onaylanamaz; talebi reddedebilirsiniz.</template>
  </Diyalog>
</template>

<style scoped>
.takas-secim {
  margin: var(--aralik-4) 0;
  border: 1px solid var(--renk-cizgi);
  border-radius: var(--yaricap);
}
.takas-secenek {
  display: block;
  padding: var(--aralik-2) 0;
}
</style>
