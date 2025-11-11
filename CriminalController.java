package com.criminaldetector.controller;

import com.criminaldetector.model.Criminal;
import com.criminaldetector.service.CriminalService;
import com.criminaldetector.service.FaceDetectionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.ResponseEntity;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.List;
import java.io.File;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Controller
public class CriminalController {

    private static final Logger logger = LoggerFactory.getLogger(CriminalController.class);

    @Autowired
    private CriminalService criminalService;

    @Autowired
    private FaceDetectionService faceDetectionService;

    // Store uploads in static resources directory
    private static final String UPLOAD_DIR = "src/main/resources/static/uploads";

    @PostConstruct
    public void init() {
        // Create upload directory when application starts
        try {
            File uploadDir = new File(UPLOAD_DIR);
            if (!uploadDir.exists()) {
                boolean created = uploadDir.mkdirs();
                if (!created) {
                    throw new RuntimeException("Failed to create upload directory: " + UPLOAD_DIR);
                }
                logger.info("Created upload directory: {}", UPLOAD_DIR);
            }
        } catch (Exception e) {
            logger.error("Error creating upload directory: {}", e.getMessage(), e);
        }
    }

    @GetMapping({"/", "/home"})
    public String showDetectionPage(Model model) {
        model.addAttribute("criminals", criminalService.getAllCriminals());
        return "detection";
    }

    @GetMapping("/criminal-list")
    public String showCriminalList(Model model) {
        model.addAttribute("criminals", criminalService.getAllCriminals());
        return "criminal-list";
    }

    @PostMapping("/addCriminal")
    public String addCriminal(@RequestParam("name") String name,
                            @RequestParam("age") int age,
                            @RequestParam("gender") String gender,
                            @RequestParam("crimeDetails") String crimeDetails,
                            @RequestParam("photo") MultipartFile photo,
                            RedirectAttributes redirectAttributes) {
        try {
            logger.info("Starting criminal addition process...");
            logger.info("Upload directory: {}", UPLOAD_DIR);

            // Validate input parameters
            if (name == null || name.trim().isEmpty()) {
                throw new IllegalArgumentException("Name is required");
            }
            if (age < 0 || age > 150) {
                throw new IllegalArgumentException("Age must be between 0 and 150");
            }
            if (gender == null || gender.trim().isEmpty()) {
                throw new IllegalArgumentException("Gender is required");
            }
            if (crimeDetails == null || crimeDetails.trim().isEmpty()) {
                throw new IllegalArgumentException("Crime details is required");
            }

            // Validate file
            if (photo == null || photo.isEmpty()) {
                throw new IllegalArgumentException("Please select a photo");
            }

            // Validate file type
            String contentType = photo.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                throw new IllegalArgumentException("Please upload an image file");
            }

            // Generate unique filename with original extension
            String originalFilename = photo.getOriginalFilename();
            String extension = originalFilename != null ? originalFilename.substring(originalFilename.lastIndexOf(".")) : ".jpg";
            String filename = UUID.randomUUID().toString() + extension;
            logger.info("Generated filename: {}", filename);
            
            // Ensure upload directory exists
            File uploadDir = new File(UPLOAD_DIR);
            if (!uploadDir.exists()) {
                boolean created = uploadDir.mkdirs();
                if (!created) {
                    throw new IOException("Failed to create upload directory: " + UPLOAD_DIR);
                }
            }

            // Create criminal record
            Criminal criminal = new Criminal();
            criminal.setName(name.trim());
            criminal.setAge(age);
            criminal.setGender(gender.trim());
            criminal.setCrimeDetails(crimeDetails.trim());
            criminal.setImageName(filename);
            
            logger.info("Attempting to save criminal to database...");
            
            try {
                // Save to database first
                Criminal savedCriminal = criminalService.saveCriminal(criminal);
                if (savedCriminal == null || savedCriminal.getId() == null) {
                    throw new RuntimeException("Failed to save criminal record to database");
                }
                
                // After successful database save, save the file
                File destFile = new File(UPLOAD_DIR, filename);
                logger.info("Saving file to: {}", destFile.getAbsolutePath());
                
                try {
                    Files.copy(photo.getInputStream(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    if (!destFile.exists()) {
                        throw new IOException("File was not saved successfully");
                    }
                    // Clear the embeddings cache after successful file save
                    faceDetectionService.clearEmbeddingsCache();
                } catch (IOException e) {
                    // If file save fails, delete the database record
                    criminalService.deleteCriminal(savedCriminal.getId());
                    throw new IOException("Failed to save photo file: " + e.getMessage(), e);
                }
                
                redirectAttributes.addFlashAttribute("success", "Criminal added successfully!");
                logger.info("Criminal added successfully with ID: {}", savedCriminal.getId());
                
            } catch (Exception e) {
                throw new RuntimeException("Failed to save criminal record: " + e.getMessage(), e);
            }
            
            return "redirect:/add-criminal";
            
        } catch (Exception e) {
            logger.error("Error adding criminal: {}", e.getMessage(), e);
            
            String errorMessage = e.getMessage();
            if (errorMessage == null || errorMessage.trim().isEmpty()) {
                errorMessage = "An unexpected error occurred";
            }
            redirectAttributes.addFlashAttribute("error", errorMessage);
            return "redirect:/";
        }
    }

    @GetMapping("/edit/{id}")
    public String showEditForm(@PathVariable Long id, Model model) {
        try {
            Criminal criminal = criminalService.getCriminalById(id);
            model.addAttribute("criminal", criminal);
            return "edit";
        } catch (Exception e) {
            logger.error("Error showing edit form for criminal ID {}: {}", id, e.getMessage(), e);
            return "redirect:/?error=Criminal not found";
        }
    }

    @PostMapping("/edit/{id}")
    public String updateCriminal(@PathVariable Long id,
                               @RequestParam("name") String name,
                               @RequestParam("age") int age,
                               @RequestParam("gender") String gender,
                               @RequestParam("crimeDetails") String crimeDetails,
                               @RequestParam(value = "photo", required = false) MultipartFile photo,
                               RedirectAttributes redirectAttributes) {
        try {
            Criminal criminal = criminalService.getCriminalById(id);
            if (criminal == null) {
                throw new RuntimeException("Criminal not found with ID: " + id);
            }

            // Update basic information
            criminal.setName(name.trim());
            criminal.setAge(age);
            criminal.setGender(gender.trim());
            criminal.setCrimeDetails(crimeDetails.trim());

            // Handle photo update if provided
            if (photo != null && !photo.isEmpty()) {
                // Validate file type
                String contentType = photo.getContentType();
                if (contentType == null || !contentType.startsWith("image/")) {
                    throw new IllegalArgumentException("Please upload an image file");
                }

                // Delete old photo if it exists
                File oldPhoto = new File(UPLOAD_DIR, criminal.getImageName());
                if (oldPhoto.exists()) {
                    oldPhoto.delete();
                }

                // Save new photo
                String originalFilename = photo.getOriginalFilename();
                String extension = originalFilename != null ? originalFilename.substring(originalFilename.lastIndexOf(".")) : ".jpg";
                String filename = UUID.randomUUID().toString() + extension;
                
                File destFile = new File(UPLOAD_DIR, filename);
                Files.copy(photo.getInputStream(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                
                criminal.setImageName(filename);
            }

            criminalService.saveCriminal(criminal);
            // Clear the embeddings cache after successful update
            faceDetectionService.clearEmbeddingsCache();
            redirectAttributes.addFlashAttribute("success", "Criminal updated successfully!");
            return "redirect:/";

        } catch (Exception e) {
            logger.error("Error updating criminal: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/edit/" + id;
        }
    }

    @DeleteMapping("/deleteCriminal/{id}")
    @ResponseBody
    public ResponseEntity<?> deleteCriminal(@PathVariable Long id) {
        try {
            Criminal criminal = criminalService.getCriminalById(id);
            if (criminal != null) {
                // Delete photo file using imageName
                Path photoPath = Paths.get(UPLOAD_DIR, criminal.getImageName());
                Files.deleteIfExists(photoPath);
                
                // Delete database record
                criminalService.deleteCriminal(id);
                return ResponseEntity.ok().build();
            }
            return ResponseEntity.notFound().build();
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/delete/{id}")
    public String deleteCriminal(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            Criminal criminal = criminalService.getCriminalById(id);
            if (criminal != null) {
                // Delete photo file
                File photo = new File(UPLOAD_DIR, criminal.getImageName());
                if (photo.exists()) {
                    photo.delete();
                }
                
                // Delete database record
                criminalService.deleteCriminal(id);
                redirectAttributes.addFlashAttribute("success", "Criminal deleted successfully!");
            } else {
                redirectAttributes.addFlashAttribute("error", "Criminal not found");
            }
        } catch (Exception e) {
            logger.error("Error deleting criminal: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", "Error deleting criminal: " + e.getMessage());
        }
        return "redirect:/";
    }

    @GetMapping("/editCriminal/{id}")
    public String showEditFormOld(@PathVariable Long id, Model model) {
        Criminal criminal = criminalService.getCriminalById(id);
        if (criminal != null) {
            model.addAttribute("criminal", criminal);
            return "editCriminal";
        }
        return "redirect:/";
    }

    @GetMapping("/detect")
    public String showDetectionForm() {
        return "detection";
    }

    @GetMapping("/add-criminal")
    public String showAddCriminalForm(Model model) {
        return "add-criminal";
    }

    @GetMapping("/detect-criminal")
    public String showDetectCriminalForm(Model model) {
        return "detect-criminal";
    }

    @PostMapping("/detect")
    public String detectCriminal(@RequestParam("image") MultipartFile image, Model model) {
        try {
            logger.info("Starting criminal detection process...");

            // Validate file
            if (image == null || image.isEmpty()) {
                throw new IllegalArgumentException("No image provided");
            }

            // Validate file type
            String contentType = image.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                throw new IllegalArgumentException("Invalid file type. Please upload an image file.");
            }

            // Save the uploaded image temporarily
            String filename = UUID.randomUUID().toString() + ".jpg";
            Path tempPath = Paths.get(UPLOAD_DIR, filename);
            Files.copy(image.getInputStream(), tempPath, StandardCopyOption.REPLACE_EXISTING);
            logger.info("Temporary image saved at: {}", tempPath);

            try {
                // Detect faces in the image
                List<Criminal> matches = faceDetectionService.detectCriminal(tempPath.toString());
                logger.info("Face detection completed. Found {} matches", matches.size());

                if (!matches.isEmpty()) {
                    model.addAttribute("matchedcriminal", matches.get(0));
                    if (matches.size() > 1) {
                        model.addAttribute("otherMatches", matches.subList(1, matches.size()));
                    }
                    return "result";
                }

                model.addAttribute("message", "No criminal match found in our database.");
                return "result";

            } finally {
                // Always try to delete the temporary file
                try {
                    Files.deleteIfExists(tempPath);
                    logger.info("Temporary image deleted: {}", tempPath);
                } catch (IOException e) {
                    logger.warn("Failed to delete temporary image: {}", tempPath, e);
                }
            }

        } catch (IllegalArgumentException e) {
            logger.warn("Invalid input: {}", e.getMessage());
            model.addAttribute("error", e.getMessage());
            return "result";
        } catch (IOException e) {
            logger.error("IO error during detection: {}", e.getMessage(), e);
            model.addAttribute("error", "Failed to process the image. Please try again.");
            return "result";
        } catch (Exception e) {
            logger.error("Unexpected error during detection: {}", e.getMessage(), e);
            model.addAttribute("error", "An unexpected error occurred. Please try again.");
            return "result";
        }
    }

    @GetMapping("/error")
    public String handleError(Model model) {
        model.addAttribute("error", "An error occurred. Please try again.");
        return "redirect:/";
    }
}