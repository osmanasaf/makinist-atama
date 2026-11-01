package com.makinistatama.parametre;

import java.util.List;

import com.makinistatama.Tarayici;
import com.makinistatama.TestVeritabani;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** Profil testlerinin ortak yardımcıları: dışa aktarılan paketi JSON ağacı olarak alır, değiştirir, önizleme/uygulama gövdesini kurar. */
abstract class ProfilTestTabani extends TestVeritabani {

    static final JsonMapper JSON = new JsonMapper();

    ObjectNode disaAktar(Tarayici y) throws Exception {
        return (ObjectNode) JSON.readTree(y.get("/api/profil/disa-aktar").andReturn().getResponse().getContentAsString());
    }

    static ObjectNode bolum(ObjectNode paket, String ad) {
        return (ObjectNode) paket.get(ad);
    }

    static String uygulaGovdesi(ObjectNode paket, List<String> secilen, String neden) throws Exception {
        ObjectNode g = JSON.createObjectNode();
        g.set("paket", paket);
        ArrayNode s = g.putArray("secilen");
        secilen.forEach(s::add);
        if (neden != null) {
            g.put("neden", neden);
        }
        return JSON.writeValueAsString(g);
    }

    static JsonNode satir(JsonNode onizleme, String kimlik) {
        for (JsonNode s : onizleme.get("satirlar")) {
            if (s.get("kimlik").asString().equals(kimlik)) {
                return s;
            }
        }
        throw new AssertionError("Satır yok: " + kimlik);
    }

    JsonNode onizle(Tarayici y, ObjectNode paket) throws Exception {
        return JSON.readTree(y.post("/api/profil/onizle", JSON.writeValueAsString(paket)).andReturn().getResponse().getContentAsString());
    }

    /** Hat + gar ön verisi ve bir özel kural (kapsam: kalkış garı Ankara, hat Ankara–Eskişehir). */
    static String ozelKuralGovdesi(String ad, String esik, String siddet, long kalkisGar, long hat) {
        return "{\"ad\":\"" + ad + "\",\"olcu\":\"TOPLAM_GOREV_SURESI\",\"pencere\":\"GUN\",\"karsilastirma\":\"EN_FAZLA\",\"esik\":" + esik + ",\"siddet\":\"" + siddet
                + "\",\"gerekce\":\"deneme\",\"gecerlilikBas\":\"2026-01-01\",\"kapsam\":{\"kalkisGarlari\":[" + kalkisGar + "],\"hatlar\":[" + hat + "]}}";
    }
}
