package com.miw.presentation.controller;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.Properties;
import org.apache.logging.log4j.*;
import com.miw.presentation.commands.Command;

public class ControllerConfigurationManager {

	protected Logger logger = LogManager.getLogger(getClass());
	private static ControllerConfigurationManager instance = null;
	private Properties data;

	public static synchronized ControllerConfigurationManager getInstance() {
		if (instance == null) {
			instance = new ControllerConfigurationManager();
		}
		return instance;
	}

	public ControllerConfigurationManager() {
		// We load the properties file
		data = new Properties();
		try {
			InputStream file = ControllerConfigurationManager.class.getClassLoader()
					.getResourceAsStream("controller.properties");
			logger.debug("Loading " + file);
			data.load(file);
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	public Command getCommand(String action) {
		try {

			Class<?> clazz = Class.forName(data.getProperty(action + ".command"));
			return (Command) clazz.getDeclaredConstructor().newInstance();

		} catch (ClassNotFoundException e) {
			throw new RuntimeException(e);
		} catch (InstantiationException e) {
			throw new RuntimeException(e);
		} catch (IllegalAccessException e) {
			throw new RuntimeException(e);
		} catch (IllegalArgumentException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InvocationTargetException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (NoSuchMethodException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (SecurityException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

	public String getForward(String action) {
		return data.getProperty(action + ".success");
	}
}
