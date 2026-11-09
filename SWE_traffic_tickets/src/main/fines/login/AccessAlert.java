package fines.login;

/**
 * Porta verso chi avvisa il titolare di un account (in pratica una email) quando qualcuno prova a entrare
 * con troppe password sbagliate. Non blocca nulla: informa soltanto.
 *
 * Il LoginProxy la chiama su un thread dedicato, quindi non rallenta né fa fallire gli accessi, e la chiama
 * ANCHE per codici fiscali che non corrispondono a nessun account (non può saperlo, e non deve far capire a chi
 * prova se un account esiste): l'implementazione cerca il recapito e, se non lo trova, non fa nulla.
 * Al massimo una volta per account e per finestra di tempo, per non riempire di email il titolare.
 */
public interface AccessAlert {

    /** Nessun avviso (default per test e demo). */
    AccessAlert NONE = (fiscalCode, officeAccount, attempts) -> { };

    /**
     * @param officeAccount true se i tentativi riguardano l'accesso con la carica, false per l'account privato
     * @param attempts      quanti tentativi falliti consecutivi hanno fatto scattare l'avviso
     */
    void failedAttempts(String fiscalCode, boolean officeAccount, int attempts);
}
