package nofy.p17;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class MyScanner {
    private List<Class<?>> controllers = new ArrayList<>();
    
    // Scanner depuis un JAR
    public void scanControllersFromJar(String jarPath) throws Exception {
        System.out.println("🔍 Scan du JAR: " + jarPath);
        
        JarFile jarFile = new JarFile(jarPath);
        Enumeration<JarEntry> entries = jarFile.entries();
        
        URL[] urls = { new URL("jar:file:" + jarPath + "!/") };
        URLClassLoader classLoader = URLClassLoader.newInstance(urls);
        
        int found = 0;
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            if (entry.getName().endsWith(".class")) {
                String className = entry.getName()
                    .replace("/", ".")
                    .replace(".class", "");
                
                try {
                    Class<?> clazz = classLoader.loadClass(className);
                    if (clazz.isAnnotationPresent(Controller.class)) {
                        controllers.add(clazz);
                        found++;
                        System.out.println("✅ Contrôleur: " + className);
                    }
                } catch (NoClassDefFoundError | ClassNotFoundException e) {
                    // Ignorer
                }
            }
        }
        jarFile.close();
        System.out.println("🎯 " + found + " contrôleurs trouvés dans le JAR");
    }
    
    // Scanner depuis un package (classpath)
    public void scanControllersFromPackage(String packageName) throws Exception {
        System.out.println("🔍 Scan du package: " + packageName);
        
        String path = packageName.replace('.', '/');
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        Enumeration<URL> resources = classLoader.getResources(path);
        
        int found = 0;
        while (resources.hasMoreElements()) {
            URL resource = resources.nextElement();
            if (resource.getProtocol().equals("file")) {
                scanDirectory(new File(resource.getFile()), packageName, found);
            }
        }
        System.out.println("🎯 " + found + " contrôleurs trouvés dans le package");
    }
    
    private void scanDirectory(File directory, String packageName, int count) throws Exception {
        File[] files = directory.listFiles();
        if (files == null) return;
        
        for (File file : files) {
            if (file.isDirectory()) {
                scanDirectory(file, packageName + "." + file.getName(), count);
            } else if (file.getName().endsWith(".class")) {
                String className = packageName + '.' + file.getName().replace(".class", "");
                try {
                    Class<?> clazz = Class.forName(className);
                    if (clazz.isAnnotationPresent(Controller.class)) {
                        controllers.add(clazz);
                        count++;
                        System.out.println("✅ Contrôleur: " + className);
                    }
                } catch (ClassNotFoundException | NoClassDefFoundError e) {
                    // Ignorer
                }
            }
        }
    }
    
    public List<Class<?>> getControllers() {
        return controllers;
    }
}