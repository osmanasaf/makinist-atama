package com.makinistatama.atama;

/** {@code takasUygula} sonucu: iki yeni atamanın kimliği ve uyarı düzeyinde kalan ihlaller (gerekçeyle geçilmiş). */
public record TakasUygulamaSonucu(long kaynakYeniAtamaId, long hedefYeniAtamaId, java.util.List<TakasIhlali> uyarilar) {
}
