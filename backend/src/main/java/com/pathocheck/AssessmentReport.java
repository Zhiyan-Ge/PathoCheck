package com.pathocheck;

import java.util.ArrayList;
import java.util.List;

/**
 * 单张病理图像的完整评估结果。
 *
 * 该对象同时承担三个角色：
 * 1. ModelPredictor 的输出；
 * 2. DatabaseService 的保存输入；
 * 3. Controller 返回给 Vue3 的 JSON 数据。
 */
public class AssessmentReport {

    private Long reportId;
    private Long imageId;
    private String imageName;
    private Long modelId;
    private String modelName;
    private String assessmentTime;

    private String predictedLabel;
    private double validProbability;
    private double invalidProbability;

    private double meanRed;
    private double meanGreen;
    private double meanBlue;
    private double meanBrightness;
    private double brightnessStd;
    private double meanSaturation;
    private double whitePixelRatio;
    private double darkPixelRatio;
    private double tissueRatio;
    private double entropy;
    private double sharpness;
    private double edgeRatio;

    private double qualityScore;
    private String finalStatus;
    private List<String> warningMessages = new ArrayList<>();
    private String suggestion;

    public AssessmentReport() {
    }

    public Long getReportId() {
        return reportId;
    }

    public void setReportId(Long reportId) {
        this.reportId = reportId;
    }

    public Long getImageId() {
        return imageId;
    }

    public void setImageId(Long imageId) {
        this.imageId = imageId;
    }

    public String getImageName() {
        return imageName;
    }

    public void setImageName(String imageName) {
        this.imageName = imageName;
    }

    public Long getModelId() {
        return modelId;
    }

    public void setModelId(Long modelId) {
        this.modelId = modelId;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public String getAssessmentTime() {
        return assessmentTime;
    }

    public void setAssessmentTime(String assessmentTime) {
        this.assessmentTime = assessmentTime;
    }

    public String getPredictedLabel() {
        return predictedLabel;
    }

    public void setPredictedLabel(String predictedLabel) {
        this.predictedLabel = predictedLabel;
    }

    public double getValidProbability() {
        return validProbability;
    }

    public void setValidProbability(double validProbability) {
        this.validProbability = validProbability;
    }

    public double getInvalidProbability() {
        return invalidProbability;
    }

    public void setInvalidProbability(double invalidProbability) {
        this.invalidProbability = invalidProbability;
    }

    public double getMeanRed() {
        return meanRed;
    }

    public void setMeanRed(double meanRed) {
        this.meanRed = meanRed;
    }

    public double getMeanGreen() {
        return meanGreen;
    }

    public void setMeanGreen(double meanGreen) {
        this.meanGreen = meanGreen;
    }

    public double getMeanBlue() {
        return meanBlue;
    }

    public void setMeanBlue(double meanBlue) {
        this.meanBlue = meanBlue;
    }

    public double getMeanBrightness() {
        return meanBrightness;
    }

    public void setMeanBrightness(double meanBrightness) {
        this.meanBrightness = meanBrightness;
    }

    public double getBrightnessStd() {
        return brightnessStd;
    }

    public void setBrightnessStd(double brightnessStd) {
        this.brightnessStd = brightnessStd;
    }

    public double getMeanSaturation() {
        return meanSaturation;
    }

    public void setMeanSaturation(double meanSaturation) {
        this.meanSaturation = meanSaturation;
    }

    public double getWhitePixelRatio() {
        return whitePixelRatio;
    }

    public void setWhitePixelRatio(double whitePixelRatio) {
        this.whitePixelRatio = whitePixelRatio;
    }

    public double getDarkPixelRatio() {
        return darkPixelRatio;
    }

    public void setDarkPixelRatio(double darkPixelRatio) {
        this.darkPixelRatio = darkPixelRatio;
    }

    public double getTissueRatio() {
        return tissueRatio;
    }

    public void setTissueRatio(double tissueRatio) {
        this.tissueRatio = tissueRatio;
    }

    public double getEntropy() {
        return entropy;
    }

    public void setEntropy(double entropy) {
        this.entropy = entropy;
    }

    public double getSharpness() {
        return sharpness;
    }

    public void setSharpness(double sharpness) {
        this.sharpness = sharpness;
    }

    public double getEdgeRatio() {
        return edgeRatio;
    }

    public void setEdgeRatio(double edgeRatio) {
        this.edgeRatio = edgeRatio;
    }

    public double getQualityScore() {
        return qualityScore;
    }

    public void setQualityScore(double qualityScore) {
        this.qualityScore = qualityScore;
    }

    public String getFinalStatus() {
        return finalStatus;
    }

    public void setFinalStatus(String finalStatus) {
        this.finalStatus = finalStatus;
    }

    public List<String> getWarningMessages() {
        return warningMessages;
    }

    public void setWarningMessages(List<String> warningMessages) {
        this.warningMessages = warningMessages == null
                ? new ArrayList<>()
                : new ArrayList<>(warningMessages);
    }

    public String getSuggestion() {
        return suggestion;
    }

    public void setSuggestion(String suggestion) {
        this.suggestion = suggestion;
    }
}
