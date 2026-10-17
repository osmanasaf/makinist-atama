-- Kural kurucu K2: (1) kayan pencere için gün sayısı; (2) sefere isteğe bağlı tren türü (kural kapsamı). Boş tren türü hiçbir tren türü kapsamına girmez.
ALTER TABLE ozel_kural ADD COLUMN kayan_gun INTEGER;
ALTER TABLE ozel_kural ADD CONSTRAINT ozel_kural_kayan_ck CHECK (
    (pencere = 'KAYAN' AND kayan_gun BETWEEN 2 AND 366) OR (pencere <> 'KAYAN' AND kayan_gun IS NULL));

ALTER TABLE sefer ADD COLUMN tren_turu VARCHAR(10);
ALTER TABLE sefer ADD CONSTRAINT sefer_tren_turu_ck CHECK (tren_turu IN ('YUK', 'YOLCU'));
