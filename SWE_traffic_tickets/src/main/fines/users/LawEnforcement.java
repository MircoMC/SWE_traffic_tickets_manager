package fines.users;

public abstract class LawEnforcement extends User {

    LawEnforcement(int id, String name) {
        super(id, name);
    }

    /** Law enforcement (police officers and administrators) can view everyone's fines. */
    @Override
    public boolean canViewFinesOf(int otherUserId) {
        return true;
    }
}
