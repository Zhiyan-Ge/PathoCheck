<script setup lang="ts">
import type { AssessmentReport } from '../types'
import { exportPdf } from '../api'

const props = defineProps<{
  visible: boolean;
  report: AssessmentReport | null;
}>()

const emit = defineEmits(['update:visible'])

const close = () => {
  emit('update:visible', false)
}

const handleExport = () => {
  if (props.report) {
    exportPdf(props.report.reportId)
  }
}
</script>

<template>
  <el-dialog
    :model-value="visible"
    @update:model-value="close"
    title="评估报告"
    width="700px"
    destroy-on-close
  >
    <div v-if="report" class="space-y-6">
      
      <div class="grid grid-cols-2 gap-4">
        <div>
          <h4 class="font-bold text-[var(--trae-text-active)] mb-2">基本信息</h4>
          <div class="text-sm space-y-1">
            <p>Report ID: {{ report.reportId }}</p>
            <p>Image Name: {{ report.imageName }}</p>
            <p>Model Name: {{ report.modelName }}</p>
            <p>Time: {{ new Date(report.assessmentTime).toLocaleString() }}</p>
          </div>
        </div>
        <div>
          <h4 class="font-bold text-[var(--trae-text-active)] mb-2">总体结果</h4>
          <div class="text-sm space-y-1">
            <p class="text-lg font-bold" :class="{
              'text-green-500': report.finalStatus === 'PASS',
              'text-yellow-500': report.finalStatus === 'WARNING',
              'text-red-500': report.finalStatus === 'REJECT'
            }">
              {{ report.finalStatus }}
            </p>
            <p>Quality Score: {{ report.qualityScore.toFixed(2) }}</p>
          </div>
        </div>
      </div>

      <el-divider />

      <div class="grid grid-cols-2 gap-4">
        <div>
          <h4 class="font-bold text-[var(--trae-text-active)] mb-2">模型预测</h4>
          <div class="text-sm space-y-1">
            <p>Label: <span class="font-medium text-[var(--trae-accent)]">{{ report.predictedLabel }}</span></p>
            <p>Valid Prob: {{ (report.validProbability * 100).toFixed(2) }}%</p>
            <p>Invalid Prob: {{ (report.invalidProbability * 100).toFixed(2) }}%</p>
          </div>
        </div>
        <div>
          <h4 class="font-bold text-[var(--trae-text-active)] mb-2">质量指标</h4>
          <div class="text-sm grid grid-cols-2 gap-x-2 gap-y-1">
            <p>Brightness: {{ report.meanBrightness.toFixed(1) }}</p>
            <p>Entropy: {{ report.entropy.toFixed(2) }}</p>
            <p>Tissue Ratio: {{ (report.tissueRatio * 100).toFixed(1) }}%</p>
            <p>Sharpness: {{ report.sharpness.toFixed(1) }}</p>
            <p>White Ratio: {{ (report.whitePixelRatio * 100).toFixed(1) }}%</p>
            <p>Dark Ratio: {{ (report.darkPixelRatio * 100).toFixed(1) }}%</p>
          </div>
        </div>
      </div>

      <el-divider />

      <div>
        <h4 class="font-bold text-[var(--trae-text-active)] mb-2">问题提示</h4>
        <ul v-if="report.warningMessages && report.warningMessages.length > 0" class="list-disc pl-5 text-sm text-yellow-500 space-y-1">
          <li v-for="(msg, i) in report.warningMessages" :key="i">{{ msg }}</li>
        </ul>
        <p v-else class="text-sm text-green-500">No significant quality problem detected.</p>
      </div>

      <div>
        <h4 class="font-bold text-[var(--trae-text-active)] mb-2">建议</h4>
        <p class="text-sm">{{ report.suggestion }}</p>
      </div>

    </div>

    <template #footer>
      <div class="flex justify-end space-x-2">
        <el-button @click="close">关闭</el-button>
        <el-button type="primary" @click="handleExport">保存为 PDF</el-button>
      </div>
    </template>
  </el-dialog>
</template>
