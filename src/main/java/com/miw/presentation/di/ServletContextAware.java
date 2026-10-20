package com.miw.presentation.di;

import jakarta.servlet.ServletContext;

public interface ServletContextAware {
	public void setServletContext( ServletContext context );
}
