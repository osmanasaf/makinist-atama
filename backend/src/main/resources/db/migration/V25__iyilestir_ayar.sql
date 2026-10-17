-- F2d (MK-69): opt-in "iyileştir" otomatik atama bütçesi. Tur sayısı tur 0'ı (bugünkü plan) içerir; 1 = iyileştirme fiilen kapalı.
-- Geri alma: iki satırı sil (şema değişmez).
INSERT INTO uygulama_ayar (anahtar, deger, birim, alt, ust, aciklama) VALUES
    ('iyilestir.tur', 20, 'tur', 1, 40, 'Daha çok sefer doldur seçeneğinde en çok kaç plan denenir (ilk tur bugünkü plandır; 1 yapılırsa iyileştirme kapanır).'),
    ('iyilestir.sure_sn', 15, 'sn', 1, 60, 'Daha çok sefer doldur seçeneğinde önizlemenin süre bütçesi; yeni plan denemesi bu süreyi aşacaksa başlamaz.');
