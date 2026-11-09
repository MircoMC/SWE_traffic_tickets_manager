package fines.users;

public final class Citizen extends User {

    public Citizen(int id, String name) {
        super(id, name);
    }

    /** Il cittadino vede solo le proprie multe. */
    @Override
    public boolean canViewFinesOf(int otherUserId) {
        return otherUserId == getId();
    }
}
