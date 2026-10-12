import jdk.jfr.Event;
enum Evento { NOTIFICATA, PAGATA, SCADUTA, ANNULLATA }

public interface MultaObserver {
    void AggiornaMulta(Multa multa,Evento evento);

}
