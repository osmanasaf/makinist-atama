-- Gar çifti intikal süresi (MK-109): yönetici Tanımlar ekranından girer; "yolculukla ata" öneri satırını besler. Yönsüz (gar_a_id < gar_b_id, servis min/max normalize eder).
-- TOHUMLANMAZ: tanımsız çift = öneri yok (varsayılan süre yanlış saat üretir). Ayar DEĞİL (uygulama_ayar tamsayı anahtar-değerdir, matris taşımaz).
-- Geri alma: DROP TABLE gar_intikal_suresi.
CREATE TABLE gar_intikal_suresi (
    gar_a_id  BIGINT  NOT NULL REFERENCES tanim (id),
    gar_b_id  BIGINT  NOT NULL REFERENCES tanim (id),
    dakika    INTEGER NOT NULL,
    versiyon  BIGINT  NOT NULL DEFAULT 0,
    PRIMARY KEY (gar_a_id, gar_b_id),
    CONSTRAINT gar_intikal_sira_ck CHECK (gar_a_id < gar_b_id),
    CONSTRAINT gar_intikal_dakika_ck CHECK (dakika BETWEEN 1 AND 1440)
);
