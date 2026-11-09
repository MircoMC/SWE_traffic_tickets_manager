package fines.users;

/**
 * Il giudice può consultare le multe di tutti e, dopo il login, ottenere l'Cancellation.
 * Non è una LawEnforcement: non fa rilevazioni, decide sui ricorsi.
 */
public final class Judge extends User {

    public Judge(int id, String name) {
        super(id, name);
    }

    @Override
    public boolean canViewFinesOf(int otherUserId) {
        return true;
    }
}
