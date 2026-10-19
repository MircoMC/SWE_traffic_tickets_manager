import java.time.LocalDate;

public class Multa extends Observable {

    private final int id;
    private final double amount;
    private final LocalDate dueDate;
    private final double detectedSpeed;
    private StatoMulta state;

    public Multa(int id, double amount, LocalDate dueDate, double detectedSpeed, StatoMulta state) {
        this.id = id;
        this.amount = amount;
        this.dueDate = dueDate;
        this.detectedSpeed = detectedSpeed;
        this.state = state;
    }

    public int getId() {
        return id;
    }

    public StatoMulta getState() {
        return state;
    }

    public void changeState(StatoMulta newState) {
        StatoMulta previousState = this.state;
        this.state = newState;

        // Notifica agli Observer del cambio di stato
        setChanged();
        notifyObservers(previousState);
    }

    public void checkExpiration(LocalDate currentDate) {
        if (currentDate.isAfter(dueDate)) {
            expire();
        }
    }

    // Deleghe delle transizioni allo stato attuale
    public void pay() {
        state.pay(this);
    }

    public void cancel() {
        state.cancel(this);
    }

    public void expire() {
        state.expire(this);
    }
}
