package com.makinistatama.takas;

/** Talebin makinistleri (varlık yüklemeden): kilit alınmadan ÖNCE okunur, böylece talep varlığı kilit sonrası taze yüklenir (MK-34/4). {@code hedef} bırakmada null. */
record TakasKisileri(Long kaynak, Long hedef) {
}
