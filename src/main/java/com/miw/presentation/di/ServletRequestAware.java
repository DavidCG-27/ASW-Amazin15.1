package com.miw.presentation.di;

import jakarta.servlet.http.HttpServletRequest;

public interface ServletRequestAware {
	public void setServletRequest(HttpServletRequest request );
}
