public class Scaduta implements StatoMulta{
    @Override
    public String getStato() {
        return "SCADUTA";
    }

    private static final Scaduta ScadutaUnique = new Scaduta();
    private Scaduta() {}
    //@Override
    public static Scaduta GetRefAnnullata() {
        return ScadutaUnique;
    }

    public void Paga(Multa multa) {
        multa.setStato(Pagata.GetRefAnnullata());
    }

    //@Override
    public void Annulla(Multa multa) {
        multa.setStato(Annullata.GetRefAnnullata());
    }
}
