package com.criminaldetector.repository;

import com.criminaldetector.model.Criminal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CriminalRepository extends JpaRepository<Criminal, Long> {
} 