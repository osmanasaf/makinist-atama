package com.makinistatama.rapor;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.makinistatama.cikti.TabloExcel;
import com.makinistatama.degisiklik.DegisiklikServisi;
import com.makinistatama.degisiklik.DegisiklikServisi.Satir;
import com.makinistatama.degisiklik.DegisiklikTuru;
import com.makinistatama.makinist.MakinistDto;
import com.makinistatama.makinist.MakinistServisi;
import com.makinistatama.ortak.AramaMetni;
import com.makinistatama.ortak.UygulamaHatasi;
import com.makinistatama.ortak.ZamanAyari;
import com.makinistatama.parametre.AyarAdlari;
import com.makinistatama.parametre.OzelKuralMetni;
import com.makinistatama.rapor.DegisiklikRaporuDto.Bolum;
import com.makinistatama.rapor.DegisiklikRaporuDto.Filtre;
import com.makinistatama.sefer.SeferDto;
import com.makinistatama.sefer.SeferServisi;
import com.makinistatama.tanim.TanimServisi;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Şeffaflık / denetim raporu (FR-08). Kaynak: ek-yalnız {@code degisiklik_kaydi}. Süzgeçler: tarih aralığı (zorunlu, İstanbul günü, en çok 366 gün),
 * makinist, tren no, kullanıcı, tür. Her kayıt bir bölüme düşer (toplam = kayıt sayısı): uyarılı atamalar, atamalar (elle + otomatik; satırda {@code otomatik} bayrağı), iptal/gecikmeler, atama değişiklikleri,
 * parametre değişiklikleri, diğer. Excel aynı veriyi satır satır verir.
 */
@Service
@Transactional(readOnly = true)
public class DegisiklikRaporServisi {

    static final int EN_COK_GUN = 366;

    private static final Map<DegisiklikTuru, String> TUR_ADLARI = new EnumMap<>(DegisiklikTuru.class);

    static {
        TUR_ADLARI.put(DegisiklikTuru.TANIM_EKLE, "Tanım eklendi");
        TUR_ADLARI.put(DegisiklikTuru.TANIM_GUNCELLE, "Tanım yeniden adlandırıldı");
        TUR_ADLARI.put(DegisiklikTuru.PARAMETRE_DEGISTI, "Parametre / kural değişti");
        TUR_ADLARI.put(DegisiklikTuru.MAKINIST_EKLE, "Makinist eklendi");
        TUR_ADLARI.put(DegisiklikTuru.MAKINIST_GUNCELLE, "Makinist güncellendi");
        TUR_ADLARI.put(DegisiklikTuru.IZIN_EKLE, "İzin eklendi");
        TUR_ADLARI.put(DegisiklikTuru.IZIN_TALEP, "İzin talebi");
        TUR_ADLARI.put(DegisiklikTuru.IZIN_ONAY, "İzin onaylandı");
        TUR_ADLARI.put(DegisiklikTuru.IZIN_RED, "İzin reddedildi");
        TUR_ADLARI.put(DegisiklikTuru.SEFER_EKLE, "Sefer eklendi");
        TUR_ADLARI.put(DegisiklikTuru.SEFER_GUNCELLE, "Sefer güncellendi");
        TUR_ADLARI.put(DegisiklikTuru.SEFER_YUKLE, "Sefer Excel yüklemesi");
        TUR_ADLARI.put(DegisiklikTuru.SEFER_IPTAL, "Sefer iptali");
        TUR_ADLARI.put(DegisiklikTuru.SEFER_GECIKME, "Sefer gecikmesi");
        TUR_ADLARI.put(DegisiklikTuru.SEFER_BOL, "Sefer bölündü");
        TUR_ADLARI.put(DegisiklikTuru.SEFER_BIRLESTIR, "Sefer birleştirildi");
        TUR_ADLARI.put(DegisiklikTuru.ATAMA, "Atama");
        TUR_ADLARI.put(DegisiklikTuru.ATAMA_KALDIR, "Atama kaldırıldı");
        TUR_ADLARI.put(DegisiklikTuru.ATAMA_DEGISTIR, "Makinist değiştirildi");
        TUR_ADLARI.put(DegisiklikTuru.OTOMATIK_ATAMA, "Otomatik atama çalıştırıldı");
        TUR_ADLARI.put(DegisiklikTuru.KURAL_EKLE, "Kural eklendi");
        TUR_ADLARI.put(DegisiklikTuru.KURAL_GUNCELLE, "Kural güncellendi");
        TUR_ADLARI.put(DegisiklikTuru.HESAP_ACILDI, "Hesap açıldı");
        TUR_ADLARI.put(DegisiklikTuru.HESAP_GUNCELLENDI, "Hesap güncellendi");
        TUR_ADLARI.put(DegisiklikTuru.HESAP_PAROLA_SIFIRLANDI, "Hesap parolası sıfırlandı");
        TUR_ADLARI.put(DegisiklikTuru.HESAP_KILIT_ACILDI, "Hesap kilidi açıldı");
        TUR_ADLARI.put(DegisiklikTuru.PAROLA_DEGISTI, "Kendi parolasını değiştirdi");
        TUR_ADLARI.put(DegisiklikTuru.GORDUM, "Görev görüldü (gördüm)");
        TUR_ADLARI.put(DegisiklikTuru.TAKAS_TALEP, "Takas / bırakma talebi açıldı");
        TUR_ADLARI.put(DegisiklikTuru.TAKAS_KABUL, "Takas talebi kabul edildi");
        TUR_ADLARI.put(DegisiklikTuru.TAKAS_RED, "Takas talebi reddedildi");
        TUR_ADLARI.put(DegisiklikTuru.TAKAS_IPTAL, "Takas talebi geri çekildi");
        TUR_ADLARI.put(DegisiklikTuru.TAKAS_ONAY, "Takas / bırakma talebi onaylandı");
        TUR_ADLARI.put(DegisiklikTuru.TAKAS_BAYAT, "Takas talebi bayatladı");
        TUR_ADLARI.put(DegisiklikTuru.TAKAS_SURESI_DOLDU, "Takas talebinin süresi doldu");
        TUR_ADLARI.put(DegisiklikTuru.KONAKLAMA_EKLE, "Konaklama eklendi");
        TUR_ADLARI.put(DegisiklikTuru.KONAKLAMA_GUNCELLE, "Konaklama güncellendi");
        TUR_ADLARI.put(DegisiklikTuru.KONAKLAMA_SIL, "Konaklama silindi");
        TUR_ADLARI.put(DegisiklikTuru.INTIKAL_EKLE, "İntikal eklendi");
        TUR_ADLARI.put(DegisiklikTuru.INTIKAL_GUNCELLE, "İntikal güncellendi");
        TUR_ADLARI.put(DegisiklikTuru.INTIKAL_IPTAL, "İntikal iptal edildi");
        TUR_ADLARI.put(DegisiklikTuru.INTIKAL_SURESI_DEGISTI, "Gar intikal süresi değişti");
        TUR_ADLARI.put(DegisiklikTuru.PROFIL_UYGULA, "Kural profili uygulandı");
        TUR_ADLARI.put(DegisiklikTuru.KURUM_GUNCELLE, "Kurum bilgisi güncellendi");
        TUR_ADLARI.put(DegisiklikTuru.KURUM_LOGO, "Kurum logosu değişti");
        TUR_ADLARI.put(DegisiklikTuru.MAKINIST_ICE_AKTAR, "Makinist Excel yüklemesi");
    }

    private static final Map<String, String> BOLUM_ADLARI = new LinkedHashMap<>();

    static {
        BOLUM_ADLARI.put("UYARILI_ATAMALAR", "Uyarılı atamalar (gerekçeyle yapılan)");
        BOLUM_ADLARI.put("ATAMALAR", "Atamalar (elle ve otomatik)");
        BOLUM_ADLARI.put("IPTAL_GECIKME", "Sefer iptal ve gecikmeleri");
        BOLUM_ADLARI.put("ATAMA_DEGISIKLIKLERI", "Atama değişiklikleri (yerine atama, kaldırma)");
        BOLUM_ADLARI.put("PARAMETRE", "Parametre ve kural değişiklikleri");
        BOLUM_ADLARI.put("DIGER", "Diğer kayıtlar");
    }

    /** "Gördüm" kayıtları günde yüzlerce olur (V10): varsayılan raporda YOK, yalnız {@code tur=GORDUM} süzgeciyle ayrı bölümde gelir. */
    private static final String GORDUM_BOLUMU = "GORDUM";

    private final DegisiklikServisi degisiklik;
    private final SeferServisi seferler;
    private final MakinistServisi makinistler;
    private final TanimServisi tanimlar;
    private final JsonMapper json;
    private final Clock saat;

    DegisiklikRaporServisi(DegisiklikServisi degisiklik, SeferServisi seferler, MakinistServisi makinistler, TanimServisi tanimlar, JsonMapper json, Clock saat) {
        this.degisiklik = degisiklik;
        this.seferler = seferler;
        this.makinistler = makinistler;
        this.tanimlar = tanimlar;
        this.json = json;
        this.saat = saat;
    }

    public DegisiklikRaporuDto rapor(LocalDate bas, LocalDate bit, Long makinistId, String trenNo, String kullanici, DegisiklikTuru tur) {
        if (bas == null || bit == null) {
            throw UygulamaHatasi.dogrulama("bas", "Tarih aralığı gerekli.");
        }
        if (bit.isBefore(bas)) {
            throw UygulamaHatasi.dogrulama("bit", "Bitiş tarihi başlangıçtan önce olamaz.");
        }
        if (ChronoUnit.DAYS.between(bas, bit) >= EN_COK_GUN) {
            throw UygulamaHatasi.dogrulama("bit", "Aralık en çok " + EN_COK_GUN + " gün olabilir.");
        }
        String sicilSuzgeci = null;
        if (makinistId != null) {
            sicilSuzgeci = makinistler.getir(makinistId).sicil(); // 404
        }
        Set<Long> trenSeferleri = trenNo == null || trenNo.isBlank() ? null : new HashSet<>(seferler.trenNoIle(trenNo));
        String kullaniciKatli = kullanici == null || kullanici.isBlank() ? null : AramaMetni.katla(kullanici.strip());

        List<Satir> ham = degisiklik.ara(bas.atStartOfDay(ZamanAyari.BOLGE).toInstant(), bit.plusDays(1).atStartOfDay(ZamanAyari.BOLGE).toInstant());
        Map<Long, List<Satir>> gecmis = sicilSuzgeci == null ? Map.of() : atamaGecmisi(ham);
        String sicilFiltre = sicilSuzgeci;
        Long mid = makinistId;
        List<Satir> secilen = ham.stream()
                .filter(s -> tur == null ? s.tur() != DegisiklikTuru.GORDUM : s.tur() == tur)
                .filter(s -> sicilFiltre == null || mid.equals(s.makinistId()) || etkilenenSicil(s, gecmis).contains(sicilFiltre))
                .filter(s -> trenSeferleri == null || (s.seferId() != null && trenSeferleri.contains(s.seferId())))
                .filter(s -> kullaniciKatli == null || AramaMetni.katla(s.kim()).contains(kullaniciKatli))
                .toList();

        Map<Long, SeferDto> seferMap = seferler.kimliklerle(secilen.stream().map(Satir::seferId).filter(java.util.Objects::nonNull).collect(Collectors.toSet()));
        Map<Long, String> sicilMap = makinistler.liste(null, null).stream().collect(Collectors.toMap(MakinistDto.Ozet::id, MakinistDto.Ozet::sicil));

        Map<Long, String> tanimAdlari = secilen.stream().anyMatch(s -> s.tur() == DegisiklikTuru.KURAL_EKLE || s.tur() == DegisiklikTuru.KURAL_GUNCELLE) ? tanimlar.tumAdlar() : Map.of();
        Map<String, List<DegisiklikRaporuDto.Satir>> bolumler = new LinkedHashMap<>();
        BOLUM_ADLARI.keySet().forEach(k -> bolumler.put(k, new ArrayList<>()));
        if (tur == DegisiklikTuru.GORDUM) {
            bolumler.put(GORDUM_BOLUMU, new ArrayList<>());
        }
        for (Satir s : secilen) {
            JsonNode ayr = ayrinti(s);
            boolean uyarili = (s.tur() == DegisiklikTuru.ATAMA || s.tur() == DegisiklikTuru.ATAMA_DEGISTIR) && "UYARI".equals(ayr.path("sonuc").asString());
            String gerekce = uyarili ? ayr.path("gerekce").asString() : null;
            SeferDto sefer = s.seferId() == null ? null : seferMap.get(s.seferId());
            String[] degisim = kuralMetni(s, tanimAdlari);
            List<String> uyarilar = uyarili ? uyarilar(ayr) : List.of();
            var satir = new DegisiklikRaporuDto.Satir(s.id(), s.zaman().atZone(ZamanAyari.BOLGE).toOffsetDateTime(), s.kim(), s.tur(),
                    turAdi(s, ayr), s.makinistId() == null ? null : sicilMap.get(s.makinistId()),
                    sefer == null ? null : sefer.trenNo(), sefer == null ? null : sefer.tarih(), degisim[0], degisim[1], s.neden(), nedenNotu(s.nedenNotu()), uyarili, gerekce,
                    s.tur() == DegisiklikTuru.OTOMATIK_ATAMA || ayr.path("otomatik").asBoolean(false), uyarilar);
            bolumler.get(bolum(s.tur(), uyarili)).add(satir);
        }
        List<Bolum> sonuc = bolumler.entrySet().stream().map(e -> new Bolum(e.getKey(), BOLUM_ADLARI.getOrDefault(e.getKey(), "Gördüm kayıtları (tebliğ kanıtı)"), e.getValue().size(), e.getValue())).toList();
        return new DegisiklikRaporuDto(OffsetDateTime.now(saat).truncatedTo(ChronoUnit.SECONDS), DegisiklikServisi.simdikiKullaniciAdi(),
                new Filtre(bas, bit, makinistId, sicilSuzgeci, trenNo == null || trenNo.isBlank() ? null : trenNo.strip(), kullanici == null || kullanici.isBlank() ? null : kullanici.strip(), tur),
                secilen.size(), sonuc);
    }

    /** V30'un sistem kayıtlarındaki sürüm/iş-kodu notları ("V30: yeni varsayılan (OK-24)") yöneticiye anlaşılır yazılır; kayıt (ek-yalnız) değişmez, yalnız gösterim. */
    private static String nedenNotu(String not) {
        return not == null ? null : switch (not) {
            case "V30: yeni varsayılan (OK-24)" -> "Sürüm güncellemesi: yeni varsayılan değer";
            case "V30: demo artığı (OK-24)" -> "Sürüm güncellemesi: eski demo artığı düzeltildi";
            default -> not;
        };
    }

    /** Ayar değişikliği satırı HANGİ ayarın değiştiğini söyler (QA BUG-1): "Ayar değişti: Nöbet içinde asgari aktarma süresi" / "Kural değişti: K-14 Haftalık görev süresi". Anahtarsız (eski) kayıt genel adı korur. */
    private static String turAdi(Satir s, JsonNode ayr) {
        String genel = TUR_ADLARI.getOrDefault(s.tur(), s.tur().name());
        String anahtar = ayr.path("anahtar").asString("");
        if (s.tur() != DegisiklikTuru.PARAMETRE_DEGISTI || anahtar.isEmpty()) {
            return genel;
        }
        return "KURAL_SIDDET".equals(s.varlikTipi()) ? "Kural değişti: " + anahtar + " " + AyarAdlari.ad(anahtar) : "Ayar değişti: " + AyarAdlari.ad(anahtar);
    }

    /** Kural ekleme/güncelleme kaydı: ham JSON yerine okunur metin (hat adı, "Eşik 8 s" → "Eşik 9 s"); diğer türlerde kayıt olduğu gibi. */
    private String[] kuralMetni(Satir s, Map<Long, String> adlar) {
        if (s.tur() != DegisiklikTuru.KURAL_EKLE && s.tur() != DegisiklikTuru.KURAL_GUNCELLE) {
            return new String[] { s.eski(), s.yeni() };
        }
        try {
            JsonNode yeni = json.readTree(s.yeni());
            if (s.tur() == DegisiklikTuru.KURAL_EKLE) {
                return new String[] { null, OzelKuralMetni.ozet(yeni, adlar) };
            }
            return OzelKuralMetni.fark(json.readTree(s.eski()), yeni, adlar);
        } catch (RuntimeException e) { // bozuk/eski biçimli kayıt rapora yine girer
            return new String[] { s.eski(), s.yeni() };
        }
    }

    /** Atama anlık görüntüsündeki UYARI düzeyi ihlaller: "Ö-001: Tek sefer süresi ... (Kural: Dizel hatlar)". */
    private static List<String> uyarilar(JsonNode ayrinti) {
        List<String> l = new ArrayList<>();
        for (JsonNode i : ayrinti.path("ihlaller")) {
            if ("UYARI".equals(i.path("siddet").asString())) {
                l.add(i.path("kod").asString() + ": " + i.path("metin").asString());
            }
        }
        return l;
    }

    /** Süzgeç için sefer kayıtlarının atama geçmişi (seferId -> kimlik sırasıyla ATAMA* kayıtları); yalnız iptal/gecikme/bölme/birleştirme kayıtları için okunur. */
    private Map<Long, List<Satir>> atamaGecmisi(List<Satir> ham) {
        Set<Long> seferler = ham.stream().filter(s -> seferKaydi(s.tur())).map(Satir::seferId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        return degisiklik.atamaGecmisi(seferler).stream().collect(Collectors.groupingBy(Satir::seferId));
    }

    /**
     * Kaydın makinist süzgecinde sayılacağı ek sicil kümesi: yerine atamada/kaldırmada ÇIKARILAN makinist ("ROL:SICIL" eski değerinden), sefer iptal/gecikme/bölme/birleştirmesinde
     * o anda seferde görevli olanlar (atama kayıtları kimlik sırasıyla yeniden oynatılır).
     */
    static Set<String> etkilenenSicil(Satir s, Map<Long, List<Satir>> gecmis) {
        Set<String> sonuc = new HashSet<>();
        if ((s.tur() == DegisiklikTuru.ATAMA_DEGISTIR || s.tur() == DegisiklikTuru.ATAMA_KALDIR) && s.eski() != null) {
            sonuc.add(sicilOku(s.eski()));
        } else if (seferKaydi(s.tur())) {
            Map<String, String> rolSicil = new LinkedHashMap<>();
            for (Satir g : gecmis.getOrDefault(s.seferId(), List.of())) {
                if (g.id() >= s.id()) {
                    break;
                }
                if (g.tur() == DegisiklikTuru.ATAMA_KALDIR && g.eski() != null) {
                    rolSicil.remove(g.eski().substring(0, g.eski().indexOf(':')));
                } else if (g.yeni() != null) {
                    rolSicil.put(g.yeni().substring(0, g.yeni().indexOf(':')), sicilOku(g.yeni()));
                }
            }
            sonuc.addAll(rolSicil.values());
        }
        return sonuc;
    }

    /** Seferdeki görevlileri etkileyen sefer kayıtları (makinist süzgecinde atama geçmişinden etkilenenler eklenir). */
    private static boolean seferKaydi(DegisiklikTuru tur) {
        return tur == DegisiklikTuru.SEFER_IPTAL || tur == DegisiklikTuru.SEFER_GECIKME || tur == DegisiklikTuru.SEFER_BOL || tur == DegisiklikTuru.SEFER_BIRLESTIR;
    }

    private static String sicilOku(String rolSicil) {
        return rolSicil.substring(rolSicil.indexOf(':') + 1);
    }

    /** Bir kaydın bölümü: her kayıt tam bir bölümde. */
    static String bolum(DegisiklikTuru tur, boolean uyarili) {
        if (uyarili) {
            return "UYARILI_ATAMALAR";
        }
        return switch (tur) {
            case ATAMA, OTOMATIK_ATAMA -> "ATAMALAR";
            case SEFER_IPTAL, SEFER_GECIKME -> "IPTAL_GECIKME";
            case ATAMA_DEGISTIR, ATAMA_KALDIR -> "ATAMA_DEGISIKLIKLERI";
            case PARAMETRE_DEGISTI, KURAL_EKLE, KURAL_GUNCELLE, PROFIL_UYGULA -> "PARAMETRE";
            case GORDUM -> GORDUM_BOLUMU;
            default -> "DIGER";
        };
    }

    private JsonNode ayrinti(Satir s) {
        if (s.ayrinti() == null || s.ayrinti().isBlank()) {
            return json.createObjectNode();
        }
        try {
            return json.readTree(s.ayrinti());
        } catch (RuntimeException e) { // ayrıntı JSON olmayan (eski/serbest) kayıt rapora yine girer
            return json.createObjectNode();
        }
    }

    /** Aynı rapor .xlsx: "Değişiklikler" sayfası (bölüm sütunuyla) + "Rapor bilgisi" sayfası (üretim zamanı, üreten, süzgeç, kayıt sayısı). */
    public byte[] excel(DegisiklikRaporuDto r) {
        List<List<Object>> satirlar = new ArrayList<>();
        for (Bolum b : r.bolumler()) {
            for (DegisiklikRaporuDto.Satir s : b.satirlar()) {
                satirlar.add(java.util.Arrays.asList(s.zaman().toLocalDateTime().toString().replace('T', ' '), s.kim(), b.ad(), s.turAdi(), s.sicil(), s.trenNo(),
                        s.seferTarihi() == null ? null : s.seferTarihi().toString(), s.eski(), s.yeni(), s.neden() == null ? null : s.neden().name(), s.nedenNotu(),
                        s.uyarili() ? "Evet" : "", s.uyariGerekcesi(), String.join("; ", s.uyarilar()), s.otomatik() ? "Evet" : ""));
            }
        }
        satirlar.sort(java.util.Comparator.comparing((List<Object> x) -> String.valueOf(x.get(0))));
        Filtre f = r.filtre();
        List<List<Object>> bilgi = List.of(
                List.of("Üretim zamanı", r.uretimZamani().toLocalDateTime().toString().replace('T', ' ')),
                List.of("Üreten", r.ureten()),
                List.of("Tarih aralığı", f.bas() + " – " + f.bit()),
                java.util.Arrays.asList("Makinist", f.makinist()),
                java.util.Arrays.asList("Tren no", f.trenNo()),
                java.util.Arrays.asList("Kullanıcı", f.kullanici()),
                java.util.Arrays.asList("Tür", f.tur() == null ? null : f.tur().name()),
                List.of("Kayıt sayısı", r.kayitSayisi()));
        return TabloExcel.yaz(List.of(
                new TabloExcel.Sayfa("Değişiklikler", List.of("Zaman", "Kim", "Bölüm", "Tür", "Sicil", "Tren no", "Sefer tarihi", "Eski", "Yeni", "Neden", "Not",
                        "Uyarılı", "Uyarı gerekçesi", "Uyaran kural", "Otomatik"), satirlar),
                new TabloExcel.Sayfa("Rapor bilgisi", List.of("Alan", "Değer"), bilgi)));
    }
}
