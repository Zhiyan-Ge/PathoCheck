import axios from 'axios';
import type { Dataset, Model, ImageInfo, AssessmentReport } from '../types';

const apiClient = axios.create({
  baseURL: 'http://localhost:8080/api',
  timeout: 60000,
});

export const loadDataset = async (datasetName: string, files: File[], relativePaths: string[]) => {
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

export const loadModel = async (modelName: string, modelFile: File) => {
  const formData = new FormData();
  formData.append('modelName', modelName);
  formData.append('modelFile', modelFile);
  const res = await apiClient.post('/models/load', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
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
  window.open(`http://localhost:8080/api/reports/${reportId}/pdf`, '_blank');
};

export const getLogs = async (): Promise<string> => {
  const res = await apiClient.get('/logs');
  return res.data;
};
