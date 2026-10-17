package com.makinistatama.makinist;

/** Karttan eklenen izin doğrudan {@code ONAY}; talep ({@code TALEP}) onaylanınca {@code ONAY}, reddedilince {@code RED} olur (T13.3). */
public enum IzinDurumu {
    TALEP,
    ONAY,
    RED
}
