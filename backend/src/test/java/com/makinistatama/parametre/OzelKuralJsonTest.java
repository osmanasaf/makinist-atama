package com.makinistatama.parametre;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.makinistatama.kural.Kapsam;
import com.makinistatama.kural.Karsilastirma;
import com.makinistatama.kural.Olcu;
import com.makinistatama.kural.OzelKural;
import com.makinistatama.kural.Pencere;
import com.makinistatama.kural.Siddet;
import com.makinistatama.kural.Vardiya;

import tools.jackson.databind.json.JsonMapper;

/** DB'siz: kapsam JSON gidiş-dönüşü (MK-16), istek gövdesi, denetim kaydı JSON'u ve özet cümlesi. */
class OzelKuralJsonTest {

    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    void kapsam_gidisDonus_sirali_veBosKapsamTumu() {
        Kapsam k = new Kapsam(Set.of(3L, 1L), Set.of(), Set.of(2L), null, null, Vardiya.GECE);
        String s = json.writeValueAsString(k);
        assertThat(s).isEqualTo("{\"hatlar\":[1,3],\"aracTipleri\":[],\"kalkisGarlari\":[2],\"varisGarlari\":[],\"trenTurleri\":[],\"vardiya\":\"GECE\",\"personelTurleri\":[]}"); // F3b MK-85: kapsam JSON'u personelTurleri taşır (boş = tümü)
        assertThat(json.readValue(s, Kapsam.class)).isEqualTo(k);
        assertThat(json.readValue("{}", Kapsam.class)).isEqualTo(Kapsam.TUMU);
        assertThat(json.readValue("{\"hatlar\":[null,5],\"vardiya\":null}", Kapsam.class).hatlar()).containsExactly(5L);
    }

    @Test
    void istek_kapsamsiz_ve_kapsamli_okunur() {
        String govde = "{\"ad\":\"A\",\"olcu\":\"TOPLAM_GOREV_SURESI\",\"pencere\":\"GUN\",\"karsilastirma\":\"EN_FAZLA\",\"esik\":7.5,\"siddet\":\"ENGEL\",\"gerekce\":\"g\"%s}";
        OzelKuralIstegi a = json.readValue(govde.formatted(""), OzelKuralIstegi.class);
        assertThat(a.kapsam()).isNull();
        assertThat(a.esik()).isEqualByComparingTo("7.5");
        assertThat(a.olcu()).isEqualTo(Olcu.TOPLAM_GOREV_SURESI);
        assertThat(a.versiyon()).isNull();
        OzelKuralIstegi b = json.readValue(govde.formatted(",\"kapsam\":{\"hatlar\":[4]},\"versiyon\":2,\"gecerlilikBas\":\"2026-03-01\""), OzelKuralIstegi.class);
        assertThat(b.kapsam().hatlar()).containsExactly(4L);
        assertThat(b.versiyon()).isEqualTo(2L);
        assertThat(b.gecerlilikBas()).isEqualTo(LocalDate.of(2026, 3, 1));
    }

    @Test
    void denetimKaydiJson_esikSade_kodYok_kapsamGomulu() {
        OzelKural k = new OzelKural(3, "Günlük sınır", Olcu.TOPLAM_GOREV_SURESI, Pencere.GUN, Karsilastirma.EN_FAZLA, new BigDecimal("8"),
                new Kapsam(Set.of(1L), null, null, null, null, null), Siddet.ENGEL, LocalDate.of(2026, 1, 1), null, "TİS", null, 4);
        assertThat(json.writeValueAsString(k)).isEqualTo("{\"id\":3,\"ad\":\"Günlük sınır\",\"olcu\":\"TOPLAM_GOREV_SURESI\",\"pencere\":\"GUN\",\"kayanGun\":null,"
                + "\"karsilastirma\":\"EN_FAZLA\",\"esik\":8,\"kapsam\":{\"hatlar\":[1],\"aracTipleri\":[],\"kalkisGarlari\":[],\"varisGarlari\":[],\"trenTurleri\":[],"
                + "\"vardiya\":\"HEPSI\",\"personelTurleri\":[]},\"siddet\":\"ENGEL\",\"gecerlilikBas\":\"2026-01-01\",\"gecerlilikBit\":null,\"gerekce\":\"TİS\",\"kaynak\":null,\"versiyon\":4}");
    }

    @Test
    void ozet_cumlesi_kapsamAdlariyla() {
        OzelKural k = new OzelKural(1, "x", Olcu.TOPLAM_GOREV_SURESI, Pencere.HAFTA, Karsilastirma.EN_FAZLA, new BigDecimal("50"),
                new Kapsam(Set.of(7L, 8L), Set.of(9L), null, null, null, Vardiya.GUNDUZ), Siddet.UYARI, LocalDate.of(2026, 1, 1), null, "g", null, 0);
        assertThat(OzelKuralDto.ozet(k, Map.of(7L, "Ankara–Sivas", 8L, "Sivas–Erzurum", 9L, "Dizel"))).isEqualTo(
                "Toplam görev süresi (hafta) en fazla 50 s · Hat: Ankara–Sivas, Sivas–Erzurum · Araç tipi: Dizel · Vardiya: gündüz");
        OzelKural tek = new OzelKural(2, "y", Olcu.TEK_SEFER_SURESI, Pencere.SEFER, Karsilastirma.EN_FAZLA, new BigDecimal("8"), null, Siddet.ENGEL,
                LocalDate.of(2026, 1, 1), null, "g", null, 0);
        assertThat(OzelKuralDto.ozet(tek, Map.of())).isEqualTo("Tek sefer süresi en fazla 8 s");
    }
}
