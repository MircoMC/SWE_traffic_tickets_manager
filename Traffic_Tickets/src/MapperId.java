import java.util.HashMap;
import java.util.Map;

public class MapperId {
   private Map<Integer,Multa> Mapper = new HashMap<Integer,Multa>();

   public MapperId() {}

    public void Register(Multa m) {
       Mapper.put(m.getId(),m);
    }

    public Multa get(int id) {
       return Mapper.get(id);
    }


}
