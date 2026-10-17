-- Dinamik ayarlar (MK-93, OK-24): üç ayar tablosuna ekran katalogu alanları. DAVRANIŞ DEĞİŞMEZ:
--   varsayilan = mevcut deger/siddet (yeni varsayılanlar V30'da), grup/sira yalnız ekranın sıralaması içindir.
-- Üç gömülü sabit (Gördüm 7 gün, kapanmış takas 7 gün, gece konaklama 60 dk) uygulama_ayar satırı olur (MK-95).
-- Grup kodları (ekran sırası): CALISMA, GECE, BELGE, PUANTAJ, TAKAS_IZIN, YOLCULUK, OTOMATIK, KADRO, PERSONEL, MAKINIST.
-- Geri alma: üç tabloda DROP COLUMN varsayilan, grup, sira; üç yeni uygulama_ayar satırını sil.

ALTER TABLE kural_parametre ADD COLUMN varsayilan TEXT, ADD COLUMN grup VARCHAR(20), ADD COLUMN sira INTEGER;
ALTER TABLE kural_siddet ADD COLUMN varsayilan VARCHAR(10), ADD COLUMN grup VARCHAR(20), ADD COLUMN sira INTEGER;
ALTER TABLE uygulama_ayar ADD COLUMN varsayilan INTEGER, ADD COLUMN grup VARCHAR(20), ADD COLUMN sira INTEGER;

INSERT INTO uygulama_ayar (anahtar, deger, birim, alt, ust, aciklama) VALUES
    ('gordum.kalem_gun', 7, 'gün', 1, 60, 'Makinistin elinden alınan görev bildirimi, makinist kapatmasa bile bu kadar gün sonra makinist ekranından kalkar.'),
    ('takas.kapanmis_gun', 7, 'gün', 1, 60, 'Sonuçlanmış (kabul, ret, iptal, süresi dolmuş) takas ve bırakma talepleri bu kadar gün listede kalır.'),
    ('konaklama.gece_min_dk', 60, 'dk', 0, 600, 'Konaklama önerisinde bir gecenin sayılması için dış gardaki boşluğun gece penceresiyle kesişmesi gereken en az süre.');

UPDATE kural_parametre SET varsayilan = deger;
UPDATE kural_siddet SET varsayilan = siddet;
UPDATE uygulama_ayar SET varsayilan = deger;

UPDATE kural_parametre k SET grup = v.grup, sira = v.sira
FROM (VALUES
    ('dinlenme.ev_saat', 'CALISMA', 10), ('dinlenme.dis_merkez_saat', 'CALISMA', 20), ('nobet.ayirma_saat', 'CALISMA', 30),
    ('nobet.bekleme_sayilir', 'CALISMA', 40), ('nobet.asgari_aktarma_dk', 'CALISMA', 50), ('gunluk.max_saat', 'CALISMA', 60),
    ('haftalik.normal_saat', 'CALISMA', 70), ('haftalik.max_saat', 'CALISMA', 80), ('yillik.fazla_mesai_max_saat', 'CALISMA', 90),
    ('hafta_tatili.min_saat', 'CALISMA', 100), ('gorev.hazirlik_dk', 'CALISMA', 110), ('gorev.alikoyma_dk', 'CALISMA', 120),
    ('sefer.max_saat', 'CALISMA', 130),
    ('gece.baslangic', 'GECE', 10), ('gece.bitis', 'GECE', 20), ('gece.max_saat', 'GECE', 30), ('gece.gorev_orani', 'GECE', 40),
    ('gece.ard_hafta_max', 'GECE', 50), ('gece.hafta_min_gorev', 'GECE', 60),
    ('belge.sinav_gecerlilik_ay', 'BELGE', 10), ('hat.ara_max_ay', 'BELGE', 20), ('brove.surus_ara_max_ay', 'BELGE', 30), ('belge.uyari_gun', 'BELGE', 40),
    ('puantaj.gece_bitis', 'PUANTAJ', 10), ('puantaj.gece_tazminat_orani', 'PUANTAJ', 20), ('puantaj.asgari_gunluk_saat', 'PUANTAJ', 30),
    ('puantaj.fazla_mesai_carpani', 'PUANTAJ', 40), ('puantaj.saat_ucreti', 'PUANTAJ', 50), ('puantaj.km_birim_tutari', 'PUANTAJ', 60),
    ('adalet.olcut', 'OTOMATIK', 10), ('yedek.oran', 'OTOMATIK', 20), ('yedek.min', 'OTOMATIK', 30)
) AS v(anahtar, grup, sira) WHERE k.anahtar = v.anahtar;

UPDATE kural_siddet k SET grup = v.grup, sira = v.sira
FROM (VALUES
    ('K-11', 'CALISMA', 10), ('K-12', 'CALISMA', 20), ('K-23', 'CALISMA', 30), ('K-13', 'CALISMA', 40), ('K-14', 'CALISMA', 50),
    ('K-15', 'CALISMA', 60), ('K-19', 'CALISMA', 70), ('K-20', 'CALISMA', 80), ('K-21', 'CALISMA', 90),
    ('K-16', 'GECE', 10), ('K-17', 'GECE', 20), ('K-18', 'GECE', 30),
    ('K-01', 'BELGE', 10), ('K-02', 'BELGE', 20), ('K-03', 'BELGE', 30), ('K-04', 'BELGE', 40), ('K-05', 'BELGE', 50),
    ('K-06', 'BELGE', 60), ('K-07', 'BELGE', 70), ('K-08', 'BELGE', 80),
    ('K-09', 'TAKAS_IZIN', 10),
    ('K-22', 'OTOMATIK', 10)
) AS v(kod, grup, sira) WHERE k.kod = v.kod;

UPDATE uygulama_ayar k SET grup = v.grup, sira = v.sira
FROM (VALUES
    ('sefer.parca_azami_saat', 'CALISMA', 10),
    ('izin.en_ileri_gun', 'TAKAS_IZIN', 10), ('izin.en_uzun_gun', 'TAKAS_IZIN', 20), ('izin.en_cok_acik_talep', 'TAKAS_IZIN', 30),
    ('takas.son_saat', 'TAKAS_IZIN', 40), ('takas.karsi_onay_saat', 'TAKAS_IZIN', 50), ('takas.gunluk_acma', 'TAKAS_IZIN', 60),
    ('takas.kapanmis_gun', 'TAKAS_IZIN', 70),
    ('yolculuk.calisma_sayilir', 'YOLCULUK', 10), ('yolculuk.yuk_trenine_binilir', 'YOLCULUK', 20), ('yolculuk.aktarma_dk', 'YOLCULUK', 30),
    ('yolculuk.arama_saat', 'YOLCULUK', 40), ('yolculuk.oksuz_saat', 'YOLCULUK', 50),
    ('konaklama.oneri_min_saat', 'YOLCULUK', 60), ('konaklama.gece_min_dk', 'YOLCULUK', 70),
    ('iyilestir.tur', 'OTOMATIK', 40), ('iyilestir.sure_sn', 'OTOMATIK', 50),
    ('kadro.is_parcacigi', 'KADRO', 10),
    ('konduktor.yolcu_trenine_gerekli', 'PERSONEL', 10),
    ('makinist.gorunur_gun', 'MAKINIST', 10), ('gordum.uyari_saat', 'MAKINIST', 20), ('gordum.kalem_gun', 'MAKINIST', 30)
) AS v(anahtar, grup, sira) WHERE k.anahtar = v.anahtar;

ALTER TABLE kural_parametre ALTER COLUMN varsayilan SET NOT NULL, ALTER COLUMN grup SET NOT NULL, ALTER COLUMN sira SET NOT NULL;
ALTER TABLE kural_siddet ALTER COLUMN varsayilan SET NOT NULL, ALTER COLUMN grup SET NOT NULL, ALTER COLUMN sira SET NOT NULL,
    ADD CONSTRAINT kural_siddet_varsayilan_ck CHECK (varsayilan IN ('ENGEL', 'UYARI', 'KAPALI'));
ALTER TABLE uygulama_ayar ALTER COLUMN varsayilan SET NOT NULL, ALTER COLUMN grup SET NOT NULL, ALTER COLUMN sira SET NOT NULL,
    ADD CONSTRAINT uygulama_ayar_varsayilan_ck CHECK (alt <= varsayilan AND varsayilan <= ust);
