package com.miw.presentation.interceptors;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Date;

import org.apache.logging.log4j.Logger;

import com.miw.presentation.commands.Command;

public class LoggingInterceptor implements Interceptor {

    private Logger log;

    public void setLog(Logger log) {
        this.log = log;
    }

    @Override
    public boolean preExecute(HttpServletRequest request, HttpServletResponse response, Command command) throws Exception {

        long startTime = System.currentTimeMillis();
        Date startDate = new Date(startTime);
        String commandName = command.getClass().getSimpleName();
        
        request.setAttribute("startTime_" + commandName, startTime);
        
        if (log != null) {
            log.info("INICIO - Comando: " + commandName + " | Fecha y hora: " + startDate.toString());
        }
        
        return true; 
    }

    @Override
    public void postExecute(HttpServletRequest request, HttpServletResponse response, Command command, Exception ex) throws Exception {
        String commandName = command.getClass().getSimpleName();
        
        Long startTime = (Long) request.getAttribute("startTime_" + commandName);
        
        if (startTime != null) {
            long duration = System.currentTimeMillis() - startTime;
            
            String resultado = (ex == null) ? "CORRECTAMENTE" : "CON EXCEPCION: " + ex.getClass().getSimpleName();
            if (ex == null && Boolean.TRUE.equals(request.getAttribute(Interceptor.BLOCKED_ATTRIBUTE))) {
                resultado = "BLOQUEADO";
            }
            
            if (log != null) {
                log.info("FIN - Comando: " + commandName + " | Resultado: " + resultado + " | Duración: " + duration + " ms");
            }
            request.removeAttribute("startTime_" + commandName);
        }
    }
}
