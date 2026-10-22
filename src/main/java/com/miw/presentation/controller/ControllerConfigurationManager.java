package com.miw.presentation.controller;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import com.miw.presentation.commands.Command;
import com.miw.presentation.interceptors.Interceptor;

public class ControllerConfigurationManager {
    private static ControllerConfigurationManager instance;
    private final Properties data;
    private final Properties commands;
    private final Properties interceptors;

    public static synchronized ControllerConfigurationManager getInstance() {
        if (instance == null) {
            instance = new ControllerConfigurationManager();
        }
        return instance;
    }

    private ControllerConfigurationManager() {
        data = loadProperties("controller.properties");
        commands = loadProperties("commands.properties");
        interceptors = loadProperties("interceptors.properties");
    }

    private Properties loadProperties(String resource) {
        try (InputStream file = getClass().getClassLoader().getResourceAsStream(resource)) {
            if (file == null) {
                throw new IllegalStateException("No se encuentra " + resource);
            }
            Properties properties = new Properties();
            properties.load(file);
            return properties;
        } catch (IOException e) {
            throw new IllegalStateException("No se puede cargar " + resource, e);
        }
    }

    public Command getCommand(String action) {
        String className = data.getProperty(action + ".command");
        if (className == null) {
            return null;
        }
        try {
            return Class.forName(className.trim()).asSubclass(Command.class).getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException | ClassCastException e) {
            throw new IllegalStateException("No se puede crear el comando de " + action, e);
        }
    }

    public String getForward(String action) {
        String forward = data.getProperty(action + ".success");
        if (forward == null || forward.isBlank()) {
            throw new IllegalStateException("No hay vista de destino para " + action);
        }
        return forward.trim();
    }

    public String getBlockedForward(String action) {
        String forward = data.getProperty(action + ".blocked", data.getProperty("controller.blocked"));
        if (forward == null || forward.isBlank()) {
            throw new IllegalStateException("No hay vista de bloqueo para " + action);
        }
        return forward.trim();
    }

    public List<Interceptor> getInterceptorsForCommand(String action) {
        String key = "command." + action + ".interceptors";
        String aliases = commands.getProperty(key);
        if (aliases == null) {
            throw new IllegalStateException("Falta la configuración " + key);
        }
        List<Interceptor> result = new ArrayList<>();
        if (aliases.isBlank()) {
            return result;
        }
        for (String alias : aliases.split(",", -1)) {
            String className = interceptors.getProperty("interceptor." + alias.trim() + ".class");
            if (className == null || className.isBlank()) {
                throw new IllegalStateException("No hay clase para el interceptor " + alias);
            }
            try {
                result.add(Class.forName(className.trim()).asSubclass(Interceptor.class)
                        .getDeclaredConstructor().newInstance());
            } catch (ReflectiveOperationException | ClassCastException e) {
                throw new IllegalStateException("No se puede crear el interceptor " + alias, e);
            }
        }
        return result;
    }
}
