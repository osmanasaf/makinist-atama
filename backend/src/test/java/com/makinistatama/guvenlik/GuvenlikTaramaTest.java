package com.makinistatama.guvenlik;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;

import jakarta.servlet.http.Cookie;

/**
 * T8.6: rol/uç tablosu. {@code RequestMappingHandlerMapping} ile {@code /api/**} uç noktaları numaralandırılır; her biri aşağıdaki tabloda
 * sınıflandırılmış olmalıdır (yeni uç eklenip sınıflandırılmazsa test kırılır). Sınıf: {@code A} anonim açık (yalnız giriş), {@code G} giriş yapmış personel (ATAMACI+YONETICI; /api/auth/* tüm roller), {@code M} yalnız MAKINIST,
 * {@code Y} yalnız YONETICI ({@code @SadeceYonetici}). Anonim her {@code G/Y} uç 401 alır; hata gövdesinde kişisel veri/yığın yoktur.
 */
class GuvenlikTaramaTest extends TestVeritabani {

    static final Map<String, String> ROLLER = Map.ofEntries(
            Map.entry("POST /api/auth/giris", "A"),
            Map.entry("POST /api/auth/cikis", "G"),
            Map.entry("GET /api/auth/ben", "G"),
            Map.entry("GET /api/kullanicilar", "Y"), Map.entry("POST /api/kullanicilar", "Y"), Map.entry("PUT /api/kullanicilar/{id}", "Y"),
            Map.entry("POST /api/kullanicilar/{id}/parola", "Y"),
            Map.entry("GET /api/tanimlar", "G"), Map.entry("POST /api/tanimlar", "Y"), Map.entry("PUT /api/tanimlar/{id}", "Y"),
            Map.entry("GET /api/parametre", "G"), Map.entry("GET /api/kural", "G"),
            Map.entry("GET /api/ozel-kural", "G"), Map.entry("GET /api/ozel-kural/katalog", "G"), Map.entry("POST /api/ozel-kural", "Y"),
            Map.entry("PUT /api/ozel-kural/{id}", "Y"), Map.entry("POST /api/ozel-kural/dene", "Y"), Map.entry("POST /api/rapor/kadro", "Y"), Map.entry("POST /api/rapor/kadro/excel", "Y"),
            Map.entry("PUT /api/parametre/{anahtar}", "Y"), Map.entry("PUT /api/kural/{kod}/siddet", "Y"),
            // F2 kural profili paketi (yeni-ozellikler Tur 1, MK-163): hepsi yalnız yönetici
            Map.entry("GET /api/profil/disa-aktar", "Y"), Map.entry("GET /api/profil/hazir", "Y"), Map.entry("GET /api/profil/hazir/{kod}", "Y"),
            Map.entry("POST /api/profil/onizle", "Y"), Map.entry("POST /api/profil/uygula", "Y"),
            // F1 kurum adı + logo (yeni-ozellikler Tur 2, MK-167/168): okuma ANONİM (A, yalnız bu iki GET), yazma yalnız yönetici
            Map.entry("GET /api/kurum/acik", "A"), Map.entry("GET /api/kurum/logo", "A"),
            Map.entry("PUT /api/kurum", "Y"), Map.entry("PUT /api/kurum/logo", "Y"), Map.entry("DELETE /api/kurum/logo", "Y"),
            // F1 makinist toplu içe aktarma (MK-170): yalnız yönetici
            Map.entry("POST /api/makinist/ice-aktar", "Y"), Map.entry("GET /api/makinist/ice-aktar/sablon", "Y"),
            Map.entry("GET /api/kurulum/durum", "Y"), Map.entry("PUT /api/kurulum/sihirbaz-gizle", "Y"),
            Map.entry("GET /api/makinist", "G"), Map.entry("GET /api/makinist/{id}", "G"), Map.entry("POST /api/makinist", "G"),
            Map.entry("PUT /api/makinist/{id}", "G"), Map.entry("POST /api/makinist/{id}/izin", "G"),
            Map.entry("GET /api/sefer", "G"), Map.entry("GET /api/sefer/{id}", "G"), Map.entry("POST /api/sefer", "G"), Map.entry("PUT /api/sefer/{id}", "G"),
            Map.entry("POST /api/sefer/yukle", "G"), Map.entry("GET /api/sefer/sablon", "G"),
            Map.entry("GET /api/atama/sefer/{seferId}/adaylar", "G"), Map.entry("POST /api/atama/sefer/{seferId}", "G"),
            Map.entry("DELETE /api/atama/sefer/{seferId}", "G"), Map.entry("GET /api/atama/gun", "G"),
            Map.entry("GET /api/atama/makinist/{makinistId}/izin-etkisi", "G"),
            Map.entry("POST /api/sefer/{id}/iptal", "G"), Map.entry("POST /api/sefer/{id}/gecikme", "G"),
            Map.entry("GET /api/atama/sefer/{seferId}/oneri", "G"),
            // F3 (yeni-ozellikler Tur 3, MK-174): sınıra yakınlık + yorgunluk bilgisi, yalnız personel (makinist 403: OK-35)
            Map.entry("GET /api/atama/sefer/{seferId}/yakinlik", "G"), Map.entry("GET /api/atama/yakinlik", "G"),
            Map.entry("GET /api/atama/sefer/{seferId}/yolculuk-oner", "G"), Map.entry("POST /api/atama/sefer/{seferId}/yolculukla", "G"),
            Map.entry("POST /api/atama/sefer/{id}/bol", "G"),
            Map.entry("POST /api/atama/sefer/{id}/birlestir", "G"),
            // F2b: konaklama (personel) ve makinistin kendi konaklaması (M)
            Map.entry("GET /api/konaklama", "G"), Map.entry("POST /api/konaklama", "G"), Map.entry("PUT /api/konaklama/{id}", "G"), Map.entry("DELETE /api/konaklama/{id}", "G"),
            Map.entry("GET /api/konaklama/oneri", "G"), Map.entry("GET /api/konaklama/rapor", "G"), Map.entry("GET /api/konaklama/rapor/excel", "G"),
            Map.entry("GET /api/benim/konaklama", "M"), Map.entry("GET /api/atama/kirmizi-sayisi", "G"),
            // serbest intikal (İ-1, MK-106/MK-108): personel yazar / okur; makinist yalnız kendi kaydını "gördüm" der
            Map.entry("GET /api/intikal", "G"), Map.entry("POST /api/intikal", "G"), Map.entry("PUT /api/intikal/{id}", "G"), Map.entry("DELETE /api/intikal/{id}", "G"),
            Map.entry("POST /api/benim/intikal/{id}/gordum", "M"),
            // İ-2: gar süre tablosu (okuma personel, yazma yönetici) ve "intikalle ata"
            Map.entry("GET /api/tanimlar/intikal-sure", "G"), Map.entry("PUT /api/tanimlar/intikal-sure", "Y"), Map.entry("DELETE /api/tanimlar/intikal-sure", "Y"),
            Map.entry("POST /api/atama/sefer/{seferId}/intikalle", "G"),
            Map.entry("GET /api/atama/gordum-ozeti", "G"), Map.entry("GET /api/atama/makinist/{makinistId}/uygun-seferler", "G"), Map.entry("POST /api/atama/otomatik", "G"),
            Map.entry("GET /api/degisiklik", "G"), Map.entry("GET /api/degisiklik/excel", "G"),
            Map.entry("GET /api/rapor/belge-durumu", "G"),
            Map.entry("GET /api/rapor/adalet", "G"), Map.entry("GET /api/rapor/adalet/excel", "G"), Map.entry("GET /api/rapor/yedek", "G"),
            Map.entry("GET /api/rapor/puantaj", "G"), Map.entry("GET /api/rapor/puantaj/excel", "G"),
            Map.entry("GET /api/izin", "G"), Map.entry("POST /api/izin", "G"), Map.entry("POST /api/izin/{id}/onay", "G"), Map.entry("POST /api/izin/{id}/red", "G"),
            // F2a: makinist hesabı (personel) ve kendi parolası (tüm roller; /api/auth/* altında)
            Map.entry("GET /api/makinist/{id}/hesap", "G"), Map.entry("POST /api/makinist/{id}/hesap", "G"),
            Map.entry("POST /api/makinist/{id}/hesap/parola-sifirla", "G"), Map.entry("POST /api/makinist/{id}/hesap/kilit-ac", "G"),
            Map.entry("POST /api/auth/parola", "G"), Map.entry("PUT /api/ayar/{anahtar}", "Y"), Map.entry("GET /api/ayar", "Y"),
            // M = yalnız MAKINIST (@SadeceMakinist, /api/benim/**)
            Map.entry("GET /api/benim/gorevler", "M"), Map.entry("GET /api/benim/belgeler", "M"), Map.entry("GET /api/benim/ozet", "M"),
            Map.entry("GET /api/benim/izinler", "M"), Map.entry("POST /api/benim/izinler", "M"),
            Map.entry("POST /api/benim/gorevler/{atamaId}/gordum", "M"), Map.entry("POST /api/benim/olaylar/{olayId}/gordum", "M"),
            // F2a-3: makinist takas / bırakma uçları (M) ve atamacı onay uçları (G)
            Map.entry("GET /api/benim/takas", "M"), Map.entry("GET /api/benim/takas/hedefler", "M"), Map.entry("POST /api/benim/takas", "M"),
            Map.entry("POST /api/benim/takas/{id}/kabul", "M"), Map.entry("POST /api/benim/takas/{id}/red", "M"), Map.entry("POST /api/benim/takas/{id}/iptal", "M"),
            Map.entry("GET /api/takas", "G"), Map.entry("GET /api/takas/{id}", "G"), Map.entry("POST /api/takas/{id}/onay", "G"), Map.entry("POST /api/takas/{id}/red", "G"));

    /** Yalnız dev profilinde kayıtlı (testte yok olabilir) ama varsa YONETICI olmalı. */
    static final Map<String, String> DEV_UCLARI = Map.of("POST /api/demo/yukle", "Y", "POST /api/demo/sifirla", "Y", "GET /api/demo/durum", "Y", "POST /api/demo/yolculuk-ornekleri", "Y", "POST /api/demo/intikal-ornek", "Y");

    @Autowired
    @org.springframework.beans.factory.annotation.Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping eslemeler;

    record Uc(String yontem, String desen, HandlerMethod islem) {
        String anahtar() {
            return yontem + " " + desen;
        }
    }

    private List<Uc> ucNoktalar() {
        List<Uc> sonuc = new ArrayList<>();
        eslemeler.getHandlerMethods().forEach((bilgi, islem) -> {
            var desenler = bilgi.getPathPatternsCondition().getPatternValues();
            var yontemler = bilgi.getMethodsCondition().getMethods();
            for (String desen : desenler) {
                if (!desen.startsWith("/api/")) {
                    continue;
                }
                for (RequestMethod y : yontemler) {
                    sonuc.add(new Uc(y.name(), desen, islem));
                }
            }
        });
        return sonuc;
    }

    private static boolean yoneticiOzel(HandlerMethod h) {
        return AnnotatedElementUtils.hasAnnotation(h.getMethod(), SadeceYonetici.class)
                || AnnotatedElementUtils.hasAnnotation(h.getBeanType(), SadeceYonetici.class);
    }

    @Test
    void herApiUcuSiniflandirilmis_yoneticiOzelOlanlarSadeceYoneticiIsaretli() {
        Map<String, String> beklenen = new TreeMap<>(ROLLER);
        beklenen.putAll(DEV_UCLARI);
        List<String> siniflanmamis = new ArrayList<>();
        List<String> yanlisIsaret = new ArrayList<>();
        for (Uc uc : ucNoktalar()) {
            String rol = beklenen.get(uc.anahtar());
            if (rol == null) {
                siniflanmamis.add(uc.anahtar());
            } else if (rol.equals("Y") != yoneticiOzel(uc.islem())) {
                yanlisIsaret.add(uc.anahtar() + " (tablo " + rol + ", @SadeceYonetici " + yoneticiOzel(uc.islem()) + ")");
            }
        }

        assertThat(siniflanmamis).as("rol tablosunda olmayan uçlar (GuvenlikTaramaTest.ROLLER'e ekleyin)").isEmpty();
        assertThat(yanlisIsaret).isEmpty();
        assertThat(ucNoktalar()).as("tablodaki kayıtlı uçlar bulundu").hasSizeGreaterThanOrEqualTo(ROLLER.size());
    }

    /** MK-168: anonim erişime açık uçların kümesi TAM eşitlik; üçüncü bir anonim uç eklenirse (ya da biri kapanırsa) bu test kırılır. */
    @Test
    void anonimAcikUclarKumesi_tamOlarakUc() {
        List<String> acik = new ArrayList<>();
        for (Uc uc : ucNoktalar()) {
            if (YetkiOnKontrol.beyan(uc.islem()).filter(b -> b == Acik.class).isPresent()) {
                acik.add(uc.anahtar());
            }
        }
        assertThat(acik).containsExactlyInAnyOrder("POST /api/auth/giris", "GET /api/kurum/acik", "GET /api/kurum/logo");
        assertThat(ROLLER.entrySet().stream().filter(e -> "A".equals(e.getValue())).map(Map.Entry::getKey)).containsExactlyInAnyOrderElementsOf(acik);
    }

    @Test
    void anonimIstekler_401_govdedeKisiselVeriVeYiginYok() throws Exception {
        yonetici(); // kayıtlı e-posta var: yanıtta geçmemeli
        Tarayici anonim = new Tarayici(mvc);
        for (Uc uc : ucNoktalar()) {
            if (ROLLER.getOrDefault(uc.anahtar(), DEV_UCLARI.get(uc.anahtar())) == null || "A".equals(ROLLER.get(uc.anahtar()))) {
                continue;
            }
            String yol = uc.desen().replaceAll("\\{[^}]+}", "1");
            var istek = switch (uc.yontem()) {
                case "GET" -> MockMvcRequestBuilders.get(yol);
                case "POST" -> MockMvcRequestBuilders.post(yol).contentType("application/json").content("{}");
                case "PUT" -> MockMvcRequestBuilders.put(yol).contentType("application/json").content("{}");
                case "DELETE" -> MockMvcRequestBuilders.delete(yol);
                default -> throw new IllegalStateException(uc.anahtar());
            };
            var yanit = anonim.istek(istek).andReturn().getResponse();
            assertThat(yanit.getStatus()).as(uc.anahtar()).isEqualTo(401);
            String govde = yanit.getContentAsString();
            assertThat(govde).as(uc.anahtar()).doesNotContain("@ornek.test").doesNotContain("Exception").doesNotContain("com.makinistatama").doesNotContain("\tat ");
        }
    }

    @Test
    void atamaciYoneticiUclarinda403_veYazma_kimlikliCerezOlmadanCsrf403() throws Exception {
        Tarayici a = atamaci();
        a.get("/api/kullanicilar").andReturn().getResponse().getStatus();
        assertThat(a.get("/api/kullanicilar").andReturn().getResponse().getStatus()).isEqualTo(403);
        // CSRF belirteci olmadan yazma isteği oturumlu olsa da reddedilir
        var yanit = mvc.perform(MockMvcRequestBuilders.post("/api/atama/sefer/1").session(a.oturum).contentType("application/json").content("{}")
                .cookie(new Cookie("XSRF-TOKEN", "x"))).andReturn().getResponse();
        assertThat(yanit.getStatus()).isEqualTo(403);
    }
}
