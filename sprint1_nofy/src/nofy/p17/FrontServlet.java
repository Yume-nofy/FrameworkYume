package nofy.p17;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


public class FrontServlet extends HttpServlet {

    private RequestDispatcher defaultDispatcher;
    private MyScanner controllerScanner;
    private Map<String, Class<?>> baseUrlToController;
    

    @Override
    public void init() throws ServletException {
        // Le dispatcher par défaut pour les ressources statiques
        defaultDispatcher = getServletContext().getNamedDispatcher("default");
        
        controllerScanner = new MyScanner();
        baseUrlToController = new HashMap<>();

        initializeControllers();
    }

    private void initializeControllers() throws ServletException {
        try {
            // Scanner le package des contrôleurs
            controllerScanner.scanControllersFromPackage("nofy.p17");
            
            // Construire la map des routes
            for (Class<?> controller : controllerScanner.getControllers()) {
                Controller controllerAnnotation = controller.getAnnotation(Controller.class);
                if (controllerAnnotation == null) {
                    continue; // Ignorer si pas d'annotation @Controller
                }
                
                String baseUrl = controllerAnnotation.value();
                if (!baseUrl.startsWith("/")) {
                    baseUrl = "/" + baseUrl; // Normaliser l'URL
                }

                // Enregistrer le contrôleur pour son baseUrl
                baseUrlToController.put(baseUrl, controller);
            }
            
            System.out.println("🎯 " + baseUrlToController.size() + " contrôleurs chargés");
            
        } catch (Exception e) {
            throw new ServletException("Erreur lors de l'initialisation des contrôleurs", e);
        }
    }

    
    @Override
    protected void service(HttpServletRequest req, HttpServletResponse res) 
            throws ServletException, IOException {
        String path = req.getRequestURI().substring(req.getContextPath().length());
        
        boolean resourceExists = getServletContext().getResource(path) != null;

        if (resourceExists) {
            defaultServe(req, res); // Servir la ressource statique
        } else {
            customServe(req, res);  // Gérer la requête par un contrôleur
        }
    }

    // Servir une ressource statique via le dispatcher par défaut du conteneur
    private void defaultServe(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        defaultDispatcher.forward(req, res);
    }
    

    private void customServe(HttpServletRequest req, HttpServletResponse res) throws IOException {
        try {
            String uri = req.getRequestURI();
            String path = uri.substring(req.getContextPath().length());
            
            for (Map.Entry<String, Class<?>> entry : baseUrlToController.entrySet()) {
                String baseUrl = entry.getKey();
                
                if (path.startsWith(baseUrl)) {
                    Class<?> controllerClass = entry.getValue();
                    
                    String actionPath = path.substring(baseUrl.length()); // ex: /user/edit -> /edit
                    if (actionPath.isEmpty()) { // Gérer le cas où l'URL est exactement le baseUrl
                         actionPath = "/"; 
                    }
                    
                    Map<String, String> pathParams = new HashMap<>();

                Method targetMethod = findTargetMethod(controllerClass, actionPath, pathParams);

                if (targetMethod != null) {
                    Object controllerInstance = controllerClass.getDeclaredConstructor().newInstance();
                    Object result = invokeMethodWithParams(targetMethod, controllerInstance, req, res, pathParams);
                    handleControllerResult(result, req, res);
                    return;
                }

                    
                    displayControllerInfo(controllerClass, baseUrl, res);
                    return;
                }
            }

            // Si aucun contrôleur ne correspond au baseUrl
            res.setStatus(HttpServletResponse.SC_NOT_FOUND);
            try (PrintWriter out = res.getWriter()) {
                 out.println("<h1>404 Not Found</h1>");
                 out.println("<p>La ressource demandée n'a pas été trouvée : <strong>" + path + "</strong></p>");
            }
            
        } catch (Exception e) {
            // Gestion des erreurs d'invocation/réflexion
            res.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            try (PrintWriter out = res.getWriter()) {
                 out.println("<h1>500 Internal Server Error</h1>");
                 out.println("<p>Erreur interne du serveur: " + e.getMessage() + "</p>");
                 e.printStackTrace(out); // Afficher la stack trace pour le debug
            }
        }
    }
    
    // Recherche de la méthode par son annotation @MyMap
private Method findTargetMethod(Class<?> controllerClass, String path, Map<String, String> pathParams) {
    Method exactMatch = null;
    Method patternMatch = null;
    Map<String, String> tempPathParams = new HashMap<>();

    for (Method method : controllerClass.getDeclaredMethods()) {
        MyMap map = method.getAnnotation(MyMap.class);
        if (map == null) continue;

        String route = map.url();

        // Exact match
        if (!route.contains("{") && route.equals(path)) {
            exactMatch = method;
            break;
        }

        // Pattern match
        if (route.contains("{")) {
            String regex = convertToRegex(route); // ex: "/etudiant/{id}" -> "/etudiant/([^/]+)"
            Pattern pattern = Pattern.compile("^" + regex + "$");
            Matcher matcher = pattern.matcher(path);

            if (matcher.matches()) {
                tempPathParams.clear();

                // Extraire les noms des variables depuis la route
                List<String> varNames = new ArrayList<>();
                Matcher m = Pattern.compile("\\{([^/]+)\\}").matcher(route);
                while (m.find()) varNames.add(m.group(1));

                for (int i = 0; i < varNames.size(); i++) {
                    tempPathParams.put(varNames.get(i), matcher.group(i + 1));
                }

                patternMatch = method;
            }
        }
    }

    if (exactMatch != null) return exactMatch;

    if (patternMatch != null) {
        pathParams.putAll(tempPathParams);
        return patternMatch;
    }

    return null;
}


    public void handleControllerResult(Object result, HttpServletRequest req, HttpServletResponse res) throws Exception {
        
        if (result == null) {
            res.setStatus(HttpServletResponse.SC_NO_CONTENT); // 204 No Content
            return;
        }
        
        // Cas 1 : Le contrôleur retourne un String
        if (result instanceof String) {
            String viewOrContent = (String) result;

            if (isViewName(viewOrContent)) {
                // Si String est un nom de vue (ex: "home.jsp") -> Redirection interne
                RequestDispatcher dispatcher = req.getRequestDispatcher("/" + viewOrContent);
                dispatcher.forward(req, res); 
            } else {
                // Si String est un contenu (ex: du texte brut, HTML ou JSON) -> Affichage direct
                res.setContentType("text/html;charset=UTF-8");
                try (PrintWriter out = res.getWriter()) {
                    out.println(viewOrContent); 
                }
            }
        }
        
        // Cas 2 : Le contrôleur retourne un ModelView
        else if (result instanceof ModelView) {
        ModelView mv = (ModelView) result;
        
        // Transférer les données de la Map vers l'objet Request
        for (Map.Entry<String, Object> entry : mv.getData().entrySet()) {
            req.setAttribute(entry.getKey(), entry.getValue());
        }
        
        // Rediriger vers la vue (ex: /profile.jsp)
        String viewPath = "/" + mv.getView();
        RequestDispatcher dispatcher = req.getRequestDispatcher(viewPath);
        dispatcher.forward(req, res);
    }
        // Cas 3 : Tout autre type de retour (peut être étendu pour JSON, etc.)
        else {
            res.setContentType("text/plain;charset=UTF-8");
            try (PrintWriter out = res.getWriter()) {
                 out.println("Type de retour non géré : " + result.getClass().getName());
            }
        }
    }
    
    // --- 5. UTILITAIRES ET DEBUG ---
    
    // Utilité pour distinguer un nom de vue d'un contenu direct
    private boolean isViewName(String result) {
        // Le critère typique est de vérifier l'extension (ex: .jsp)
        return result.endsWith(".jsp") || result.endsWith(".html");
    }

    private void displayControllerInfo(Class<?> controllerClass, String baseUrl, HttpServletResponse res) throws IOException {
        res.setContentType("text/html;charset=UTF-8");
        try (PrintWriter out = res.getWriter()) {
            out.println("<h2>Controller: " + controllerClass.getSimpleName() + ".class</h2>");
            out.println("<p>Base URL: " + baseUrl + "</p>");
            out.println("<h3>Méthodes supportées :</h3>");
            out.println("<ul>");
    
            for (Method method : controllerClass.getDeclaredMethods()) {
                MyMap mapping = method.getAnnotation(MyMap.class);
                if (mapping != null) {
                    out.println("<li>" + method.getName() + "() ➜ " + mapping.url() + "</li>");
                }
            }
    
            out.println("</ul>");
            out.println("<p>Retourne Spring ✅</p>");
        }
    }
  private Object invokeMethodWithParams(Method method,
                                      Object controllerInstance,
                                      HttpServletRequest req,
                                      HttpServletResponse res,
                                      Map<String, String> pathParams) throws Exception {

    Class<?>[] paramTypes = method.getParameterTypes();
    java.lang.reflect.Parameter[] parameters = method.getParameters();
    Object[] args = new Object[paramTypes.length];

    for (int i = 0; i < paramTypes.length; i++) {
        Class<?> paramType = paramTypes[i];
        java.lang.reflect.Parameter parameter = parameters[i];
        String paramName=null;
        String paramValue = null;

        // 1️⃣ HttpServletRequest / HttpServletResponse
        if (paramType.equals(HttpServletRequest.class)) {
            args[i] = req;
            continue;
        } else if (paramType.equals(HttpServletResponse.class)) {
            args[i] = res;
            continue;
        }

        // 2️⃣ Path params depuis URL
        if (pathParams.containsKey(parameter.getName())) {
            paramName = parameter.getName();
            paramValue = pathParams.get(paramName);
        }

        // 3️⃣ RequestParam (GET ou POST)
        RequestParam requestParam = parameter.getAnnotation(RequestParam.class);
        if (requestParam != null) {
            paramName = requestParam.value();
            paramValue = req.getParameter(paramName);
        } else if (paramValue == null) {
            // fallback sur nom du paramètre
            paramName = parameter.getName();
            paramValue = req.getParameter(paramName);
        }

        // 4️⃣ Conversion
        if (paramValue == null || paramValue.trim().isEmpty()) {
            if (paramType.isPrimitive()) {
                throw new IllegalArgumentException("Paramètre primitif requis manquant: " + paramName);
            }
            args[i] = null;
        } else {
            args[i] = convertParameterValue(paramValue, paramType);
        }

        System.out.println("🔹 Injection param: " + paramName + " = " + paramValue);
    }

    // Invocation finale
    return method.invoke(controllerInstance, args);
}


private Object convertParameterValue(String value, Class<?> targetType) {
    
    try {
        if (targetType.equals(String.class)) {
            return value;
        } else if (targetType.equals(int.class) || targetType.equals(Integer.class)) {
            return Integer.parseInt(value);
        } else if (targetType.equals(long.class) || targetType.equals(Long.class)) {
            return Long.parseLong(value);
        } else if (targetType.equals(double.class) || targetType.equals(Double.class)) {
            return Double.parseDouble(value);
        } else if (targetType.equals(boolean.class) || targetType.equals(Boolean.class)) {
            return Boolean.parseBoolean(value);
        } else {
            throw new IllegalArgumentException("Type non supporté: " + targetType.getName());
        }
    } catch (NumberFormatException e) {
        throw new IllegalArgumentException("Erreur de conversion pour la valeur: " + value, e);
    }
}
private String convertToRegex(String route) {
    // Remplace {id} par (?<id>[^/]+)
    return route.replaceAll("\\{([^/]+)\\}", "(?<$1>[^/]+)");
}
private Iterable<String> extractGroupNames(String route) {
    java.util.List<String> names = new java.util.ArrayList<>();
    java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\{([^/]+)\\}").matcher(route);
    while (m.find()) {
        names.add(m.group(1)); // ajoute "id", "code", etc.
    }
    return names;
}

}