package com.makinistatama.ortak;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;

/**
 * Beklenen iş/istek hatalarının tek istisna sınıfı . {@code HataYonetimi} bunu ProblemDetail + {@code kod}'a çevirir.
 * {@code ekstra} anahtarları yanıta düz alan olarak eklenir (örn. {@code alanlar}).
 * Mesaj Türkçe ve kullanıcıya gösterilebilir olmalı.
 */
public class UygulamaHatasi extends RuntimeException {

    private final HttpStatus durum;
    private final String kod;
    private final Map<String, Object> ekstra;

    public UygulamaHatasi(HttpStatus durum, String kod, String mesaj, Map<String, Object> ekstra) {
        super(mesaj);
        this.durum = durum;
        this.kod = kod;
        this.ekstra = ekstra == null ? Map.of() : Map.copyOf(ekstra);
    }

    /** 400 DOGRULAMA, tek alanlı (Bean Validation'ın yakalayamadığı iş kuralı için). */
    public static UygulamaHatasi dogrulama(String alan, String mesaj) {
        return new UygulamaHatasi(HttpStatus.BAD_REQUEST, "DOGRULAMA", mesaj, Map.of("alanlar", Map.of(alan, mesaj)));
    }

    public static UygulamaHatasi bulunamadi(String mesaj) {
        return new UygulamaHatasi(HttpStatus.NOT_FOUND, "BULUNAMADI", mesaj, null);
    }

    /** 409 {@code VERSIYON_CAKISMASI}: kayıt siz düzenlerken başkası tarafından değişti (iyimser kilit). */
    public static UygulamaHatasi versiyonCakismasi() {
        return cakisma("VERSIYON_CAKISMASI", "Kayıt siz düzenlerken başka biri tarafından değiştirildi. Sayfayı yenileyip tekrar deneyin.");
    }

    /**
     * 409 {@code ISLEM_SURUYOR}: makinist satır kilidi süre içinde alınamadı ya da veritabanı kilitlenmesi oldu.
     * Hiçbir şey yazılmadı; birkaç saniye sonra aynı istek tekrar denenebilir.
     */
    public static UygulamaHatasi islemSuruyor() {
        return cakisma("ISLEM_SURUYOR", "Aynı kayıtlar üzerinde başka bir işlem sürüyor. Hiçbir değişiklik yapılmadı; birkaç saniye bekleyip tekrar deneyin.");
    }

    /** 400 {@code GECERSIZ_DOSYA} dosya açılamadı / çok büyük / yanlış biçim. Hiçbir şey saklanmadı. */
    public static UygulamaHatasi gecersizDosya(String mesaj) {
        return new UygulamaHatasi(HttpStatus.BAD_REQUEST, "GECERSIZ_DOSYA", mesaj, null);
    }

    /** 422 {@code KURAL_IHLALI}: ENGEL düzeyinde kural ihlali; {@code ihlaller} yanıta düz alan olarak eklenir. */
    public static UygulamaHatasi kuralIhlali(String mesaj, Object ihlaller) {
        return new UygulamaHatasi(HttpStatus.UNPROCESSABLE_ENTITY, "KURAL_IHLALI", mesaj, Map.of("ihlaller", ihlaller));
    }

    /** 403: oturum var ama bu işlem bu kullanıcıya kapalı (rol yetkisi değil, iş kuralı: örn. kendi talebini onaylamak). */
    public static UygulamaHatasi yasak(String kod, String mesaj) {
        return new UygulamaHatasi(HttpStatus.FORBIDDEN, kod, mesaj, null);
    }

    public static UygulamaHatasi cakisma(String kod, String mesaj) {
        return new UygulamaHatasi(HttpStatus.CONFLICT, kod, mesaj, null);
    }

    public HttpStatus durum() {
        return durum;
    }

    public String kod() {
        return kod;
    }

    public Map<String, Object> ekstra() {
        return ekstra;
    }
}
