package fines.history;

import fines.domain.Fine;
import fines.domain.FineDAO;

import java.util.List;

/** Storico reale (il "reale" dietro FineHistoryProxy): tutte le multe intestate all'utente. */
public class FullFineHistory implements FineHistory {

    private final FineDAO fineDAO;

    public FullFineHistory(FineDAO fineDAO) {
        this.fineDAO = fineDAO;
    }

    @Override
    public List<Fine> getFines(int userId) {
        return fineDAO.findByOwner(userId);
    }
}
