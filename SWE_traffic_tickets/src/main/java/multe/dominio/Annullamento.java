package multe.dominio;

/**
 * Capability per annullare le multe, parallela al pagamento: l'unico modo per annullare è avere
 * in mano questo oggetto, e lo si ottiene solo dal login di un Giudice (vedi LoginProxy).
 * Il costruttore è package-private: nasce solo da ServiziMulte.annullamentoPer.
 */
public final class Annullamento {

    private final StateManager stateManager;
    private final int idGiudice;

    Annullamento(StateManager stateManager, int idGiudice) {
        this.stateManager = stateManager;
        this.idGiudice = idGiudice;
    }

    /**
     * @throws IllegalStateException    se nello stato attuale la multa non è annullabile (es. già pagata)
     * @throws IllegalArgumentException se la multa non esiste o la motivazione è vuota
     */
    public void annulla(int idMulta, String motivazione) {
        stateManager.cancelStarting(idMulta, idGiudice, motivazione);
    }
}
