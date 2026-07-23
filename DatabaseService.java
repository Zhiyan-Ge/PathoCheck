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
 * 数据库服务层 —— 基于 Spring JdbcTemplate 实现，负责所有 MySQL 持久化操作。
 *
 * <p>本类封装了四大业务表的 CRUD 操作：
 * <ul>
 *   <li>dataset_record —— 数据集记录（名称、路径、图片统计）</li>
 *   <li>model_record  —— 模型记录（训练参数、评估指标）</li>
 *   <li>image_record  —— 图像记录（文件元信息）</li>
 *   <li>assessment_report —— 质量评估报告（预测结果、图像特征、质量评分）</li>
 * </ul>
 *
 * <p>所有 public 方法的方法名、参数、返回类型与原骨架保持一致，
 * 内部已替换为 SQL 操作。返回的 Map 使用 camelCase 键名，方便前端直接消费。
 */
@Service
public class DatabaseService {

    /** 统一的时间格式化器，输出格式为 "yyyy-MM-dd HH:mm:ss" */
    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** Spring 注入的 JDBC 模板，所有数据库操作均通过此对象执行 */
    private final JdbcTemplate jdbc;

    /**
     * 构造器注入 JdbcTemplate（Spring 自动装配）。
     *
     * @param jdbc Spring 容器中配置好的 JdbcTemplate 实例
     */
    public DatabaseService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ==================== Dataset（数据集管理） ====================

    /**
     * 保存一条新的数据集记录到数据库。
     *
     * @param datasetName  数据集名称（如 "CRC-VAL-HE-7K"）
     * @param datasetPath  数据集在磁盘上的绝对路径
     * @param imageCount   数据集中图片总数
     * @param validCount   有效（合格）图片数量
     * @param invalidCount 无效（不合格）图片数量
     * @return 插入后从数据库读取的完整数据集记录（含自增 ID 和创建时间）
     */
    public Map<String, Object> saveDataset(
            String datasetName,
            String datasetPath,
            int imageCount,
            int validCount,
            int invalidCount) {

        // 使用 KeyHolder 获取数据库自增生成的主键 ID
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

    /**
     * 查询所有数据集记录，按 ID 降序排列（最新创建的排在前面）。
     *
     * @return 数据集记录列表，每条记录为一个 Map
     */
    public List<Map<String, Object>> listDatasets() {
        return jdbc.query(
                "SELECT * FROM dataset_record ORDER BY id DESC",
                (rs, rowNum) -> mapDatasetRow(rs));
    }

    /**
     * 根据 ID 查询单条数据集记录。
     *
     * @param datasetId 数据集主键 ID
     * @return 数据集记录 Map
     * @throws IllegalArgumentException 若指定 ID 不存在
     */
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

    /**
     * 根据 ID 删除数据集记录。
     *
     * @param datasetId 数据集主键 ID
     * @throws IllegalArgumentException 若指定 ID 不存在（影响行数为 0）
     */
    public void deleteDataset(Long datasetId) {
        int rows = jdbc.update("DELETE FROM dataset_record WHERE id = ?", datasetId);
        if (rows == 0) {
            throw new IllegalArgumentException("Dataset does not exist: " + datasetId);
        }
    }

    // ==================== Model（模型管理） ====================

    /**
     * 保存一条新的模型训练记录。
     *
     * @param modelName      模型名称
     * @param modelPath      模型文件在磁盘上的路径
     * @param trainingResult 训练结果 Map，包含 trainSampleCount、testSampleCount、
     *                       accuracy、precision、recall、f1Score、confusionMatrix 等字段
     * @return 插入后从数据库读取的完整模型记录
     */
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

    /**
     * 查询所有模型记录，按 ID 降序排列。
     *
     * @return 模型记录列表
     */
    public List<Map<String, Object>> listModels() {
        return jdbc.query(
                "SELECT * FROM model_record ORDER BY id DESC",
                (rs, rowNum) -> mapModelRow(rs));
    }

    /**
     * 根据 ID 查询单条模型记录。
     *
     * @param modelId 模型主键 ID
     * @return 模型记录 Map
     * @throws IllegalArgumentException 若指定 ID 不存在
     */
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

    /**
     * 根据 ID 删除模型记录。
     *
     * @param modelId 模型主键 ID
     * @throws IllegalArgumentException 若指定 ID 不存在
     */
    public void deleteModel(Long modelId) {
        int rows = jdbc.update("DELETE FROM model_record WHERE id = ?", modelId);
        if (rows == 0) {
            throw new IllegalArgumentException("Model does not exist: " + modelId);
        }
    }

    // ==================== Image（图像管理） ====================

    /**
     * 保存一条新的图像记录。
     *
     * @param imageName 图像文件名
     * @param imagePath 图像文件在磁盘上的路径
     * @param imageInfo 图像元信息 Map，包含 fileFormat、width、height、fileSize、colorMode
     * @return 插入后从数据库读取的完整图像记录
     */
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

    /**
     * 根据 ID 查询单条图像记录。
     *
     * @param imageId 图像主键 ID
     * @return 图像记录 Map
     * @throws IllegalArgumentException 若指定 ID 不存在
     */
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

    /**
     * 根据 ID 删除图像记录。
     *
     * @param imageId 图像主键 ID
     * @throws IllegalArgumentException 若指定 ID 不存在
     */
    public void deleteImage(Long imageId) {
        int rows = jdbc.update("DELETE FROM image_record WHERE id = ?", imageId);
        if (rows == 0) {
            throw new IllegalArgumentException("Image does not exist: " + imageId);
        }
    }

    // ==================== Assessment Report（评估报告管理） ====================

    /**
     * 保存一份完整的质量评估报告。
     * <p>报告包含：模型预测结果（标签、概率）、图像统计特征（RGB 均值、亮度、
     * 饱和度、白/暗像素比例、组织占比、熵、清晰度、边缘比例）以及最终质量评分和建议。
     *
     * @param report 评估报告对象（reportId 会被自动回填）
     * @return 设置了 reportId 的报告对象
     */
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

    /**
     * 根据 ID 查询单条评估报告。
     *
     * @param reportId 报告主键 ID
     * @return 评估报告对象
     * @throws IllegalArgumentException 若指定 ID 不存在
     */
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

    /**
     * 更新评估报告关联的 PDF 文件路径（报告生成后回填）。
     *
     * @param reportId 报告主键 ID
     * @param pdfPath  PDF 文件的存储路径
     * @throws IllegalArgumentException 若指定 ID 不存在
     */
    public void updatePdfPath(Long reportId, String pdfPath) {
        int rows = jdbc.update(
                "UPDATE assessment_report SET pdf_path = ? WHERE id = ?",
                pdfPath, reportId);
        if (rows == 0) {
            throw new IllegalArgumentException("Assessment report does not exist: " + reportId);
        }
    }

    /**
     * 查询评估报告对应的 PDF 文件路径。
     *
     * @param reportId 报告主键 ID
     * @return PDF 路径字符串，若报告不存在或尚未生成 PDF 则返回 null
     */
    public String findPdfPath(Long reportId) {
        List<String> results = jdbc.query(
                "SELECT pdf_path FROM assessment_report WHERE id = ?",
                (rs, rowNum) -> rs.getString("pdf_path"),
                reportId);
        return results.isEmpty() ? null : results.get(0);
    }

    // ==================== Row Mappers（ResultSet → Java 对象映射） ====================

    /**
     * 将 dataset_record 表的一行映射为 camelCase 键的 Map。
     */
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

    /**
     * 将 model_record 表的一行映射为 camelCase 键的 Map。
     * 包含训练指标（accuracy、precision、recall、f1）和混淆矩阵。
     */
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

    /**
     * 将 image_record 表的一行映射为 camelCase 键的 Map。
     * 注意：同时放入 "id" 和 "imageId" 两个键，兼容前端不同取值方式。
     */
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

    /**
     * 将 assessment_report 表的一行映射为 AssessmentReport 对象。
     * warning_messages 字段以 JSON 数组字符串存储，此处反序列化为 List。
     */
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

    // ==================== Helpers（工具方法） ====================

    /**
     * 将 SQL Timestamp 格式化为 "yyyy-MM-dd HH:mm:ss" 字符串，null 安全。
     */
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

    /**
     * 对字符串中的反斜杠和双引号进行 JSON 转义，防止序列化后格式错误。
     */
    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * 对 JSON 转义字符进行反转义，还原为原始字符串。
     */
    private String unescapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\\"", "\"").replace("\\\\", "\\");
    }
}
