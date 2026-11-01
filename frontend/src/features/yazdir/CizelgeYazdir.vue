<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watchEffect } from 'vue'
import { ApiHata } from '@/api/istemci'
import Simge from '@/bilesenler/Simge.vue'
import { cizelgeApi, type CizelgeYaniti } from '@/features/rapor/cizelgeApi'
import { gunBasligi, gunMetni, hucreGorunumu, IZIN_KODLARI } from '@/features/rapor/cizelgeHucre'
import { TUR_ETIKETLERI } from '@/features/makinist/makinistApi'
import { tanimApi } from '@/features/tanim/tanimApi'
import { sayiBicimle } from '@/lib/sayi'
import { ayGecerliMi } from '@/lib/tarih'
import YazdirSayfasi from './YazdirSayfasi.vue'

/**
 * Aylık çizelge ilan baskısı (F5, MK-179): menüsüz, yatay, A3 ya da A4. Sayfa boyutu bu rotaya özgü `@page` kuralıdır (başlık içinde enjekte edilir; global `yazdir.css`'in A4 yatay kuralı bu sayfada
 * geçersiz kılınır, dosyaya dokunulmaz). `thead` her sayfada yinelenir, satır bölünmez (global yazdırma kuralları), siyah-beyaz. Başlıkta kurum adı/logo (F1), dönem, süzgeç; altbilgide sunucu
 * zamanı ve yazdıran (`YazdirSayfasi`). Hücre anlamı renksiz: kısa kod, "İ/R/D", "Y" öneki, gece simgesi.
 */
const props = defineProps<{ ay?: string; boyut?: string; tur?: string; garId?: string }>()

const boyut = computed(() => (props.boyut === 'A3' ? 'A3' : 'A4'))
const kondukturMu = computed(() => props.tur === 'KONDUKTOR')
const garNo = computed(() =>
  props.garId && /^\d+$/.test(props.garId) ? Number(props.garId) : null,
)

const yanit = ref<CizelgeYaniti | null>(null)
const hata = ref('')
const garAdi = ref('')

// Bu rotaya özgü sayfa boyutu (A3/A4 yatay); sayfadan çıkınca kural kalkar.
let stil: HTMLStyleElement | null = null
watchEffect(() => {
  if (!stil) {
    stil = document.createElement('style')
    stil.dataset.cizelgeSayfa = ''
    document.head.appendChild(stil)
  }
  stil.textContent = `@media print { @page { size: ${boyut.value} landscape; margin: 8mm; } }`
})
onBeforeUnmount(() => {
  stil?.remove()
  stil = null
})

onMounted(async () => {
  if (!props.ay) {
    hata.value = 'Ay gerekli.'
    return
  }
  if (!ayGecerliMi(props.ay)) {
    hata.value = 'Ay geçersiz: YYYY-AA biçiminde olmalı (ör. 2026-10).'
    return
  }
  try {
    yanit.value = await cizelgeApi.getir(props.ay, {
      tur: kondukturMu.value ? 'KONDUKTOR' : undefined,
      garId: garNo.value,
      kontrol: false,
    })
  } catch (e) {
    hata.value = e instanceof ApiHata ? e.detail : 'Çizelge alınamadı.'
  }
  if (garNo.value !== null) {
    try {
      garAdi.value = (await tanimApi.liste('GAR')).find((g) => g.id === garNo.value)?.ad ?? ''
    } catch {
      // gar adı gelmezse başlıkta numarası yazılır
    }
  }
})

/**
 * Basılı hücreye sığan kod (QA BUG-3): hücre genişliği (A4 ≈ 26 px, A3 ≈ 37 px) tam tren no'ya yetmez; "AS-035" tireden bölünüp satır yüksekliğini bozuyordu. Kod bölünmez (nowrap);
 * sığmayacak kadar uzunsa öndeki harf kısmı atılıp numara basılır (tam no erişilebilir adda ve ekran çizelgesinde; açıklama satırında yazılı). Numaranın kendisi ASLA
 * kırpılmaz ("32001" → "2001" yanlış tren olurdu): numara da sığmıyorsa tam kod basılır ve hücrede satır kırar (`kirilir`).
 */
const SIGAN_KARAKTER = { A4: 4, A3: 5 } as const
const siganKod = (kod: string) => {
  const en = SIGAN_KARAKTER[boyut.value]
  if (kod.length <= en) return { metin: kod, kirilir: false }
  const no = /\d+$/.exec(kod)?.[0]
  return no && no.length <= en ? { metin: no, kirilir: false } : { metin: kod, kirilir: true }
}

const donem = computed(() =>
  props.ay && ayGecerliMi(props.ay) ? gunMetni(props.ay, 1).replace(/^1 /, '') : '',
)
const filtre = computed(
  () =>
    `Dönem: ${donem.value} · Personel: ${TUR_ETIKETLERI[kondukturMu.value ? 'KONDUKTOR' : 'MAKINIST']}` +
    (garNo.value !== null ? ` · Gar: ${garAdi.value || `#${garNo.value}`}` : '') +
    ` · Kâğıt: ${boyut.value} yatay`,
)

const gunler = computed(() =>
  Array.from({ length: yanit.value?.gunSayisi ?? 0 }, (_, i) => ({
    ...gunBasligi(yanit.value!.ay, i + 1),
    tatil: yanit.value?.tatiller.find((t) => t.gun === i + 1)?.ad ?? null,
  })),
)
const satirlar = computed(() => {
  const y = yanit.value
  if (!y) return []
  return y.satirlar.map((s) => {
    const harita = new Map(s.hucreler.map((h) => [h.gun, h]))
    return {
      s,
      hucreler: gunler.value.map((g) =>
        hucreGorunumu(harita.get(g.no), s.adSoyad, gunMetni(y.ay, g.no), g.tatil ?? undefined),
      ),
    }
  })
})
</script>

<template>
  <YazdirSayfasi
    baslik="Aylık çizelge"
    :filtre="filtre"
    :uretim-zamani="yanit?.uretimZamani ?? null"
    :kayit-sayisi="yanit?.satirlar.length ?? 0"
    :geri="{ name: 'rapor-cizelge' }"
    :yazdirilabilir="yanit != null && !hata"
  >
    <p v-if="hata" class="bildirim bildirim-hata no-print" role="alert">{{ hata }}</p>
    <div v-if="yanit" class="yazdir-tablo-kap">
      <table class="yazdir-tablo cizelge-yazdir" :class="`boyut-${boyut}`">
        <caption class="gorunmez">
          Aylık çizelge
          {{
            yanit.ay
          }}
        </caption>
        <colgroup>
          <col class="kol-ad" />
          <col v-for="g in gunler" :key="g.no" class="kol-gun" />
          <col v-for="k in 4" :key="`t${k}`" class="kol-toplam" />
        </colgroup>
        <thead>
          <tr>
            <th scope="col" class="sutun-ad">Personel</th>
            <th
              v-for="g in gunler"
              :key="g.no"
              scope="col"
              class="gun"
              :class="{ 'hafta-sonu': g.haftaSonu, tatil: g.tatil }"
            >
              {{ g.no }}<span class="gun-adi">{{ g.ad.slice(0, 2) }}</span>
              <span v-if="g.tatil" class="gun-tatil">T</span>
              <span v-if="g.haftaSonu" class="gorunmez"> hafta sonu</span>
              <span v-if="g.tatil" class="gorunmez"> resmi tatil: {{ g.tatil }}</span>
            </th>
            <th scope="col" class="toplam">Saat</th>
            <th scope="col" class="toplam">Gece</th>
            <th scope="col" class="toplam">Sefer</th>
            <th scope="col" class="toplam">İzin</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="r in satirlar" :key="r.s.makinistId">
            <th scope="row" class="sutun-ad">{{ r.s.sicil }} {{ r.s.adSoyad }}</th>
            <td
              v-for="(v, c) in r.hucreler"
              :key="c"
              class="hucre"
              :class="{ tatil: gunler[c]!.tatil }"
            >
              <template v-if="v">
                <span class="gorunmez">{{ v.ad }}</span>
                <span aria-hidden="true" :class="{ kirilir: siganKod(v.kod).kirilir }"
                  >{{ siganKod(v.kod).metin }}<b v-if="v.ek" class="ek">+{{ v.ek }}</b></span
                >
                <Simge v-if="v.gece" ad="ay" boyut="0.8em" />
                <span v-if="boyut === 'A3' && v.saat" class="hucre-saat" aria-hidden="true">{{
                  v.saat
                }}</span>
              </template>
            </td>
            <td class="toplam">{{ sayiBicimle(r.s.gorevSaat) }}</td>
            <td class="toplam">{{ sayiBicimle(r.s.geceSaat) }}</td>
            <td class="toplam">{{ r.s.seferSayisi }}</td>
            <td class="toplam">{{ r.s.izinGunu }}</td>
          </tr>
        </tbody>
      </table>
      <p class="aciklama">
        Kodlar: tren no (görev; A3'te altında kalkış saati; hücreye sığmayan uzun no'da yalnız
        numara kısmı) · +N aynı günde N görev daha ·
        {{
          Object.values(IZIN_KODLARI)
            .map((i) => `${i.kod} ${i.ad}`)
            .join(' · ')
        }}
        · Y yolculuk · <Simge ad="ay" boyut="0.8em" /> gece görevi · çizgili sütun başlığı hafta
        sonu · T resmi tatil
        <template v-if="yanit.tatiller.length > 0"
          >({{ yanit.tatiller.map((t) => `${t.gun}: ${t.ad}`).join('; ') }})</template
        >.
      </p>
    </div>
  </YazdirSayfasi>
</template>

<style scoped>
/* Ekran önizlemesi: tablo kendi kabında kayar; yazdırmada tam genişlik, sabit sütunlar, her sayfada thead. Renkler yalnız belirteç/currentcolor (ham renk yok). */
.cizelge-yazdir {
  table-layout: fixed;
  width: 100%;
  min-width: 70rem;
}
.cizelge-yazdir th,
.cizelge-yazdir td {
  padding: 1px 2px;
  text-align: center;
  white-space: nowrap;
  overflow: hidden;
  border: 1px solid currentcolor;
}
.cizelge-yazdir .kol-ad {
  width: 13%;
}
.cizelge-yazdir .kol-toplam {
  width: 2.8%;
}
/* Tren no BÖLÜNMEZ (siganKod uzun kodu kısaltır); aynı günde ikinci görev "+N" kendi satırında. Satır sayfada bölünmez. */
.cizelge-yazdir td.hucre {
  white-space: nowrap;
  line-height: 1.05;
}
.cizelge-yazdir td.hucre .ek {
  display: block;
}
/* Numara bile hücreye sığmıyorsa kırpılmaz, satır kırar (satır uzar ama tren no doğru kalır) */
.cizelge-yazdir td.hucre .kirilir {
  display: block;
  white-space: normal;
  overflow-wrap: anywhere;
}
.cizelge-yazdir th.sutun-ad {
  text-align: left;
}
.cizelge-yazdir .gun-adi,
.cizelge-yazdir .gun-tatil,
.cizelge-yazdir .hucre-saat {
  display: block;
}
/* Hafta sonu yalnız başlıkta: desen + altı çizili kalın gün adı (arka plan grafiği basılmasa da gün adı işareti kalır); hücre gövdesi sade (okunurluk) */
.cizelge-yazdir th.hafta-sonu {
  background-image: repeating-linear-gradient(135deg, transparent 0 3px, currentcolor 3px 4px);
}
.cizelge-yazdir th.hafta-sonu .gun-adi {
  font-weight: bold;
  text-decoration: underline;
}
.cizelge-yazdir th.tatil {
  font-weight: bold;
  text-decoration: underline;
}
.aciklama {
  margin: 0.4rem 0 0;
  font-size: 0.75rem;
}
@media print {
  .yazdir-tablo-kap {
    overflow: visible;
  }
  .cizelge-yazdir {
    min-width: 0;
    line-height: 1.1;
  }
  /* Kâğıda göre punto: 31 gün sütunu A4'te de okunur (≥ 6,5 pt), A3'te daha rahat */
  .cizelge-yazdir.boyut-A4 {
    font-size: 6.5pt;
  }
  .cizelge-yazdir.boyut-A3 {
    font-size: 9pt;
  }
  .cizelge-yazdir th.hafta-sonu {
    background-image: repeating-linear-gradient(135deg, transparent 0 2px, currentcolor 2px 2.4px);
  }
  .aciklama {
    font-size: 6.5pt;
  }
}
</style>
