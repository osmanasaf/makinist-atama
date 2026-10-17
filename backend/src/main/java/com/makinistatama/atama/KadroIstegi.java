package com.makinistatama.atama;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * Kadro ihtiyacı hesabı (F3a, MK-79): {@code bas}'tan {@code hafta} hafta. Eksik alanlar varsayılana düşer (Jackson 3 eksik ilkel alanı hata sayar: sarmalayıcılar).
 * {@code seferler} (Faz 3, "ne olurdu"): bellekte açılan sanal seferler; veritabanına yazılmaz.
 */
public record KadroIstegi(
        @NotNull(message = "Başlangıç tarihi gerekli.") LocalDate bas,
        Integer hafta,
        Integer devamsizlikYuzde,
        Boolean yedekDahil,
        Boolean yalnizAnalitik,
        Integer sureSn,
        Boolean yolculuk,
        @Valid List<SanalSefer> seferler) {

    public static final int VARSAYILAN_HAFTA = 4;
    public static final int VARSAYILAN_DEVAMSIZLIK = 13;
    public static final int VARSAYILAN_SURE_SN = 25;

    public KadroIstegi {
        hafta = hafta == null ? VARSAYILAN_HAFTA : hafta;
        devamsizlikYuzde = devamsizlikYuzde == null ? VARSAYILAN_DEVAMSIZLIK : devamsizlikYuzde;
        yedekDahil = yedekDahil == null || yedekDahil;
        yalnizAnalitik = yalnizAnalitik != null && yalnizAnalitik;
        sureSn = sureSn == null ? VARSAYILAN_SURE_SN : sureSn;
        yolculuk = yolculuk != null && yolculuk;
        seferler = seferler == null ? List.of() : seferler.stream().filter(java.util.Objects::nonNull).toList();
    }

    public KadroIstegi(LocalDate bas) {
        this(bas, null, null, null, null, null, null, null);
    }

    /**
     * Sanal sefer satırı: her seçili hafta gününde, her kalkış saatinde bir sefer ({@code gunler} boşsa her gün). Tren türü verilmezse belirsiz (özel kural kapsamı yok
     * sayar). {@code km} boşsa 0.
     */
    public record SanalSefer(
            @NotNull(message = "Hat gerekli.") Long hatId,
            @NotNull(message = "Araç tipi gerekli.") Long aracTipiId,
            @NotNull(message = "Kalkış garı gerekli.") Long kalkisGarId,
            @NotNull(message = "Varış garı gerekli.") Long varisGarId,
            @NotNull(message = "Kalkış saatleri gerekli.") List<LocalTime> saatler,
            @NotNull(message = "Süre gerekli.") Integer sureDk,
            Set<DayOfWeek> gunler,
            BigDecimal km,
            String trenTuru) {

        public SanalSefer {
            gunler = gunler == null ? Set.of() : Set.copyOf(gunler.stream().filter(java.util.Objects::nonNull).toList());
            saatler = saatler == null ? null : new java.util.ArrayList<>(saatler);
        }
    }
}
