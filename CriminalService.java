package com.criminaldetector.service;

import com.criminaldetector.model.Criminal;
import java.util.List;

public interface CriminalService {
    Criminal saveCriminal(Criminal criminal);
    Criminal getCriminalById(Long id);
    List<Criminal> getAllCriminals();
    void deleteCriminal(Long id);
} 