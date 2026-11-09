package fines.users;

public abstract class LawEnforcement extends User {

    LawEnforcement(int id, String name) {
        super(id, name);
    }

    /** Le forze dell'ordine (carabinieri e amministratori) possono consultare le multe di tutti. */
    @Override
    public boolean canViewFinesOf(int otherUserId) {
        return true;
    }
}
