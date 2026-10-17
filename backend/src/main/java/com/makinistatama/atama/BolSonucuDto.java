package com.makinistatama.atama;

import java.util.List;

import com.makinistatama.sefer.SeferDto;

/** Seferi bölme yanıtı (MK-128): {@code parcalar} parça sırasıyla (ilki mevcut satır); {@code yeniKirmizi} boş olsa da liste döner (önizlemede de aynı biçim). */
public record BolSonucuDto(List<SeferDto> parcalar, List<KaldirSonucuDto.Etkilenen> yeniKirmizi) {
}
