package nofy.p17;
import java.util.HashMap;
import java.util.Map;
public class ModelView{
    private String view;
    private Map<String, Object> data;
    public ModelView(String view){
        this.view=view;
        this.data = new HashMap<>();
    }
    public void setView(String view){
        this.view=view;
    }
    public String getView(){
        return this.view;
    } 
    /**
     * Ajoute un attribut et sa valeur à la Map des données.
     * @param key La clé (nom de l'attribut)
     * @param value La valeur de l'attribut
     */
   public void addItem(String key, Object value) {
    // Vérifier d'abord si value est null
    if (value == null) {
        this.data.put(key, null);
    } 
    // Ensuite vérifier si c'est une String vide
    else if (value instanceof String && ((String) value).isEmpty()) {
        this.data.put(key, null);
    } 
    // Sinon mettre la valeur telle quelle
    else {
        this.data.put(key, value);
    }
}

    // --- Méthode pour récupérer toutes les données ---
    
    /**
     * Retourne la Map contenant toutes les données à transférer à la requête.
     * @return La Map des données.
     */
    public Map<String, Object> getData() {
        return data;
    }
}