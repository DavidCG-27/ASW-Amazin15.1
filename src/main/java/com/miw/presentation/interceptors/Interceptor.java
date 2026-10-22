package com.miw.presentation.interceptors;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.miw.presentation.commands.Command;

public interface Interceptor {

    String BLOCKED_ATTRIBUTE = "commandBlocked";
    String ERROR_MESSAGE_ATTRIBUTE = "errorMessage";

    /**
     * Devuelve false para detener la cadena y omitir el comando. El interceptor
     * puede aportar ERROR_MESSAGE_ATTRIBUTE, pero no necesita escribir la respuesta
     * ni conocer una vista. El controlador muestra la vista de bloqueo configurada
     * con estado 403 y un mensaje de error.
     */
    boolean preExecute(HttpServletRequest request, HttpServletResponse response, Command command) throws Exception;

    /**
     * Se invoca en orden inverso para cada interceptor cuyo preExecute se inició,
     * incluido el que bloqueó o lanzó una excepción. ex contiene el fallo propagado;
     * BLOCKED_ATTRIBUTE distingue un bloqueo normal de una ejecución correcta.
     */
    void postExecute(HttpServletRequest request, HttpServletResponse response, Command command, Exception ex) throws Exception;
}
