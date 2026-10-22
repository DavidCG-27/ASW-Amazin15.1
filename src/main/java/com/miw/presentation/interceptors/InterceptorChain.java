package com.miw.presentation.interceptors;

import java.util.ArrayList;
import java.util.List;

import com.miw.presentation.commands.Command;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Ejecuta una cadena por petición; no selecciona ni muestra vistas. */
public final class InterceptorChain {
    private final List<Interceptor> interceptors;

    public InterceptorChain(List<Interceptor> interceptors) {
        this.interceptors = List.copyOf(interceptors);
    }

    public boolean execute(HttpServletRequest req, HttpServletResponse resp, Command command) throws Exception {
        List<Interceptor> started = new ArrayList<>();
        Exception failure = null;
        boolean allowed = true;
        req.removeAttribute(Interceptor.BLOCKED_ATTRIBUTE);
        try {
            for (Interceptor interceptor : interceptors) {
                started.add(interceptor);
                if (!interceptor.preExecute(req, resp, command)) {
                    allowed = false;
                    req.setAttribute(Interceptor.BLOCKED_ATTRIBUTE, Boolean.TRUE);
                    break;
                }
            }
            if (allowed) {
                command.execute();
            }
        } catch (Exception e) {
            failure = e;
        } finally {
            for (int i = started.size() - 1; i >= 0; i--) {
                try {
                    started.get(i).postExecute(req, resp, command, failure);
                } catch (Exception e) {
                    if (failure == null) {
                        failure = e;
                    } else if (failure != e) {
                        failure.addSuppressed(e);
                    }
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
        return allowed;
    }

}
