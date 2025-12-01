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
        }

        try {
            Object controllerInstance = methodInstances.get(targetMethod);
            Object result = invokeMethodWithParams(targetMethod, controllerInstance, req, res, pathParams);
            handleControllerResult(result, req, res);
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

    public void handleControllerResult(Object result, HttpServletRequest req, HttpServletResponse res) throws Exception {
        if (result == null) {
            res.setStatus(HttpServletResponse.SC_NO_CONTENT);
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
}
