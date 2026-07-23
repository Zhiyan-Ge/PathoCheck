import axios from 'axios';
import type { Dataset, Model, ImageInfo, AssessmentReport } from '../types';

const apiClient = axios.create({
  baseURL: '/api',
  timeout: 0,
});

<<<<<<< HEAD
export const loadDataset = async (datasetName: string, files: File[], relativePaths: string[], onProgress?: (percent: number) => void) => {
=======
export const loadDataset = async (datasetName: string, files: File[], relativePaths: string[]) => {
>>>>>>> f2b1b414223fd24c9e02afe59abc1f558944c254
  const formData = new FormData();
  formData.append('datasetName', datasetName);
  files.forEach(file => {
    formData.append('files', file);
  });
  relativePaths.forEach(path => {
    formData.append('relativePaths', path);
  });
  const res = await apiClient.post('/datasets/load', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
<<<<<<< HEAD
    onUploadProgress: (progressEvent) => {
      if (onProgress && progressEvent.total) {
        const percent = Math.round((progressEvent.loaded * 100) / progressEvent.total);
        onProgress(percent);
      }
    },
=======
>>>>>>> f2b1b414223fd24c9e02afe59abc1f558944c254
  });
  return res.data;
};

export const listDatasets = async (): Promise<Dataset[]> => {
  const res = await apiClient.get('/datasets');
  return res.data;
};

export const deleteDataset = async (datasetId: number) => {
  const res = await apiClient.delete(`/datasets/${datasetId}`);
  return res.data;
};

<<<<<<< HEAD
export const loadModel = async (modelName: string, modelFile: File, onProgress?: (percent: number) => void) => {
=======
export const loadModel = async (modelName: string, modelFile: File) => {
>>>>>>> f2b1b414223fd24c9e02afe59abc1f558944c254
  const formData = new FormData();
  formData.append('modelName', modelName);
  formData.append('modelFile', modelFile);
  const res = await apiClient.post('/models/load', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
<<<<<<< HEAD
    onUploadProgress: (progressEvent) => {
      if (onProgress && progressEvent.total) {
        const percent = Math.round((progressEvent.loaded * 100) / progressEvent.total);
        onProgress(percent);
      }
    },
=======
>>>>>>> f2b1b414223fd24c9e02afe59abc1f558944c254
  });
  return res.data;
};

export const trainModel = async (datasetId: number, modelName: string) => {
  const formData = new FormData();
  formData.append('datasetId', datasetId.toString());
  formData.append('modelName', modelName);
  const res = await apiClient.post('/models/train', formData);
  return res.data;
};

export const listModels = async (): Promise<Model[]> => {
  const res = await apiClient.get('/models');
  return res.data;
};

export const deleteModel = async (modelId: number) => {
  const res = await apiClient.delete(`/models/${modelId}`);
  return res.data;
};

export const selectImage = async (imageFile: File): Promise<ImageInfo> => {
  const formData = new FormData();
  formData.append('imageFile', imageFile);
  const res = await apiClient.post('/images/select', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
  return res.data;
};

export const startAssessment = async (modelId: number, imageId: number): Promise<AssessmentReport> => {
  const res = await apiClient.post('/assessments/start', { modelId, imageId });
  return res.data;
};

export const exportPdf = (reportId: number) => {
  window.open(`/api/reports/${reportId}/pdf`, '_blank');
};

export const getLogs = async (): Promise<string> => {
  const res = await apiClient.get('/logs');
  return res.data;
};
