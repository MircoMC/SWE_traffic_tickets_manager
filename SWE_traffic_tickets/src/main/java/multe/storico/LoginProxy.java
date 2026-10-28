package multe.storico;

import multe.dominio.Annullamento;
import multe.dominio.ServiziMulte;
import multe.login.AutenticazioneException;
import multe.login.GatewayLogin;
import multe.login.Identita;
import multe.utenti.Amministratore;
import multe.utenti.Carabiniere;
import multe.utenti.Cittadino;
import multe.utenti.Giudice;
import multe.utenti.Utente;

import java.util.Arrays;
import java.util.Objects;

/**
 * Punto di ingresso per i client: autentica l'utente tramite GatewayLogin e gli consegna
 * una Sessione con ciò che il suo ruolo consente: uno StoricoMulteProxy legato alla sua identità,
 * il pagamento delle proprie multe e, solo per i giudici, l'Annullamento.
 * L'identità non è mai un oggetto Utente fornito dal chiamante: nasce qui dal ruolo salvato nel database.
 * Il codice di assemblaggio può comunque costruire uno StoricoMulteTotale (vedi DemoTarghe):
 * la protezione vale per chi riceve solo la Sessione.
 */
public class LoginProxy {

    private final GatewayLogin gateway;
    private final StoricoMulte storicoReale;
    private final ServiziMulte servizi;   // può essere null: allora nessuno ottiene Annullamento e pagamento

    public LoginProxy(GatewayLogin gateway, StoricoMulte storicoReale) {
        this(gateway, storicoReale, null);
    }

    public LoginProxy(GatewayLogin gateway, StoricoMulte storicoReale, ServiziMulte servizi) {
        this.gateway = Objects.requireNonNull(gateway);
        this.storicoReale = Objects.requireNonNull(storicoReale);
        this.servizi = servizi;
    }

    /**
     * La password viene azzerata prima di uscire, anche in caso di errore.
     *
     * @throws AutenticazioneException se username o password non sono corretti
     */
    public Sessione accedi(String username, char[] password) throws AutenticazioneException {
        try {
            Identita identita = gateway.autentica(username, password)
                .orElseThrow(() -> new AutenticazioneException("Credenziali non valide"));
            Utente utente = creaUtente(identita);
            Annullamento annullamento = (utente instanceof Giudice && servizi != null)
                ? servizi.annullamentoPer((Giudice) utente) : null;
            PagamentoOnline pagamento = (servizi != null) ? new PagamentoOnline(servizi) : null;
            return new Sessione(utente, new StoricoMulteProxy(utente, storicoReale), annullamento, pagamento);
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    /** Scorciatoia per chi serve solo lo storico. */
    public StoricoMulte login(String username, char[] password) throws AutenticazioneException {
        return accedi(username, password).getStorico();
    }

    /** Unico punto in cui il ruolo salvato diventa il tipo di Utente (e quindi i suoi privilegi). */
    private Utente creaUtente(Identita identita) {
        switch (identita.getRuolo()) {
            case CITTADINO:      return new Cittadino(identita.getIdUtente(), identita.getUsername());
            case CARABINIERE:    return new Carabiniere(identita.getIdUtente(), identita.getUsername());
            case AMMINISTRATORE: return new Amministratore(identita.getIdUtente(), identita.getUsername());
            case GIUDICE:        return new Giudice(identita.getIdUtente(), identita.getUsername());
            default:
                throw new IllegalStateException("Ruolo sconosciuto: " + identita.getRuolo());
        }
    }
}
