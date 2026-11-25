package fines.users;

/**
 * Whoever uses the system. The user types are ONLY those in this package: the constructor is package-private,
 * so nobody from outside can create a subclass with a "clever" getId(), and getId() is final.
 * The identity of whoever is logged in is created in the LoginProxy from the role stored in the database, never by the caller.
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

    /** History access rule: each user type decides whose fines it can view. */
    public abstract boolean canViewFinesOf(int otherUserId);
}
