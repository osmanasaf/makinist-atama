-- F2c tur 2: Excel "Değişim Noktaları" ile açılan tek bir parçanın üst süre sınırı. Saat yazım hatası (varış 13:30 yerine 07:30 -> 23,5 s'lik parça)
-- sessizce kabul edilmesin; üstündeki parça için tren yüklenmez ve onay kilitlenir. Üst sınır 24: sefer süresi CHECK'i (sefer_guncel_sure_ck) zaten 24 saatten fazlasını reddeder.
INSERT INTO uygulama_ayar (anahtar, deger, birim, alt, ust, aciklama) VALUES
    ('sefer.parca_azami_saat', 16, 'saat', 1, 24, 'Excel Değişim Noktaları ile açılan bir parçanın en uzun süresi; aşan parça saat yazım hatası sayılır ve tren yüklenmez.');
