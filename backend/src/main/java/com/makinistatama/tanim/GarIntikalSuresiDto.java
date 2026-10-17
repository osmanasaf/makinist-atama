package com.makinistatama.tanim;

/** Gar çifti süresi (yönsüz; {@code garAId < garBId}). */
public record GarIntikalSuresiDto(Long garAId, String garA, Long garBId, String garB, int dakika, long versiyon) {
}
