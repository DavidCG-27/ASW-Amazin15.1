package com.miw.presentation.di;

import jakarta.servlet.http.HttpSession;

public interface HttpSessionAware {
	public void setHttpSession(HttpSession session );
}
