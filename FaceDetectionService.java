package com.criminaldetector.service;

import com.criminaldetector.model.Criminal;
import org.bytedeco.javacpp.DoublePointer;
import org.bytedeco.opencv.opencv_core.*;
import org.bytedeco.opencv.opencv_objdetect.CascadeClassifier;
import static org.bytedeco.opencv.global.opencv_imgcodecs.*;
import static org.bytedeco.opencv.global.opencv_imgproc.*;
import static org.bytedeco.opencv.global.opencv_core.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.core.io.ClassPathResource;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class FaceDetectionService {
    private static final Logger logger = LoggerFactory.getLogger(FaceDetectionService.class);
    private static final String UPLOAD_DIR = "src/main/resources/static/uploads";
    private static final double SIMILARITY_THRESHOLD = 0.75; // Increased threshold for better accuracy
    private static final int MIN_FACE_SIZE = 80; // Minimum face size for detection
    private static final double SCALE_FACTOR = 1.05; // More precise scaling
    private static final int MIN_NEIGHBORS = 4; // Increased for better quality detections
    private final CascadeClassifier faceDetector;
    private final ConcurrentHashMap<String, Mat> criminalEmbeddings;
    
    @Autowired
    private CriminalService criminalService;

    public FaceDetectionService() throws IOException {
        // Create upload directory if it doesn't exist
        createUploadDirectory();

        // Initialize OpenCV face detector
        File cascadeFile = new ClassPathResource("haarcascade_frontalface_default.xml").getFile();
        faceDetector = new CascadeClassifier(cascadeFile.getAbsolutePath());
        if (faceDetector.empty()) {
            throw new IOException("Error loading face detection cascade classifier");
        }

        criminalEmbeddings = new ConcurrentHashMap<>();
        logger.info("FaceDetectionService initialized successfully with OpenCV face detection");
    }

    public List<Criminal> detectCriminal(String imagePath) throws IOException {
        logger.info("Starting face detection for image: {}", imagePath);
        
        List<Criminal> allCriminals = criminalService.getAllCriminals();
        logger.info("Total criminals in database: {}", allCriminals.size());

        // Load and preprocess the input image
        Mat image = imread(imagePath);
        if (image.empty()) {
            throw new IOException("Error loading image: " + imagePath);
        }

        // Enhance image quality
        Mat enhanced = new Mat();
        image.copyTo(enhanced);
        
        // Apply image enhancement techniques
        // 1. Normalize brightness and contrast
        Mat ycrcb = new Mat();
        cvtColor(enhanced, ycrcb, COLOR_BGR2YCrCb);
        
        // Split channels using MatVector
        MatVector channels = new MatVector(3);
        split(ycrcb, channels);
        
        // Equalize the luminance channel
        equalizeHist(channels.get(0), channels.get(0));
        
        // Merge channels back
        merge(channels, ycrcb);
        cvtColor(ycrcb, enhanced, COLOR_YCrCb2BGR);

        // 2. Reduce noise
        GaussianBlur(enhanced, enhanced, new Size(3, 3), 0);

        // Convert to grayscale for face detection
        Mat gray = new Mat();
        cvtColor(enhanced, gray, COLOR_BGR2GRAY);

        // Detect faces with improved parameters
        RectVector faces = new RectVector();
        faceDetector.detectMultiScale(
            gray, 
            faces,
            SCALE_FACTOR,  // More precise scaling
            MIN_NEIGHBORS, // Increased minimum neighbors
            0,            // Flags
            new Size(MIN_FACE_SIZE, MIN_FACE_SIZE), // Minimum face size
            new Size()    // Maximum face size
        );

        List<Criminal> detectedCriminals = new ArrayList<>();
        
        if (faces.empty()) {
            logger.warn("No faces detected in the image");
            return detectedCriminals;
        }

        // Process each detected face
        for (long i = 0; i < faces.size(); i++) {
            Rect faceRect = faces.get(i);
            
            // Extract and preprocess the face region
            Mat face = new Mat(enhanced, faceRect);
            Mat processedFace = preprocessFace(face);
            
            if (processedFace != null) {
                Criminal matchedCriminal = findMatchingCriminal(processedFace);
                if (matchedCriminal != null && !detectedCriminals.contains(matchedCriminal)) {
                    detectedCriminals.add(matchedCriminal);
                }
            }
        }

        return detectedCriminals;
    }

    private Mat preprocessFace(Mat face) {
        try {
            // Resize to standard size
            Mat resized = new Mat();
            resize(face, resized, new Size(256, 256));

            // Convert to grayscale
            Mat gray = new Mat();
            cvtColor(resized, gray, COLOR_BGR2GRAY);

            // Apply histogram equalization for better contrast
            equalizeHist(gray, gray);

            // Apply bilateral filter to reduce noise while preserving edges
            Mat filtered = new Mat();
            bilateralFilter(gray, filtered, 9, 75, 75);

            return filtered;
        } catch (Exception e) {
            logger.error("Error preprocessing face: {}", e.getMessage());
            return null;
        }
    }

    private Criminal findMatchingCriminal(Mat faceImage) {
        double bestMatch = 0;
        Criminal bestCriminal = null;

        for (Criminal criminal : criminalService.getAllCriminals()) {
            Mat criminalEmbedding = getCriminalEmbedding(criminal);
            if (criminalEmbedding != null) {
                // Calculate similarity using multiple metrics
                double similarity = calculateSimilarity(faceImage, criminalEmbedding);
                
                if (similarity > SIMILARITY_THRESHOLD && similarity > bestMatch) {
                    bestMatch = similarity;
                    bestCriminal = criminal;
                }
            }
        }

        return bestCriminal;
    }

    private double calculateSimilarity(Mat face1, Mat face2) {
        try {
            // Ensure both images are the same size
            if (face1.cols() != face2.cols() || face1.rows() != face2.rows()) {
                resize(face2, face2, face1.size());
            }

            // Calculate structural similarity
            double ssim = calculateSSIM(face1, face2);
            
            // Calculate normalized correlation coefficient
            Mat result = new Mat();
            matchTemplate(face1, face2, result, TM_CCOEFF_NORMED);
            DoublePointer minVal = new DoublePointer(1);
            DoublePointer maxVal = new DoublePointer(1);
            Point minLoc = new Point();
            Point maxLoc = new Point();
            minMaxLoc(result, minVal, maxVal, minLoc, maxLoc, null);
            double correlation = maxVal.get();

            // Combine both metrics
            return (ssim + correlation) / 2.0;
        } catch (Exception e) {
            logger.error("Error calculating similarity: {}", e.getMessage());
            return 0.0;
        }
    }

    private double calculateSSIM(Mat img1, Mat img2) {
        // Constants for SSIM calculation
        double k1 = 0.01;
        double k2 = 0.03;
        double L = 255.0;
        double c1 = (k1 * L) * (k1 * L);
        double c2 = (k2 * L) * (k2 * L);

        // Calculate means
        Scalar mean1 = mean(img1);
        Scalar mean2 = mean(img2);
        double mu1 = mean1.get(0);
        double mu2 = mean2.get(0);

        // Calculate variances and covariance
        Mat mu1_sq = new Mat();
        Mat mu2_sq = new Mat();
        Mat mu1_mu2 = new Mat();
        
        multiply(img1, img1, mu1_sq);
        multiply(img2, img2, mu2_sq);
        multiply(img1, img2, mu1_mu2);
        
        Scalar sigma1_sq = mean(mu1_sq);
        Scalar sigma2_sq = mean(mu2_sq);
        Scalar sigma12 = mean(mu1_mu2);

        // Calculate SSIM
        double ssim = ((2 * mu1 * mu2 + c1) * (2 * sigma12.get(0) + c2)) /
                     ((mu1 * mu1 + mu2 * mu2 + c1) * (sigma1_sq.get(0) + sigma2_sq.get(0) + c2));

        return Math.max(0, Math.min(1, ssim));
    }

    private Mat getCriminalEmbedding(Criminal criminal) {
        String imagePath = Paths.get(UPLOAD_DIR, criminal.getImageName()).toString();
        return criminalEmbeddings.computeIfAbsent(imagePath, path -> {
            try {
                Mat image = imread(path);
                if (image.empty()) {
                    logger.error("Error loading criminal image: {}", path);
                    return null;
                }

                // Convert to grayscale and normalize
                Mat gray = new Mat();
                cvtColor(image, gray, COLOR_BGR2GRAY);
                equalizeHist(gray, gray);

                // Detect face in criminal image
                RectVector faces = new RectVector();
                faceDetector.detectMultiScale(gray, faces);

                if (faces.empty()) {
                    logger.error("No face detected in criminal image: {}", path);
                    return null;
                }

                // Get the first (presumably only) face
                Rect face = faces.get(0);
                Mat faceImage = new Mat(image, face);
                Mat normalizedFace = new Mat();
                resize(faceImage, normalizedFace, new Size(150, 150));
                cvtColor(normalizedFace, normalizedFace, COLOR_BGR2GRAY);
                equalizeHist(normalizedFace, normalizedFace);

                faces.close();
                gray.close();
                image.close();
                faceImage.close();

                return normalizedFace;
            } catch (Exception e) {
                logger.error("Error processing criminal image {}: {}", path, e.getMessage());
                return null;
            }
        });
    }

    public void clearEmbeddingsCache() {
        if (criminalEmbeddings != null) {
            criminalEmbeddings.clear();
            logger.info("Criminal embeddings cache cleared");
        }
    }

    private void createUploadDirectory() {
        try {
            Path uploadPath = Paths.get(UPLOAD_DIR);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
                
                // Copy the cascade file to resources if it doesn't exist
                Path cascadePath = uploadPath.getParent().resolve("haarcascade_frontalface_default.xml");
                if (!Files.exists(cascadePath)) {
                    Files.copy(
                        getClass().getResourceAsStream("/haarcascade_frontalface_default.xml"),
                        cascadePath,
                        StandardCopyOption.REPLACE_EXISTING
                    );
                }
                
                logger.info("Created upload directory: {}", UPLOAD_DIR);
            }
        } catch (IOException e) {
            logger.error("Could not create upload directory: {}", e.getMessage());
        }
    }
}