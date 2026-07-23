package com.pathocheck;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import java.io.IOException;

/**
 * Vue3 与后端之间的“按钮接线表”。
 *
 * Controller 只负责：
 * - 接收请求；
 * - 读取参数；
 * - 调用 PathoCheckService；
 * - 返回 JSON 或 PDF。
 */
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = {
        "http://localhost:5173",
        "http://127.0.0.1:5173"
})
public class PathoCheckController {

    private final PathoCheckService pathoCheckService;

    public PathoCheckController(PathoCheckService pathoCheckService) {
        this.pathoCheckService = pathoCheckService;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "service", "PathoCheck",
                "time", LocalDateTime.now().toString());
    }

    // ==================== Dataset ====================

    @PostMapping(
            value = "/datasets/load",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> loadDataset(
            @RequestParam("datasetName") String datasetName,
            @RequestPart("files") List<MultipartFile> files,
            @RequestParam(value = "relativePaths", required = false)
            List<String> relativePaths) throws Exception {

        return pathoCheckService.loadDataset(
                datasetName,
                files,
                relativePaths);
    }

    @GetMapping("/datasets")
    public List<Map<String, Object>> listDatasets() {
        return pathoCheckService.listDatasets();
    }

    @DeleteMapping("/datasets/{datasetId}")
    public Map<String, Object> deleteDataset(
            @PathVariable Long datasetId) throws Exception {

        pathoCheckService.deleteDataset(datasetId);
        return Map.of(
                "datasetId", datasetId,
                "result", "SUCCESS");
    }

    // ==================== Model ====================

    @PostMapping(
            value = "/models/load",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> loadModel(
            @RequestParam("modelName") String modelName,
            @RequestPart("modelFile") MultipartFile modelFile)
            throws Exception {

        return pathoCheckService.loadModel(modelName, modelFile);
    }

    @PostMapping("/models/train")
    public Map<String, Object> trainModel(
            @RequestParam("datasetId") Long datasetId,
            @RequestParam("modelName") String modelName)
            throws Exception {

        return pathoCheckService.trainModel(datasetId, modelName);
    }

    @GetMapping("/models")
    public List<Map<String, Object>> listModels() {
        return pathoCheckService.listModels();
    }

    @DeleteMapping("/models/{modelId}")
    public Map<String, Object> deleteModel(
            @PathVariable Long modelId) throws Exception {

        pathoCheckService.deleteModel(modelId);
        return Map.of(
                "modelId", modelId,
                "result", "SUCCESS");
    }

    // ==================== Image ====================

    @PostMapping(
            value = "/images/select",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> selectImage(
            @RequestPart("imageFile") MultipartFile imageFile)
            throws Exception {

        return pathoCheckService.selectImage(imageFile);
    }

    @GetMapping(
        value = "/images/{imageId}/preview",
        produces = MediaType.IMAGE_PNG_VALUE)
public ResponseEntity<byte[]> previewImage(
        @PathVariable Long imageId) throws IOException {

    byte[] pngBytes =
            pathoCheckService.getImagePreview(imageId);

    return ResponseEntity.ok()
            .contentType(MediaType.IMAGE_PNG)
            .body(pngBytes);
   }

    // ==================== Assessment ====================

    @PostMapping("/assessments/start")
    public AssessmentReport startAssessment(
            @RequestBody Map<String, Long> request) throws Exception {

        Long modelId = request.get("modelId");
        Long imageId = request.get("imageId");
        if (modelId == null || imageId == null) {
            throw new IllegalArgumentException(
                    "Request body must contain modelId and imageId.");
        }
        return pathoCheckService.assess(modelId, imageId);
    }

    @GetMapping("/reports/{reportId}/pdf")
    public ResponseEntity<Resource> exportPdf(
            @PathVariable Long reportId) throws Exception {

        Path pdfPath = pathoCheckService.exportReport(reportId);
        Resource resource = new FileSystemResource(pdfPath);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=report_" + reportId + ".pdf")
                .body(resource);
    }

    // ==================== Help ====================

    @GetMapping(value = "/logs", produces = MediaType.TEXT_PLAIN_VALUE)
    public String getLog() throws Exception {
        return pathoCheckService.readLog();
    }

    // ==================== Unified error response ====================

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleException(
            Exception exception) {

        HttpStatus status = exception instanceof IllegalArgumentException
                ? HttpStatus.BAD_REQUEST
                : HttpStatus.INTERNAL_SERVER_ERROR;

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("result", "FAILED");
        response.put("error", exception.getClass().getSimpleName());
        response.put("message", exception.getMessage());
        response.put("time", LocalDateTime.now().toString());
        return ResponseEntity.status(status).body(response);
    }
}
