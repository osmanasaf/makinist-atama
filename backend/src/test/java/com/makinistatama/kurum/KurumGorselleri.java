package com.makinistatama.kurum;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.imageio.ImageIO;

/** Testlerde bellekte küçük PNG/JPEG/GIF üretir (gerçek bir kurumun logosu DEĞİL: düz renkli kare). */
public final class KurumGorselleri {

    private KurumGorselleri() {
    }

    public static byte[] goruntu(String bicim, int genislik, int yukseklik) {
        try {
            BufferedImage g = new BufferedImage(genislik, yukseklik, BufferedImage.TYPE_INT_RGB);
            for (int x = 0; x < genislik; x++) {
                for (int y = 0; y < yukseklik; y++) {
                    g.setRGB(x, y, ((x / 4 + y / 4) % 2 == 0) ? 0x2255AA : 0xEEEEEE);
                }
            }
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            ImageIO.write(g, bicim, b);
            return b.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static byte[] png() {
        return goruntu("png", 64, 32);
    }

    public static byte[] jpeg() {
        return goruntu("jpeg", 64, 32);
    }
}
