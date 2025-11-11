package com.criminaldetector.controller;

import com.criminaldetector.service.CriminalService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class WebController {

    @Autowired
    private CriminalService criminalService;

    @GetMapping("/criminals")
    public String showCriminals(Model model) {
        model.addAttribute("criminals", criminalService.getAllCriminals());
        return "criminals";
    }
} 