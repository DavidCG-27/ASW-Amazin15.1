package com.miw.presentation.controller;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.miw.presentation.commands.Command;
import com.miw.presentation.di.HttpSessionAware;
import com.miw.presentation.di.ServletContextAware;
import com.miw.presentation.di.ServletRequestAware;
import com.miw.presentation.interceptors.Interceptor;
import com.miw.presentation.interceptors.InterceptorChain;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebServlet(value = "/Controller")
public class ControllerServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    protected Logger logger = LogManager.getLogger(getClass());

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String action = req.getParameter("action");
        if (action == null || action.isBlank()) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Falta el parámetro action");
            return;
        }
        try {
            ControllerConfigurationManager configuration = ControllerConfigurationManager.getInstance();

            Command command = configuration.getCommand(action);
            if (command == null) {
                resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Acción desconocida");
                return;
            }
            String forward = configuration.getForward(action);
            String blockedForward = configuration.getBlockedForward(action);
            List<Interceptor> interceptors = configuration.getInterceptorsForCommand(action);
            injectDependencies(command, req);
            populateParameters(command, req);
            for (Interceptor interceptor : interceptors) {
                injectDependencies(interceptor, req);
                try {
                    interceptor.getClass().getMethod("setLog", Logger.class).invoke(interceptor, logger);
                } catch (NoSuchMethodException e) {
                    // El setter del logger es opcional.
                }
            }
            boolean allowed = new InterceptorChain(interceptors).execute(req, resp, command);
            if (resp.isCommitted()) {
                return;
            }
            if (!allowed) {
                resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
                if (req.getAttribute(Interceptor.ERROR_MESSAGE_ATTRIBUTE) == null) {
                    req.setAttribute(Interceptor.ERROR_MESSAGE_ATTRIBUTE, "Operación no permitida.");
                }
                forward = blockedForward;
            }
            req.getRequestDispatcher(forward).forward(req, resp);
        } catch (Exception e) {
            throw new ServletException("Error ejecutando la acción " + action, e);
        }
    }

    private void injectDependencies(Object target, HttpServletRequest req) {
        if (target instanceof ServletRequestAware) {
            ((ServletRequestAware) target).setServletRequest(req);
        }
        if (target instanceof HttpSessionAware) {
            ((HttpSessionAware) target).setHttpSession(req.getSession());
        }
        if (target instanceof ServletContextAware) {
            ((ServletContextAware) target).setServletContext(req.getServletContext());
        }
    }

    private void populateParameters(Command command, HttpServletRequest req) throws ReflectiveOperationException {
        for (String parameter : req.getParameterMap().keySet()) {
            if (parameter.isEmpty() || "action".equals(parameter)) {
                continue;
            }
            try {
                Method setter = command.getClass().getMethod(
                        "set" + parameter.substring(0, 1).toUpperCase() + parameter.substring(1), String.class);
                setter.invoke(command, req.getParameter(parameter));
            } catch (NoSuchMethodException e) {
            }
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doGet(req, resp);
    }
}
