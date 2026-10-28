package multe.storico;

import multe.dominio.Multa;
import multe.dominio.MultaDAO;

import java.util.List;

/** Storico reale (il "reale" dietro StoricoMulteProxy): tutte le multe intestate all'utente. */
public class StoricoMulteTotale implements StoricoMulte {

    private final MultaDAO multaDAO;

    public StoricoMulteTotale(MultaDAO multaDAO) {
        this.multaDAO = multaDAO;
    }

    @Override
    public List<Multa> getMulte(int idUtente) {
        return multaDAO.trovaPerIntestatario(idUtente);
    }
}
