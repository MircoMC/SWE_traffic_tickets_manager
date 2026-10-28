package multe.utenti;

/**
 * Chi usa il sistema. I tipi di utente sono SOLO quelli di questo package: il costruttore è package-private,
 * quindi da fuori nessuno può creare una sottoclasse con un getId() "furbo", e getId() è final.
 * L'identità di chi è loggato nasce nel LoginProxy dal ruolo salvato nel database, mai dal chiamante.
 */
public abstract class Utente {

    private final int id;
    private final String nome;

    Utente(int id, String nome) {
        this.id = id;
        this.nome = nome;
    }

    public final int getId()      { return id; }
    public final String getNome() { return nome; }

    /** Regola di accesso allo storico: ogni tipo di utente decide di quali utenti può vedere le multe. */
    public abstract boolean puoConsultareMulteDi(int idAltroUtente);
}
