<script setup>
import { h, onMounted, ref } from 'vue'
import { NButton, NCard, NDataTable, NTag, NSpace, useMessage } from 'naive-ui'
import { fetchOperationLogs } from '@/api/operationLogs'
import { useDataTable } from '@/composables/useDataTable'

const message = useMessage()

const { loading, records, total, query, loadData } = useDataTable(
  fetchOperationLogs,
  {},
  { pageSize: 20 },
)

function getStatusType(s) {
  if (s === '成功') return 'success'
  if (s === '失败') return 'error'
  return 'info'
}

function formatTime(v) {
  if (!v) return ''
  return String(v).replace('T', ' ').substring(0, 19)
}

const columns = [
  { title: '时间', key: 'createTime', width: 170,
    render: (row) => formatTime(row.createTime) },
  { title: '操作人', key: 'operator', width: 100 },
  { title: '方法', key: 'httpMethod', width: 70, align: 'center' },
  { title: '接口路径', key: 'apiPath', width: 200, ellipsis: { tooltip: true } },
  { title: '操作类型', key: 'operationType', width: 80, align: 'center' },
  { title: '目标', key: 'target', width: 220, ellipsis: { tooltip: true } },
  { title: '状态', key: 'status', width: 80, align: 'center',
    render(row) {
      return h(NTag, { size: 'small', type: getStatusType(row.status), bordered: false },
        { default: () => row.status })
    },
  },
  { title: '总数', key: 'totalCount', width: 70, align: 'center' },
  { title: '成功数', key: 'successCount', width: 80, align: 'center' },
  { title: '失败数', key: 'failCount', width: 80, align: 'center' },
  { title: 'IP', key: 'ipAddress', width: 120 },
  { title: '错误信息', key: 'errorMessage', width: 280, ellipsis: { tooltip: true },
    render: (row) => row.errorMessage || '—' },
]
</script>

<template>
  <div class="dashboard-page">
    <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:4px">
      <h2 class="users-title" style="margin:0">操作日志</h2>
      <NButton @click="loadData" :loading="loading">刷新</NButton>
    </div>

    <NCard size="small">
      <template #header-extra>
        <NTag size="small" :bordered="false">共 {{ total }} 条</NTag>
      </template>
      <NDataTable
        remote
        :loading="loading"
        :columns="columns"
        :data="records"
        :row-key="(row) => row.id"
        :pagination="{
          page: query.page,
          pageSize: query.size,
          itemCount: total,
          showSizePicker: true,
          pageSizes: [10, 20, 50],
          onUpdatePage: (p) => { query.page = p; loadData() },
          onUpdatePageSize: (s) => { query.size = s; query.page = 1; loadData() },
        }"
      />
    </NCard>
  </div>
</template>
