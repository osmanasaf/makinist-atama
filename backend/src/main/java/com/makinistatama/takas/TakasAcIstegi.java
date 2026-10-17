package com.makinistatama.takas;

import com.makinistatama.degisiklik.Neden;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Makinistin talep açışı. Makinist kimliği YOKTUR (oturumdan). {@code hedefAtamaId} dolu = karşılıklı takas, boş = görevi bırakma. {@code neden} HASTALIK / IZIN / DIGER; DIGER'de not zorunlu.
 * Not tanı içermemelidir (ekranda uyarılır).
 */
public record TakasAcIstegi(
        @NotNull(message = "Görev seçilmeli.") Long atamaId,
        Long hedefAtamaId,
        @NotNull(message = "Neden seçilmeli.") Neden neden,
        @Size(max = 120, message = "Not en çok 120 karakter olabilir.") String nedenNotu) {
}
