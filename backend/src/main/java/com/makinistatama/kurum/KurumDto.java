package com.makinistatama.kurum;

import jakarta.validation.constraints.NotNull;

/** Kurum uçlarının veri kayıtları. */
public final class KurumDto {

    private KurumDto() {
    }

    /** Anonim yüzeyin TÜM içeriği (MK-168): yalnız ad ve logo varlığı/sürümü; kullanıcı, yapılandırma ya da sayaç bilgisi yoktur. {@code ad}: girilmemişse null. */
    public record Acik(String ad, boolean logoVar, long logoSurum) {
    }

    /** Logo yanıtı: {@code tur} PNG | JPEG, {@code surum} ETag. */
    public record Logo(byte[] bayt, String tur, long surum) {
    }

    public record AdIstegi(@NotNull(message = "Kurum adı boş olamaz.") String ad) {
    }
}
