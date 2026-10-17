package com.makinistatama.ortak;

import java.sql.SQLException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import tools.jackson.databind.DatabindException;

/**
 * Tüm hataları ProblemDetail + {@code kod} (+ {@code alanlar}) biçimine çevirir.
 * Mesajlar Türkçe; kişisel veri, yığın ve istek gövdesi yanıta/loga girmez.
 */
@RestControllerAdvice
public class HataYonetimi extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(HataYonetimi.class);

    @ExceptionHandler(UygulamaHatasi.class)
    ProblemDetail uygulamaHatasi(UygulamaHatasi e) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(e.durum(), e.getMessage());
        pd.setProperty("kod", e.kod());
        e.ekstra().forEach(pd::setProperty);
        return pd;
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail versiyonCakismasi(OptimisticLockingFailureException e) {
        return uygulamaHatasi(UygulamaHatasi.versiyonCakismasi());
    }

    /**
     * Kilit bekleme zaman aşımı, kilitlenme ya da serileştirme çakışması: 500 değil 409 {@code ISLEM_SURUYOR}; transaction geri alındı, tekrar denenebilir.
     * Sürücü iletisi (tablo/sorgu ayrıntısı) yanıta girmez, yalnız tür loglanır.
     */
    @ExceptionHandler(PessimisticLockingFailureException.class)
    ProblemDetail islemSuruyor(PessimisticLockingFailureException e) {
        log.warn("Kilit çakışması: {}", e.getClass().getSimpleName());
        return uygulamaHatasi(UygulamaHatasi.islemSuruyor());
    }

    /** Oturum yok (güvenlik zincirinin giriş noktası da buraya yönlenir). {@code WWW-Authenticate} gönderilmez: tarayıcı parola penceresi açmaz. */
    @ExceptionHandler(AuthenticationException.class)
    ProblemDetail oturumYok(AuthenticationException e) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Oturum bulunamadı. Lütfen giriş yapın.");
        pd.setProperty("kod", "OTURUM_YOK");
        return pd;
    }

    @ExceptionHandler(CsrfException.class)
    ProblemDetail guvenlikBelirteci(CsrfException e) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Güvenlik belirteci geçersiz. Sayfayı yenileyip tekrar deneyin.");
        pd.setProperty("kod", "GUVENLIK_TOKENI");
        return pd;
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail yetkisiz(AccessDeniedException e) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Bu işlem için yetkiniz yok.");
        pd.setProperty("kod", "YETKISIZ");
        return pd;
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail beklenmeyen(Exception e) {
        if (nulKarakteriMi(e)) { // PostgreSQL metinde NUL (\0) kabul etmez (SQLState 22021); girdi hatası, sunucu hatası değil
            return uygulamaHatasi(new UygulamaHatasi(HttpStatus.BAD_REQUEST, "DOGRULAMA", "Girilen bilgilerde hata var. Metinde geçersiz (NUL) karakter olamaz.", null));
        }
        log.error("Beklenmeyen hata", e);
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "Beklenmeyen bir hata oluştu. Sorun sürerse yöneticinize bildirin.");
        pd.setProperty("kod", "SUNUCU_HATASI");
        return pd;
    }

    private static boolean nulKarakteriMi(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t instanceof SQLException s && "22021".equals(s.getSQLState())) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected @Nullable ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> alanlar = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().stream()
                .sorted(Comparator.comparing(FieldError::getField))
                .forEach(f -> alanlar.putIfAbsent(f.getField(), f.getDefaultMessage()));
        return dogrulama(ex, headers, request, alanlar);
    }

    @Override
    protected @Nullable ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> alanlar = new LinkedHashMap<>();
        ex.getParameterValidationResults().forEach(r -> {
            String ad = r.getMethodParameter().getParameterName();
            r.getResolvableErrors().stream().findFirst()
                    .ifPresent(h -> alanlar.putIfAbsent(ad == null ? "parametre" : ad, h.getDefaultMessage()));
        });
        return dogrulama(ex, headers, request, alanlar);
    }

    @Override
    protected @Nullable ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> alanlar = new LinkedHashMap<>();
        if (ex.getCause() instanceof DatabindException d && !d.getPath().isEmpty()) {
            String alan = d.getPath().getLast().getPropertyName();
            if (alan != null) {
                alanlar.put(alan, "Geçersiz değer.");
            }
        }
        return dogrulama(ex, headers, request, alanlar);
    }

    /** Eksik multipart parçası (örn. {@code dosya}): alan adı istek alanıyla aynıdır, FE doğrudan eşler. */
    @Override
    protected @Nullable ResponseEntity<Object> handleMissingServletRequestPart(MissingServletRequestPartException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return dogrulama(ex, headers, request, Map.of(ex.getRequestPartName(), "Bu alan zorunlu."));
    }

    /** Eksik sorgu/form parametresi (örn. {@code kategoriId}, {@code tip}). */
    @Override
    protected @Nullable ResponseEntity<Object> handleMissingServletRequestParameter(MissingServletRequestParameterException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return dogrulama(ex, headers, request, Map.of(ex.getParameterName(), "Bu alan zorunlu."));
    }

    /** Parametre türü tutmuyor (örn. {@code kategoriId=abc}, {@code ?tip=BILINMEYEN}). */
    @Override
    protected @Nullable ResponseEntity<Object> handleTypeMismatch(TypeMismatchException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        String ad = ex instanceof MethodArgumentTypeMismatchException m ? m.getName() : ex.getPropertyName();
        return dogrulama(ex, headers, request, ad == null ? Map.of() : Map.of(ad, "Geçersiz değer."));
    }

    private ResponseEntity<Object> dogrulama(Exception ex, HttpHeaders headers, WebRequest request,
            Map<String, String> alanlar) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Girilen bilgilerde hata var. Alanları kontrol edin.");
        pd.setProperty("kod", "DOGRULAMA");
        if (!alanlar.isEmpty()) {
            pd.setProperty("alanlar", alanlar);
        }
        return handleExceptionInternal(ex, pd, headers, HttpStatus.BAD_REQUEST, request);
    }

    /** Spring'in kendi hataları (404, 405, 413 …): {@code kod} ve Türkçe mesaj ekler. */
    @Override
    protected @Nullable ResponseEntity<Object> handleExceptionInternal(Exception ex, @Nullable Object body,
            HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        ResponseEntity<Object> yanit = super.handleExceptionInternal(ex, body, headers, statusCode, request);
        if (yanit != null && yanit.getBody() instanceof ProblemDetail pd
                && (pd.getProperties() == null || !pd.getProperties().containsKey("kod"))) {
            int s = statusCode.value();
            pd.setProperty("kod", s == 404 ? "BULUNAMADI" : s == 413 ? "DOSYA_COK_BUYUK" : s >= 500 ? "SUNUCU_HATASI" : "DOGRULAMA");
            pd.setDetail(switch (s) {
                case 404 -> "Kayıt bulunamadı.";
                case 405 -> "Bu istek yöntemi desteklenmiyor.";
                case 413 -> "Dosya çok büyük. En çok 5 MB yüklenebilir.";
                case 415 -> "Desteklenmeyen içerik türü.";
                default -> s >= 500 ? "Beklenmeyen bir hata oluştu." : "İstek geçersiz.";
            });
        }
        return yanit;
    }
}
