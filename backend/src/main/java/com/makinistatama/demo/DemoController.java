package com.makinistatama.demo;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.makinistatama.guvenlik.SadeceYonetici;

/** Yalnız {@code dev} profilinde ve yalnız YONETICI: demo veriyi yükler / sıfırlar. Prod'da bu uçlar yoktur (404). */
@RestController
@Profile("dev")
@RequestMapping("/api/demo")
@SadeceYonetici
public class DemoController {

    private final DemoVeriServisi servis;

    DemoController(DemoVeriServisi servis) {
        this.servis = servis;
    }

    /** Arayüzün demo düğmesi için: veritabanında veri var mı (yüklenebilir mi). Prod'da bu uç da yoktur (404). */
    @GetMapping("/durum")
    public DemoVeriServisi.Durum durum() {
        return servis.durum();
    }

    @PostMapping("/yukle")
    @ResponseStatus(HttpStatus.CREATED)
    public DemoVeriServisi.Sonuc yukle() {
        return servis.yukle();
    }

    /** F2b: demo veriye birkaç yolculuk (D001..D003) ve bir konaklama örneği ekler. */
    @PostMapping("/yolculuk-ornekleri")
    @ResponseStatus(HttpStatus.CREATED)
    public java.util.Map<String, Integer> yolculukOrnekleri() {
        return java.util.Map.of("yolculuk", servis.yolculukOrnekleri());
    }

    /** F2c: demo veriye ara istasyonda makinist değişen bir tren (iki parça) ekler; temel demo yüklemesi değişmez. */
    @PostMapping("/parcali-ornek")
    @ResponseStatus(HttpStatus.CREATED)
    public java.util.Map<String, Integer> parcaliOrnek() {
        return java.util.Map.of("parca", servis.parcaliOrnek());
    }

    /** F3b: demo veriye 4 kondüktör (K001..K004), 3 kondüktör ataması ve "yolcu trenine kondüktör gerekli" ayarı ekler; temel demo yüklemesi değişmez. */
    @PostMapping("/konduktor-ornegi")
    @ResponseStatus(HttpStatus.CREATED)
    public DemoVeriServisi.KonduktorSonucu konduktorOrnegi() {
        return servis.konduktorOrnegi();
    }

    /** İ-2: demo veriye gar çifti intikal süreleri ve bir serbest intikal örneği ekler (isteğe bağlı; "yolculukla ata"da servis satırını gösterir). Temel demo yüklemesi değişmez. */
    @PostMapping("/intikal-ornek")
    @ResponseStatus(HttpStatus.CREATED)
    public DemoVeriServisi.IntikalOrnekSonucu intikalOrnek() {
        return servis.intikalOrnek();
    }

    @PostMapping("/sifirla")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void sifirla() {
        servis.sifirla();
    }
}
