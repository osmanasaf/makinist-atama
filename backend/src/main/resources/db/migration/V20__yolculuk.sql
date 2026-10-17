-- F2b (MK-38): yolculuk (deadhead). Makinist başka bir gara, görevsiz yolcu olarak giden bir seferde YOLCU rolüyle yer alır (MK-1 göç yolu).
-- Bir seferde birden çok YOLCU olabilir: rol başına benzersizlik yalnız ASIL/YARDIMCI için kalır (aynı adlı kısmi indeks). Aynı makinist aynı
-- seferde rol ne olursa olsun tek satırdır (atama_sefer_makinist_uk değişmez).
ALTER TABLE atama DROP CONSTRAINT atama_rol_ck;
ALTER TABLE atama ADD CONSTRAINT atama_rol_ck CHECK (rol IN ('ASIL', 'YARDIMCI', 'YOLCU'));
ALTER TABLE atama DROP CONSTRAINT atama_sefer_rol_uk;
CREATE UNIQUE INDEX atama_sefer_rol_uk ON atama (sefer_id, rol) WHERE rol <> 'YOLCU';

-- Yolculuk ayarları (kural_parametre kataloğu 31 satıra kilitli; MK-39): uygulama_ayar'da.
INSERT INTO uygulama_ayar (anahtar, deger, birim, alt, ust, aciklama) VALUES
    ('yolculuk.calisma_sayilir', 1, '0/1', 0, 1, 'Yolcu olarak geçen süre çalışma süresinden sayılır mı (1 evet, 0 hayır); adalet, puantaj ve süre kurallarında geçerlidir.'),
    ('yolculuk.yuk_trenine_binilir', 0, '0/1', 0, 1, 'Yolculuk için yük trenine binilebilir mi (1 evet, 0 hayır); tren türü boş seferler binilebilir sayılır.'),
    ('yolculuk.aktarma_dk', 15, 'dk', 0, 240, 'Yolculuk önerisinde yolculuk varışı ile görev başlangıcı arasında aranan en az aktarma süresi.'),
    ('yolculuk.arama_saat', 12, 'saat', 1, 48, 'Yolculuk önerisinde ve öksüz yolculuk uyarısında görevden önce/sonra aranan pencere.');
