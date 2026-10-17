package com.makinistatama;

import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

/**
 * "Gördüm" isteği makinistin ekranda gördüğü görev özetini taşır (05-review Tur 2 W1). Bu yardımcı testlerde "makinist güncel kartı görüp basıyor" durumunu kurar:
 * önce {@code GET /api/benim/gorevler}, kartın {@code ozet}'i, sonra {@code POST .../gordum}. Listede olmayan atama (yabancı / ufuk dışı) için özet yerine "-" gider.
 */
public final class GordumYardimci {

    private GordumYardimci() {
    }

    public static ResultActions gordum(Tarayici t, long atamaId) throws Exception {
        String liste = t.get("/api/benim/gorevler").andReturn().getResponse().getContentAsString();
        java.util.List<String> ozet = JsonPath.read(liste, "$.gorevler[?(@.atamaId==" + atamaId + ")].ozet");
        return gordum(t, atamaId, ozet.isEmpty() ? "-" : ozet.getFirst());
    }

    public static ResultActions gordum(Tarayici t, long atamaId, String ekrandakiOzet) throws Exception {
        return t.post("/api/benim/gorevler/" + atamaId + "/gordum", "{\"ozet\":" + new tools.jackson.databind.json.JsonMapper().writeValueAsString(ekrandakiOzet) + "}");
    }
}
