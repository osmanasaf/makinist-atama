-- OK-24 düzeltme turu (review W1): V29 `varsayilan` sütunlarını KURULU DB'deki o anki değerden doldurmuştu; ev_saat=12 yapılmış bir kurulumda
-- "varsayılana dön" etkisiz kalıyordu. V29 uygulanmış kurulumlar olduğundan V29 değiştirilmez (Flyway checksum); varsayılanlar burada
-- SABİT tohum değerlerine (V4/V15-V28 tohumu + V30 yeni varsayılanları: K-14 KAPALI, K-23 ENGEL, asgari aktarma 20 dk) yeniden yazılır.
-- Çalışan değere (deger/siddet) dokunulmaz; yalnız `varsayilan` sütunu ve (dosya sonunda) birkaç ekran metni düzeltilir. Taze kurulumda sonuç V29+V30 ile aynıdır.
-- Geri alma: gerek yok (yalnız metaveri ve metin düzeltmesi).

UPDATE kural_parametre k SET varsayilan = v.varsayilan
FROM (VALUES
    ('adalet.olcut', 'SAAT'),
    ('belge.sinav_gecerlilik_ay', '60'),
    ('belge.uyari_gun', '30'),
    ('brove.surus_ara_max_ay', '18'),
    ('dinlenme.dis_merkez_saat', '11'),
    ('dinlenme.ev_saat', '11'),
    ('gece.ard_hafta_max', '1'),
    ('gece.baslangic', '20:00'),
    ('gece.bitis', '06:00'),
    ('gece.gorev_orani', '0.5'),
    ('gece.hafta_min_gorev', '1'),
    ('gece.max_saat', '7.5'),
    ('gorev.alikoyma_dk', '0'),
    ('gorev.hazirlik_dk', '0'),
    ('gunluk.max_saat', '11'),
    ('haftalik.max_saat', '45'),
    ('haftalik.normal_saat', '45'),
    ('hafta_tatili.min_saat', '24'),
    ('hat.ara_max_ay', '18'),
    ('nobet.asgari_aktarma_dk', '20'),
    ('nobet.ayirma_saat', '4'),
    ('nobet.bekleme_sayilir', '0'),
    ('puantaj.asgari_gunluk_saat', '7.5'),
    ('puantaj.fazla_mesai_carpani', '1.5'),
    ('puantaj.gece_bitis', '07:00'),
    ('puantaj.gece_tazminat_orani', '0.15'),
    ('puantaj.km_birim_tutari', ''),
    ('puantaj.saat_ucreti', ''),
    ('sefer.max_saat', '9'),
    ('yedek.min', '1'),
    ('yedek.oran', '0.10'),
    ('yillik.fazla_mesai_max_saat', '270')
) AS v(anahtar, varsayilan) WHERE k.anahtar = v.anahtar;

UPDATE kural_siddet k SET varsayilan = v.varsayilan
FROM (VALUES
    ('K-01', 'ENGEL'),
    ('K-02', 'ENGEL'),
    ('K-03', 'ENGEL'),
    ('K-04', 'ENGEL'),
    ('K-05', 'ENGEL'),
    ('K-06', 'ENGEL'),
    ('K-07', 'ENGEL'),
    ('K-08', 'ENGEL'),
    ('K-09', 'ENGEL'),
    ('K-11', 'ENGEL'),
    ('K-12', 'ENGEL'),
    ('K-13', 'ENGEL'),
    ('K-14', 'KAPALI'),
    ('K-15', 'ENGEL'),
    ('K-16', 'ENGEL'),
    ('K-17', 'ENGEL'),
    ('K-18', 'ENGEL'),
    ('K-19', 'ENGEL'),
    ('K-20', 'UYARI'),
    ('K-21', 'KAPALI'),
    ('K-22', 'UYARI'),
    ('K-23', 'ENGEL')
) AS v(kod, varsayilan) WHERE k.kod = v.kod;

UPDATE uygulama_ayar k SET varsayilan = v.varsayilan
FROM (VALUES
    ('gordum.kalem_gun', 7),
    ('gordum.uyari_saat', 12),
    ('iyilestir.sure_sn', 15),
    ('iyilestir.tur', 20),
    ('izin.en_cok_acik_talep', 5),
    ('izin.en_ileri_gun', 365),
    ('izin.en_uzun_gun', 60),
    ('kadro.is_parcacigi', 0),
    ('konaklama.gece_min_dk', 60),
    ('konaklama.oneri_min_saat', 8),
    ('konduktor.yolcu_trenine_gerekli', 0),
    ('makinist.gorunur_gun', 7),
    ('sefer.parca_azami_saat', 16),
    ('takas.gunluk_acma', 10),
    ('takas.kapanmis_gun', 7),
    ('takas.karsi_onay_saat', 24),
    ('takas.son_saat', 12),
    ('yolculuk.aktarma_dk', 15),
    ('yolculuk.arama_saat', 12),
    ('yolculuk.calisma_sayilir', 1),
    ('yolculuk.oksuz_saat', 24),
    ('yolculuk.yuk_trenine_binilir', 0)
) AS v(anahtar, varsayilan) WHERE k.anahtar = v.anahtar;

-- QA UX: ekranda iç belge/kod göndermesi kalmasın (kaynak ve açıklama metinleri yönetici içindir).
UPDATE kural_parametre SET kaynak = '[V] kaynaksız varsayım' WHERE kaynak = '[V] kaynaksız varsayım (MK-2)';
UPDATE kural_parametre SET kaynak = '[V] kaynaksız varsayım (işletme kararı)' WHERE kaynak = '[V] iş kuralı açık soru (SON-QA Q-1)';
UPDATE kural_siddet SET kaynak = '[V] kaynaksız varsayım (işletme kararı)' WHERE kaynak LIKE '[V] iş kuralı açık soru (SON-QA Q-1)%';
UPDATE uygulama_ayar SET aciklama = replace(aciklama, 'Dolunca yeni talep 429 ile reddedilir.', 'Dolunca yeni talep alınmaz.') WHERE anahtar = 'takas.gunluk_acma';
