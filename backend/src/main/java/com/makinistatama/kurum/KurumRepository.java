package com.makinistatama.kurum;

import org.springframework.data.jpa.repository.JpaRepository;

interface KurumRepository extends JpaRepository<Kurum, Short> {

    /** Logo baytları yüklenmeden (kabuk her açılışta sorar). */
    interface AcikOzet {
        String getAd();

        int getLogoBoyut();

        long getVersiyon();
    }

    AcikOzet findProjectedById(Short id);
}
