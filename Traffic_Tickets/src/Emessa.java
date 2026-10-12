public class Emessa implements StatoMulta{


    public String nome() { return "EMESSA"; }
    @Override
    public void Paga(Multa multa) {
        multa.cambiaStato(new Emessa(), Evento.valueOf("PAGATA"));
    }

    @Override
    public void Scade(Multa multa) {

    }

    @Override
    public void Anulla(Multa multa) {

    }
}
