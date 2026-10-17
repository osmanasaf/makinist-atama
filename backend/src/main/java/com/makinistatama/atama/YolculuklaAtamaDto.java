package com.makinistatama.atama;

/** "Yolculukla ata" sonucu: yazılan iki satır (önce yolculuk, sonra hedef sefer). */
public record YolculuklaAtamaDto(AtamaDto yolcu, AtamaDto asil) {
}
