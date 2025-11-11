package com.criminaldetector.criminal_face_detector;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.event.EventListener;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.core.env.Environment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.boot.context.event.ApplicationFailedEvent;
import java.awt.Desktop;
import java.net.URI;
import java.util.concurrent.TimeUnit;
import java.net.ServerSocket;
import java.util.Arrays;
import org.springframework.lang.NonNull;
import org.bytedeco.opencv.opencv_core.*;
import static org.bytedeco.opencv.global.opencv_core.*;
import static org.bytedeco.opencv.global.opencv_imgproc.*;
import ai.djl.inference.Predictor;
import ai.djl.modality.cv.Image;
import ai.djl.modality.cv.ImageFactory;
import ai.djl.modality.cv.output.DetectedObjects;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.training.util.ProgressBar;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDManager;
import ai.djl.modality.cv.transform.Resize;
import ai.djl.modality.cv.transform.ToTensor;
import ai.djl.modality.cv.transform.Normalize;
import ai.djl.modality.cv.translator.SingleShotDetectionTranslator;
import javax.sound.sampled.*;

@SpringBootApplication
@ComponentScan(basePackages = "com.criminaldetector")
@EntityScan("com.criminaldetector.model")
@EnableJpaRepositories("com.criminaldetector.repository")
public class CriminalFaceDetectorApplication {

	@Autowired
	private Environment environment;

	private static final int[] PORTS = { 8080, 8081, 8082, 8083, 8084, 8085 };
	private static final int MIN_FACE_SIZE = 40; // Increased minimum face size for better quality
	private static final float DETECTION_THRESHOLD = 0.9f; // Increased confidence threshold
	private static final double MIN_SHARPNESS = 50; // Minimum sharpness threshold
	private static final double MIN_FACE_RATIO = 0.5; // Minimum ratio of face width to height
	private ZooModel<Image, DetectedObjects> model;

	public static void main(String[] args) {
		try {
			// Find an available port
			int port = findAvailablePort();
			System.out.println("\n------------------------------------");
			System.out.println("Starting application on port: " + port);
			System.out.println("------------------------------------\n");

			// Set the server port programmatically
			System.setProperty("server.port", String.valueOf(port));

			// Create the application context
			SpringApplication app = new SpringApplication(CriminalFaceDetectorApplication.class);

			// Add error listener
			app.addListeners(new ApplicationListener<ApplicationFailedEvent>() {
				@Override
				public void onApplicationEvent(@NonNull ApplicationFailedEvent event) {
					System.err.println("\n------------------------------------");
					System.err.println("Application failed to start!");
					System.err.println("Please ensure:");
					System.err.println("1. No other application is using the ports " + Arrays.toString(PORTS));
					System.err.println("2. You have proper permissions to bind to ports");
					System.err.println("3. Your MySQL database is running and accessible");
					System.err.println("4. All required environment variables are set");
					System.err.println("Error: " + event.getException().getMessage());
					System.err.println("------------------------------------\n");
				}
			});

			// Run the application
			app.run(args);

		} catch (Exception e) {
			System.err.println("\n------------------------------------");
			System.err.println("Failed to start application: " + e.getMessage());
			System.err.println("------------------------------------\n");
			System.exit(1);
		}
	}

	private static int findAvailablePort() {
		System.out.println("Checking for available ports...");
		for (int port : PORTS) {
			try (ServerSocket serverSocket = new ServerSocket(port)) {
				serverSocket.close();
				System.out.println("Port " + port + " is available");
				return port;
			} catch (Exception e) {
				System.out.println("Port " + port + " is in use: " + e.getMessage());
			}
		}
		throw new RuntimeException("No available ports found between " + PORTS[0] + " and " + PORTS[PORTS.length - 1]);
	}

	@EventListener(ApplicationReadyEvent.class)
	public void openBrowserAfterStartup() {
		// Get the actual port that the server is running on
		String port = environment.getProperty("local.server.port");
		String url = "http://localhost:" + port + "/detect";

		// Run browser opening in a separate thread
		new Thread(() -> {
			try {
				// Increase delay to ensure server is fully ready
				TimeUnit.SECONDS.sleep(3);

				boolean browserLaunched = false;

				// Try using Desktop API first (more reliable)
				if (Desktop.isDesktopSupported()) {
					try {
						Desktop desktop = Desktop.getDesktop();
						if (desktop.isSupported(Desktop.Action.BROWSE)) {
							desktop.browse(new URI(url));
							browserLaunched = true;
						}
					} catch (Exception e) {
						// Desktop API failed, will try Runtime next
					}
				}

				// If Desktop API failed, try Runtime
				if (!browserLaunched) {
					Runtime rt = Runtime.getRuntime();
					String os = System.getProperty("os.name").toLowerCase();

					try {
						if (os.contains("win")) {
							rt.exec(new String[] { "cmd", "/c", "start", url });
						} else if (os.contains("mac")) {
							rt.exec(new String[] { "open", url });
						} else if (os.contains("nix") || os.contains("nux")) {
							// Try common Linux browsers
							String[] browsers = { "xdg-open", "google-chrome", "firefox", "mozilla", "opera" };
							for (String browser : browsers) {
								try {
									rt.exec(new String[] { browser, url });
									browserLaunched = true;
									break;
								} catch (Exception e) {
									continue;
								}
							}
						}
					} catch (Exception e) {
						// Both approaches failed
						browserLaunched = false;
					}
				}

				// Print the URL regardless of whether browser was launched
				System.out.println("\n------------------------------------");
				if (!browserLaunched) {
					System.out.println("Could not automatically open browser.");
					System.out.println("Please open your browser and visit:");
				}
				System.out.println("Application running at: " + url);
				System.out.println("------------------------------------\n");

			} catch (Exception e) {
				System.err.println("Failed to open browser: " + e.getMessage());
				System.out.println("\n------------------------------------");
				System.out.println("Please open your browser and visit:");
				System.out.println(url);
				System.out.println("------------------------------------\n");
			}
		}).start();
	}

	@PostConstruct
	public void initModel() throws Exception {
		Criteria<Image, DetectedObjects> criteria = Criteria.builder()
				.setTypes(Image.class, DetectedObjects.class)
				.optEngine("PyTorch")
				.optModelUrls("https://resources.djl.ai/test-models/pytorch/retinaface.zip") // Face-only model
				.optTranslator(SingleShotDetectionTranslator.builder()
						.addTransform(new Resize(640, 640)) // Resize to standard face input
						.addTransform(new ToTensor())
						.addTransform(new Normalize(new float[] { 0.485f, 0.456f, 0.406f },
								new float[] { 0.229f, 0.224f, 0.225f })) // Standard normalization
						.optThreshold(0.9f) // Higher threshold to remove false positives
						.build())
				.optProgress(new ProgressBar())
				.build();

		model = criteria.loadModel();

	}

	private void playAlertSound() {
		try {
			// Generate a beep tone
			Tone.sound(1000, 100); // 1000 Hz for 100ms
		} catch (Exception e) {
			System.err.println("Failed to play alert sound: " + e.getMessage());
			e.printStackTrace();
		}
	}

	// Add this utility class inside CriminalFaceDetectorApplication
	private static class Tone {
		public static void sound(int hz, int msecs) {
			try {
				AudioFormat af = new AudioFormat(44100f, 16, 1, true, false);
				SourceDataLine sdl = AudioSystem.getSourceDataLine(af);
				sdl.open(af);
				sdl.start();

				byte[] buf = new byte[msecs * 44100 / 1000];
				for (int i = 0; i < buf.length; i++) {
					double angle = i / (44100.0 / hz) * 2.0 * Math.PI;
					buf[i] = (byte) (Math.sin(angle) * 127.0);
				}

				sdl.write(buf, 0, buf.length);
				sdl.drain();
				sdl.stop();
				sdl.close();
			} catch (Exception e) {
				System.err.println("Error generating tone: " + e.getMessage());
				e.printStackTrace();
			}
		}
	}

	public Mat detectFaces(Mat image) {
		try (Predictor<Image, DetectedObjects> predictor = model.newPredictor()) {
			// Convert OpenCV Mat to DJL Image
			try (NDManager manager = NDManager.newBaseManager()) {
				byte[] data = new byte[image.rows() * image.cols() * image.channels()];
				image.data().get(data);
				NDArray array = manager.create(data).reshape(image.rows(), image.cols(), image.channels());
				Image djlImage = ImageFactory.getInstance().fromNDArray(array);

				// Detect faces
				DetectedObjects detections = predictor.predict(djlImage);

				// Debug: Print number of detections
				System.out.println("Number of detections: " + detections.getNumberOfObjects());

				// Draw results
				RectVector faces = new RectVector();
				boolean hasValidFace = false;

				for (DetectedObjects.DetectedObject detection : detections.<DetectedObjects.DetectedObject>items()) {
					if (detection.getProbability() > DETECTION_THRESHOLD) {
						String detectedClass = detection.getClassName(); // Ensure it detects ONLY faces

						// Debug: Print detected class and probability
						System.out.println(
								"Detected class: " + detectedClass + ", Probability: " + detection.getProbability());

						if (!detectedClass.equalsIgnoreCase("face")) {
							continue; // Ignore non-face objects
						}

						ai.djl.modality.cv.output.BoundingBox box = detection.getBoundingBox();
						int x = (int) (box.getBounds().getX() * image.cols());
						int y = (int) (box.getBounds().getY() * image.rows());
						int width = (int) (box.getBounds().getWidth() * image.cols());
						int height = (int) (box.getBounds().getHeight() * image.rows());

						Rect face = new Rect(x, y, width, height);
						if (isGoodQualityFace(image, face)) {
							faces.push_back(face);
							hasValidFace = true;
						}
					}
				}

				// Play alert only when valid faces are detected
				if (hasValidFace) {
					System.out.println("Valid face detected - Playing alert sound");
					playAlertSound();
				}

				return drawFaces(image, faces);
			}
		} catch (Exception e) {
			System.err.println("Face detection failed: " + e.getMessage());
			e.printStackTrace();
			throw new RuntimeException("Face detection failed", e);
		}
	}

	private boolean isGoodQualityFace(Mat image, Rect face) {
		Mat faceRegion = new Mat(image, face);

		// Size check
		if (face.width() < MIN_FACE_SIZE || face.height() < MIN_FACE_SIZE) {
			return false;
		}

		// Face aspect ratio check
		double aspectRatio = (double) face.width() / face.height();
		if (aspectRatio < MIN_FACE_RATIO || aspectRatio > 1.0 / MIN_FACE_RATIO) {
			return false;
		}

		// Brightness and contrast check
		Scalar mean = mean(faceRegion);
		Mat stddev = new Mat();
		meanStdDev(faceRegion, new Mat(), stddev);
		double brightness = mean.get(0); // Fixed: Using get() to access Scalar value
		double contrast = stddev.createIndexer().getDouble(0);

		if (brightness < 30 || brightness > 250 || contrast < 10) {
			return false;
		}

		// Enhanced blur detection using Laplacian variance
		// Convert image to grayscale
		Mat gray = new Mat();
		cvtColor(image, gray, COLOR_BGR2GRAY);
		// Apply Gaussian blur to reduce noise
		GaussianBlur(gray, gray, new Size(5, 5), 0);
		// Apply Canny edge detection to emphasize face contours
		Mat edges = new Mat();
		Canny(gray, edges, 100, 200);
		Mat laplacian = new Mat();
		Laplacian(gray, laplacian, CV_64F);
		Mat stddev_lap = new Mat();
		meanStdDev(laplacian, new Mat(), stddev_lap);
		double variance = Math.pow(stddev_lap.createIndexer().getDouble(), 2);

		return variance > MIN_SHARPNESS;
	}

	private Mat drawFaces(Mat image, RectVector faces) {
		for (Rect face : faces.get()) {
			// Draw rectangle with thickness based on face size
			int thickness = Math.max(2, face.width() / 100);
			rectangle(image, face, new Scalar(0, 255, 0, 255), thickness, LINE_AA, 0);
		}
		return image;
	}

	@PreDestroy
	public void cleanup() {
		if (model != null) {
			model.close();
		}
	}

}
