package com.makinistatama.atama;

import java.time.Instant;

/** Pencere öncesi son sürüş: makinist x hat için en son planlanan varış (K-07/K-08, MK-6). */
public record OncekiSurus(Long makinistId, Long hatId, Instant varis) {
}
