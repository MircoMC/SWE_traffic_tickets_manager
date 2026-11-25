package fines.users;

/**
 * The judge can view everyone's fines and, after the login, obtain the Cancellation.
 * Not a LawEnforcement: it makes no detections, it decides on appeals.
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
