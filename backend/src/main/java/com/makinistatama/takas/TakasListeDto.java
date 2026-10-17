package com.makinistatama.takas;

import java.util.List;

/** Atamacı listesi. {@code bekleyen}: atamacı onayı bekleyen (etkin durumu hâlâ ATAMACI_ONAYI_BEKLIYOR) talep sayısı = üst çubuk rozeti. */
public record TakasListeDto(int bekleyen, List<TakasSatiriDto> talepler) {
}
