package multe.utenti;

/**
 * Il giudice può consultare le multe di tutti e, dopo il login, ottenere l'Annullamento.
 * Non è una ForzaDellOrdine: non fa rilevazioni, decide sui ricorsi.
 */
public final class Giudice extends Utente {

    public Giudice(int id, String nome) {
        super(id, nome);
    }

    @Override
    public boolean puoConsultareMulteDi(int idAltroUtente) {
        return true;
    }
}
