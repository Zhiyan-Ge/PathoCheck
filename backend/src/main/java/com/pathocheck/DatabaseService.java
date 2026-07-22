package com.pathocheck;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据库模块 —— 基于 JdbcTemplate 的真实 MySQL 实现。
 *
 * 所有 public 方法的方法名、参数、返回类型与原骨架保持一致，
 * 内部已替换为 SQL 操作。
 */
@Service
public class DatabaseService {

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JdbcTemplate jdbc;

    public DatabaseService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ==================== Dataset ====================

    public Map<String, Object> saveDataset(
            String datasetName,
            String datasetPath,
            int imageCount,
            int validCount,
            int invalidCount) {

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO dataset_record (dataset_name, dataset_path, image_count, valid_count, invalid_count, created_time) "
                            + "VALUES (?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, datasetName);
            ps.setString(2, datasetPath);
            ps.setInt(3, imageCount);
            ps.setInt(4, validCount);
            ps.setInt(5, invalidCount);
            ps.setTimestamp(6, Timestamp.valueOf(LocalDateTime.now()));
            return ps;
        }, keyHolder);

        long id = keyHolder.getKey().longValue();
        return findDatasetById(id);
    }

    public List<Map<String, Object>> listDatasets() {
        return jdbc.query(
                "SELECT * FROM dataset_record ORDER BY id DESC",
                (rs, rowNum) -> mapDatasetRow(rs));
    }

    public Map<String, Object> findDatasetById(Long datasetId) {
        List<Map<String, Object>> results = jdbc.query(
                "SELECT * FROM dataset_record WHERE id = ?",
                (rs, rowNum) -> mapDatasetRow(rs),
                datasetId);
        if (results.isEmpty()) {
            throw new IllegalArgumentException("Dataset does not exist: " + datasetId);
        }
        return results.get(0);
    }

    public void deleteDataset(Long datasetId) {
        int rows = jdbc.update("DELETE FROM dataset_record WHERE id = ?", datasetId);
        if (rows == 0) {
            throw new IllegalArgumentException("Dataset does not exist: " + datasetId);
        }
    }

    // ==================== Model ====================

    public Map<String, Object> saveModel(
            String modelName,
            String modelPath,
            Map<String, Object> trainingResult) {

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO model_record (model_name, model_path, algorithm, train_sample_count, test_sample_count, "
                            + "accuracy, `precision`, recall, f1_score, confusion_matrix, created_time) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, modelName);
            ps.setString(2, modelPath);
            ps.setString(3, "RandomForest");
            ps.setObject(4, trainingResult.get("trainSampleCount"));
            ps.setObject(5, trainingResult.get("testSampleCount"));
            ps.setObject(6, trainingResult.get("accuracy"));
            ps.setObject(7, trainingResult.get("precision"));
            ps.setObject(8, trainingResult.get("recall"));
            ps.setObject(9, trainingResult.get("f1Score"));
            ps.setString(10, toJsonString(trainingResult.get("confusionMatrix")));
            ps.setTimestamp(11, Timestamp.valueOf(LocalDateTime.now()));
            return ps;
        }, keyHolder);

        long id = keyHolder.getKey().longValue();
        return findModelById(id);
    }

    public List<Map<String, Object>> listModels() {
        return jdbc.query(
                "SELECT * FROM model_record ORDER BY id DESC",
                (rs, rowNum) -> mapModelRow(rs));
    }

    public Map<String, Object> findModelById(Long modelId) {
        List<Map<String, Object>> results = jdbc.query(
                "SELECT * FROM model_record WHERE id = ?",
                (rs, rowNum) -> mapModelRow(rs),
                modelId);
        if (results.isEmpty()) {
            throw new IllegalArgumentException("Model does not exist: " + modelId);
        }
        return results.get(0);
    }

    public void deleteModel(Long modelId) {
        int rows = jdbc.update("DELETE FROM model_record WHERE id = ?", modelId);
        if (rows == 0) {
            throw new IllegalArgumentException("Model does not exist: " + modelId);
        }
    }

    // ==================== Image ====================

    public Map<String, Object> saveImage(
            String imageName,
            String imagePath,
            Map<String, Object> imageInfo) {

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO image_record (image_name, image_path, file_format, width, height, file_size, color_mode, created_time) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, imageName);
            ps.setString(2, imagePath);
            ps.setObject(3, imageInfo.get("fileFormat"));
            ps.setObject(4, imageInfo.get("width"));
            ps.setObject(5, imageInfo.get("height"));
            ps.setObject(6, imageInfo.get("fileSize"));
            ps.setObject(7, imageInfo.get("colorMode"));
            ps.setTimestamp(8, Timestamp.valueOf(LocalDateTime.now()));
            return ps;
        }, keyHolder);

        long id = keyHolder.getKey().longValue();
        return findImageById(id);
    }

    public Map<String, Object> findImageById(Long imageId) {
        List<Map<String, Object>> results = jdbc.query(
                "SELECT * FROM image_record WHERE id = ?",
                (rs, rowNum) -> mapImageRow(rs),
                imageId);
        if (results.isEmpty()) {
            throw new IllegalArgumentException("Image does not exist: " + imageId);
        }
        return results.get(0);
    }

    public void deleteImage(Long imageId) {
        int rows = jdbc.update("DELETE FROM image_record WHERE id = ?", imageId);
        if (rows == 0) {
            throw new IllegalArgumentException("Image does not exist: " + imageId);
        }
    }

    // ==================== Assessment Report ====================

    public AssessmentReport saveReport(AssessmentReport report) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO assessment_report (image_id, image_name, model_id, model_name, assessment_time, "
                            + "predicted_label, valid_probability, invalid_probability, "
                            + "mean_red, mean_green, mean_blue, mean_brightness, brightness_std, mean_saturation, "
                            + "white_pixel_ratio, dark_pixel_ratio, tissue_ratio, entropy, sharpness, edge_ratio, "
                            + "quality_score, final_status, warning_messages, suggestion, pdf_path) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, report.getImageId());
            ps.setString(2, report.getImageName());
            ps.setLong(3, report.getModelId());
            ps.setString(4, report.getModelName());
            ps.setTimestamp(5, Timestamp.valueOf(LocalDateTime.now()));
            ps.setString(6, report.getPredictedLabel());
            ps.setDouble(7, report.getValidProbability());
            ps.setDouble(8, report.getInvalidProbability());
            ps.setDouble(9, report.getMeanRed());
            ps.setDouble(10, report.getMeanGreen());
            ps.setDouble(11, report.getMeanBlue());
            ps.setDouble(12, report.getMeanBrightness());
            ps.setDouble(13, report.getBrightnessStd());
            ps.setDouble(14, report.getMeanSaturation());
            ps.setDouble(15, report.getWhitePixelRatio());
            ps.setDouble(16, report.getDarkPixelRatio());
            ps.setDouble(17, report.getTissueRatio());
            ps.setDouble(18, report.getEntropy());
            ps.setDouble(19, report.getSharpness());
            ps.setDouble(20, report.getEdgeRatio());
            ps.setDouble(21, report.getQualityScore());
            ps.setString(22, report.getFinalStatus());
            ps.setString(23, listToJson(report.getWarningMessages()));
            ps.setString(24, report.getSuggestion());
            ps.setString(25, null);
            return ps;
        }, keyHolder);

        long id = keyHolder.getKey().longValue();
        report.setReportId(id);
        return report;
    }

    public AssessmentReport findReportById(Long reportId) {
        List<AssessmentReport> results = jdbc.query(
                "SELECT * FROM assessment_report WHERE id = ?",
                (rs, rowNum) -> mapReportRow(rs),
                reportId);
        if (results.isEmpty()) {
            throw new IllegalArgumentException("Assessment report does not exist: " + reportId);
        }
        return results.get(0);
    }

    public void updatePdfPath(Long reportId, String pdfPath) {
        int rows = jdbc.update(
                "UPDATE assessment_report SET pdf_path = ? WHERE id = ?",
                pdfPath, reportId);
        if (rows == 0) {
            throw new IllegalArgumentException("Assessment report does not exist: " + reportId);
        }
    }

    public String findPdfPath(Long reportId) {
        List<String> results = jdbc.query(
                "SELECT pdf_path FROM assessment_report WHERE id = ?",
                (rs, rowNum) -> rs.getString("pdf_path"),
                reportId);
        return results.isEmpty() ? null : results.get(0);
    }

    // ==================== Row Mappers ====================

    private Map<String, Object> mapDatasetRow(ResultSet rs) throws SQLException {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("id", rs.getLong("id"));
        record.put("datasetName", rs.getString("dataset_name"));
        record.put("datasetPath", rs.getString("dataset_path"));
        record.put("imageCount", rs.getInt("image_count"));
        record.put("validCount", rs.getInt("valid_count"));
        record.put("invalidCount", rs.getInt("invalid_count"));
        record.put("createdTime", formatTime(rs.getTimestamp("created_time")));
        return record;
    }

    private Map<String, Object> mapModelRow(ResultSet rs) throws SQLException {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("id", rs.getLong("id"));
        record.put("modelName", rs.getString("model_name"));
        record.put("modelPath", rs.getString("model_path"));
        record.put("algorithm", rs.getString("algorithm"));
        record.put("trainSampleCount", rs.getObject("train_sample_count"));
        record.put("testSampleCount", rs.getObject("test_sample_count"));
        record.put("accuracy", rs.getObject("accuracy"));
        record.put("precision", rs.getObject("precision"));
        record.put("recall", rs.getObject("recall"));
        record.put("f1Score", rs.getObject("f1_score"));
        record.put("confusionMatrix", rs.getString("confusion_matrix"));
        record.put("createdTime", formatTime(rs.getTimestamp("created_time")));
        return record;
    }

    private Map<String, Object> mapImageRow(ResultSet rs) throws SQLException {
    Map<String, Object> record = new LinkedHashMap<>();

    long imageId = rs.getLong("id");
    record.put("id", imageId);
    record.put("imageId", imageId);

    record.put("imageName", rs.getString("image_name"));
    record.put("imagePath", rs.getString("image_path"));
    record.put("fileFormat", rs.getString("file_format"));
    record.put("width", rs.getObject("width"));
    record.put("height", rs.getObject("height"));
    record.put("fileSize", rs.getObject("file_size"));
    record.put("colorMode", rs.getString("color_mode"));
    record.put("createdTime", formatTime(rs.getTimestamp("created_time")));
    return record;
    }

    private AssessmentReport mapReportRow(ResultSet rs) throws SQLException {
        AssessmentReport report = new AssessmentReport();
        report.setReportId(rs.getLong("id"));
        report.setImageId(rs.getLong("image_id"));
        report.setImageName(rs.getString("image_name"));
        report.setModelId(rs.getLong("model_id"));
        report.setModelName(rs.getString("model_name"));
        report.setAssessmentTime(formatTime(rs.getTimestamp("assessment_time")));
        report.setPredictedLabel(rs.getString("predicted_label"));
        report.setValidProbability(rs.getDouble("valid_probability"));
        report.setInvalidProbability(rs.getDouble("invalid_probability"));
        report.setMeanRed(rs.getDouble("mean_red"));
        report.setMeanGreen(rs.getDouble("mean_green"));
        report.setMeanBlue(rs.getDouble("mean_blue"));
        report.setMeanBrightness(rs.getDouble("mean_brightness"));
        report.setBrightnessStd(rs.getDouble("brightness_std"));
        report.setMeanSaturation(rs.getDouble("mean_saturation"));
        report.setWhitePixelRatio(rs.getDouble("white_pixel_ratio"));
        report.setDarkPixelRatio(rs.getDouble("dark_pixel_ratio"));
        report.setTissueRatio(rs.getDouble("tissue_ratio"));
        report.setEntropy(rs.getDouble("entropy"));
        report.setSharpness(rs.getDouble("sharpness"));
        report.setEdgeRatio(rs.getDouble("edge_ratio"));
        report.setQualityScore(rs.getDouble("quality_score"));
        report.setFinalStatus(rs.getString("final_status"));
        report.setWarningMessages(jsonToList(rs.getString("warning_messages")));
        report.setSuggestion(rs.getString("suggestion"));
        return report;
    }

    // ==================== Helpers ====================

    private String formatTime(Timestamp timestamp) {
        if (timestamp == null) {
            return null;
        }
        return timestamp.toLocalDateTime().format(TIME_FORMATTER);
    }

    /**
     * 将 confusionMatrix 对象转为字符串存入 TEXT 字段。
     * 如果是 Map 则简单序列化为 key=value 格式；否则直接 toString。
     */
    private String toJsonString(Object obj) {
        if (obj == null) {
            return null;
        }
        return obj.toString();
    }

    /**
     * 将 List&lt;String&gt; 序列化为 JSON 数组字符串，例如 ["a","b"]。
     */
    private String listToJson(List<String> list) {
        if (list == null || list.isEmpty()) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append("\"").append(escapeJson(list.get(i))).append("\"");
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * 将 JSON 数组字符串反序列化为 List&lt;String&gt;。
     */
    private List<String> jsonToList(String json) {
        List<String> result = new ArrayList<>();
        if (json == null || json.isBlank() || "[]".equals(json.trim())) {
            return result;
        }
        String trimmed = json.trim();
        if (trimmed.startsWith("[")) {
            trimmed = trimmed.substring(1);
        }
        if (trimmed.endsWith("]")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        if (trimmed.isBlank()) {
            return result;
        }
        // 按 "," 分割并去除引号
        String[] parts = trimmed.split("\",\"");
        for (String part : parts) {
            String cleaned = part.trim();
            if (cleaned.startsWith("\"")) {
                cleaned = cleaned.substring(1);
            }
            if (cleaned.endsWith("\"")) {
                cleaned = cleaned.substring(0, cleaned.length() - 1);
            }
            result.add(unescapeJson(cleaned));
        }
        return result;
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String unescapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\\"", "\"").replace("\\\\", "\\");
    }
}
