package com.makinistatama.kural;

import java.time.Instant;
import java.util.List;

/** Turetilmis nobet: arasi {@code nobet.ayirma_saat}'ten kisa bosluklarla birbirine bagli gorevler (MK-2). */
public record Nobet(Instant baslangic, Instant bitis, long ilkKalkisGarId, long sonVarisGarId, List<Gorev> gorevler) {
}
