package com.makinistatama.konaklama;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.time.LocalTime;
import java.util.List;

import com.makinistatama.kural.Gorev;
import com.makinistatama.kural.Nobet;
import com.makinistatama.kural.NobetHesabi;
import com.makinistatama.kural.ZamanHesabi;

/**
 * Konaklama önerisi (F2b, MK-43): SAF. Makinistin nöbetleri {@link NobetHesabi#grupla} ile türetilir (nöbet tanımının ikinci kopyası YOK; yolculuk nöbetin parçasıdır).
 * Ardışık iki nöbet arasındaki boşluk en az {@code minDk} ise ve önceki nöbetin son varış garı makinistin bağlı garı DEĞİLSE (dış gar) o gar için konaklama önerilir.
 * Boşluk ayrıca gece penceresiyle ({@code geceBas}–{@code geceBit}) en az {@code geceMinDk} dk kesişmelidir: gündüz beklemesi konaklama gerektirmez.
 * Sonraki nöbet yoksa öneri yoktur (boşluk tanımsız). Otomatik yazılmaz.
 */
public final class OneriHesabi {

    /** {@code bas}: önceki nöbetin bitişi, {@code bit}: sonraki nöbetin başlangıcı; {@code gece}: {@link KonaklamaHesabi#gece}. */
    public record Oneri(long makinistId, long garId, Instant bas, Instant bit, long sureDk, int gece) {
    }

    private OneriHesabi() {
    }

    public static List<Oneri> hesapla(long makinistId, long bagliGarId, List<Gorev> gorevler, long ayirmaDk, long minDk, LocalTime geceBas, LocalTime geceBit, long geceMinDk) {
        List<Nobet> nobetler = NobetHesabi.grupla(gorevler, ayirmaDk);
        List<Oneri> sonuc = new ArrayList<>();
        for (int i = 0; i + 1 < nobetler.size(); i++) {
            Nobet once = nobetler.get(i);
            Nobet sonra = nobetler.get(i + 1);
            long bosluk = Duration.between(once.bitis(), sonra.baslangic()).toMinutes();
            if (once.sonVarisGarId() != bagliGarId && bosluk >= minDk
                    && ZamanHesabi.geceDakika(once.bitis(), sonra.baslangic(), geceBas, geceBit) >= geceMinDk) {
                sonuc.add(new Oneri(makinistId, once.sonVarisGarId(), once.bitis(), sonra.baslangic(), bosluk, KonaklamaHesabi.gece(once.bitis(), sonra.baslangic())));
            }
        }
        return sonuc;
    }
}
