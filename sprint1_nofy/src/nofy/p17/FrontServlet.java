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
    private Map<String, Class<?>> baseUrlToController;

    @Override
    public void init() throws ServletException {
        defaultDispatcher = getServletContext().getNamedDispatcher("default");
        controllerScanner = new MyScanner();
        baseUrlToController = new HashMap<>();

        // Scanner et initialiser les contrôleurs au démarrage
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
            defaultServe(req, res);
        } else {
            customServe(req, res);
        }
    }



    private void customServe(HttpServletRequest req, HttpServletResponse res) throws IOException {
    try (PrintWriter out = res.getWriter()) {
        String uri = req.getRequestURI();
        String path = uri.substring(req.getContextPath().length());

        // Vérifier si le path commence par un baseUrl de contrôleur
        for (Map.Entry<String, Class<?>> entry : baseUrlToController.entrySet()) {
            String baseUrl = entry.getKey();
            if (path.startsWith(baseUrl)) {
                displayControllerInfo(entry.getValue(), baseUrl, res);
                return;
            }
        }

        // Si aucun contrôleur ne correspond
        res.setStatus(HttpServletResponse.SC_NOT_FOUND);
        out.println(" requested URL was not found: " + path);
    }
}

    private void displayControllerInfo(Class<?> controllerClass, String baseUrl, HttpServletResponse res) throws IOException {
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

    private void defaultServe(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        defaultDispatcher.forward(req, res);
    }


}      