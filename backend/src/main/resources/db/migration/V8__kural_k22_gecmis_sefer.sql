-- K-22: bitmiş (geçmiş tarihli) sefere atama. İş kuralı belirsiz olduğundan varsayılan UYARI (gerekçeyle atanır); YONETICI ENGEL ya da KAPALI yapabilir.
-- Yalnız atama anında denetlenir; geçmişte yapılmış atamalar kırmızı sayılmaz.
INSERT INTO kural_siddet (kod, siddet, aciklama, kaynak, guvenilirlik) VALUES
    ('K-22', 'UYARI', 'Geçmiş tarihli (varışı geçmiş) sefere atama yapılıyor', '[V] iş kuralı belirsiz; düzeltme amaçlı geçmiş kayıt meşru olabilir', 'DUSUK');
