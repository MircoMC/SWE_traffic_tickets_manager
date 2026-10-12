public class Emessa implements StatoMulta{


    public String nome() { return "EMESSA"; }

    public void Paga(Multa multa) {
        multa.cambiaStato(new Emessa(), Evento.valueOf("PAGATA"));
    }


    public void Scade(Multa multa) {

    }


    public void Annulla(Multa multa) {

    }

    @Override
    public String getStato() {
        return "";
    }
}
