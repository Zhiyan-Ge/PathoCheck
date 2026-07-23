package com.pathocheck;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

/**
 * PathoCheck 的业务总流程。
 *
 * 可以把该类理解成以前 Python 项目里的 main.py：
 * Controller 只负责把按钮请求送进来；
 * Service 决定依次调用 DatabaseService 和 ModelPredictor 的哪些方法。
 */
@Service
public class PathoCheckService {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(PathoCheckService.class);

    private static final DateTimeFormatter FILE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private static final Set<String> SUPPORTED_IMAGE_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "tif", "tiff", "bmp"
    );

    private static final Set<String> VALID_FOLDER_LABELS = Set.of(
            "ADI", "LYM", "MUC", "MUS", "NORM", "STR", "TUM"
    );

    private static final Set<String> INVALID_FOLDER_LABELS = Set.of(
            "BACK", "DEB"
    );

    private final ModelPredictor modelPredictor;
    private final DatabaseService databaseService;

    private final Path storageRoot;
    private final Path datasetsDirectory;
    private final Path modelsDirectory;
    private final Path imagesDirectory;
    private final Path reportsDirectory;
    private final Path logsDirectory;

    public PathoCheckService(
            ModelPredictor modelPredictor,
            DatabaseService databaseService,
            @Value("${pathocheck.storage.root:storage}") String storageRoot) {

        this.modelPredictor = modelPredictor;
        this.databaseService = databaseService;
        this.storageRoot = Path.of(storageRoot).toAbsolutePath().normalize();
        this.datasetsDirectory = this.storageRoot.resolve("datasets");
        this.modelsDirectory = this.storageRoot.resolve("models");
        this.imagesDirectory = this.storageRoot.resolve("images");
        this.reportsDirectory = this.storageRoot.resolve("reports");
        this.logsDirectory = this.storageRoot.resolve("logs");
        initializeStorageDirectories();
    }

    // ==================== Dataset ====================

    /**
     * 从浏览器上传一个完整数据集文件夹。
     *
     * 前端应同时发送：
     * - files：文件列表；
     * - relativePaths：每个文件的 webkitRelativePath。
     *
     * 例如：CRC-VAL-HE-7K/ADI/ADI-TCGA-1.png
     */
    public Map<String, Object> loadDataset(
            String datasetName,
            List<MultipartFile> files,
            List<String> relativePaths) throws IOException {

        String safeDatasetName = requireSafeName(datasetName, "Dataset name");
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("Dataset file list cannot be empty.");
        }

        Path destination = createUniqueDirectory(datasetsDirectory, safeDatasetName);
        int imageCount = 0;
        int validCount = 0;
        int invalidCount = 0;

        try {
            for (int index = 0; index < files.size(); index++) {
                MultipartFile file = files.get(index);
                if (file == null || file.isEmpty()) {
                    continue;
                }

                String submittedPath = pickSubmittedPath(
                        file,
                        relativePaths,
                        index);
                Path relativePath = normalizeDatasetRelativePath(
                        submittedPath,
                        safeDatasetName);

                if (!isSupportedImage(relativePath)) {
                    continue;
                }

                Path target = destination.resolve(relativePath).normalize();
                ensureInside(target, destination);
                Files.createDirectories(target.getParent());
                try (InputStream inputStream = file.getInputStream()) {
                    Files.copy(
                            inputStream,
                            target,
                            StandardCopyOption.REPLACE_EXISTING);
                }

                imageCount++;
                String classLabel = detectDatasetClass(relativePath);
                if ("VALID_TISSUE".equals(classLabel)) {
                    validCount++;
                } else if ("INVALID_REGION".equals(classLabel)) {
                    invalidCount++;
                }
            }

            if (imageCount == 0) {
                throw new IllegalArgumentException(
                        "No supported image was uploaded. Supported formats: "
                                + SUPPORTED_IMAGE_EXTENSIONS);
            }
            if (validCount == 0 || invalidCount == 0) {
                throw new IllegalArgumentException(
                        "Dataset must contain both valid folders "
                                + VALID_FOLDER_LABELS
                                + " and invalid folders "
                                + INVALID_FOLDER_LABELS
                                + ". Ensure the frontend sends relativePaths.");
            }

            Map<String, Object> record = databaseService.saveDataset(
                    safeDatasetName,
                    destination.toString(),
                    imageCount,
                    validCount,
                    invalidCount);

            record.put("result", "SUCCESS");
            LOGGER.info(
                    "LOAD_DATASET dataset={} images={} valid={} invalid={} result=SUCCESS",
                    safeDatasetName,
                    imageCount,
                    validCount,
                    invalidCount);
            return record;
        } catch (IOException | RuntimeException exception) {
            deleteRecursively(destination);
            LOGGER.error(
                    "LOAD_DATASET dataset={} result=FAILED message={}",
                    safeDatasetName,
                    exception.getMessage());
            throw exception;
        }
    }

    public List<Map<String, Object>> listDatasets() {
        return databaseService.listDatasets();
    }

    public void deleteDataset(Long datasetId) throws IOException {
        Map<String, Object> dataset = databaseService.findDatasetById(datasetId);
        Path datasetPath = Path.of(String.valueOf(dataset.get("datasetPath")))
                .toAbsolutePath()
                .normalize();
        ensureInside(datasetPath, datasetsDirectory);
        deleteRecursively(datasetPath);
        databaseService.deleteDataset(datasetId);
        LOGGER.info(
                "DELETE_DATASET datasetId={} dataset={} result=SUCCESS",
                datasetId,
                dataset.get("datasetName"));
    }

    // ==================== Model ====================

    public Map<String, Object> loadModel(
            String modelName,
            MultipartFile modelFile) throws IOException {

        String safeModelName = requireSafeName(modelName, "Model name");
        if (modelFile == null || modelFile.isEmpty()) {
            throw new IllegalArgumentException("Model file cannot be empty.");
        }

        String extension = extensionOf(modelFile.getOriginalFilename());
        if (!"model".equals(extension)) {
            throw new IllegalArgumentException(
                    "Only PathoCheck-compatible .model files are supported.");
        }

        Path destination = createUniqueFilePath(
                modelsDirectory,
                safeModelName,
                ".model");
        try (InputStream inputStream = modelFile.getInputStream()) {
            Files.copy(inputStream, destination, StandardCopyOption.REPLACE_EXISTING);
        }

        Map<String, Object> record = databaseService.saveModel(
                safeModelName,
                destination.toString(),
                Map.of());
        record.put("result", "SUCCESS");
        LOGGER.info(
                "LOAD_MODEL model={} result=SUCCESS",
                safeModelName);
        return record;
    }

    public List<Map<String, Object>> listModels() {
        return databaseService.listModels();
    }

    public Map<String, Object> trainModel(
            Long datasetId,
            String modelName) throws Exception {

        String safeModelName = requireSafeName(modelName, "Model name");
        Map<String, Object> dataset = databaseService.findDatasetById(datasetId);
        String datasetPath = String.valueOf(dataset.get("datasetPath"));

        Path modelPath = createUniqueFilePath(
                modelsDirectory,
                safeModelName,
                ".model");

        try {
            Map<String, Object> trainingResult = modelPredictor.trainModel(
                    datasetPath,
                    modelPath.toString());
            Map<String, Object> modelRecord = databaseService.saveModel(
                    safeModelName,
                    modelPath.toString(),
                    trainingResult);

            Map<String, Object> response = new LinkedHashMap<>(modelRecord);
            response.putAll(trainingResult);
            response.put("result", "SUCCESS");

            LOGGER.info(
                    "TRAIN_MODEL datasetId={} model={} accuracy={} result=SUCCESS",
                    datasetId,
                    safeModelName,
                    trainingResult.get("accuracy"));
            return response;
        } catch (Exception exception) {
            Files.deleteIfExists(modelPath);
            LOGGER.error(
                    "TRAIN_MODEL datasetId={} model={} result=FAILED message={}",
                    datasetId,
                    safeModelName,
                    exception.getMessage());
            throw exception;
        }
    }

    public void deleteModel(Long modelId) throws IOException {
        Map<String, Object> model = databaseService.findModelById(modelId);
        Path modelPath = Path.of(String.valueOf(model.get("modelPath")))
                .toAbsolutePath()
                .normalize();
        ensureInside(modelPath, modelsDirectory);
        databaseService.deleteModel(modelId);
        Files.deleteIfExists(modelPath);
        LOGGER.info(
                "DELETE_MODEL modelId={} model={} result=SUCCESS",
                modelId,
                model.get("modelName"));
    }

    // ==================== Image ====================

    public Map<String, Object> selectImage(MultipartFile imageFile)
            throws IOException {

        if (imageFile == null || imageFile.isEmpty()) {
            throw new IllegalArgumentException("Image file cannot be empty.");
        }

        String originalName = imageFile.getOriginalFilename();
        if (originalName == null || originalName.isBlank()) {
            throw new IllegalArgumentException("Image file name cannot be empty.");
        }

        String extension = extensionOf(originalName);
        if (!SUPPORTED_IMAGE_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException(
                    "Unsupported image format: " + extension);
        }

        String baseName = removeExtension(Path.of(originalName)
                .getFileName()
                .toString());
        Path destination = createUniqueFilePath(
                imagesDirectory,
                requireSafeName(baseName, "Image name"),
                "." + extension);

        try (InputStream inputStream = imageFile.getInputStream()) {
            Files.copy(inputStream, destination, StandardCopyOption.REPLACE_EXISTING);
        }

        try {
            Map<String, Object> imageInfo = modelPredictor.inspectImage(
                    destination.toString());
            Map<String, Object> record = databaseService.saveImage(
                    originalName,
                    destination.toString(),
                    imageInfo);
            record.put("result", "SUCCESS");
            LOGGER.info(
                    "SELECT_IMAGE image={} result=SUCCESS",
                    originalName);
            return record;
        } catch (IOException | RuntimeException exception) {
            Files.deleteIfExists(destination);
            LOGGER.error(
                    "SELECT_IMAGE image={} result=FAILED message={}",
                    originalName,
                    exception.getMessage());
            throw exception;
        }
    }

    /**
 * 将已选择的图像统一转换成 PNG，供浏览器预览。
 * 浏览器通常不能直接显示 TIFF，因此不能直接返回原文件。
 */
        public byte[] getImagePreview(Long imageId) throws IOException {
        Map<String, Object> imageRecord =
                databaseService.findImageById(imageId);

        Path imagePath = Path.of(
                String.valueOf(imageRecord.get("imagePath")));

        BufferedImage image = ImageIO.read(imagePath.toFile());

        if (image == null) {
                throw new IOException(
                        "Cannot read image for preview: " + imagePath);
        }

        try (ByteArrayOutputStream outputStream =
                        new ByteArrayOutputStream()) {

                boolean success = ImageIO.write(
                        image,
                        "png",
                        outputStream);

                if (!success) {
                throw new IOException(
                        "Cannot convert image to PNG preview: "
                                + imagePath);
                }

                return outputStream.toByteArray();
        }
        }

    // ==================== Assessment ====================

    public AssessmentReport assess(Long modelId, Long imageId) throws Exception {
        Map<String, Object> model = databaseService.findModelById(modelId);
        Map<String, Object> image = databaseService.findImageById(imageId);

        AssessmentReport report = modelPredictor.predict(
                String.valueOf(image.get("imagePath")),
                String.valueOf(model.get("modelPath")));

        report.setModelId(modelId);
        report.setModelName(String.valueOf(model.get("modelName")));
        report.setImageId(imageId);
        report.setImageName(String.valueOf(image.get("imageName")));

        databaseService.saveReport(report);
        LOGGER.info(
                "START_ASSESSMENT reportId={} modelId={} imageId={} status={}",
                report.getReportId(),
                modelId,
                imageId,
                report.getFinalStatus());
        return report;
    }

    public Path exportReport(Long reportId) throws IOException {
        AssessmentReport report = databaseService.findReportById(reportId);
        Path pdfPath = reportsDirectory.resolve("report_" + reportId + ".pdf")
                .toAbsolutePath()
                .normalize();
        ensureInside(pdfPath, reportsDirectory);
        generatePdf(report, pdfPath);
        databaseService.updatePdfPath(reportId, pdfPath.toString());
        LOGGER.info(
                "EXPORT_PDF reportId={} result=SUCCESS",
                reportId);
        return pdfPath;
    }

    // ==================== Help ====================

    public String readLog() throws IOException {
        Path logPath = logsDirectory.resolve("pathocheck.log")
                .toAbsolutePath()
                .normalize();
        if (!Files.exists(logPath)) {
            return "";
        }
        return Files.readString(logPath);
    }

    // ==================== PDF ====================

    private void generatePdf(
            AssessmentReport report,
            Path outputPath) throws IOException {

        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            PDType1Font titleFont = new PDType1Font(
                    Standard14Fonts.FontName.HELVETICA_BOLD);
            PDType1Font bodyFont = new PDType1Font(
                    Standard14Fonts.FontName.HELVETICA);

            try (PDPageContentStream content =
                         new PDPageContentStream(document, page)) {

                content.beginText();
                content.newLineAtOffset(50, 790);

                writePdfLine(content, titleFont, 18,
                        "PathoCheck Assessment Report", 24);
                writePdfLine(content, bodyFont, 11,
                        "Report ID: " + report.getReportId(), 16);
                writePdfLine(content, bodyFont, 11,
                        "Image: " + safePdfText(report.getImageName()), 16);
                writePdfLine(content, bodyFont, 11,
                        "Model: " + safePdfText(report.getModelName()), 16);
                writePdfLine(content, bodyFont, 11,
                        "Assessment Time: " + report.getAssessmentTime(), 22);

                writePdfLine(content, titleFont, 13,
                        "Overall Result", 18);
                writePdfLine(content, bodyFont, 11,
                        "Final Status: " + report.getFinalStatus(), 16);
                writePdfLine(content, bodyFont, 11,
                        "Quality Score: " + report.getQualityScore(), 22);

                writePdfLine(content, titleFont, 13,
                        "Model Prediction", 18);
                writePdfLine(content, bodyFont, 11,
                        "Predicted Label: " + report.getPredictedLabel(), 16);
                writePdfLine(content, bodyFont, 11,
                        "Valid Tissue Probability: "
                                + report.getValidProbability(), 16);
                writePdfLine(content, bodyFont, 11,
                        "Invalid Region Probability: "
                                + report.getInvalidProbability(), 22);

                writePdfLine(content, titleFont, 13,
                        "Image Quality Metrics", 18);
                writePdfLine(content, bodyFont, 10,
                        "Mean Brightness: " + report.getMeanBrightness(), 14);
                writePdfLine(content, bodyFont, 10,
                        "Brightness Std: " + report.getBrightnessStd(), 14);
                writePdfLine(content, bodyFont, 10,
                        "Mean Saturation: " + report.getMeanSaturation(), 14);
                writePdfLine(content, bodyFont, 10,
                        "White Pixel Ratio: " + report.getWhitePixelRatio(), 14);
                writePdfLine(content, bodyFont, 10,
                        "Dark Pixel Ratio: " + report.getDarkPixelRatio(), 14);
                writePdfLine(content, bodyFont, 10,
                        "Tissue Ratio: " + report.getTissueRatio(), 14);
                writePdfLine(content, bodyFont, 10,
                        "Entropy: " + report.getEntropy(), 14);
                writePdfLine(content, bodyFont, 10,
                        "Sharpness: " + report.getSharpness(), 14);
                writePdfLine(content, bodyFont, 10,
                        "Edge Ratio: " + report.getEdgeRatio(), 22);

                writePdfLine(content, titleFont, 13,
                        "Detected Problems", 18);
                if (report.getWarningMessages() == null
                        || report.getWarningMessages().isEmpty()) {
                    writePdfLine(content, bodyFont, 10,
                            "No significant quality problem detected.", 18);
                } else {
                    for (String warning : report.getWarningMessages()) {
                        writePdfLine(content, bodyFont, 10,
                                "- " + safePdfText(warning), 14);
                    }
                }

                writePdfLine(content, titleFont, 13,
                        "Suggestion", 18);
                writePdfLine(content, bodyFont, 10,
                        safePdfText(report.getSuggestion()), 14);

                content.endText();
            }

            document.save(outputPath.toFile());
        }
    }

    private void writePdfLine(
            PDPageContentStream content,
            PDType1Font font,
            float fontSize,
            String text,
            float nextLineOffset) throws IOException {

        content.setFont(font, fontSize);
        content.showText(safePdfText(text));
        content.newLineAtOffset(0, -nextLineOffset);
    }

    /** Standard 14 fonts only support a limited character set. */
    private String safePdfText(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (char character : value.toCharArray()) {
            if (character >= 32 && character <= 126) {
                builder.append(character);
            } else {
                builder.append('?');
            }
        }
        return builder.toString();
    }

    // ==================== Storage helpers ====================

    private void initializeStorageDirectories() {
        try {
            Files.createDirectories(datasetsDirectory);
            Files.createDirectories(modelsDirectory);
            Files.createDirectories(imagesDirectory);
            Files.createDirectories(reportsDirectory);
            Files.createDirectories(logsDirectory);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Failed to initialize storage directory: " + storageRoot,
                    exception);
        }
    }

    private Path createUniqueDirectory(Path parent, String baseName)
            throws IOException {

        Path candidate = parent.resolve(baseName);
        int suffix = 1;
        while (Files.exists(candidate)) {
            candidate = parent.resolve(baseName + "_" + suffix);
            suffix++;
        }
        Files.createDirectories(candidate);
        return candidate.toAbsolutePath().normalize();
    }

    private Path createUniqueFilePath(
            Path parent,
            String baseName,
            String extension) throws IOException {

        Files.createDirectories(parent);
        String safeExtension = extension.startsWith(".")
                ? extension
                : "." + extension;
        Path candidate = parent.resolve(baseName + safeExtension);
        int suffix = 1;
        while (Files.exists(candidate)) {
            candidate = parent.resolve(baseName + "_" + suffix + safeExtension);
            suffix++;
        }
        return candidate.toAbsolutePath().normalize();
    }

    private String pickSubmittedPath(
            MultipartFile file,
            List<String> relativePaths,
            int index) {

        if (relativePaths != null
                && index < relativePaths.size()
                && relativePaths.get(index) != null
                && !relativePaths.get(index).isBlank()) {
            return relativePaths.get(index);
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new IllegalArgumentException(
                    "Uploaded dataset file has no file name.");
        }
        return originalFilename;
    }

    private Path normalizeDatasetRelativePath(
            String submittedPath,
            String datasetName) {

        String cleaned = submittedPath.replace('\\', '/');
        while (cleaned.startsWith("/")) {
            cleaned = cleaned.substring(1);
        }

        Path path = Path.of(cleaned).normalize();
        if (path.isAbsolute()
                || path.getNameCount() == 0
                || path.startsWith("..")) {
            throw new IllegalArgumentException(
                    "Illegal dataset relative path: " + submittedPath);
        }

        if (path.getNameCount() > 1
                && path.getName(0).toString().equalsIgnoreCase(datasetName)) {
            path = path.subpath(1, path.getNameCount());
        }

        if (path.getNameCount() == 0 || path.startsWith("..")) {
            throw new IllegalArgumentException(
                    "Illegal dataset relative path: " + submittedPath);
        }
        return path;
    }

    private String detectDatasetClass(Path relativePath) {
        Set<String> parts = new HashSet<>();
        for (Path part : relativePath) {
            parts.add(part.toString().toUpperCase(Locale.ROOT));
        }

        for (String label : INVALID_FOLDER_LABELS) {
            if (parts.contains(label)) {
                return "INVALID_REGION";
            }
        }
        for (String label : VALID_FOLDER_LABELS) {
            if (parts.contains(label)) {
                return "VALID_TISSUE";
            }
        }
        return null;
    }

    private void ensureInside(Path child, Path parent) {
        Path normalizedChild = child.toAbsolutePath().normalize();
        Path normalizedParent = parent.toAbsolutePath().normalize();
        if (!normalizedChild.startsWith(normalizedParent)) {
            throw new IllegalArgumentException(
                    "Illegal file path outside storage directory: " + child);
        }
    }

    private void deleteRecursively(Path path) throws IOException {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(path)) {
            List<Path> paths = stream
                    .sorted(Comparator.reverseOrder())
                    .toList();
            for (Path current : paths) {
                Files.deleteIfExists(current);
            }
        }
    }

    private boolean isSupportedImage(Path path) {
        return SUPPORTED_IMAGE_EXTENSIONS.contains(
                extensionOf(path.getFileName().toString()));
    }

    private String extensionOf(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dotIndex + 1)
                .toLowerCase(Locale.ROOT);
    }

    private String removeExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex <= 0 ? fileName : fileName.substring(0, dotIndex);
    }

    private String requireSafeName(String rawName, String fieldName) {
        if (rawName == null || rawName.isBlank()) {
            throw new IllegalArgumentException(fieldName + " cannot be empty.");
        }

        String safeName = rawName.trim()
                .replaceAll("[^a-zA-Z0-9._-]", "_")
                .replaceAll("_+", "_");

        if (safeName.isBlank() || ".".equals(safeName) || "..".equals(safeName)) {
            throw new IllegalArgumentException(fieldName + " is invalid.");
        }
        return safeName;
    }
}
