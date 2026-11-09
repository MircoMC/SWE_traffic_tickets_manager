package fines.login;

/**
 * Finta email di avviso, su console. In seguito qui si cercherà l'indirizzo del titolare e si parlerà con il
 * server di posta. Il codice fiscale è un dato personale: non si scrive nei log.
 */
public class EmailAlert implements AccessAlert {

    @Override
    public void failedAttempts(String fiscalCode, boolean officeAccount, int attempts) {
        System.out.println("[email al titolare] Sono stati fatti " + attempts + " tentativi di accesso falliti al tuo account "
            + (officeAccount ? "di carica" : "privato") + ". Se non sei stato tu, cambia la password.");
    }
}
