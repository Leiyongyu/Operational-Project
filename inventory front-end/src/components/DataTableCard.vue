<script setup>
import { NCard, NDataTable, NEmpty, NSpin, NAlert, NSpace, NButton, NTag, NIcon } from 'naive-ui'
import { h } from 'vue'

/**
 * 通用数据表格卡片组件：封装 loading / error / empty 状态，
 * 避免每个页面重复写状态处理逻辑。
 *
 * Props:
 * - title: 卡片标题
 * - columns: NDataTable 列配置
 * - data: 数据数组
 * - loading: 是否加载中
 * - error: 错误信息（字符串或 null）
 * - rowKey: 行唯一键生成函数
 * - scrollX: 水平滚动宽度
 * - maxHeight: 最大高度
 * - pagination: 分页配置（默认每页100条）
 * - bordered / striped: 表格样式
 * - extra: 卡片头部右侧插槽内容
 * - count: 总数标签文本
 * - updatedAt: 更新时间标签文本
 */
const props = defineProps({
  title: { type: String, default: '数据列表' },
  columns: { type: Array, required: true },
  data: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  error: { type: [String, Object], default: null },
  rowKey: { type: [Function, String], default: undefined },
  scrollX: { type: Number, default: undefined },
  maxHeight: { type: Number, default: 400 },
  pagination: { type: [Object, Boolean], default: () => ({ pageSize: 100 }) },
  bordered: { type: Boolean, default: false },
  striped: { type: Boolean, default: true },
  count: { type: [Number, String], default: undefined },
  updatedAt: { type: String, default: '' },
})

const emit = defineEmits(['refresh'])

const hasError = () => !!props.error
const isEmpty = () => !props.loading && !hasError() && props.data.length === 0
</script>

<template>
  <NCard :title="title" size="large">
    <template #header-extra>
      <NSpace align="center" size="small">
        <NTag v-if="updatedAt" type="info" :bordered="false" size="small">更新 {{ updatedAt }}</NTag>
        <NTag v-if="count !== undefined" size="small" :bordered="false" type="default">
          共 {{ count !== '' ? count : data.length }} 条
        </NTag>
        <NButton size="small" secondary @click="emit('refresh')">
          <template #icon>
            <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="23 4 23 10 17 10" />
              <path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10" />
            </svg>
          </template>
          刷新
        </NButton>
        <slot name="extra" />
      </NSpace>
    </template>

    <!-- 加载中 -->
    <NSpin :show="loading" size="large">
      <template #description>加载中...</template>
      <div style="min-height: 120px">
        <!-- 错误状态 -->
        <NAlert v-if="hasError()" type="error" :title="String(error)" style="margin-bottom: 12px" />
        <!-- 空状态 -->
        <NEmpty v-else-if="isEmpty()" description="暂无数据" style="padding: 60px 0" />
        <!-- 正常表格 -->
        <NDataTable
          v-else
          :loading="loading"
          :columns="columns"
          :data="data"
          :bordered="bordered"
          :scroll-x="scrollX"
          :max-height="maxHeight"
          :row-key="rowKey"
          :pagination="pagination"
          :striped="striped"
        />
      </div>
    </NSpin>
  </NCard>
</template>

<style scoped>
:deep(.n-data-table-th) {
  background: #fafafa;
  font-weight: 600;
  font-size: 12px;
  color: rgba(0, 0, 0, 0.55);
  text-align: center !important;
}

:deep(.n-data-table-td) {
  font-size: 13px;
  border-bottom: 1px solid #f5f5f5;
  text-align: center !important;
}
</style>
