import java.time.LocalDate;
import java.util.ArrayList;

public class Multa implements StatoMulta {

    private final String Id;
    private final double Importo;
    private final LocalDate Scadenza;
    private final double VelocitaRilevata;
    private StatoMulta Stato;
    private final ArrayList<MultaObserver> osservatori = new ArrayList<>();


    Multa(String id, double importo, LocalDate date, double speed, StatoMulta stato) {
        this.Id = id;
        this.Importo = importo;
        this.Scadenza = date;
        this.VelocitaRilevata = speed;
        this.Stato = stato;
    }

    public void controllaScadenza(LocalDate oggi) {
        if (oggi.isAfter(Scadenza)) {
           // scade();
        }
    }

    public void aggiungiOsservatore(MultaObserver o) { osservatori.add(o); }

    void cambiaStato(StatoMulta nuovo, Evento evento) {
        this.Stato = nuovo;
        for (MultaObserver o : osservatori) {
            o.AggiornaMulta(this, evento);
        }
    }

    @Override
    public void Paga(Multa multa) {

    }

    @Override
    public void Scade(Multa multa) {

    }

    @Override
    public void Anulla(Multa multa) {

    }
}
