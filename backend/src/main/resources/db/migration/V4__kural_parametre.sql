-- Kural parametreleri ve kural şiddetleri (MK-10). Kodda sabit eşik yoktur: değerler burada tohumlanır, YONETICI ekrandan değiştirir (neden zorunlu, kayıtlı).
-- Şiddet kural düzeyindedir (kural_siddet); K-10 (çakışma) fiziksel olduğundan sabittir ve tabloda yoktur.
-- `kaynak` ve `guvenilirlik` ekranda gösterilir (karar destek aracı; mevzuata uyum garantisi vermez). Kaynak kodları: Y yasa/yönetmelik,
-- T Demiryol-İş 31. dönem TİS, İ TCDD genel müdürlük emri (metni yayımlanmamış), E mülga md. 21, V kaynaksız varsayım.
-- Tohum satırları TestVeritabani temizliğinde korunur; parametreyi değiştiren test eski değeri geri yazar.
CREATE TABLE kural_parametre (
    anahtar      VARCHAR(60) PRIMARY KEY,
    deger        TEXT        NOT NULL DEFAULT '',
    tip          VARCHAR(10) NOT NULL,
    alt_sinir    NUMERIC,
    ust_sinir    NUMERIC,
    birim        VARCHAR(10) NOT NULL DEFAULT '',
    aciklama     TEXT        NOT NULL,
    kaynak       VARCHAR(200) NOT NULL,
    guvenilirlik VARCHAR(10) NOT NULL,
    CONSTRAINT kural_parametre_tip_ck CHECK (tip IN ('SAYI', 'SAAT_GUNU', 'METIN')),
    CONSTRAINT kural_parametre_guv_ck CHECK (guvenilirlik IN ('YUKSEK', 'ORTA', 'DUSUK'))
);

CREATE TABLE kural_siddet (
    kod          VARCHAR(5)  PRIMARY KEY,
    siddet       VARCHAR(10) NOT NULL,
    aciklama     TEXT        NOT NULL,
    kaynak       VARCHAR(200) NOT NULL,
    guvenilirlik VARCHAR(10) NOT NULL,
    CONSTRAINT kural_siddet_siddet_ck CHECK (siddet IN ('ENGEL', 'UYARI', 'KAPALI')),
    CONSTRAINT kural_siddet_guv_ck CHECK (guvenilirlik IN ('YUKSEK', 'ORTA', 'DUSUK')),
    CONSTRAINT kural_siddet_kod_ck CHECK (kod ~ '^K-[0-9]{2}$' AND kod <> 'K-10')
);

INSERT INTO kural_parametre (anahtar, deger, tip, alt_sinir, ust_sinir, birim, aciklama, kaynak, guvenilirlik) VALUES
    ('belge.sinav_gecerlilik_ay', '60', 'SAYI', 1, 240, 'ay', 'Araç tipi ve hat sınavının geçerlilik süresi', '[Y] Tren Makinist Yönetmeliği md. 16/1', 'YUKSEK'),
    ('hat.ara_max_ay', '18', 'SAYI', 1, 240, 'ay', 'Bir hatta son sürüşten bu yana en çok geçebilecek süre', '[Y] Tren Makinist Yönetmeliği md. 16/1-b (AB''de 12)', 'YUKSEK'),
    ('brove.surus_ara_max_ay', '18', 'SAYI', 1, 240, 'ay', 'Hiçbir trende sürüş yapmadan geçebilecek en uzun süre (bröve düşer)', '[Y] Tren Makinist Yönetmeliği md. 16/7', 'YUKSEK'),
    ('dinlenme.ev_saat', '11', 'SAYI', 0, 72, 'saat', 'Görevler arası dinlenme, makinistin bağlı garında', '[İ] TCDD Taşımacılık emri 08.11.2023 (AB 12, mülga md. 21: 8)', 'ORTA'),
    ('dinlenme.dis_merkez_saat', '11', 'SAYI', 0, 72, 'saat', 'Görevler arası dinlenme, dış merkezde (önceki varış garı bağlı gar değil)', '[İ] TCDD emri (AB 8; sendika talebi 8)', 'ORTA'),
    ('gunluk.max_saat', '11', 'SAYI', 0, 24, 'saat', 'Takvim günü başına toplam görev süresi', '[Y] 4857 md. 63 (Bakanlık: normal 8, tehirle 11)', 'ORTA'),
    ('haftalik.max_saat', '45', 'SAYI', 0, 168, 'saat', 'Takvim haftası (Pzt-Paz) toplam görev süresi üst sınırı', '[Y] 4857 md. 63; [T] md. 31', 'ORTA'),
    ('haftalik.normal_saat', '45', 'SAYI', 0, 168, 'saat', 'Haftalık normal çalışma; üstü fazla mesai sayılır', '[Y] 4857 md. 63; [T] md. 31', 'ORTA'),
    ('yillik.fazla_mesai_max_saat', '270', 'SAYI', 0, 2000, 'saat', 'Yıllık fazla çalışma toplamı üst sınırı', '[Y] 4857 md. 41', 'YUKSEK'),
    ('gece.max_saat', '7.5', 'SAYI', 0, 24, 'saat', 'Gece görevi azami süresi', '[Y] 4857 md. 69; Postalar Yönetmeliği md. 7', 'YUKSEK'),
    ('gece.baslangic', '20:00', 'SAAT_GUNU', NULL, NULL, 'sa:dk', 'Gece penceresi başlangıcı', '[Y] 4857 md. 69', 'YUKSEK'),
    ('gece.bitis', '06:00', 'SAAT_GUNU', NULL, NULL, 'sa:dk', 'Gece penceresi bitişi (kural penceresi)', '[Y] 4857 md. 69', 'YUKSEK'),
    ('gece.gorev_orani', '0.5', 'SAYI', 0, 1, 'oran', 'Süresinin bu oranından fazlası gece penceresine düşen görev "gece görevi" sayılır', '[V] kaynaksız varsayım', 'DUSUK'),
    ('gece.ard_hafta_max', '1', 'SAYI', 0, 10, 'hafta', 'Üst üste en çok kaç takvim haftası gece görevi verilebilir', '[Y] Postalar Yönetmeliği md. 8', 'ORTA'),
    ('gece.hafta_min_gorev', '1', 'SAYI', 1, 50, 'adet', 'Haftada en az bu kadar gece nöbeti olan hafta "gece haftası" sayılır', '[V] kaynaksız varsayım', 'DUSUK'),
    ('hafta_tatili.min_saat', '24', 'SAYI', 0, 168, 'saat', 'Her takvim haftasında en az bu kadar kesintisiz görevsiz aralık', '[Y] 4857 md. 46; [T] md. 35-36', 'YUKSEK'),
    ('sefer.max_saat', '9', 'SAYI', 0, 48, 'saat', 'Tek sefer süresi üst sınırı (sürüş)', '[E] mülga Tren Makinist Yönetmeliği md. 21/a', 'DUSUK'),
    ('gorev.hazirlik_dk', '0', 'SAYI', 0, 600, 'dk', 'Kalkıştan önce işe çağırma süresi (görev süresine eklenir)', '[T] md. 32/6 (süre kaynakta yok)', 'DUSUK'),
    ('gorev.alikoyma_dk', '0', 'SAYI', 0, 600, 'dk', 'Varıştan sonra alıkoyma süresi (görev süresine eklenir)', '[T] md. 32/6 (süre kaynakta yok)', 'DUSUK'),
    ('belge.uyari_gun', '30', 'SAYI', 0, 365, 'gün', 'Belge bitişine bu kadar gün kala "yaklaşıyor" uyarısı', '[V] UHDGM yenileme başvurusu 30 gün önce', 'ORTA'),
    ('adalet.olcut', 'SAAT', 'METIN', NULL, NULL, '', 'Adil dağıtım ölçütü (eşitlikte km, sonra sicil)', '[V] kullanıcı: "en az çalışmış"', 'ORTA'),
    ('yedek.oran', '0.10', 'SAYI', 0, 1, 'oran', 'Günlük yedek makinist ihtiyacı: planlı sefer sayısının oranı', '[V] kaynaksız yer tutucu', 'DUSUK'),
    ('yedek.min', '1', 'SAYI', 0, 100, 'adet', 'Günlük asgari yedek makinist sayısı', '[V] kaynaksız yer tutucu', 'DUSUK'),
    ('puantaj.gece_bitis', '07:00', 'SAAT_GUNU', NULL, NULL, 'sa:dk', 'Puantaj gece saati penceresi bitişi (başlangıç gece.baslangic)', '[T] md. 45 (20:00-07:00)', 'ORTA'),
    ('puantaj.gece_tazminat_orani', '0.15', 'SAYI', 0, 5, 'oran', 'Gece saati başına saat ücretinin oranı', '[T] md. 45 (%15)', 'ORTA'),
    ('puantaj.asgari_gunluk_saat', '7.5', 'SAYI', 0, 24, 'saat', 'Çalışılan gün başına ücretlendirilen asgari süre', '[T] md. 32/7', 'ORTA'),
    ('puantaj.saat_ucreti', '', 'SAYI', 0, NULL, 'TL', 'Saat ücreti (boşsa TL tutarları hesaplanmaz)', 'bilinmiyor', 'DUSUK'),
    ('puantaj.fazla_mesai_carpani', '1.5', 'SAYI', 1, 10, 'çarpan', 'Fazla mesai ücret çarpanı', 'doğrulanmadı (4857 md. 41 genel bilgi)', 'DUSUK'),
    ('puantaj.km_birim_tutari', '', 'SAYI', 0, NULL, 'TL', 'Kilometre başına tazminat (boşsa hesaplanmaz)', 'bilinmiyor', 'DUSUK'),
    ('nobet.ayirma_saat', '4', 'SAYI', 0, 72, 'saat', 'Ardışık iki görev arası boşluk bundan kısaysa aynı nöbettir (dinlenme/gece kuralları nöbet bazlı)', '[V] kaynaksız varsayım (MK-2)', 'DUSUK'),
    ('nobet.bekleme_sayilir', '0', 'SAYI', 0, 1, '0/1', '1 ise nöbet içindeki bekleme çalışma süresine sayılır', '[V] kaynaksız varsayım (MK-2)', 'DUSUK');

INSERT INTO kural_siddet (kod, siddet, aciklama, kaynak, guvenilirlik) VALUES
    ('K-01', 'ENGEL', 'Makinist durumu AKTIF olmalı (ASKIDA/PASIF atanamaz)', '[Y] Tren Makinist Yönetmeliği md. 8, md. 16/3-4', 'YUKSEK'),
    ('K-02', 'ENGEL', 'Ehliyet geçerli olmalı (bitiş tarihi seferin varış gününden önce dolmamış)', '[Y] md. 7, 9/1-a', 'YUKSEK'),
    ('K-03', 'ENGEL', 'Sağlık raporu geçerli olmalı', '[Y] Yönetmelik Ek-1', 'YUKSEK'),
    ('K-04', 'ENGEL', 'Psikoteknik raporu geçerli olmalı', '[Y] Yönetmelik Ek-2', 'YUKSEK'),
    ('K-05', 'ENGEL', 'Cer aracı yetkisi: bröve''de araç tipi var ve sınavı geçerli', '[Y] md. 16/1-a', 'YUKSEK'),
    ('K-06', 'ENGEL', 'Hat yetkisi: bröve''de hat var ve sınavı geçerli', '[Y] md. 16/1-b', 'YUKSEK'),
    ('K-07', 'ENGEL', 'Hat ara kuralı: hatta son sürüşten bu yana azami süre aşılmamış', '[Y] md. 16/1-b', 'YUKSEK'),
    ('K-08', 'ENGEL', 'Bröve düşme: hiçbir trende azami süreden uzun sürüşsüz kalınmamış', '[Y] md. 16/7', 'YUKSEK'),
    ('K-09', 'ENGEL', 'Onaylı izin / rapor günü atanamaz', '[V] iş mantığı; 4857 md. 46', 'ORTA'),
    ('K-11', 'ENGEL', 'Görevler (nöbetler) arası dinlenme, bağlı garda', '[İ] TCDD emri 08.11.2023', 'ORTA'),
    ('K-12', 'ENGEL', 'Görevler (nöbetler) arası dinlenme, dış merkezde', '[İ] TCDD emri 08.11.2023', 'ORTA'),
    ('K-13', 'ENGEL', 'Takvim günü başına toplam görev süresi', '[Y] 4857 md. 63', 'ORTA'),
    ('K-14', 'ENGEL', 'Takvim haftası toplam görev süresi', '[Y] 4857 md. 63; [T] md. 31', 'ORTA'),
    ('K-15', 'ENGEL', 'Yıllık fazla çalışma toplamı', '[Y] 4857 md. 41', 'YUKSEK'),
    ('K-16', 'ENGEL', 'Gece nöbeti azami süresi', '[Y] 4857 md. 69; Postalar Yönetmeliği md. 7', 'YUKSEK'),
    ('K-17', 'ENGEL', 'Gece görevi haftalık normal süreyi aşırtamaz', '[Y] 4857 md. 41', 'ORTA'),
    ('K-18', 'ENGEL', 'Üst üste en fazla belirlenen sayıda gece haftası', '[Y] Postalar Yönetmeliği md. 8', 'ORTA'),
    ('K-19', 'ENGEL', 'Her takvim haftasında kesintisiz hafta tatili aralığı', '[Y] 4857 md. 46; [T] md. 35-36', 'YUKSEK'),
    ('K-20', 'UYARI', 'Konum sürekliliği: önceki görevin varış garı bu görevin kalkış garı olmalı', '[V] kaynakta yok', 'DUSUK'),
    ('K-21', 'KAPALI', 'Tek sefer süresi üst sınırı', '[E] mülga md. 21/a', 'DUSUK');
