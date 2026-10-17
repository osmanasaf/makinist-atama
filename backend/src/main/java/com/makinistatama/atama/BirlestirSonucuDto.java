package com.makinistatama.atama;

import java.util.List;

import com.makinistatama.sefer.SeferDto;

/** Seferi birleştirme yanıtı (MK-128): {@code sefer} birleşik parça 1 satırı; {@code yeniKirmizi} boş olsa da liste döner (önizlemede de aynı biçim). */
public record BirlestirSonucuDto(SeferDto sefer, List<Long> silinenSeferIdler, List<KaldirSonucuDto.Etkilenen> yeniKirmizi) {
}
