<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { ImageInfo, Model } from '../types'
import { startAssessment } from '../api'

const props = defineProps<{
  visible: boolean;
  image: ImageInfo | null;
  model: Model | null;
  previewUrl?: string | null;
}>()

const emit = defineEmits(['update:visible', 'assessment-complete'])

const loading = ref(false)

const close = () => {
  if (!loading.value) {
    emit('update:visible', false)
  }
}

const handleStart = async () => {
  if (!props.model || !props.image) return
  loading.value = true
  try {
    const report = await startAssessment(props.model.id, props.image.imageId)
    emit('assessment-complete', report)
  } catch (e: any) {
    ElMessage.error('Assessment failed: ' + e.message)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <el-dialog
    :model-value="visible"
    @update:model-value="close"
    title="病理图像预览"
    width="500px"
    destroy-on-close
  >
    <div v-if="image" v-loading="loading" element-loading-text="Assessing Image..." class="space-y-4">
      <div class="bg-[var(--trae-bg)] p-4 rounded border border-[var(--trae-border)] flex justify-center items-center h-48 overflow-hidden">
        <img v-if="previewUrl" :src="previewUrl" class="max-h-full max-w-full object-contain" />
        <div v-else class="text-gray-500">
          [ 图像预览不可用 ]
        </div>
      </div>
      
      <el-descriptions :column="1" border size="small">
        <el-descriptions-item label="File Name">{{ image.imageName }}</el-descriptions-item>
        <el-descriptions-item label="File Format">{{ image.fileFormat }}</el-descriptions-item>
        <el-descriptions-item label="Image Width">{{ image.width }} px</el-descriptions-item>
        <el-descriptions-item label="Image Height">{{ image.height }} px</el-descriptions-item>
        <el-descriptions-item label="Image Size">{{ (image.fileSize / 1024).toFixed(2) }} KB</el-descriptions-item>
        <el-descriptions-item label="Color Mode">{{ image.colorMode }}</el-descriptions-item>
        <el-descriptions-item label="Selected Model">
          <span :class="model ? 'text-green-500' : 'text-red-500'">
            {{ model ? model.modelName : '未选择模型' }}
          </span>
        </el-descriptions-item>
      </el-descriptions>
    </div>
    
    <template #footer>
      <div class="flex justify-end space-x-2">
        <el-button @click="close" :disabled="loading">关闭</el-button>
        <el-button type="primary" @click="handleStart" :disabled="!model || loading">
          开始评估 (Start Assessment)
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>
