import java.time.LocalDate;

public class Multa {

    private String Id;
    private double Importo;
    private LocalDate Scadenza;
    private double VelocitaRilevata;


    Multa(String id, double importo, LocalDate date, double speed) {
        this.Id = id;
        this.Importo = importo;
        this.Scadenza = date;
        this.VelocitaRilevata = speed;
    }


}
