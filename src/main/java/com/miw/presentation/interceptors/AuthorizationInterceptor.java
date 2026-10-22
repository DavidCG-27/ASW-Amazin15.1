package com.miw.presentation.interceptors;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import com.miw.presentation.commands.Command;
import com.miw.presentation.di.HttpSessionAware;
import java.util.ResourceBundle;
import java.util.Arrays;
import java.util.List;

public class AuthorizationInterceptor implements Interceptor, HttpSessionAware {

    private HttpSession session;

    @Override
    public void setHttpSession(HttpSession session) {
        this.session = session;
    }

    @Override
    public boolean preExecute(HttpServletRequest request, HttpServletResponse response, Command command) throws Exception {
        String commandName = command.getClass().getSimpleName();
        
        String username = (session != null) ? (String) session.getAttribute("user") : null;
        
        if (username == null) {
            return denegarAcceso(request, "Debe iniciar sesión para ejecutar esta acción.");
        }

        ResourceBundle usersBundle = ResourceBundle.getBundle("users");
        if (!usersBundle.containsKey("user." + username + ".roles")) {
            return denegarAcceso(request, "El usuario no tiene roles asignados.");
        }
        
        String[] roles = usersBundle.getString("user." + username + ".roles").split(",");

        ResourceBundle permissionsBundle = ResourceBundle.getBundle("permissions");
        boolean isAuthorized = false;

        for (String role : roles) {
            String roleKey = "role." + role.trim() + ".commands";
            if (permissionsBundle.containsKey(roleKey)) {
                String[] allowedCommands = permissionsBundle.getString(roleKey).split(",");
                List<String> allowedList = Arrays.asList(allowedCommands);
                
                for (String allowed : allowedList) {
                    if (allowed.trim().equals(commandName)) {
                        isAuthorized = true;
                        break;
                    }
                }
            }
            if (isAuthorized) break;
        }

        if (isAuthorized) {
            return true;
        } else {
            return denegarAcceso(request, "No tiene permisos para ejecutar el comando: " + commandName);
        }
    }

    @Override
    public void postExecute(HttpServletRequest request, HttpServletResponse response, Command command, Exception ex) throws Exception {
        // Este interceptor no requiere realizar operaciones después del comando
    }

    private boolean denegarAcceso(HttpServletRequest request, String mensaje) {
        request.setAttribute(Interceptor.ERROR_MESSAGE_ATTRIBUTE, mensaje);
        return false;
    }
}
