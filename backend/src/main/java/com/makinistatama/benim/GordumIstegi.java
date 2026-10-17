package com.makinistatama.benim;

import jakarta.validation.constraints.NotBlank;

/**
 * "Gördüm" isteği: makinistin EKRANDA gördüğü görev özeti ({@code MakinistGorevDto.ozet}). Sunucudaki güncel özetle uyuşmazsa 409 {@code OZET_DEGISTI} ve güncel görev döner:
 * makinist görmediği değişikliği onaylamış olmaz (tebliğ kanıtı). Makinist kimliği YOKTUR (oturumdan).
 */
public record GordumIstegi(@NotBlank(message = "Ekrandaki görev özeti gerekli.") String ozet) {
}
