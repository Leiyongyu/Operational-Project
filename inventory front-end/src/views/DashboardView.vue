<script setup>
import { computed, h, onMounted, reactive, ref } from 'vue'
import {
  NButton,
  NCard,
  NCheckbox,
  NDataTable,
  NDropdown,
  NForm,
  NFormItem,
  NInput,
  NSelect,
  NSpace,
  NTag,
  NIcon,
  useMessage,
} from 'naive-ui'
import { fetchInventoryOverview, fetchInventoryOverviewWarehouses, refreshSnapshot } from '@/api/inventoryOverview'
import { syncAll } from '@/api/sync'
import { uploadEbaySales } from '@/api/ebaySales'
import { useAuthStore } from '@/stores/auth'

const message = useMessage()
const authStore = useAuthStore()
const isAdmin = computed(() => authStore.isAdmin)

const loading = ref(false)
const syncing = ref(false)
const uploading = ref(false)
const recalculating = ref(false)
const importExportLoading = ref(false)
const warehouseLoading = ref(false)
let loadSeq = 0

const replenishRows = ref([])
const warehouseOptions = ref([])
const filters = reactive({
  warehouseNames: [],
  sku: '',
})

function formatDateTime(value) {
  const date = value instanceof Date ? value : new Date(value)
  const pad = (n) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(
    date.getHours(),
  )}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

const updatedAt = ref('')

// KPI 汇总
const kpiCards = computed(() => {
  const rows = replenishRows.value
  if (!rows.length) return []
  const totalSku = rows.length
  const totalInventory = rows.reduce((sum, r) => sum + (Number(r.totalInventory) || 0), 0)
  const totalOverseasSellable = rows.reduce((sum, r) => sum + (Number(r.overseasSellable) || 0), 0)
  const totalSales30 = rows.reduce((sum, r) => sum + (Number(r.last30DaysSales) || 0), 0)
  return [
    { label: 'SKU 总数', value: totalSku.toLocaleString(), color: '#1677ff', bg: '#e6f4ff' },
    { label: '海外可售库存', value: totalOverseasSellable.toLocaleString(), color: '#52c41a', bg: '#f6ffed' },
    { label: '总库存量', value: totalInventory.toLocaleString(), color: '#722ed1', bg: '#f9f0ff' },
    { label: '近30天总销量', value: totalSales30.toLocaleString(), color: '#fa8c16', bg: '#fff7e6' },
  ]
})

const filteredRows = computed(() => replenishRows.value)

function renderRatioTag(value) {
  if (value === undefined || value === null || value === '') return ''
  const num = Number(value)
  if (!Number.isFinite(num)) return ''
  const display = num.toFixed(1).replace(/\.0$/, '') + '%'
  const type = num > 6 ? 'warning' : num > 3 ? 'default' : 'success'
  return h(NTag, { size: 'small', type, bordered: false }, { default: () => display })
}

const replenishColumns = [
  { title: '站点', key: 'warehouseNames', width: 180, ellipsis: true, fixed: 'left' },
  { title: 'SKU', key: 'sku', width: 140, ellipsis: true, fixed: 'left' },
  { title: '产品名称', key: 'productName', width: 160, ellipsis: true, fixed: 'left' },
  { title: '近30利润', key: 'last30DaysProfit', width: 100,
    render: (row) => row.last30DaysProfit != null ? Number(row.last30DaysProfit).toFixed(1) + '%' : '' },
  { title: '退货率', key: 'returnRate', width: 90,
    render: (row) => row.returnRate ?? '' },
  { title: '海外在途', key: 'overseasOnway', width: 90,
    render: (row) => row.overseasOnway ?? '' },
  { title: '海外可售', key: 'overseasSellable', width: 90,
    render: (row) => row.overseasSellable ?? '' },
  { title: '海外总库存', key: 'overseasTotal', width: 100,
    render: (row) => row.overseasTotal ?? '' },
  { title: '采购待交付', key: 'purchasePendingDelivery', width: 100,
    render: (row) => row.purchasePendingDelivery ?? '' },
  { title: '成都可售', key: 'localSellable', width: 90,
    render: (row) => row.localSellable ?? '' },
  { title: '成都在途', key: 'localOnway', width: 90,
    render: (row) => row.localOnway ?? '' },
  { title: '采购计划', key: 'purchasePlan', width: 110, ellipsis: true,
    render: (row) => row.purchasePlan ?? '' },
  { title: '待出库', key: 'lockNum', width: 80,
    render: (row) => row.lockNum ?? '' },
  { title: '总库存', key: 'totalInventory', width: 90,
    render: (row) => row.totalInventory ?? '' },
  { title: '近7天销量', key: 'last7DaysSales', width: 90,
    render: (row) => row.last7DaysSales ?? '' },
  { title: '近30天销量', key: 'last30DaysSales', width: 100,
    render: (row) => row.last30DaysSales ?? '' },
  { title: '近3月均销量', key: 'last90DaysSales', width: 100,
    render: (row) => {
      if (row.last90DaysSales == null || row.last90DaysSales === '') return ''
      const v = Number(row.last90DaysSales)
      if (!Number.isFinite(v)) return ''
      const avg = v / 3
      return Number.isInteger(avg) ? String(avg) : avg.toFixed(2).replace(/\.?0+$/, '')
    } },
  { title: '历史最大月销', key: 'maxMonthlySales', width: 120,
    render: (row) => row.maxMonthlySales ?? '' },
  { title: '海外在库库销比', key: 'overseasInStockRatio', width: 120,
    render: (row) => renderRatioTag(row.overseasInStockRatio) },
  { title: '海外总库销比', key: 'overseasTotalRatio', width: 120,
    render: (row) => renderRatioTag(row.overseasTotalRatio) },
  { title: '总库存库销比', key: 'totalInventoryRatio', width: 120,
    render: (row) => renderRatioTag(row.totalInventoryRatio) },
  { title: '最近成都出库', key: 'lastLocalOutboundTime', width: 160, ellipsis: true,
    render: (row) => row.lastLocalOutboundTime ?? '' },
  { title: '出库天数', key: 'outboundDays', width: 100,
    render: (row) => row.outboundDays ?? '' },
  { title: '采购周期', key: 'purchaseCycle', width: 120,
    render: (row) => row.purchaseCycle ?? '' },
  { title: '采购数量', key: 'purchaseQuantity', width: 100,
    render: (row) => row.purchaseQuantity ?? '' },
  { title: '最大月销补货量', key: 'maxMonthlyReplenish', width: 130,
    render: (row) => row.maxMonthlyReplenish ?? '' },
  { title: '负责人', key: 'owner', width: 100,
    render: (row) => row.owner ?? '' },
].map((c) => ({ ...c, resizable: true, minWidth: 70 }))

const replenishScrollX = replenishColumns.reduce((s, c) => s + (Number(c?.width) || 100), 0)
const replenishMaxHeight = 400

async function loadWarehouseOptions() {
  warehouseLoading.value = true
  try {
    const list = await fetchInventoryOverviewWarehouses()
    warehouseOptions.value = Array.isArray(list)
      ? list.map((item) => {
          const label = item?.label ? String(item.label) : ''
          if (!label) return null
          return { label, value: label }
        }).filter(Boolean)
      : []
    filters.warehouseNames = []
  } catch (error) {
    message.error(error instanceof Error ? error.message : '加载仓库下拉失败')
    warehouseOptions.value = []
    filters.warehouseNames = []
  } finally {
    warehouseLoading.value = false
  }
}

async function loadInventoryOverview() {
  loading.value = true
  const seq = ++loadSeq
  const selected = Array.isArray(filters.warehouseNames) ? filters.warehouseNames : []
  const optionCount = warehouseOptions.value.length
  const warehouseParam = selected.length > 0 && selected.length < optionCount ? selected.join(',') : undefined

  try {
    const list = await fetchInventoryOverview({
      sku: filters.sku.trim() || undefined,
      warehouse: warehouseParam,
    })
    if (seq !== loadSeq) return
    replenishRows.value = list?.records || (Array.isArray(list) ? list : [])
    updatedAt.value = formatDateTime(new Date())
  } catch (error) {
    if (seq !== loadSeq) return
    message.error(error instanceof Error ? error.message : '加载运营组数据失败')
  } finally {
    if (seq === loadSeq) loading.value = false
  }
}

onMounted(() => {
  loadWarehouseOptions()
  loadInventoryOverview()
})

async function handleRecalc() {
  recalculating.value = true
  try {
    await refreshSnapshot()
    await loadInventoryOverview()
    message.success('重算完成')
  } catch (e) {
    message.error(e instanceof Error ? e.message : '重算失败')
  } finally {
    recalculating.value = false
  }
}

async function handleSyncAll() {
  syncing.value = true
  try {
    await syncAll()
    message.success('全量同步完成，刷新页面查看最新数据')
    await loadInventoryOverview()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '同步失败')
  } finally {
    syncing.value = false
  }
}

async function handleUploadExcel() {
  const input = document.createElement('input')
  input.type = 'file'
  input.accept = '.xlsx,.xls'
  input.onchange = async (e) => {
    const file = e.target.files[0]
    if (!file) return
    importExportLoading.value = true
    try {
      const res = await uploadEbaySales(file)
      message.success(`导入成功，新增${res.inserted}条，更新${res.updated}条`)
    } catch (err) {
      message.error(err instanceof Error ? err.message : '导入失败')
    } finally {
      importExportLoading.value = false
    }
  }
  input.click()
}

const importExportOptions = [
  { label: '导入销量报表', key: 'uploadSales' },
  { label: '导入利润率', key: 'uploadProfitRate' },
  { label: '导入退货率', key: 'uploadReturnRate' },
]

function handleUploadProfitRate() {
  const input = document.createElement('input')
  input.type = 'file'; input.accept = '.xlsx,.xls'
  input.onchange = async (e) => {
    const file = e.target.files[0]
    if (!file) return
    importExportLoading.value = true
    try {
      const form = new FormData(); form.append('file', file)
      const resp = await fetch('/api/goodcang/import-profit-rate', { method: 'POST', body: form })
      const data = await resp.json()
      message.success('导入完成：共' + data.total + '条，更新' + data.updated + '，跳过' + data.skipped)
    } catch (err) { message.error('导入失败') }
    finally { importExportLoading.value = false }
  }
  input.click()
}

function handleUploadReturnRate() {
  const input = document.createElement('input')
  input.type = 'file'; input.accept = '.xlsx,.xls'
  input.onchange = async (e) => {
    const file = e.target.files[0]
    if (!file) return
    importExportLoading.value = true
    try {
      const form = new FormData(); form.append('file', file)
      const resp = await fetch('/api/goodcang/import-return-rate', { method: 'POST', body: form })
      const data = await resp.json()
      message.success('导入完成：共' + data.total + '条，更新' + data.updated + '，跳过' + data.skipped)
    } catch (err) { message.error('导入失败') }
    finally { importExportLoading.value = false }
  }
  input.click()
}

function handleDropdownSelect(key) {
  if (key === 'uploadSales') handleUploadExcel()
  else if (key === 'uploadProfitRate') handleUploadProfitRate()
  else if (key === 'uploadReturnRate') handleUploadReturnRate()
}

function handleReset() {
  filters.warehouseNames = []
  filters.sku = ''
  loadInventoryOverview()
}

function renderWarehouseOption({ node, option, selected }) {
  return h('div', {
    style: { display: 'flex', alignItems: 'center', gap: '8px', width: '100%' },
    onClick: node?.props?.onClick,
    onMouseenter: node?.props?.onMouseenter,
    onMousemove: node?.props?.onMousemove,
  }, [
    h(NCheckbox, { checked: selected, style: { pointerEvents: 'none' } }),
    h('span', { style: { flex: 1, minWidth: 0 } }, option.label || option.value),
  ])
}
</script>

<template>
  <div class="dashboard-page">
    <!-- ===== KPI 数据卡片 ===== -->
    <div class="kpi-grid">
      <div
        v-for="(card, idx) in kpiCards"
        :key="card.label"
        class="kpi-card"
        :style="{ animationDelay: idx * 0.1 + 's' }"
      >
        <div class="kpi-icon" :style="{ background: card.bg, color: card.color }">
          <svg v-if="idx === 0" viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2"><path d="M4 20h16M4 20V4m0 16 4-4v4m4 0V10m0 10 4-8v8m4 0V6m0 14 4-12v12"/></svg>
          <svg v-else-if="idx === 1" viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2"><rect x="1" y="3" width="15" height="13"/><polygon points="16 8 20 8 23 11 23 16 16 16 16 8"/><circle cx="5.5" cy="18.5" r="2.5"/><circle cx="18.5" cy="18.5" r="2.5"/></svg>
          <svg v-else-if="idx === 2" viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"/><polyline points="3.27 6.96 12 12.01 20.73 6.96"/><line x1="12" y1="22.08" x2="12" y2="12"/></svg>
          <svg v-else viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2"><polyline points="22 12 18 12 15 21 9 3 6 12 2 12"/></svg>
        </div>
        <div class="kpi-info">
          <span class="kpi-value animate-count">{{ card.value }}</span>
          <span class="kpi-label">{{ card.label }}</span>
        </div>
      </div>
    </div>

    <!-- ===== 主表格卡片 ===== -->
    <div class="table-card-wrap">
    <NCard title="库存总览" size="large">
      <template #header-extra>
        <NSpace align="center" size="small">
          <NTag type="info" :bordered="false" size="small">更新 {{ updatedAt || '-' }}</NTag>
          <NTag size="small" :bordered="false" type="default">共 {{ filteredRows.length }} 条</NTag>
          <NButton size="small" secondary :loading="loading" @click="loadInventoryOverview">
            <template #icon>
              <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2"><polyline points="23 4 23 10 17 10"/><path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"/></svg>
            </template>
            刷新
          </NButton>
          <NButton size="small" type="info" :loading="recalculating" @click="handleRecalc">
            重算数据
          </NButton>
          <NButton v-if="isAdmin" size="small" type="warning" :loading="syncing" @click="handleSyncAll">
            拉取最新数据
          </NButton>
          <NDropdown trigger="click" :options="importExportOptions" @select="handleDropdownSelect">
            <NButton size="small" type="info" :loading="importExportLoading">导入导出</NButton>
          </NDropdown>
        </NSpace>
      </template>

      <!-- 筛选区 -->
      <NForm inline :model="filters" class="filter-form">
        <NFormItem label="仓库">
          <NSelect
            v-model:value="filters.warehouseNames"
            multiple
            filterable
            clearable
            placeholder="选择仓库"
            :options="warehouseOptions"
            :loading="warehouseLoading"
            :render-option="renderWarehouseOption"
            :max-tag-count="1"
            style="width: 180px"
          />
        </NFormItem>
        <NFormItem label="SKU">
          <NInput
            v-model:value="filters.sku"
            clearable
            placeholder="输入 SKU"
            style="width: 220px"
            @keyup.enter="loadInventoryOverview"
          />
        </NFormItem>
        <NFormItem>
          <NSpace size="small">
            <NButton type="primary" secondary :loading="loading" @click="loadInventoryOverview">
              查询
            </NButton>
            <NButton :disabled="loading" @click="handleReset">重置</NButton>
          </NSpace>
        </NFormItem>
      </NForm>

      <NDataTable
        :loading="loading"
        :columns="replenishColumns"
        :data="filteredRows"
        :bordered="false"
        :scroll-x="replenishScrollX"
        :max-height="replenishMaxHeight"
        :row-key="(row) => `${String(row?.warehouseNames ?? '').trim()}|${String(row?.sku ?? '').trim()}`"
        :pagination="{ pageSize: 100 }"
        striped
      />
    </NCard>
    </div>
  </div>
</template>

<style scoped>
/* ===== 页面容器：flex 纵向布局 ===== */
.dashboard-page {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

/* ===== KPI 卡片网格（不伸缩） ===== */
.kpi-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8px;
  flex-shrink: 0;
}

.kpi-card {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 14px;
  border-radius: 8px;
  background: #fff;
  border: 1px solid rgba(0, 0, 0, 0.04);
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.02);
  transition: transform 0.25s ease, box-shadow 0.25s ease;
  cursor: default;
}

.kpi-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.06);
}

.kpi-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  min-width: 36px;
  border-radius: 8px;
  transition: transform 0.3s ease;
}

.kpi-card:hover .kpi-icon { transform: scale(1.08); }

.kpi-info { display: flex; flex-direction: column; gap: 1px; }
.kpi-value { font-size: 20px; font-weight: 700; color: #1a1a2e; line-height: 1.2; }
.kpi-label { font-size: 11px; color: #999; }

/* ===== 表格卡片：填充剩余空间 ===== */
.table-card-wrap {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.table-card-wrap :deep(.n-card) {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.table-card-wrap :deep(.n-card__content) {
  flex: 1;
  min-height: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  padding: 8px 16px 12px;
}

.table-card-wrap :deep(.n-card-header) {
  padding: 10px 16px 0;
  font-weight: 600;
  font-size: 15px;
  flex-shrink: 0;
}

/* filter form: 不伸缩 */
.table-card-wrap :deep(.filter-form) {
  flex-shrink: 0;
  margin-bottom: 0;
}

/* 表格容器：填充剩余空间 + 内部滚动 */
.table-card-wrap :deep(.n-data-table) {
  flex: 1;
  min-height: 0;
}

/* 让表格 body 可滚动（覆盖组件 max-height） */
.table-card-wrap :deep(.n-data-table .n-data-table-base-table-body) {
  overflow-y: auto !important;
}

/* ===== 通用卡片 ===== */
.dashboard-card {
  border-radius: 10px;
  overflow: hidden;
}

.dashboard-card :deep(.n-card-header) {
  padding: 10px 16px 0;
  font-weight: 600;
  font-size: 15px;
}

.dashboard-card :deep(.n-card__content) {
  padding: 8px 16px 12px;
}

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

.filter-form { margin-bottom: 0; }
.filter-form :deep(.n-form-item) { margin-bottom: 0; }

@media (max-width: 1200px) { .kpi-grid { grid-template-columns: repeat(2, 1fr); } }
@media (max-width: 640px) { .kpi-grid { grid-template-columns: 1fr; } }
</style>
