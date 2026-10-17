package com.makinistatama.atama;

import java.time.OffsetDateTime;
import java.util.List;

/** Yazılan atama + atama anındaki UYARI düzeyi ihlaller. */
public record AtamaDto(Long atamaId, Long seferId, AtamaRol rol, Long makinistId, String sicil, String ad, String atayan, OffsetDateTime atamaZamani,
        String gerekce, List<IhlalDto> uyarilar) {
}
