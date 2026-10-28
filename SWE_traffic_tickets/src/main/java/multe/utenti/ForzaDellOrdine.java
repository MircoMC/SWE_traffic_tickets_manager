package multe.utenti;

public abstract class ForzaDellOrdine extends Utente {

    ForzaDellOrdine(int id, String nome) {
        super(id, nome);
    }

    /** Le forze dell'ordine (carabinieri e amministratori) possono consultare le multe di tutti. */
    @Override
    public boolean puoConsultareMulteDi(int idAltroUtente) {
        return true;
    }
}
