package multe.dominio;

import java.time.LocalDate;

/** Traccia di un annullamento: chi l'ha disposto, perché e quando. Immutabile. */
public final class Provvedimento {

    private final int idGiudice;
    private final String motivazione;
    private final LocalDate data;

    Provvedimento(int idGiudice, String motivazione, LocalDate data) {
        this.idGiudice = idGiudice;
        this.motivazione = motivazione;
        this.data = data;
    }

    public int getIdGiudice()      { return idGiudice; }
    public String getMotivazione() { return motivazione; }
    public LocalDate getData()     { return data; }
}
