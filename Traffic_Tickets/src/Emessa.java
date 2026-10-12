public class Emessa implements StatoMulta{

    private static final Emessa EmessaUnique = new Emessa();
    private Emessa() {}
    //@Override
    public static Emessa GetRefEmessa() {
        return EmessaUnique;
    }

    @Override
    public String getStato(){
        return "Emessa";}

    //@Override
    public void Paga(Multa multa) {
        multa.setStato(Pagata.GetRefAnnullata());
    }

    //@Override
    public void Annulla(Multa multa) {
        multa.setStato(Annullata.GetRefAnnullata());
    }
    public void Scade(Multa multa) {
        multa.setStato(Scaduta.GetRefAnnullata());
    }
}