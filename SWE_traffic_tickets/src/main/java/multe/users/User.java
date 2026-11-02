package fines.users;

/**
 * Chi usa il sistema. I tipi di utente sono SOLO quelli di questo package: il costruttore è package-private,
 * quindi da fuori nessuno può creare una sottoclasse con un getId() "furbo", e getId() è final.
 * L'identità di chi è loggato nasce nel LoginProxy dal ruolo salvato nel database, mai dal chiamante.
 */
public abstract class User {

    private final int id;
    private final String name;

    User(int id, String name) {
        this.id = id;
        this.name = name;
    }

    public final int getId()      { return id; }
    public final String getName() { return name; }

    /** Regola di accesso allo storico: ogni tipo di utente decide di quali utenti può vedere le multe. */
    public abstract boolean canViewFinesOf(int otherUserId);
}
