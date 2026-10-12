import java.io.Serializable;

public class Annullata implements StatoMulta{

    private static final Annullata AnnullataUnique = new Annullata();
    private Annullata() {}
    //@Override
    public static Annullata GetRefAnnullata() {
        return AnnullataUnique;
    }

    @Override
    public String getStato(){
        return "Annullata";
    }


}
