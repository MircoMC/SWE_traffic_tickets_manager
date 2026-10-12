import java.io.Serializable;

public class Pagata implements StatoMulta{

    @Override
    public String getStato() {
        return "PAGATA";
    }

    private static final Pagata PagataUnique = new Pagata();
    private Pagata() {}
    //@Override
    public static Pagata GetRefAnnullata() {
        return PagataUnique;
    }


}
