package com.miw.presentation.commands;

import com.miw.presentation.di.ServletRequestAware;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

public class LoginCommand implements Command, ServletRequestAware {

	private HttpServletRequest request;
	
    @Override
    public void execute() {
        String username = request.getParameter("username");
        
        if (username != null && !username.trim().isEmpty()) {

            HttpSession session = request.getSession();
            session.setAttribute("user", username);
        }
        
    }

	@Override
	public void setServletRequest(HttpServletRequest request) {
		this.request=request;
	}
}