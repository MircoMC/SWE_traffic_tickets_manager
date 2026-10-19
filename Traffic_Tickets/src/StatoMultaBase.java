public abstract class StatoMultaBase implements StatoMulta {

    @Override
    public void paga(Multa multa) {
        throw new IllegalStateException("Operazione 'paga' non valida per lo stato " + getState());
    }

    @Override
    public void annulla(Multa multa) {
        throw new IllegalStateException("Operazione 'annulla' non valida per lo stato " + getState());
    }

    @Override
    public void scade(Multa multa) {
        throw new IllegalStateException("Operazione 'scade' non valida per lo stato " + getState());
    }

    public abstract String getStato();
}
