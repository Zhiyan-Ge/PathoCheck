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
    title="病理评估报告 (Assessment Report)"
    width="700px"
    destroy-on-close
  >
    <div v-if="report" class="space-y-6">
      <div class="grid grid-cols-2 gap-4">
        <div>
          <h4 class="font-bold text-[var(--trae-text-active)] mb-2">基本信息</h4>
          <div class="text-sm space-y-1">
            <p><span class="opacity-60">报告 ID:</span> {{ report.reportId }}</p>
            <p><span class="opacity-60">切片名称:</span> {{ report.imageName }}</p>
            <p><span class="opacity-60">评估模型:</span> {{ report.modelName }}</p>
            <p><span class="opacity-60">评估时间:</span> {{ new Date(report.assessmentTime).toLocaleString() }}</p>
          </div>
        </div>
        <div>
          <h4 class="font-bold text-[var(--trae-text-active)] mb-2">总体结论</h4>
          <div class="text-sm space-y-1">
            <p class="text-xl font-black" :class="{
              'text-green-500': report.finalStatus === 'PASS',
              'text-yellow-500': report.finalStatus === 'WARNING',
              'text-red-500': report.finalStatus === 'REJECT'
            }">
              {{ report.finalStatus }}
            </p>
            <p class="mt-1">综合质量评分: <span class="text-white font-bold">{{ report.qualityScore.toFixed(2) }}</span></p>
          </div>
        </div>
      </div>

      <el-divider />

      <div class="grid grid-cols-2 gap-4">
        <div>
          <h4 class="font-bold text-[var(--trae-text-active)] mb-2">模型预测结果</h4>
          <div class="text-sm space-y-1">
            <p>预测标签: <span class="font-bold text-[var(--trae-accent)]">{{ report.predictedLabel }}</span></p>
            <p>有效区域概率: {{ (report.validProbability * 100).toFixed(2) }}%</p>
            <p>无效背景概率: {{ (report.invalidProbability * 100).toFixed(2) }}%</p>
          </div>
        </div>
        <div>
          <h4 class="font-bold text-[var(--trae-text-active)] mb-2">物理质量指标</h4>
          <div class="text-sm grid grid-cols-2 gap-x-2 gap-y-1">
            <p>平均亮度: {{ report.meanBrightness.toFixed(1) }}</p>
            <p>图像信息熵: {{ report.entropy.toFixed(2) }}</p>
            <p>组织占比: {{ (report.tissueRatio * 100).toFixed(1) }}%</p>
            <p>边缘清晰度: {{ report.sharpness.toFixed(1) }}</p>
            <p>过曝占比: {{ (report.whitePixelRatio * 100).toFixed(1) }}%</p>
            <p>欠曝占比: {{ (report.darkPixelRatio * 100).toFixed(1) }}%</p>
          </div>
        </div>
      </div>

      <el-divider />

      <div>
        <h4 class="font-bold text-[var(--trae-text-active)] mb-2">系统警告 (Warnings)</h4>
        <ul v-if="report.warningMessages && report.warningMessages.length > 0" class="list-disc pl-5 text-sm text-yellow-500 space-y-1">
          <li v-for="(msg, i) in report.warningMessages" :key="i">{{ msg }}</li>
        </ul>
        <p v-else class="text-sm text-green-500">未检测到显著的图像质量缺陷。</p>
      </div>

      <div>
        <h4 class="font-bold text-[var(--trae-text-active)] mb-2">诊断建议</h4>
        <div class="text-sm bg-white/5 p-3 rounded italic text-gray-300">
          {{ report.suggestion }}
        </div>
      </div>

    </div>

    <template #footer>
      <div class="flex justify-end space-x-2">
        <el-button @click="close">关闭</el-button>
        <el-button type="primary" @click="handleExport">导出 PDF 报告</el-button>
      </div>
    </template>
  </el-dialog>
</template>
