package com.miw.presentation.controller;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.miw.presentation.commands.Command;
import com.miw.presentation.di.HttpSessionAware;
import com.miw.presentation.di.ServletContextAware;
import com.miw.presentation.di.ServletRequestAware;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebServlet(value = "/Controller")
public class ControllerServlet extends HttpServlet {

	private static final long serialVersionUID = 1L;
	protected Logger logger = LogManager.getLogger(getClass());

	Command command = null;

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

		// We execute the received command
		String action = req.getParameter("action");
		if (action != null) {
			logger.debug("Executing action" + action);

			command = ControllerConfigurationManager.getInstance().getCommand(action);

			// We execute the command and redirect
			if (command != null) {
				// dependency injection
				if (command instanceof ServletRequestAware) {
					logger.debug("Injecting request in command " + action);
					((ServletRequestAware) command).setServletRequest(req);
				}
				if (command instanceof HttpSessionAware) {
					logger.debug("Injecting session in command " + action);
					((HttpSessionAware) command).setHttpSession(req.getSession());
				}
				if (command instanceof ServletContextAware) {
					logger.debug("Injecting session in command " + action);
					((ServletContextAware) command).setServletContext(req.getServletContext());
				}

				populateParameters(req, resp);

				command.execute();

				RequestDispatcher dispatcher = req
						.getRequestDispatcher(ControllerConfigurationManager.getInstance().getForward(action));
				dispatcher.forward(req, resp);
			}
		}

	}

	private void populateParameters(HttpServletRequest req, HttpServletResponse resp) {
		for (String s : req.getParameterMap().keySet()) {
			try {
				Method m = command.getClass().getMethod("set" + s.substring(0, 1).toUpperCase() + s.substring(1),
						String.class);
				if (m != null) {
					logger.debug("Found expected parameter " + "set" + s.substring(0, 1).toUpperCase() + s.substring(1)
							+ ", populating the value");
					try {
						m.invoke(command, req.getParameter(s));
					} catch (IllegalAccessException e) {
						e.printStackTrace();
					} catch (IllegalArgumentException e) {
						e.printStackTrace();
					} catch (InvocationTargetException e) {
						e.printStackTrace();
					}
				}
			} catch (NoSuchMethodException e) {
				// TODO Auto-generated catch block
				// e.printStackTrace();
			} catch (SecurityException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		this.doGet(req, resp);
	}
}
