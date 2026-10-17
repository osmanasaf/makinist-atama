package com.makinistatama.atama;

/** Atama satırının kimlik özeti (varlık değil projeksiyon): kilit alınmadan önce "bu atama kimin" sorusu, varlık önbelleğine takılmadan DB'den okunur (MK-34/4). */
public record AtamaKisa(Long atamaId, Long seferId, Long makinistId, AtamaRol rol) {
}
