-- M4 review W5: izin kararı (ONAY/RED) yarışında aynı talep iki kez karara bağlanabiliyordu; iyimser kilit (JPA @Version) ikinci yazarı 409'a çevirir.
-- OK-4: kimse kendi izin talebini onaylayamaz; talebi açan kullanıcının kimliği tutulur (görünen ad aynı olan iki kullanıcı karışmasın). Eski satırlarda boştur.
ALTER TABLE izin ADD COLUMN versiyon BIGINT NOT NULL DEFAULT 0;
ALTER TABLE izin ADD COLUMN olusturan_kullanici_id BIGINT;
