package multe.utenti;

public final class Cittadino extends Utente {

    public Cittadino(int id, String nome) {
        super(id, nome);
    }

    /** Il cittadino vede solo le proprie multe. */
    @Override
    public boolean puoConsultareMulteDi(int idAltroUtente) {
        return idAltroUtente == getId();
    }
}
