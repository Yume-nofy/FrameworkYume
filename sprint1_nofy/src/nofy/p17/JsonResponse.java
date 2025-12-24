package nofy.p17;

import java.util.*;

public class JsonResponse {
    private int code;
    private Object data;
    private String message;
    private String status;
    private long timestamp;
    
    // Constructeurs
    public JsonResponse() {
        this.timestamp = System.currentTimeMillis();
    }
    
    public JsonResponse(int code, Object data, String message, String status) {
        this.code = code;
        this.data = data;
        this.message = message;
        this.status = status;
        this.timestamp = System.currentTimeMillis();
    }
    
    // Getters
    public int getCode() { return code; }
    public Object getData() { return data; }
    public String getMessage() { return message; }
    public String getStatus() { return status; }
    public long getTimestamp() { return timestamp; }
    
    // Méthode pour convertir en JSON (version simplifiée)
    public String toJsonString() {
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"code\":").append(code).append(",");
        json.append("\"data\":").append(formatData(data)).append(",");
        json.append("\"message\":\"").append(escapeJson(message)).append("\",");
        json.append("\"status\":\"").append(escapeJson(status)).append("\"");
        json.append("}");
        return json.toString();
    }
    
    private String formatData(Object data) {
        if (data == null) return "null";
        
        if (data instanceof String) {
            return "\"" + escapeJson((String) data) + "\"";
        } else if (data instanceof Number || data instanceof Boolean) {
            return data.toString();
        } else if (data instanceof Collection) {
            return formatCollection((Collection<?>) data);
        } else if (data instanceof Object[]) {
            return formatArray((Object[]) data);
        } else {
            // Pour les objets simples, on utilise une réflexion basique
            return formatObject(data);
        }
    }
    
    private String formatCollection(Collection<?> collection) {
        StringBuilder sb = new StringBuilder("[");
        Iterator<?> it = collection.iterator();
        while (it.hasNext()) {
            sb.append(formatData(it.next()));
            if (it.hasNext()) sb.append(",");
        }
        sb.append("]");
        return sb.toString();
    }
    
    private String formatArray(Object[] array) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < array.length; i++) {
            sb.append(formatData(array[i]));
            if (i < array.length - 1) sb.append(",");
        }
        sb.append("]");
        return sb.toString();
    }
    
    private String formatObject(Object obj) {
        // Réflexion simple pour les objets
        try {
            StringBuilder sb = new StringBuilder("{");
            java.lang.reflect.Field[] fields = obj.getClass().getDeclaredFields();
            boolean first = true;
            
            for (java.lang.reflect.Field field : fields) {
                field.setAccessible(true);
                Object value = field.get(obj);
                
                if (value != null) {
                    if (!first) sb.append(",");
                    sb.append("\"").append(field.getName()).append("\":");
                    sb.append(formatData(value));
                    first = false;
                }
            }
            sb.append("}");
            return sb.toString();
        } catch (Exception e) {
            return "{}";
        }
    }
    
    private String escapeJson(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t");
    }
}