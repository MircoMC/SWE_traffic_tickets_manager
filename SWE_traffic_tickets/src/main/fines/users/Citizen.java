package fines.users;

public final class Citizen extends User {

    public Citizen(int id, String name) {
        super(id, name);
    }

    /** A citizen only sees their own fines. */
    @Override
    public boolean canViewFinesOf(int otherUserId) {
        return otherUserId == getId();
    }
}
