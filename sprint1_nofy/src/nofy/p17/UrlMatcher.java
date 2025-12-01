package nofy.p17;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public class UrlMatcher {

    // Map : URL -> Map<HTTP_METHOD, Method>
    private final Map<String, Map<String, Method>> urlToHttpMethod = new HashMap<>();

    public void register(String httpMethod, String urlPattern, Method method) {
        urlToHttpMethod.computeIfAbsent(urlPattern, k -> new HashMap<>())
                       .put(httpMethod.toUpperCase(), method);
    }

    public Method getMatchingMethod(String url, String httpMethod) {
        for (String pattern : urlToHttpMethod.keySet()) {
            if (matches(pattern, url)) {
                Map<String, Method> methodMap = urlToHttpMethod.get(pattern);
                return methodMap.get(httpMethod.toUpperCase());
            }
        }
        return null;
    }

    // Vérifie si l'URL match le pattern (support {param})
    public static boolean matches(String pattern, String url) {
        String[] p = pattern.split("/");
        String[] u = url.split("/");

        if (p.length != u.length) return false;

        for (int i = 0; i < p.length; i++) {
            if (p[i].startsWith("{") && p[i].endsWith("}")) continue;
            if (!p[i].equals(u[i])) return false;
        }
        return true;
    }
    public static Map<String, String> extractParameters(String pattern, String url) {
    Map<String, String> params = new HashMap<>();

    String[] p = pattern.split("/");
    String[] u = url.split("/");

    if (p.length != u.length) return params; // retourne vide si longueur différente

    for (int i = 0; i < p.length; i++) {
        if (p[i].startsWith("{") && p[i].endsWith("}")) {
            String paramName = p[i].substring(1, p[i].length() - 1);
            params.put(paramName, u[i]);
        }
    }

    return params;
}

}
