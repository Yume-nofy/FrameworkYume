package nofy.p17;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class FrontServlet extends HttpServlet {

    private RequestDispatcher defaultDispatcher;
    private MyScanner controllerScanner;
    private Map<String, RouteInfo> routeMap;

    @Override
    public void init() throws ServletException {
        defaultDispatcher = getServletContext().getNamedDispatcher("default");
        controllerScanner = new MyScanner();
        routeMap = new HashMap<>();
        
        // Scanner et initialiser les contrôleurs au démarrage
        initializeControllers();
    }

private void initializeControllers() throws ServletException {
    try {
        // Scanner le package des contrôleurs
        controllerScanner.scanControllersFromPackage("nofy.controllers");
        
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
            
            for (Method method : controller.getDeclaredMethods()) {
                if (method.isAnnotationPresent(MyMap.class)) {
                    MyMap myMapAnnotation = method.getAnnotation(MyMap.class);
                    String methodUrl = myMapAnnotation.url();
                    
                    if (!methodUrl.startsWith("/")) {
                        methodUrl = "/" + methodUrl; // Normaliser
                    }
                    
                    String fullUrl = baseUrl + methodUrl;
                    
                    routeMap.put(fullUrl, new RouteInfo(controller, method));
                    System.out.println("✅ Route enregistrée: " + fullUrl + " -> " + method.getName());
                }
            }
        }
        
        System.out.println("🎯 " + routeMap.size() + " routes chargées");
        
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
            defaultServe(req, res);
        } else {
            // Vérifier si c'est une route de contrôleur
            RouteInfo route = findMatchingRoute(path);
            if (route != null) {
                invokeController(route, req, res);
            } else {
                customServe(req, res);
            }
        }
    }

    private RouteInfo findMatchingRoute(String path) {
        // Recherche exacte d'abord
        RouteInfo exactMatch = routeMap.get(path);
        if (exactMatch != null) {
            return exactMatch;
        }
        
        // Recherche avec matching de pattern (pour les paramètres)
        for (Map.Entry<String, RouteInfo> entry : routeMap.entrySet()) {
            if (UrlMatcher.matches(entry.getKey(), path)) {
                return entry.getValue();
            }
        }
        
        return null;
    }

    private void invokeController(RouteInfo route, HttpServletRequest req, HttpServletResponse res) {
        try {
            // Créer une instance du contrôleur
            Object controllerInstance = route.getControllerClass().getDeclaredConstructor().newInstance();
            
            // Appeler la méthode du contrôleur
            Object result = route.getMethod().invoke(controllerInstance);
            
            // Gérer la réponse
            if (result instanceof String) {
                handleStringResponse((String) result, req, res);
            } else {
                // Par défaut, retourner le résultat comme JSON
                handleJsonResponse(result, res);
            }
            
        } catch (Exception e) {
            handleControllerError(e, res);
        }
    }

    private void handleStringResponse(String result, HttpServletRequest req, HttpServletResponse res) 
            throws ServletException, IOException {
        // Si le résultat contient "redirect:", faire une redirection
        if (result.startsWith("redirect:")) {
            String redirectUrl = result.substring("redirect:".length());
            res.sendRedirect(redirectUrl);
            return;
        }
        
        // Sinon, traiter comme une vue JSP
        String viewPath = "/WEB-INF/views/" + result + ".jsp";
        if (getServletContext().getResource(viewPath) != null) {
            RequestDispatcher dispatcher = req.getRequestDispatcher(viewPath);
            dispatcher.forward(req, res);
        } else {
            // Si pas de vue JSP, retourner le texte directement
            res.setContentType("text/html;charset=UTF-8");
            res.getWriter().println(result);
        }
    }

    private void handleJsonResponse(Object result, HttpServletResponse res) throws IOException {
        res.setContentType("application/json;charset=UTF-8");
        // Simplifié - dans une vraie implémentation, utiliser Gson ou Jackson
        String json = "{\"result\": \"" + result.toString() + "\"}";
        res.getWriter().println(json);
    }

    private void handleControllerError(Exception e, HttpServletResponse res) {
        try {
            res.setStatus(500);
            res.setContentType("text/html;charset=UTF-8");
            PrintWriter out = res.getWriter();
            out.println("""
                <html>
                    <head><title>500 Internal Server Error</title></head>
                    <body>
                        <h1>Erreur dans le contrôleur</h1>
                        <p><strong>Message:</strong> %s</p>
                    </body>
                </html>
                """.formatted(e.getMessage()));
        } catch (IOException ioException) {
            ioException.printStackTrace();
        }
    }

    private void customServe(HttpServletRequest req, HttpServletResponse res) throws IOException {
        try (PrintWriter out = res.getWriter()) {
            String uri = req.getRequestURI();
            String responseBody = """
                <html>
                    <head><title>404 Not Found</title></head>
                    <body>
                        <h1>404 - Page non trouvée</h1>
                        <p>The requested URL was not found: <strong>%s</strong></p>
                        <p><em>Aucun contrôleur trouvé pour cette URL.</em></p>
                    </body>
                </html>
                """.formatted(uri);

            res.setContentType("text/html;charset=UTF-8");
            res.setStatus(404);
            out.println(responseBody);
        }
    }

    private void defaultServe(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        defaultDispatcher.forward(req, res);
    }

    // Classe interne pour stocker les informations de route
    private static class RouteInfo {
        private Class<?> controllerClass;
        private Method method;
        
        public RouteInfo(Class<?> controllerClass, Method method) {
            this.controllerClass = controllerClass;
            this.method = method;
        }
        
        public Class<?> getControllerClass() { return controllerClass; }
        public Method getMethod() { return method; }
    }
}