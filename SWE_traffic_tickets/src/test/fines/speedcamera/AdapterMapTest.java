package fines.speedcamera;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AdapterMapTest {

    private static final String DAL = "2026-01-01T00:00:00Z";

    @Test
    void traduceIlFormatoEsternoNeiNostriTipi() {
        ExternalMapper esterno = new ExternalMapper();
        esterno.load("A1", DAL, "50", "43.77", "11.25", "Via Roma 1");
        List<Period> history = new SpeedCameraMapAdapter(esterno).history("A1");

        assertEquals(1, history.size());
        assertEquals(Instant.parse(DAL), history.get(0).getFrom());
        Configuration c = history.get(0).getConfiguration();
        assertEquals(50, c.getLimit());
        assertEquals("Via Roma 1", c.getLocation().getAddress());
        assertEquals(43.77, c.getLocation().getLatitude());
    }

    @Test
    void laStoriaEOrdinataAncheSeIlServizioNonLoE() {
        ExternalMapper esterno = new ExternalMapper();
        esterno.load("A1", "2026-03-01T00:00:00Z", "70", "43.77", "11.25", "Via Roma 1");
        esterno.load("A1", "2026-01-01T00:00:00Z", "50", "43.77", "11.25", "Via Roma 1");
        esterno.load("A1", "2026-02-01T00:00:00Z", "60", "43.77", "11.25", "Via Roma 1");

        List<Period> history = new SpeedCameraMapAdapter(esterno).history("A1");
        assertEquals(50, history.get(0).getConfiguration().getLimit());
        assertEquals(60, history.get(1).getConfiguration().getLimit());
        assertEquals(70, history.get(2).getConfiguration().getLimit());
    }

    @Test
    void unAutoveloxSconosciutoHaStoriaVuotaEIlRisultatoNonSiModifica() {
        ExternalMapper esterno = new ExternalMapper();
        esterno.load("A1", DAL, "50", "43.77", "11.25", "Via Roma 1");
        SpeedCameraMapAdapter adapter = new SpeedCameraMapAdapter(esterno);
        assertTrue(adapter.history("A9").isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> adapter.history("A1").clear());
    }

    @Test
    void datiNonValidiFannoFallireInModoEsplicito() {
        String[][] sbagliati = {
            { "non-una-data", "50", "43.77", "11.25" },
            { DAL, "cinquanta", "43.77", "11.25" },
            { DAL, "0", "43.77", "11.25" },
            { DAL, "-30", "43.77", "11.25" },
            { DAL, "50", "91.0", "11.25" },
            { DAL, "50", "43.77", "181.0" },
            { DAL, "50", "nord", "11.25" },
        };
        for (String[] r : sbagliati) {
            ExternalMapper esterno = new ExternalMapper();
            esterno.load("A1", r[0], r[1], r[2], r[3], "Via Roma 1");
            assertThrows(IllegalStateException.class, () -> new SpeedCameraMapAdapter(esterno).history("A1"));
        }
    }

    @Test
    void campoMancanteEInizioDoppioFannoFallire() {
        ExternalMapper senzaLat = new ExternalMapper() {
            @Override public List<Map<String, String>> read(String id) {
                Map<String, String> riga = new HashMap<>();
                riga.put("dal", DAL);
                riga.put("limite_kmh", "50");
                riga.put("lon", "11.25");
                riga.put("indirizzo", "Via Roma 1");
                return Collections.singletonList(riga);
            }
        };
        assertThrows(IllegalStateException.class, () -> new SpeedCameraMapAdapter(senzaLat).history("A1"));

        ExternalMapper doppio = new ExternalMapper();
        doppio.load("A1", DAL, "50", "43.77", "11.25", "Via Roma 1");
        doppio.load("A1", DAL, "70", "43.77", "11.25", "Via Roma 1");
        assertThrows(IllegalStateException.class, () -> new SpeedCameraMapAdapter(doppio).history("A1"));
    }
}
