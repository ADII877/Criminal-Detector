package com.criminaldetector.service;

import com.criminaldetector.model.Criminal;
import com.criminaldetector.repository.CriminalRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class CriminalServiceImpl implements CriminalService {

    private static final Logger logger = LoggerFactory.getLogger(CriminalServiceImpl.class);

    @Autowired
    private CriminalRepository criminalRepository;

    @Override
    @Transactional
    public Criminal saveCriminal(Criminal criminal) {
        try {
            logger.info("Attempting to save criminal: {}", criminal.getName());
            Criminal savedCriminal = criminalRepository.save(criminal);
            logger.info("Successfully saved criminal with ID: {}", savedCriminal.getId());
            return savedCriminal;
        } catch (Exception e) {
            logger.error("Error saving criminal: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to save criminal record: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Criminal getCriminalById(Long id) {
        try {
            logger.info("Fetching criminal with ID: {}", id);
            return criminalRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Criminal not found with ID: " + id));
        } catch (Exception e) {
            logger.error("Error fetching criminal with ID {}: {}", id, e.getMessage(), e);
            throw new RuntimeException("Failed to fetch criminal: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<Criminal> getAllCriminals() {
        try {
            logger.info("Fetching all criminals");
            return criminalRepository.findAll();
        } catch (Exception e) {
            logger.error("Error fetching all criminals: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to fetch criminals: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public void deleteCriminal(Long id) {
        try {
            logger.info("Deleting criminal with ID: {}", id);
            criminalRepository.deleteById(id);
            logger.info("Successfully deleted criminal with ID: {}", id);
        } catch (Exception e) {
            logger.error("Error deleting criminal with ID {}: {}", id, e.getMessage(), e);
            throw new RuntimeException("Failed to delete criminal: " + e.getMessage(), e);
        }
    }
}