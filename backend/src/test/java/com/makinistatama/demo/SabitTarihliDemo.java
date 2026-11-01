package com.makinistatama.demo;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.makinistatama.kullanici.KullaniciServisi;
import com.makinistatama.makinist.MakinistServisi;
import com.makinistatama.parametre.ParametreServisi;
import com.makinistatama.sefer.SeferYuklemeServisi;
import com.makinistatama.tanim.TanimServisi;

/**
 * Demo veriyi SABİT bir "bugün" ile yükler (F2b altın dosya: {@link DemoVeriServisi} tarihi sistem saatinden alır, her gün başka veri üretirdi).
 * {@code dev} profili gerekmez: servis elle kurulur (kurucusu paket içi).
 */
public final class SabitTarihliDemo {

    /** Pazartesi 12 Ekim 2026, 09:00 (İstanbul). */
    public static final Instant BUGUN = Instant.parse("2026-10-12T06:00:00Z");

    private SabitTarihliDemo() {
    }

    /** Demo tabloları (yalnız flyway geçmişi hariç: yükleme {@code puantaj.*} parametrelerini de yazar) ve ilk yüklemenin ardından alınan dizi değerleri; JVM (= fork = kendi veritabanı kabı) başına bir kez. */
    private static final List<String> TOHUM_TABLOLARI = List.of("kural_parametre", "kural_siddet", "uygulama_ayar", "kurum");
    private static List<String> yedekTablolar;
    private static List<Map<String, Object>> yedekDiziler;

    /**
     * Demo veriyi yükler. JVM'deki ilk çağrı gerçek yükleyiciyi çalıştırır (~4-6 sn) ve sonucu {@code demo_yedek} şemasına kopyalar; sonraki çağrılar
     * (taban sınıf tabloları testten önce boşaltmış olmalı) aynı veriyi {@code INSERT ... SELECT} ile geri yükler (~50 ms). Yüklemenin kendisini sınayan
     * testler ({@code DemoVeriTest} vb.) {@link DemoVeriServisi}'ni doğrudan kullanır.
     */
    public static void yukle(ApplicationContext ctx) {
        JdbcTemplate jdbc = ctx.getBean(JdbcTemplate.class);
        TransactionTemplate tx = new TransactionTemplate(ctx.getBean(PlatformTransactionManager.class));
        if (yedekTablolar != null) {
            tx.executeWithoutResult(s -> {
                jdbc.execute("SET LOCAL session_replication_role = replica"); // FK sırası önemsiz; tetikleyiciler atlanır (kopya zaten tutarlı)
                yedekTablolar.forEach(t -> {
                    if (TOHUM_TABLOLARI.contains(t)) { // taban sınıf bunları tohuma döndürür, boşaltmaz: demo değerleriyle değiştir
                        jdbc.execute("DELETE FROM public." + t);
                    }
                    jdbc.execute("INSERT INTO public." + t + " SELECT * FROM demo_yedek." + t);
                });
                yedekDiziler.stream().filter(d -> d.get("last_value") != null)
                        .forEach(d -> jdbc.queryForObject("SELECT setval('public.\"" + d.get("sequencename") + "\"', " + d.get("last_value") + ")", Long.class));
            });
            return;
        }
        Clock saat = Clock.fixed(BUGUN, ZoneId.of("Europe/Istanbul"));
        DemoVeriServisi servis = new DemoVeriServisi(ctx.getBean(TanimServisi.class), ctx.getBean(MakinistServisi.class), ctx.getBean(SeferYuklemeServisi.class),
                ctx.getBean(ParametreServisi.class), ctx.getBean(KullaniciServisi.class), jdbc, saat);
        tx.executeWithoutResult(s -> servis.yukle());
        List<String> tablolar = jdbc.queryForList("SELECT quote_ident(tablename) FROM pg_tables WHERE schemaname = 'public' AND tablename <> 'flyway_schema_history'", String.class);
        yedekDiziler = jdbc.queryForList("SELECT sequencename, last_value FROM pg_sequences WHERE schemaname = 'public'");
        jdbc.execute("DROP SCHEMA IF EXISTS demo_yedek CASCADE");
        jdbc.execute("CREATE SCHEMA demo_yedek");
        tablolar.forEach(t -> jdbc.execute("CREATE TABLE demo_yedek." + t + " AS TABLE public." + t));
        yedekTablolar = tablolar;
    }
}
