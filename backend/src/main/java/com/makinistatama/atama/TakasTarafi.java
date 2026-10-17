package com.makinistatama.atama;

/** Takas talebinin bir tarafı, talep anında: atama + makinist + sefer kimliği ve {@link GorevOzeti}. Bayatlık bu değerlerin güncelle karşılaştırılmasıdır (MK-34/3, MK-35). */
public record TakasTarafi(long atamaId, long makinistId, long seferId, String ozet) {
}
