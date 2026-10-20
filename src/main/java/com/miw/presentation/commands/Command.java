package com.miw.presentation.commands;

import org.apache.logging.log4j.*;

public interface Command {
	Logger logger = LogManager.getLogger(Command.class);

	public abstract void execute();
}