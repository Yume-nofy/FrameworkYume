package nofy.p17;

import java.util.HashMap;
import java.util.Map;

public class UrlMatcher {
    
    public static boolean matches(String pattern, String url) {
        String[] patternParts = pattern.split("/");
        String[] urlParts = url.split("/");
        
        if (patternParts.length != urlParts.length) {
            return false;
        }
        
        for (int i = 0; i < patternParts.length; i++) {
            if (patternParts[i].startsWith("{") && patternParts[i].endsWith("}")) {
                // C'est un paramètre, on accepte n'importe quelle valeur
                continue;
            }
            if (!patternParts[i].equals(urlParts[i])) {
                return false;
            }
        }
        return true;
    }
    
    public static Map<String, String> extractParameters(String pattern, String url) {
        Map<String, String> params = new HashMap<>();
        String[] patternParts = pattern.split("/");
        String[] urlParts = url.split("/");
        
        for (int i = 0; i < patternParts.length; i++) {
            if (patternParts[i].startsWith("{") && patternParts[i].endsWith("}")) {
                String paramName = patternParts[i].substring(1, patternParts[i].length() - 1);
                params.put(paramName, urlParts[i]);
            }
        }
        return params;
    }
}