package com.makinistatama.atama;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import com.makinistatama.degisiklik.DegisiklikServisi;
import com.makinistatama.degisiklik.DegisiklikServisi.Kayit;
import com.makinistatama.degisiklik.DegisiklikTuru;
import com.makinistatama.makinist.MakinistServisi;
import com.makinistatama.ortak.UygulamaHatasi;
import com.makinistatama.sefer.BirlestirSonucu;
import com.makinistatama.sefer.BolSonucu;
import com.makinistatama.sefer.SeferBolIstegi;
import com.makinistatama.sefer.SeferDto;
import com.makinistatama.sefer.SeferParcaServisi;
import com.makinistatama.sefer.SeferServisi;
import com.makinistatama.sefer.Zincir;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Seferi ekrandan bölme orkestratörü (MK-121/123/124/127): sefer çekirdeği ({@link SeferParcaServisi}) atama bilmez; atamalar, makinist kilidi, "yeni kırmızı" farkı ve denetim kaydı
 * burada. Atamalar parça 1'de kalır (MK-122). Kilit sırası makinist (artan kimlik) -> yazma; sefer satırı için pessimistic kilit YOK ({@code @Version} + tekillik indeksi, MK-123).
 * {@code onizle}: aynı yol uçtan uca koşar, işlem sonunda geri alınır (MK-124).
 */
@Service
public class SeferParcalamaServisi {

    private final SeferParcaServisi cekirdek;
    private final SeferServisi seferler;
    private final AtamaRepository atamalar;
    private final MakinistServisi makinistler;
    private final KirmiziEtkisi etki;
    private final DegisiklikServisi degisiklik;
    private final AtamaSaati saat;
    private final JsonMapper json;
    private final GorevGordumRepository gordumlar;

    SeferParcalamaServisi(SeferParcaServisi cekirdek, SeferServisi seferler, AtamaRepository atamalar, MakinistServisi makinistler, KirmiziEtkisi etki,
            DegisiklikServisi degisiklik, AtamaSaati saat, JsonMapper json, GorevGordumRepository gordumlar) {
        this.cekirdek = cekirdek;
        this.seferler = seferler;
        this.atamalar = atamalar;
        this.makinistler = makinistler;
        this.etki = etki;
        this.degisiklik = degisiklik;
        this.saat = saat;
        this.json = json;
        this.gordumlar = gordumlar;
    }

    @Transactional
    public BolSonucuDto bol(long id, SeferBolIstegi istek) {
        SeferDto capa = seferler.getir(id); // 404
        TreeSet<Long> kume = makinistKumesi(id);
        makinistler.kilitle(kume); // MK-7: artan kimlik; küme boşsa kilit yok
        if (!kume.equals(makinistKumesi(id))) { // kilit beklerken bir atama eklendi/kaldırıldı/değişti
            throw UygulamaHatasi.cakisma("ATAMA_DEGISTI", "Seferin atamaları az önce değişti; yeniden deneyin.");
        }
        var once = etki.once(kume, capa, a -> false);
        BolSonucu bolunen;
        KaldirSonucuDto fark;
        try {
            bolunen = cekirdek.bol(id, istek, saat.simdi());
            fark = etki.sonra(once);
        } catch (DataIntegrityViolationException e) { // eşzamanlı ikinci bölme / tekillik: çekirdek kendi yazımında çevirir, bu ağ bırakılan yoldur
            throw UygulamaHatasi.cakisma("SEFER_TEKRARI", "Sefer az önce başka bir işlemle değişti; yeniden deneyin. Hiçbir değişiklik yapılmadı.");
        }
        degisiklik.kaydet(Kayit.of(DegisiklikTuru.SEFER_BOL, "SEFER", bolunen.parca1().id()).sefer(bolunen.parca1().id()).degisim(bolunen.eskiOzet(), bolunen.yeniOzet())
                .ayrinti(ayrinti(istek, bolunen, kume.size())));
        if (istek.onizle()) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly(); // MK-124: kayıt da (MANDATORY, aynı işlem) geri gider
        }
        return new BolSonucuDto(bolunen.parcalar(), fark.yeniKirmizi());
    }

    /**
     * Zinciri parça 1 satırına geri döndürür (MK-122/123/126/127). Sıra: sefer koşulları -> atama koşulları ([V9]) -> makinist kilitleri (zincirin tüm atamalı makinistleri, artan kimlik)
     * -> parça 2..N satır kilidi (artan kimlik) + atamaları yeniden oku (küme değiştiyse 409 {@code ATAMA_DEGISTI}) -> "önce" kırmızı -> atama satırlarını sil + gördüm temizliği
     * -> çekirdek birleştirme (parça 2..N silinir) -> "sonra" -> tek {@code SEFER_BIRLESTIR} kaydı -> {@code onizle} ise geri al. Silinen atamalar için ayrı ATAMA_KALDIR yazılmaz
     * (aynı makinist parça 1'de kalır; ayrıntı tek kayıtta).
     */
    @Transactional
    public BirlestirSonucuDto birlestir(long id, SeferBirlestirIstegi istek) {
        Zincir zincir = cekirdek.birlestirKontrol(id, istek.versiyon(), saat.simdi());
        List<AtamaKisa> ilkOkuma = atamalar.seferKisalar(zincir.idler()); // projeksiyon: kilit sonrası yeniden okuma önbelleğe takılmaz
        atamaKosullari(zincir, ilkOkuma);
        TreeSet<Long> kume = ilkOkuma.stream().map(AtamaKisa::makinistId).collect(Collectors.toCollection(TreeSet::new));
        makinistler.kilitle(kume); // MK-7: artan kimlik; küme boşsa kilit yok
        zincir = cekirdek.birlestirKilitle(zincir); // MK-123: parça 2..N satırları, artan kimlik
        if (!imza(ilkOkuma).equals(imza(atamalar.seferKisalar(zincir.idler())))) { // kilit beklerken bir atama eklendi/kaldırıldı/değişti
            throw UygulamaHatasi.cakisma("ATAMA_DEGISTI", "Seferin atamaları az önce değişti; yeniden deneyin.");
        }

        List<Atama> silinen = atamalar.findBySeferIdIn(zincir.silinecekIdler());
        Set<Long> silinenIdler = silinen.stream().map(Atama::id).collect(Collectors.toSet());
        var once = etki.once(kume, seferler.getir(zincir.parca1Id()), a -> silinenIdler.contains(a.id())); // silinenler "sonra"dan çıkar: sefer satırları yok olacak
        Map<Long, String> sicil = new LinkedHashMap<>();
        silinen.forEach(a -> sicil.computeIfAbsent(a.makinistId(), m -> makinistler.getir(m).sicil()));
        List<Map<String, Object>> silinenAyrinti = new ArrayList<>();
        for (Atama a : silinen) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("atamaId", a.id());
            m.put("seferId", a.seferId());
            m.put("parcaNo", zincir.idler().indexOf(a.seferId()) + 1);
            m.put("rol", a.rol().name());
            m.put("makinistId", a.makinistId());
            m.put("sicil", sicil.get(a.makinistId()));
            silinenAyrinti.add(m);
        }
        BirlestirSonucu birlesen;
        KaldirSonucuDto fark;
        try {
            if (!silinen.isEmpty()) {
                atamalar.deleteAll(silinen);
                atamalar.flush();
                gordumlar.atamalariSilinenleriTemizle(silinenIdler); // sıra: atama -> gördüm -> sefer (FK atama.sefer_id)
            }
            birlesen = cekirdek.birlestir(zincir, saat.simdi());
            fark = etki.sonra(once);
        } catch (DataIntegrityViolationException e) { // kilit sonrası araya giren atama (FK) / tekillik: açık 409 (MK-123)
            throw UygulamaHatasi.cakisma("ATAMA_DEGISTI", "Sefer az önce başka bir işlemle değişti; yeniden deneyin. Hiçbir değişiklik yapılmadı.");
        }
        degisiklik.kaydet(Kayit.of(DegisiklikTuru.SEFER_BIRLESTIR, "SEFER", birlesen.sefer().id()).sefer(birlesen.sefer().id()).degisim(birlesen.eskiOzet(), birlesen.yeniOzet())
                .ayrinti(birlestirAyrinti(birlesen, silinenAyrinti)));
        if (istek.onizle()) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly(); // MK-124: kayıt da (MANDATORY, aynı işlem) geri gider
        }
        return new BirlestirSonucuDto(birlesen.sefer(), birlesen.silinenSeferIdler(), fark.yeniKirmizi());
    }

    /**
     * [V9] Parça 2..N'de dolu her rol, parça 1'de AYNI makinistle dolu olmalı; YOLCU kümesi parça 1'inkinin altkümesi olmalı. Atama taşıma yok: kural motoru atlanmasın diye parça 1 boş + parça k dolu
     * reddedilir (önce parça 1'e normal atama yapılır).
     */
    private void atamaKosullari(Zincir zincir, List<AtamaKisa> atamalar) {
        for (int k = 1; k < zincir.idler().size(); k++) {
            long parcaSeferi = zincir.idler().get(k);
            for (AtamaRol rol : AtamaRol.values()) {
                List<AtamaKisa> ilk = atamalar.stream().filter(a -> a.seferId() == zincir.parca1Id() && a.rol() == rol).toList();
                for (AtamaKisa a : atamalar) {
                    if (a.seferId() != parcaSeferi || a.rol() != rol || ilk.stream().anyMatch(x -> x.makinistId().equals(a.makinistId()))) {
                        continue;
                    }
                    String sicilK = makinistler.getir(a.makinistId()).sicil();
                    if (ilk.isEmpty()) {
                        throw UygulamaHatasi.cakisma("BIRLESTIRME_ATAMA_PARCA1_BOS", rol + " rolünde " + (k + 1) + ". parçada " + sicilK + " görevli, 1. parçada bu rol boş. Önce 1. parçaya normal "
                                + "atama yapın (kurallar değerlendirilsin) ya da " + (k + 1) + ". parçadaki atamayı Kaldır'ın.");
                    }
                    String ilkSicil = ilk.stream().map(x -> makinistler.getir(x.makinistId()).sicil()).collect(Collectors.joining(", "));
                    throw UygulamaHatasi.cakisma("BIRLESTIRME_ATAMA_FARKLI", rol + " rolünde " + (k + 1) + ". parçada " + sicilK + " görevli; 1. parçada " + (rol == AtamaRol.YOLCU
                            ? "yolcu olarak " + ilkSicil + " var, " + sicilK + " yok" : ilkSicil + " görevli") + ". Birleştirmek için aynı makinist olmalı: önce birini Kaldır'ın.");
                }
            }
        }
    }

    /** Atama kümesi imzası: kimlik + makinist + rol + sefer (aynı satırın makinisti değişse de farkı yakalar). */
    private static Set<String> imza(List<AtamaKisa> atamalar) {
        return atamalar.stream().map(a -> a.atamaId() + ":" + a.makinistId() + ":" + a.rol() + ":" + a.seferId()).collect(Collectors.toSet());
    }

    private String birlestirAyrinti(BirlestirSonucu s, List<Map<String, Object>> silinenAtamalar) {
        Map<String, Object> g = new LinkedHashMap<>();
        g.put("silinenSeferIdler", s.silinenSeferIdler());
        g.put("silinenAtamalar", silinenAtamalar);
        g.put("gecikme", s.eskiParcalar().stream().map(p -> Map.of("parcaNo", p.parcaNo(), "kalkisGecikmeDk", p.kalkisGecikmeDk(), "varisGecikmeDk", p.varisGecikmeDk())).toList()); // birleştirme öncesi; ara değerler atılır
        try {
            return json.writeValueAsString(g);
        } catch (JacksonException e) {
            throw new IllegalStateException("Birleştirme ayrıntısı yazılamadı", e);
        }
    }

    private TreeSet<Long> makinistKumesi(long seferId) {
        return atamalar.findBySeferIdIn(List.of(seferId)).stream().map(Atama::makinistId).collect(Collectors.toCollection(TreeSet::new));
    }

    private String ayrinti(SeferBolIstegi istek, BolSonucu s, int atamadaKalanMakinist) {
        Map<String, Object> g = new LinkedHashMap<>();
        g.put("yeniIdler", s.yeniIdler());
        List<Map<String, Object>> noktalar = new ArrayList<>();
        for (var n : istek.noktalar()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("garId", n.garId());
            m.put("varisSaat", n.varisSaat().toString());
            m.put("kalkisSaat", n.kalkisSaat().toString());
            m.put("km", n.km() == null ? null : n.km().stripTrailingZeros().toPlainString());
            m.put("hatId", n.hatId());
            m.put("aracTipiId", n.aracTipiId());
            noktalar.add(m);
        }
        g.put("noktalar", noktalar);
        g.put("gecikme", s.parcalar().stream().map(p -> Map.of("parcaNo", p.parcaNo(), "kalkisGecikmeDk", p.kalkisGecikmeDk(), "varisGecikmeDk", p.varisGecikmeDk())).toList());
        g.put("atamadaKalanMakinist", atamadaKalanMakinist);
        try {
            return json.writeValueAsString(g);
        } catch (JacksonException e) {
            throw new IllegalStateException("Bölme ayrıntısı yazılamadı", e);
        }
    }
}
