export interface Dataset {
  id: number;
  datasetName: string;
  imageCount: number;
  validCount: number;
  invalidCount: number;
}

export interface Model {
  id: number;
  modelName: string;
  accuracy: number | null;
  precision: number | null;
  recall: number | null;
  f1Score: number | null;
  createdTime: string;
}

export interface ImageInfo {
  imageId: number;
  imageName: string;
  fileFormat: string;
  width: number;
  height: number;
  fileSize: number;
  colorMode: string;
}

export interface AssessmentReport {
  reportId: number;
  imageId: number;
  imageName: string;
  modelId: number;
  modelName: string;
  
  predictedLabel: string;
  validProbability: number;
  invalidProbability: number;
  
  meanBrightness: number;
  brightnessStd: number;
  meanSaturation: number;
  whitePixelRatio: number;
  darkPixelRatio: number;
  tissueRatio: number;
  entropy: number;
  sharpness: number;
  edgeRatio: number;
  
  qualityScore: number;
  finalStatus: 'PASS' | 'WARNING' | 'REJECT';
  warningMessages: string[];
  suggestion: string;
  assessmentTime: string;
}
