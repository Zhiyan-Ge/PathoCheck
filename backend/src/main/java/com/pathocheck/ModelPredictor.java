package com.pathocheck;

import org.springframework.stereotype.Service;
import weka.classifiers.Classifier;
import weka.classifiers.Evaluation;
import weka.classifiers.trees.RandomForest;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.SerializationHelper;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Stream;

/**
 * PathoCheck 的图像处理与随机森林模块。
 *
 * 该文件包含：
 * - 图像基础信息读取；
 * - 15 个手工图像特征；
 * - Weka RandomForest 训练；
 * - 模型保存与加载；
 * - 单图预测；
 * - 质量评分和 PASS/WARNING/REJECT 判定。
 */
@Service
public class ModelPredictor {

    private static final int RANDOM_SEED = 2026;
    private static final int NUMBER_OF_TREES = 100;
    private static final double SOBEL_EDGE_THRESHOLD = 100.0;

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final List<String> FEATURE_NAMES = List.of(
            "meanRed",
            "meanGreen",
            "meanBlue",
            "stdRed",
            "stdGreen",
            "stdBlue",
            "meanBrightness",
            "brightnessStd",
            "meanSaturation",
            "whitePixelRatio",
            "darkPixelRatio",
            "tissueRatio",
            "entropy",
            "sharpness",
            "edgeRatio"
    );

    /** 类别顺序必须固定；预测时通过类别名称查索引，不直接假设 0/1。 */
    private static final List<String> CLASS_VALUES = List.of(
            "INVALID_REGION",
            "VALID_TISSUE"
    );

    private static final Set<String> VALID_FOLDER_LABELS = Set.of(
            "ADI", "LYM", "MUC", "MUS", "NORM", "STR", "TUM"
    );

    private static final Set<String> INVALID_FOLDER_LABELS = Set.of(
            "BACK", "DEB"
    );

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "tif", "tiff", "bmp"
    );

    // ==================== Public API ====================

    /**
     * 读取图像基础信息，供 Select Image 弹窗使用。
     */
    public Map<String, Object> inspectImage(String imagePath) throws IOException {
        Path path = requireReadableFile(imagePath, "Image");
        BufferedImage image = readImage(path);

        Map<String, Object> info = new LinkedHashMap<>();
        info.put("imageName", path.getFileName().toString());
        info.put("fileFormat", extensionOf(path).toUpperCase(Locale.ROOT));
        info.put("width", image.getWidth());
        info.put("height", image.getHeight());
        info.put("fileSize", Files.size(path));
        info.put("colorMode", detectColorMode(image));
        return info;
    }

    /**
     * 对外提供可读的特征 Map，方便调试。
     */
    public Map<String, Object> extractFeatures(String imagePath) throws IOException {
        Path path = requireReadableFile(imagePath, "Image");
        return computeFeatureVector(readImage(path)).toMap();
    }

    /**
     * 使用按类别文件夹组织的数据集训练随机森林。
     *
     * 输出模型中同时保存：
     * 1. Classifier；
     * 2. 训练时的 Instances 表头。
     *
     * 预测阶段必须同时读取二者，以确保特征顺序和类别顺序完全一致。
     */
    public Map<String, Object> trainModel(
            String datasetPath,
            String outputModelPath) throws Exception {

        Path datasetRoot = Path.of(datasetPath).toAbsolutePath().normalize();
        if (!Files.isDirectory(datasetRoot)) {
            throw new IllegalArgumentException(
                    "Dataset directory does not exist: " + datasetRoot);
        }

        List<Path> imagePaths;
        try (Stream<Path> stream = Files.walk(datasetRoot)) {
            imagePaths = stream
                    .filter(Files::isRegularFile)
                    .filter(this::isSupportedImage)
                    .toList();
        }

        if (imagePaths.isEmpty()) {
            throw new IllegalArgumentException(
                    "No supported image was found in dataset: " + datasetRoot);
        }

        List<LabeledFeature> validSamples = new ArrayList<>();
        List<LabeledFeature> invalidSamples = new ArrayList<>();
        int skippedImages = 0;

        for (Path imagePath : imagePaths) {
            String classLabel = detectClassLabel(datasetRoot, imagePath);
            if (classLabel == null) {
                skippedImages++;
                continue;
            }

            try {
                FeatureVector features = computeFeatureVector(readImage(imagePath));
                LabeledFeature sample = new LabeledFeature(features, classLabel);
                if ("VALID_TISSUE".equals(classLabel)) {
                    validSamples.add(sample);
                } else {
                    invalidSamples.add(sample);
                }
            } catch (IOException exception) {
                skippedImages++;
            }
        }

        if (validSamples.size() < 2 || invalidSamples.size() < 2) {
            throw new IllegalArgumentException(
                    "Training requires at least 2 readable VALID_TISSUE images "
                            + "and 2 readable INVALID_REGION images. Current counts: valid="
                            + validSamples.size() + ", invalid=" + invalidSamples.size());
        }

        Random random = new Random(RANDOM_SEED);
        Collections.shuffle(validSamples, random);
        Collections.shuffle(invalidSamples, random);

        // 简单下采样：两个类别使用相同数量，避免类别极度不平衡。
        int samplesPerClass = Math.min(validSamples.size(), invalidSamples.size());
        validSamples = new ArrayList<>(validSamples.subList(0, samplesPerClass));
        invalidSamples = new ArrayList<>(invalidSamples.subList(0, samplesPerClass));

        int trainPerClass = Math.max(1, (int) Math.floor(samplesPerClass * 0.8));
        if (trainPerClass >= samplesPerClass) {
            trainPerClass = samplesPerClass - 1;
        }

        List<LabeledFeature> trainingSamples = new ArrayList<>();
        List<LabeledFeature> testingSamples = new ArrayList<>();

        trainingSamples.addAll(validSamples.subList(0, trainPerClass));
        trainingSamples.addAll(invalidSamples.subList(0, trainPerClass));
        testingSamples.addAll(validSamples.subList(trainPerClass, samplesPerClass));
        testingSamples.addAll(invalidSamples.subList(trainPerClass, samplesPerClass));

        Collections.shuffle(trainingSamples, new Random(RANDOM_SEED));
        Collections.shuffle(testingSamples, new Random(RANDOM_SEED + 1));

        Instances trainingData = buildInstances("PathoCheckTraining", trainingSamples);
        Instances testingData = buildInstances("PathoCheckTesting", testingSamples);

        RandomForest classifier = new RandomForest();
        classifier.setSeed(RANDOM_SEED);
        classifier.setNumIterations(NUMBER_OF_TREES);
        classifier.buildClassifier(trainingData);

        Evaluation evaluation = new Evaluation(trainingData);
        evaluation.evaluateModel(classifier, testingData);

        Instances modelHeader = new Instances(trainingData, 0);
        modelHeader.setClassIndex(trainingData.classIndex());

        Path modelPath = Path.of(outputModelPath).toAbsolutePath().normalize();
        if (modelPath.getParent() != null) {
            Files.createDirectories(modelPath.getParent());
        }

        SerializationHelper.writeAll(
                modelPath.toString(),
                new Object[]{classifier, modelHeader});

        int validClassIndex = testingData.classAttribute()
                .indexOfValue("VALID_TISSUE");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("modelPath", modelPath.toString());
        result.put("algorithm", "RandomForest");
        result.put("numberOfTrees", NUMBER_OF_TREES);
        result.put("randomSeed", RANDOM_SEED);
        result.put("originalValidCount", validSamples.size());
        result.put("originalInvalidCount", invalidSamples.size());
        result.put("balancedSamplesPerClass", samplesPerClass);
        result.put("trainSampleCount", trainingData.numInstances());
        result.put("testSampleCount", testingData.numInstances());
        result.put("accuracy", safeMetric(evaluation.pctCorrect() / 100.0));
        result.put("precision", safeMetric(evaluation.precision(validClassIndex)));
        result.put("recall", safeMetric(evaluation.recall(validClassIndex)));
        result.put("f1Score", safeMetric(evaluation.fMeasure(validClassIndex)));
        result.put("confusionMatrix", Arrays.deepToString(evaluation.confusionMatrix()));
        result.put("skippedImageCount", skippedImages);
        result.put("createdTime", LocalDateTime.now().format(TIME_FORMATTER));
        return result;
    }

    /**
     * 对单张图像进行随机森林预测并生成完整报告。
     */
    public AssessmentReport predict(
            String imagePath,
            String modelPath) throws Exception {

        Path imageFile = requireReadableFile(imagePath, "Image");
        Path modelFile = requireReadableFile(modelPath, "Model");

        FeatureVector features = computeFeatureVector(readImage(imageFile));

        Object[] storedObjects = SerializationHelper.readAll(modelFile.toString());
        if (storedObjects.length < 2
                || !(storedObjects[0] instanceof Classifier)
                || !(storedObjects[1] instanceof Instances)) {
            throw new IllegalArgumentException(
                    "The model is not a PathoCheck-compatible model. "
                            + "A compatible .model file must contain both classifier and feature header.");
        }

        Classifier classifier = (Classifier) storedObjects[0];
        Instances header = new Instances((Instances) storedObjects[1], 0);
        if (header.classIndex() < 0) {
            header.setClassIndex(header.numAttributes() - 1);
        }

        validateModelHeader(header);

        Instance instance = new DenseInstance(header.numAttributes());
        instance.setDataset(header);
        double[] featureArray = features.toArray();
        for (int index = 0; index < featureArray.length; index++) {
            instance.setValue(index, featureArray[index]);
        }
        instance.setMissing(header.classIndex());

        double[] distribution = classifier.distributionForInstance(instance);
        int validIndex = header.classAttribute().indexOfValue("VALID_TISSUE");
        int invalidIndex = header.classAttribute().indexOfValue("INVALID_REGION");

        if (validIndex < 0 || invalidIndex < 0
                || validIndex >= distribution.length
                || invalidIndex >= distribution.length) {
            throw new IllegalArgumentException(
                    "The model class labels are incompatible with PathoCheck.");
        }

        double validProbability = clamp(distribution[validIndex], 0.0, 1.0);
        double invalidProbability = clamp(distribution[invalidIndex], 0.0, 1.0);
        String predictedLabel = validProbability >= invalidProbability
                ? "VALID_TISSUE"
                : "INVALID_REGION";

        List<String> warnings = generateWarnings(features, validProbability);
        double qualityScore = calculateQualityScore(features, validProbability);
        String finalStatus = determineFinalStatus(features, validProbability);
        String suggestion = suggestionFor(finalStatus);

        AssessmentReport report = new AssessmentReport();
        report.setImageName(imageFile.getFileName().toString());
        report.setAssessmentTime(LocalDateTime.now().format(TIME_FORMATTER));
        report.setPredictedLabel(predictedLabel);
        report.setValidProbability(round(validProbability, 4));
        report.setInvalidProbability(round(invalidProbability, 4));

        report.setMeanRed(round(features.meanRed(), 4));
        report.setMeanGreen(round(features.meanGreen(), 4));
        report.setMeanBlue(round(features.meanBlue(), 4));
        report.setMeanBrightness(round(features.meanBrightness(), 4));
        report.setBrightnessStd(round(features.brightnessStd(), 4));
        report.setMeanSaturation(round(features.meanSaturation(), 4));
        report.setWhitePixelRatio(round(features.whitePixelRatio(), 4));
        report.setDarkPixelRatio(round(features.darkPixelRatio(), 4));
        report.setTissueRatio(round(features.tissueRatio(), 4));
        report.setEntropy(round(features.entropy(), 4));
        report.setSharpness(round(features.sharpness(), 4));
        report.setEdgeRatio(round(features.edgeRatio(), 4));

        report.setQualityScore(round(qualityScore, 2));
        report.setFinalStatus(finalStatus);
        report.setWarningMessages(warnings);
        report.setSuggestion(suggestion);
        return report;
    }

    // ==================== Feature extraction ====================

    private FeatureVector computeFeatureVector(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Image width and height must be positive.");
        }

        long pixelCount = (long) width * height;
        int[][] gray = new int[height][width];
        long[] grayHistogram = new long[256];

        double sumRed = 0.0;
        double sumGreen = 0.0;
        double sumBlue = 0.0;
        double sumRedSquare = 0.0;
        double sumGreenSquare = 0.0;
        double sumBlueSquare = 0.0;
        double sumBrightness = 0.0;
        double sumBrightnessSquare = 0.0;
        double sumSaturation = 0.0;
        long whitePixelCount = 0;
        long darkPixelCount = 0;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = image.getRGB(x, y);
                int red = (rgb >>> 16) & 0xFF;
                int green = (rgb >>> 8) & 0xFF;
                int blue = rgb & 0xFF;

                sumRed += red;
                sumGreen += green;
                sumBlue += blue;
                sumRedSquare += (double) red * red;
                sumGreenSquare += (double) green * green;
                sumBlueSquare += (double) blue * blue;

                double brightness = 0.299 * red + 0.587 * green + 0.114 * blue;
                sumBrightness += brightness;
                sumBrightnessSquare += brightness * brightness;

                float[] hsb = Color.RGBtoHSB(red, green, blue, null);
                sumSaturation += hsb[1];

                if (red > 230 && green > 230 && blue > 230) {
                    whitePixelCount++;
                }
                if (red < 30 && green < 30 && blue < 30) {
                    darkPixelCount++;
                }

                int grayValue = clampInt((int) Math.round(brightness), 0, 255);
                gray[y][x] = grayValue;
                grayHistogram[grayValue]++;
            }
        }

        double meanRed = sumRed / pixelCount;
        double meanGreen = sumGreen / pixelCount;
        double meanBlue = sumBlue / pixelCount;
        double meanBrightness = sumBrightness / pixelCount;

        double stdRed = standardDeviation(sumRedSquare, meanRed, pixelCount);
        double stdGreen = standardDeviation(sumGreenSquare, meanGreen, pixelCount);
        double stdBlue = standardDeviation(sumBlueSquare, meanBlue, pixelCount);
        double brightnessStd = standardDeviation(
                sumBrightnessSquare,
                meanBrightness,
                pixelCount);

        double meanSaturation = sumSaturation / pixelCount;
        double whitePixelRatio = whitePixelCount / (double) pixelCount;
        double darkPixelRatio = darkPixelCount / (double) pixelCount;
        double tissueRatio = 1.0 - whitePixelRatio;
        double entropy = calculateEntropy(grayHistogram, pixelCount);
        double sharpness = calculateLaplacianVariance(gray, width, height);
        double edgeRatio = calculateSobelEdgeRatio(gray, width, height);

        return new FeatureVector(
                meanRed,
                meanGreen,
                meanBlue,
                stdRed,
                stdGreen,
                stdBlue,
                meanBrightness,
                brightnessStd,
                meanSaturation,
                whitePixelRatio,
                darkPixelRatio,
                tissueRatio,
                entropy,
                sharpness,
                edgeRatio);
    }

    private double calculateEntropy(long[] histogram, long pixelCount) {
        double entropy = 0.0;
        for (long count : histogram) {
            if (count == 0) {
                continue;
            }
            double probability = count / (double) pixelCount;
            entropy -= probability * (Math.log(probability) / Math.log(2.0));
        }
        return entropy;
    }

    private double calculateLaplacianVariance(
            int[][] gray,
            int width,
            int height) {

        if (width < 3 || height < 3) {
            return 0.0;
        }

        double sum = 0.0;
        double sumSquare = 0.0;
        long count = 0;

        for (int y = 1; y < height - 1; y++) {
            for (int x = 1; x < width - 1; x++) {
                double laplacian =
                        gray[y - 1][x]
                                + gray[y + 1][x]
                                + gray[y][x - 1]
                                + gray[y][x + 1]
                                - 4.0 * gray[y][x];
                sum += laplacian;
                sumSquare += laplacian * laplacian;
                count++;
            }
        }

        double mean = sum / count;
        return Math.max(0.0, sumSquare / count - mean * mean);
    }

    private double calculateSobelEdgeRatio(
            int[][] gray,
            int width,
            int height) {

        if (width < 3 || height < 3) {
            return 0.0;
        }

        long edgeCount = 0;
        long interiorCount = 0;

        for (int y = 1; y < height - 1; y++) {
            for (int x = 1; x < width - 1; x++) {
                int topLeft = gray[y - 1][x - 1];
                int top = gray[y - 1][x];
                int topRight = gray[y - 1][x + 1];
                int left = gray[y][x - 1];
                int right = gray[y][x + 1];
                int bottomLeft = gray[y + 1][x - 1];
                int bottom = gray[y + 1][x];
                int bottomRight = gray[y + 1][x + 1];

                double gradientX =
                        -topLeft + topRight
                                - 2.0 * left + 2.0 * right
                                - bottomLeft + bottomRight;
                double gradientY =
                        -topLeft - 2.0 * top - topRight
                                + bottomLeft + 2.0 * bottom + bottomRight;

                double magnitude = Math.hypot(gradientX, gradientY);
                if (magnitude > SOBEL_EDGE_THRESHOLD) {
                    edgeCount++;
                }
                interiorCount++;
            }
        }

        return edgeCount / (double) interiorCount;
    }

    // ==================== Weka dataset ====================

    private Instances buildInstances(
            String relationName,
            List<LabeledFeature> samples) {

        ArrayList<Attribute> attributes = new ArrayList<>();
        for (String featureName : FEATURE_NAMES) {
            attributes.add(new Attribute(featureName));
        }
        attributes.add(new Attribute(
                "classLabel",
                new ArrayList<>(CLASS_VALUES)));

        Instances data = new Instances(relationName, attributes, samples.size());
        data.setClassIndex(data.numAttributes() - 1);

        for (LabeledFeature sample : samples) {
            double[] featureArray = sample.features().toArray();
            double[] values = new double[data.numAttributes()];
            System.arraycopy(featureArray, 0, values, 0, featureArray.length);
            values[data.classIndex()] = CLASS_VALUES.indexOf(sample.classLabel());
            data.add(new DenseInstance(1.0, values));
        }

        return data;
    }

    private void validateModelHeader(Instances header) {
        if (header.numAttributes() != FEATURE_NAMES.size() + 1) {
            throw new IllegalArgumentException(
                    "The model expects " + (header.numAttributes() - 1)
                            + " features, but PathoCheck uses " + FEATURE_NAMES.size() + ".");
        }

        for (int index = 0; index < FEATURE_NAMES.size(); index++) {
            String expected = FEATURE_NAMES.get(index);
            String actual = header.attribute(index).name();
            if (!expected.equals(actual)) {
                throw new IllegalArgumentException(
                        "Feature order mismatch at index " + index
                                + ": expected=" + expected + ", actual=" + actual);
            }
        }
    }

    // ==================== Quality rules ====================

    private List<String> generateWarnings(
            FeatureVector features,
            double validProbability) {

        List<String> warnings = new ArrayList<>();

        if (features.whitePixelRatio() > 0.60) {
            warnings.add("Background area is too large.");
        }
        if (features.tissueRatio() < 0.30) {
            warnings.add("Tissue area is insufficient.");
        }
        if (features.meanBrightness() > 220.0) {
            warnings.add("Image may be overexposed.");
        }
        if (features.meanBrightness() < 50.0) {
            warnings.add("Image may be underexposed.");
        }
        if (features.darkPixelRatio() > 0.50) {
            warnings.add("Dark area is too large.");
        }
        if (features.sharpness() < 50.0) {
            warnings.add("Image may be blurred.");
        }
        if (validProbability < 0.70) {
            warnings.add("Valid tissue probability is low.");
        }

        return warnings;
    }

    private double calculateQualityScore(
            FeatureVector features,
            double validProbability) {

        double tissueScore = normalize(features.tissueRatio(), 0.10, 0.70);
        double sharpnessScore = normalize(features.sharpness(), 0.0, 150.0);
        double exposureScore = clamp(
                1.0 - Math.abs(features.meanBrightness() - 160.0) / 160.0,
                0.0,
                1.0);
        double entropyScore = normalize(features.entropy(), 0.0, 8.0);

        return 60.0 * validProbability
                + 15.0 * tissueScore
                + 10.0 * sharpnessScore
                + 10.0 * exposureScore
                + 5.0 * entropyScore;
    }

    private String determineFinalStatus(
            FeatureVector features,
            double validProbability) {

        boolean reject =
                validProbability < 0.45
                        || features.tissueRatio() < 0.15
                        || features.whitePixelRatio() > 0.80
                        || features.darkPixelRatio() > 0.80
                        || features.sharpness() < 20.0;

        if (reject) {
            return "REJECT";
        }

        boolean warning =
                validProbability < 0.70
                        || features.tissueRatio() < 0.30
                        || features.whitePixelRatio() > 0.60
                        || features.meanBrightness() > 220.0
                        || features.meanBrightness() < 50.0
                        || features.darkPixelRatio() > 0.50
                        || features.sharpness() < 50.0;

        return warning ? "WARNING" : "PASS";
    }

    private String suggestionFor(String finalStatus) {
        return switch (finalStatus) {
            case "PASS" -> "The image is suitable for subsequent analysis.";
            case "WARNING" -> "The image can be used with caution. "
                    + "Please review the detected quality issues.";
            case "REJECT" -> "The image is not recommended for subsequent analysis. "
                    + "Please select another image.";
            default -> throw new IllegalArgumentException(
                    "Unknown assessment status: " + finalStatus);
        };
    }

    // ==================== General helpers ====================

    private BufferedImage readImage(Path imagePath) throws IOException {
        BufferedImage image = ImageIO.read(imagePath.toFile());
        if (image == null) {
            throw new IOException(
                    "Unsupported or damaged image file: " + imagePath);
        }
        return image;
    }

    private Path requireReadableFile(String filePath, String objectName) {
        if (filePath == null || filePath.isBlank()) {
            throw new IllegalArgumentException(objectName + " path cannot be empty.");
        }
        Path path = Path.of(filePath).toAbsolutePath().normalize();
        if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
            throw new IllegalArgumentException(
                    objectName + " file cannot be read: " + path);
        }
        return path;
    }

    private boolean isSupportedImage(Path path) {
        return SUPPORTED_EXTENSIONS.contains(extensionOf(path));
    }

    private String extensionOf(Path path) {
        String fileName = path.getFileName().toString();
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }

    private String detectClassLabel(Path datasetRoot, Path imagePath) {
        Path relativePath = datasetRoot.relativize(imagePath);
        Set<String> pathParts = new HashSet<>();
        for (Path part : relativePath) {
            pathParts.add(part.toString().toUpperCase(Locale.ROOT));
        }

        for (String label : INVALID_FOLDER_LABELS) {
            if (pathParts.contains(label)) {
                return "INVALID_REGION";
            }
        }
        for (String label : VALID_FOLDER_LABELS) {
            if (pathParts.contains(label)) {
                return "VALID_TISSUE";
            }
        }
        return null;
    }

    private String detectColorMode(BufferedImage image) {
        int componentCount = image.getColorModel().getNumColorComponents();
        return componentCount <= 1 ? "GRAY" : "RGB";
    }

    private double standardDeviation(
            double sumSquare,
            double mean,
            long count) {

        return Math.sqrt(Math.max(0.0, sumSquare / count - mean * mean));
    }

    private double normalize(double value, double minimum, double maximum) {
        if (maximum <= minimum) {
            throw new IllegalArgumentException(
                    "Normalization maximum must be greater than minimum.");
        }
        return clamp((value - minimum) / (maximum - minimum), 0.0, 1.0);
    }

    private double safeMetric(double value) {
        return Double.isFinite(value) ? round(value, 4) : 0.0;
    }

    private double round(double value, int decimalPlaces) {
        double scale = Math.pow(10.0, decimalPlaces);
        return Math.round(value * scale) / scale;
    }

    private double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private int clampInt(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    /**
     * 私有 record 不会增加新的 Java 文件，但能让 15 个特征保持固定顺序。
     */
    private record FeatureVector(
            double meanRed,
            double meanGreen,
            double meanBlue,
            double stdRed,
            double stdGreen,
            double stdBlue,
            double meanBrightness,
            double brightnessStd,
            double meanSaturation,
            double whitePixelRatio,
            double darkPixelRatio,
            double tissueRatio,
            double entropy,
            double sharpness,
            double edgeRatio) {

        private double[] toArray() {
            return new double[]{
                    meanRed,
                    meanGreen,
                    meanBlue,
                    stdRed,
                    stdGreen,
                    stdBlue,
                    meanBrightness,
                    brightnessStd,
                    meanSaturation,
                    whitePixelRatio,
                    darkPixelRatio,
                    tissueRatio,
                    entropy,
                    sharpness,
                    edgeRatio
            };
        }

        private Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            double[] values = toArray();
            for (int index = 0; index < FEATURE_NAMES.size(); index++) {
                map.put(FEATURE_NAMES.get(index), values[index]);
            }
            return map;
        }
    }

    private record LabeledFeature(
            FeatureVector features,
            String classLabel) {
    }
}
