package com.makinistatama.guvenlik;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;
import com.makinistatama.kullanici.KullaniciIstekleri;
import com.makinistatama.kullanici.Rol;
import com.makinistatama.ortak.UygulamaHatasi;

/**
 * MK-25 / NFR-M01: varsayılan-ret kapısı. Her {@code /api/**} ucu tam bir rol beyanı taşır; beyansız uç herkese 403'tür; yeni uç beyansız
 * eklenirse {@link #herApiUcuTamBirBeyanTasir} kırılır. (Mevcut {@code GuvenlikTaramaTest.ucNoktalar()} yöntemsiz ve {@code /api} dışı eşlemeleri
 * atladığı için burada kullanılmaz.)
 */
class YetkiKapisiTest extends TestVeritabani {

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping eslemeler;

    private final YetkiOnKontrol kapi = new YetkiOnKontrol();

    @AfterEach
    void oturumuKapat() {
        TestOturumu.kapat();
    }

    // ------------------------------------------------------------ sahte controller'lar (ortak bağlama eklenmez)

    @Personel
    static class SinifPersonel {
        @GetMapping("/api/x/a")
        public void sinifaGore() {
        }

        @GetMapping("/api/x/b")
        @SadeceYonetici
        public void metotGecersizKilar() {
        }
    }

    static class Beyansiz {
        @GetMapping("/api/x/c")
        public void yok() {
        }
    }

    static class AcikUc {
        @GetMapping("/api/x/d")
        @Acik
        public void acik() {
        }

        @GetMapping("/api/x/e")
        @HerkesGiris
        public void herkes() {
        }
    }

    @Personel
    @Acik
    static class CiftIsaret {
        @GetMapping("/api/x/f")
        public void cift() {
        }
    }

    private static HandlerMethod uc(Object bean, String metot) throws Exception {
        return new HandlerMethod(bean, bean.getClass().getMethod(metot));
    }

    private boolean gecer(HandlerMethod m) {
        return kapi.preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(), m);
    }

    private void oturum(Rol rol) {
        String eposta = rol.name().toLowerCase() + "@ornek.test";
        if (kullanicilar.epostaIle(rol == Rol.MAKINIST ? "d001" : eposta).isEmpty()) {
            if (rol == Rol.MAKINIST) {
                kullanicilar.makinistHesabiAc(1L, "d001", "Ad Soyad", "gecici-parola-123");
            } else {
                kullanicilar.olustur(new KullaniciIstekleri.Olustur(eposta, "Ad Soyad", rol, Tarayici.PAROLA));
            }
        }
        TestOturumu.ac(kullanicilar.epostaIle(rol == Rol.MAKINIST ? "d001" : eposta).orElseThrow());
    }

    // ------------------------------------------------------------ çözüm ve preHandle

    @Test
    void cozum_metotSinifaGoreOncelikli() throws Exception {
        assertThat(YetkiOnKontrol.beyan(uc(new SinifPersonel(), "sinifaGore"))).contains(Personel.class);
        assertThat(YetkiOnKontrol.beyan(uc(new SinifPersonel(), "metotGecersizKilar"))).contains(SadeceYonetici.class);
        assertThat(YetkiOnKontrol.beyan(uc(new Beyansiz(), "yok"))).isEmpty();
    }

    @Test
    void taramaBeyansizUcuYakalar() throws Exception {
        assertThat(YetkiOnKontrol.beyan(uc(new Beyansiz(), "yok"))).as("tarama testinin kullandığı çözüm beyansız ucu boş döndürür").isEmpty();
        assertThat(YetkiOnKontrol.isaretler(CiftIsaret.class)).as("aynı yerde iki işaret yasak, tarama bunu sayar").hasSize(2);
    }

    @Test
    void beyansizUc_403_herkeseVe_yonetici_dahil() throws Exception {
        for (Rol rol : Rol.values()) {
            oturum(rol);
            assertThatThrownBy(() -> gecer(uc(new Beyansiz(), "yok")))
                    .isInstanceOfSatisfying(UygulamaHatasi.class, e -> {
                        assertThat(e.durum()).isEqualTo(HttpStatus.FORBIDDEN);
                        assertThat(e.kod()).isEqualTo("UC_BEYANSIZ");
                    });
        }
    }

    @Test
    void preHandle_rolDisi403_acikAtlanir_isleyiciOlmayanGecer() throws Exception {
        oturum(Rol.ATAMACI);
        assertThat(gecer(uc(new SinifPersonel(), "sinifaGore"))).isTrue();
        assertThatThrownBy(() -> gecer(uc(new SinifPersonel(), "metotGecersizKilar"))).isInstanceOf(AccessDeniedException.class);
        assertThat(gecer(uc(new AcikUc(), "herkes"))).isTrue();
        oturum(Rol.YONETICI);
        assertThat(gecer(uc(new SinifPersonel(), "metotGecersizKilar"))).isTrue();
        TestOturumu.kapat();
        assertThat(gecer(uc(new AcikUc(), "acik"))).as("@Acik oturumsuz geçer").isTrue();
        assertThatThrownBy(() -> gecer(uc(new AcikUc(), "herkes"))).as("oturumsuz @HerkesGiris reddedilir").isInstanceOf(AccessDeniedException.class);
        assertThat(kapi.preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(), new Object())).isTrue();
    }

    @SadeceMakinist
    static class MakinistUc {
        @GetMapping("/api/x/g")
        public void uc() {
        }
    }

    @Test
    void bayrakliHesapYalnizHerkesGirisUclarindaGecer_diger403ParolaDegistir() throws Exception {
        oturum(Rol.MAKINIST); // makinistHesabiAc: geçici parola, bayrak açık
        assertThat(gecer(uc(new AcikUc(), "herkes"))).as("@HerkesGiris: kimlik, çıkış, parola").isTrue();
        assertThatThrownBy(() -> gecer(uc(new MakinistUc(), "uc")))
                .isInstanceOfSatisfying(UygulamaHatasi.class, e -> {
                    assertThat(e.durum()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(e.kod()).isEqualTo("PAROLA_DEGISTIR");
                });
        jdbc.update("UPDATE kullanici SET parola_degistirmeli = FALSE WHERE rol = 'MAKINIST'");
        oturum(Rol.MAKINIST);
        assertThat(gecer(uc(new MakinistUc(), "uc"))).isTrue();
        oturum(Rol.ATAMACI);
        assertThatThrownBy(() -> gecer(uc(new MakinistUc(), "uc"))).as("personel makinist ucuna giremez").isInstanceOf(AccessDeniedException.class);
    }

    // ------------------------------------------------------------ gerçek eşlemeler

    private record Esleme(String desen, List<RequestMethod> yontemler, HandlerMethod islem) {
    }

    private List<Esleme> tumEslemeler() {
        List<Esleme> sonuc = new ArrayList<>();
        eslemeler.getHandlerMethods().forEach((bilgi, islem) -> {
            for (String desen : bilgi.getPathPatternsCondition().getPatternValues()) {
                sonuc.add(new Esleme(desen, List.copyOf(bilgi.getMethodsCondition().getMethods()), islem));
            }
        });
        return sonuc;
    }

    @Test
    void herApiUcuTamBirBeyanTasir() {
        List<String> sorunlar = new ArrayList<>();
        for (Esleme e : tumEslemeler()) {
            if (e.desen().equals("/error")) {
                continue;
            }
            String ad = e.desen() + " (" + e.islem().getShortLogMessage() + ")";
            if (!e.desen().startsWith("/api/")) {
                sorunlar.add(ad + ": /api/ dışında eşleme (yetki kapısının dışında kalır)");
            }
            if (e.yontemler().isEmpty()) {
                sorunlar.add(ad + ": HTTP yöntemi belirtilmemiş (ANY yasak)");
            }
            if (YetkiOnKontrol.isaretler(e.islem().getMethod()).size() > 1) {
                sorunlar.add(ad + ": metotta birden çok beyan");
            }
            if (YetkiOnKontrol.isaretler(e.islem().getBeanType()).size() > 1) {
                sorunlar.add(ad + ": sınıfta birden çok beyan");
            }
            if (YetkiOnKontrol.beyan(e.islem()).isEmpty()) {
                sorunlar.add(ad + ": rol beyanı yok");
            }
        }
        assertThat(sorunlar).isEmpty();
        assertThat(tumEslemeler()).as("eşlemeler bulundu").hasSizeGreaterThanOrEqualTo(GuvenlikTaramaTest.ROLLER.size());
    }

    /**
     * MK-28 / NFR-M01: {@code /api/benim/**} uçları yalnız {@code benim} paketindeki controller'larda, yalnız sınıf düzeyinde {@code @SadeceMakinist} ile; hiçbir uç yol değişkeni,
     * sorgu parametresi ya da gövde alanı olarak makinist/kullanıcı kimliği almaz (kimlik oturumdan). Kaynak kimliği ({@code atamaId} gibi) serbesttir, sahiplik serviste süzülür.
     */
    @Test
    void benimUclariKimlikAlaniTasimaz() {
        java.util.regex.Pattern yasak = java.util.regex.Pattern.compile("(?i).*(makinist|kullanici).*");
        List<String> sorunlar = new ArrayList<>();
        int benimUc = 0;
        for (Esleme e : tumEslemeler()) {
            boolean benimYolu = e.desen().startsWith("/api/benim/") || e.desen().equals("/api/benim");
            boolean benimPaketi = e.islem().getBeanType().getPackageName().equals("com.makinistatama.benim");
            if (benimYolu != benimPaketi) {
                sorunlar.add(e.desen() + ": /api/benim yolu ile benim paketi birlikte olmalı");
            }
            if (!benimYolu) {
                continue;
            }
            benimUc++;
            if (!org.springframework.core.annotation.AnnotatedElementUtils.hasAnnotation(e.islem().getBeanType(), SadeceMakinist.class)) {
                sorunlar.add(e.desen() + ": sınıf düzeyinde @SadeceMakinist yok");
            }
            if (!YetkiOnKontrol.isaretler(e.islem().getMethod()).isEmpty()) {
                sorunlar.add(e.desen() + ": benim metodunda işaret yasak (yetkiyi genişletebilir)");
            }
            for (org.springframework.core.MethodParameter prm : e.islem().getMethodParameters()) {
                boolean istekAlani = prm.hasParameterAnnotation(org.springframework.web.bind.annotation.PathVariable.class)
                        || prm.hasParameterAnnotation(org.springframework.web.bind.annotation.RequestParam.class)
                        || prm.hasParameterAnnotation(org.springframework.web.bind.annotation.RequestBody.class);
                if (!istekAlani) {
                    continue;
                }
                if (prm.hasParameterAnnotation(org.springframework.web.bind.annotation.RequestBody.class)) {
                    if (!prm.getParameterType().isRecord()) {
                        sorunlar.add(e.desen() + ": gövde record olmalı (alan adları denetlenir)");
                        continue;
                    }
                    for (var bilesen : prm.getParameterType().getRecordComponents()) {
                        if (yasak.matcher(bilesen.getName()).matches()) {
                            sorunlar.add(e.desen() + ": gövde alanı kimlik taşıyor: " + bilesen.getName());
                        }
                    }
                } else {
                    String ad = prm.getParameterName();
                    if (ad == null) {
                        sorunlar.add(e.desen() + ": parametre adı okunamadı (-parameters kapalı?)");
                    } else if (yasak.matcher(ad).matches()) {
                        sorunlar.add(e.desen() + ": istek parametresi kimlik taşıyor: " + ad);
                    }
                }
            }
        }
        assertThat(sorunlar).isEmpty();
        assertThat(benimUc).as("benim uçları bulundu").isGreaterThanOrEqualTo(5);
    }

    @Test
    void beyanlar_rolTablosuylaTutarli() {
        Map<String, String> tablo = new TreeMap<>(GuvenlikTaramaTest.ROLLER);
        tablo.putAll(GuvenlikTaramaTest.DEV_UCLARI);
        List<String> uyumsuz = new ArrayList<>();
        for (Esleme e : tumEslemeler()) {
            for (RequestMethod y : e.yontemler()) {
                String anahtar = y.name() + " " + e.desen();
                String sinif = tablo.get(anahtar);
                if (sinif == null) {
                    continue; // tabloda olmayan uçları GuvenlikTaramaTest yakalar
                }
                Class<? extends Annotation> beyan = YetkiOnKontrol.beyan(e.islem()).orElse(null);
                boolean tamam = switch (sinif) {
                    case "A" -> beyan == Acik.class;
                    case "Y" -> beyan == SadeceYonetici.class;
                    case "M" -> beyan == SadeceMakinist.class;
                    case "G" -> e.desen().startsWith("/api/auth/") ? beyan == HerkesGiris.class : beyan == Personel.class;
                    default -> false;
                };
                if (!tamam) {
                    uyumsuz.add(anahtar + " tablo " + sinif + ", beyan " + (beyan == null ? "yok" : beyan.getSimpleName()));
                }
            }
        }
        assertThat(uyumsuz).isEmpty();
    }

    @Test
    void anonimActuatorEnvAcikDegil() throws Exception {
        int durum = new Tarayici(mvc).get("/actuator/env").andReturn().getResponse().getStatus();
        assertThat(durum).isIn(401, 403, 404);
    }
}
