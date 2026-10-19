public class EmissionMultaManager extends Observable {

    public void Emission(Multa multa) {

        setChanged();
        notifyObservers(multa);

    }
}
