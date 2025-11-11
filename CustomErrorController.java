package com.criminaldetector.controller;

import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;

@Controller
public class CustomErrorController implements ErrorController {

    @RequestMapping("/error")
    public String handleError(HttpServletRequest request, Model model) {
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        Object exception = request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);
        Object message = request.getAttribute(RequestDispatcher.ERROR_MESSAGE);
        
        StringBuilder errorMsg = new StringBuilder("An error occurred: ");
        
        if (status != null) {
            errorMsg.append(" (Status: ").append(status).append(")");
        }
        
        if (message != null) {
            errorMsg.append(" - ").append(message);
        }
        
        if (exception != null) {
            errorMsg.append(" - ").append(((Exception) exception).getMessage());
        }
        
        System.err.println("Error details: " + errorMsg.toString()); // Debug log
        model.addAttribute("error", errorMsg.toString());
        return "detection";
    }
} 