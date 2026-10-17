package com.makinistatama.atama;

import com.makinistatama.degisiklik.Neden;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * "Yolculukla ata" (F2b, MK-41): {@code makinistId} önce {@code yolculukSeferId}'e YOLCU, sonra hedef sefere ASIL olarak tek işlemde yazılır. {@code gerekce}: iki
 * satırdan biri UYARI veriyorsa zorunlu (aynı gerekçe iki satıra); {@code neden}/{@code nedenNotu}: hedef seferin ASIL'ı doluysa (değiştirme) zorunlu.
 */
public record YolculukluAtamaIstegi(
        @NotNull(message = "Makinist seçilmeli.") Long makinistId,
        @NotNull(message = "Yolculuk seferi seçilmeli.") Long yolculukSeferId,
        @Size(max = 500, message = "Gerekçe en çok 500 karakter olabilir.") String gerekce,
        Neden neden,
        @Size(max = 500, message = "Not en çok 500 karakter olabilir.") String nedenNotu) {
}
