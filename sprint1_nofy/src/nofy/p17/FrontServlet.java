package nofy.p17;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@jakarta.servlet.annotation.MultipartConfig
public class FrontServlet extends HttpServlet {

    private RequestDispatcher defaultDispatcher;
    private MyScanner controllerScanner;

    // URL → (HTTP method → Method Java)
    private final Map<String, Map<String, Method>> urlToHttpMethod = new HashMap<>();
    private final Map<Method, Object> methodInstances = new HashMap<>(); // Méthode → instance du contrôleur

    @Override
    public void init() throws ServletException {
        defaultDispatcher = getServletContext().getNamedDispatcher("default");
        controllerScanner = new MyScanner();

        try {
            controllerScanner.scanControllersFromPackage("nofy.p17");

            for (Class<?> controllerClass : controllerScanner.getControllers()) {
                Controller ctrlAnn = controllerClass.getAnnotation(Controller.class);
                String baseUrl = (ctrlAnn != null) ? ctrlAnn.value() : "";

                Object controllerInstance = controllerClass.getDeclaredConstructor().newInstance();

                for (Method method : controllerClass.getDeclaredMethods()) {
                    // GET
                    if (method.isAnnotationPresent(GetMapping.class)) {
                        String url = method.getAnnotation(GetMapping.class).value();
                        registerUrl(baseUrl + url, "GET", method, controllerInstance);
                    }
                    // POST
                    if (method.isAnnotationPresent(PostMapping.class)) {
                        String url = method.getAnnotation(PostMapping.class).value();
                        registerUrl(baseUrl + url, "POST", method, controllerInstance);
                    }
                    // MyMap fallback
                    if (method.isAnnotationPresent(MyMap.class)) {
                        String url = method.getAnnotation(MyMap.class).url();
                        registerUrl(baseUrl + url, "GET", method, controllerInstance);
                        registerUrl(baseUrl + url, "POST", method, controllerInstance);
                    }
                }
            }
        } catch (Exception e) {
            throw new ServletException("Erreur lors de l'initialisation des contrôleurs", e);
        }
    }

    private void registerUrl(String url, String httpMethod, Method method, Object instance) {
        urlToHttpMethod.computeIfAbsent(url, k -> new HashMap<>())
                       .put(httpMethod.toUpperCase(), method);
        methodInstances.put(method, instance);
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        String path = req.getRequestURI().substring(req.getContextPath().length());
        String methodType = req.getMethod().toUpperCase();

        customServe(req, res, path);
    }

    private void customServe(HttpServletRequest req, HttpServletResponse res, String path) throws IOException {
        String httpMethod = req.getMethod().toUpperCase();

        Method targetMethod = null;
        Map<String, String> pathParams = new HashMap<>();

        // Parcourir toutes les URL pour matcher les patterns
        for (Map.Entry<String, Map<String, Method>> entry : urlToHttpMethod.entrySet()) {
            String pattern = entry.getKey();
            if (UrlMatcher.matches(pattern, path)) {
                Map<String, Method> methodMap = entry.getValue();
                targetMethod = methodMap.get(httpMethod);
                pathParams = UrlMatcher.extractParameters(pattern, path);
                break;
            }
        }

        if (targetMethod == null) {
            res.setStatus(HttpServletResponse.SC_NOT_FOUND);
            try (PrintWriter out = res.getWriter()) {
                out.println("<h1>404 Not Found</h1>");
                out.println("<p>Aucune route correspondante pour " + path + " [" + httpMethod + "]</p>");
            }
            return;
        }try {
            Object controllerInstance = methodInstances.get(targetMethod);
            
            // --- MODIFICATION ICI ---
            Object result = invokeMethodWithParams(targetMethod, controllerInstance, req, res, pathParams);
            handleControllerResult(result, req, res, targetMethod); // Passer targetMethod
            // ------------------------
            
        } catch (Exception e) {
            
        }

        try {
            Object controllerInstance = methodInstances.get(targetMethod);
            Object result = invokeMethodWithParams(targetMethod, controllerInstance, req, res, pathParams);
            handleControllerResult(result, req, res,targetMethod);
        } catch (Exception e) {
            res.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            try (PrintWriter out = res.getWriter()) {
                out.println("<h1>500 Internal Server Error</h1>");
                out.println("<p>" + e.getMessage() + "</p>");
                e.printStackTrace(out);
            }
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
            String paramName = null;
            String paramValue = null;

            // HttpServletRequest / HttpServletResponse
            if (paramType.equals(HttpServletRequest.class)) {
                args[i] = req;
                continue;
            } else if (paramType.equals(HttpServletResponse.class)) {
                args[i] = res;
                continue;
            }
            if (Map.class.isAssignableFrom(paramType) && isStringByteArrayMap(parameter)) {
                if (req.getContentType() != null && req.getContentType().startsWith("multipart/form-data")) {
                    Map<String, byte[]> fileMap = new HashMap<>();

                    // Extraction des fichiers
                    for (jakarta.servlet.http.Part part : req.getParts()) {
                        String fileName = part.getSubmittedFileName();
                        if (fileName != null && !fileName.isEmpty()) {
                            // On utilise le nom du fichier comme clé et le contenu comme valeur
                            byte[] fileContent = part.getInputStream().readAllBytes();
                            fileMap.put(fileName, fileContent);
                        }
                    }
                    args[i] = fileMap;
                    continue;
                }
            }
            if (Map.class.isAssignableFrom(paramType)) {
                Map<String, String[]> parameterMap = req.getParameterMap();
                Map<String, Object> formMap = new HashMap<>(); 
    
                for (Map.Entry<String, String[]> entry : parameterMap.entrySet()) {
                        String key = entry.getKey();
                        String[] values = entry.getValue();

                if (values.length == 1) {
                    // S'il y a une seule valeur (cas normal : texte, radio, checkbox simple)
                    formMap.put(key, values[0]);

                } else if (values.length > 1) {
                    // S'il y a plusieurs valeurs (cas : multiples checkboxes ou select multiple)
                    formMap.put(key, values); // <-- Mettre le tableau de String[]

                }
                // Si values.length == 0, on n'ajoute rien (ce cas n'arrive normalement pas 
                // car la clé n'est pas dans parameterMap si aucune valeur n'est fournie)
                }

    args[i] = formMap;
    continue;}
            if (!paramType.isPrimitive() && 
                !paramType.equals(String.class) && 
                !paramType.getName().startsWith("java.") &&
                !paramType.getName().startsWith("jakarta.")) {
                
                try {
                    Object pojoInstance = paramType.getDeclaredConstructor().newInstance();
                    Map<String, String[]> parameterMap = req.getParameterMap();
                    
                    // APPEL À LA MÉTHODE QUI FAIT LE BINDING PAR RÉFLEXION (bindParametersToPojo)
                    bindParametersToPojo(pojoInstance, parameterMap); 
                    
                    args[i] = pojoInstance;
                    continue;
                    
                } catch (NoSuchMethodException e) {
                    throw new ServletException("La classe argument " + paramType.getName() + " doit avoir un constructeur sans argument pour le binding.", e);
                }
            }
            // Path params
            if (pathParams.containsKey(parameter.getName())) {
                paramName = parameter.getName();
                paramValue = pathParams.get(paramName);
            }

            // RequestParam
            RequestParam requestParam = parameter.getAnnotation(RequestParam.class);
            if (requestParam != null) {
                paramName = requestParam.value();
                paramValue = req.getParameter(paramName);
            } else if (paramValue == null) {
                paramName = parameter.getName();
                paramValue = req.getParameter(paramName);
            }

            // Conversion type
            if (paramValue == null || paramValue.trim().isEmpty()) {
                if (paramType.isPrimitive()) throw new IllegalArgumentException("Paramètre primitif requis manquant: " + paramName);
                args[i] = null;
            } else {
                args[i] = convertParameterValue(paramValue, paramType);
            }
        }

        return method.invoke(controllerInstance, args);
    }

    private Object convertParameterValue(String value, Class<?> targetType) {
    try {
        if (targetType.equals(String.class)) return value;
        else if (targetType.equals(int.class) || targetType.equals(Integer.class)) return Integer.parseInt(value);
        else if (targetType.equals(long.class) || targetType.equals(Long.class)) return Long.parseLong(value);
        else if (targetType.equals(double.class) || targetType.equals(Double.class)) return Double.parseDouble(value);
        else if (targetType.equals(boolean.class) || targetType.equals(Boolean.class)) return Boolean.parseBoolean(value);
        else throw new IllegalArgumentException("Type non supporté: " + targetType.getName());
    } catch (NumberFormatException e) {
        throw new IllegalArgumentException("Erreur de conversion pour la valeur: " + value, e);
    }
}
public void handleControllerResult(Object result, HttpServletRequest req, HttpServletResponse res, java.lang.reflect.Method method) throws Exception {
    if (result == null) {
        res.setStatus(HttpServletResponse.SC_NO_CONTENT);
        return;
    }

    if (method.isAnnotationPresent(MyJson.class)) {
        MyJson jsonAnnotation = method.getAnnotation(MyJson.class);
        
        JsonResponse jsonResponse = new JsonResponse(
            jsonAnnotation.code(),
            result, // Le résultat de la méthode du contrôleur est les 'data'
            jsonAnnotation.message(),
            jsonAnnotation.status()
        );

        res.setContentType("application/json;charset=UTF-8");
        res.setStatus(jsonAnnotation.code()); // Définir le statut HTTP
        
        try (PrintWriter out = res.getWriter()) {
            out.println(jsonResponse.toJsonString());
        }
        return;
    }

    if (result instanceof String) {
        String viewOrContent = (String) result;
        if (isViewName(viewOrContent)) {
            RequestDispatcher dispatcher = req.getRequestDispatcher("/" + viewOrContent);
            dispatcher.forward(req, res);
        } else {
            res.setContentType("text/html;charset=UTF-8");
            try (PrintWriter out = res.getWriter()) { out.println(viewOrContent); }
        }
    } else if (result instanceof ModelView) {
        ModelView mv = (ModelView) result;
        for (Map.Entry<String, Object> entry : mv.getData().entrySet()) req.setAttribute(entry.getKey(), entry.getValue());
        RequestDispatcher dispatcher = req.getRequestDispatcher("/" + mv.getView());
        dispatcher.forward(req, res);
    } else {
        res.setContentType("text/plain;charset=UTF-8");
        try (PrintWriter out = res.getWriter()) { out.println("Type de retour non géré : " + result.getClass().getName()); }
    }
}

    private boolean isViewName(String result) {
        return result.endsWith(".jsp") || result.endsWith(".html");
    }

    // --- Utilitaires regex / path param ---
    private String convertToRegex(String route) {
        return route.replaceAll("\\{([^/]+)\\}", "(?<$1>[^/]+)");
    }

    private Iterable<String> extractGroupNames(String route) {
        List<String> names = new ArrayList<>();
        Matcher m = Pattern.compile("\\{([^/]+)\\}").matcher(route);
        while (m.find()) names.add(m.group(1));
        return names;
    }
    private void bindParametersToPojo(Object pojoInstance, Map<String, String[]> parameterMap) {
        Class<?> pojoClass = pojoInstance.getClass();

        for (Map.Entry<String, String[]> entry : parameterMap.entrySet()) {
            String paramName = entry.getKey();
            String[] values = entry.getValue();
            
            // Normalisation : nom du paramètre -> nom de la propriété
            String fieldName = paramName; 

            try {
                // 1. Tenter d'utiliser un Setter (Méthode setPropertyName)
                String setterName = "set" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
                
                // On essaie de trouver un setter adapté aux types simples ou aux tableaux (pour les checkboxes multiples)
                Method setter = null;
                
                // Recherche d'un setter pour un type simple (ex: setNom(String))
                try {
                    setter = pojoClass.getMethod(setterName, String.class);
                    if (values.length == 1) {
                         setter.invoke(pojoInstance, values[0]);
                         continue;
                    }
                } catch (NoSuchMethodException ignored) { /* on ignore, on essaie l'autre type de setter */ }

                // Recherche d'un setter pour un tableau de String (ex: setInterets(String[]))
                try {
                    setter = pojoClass.getMethod(setterName, String[].class);
                    if (values.length > 1) {
                        setter.invoke(pojoInstance, (Object) values); // Le cast (Object) est nécessaire pour éviter l'ambiguïté avec l'appel varargs
                        continue;
                    }
                } catch (NoSuchMethodException ignored) { /* on ignore, on essaie le champ direct */ }
                
                
                // 2. Tenter d'accéder directement au champ (si le champ est public)
                try {
                    java.lang.reflect.Field field = pojoClass.getDeclaredField(fieldName);
                    field.setAccessible(true); // Permet d'accéder aux champs privés
                    
                    if (field.getType().equals(String.class) && values.length == 1) {
                        field.set(pojoInstance, values[0]);
                    } else if (field.getType().equals(String[].class) && values.length > 1) {
                        field.set(pojoInstance, values);
                    } else {
                        // Pour les autres types de champs (int, Integer, Date, etc.)
                        if (values.length == 1) {
                            Object convertedValue = convertParameterValue(values[0], field.getType());
                            field.set(pojoInstance, convertedValue);
                        }
                    }
                    continue;

                } catch (NoSuchFieldException ignored) { /* on ignore */ }
                
                // Gérer les cas où le paramètre n'a pas de champ/setter correspondant
                // (Souvent ignoré, car tous les paramètres de formulaire ne correspondent pas à des champs de la classe)
                
            } catch (Exception e) {
                // Erreur lors de l'invocation du setter ou de l'accès au champ
                System.err.println("Erreur de binding pour le champ " + paramName + " dans la classe " + pojoClass.getName() + ": " + e.getMessage());
            }
        }
    }
    private boolean isStringByteArrayMap(java.lang.reflect.Parameter parameter) {
    java.lang.reflect.Type type = parameter.getParameterizedType();
    if (type instanceof java.lang.reflect.ParameterizedType) {
        java.lang.reflect.ParameterizedType pType = (java.lang.reflect.ParameterizedType) type;
        java.lang.reflect.Type[] args = pType.getActualTypeArguments();
        
        if (args.length == 2) {
            boolean keyIsString = args[0].equals(String.class);
            boolean valueIsByteArray = false;
            if (args[1] instanceof Class) {
                Class<?> clz = (Class<?>) args[1];
                valueIsByteArray = clz.isArray() && clz.getComponentType().equals(byte.class);
            }
            return keyIsString && valueIsByteArray;
        }
    }
    return false;
}
}
