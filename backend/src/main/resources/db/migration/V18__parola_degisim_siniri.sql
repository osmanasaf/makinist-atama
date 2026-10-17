-- F2a-3 (05-review INFO): kendi-parola değişiminin kaba kuvvet sınırı giriş sayacından AYRI. Anonim yanlış girişler (giriş kilidi) oturumu açık kullanıcının zorunlu
-- ilk parola değişimini 429'a düşürmesin; paralel denemelerde sınır gevşemesin diye sayım satır kilidi altında, doğrulamadan ÖNCE yapılır.
ALTER TABLE kullanici
    ADD COLUMN parola_deneme       INTEGER     NOT NULL DEFAULT 0,
    ADD COLUMN parola_kilit_bitis  TIMESTAMPTZ;
