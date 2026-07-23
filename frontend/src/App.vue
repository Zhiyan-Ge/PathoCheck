<script setup lang="ts">
import { ref, provide } from 'vue'
import Toolbar from './components/Toolbar.vue'
import Explorer from './components/Explorer.vue'
import AssessmentDialog from './components/AssessmentDialog.vue'
import ReportDialog from './components/ReportDialog.vue'
import type { Model, ImageInfo, AssessmentReport, Dataset } from './types'

const explorerRef = ref<InstanceType<typeof Explorer> | null>(null)
const explorerVisible = ref(false)
const assessmentDialogVisible = ref(false)
const reportDialogVisible = ref(false)

const currentDataset = ref<Dataset | null>(null)
const currentModel = ref<Model | null>(null)
const currentImage = ref<ImageInfo | null>(null)
const currentImagePreviewUrl = ref<string | null>(null)
const currentReport = ref<AssessmentReport | null>(null)

const openExplorer = () => {
  explorerVisible.value = true
}

const handleImageSelected = (image: ImageInfo, previewUrl?: string) => {
  currentImage.value = image
  if (previewUrl) {
    currentImagePreviewUrl.value = previewUrl
  }
  assessmentDialogVisible.value = true
}

const handleAssessmentComplete = (report: AssessmentReport) => {
  currentReport.value = report
  assessmentDialogVisible.value = false
  reportDialogVisible.value = true
}

provide('appState', {
  currentDataset,
  currentModel,
  currentImage,
  explorerVisible,
  assessmentDialogVisible,
  reportDialogVisible
})

</script>

<template>
  <div class="h-screen w-screen flex flex-col relative overflow-hidden bg-black">
    <!-- Background -->
    <div class="absolute inset-0 z-0 opacity-40 flex items-center justify-center pointer-events-none">
      <img src="/taffy.png" class="object-contain w-full h-full" alt="Background" />
    </div>

    <!-- Main UI layer -->
    <div class="relative z-10 flex flex-col h-full">
      <Toolbar 
        @open-explorer="openExplorer"
        @import-dataset="explorerRef?.handleImportDataset()"
        @import-model="explorerRef?.handleImportModel()"
        @train-model="explorerRef?.handleTrainModel()"
        @select-image="explorerRef?.handleSelectImage()"
      />
      
      <div class="flex-1 flex flex-col items-center justify-center">
        <h1 class="text-4xl font-light text-[var(--trae-text-active)] mb-4">PathoCheck</h1>
        <p class="text-lg text-[var(--trae-text)]">病理切片图像质量评估与有效区域筛选平台</p>
        <p class="text-sm mt-8 text-[var(--trae-text-active)]">请从左上角菜单开始导入数据或选择模型</p>
      </div>
    </div>

    <!-- Modals -->
    <Explorer 
      ref="explorerRef"
      v-model:visible="explorerVisible" 
      v-model:selectedDataset="currentDataset"
      v-model:selectedModel="currentModel"
      @image-selected="handleImageSelected"
    />

    <AssessmentDialog 
      v-model:visible="assessmentDialogVisible"
      :image="currentImage"
      :model="currentModel"
      :preview-url="currentImagePreviewUrl"
      @assessment-complete="handleAssessmentComplete"
    />

    <ReportDialog 
      v-model:visible="reportDialogVisible"
      :report="currentReport"
    />
  </div>
</template>

<style scoped>
</style>
