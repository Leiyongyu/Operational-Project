<script setup>
import { computed, h, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import {
  NButton,
  NCard,
  NCheckbox,
  NDataTable,
  NDrawer,
  NDrawerContent,
  NDropdown,
  NInput,
  NPopover,
  NSpace,
  NTag,
  useMessage,
} from 'naive-ui'
import { searchInventoryOverview, fetchDistinctValues, refreshSnapshot } from '@/api/inventoryOverview'
import { syncAll } from '@/api/sync'
import { uploadEbaySales } from '@/api/ebaySales'
import { useAuthStore } from '@/stores/auth'
import { useColumnConfig } from '@/composables/useColumnConfig'

const message = useMessage()
const authStore = useAuthStore()
const isAdmin = computed(() => authStore.isAdmin)

const loading = ref(false)
const syncing = ref(false)
const uploading = ref(false)
const importExportLoading = ref(false)
let loadSeq = 0

const replenishRows = ref([])
const totalRecords = ref(0)
const query = reactive({ page: 1, size: 100 })

// ===== 列排序 =====
const sortField = ref('')
const sortOrder = ref('')  // '' | 'asc' | 'desc'
const NUMERIC_FIELDS = new Set([
  'last30DaysProfit', 'returnRate', 'overseasOnway', 'overseasSellable',
  'overseasTotal', 'purchasePendingDelivery', 'localSellable', 'localOnway',
  'lockNum', 'totalInventory', 'last7DaysSales', 'last30DaysSales',
  'last90DaysSales', 'maxMonthlySales', 'overseasInStockRatio', 'overseasTotalRatio',
  'totalInventoryRatio', 'outboundDays', 'purchaseCycle', 'purchaseQuantity',
  'maxMonthlyReplenish', 'purchasePlan',
])

// ===== 列筛选 =====
const filterField = ref('')
const activeFilters = ref([])  // [{ field, value, display }] 多条件联动
const filterInputVal = ref('')
const filterNumOpVal = ref('>')
const filterNumInputVal = ref('')
const filterChecked = ref([])  // 多选值
const filterSearchResults = ref([])
const filterRawRef = ref(null)   // 原生输入框 DOM 引用
let filterTimer = null
const showFilter = ref(false)
const filterX = ref(0)
const filterY = ref(0)
const TEXT_FIELDS = new Set(['warehouseNames', 'sku', 'productName', 'skuLevel', 'lastLocalOutboundTime', 'owner'])

watch(filterInputVal, (val) => {
  if (!showFilter.value || !filterField.value) return
  clearTimeout(filterTimer)
  filterTimer = setTimeout(async () => {
    if (!val || !val.trim()) { filterSearchResults.value = []; return }
    try {
      filterSearchResults.value = await fetchDistinctValues(filterField.value, val.trim()) || []
    } catch { filterSearchResults.value = [] }
  }, 200)
})

const distinctFilterValues = computed(() => {
  if (!filterField.value) return []
  const set = new Set()
  for (const row of replenishRows.value) {
    const v = row[filterField.value]
    if (v != null && String(v).trim()) set.add(String(v).trim())
  }
  return [...set].sort().slice(0, 50)
})

function openFilter(key, e) {
  filterField.value = key
  filterInputVal.value = ''
  filterNumOpVal.value = '>'
  filterNumInputVal.value = ''
  filterSearchResults.value = []
  const exist = activeFilters.value.find(f => f.field === key)
  filterChecked.value = exist && !NUMERIC_FIELDS.has(key)
    ? exist.value.split(',').filter(Boolean) : []
  // 回显已有的筛选值
  if (exist) {
    if (NUMERIC_FIELDS.has(key)) {
      const m = exist.value.match(/^(>=|<=|>|<|=)(.+)$/)
      if (m) { filterNumOpVal.value = m[1]; filterNumInputVal.value = m[2] }
    } else {
      filterInputVal.value = exist.value
    }
  }
  const rect = e.currentTarget.getBoundingClientRect()
  filterX.value = rect.left
  filterY.value = rect.bottom + 4
  showFilter.value = true
}
function toggleFilterCheck(val) {
  const idx = filterChecked.value.indexOf(val)
  if (idx >= 0) filterChecked.value.splice(idx, 1)
  else filterChecked.value.push(val)
}
function applyFilter() {
  const field = filterField.value
  const rawVal = NUMERIC_FIELDS.has(field)
    ? (filterNumInputVal.value ? filterNumOpVal.value + filterNumInputVal.value.trim() : '')
    : filterInputVal.value
  const val = filterChecked.value.length ? filterChecked.value.join(',') : (rawVal && rawVal.trim() ? rawVal.trim() : '')
  // 更新或移除
  activeFilters.value = activeFilters.value.filter(f => f.field !== field)
  if (val) {
    const title = allColumnMap[field] || field
    activeFilters.value.push({ field, value: val, display: title + ':' + val })
  }
  showFilter.value = false
  query.page = 1
  loadInventoryOverview()
}
function clearFilter() {
  activeFilters.value = activeFilters.value.filter(f => f.field !== filterField.value)
  filterInputVal.value = ''
  filterNumOpVal.value = '>'
  filterNumInputVal.value = ''
  filterChecked.value = []
  showFilter.value = false
  query.page = 1
  loadInventoryOverview()
}
function clearAllFilters() {
  activeFilters.value = []
  filterField.value = ''
  query.page = 1
  loadInventoryOverview()
}

function handleSortClick(key) {
  if (!NUMERIC_FIELDS.has(key)) return
  if (sortField.value !== key) {
    sortField.value = key; sortOrder.value = 'desc'
  } else if (sortOrder.value === 'desc') {
    sortOrder.value = 'asc'
  } else if (sortOrder.value === 'asc') {
    sortField.value = ''; sortOrder.value = ''
  } else {
    sortField.value = key; sortOrder.value = 'desc'
  }
  query.page = 1
  loadInventoryOverview()
}

function renderFilterIcon(key) {
  const isActive = activeFilters.value.some(f => f.field === key)
  const color = isActive ? '#1677ff' : '#bfbfbf'
  return h('svg', {
    viewBox: '0 0 1024 1024', width: '12', height: '12',
    style: { color, marginLeft: '0', cursor: 'pointer', flexShrink: 0 },
    onClick: (e) => { e.stopPropagation(); openFilter(key, e) },
  }, [h('path', { fill: 'currentColor', d: 'M911.2 128H112.8c-52.8 0-80 58.4-41.6 98.4L384 576v288c0 17.6 14.4 32 32 32h192c17.6 0 32-14.4 32-32V576l312.8-349.6c38.4-40 11.2-98.4-41.6-98.4z' })])
}

function renderSortIcon(key) {
  if (!NUMERIC_FIELDS.has(key)) return ''
  const isActive = sortField.value === key
  const color = isActive && sortOrder.value ? '#1677ff' : '#bfbfbf'
  const isAsc = isActive && sortOrder.value === 'asc'
  const triangle = isAsc
    ? 'M863.764 270.458c32.518-35.975 85.239-35.975 117.757 0l401.018 443.652c32.519 35.977 19.57 65.14-28.913 65.14h-861.969c-48.485 0-61.43-29.164-28.913-65.14l401.018-443.652z'
    : 'M855.721 739.856c30.486 33.728 79.913 33.728 110.397 0l375.954-415.921c30.487-33.729 18.347-61.069-27.103-61.069h-808.099c-45.455 0-57.591 27.341-27.106 61.068l375.955 415.923z'
  return h('svg', {
    viewBox: '0 0 1824 1024', width: '12', height: '12',
    style: { color, marginLeft: '0', cursor: 'pointer', flexShrink: 0, verticalAlign: 'middle' },
    onClick: (e) => { e.stopPropagation(); handleSortClick(key) },
  }, [h('path', { fill: 'currentColor', d: triangle })])
}

function formatDateTime(value) {
  const date = value instanceof Date ? value : new Date(value)
  const pad = (n) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(
    date.getHours(),
  )}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

const updatedAt = ref('')

//// KPI 汇总
//const kpiCards = computed(() => {
//  const rows = replenishRows.value
//  if (!rows.length) return []
//  const totalSku = rows.length
//  const totalInventory = rows.reduce((sum, r) => sum + (Number(r.totalInventory) || 0), 0)
//  const totalOverseasSellable = rows.reduce((sum, r) => sum + (Number(r.overseasSellable) || 0), 0)
//  const totalSales30 = rows.reduce((sum, r) => sum + (Number(r.last30DaysSales) || 0), 0)
//  return [
//    { label: 'SKU 总数', value: totalSku.toLocaleString(), color: '#1677ff', bg: '#e6f4ff' },
//    { label: '海外可售库存', value: totalOverseasSellable.toLocaleString(), color: '#52c41a', bg: '#f6ffed' },
//    { label: '总库存量', value: totalInventory.toLocaleString(), color: '#722ed1', bg: '#f9f0ff' },
//    { label: '近30天总销量', value: totalSales30.toLocaleString(), color: '#fa8c16', bg: '#fff7e6' },
//  ]
//})
//
const filteredRows = computed(() => replenishRows.value) // KPI removed

function renderRatioTag(value) {
  if (value === undefined || value === null || value === '') return ''
  const num = Number(value)
  if (!Number.isFinite(num)) return ''
  const display = num.toFixed(1).replace(/\.0$/, '') + '%'
  const type = num > 6 ? 'warning' : num > 3 ? 'default' : 'success'
  return h(NTag, { size: 'small', type, bordered: false }, { default: () => display })
}

const replenishColumns = [
  { type: 'selection', multiple: true, width: 40, fixed: 'left' },
  { title: '站点', key: 'warehouseNames', width: 110, ellipsis: true, fixed: 'left' },
  { title: 'SKU', key: 'sku', width: 150, ellipsis: true, fixed: 'left' },
  { title: '等级', key: 'skuLevel', width: 80, render: (row) => row.skuLevel ?? '' },
  { title: '产品名称', key: 'productName', width: 180, ellipsis: true },
  { title: '近30利润', key: 'last30DaysProfit', width: 130,
    render: (row) => row.last30DaysProfit != null ? Number(row.last30DaysProfit).toFixed(1) + '%' : '' },
  { title: '退货率', key: 'returnRate', width: 110,
    render: (row) => row.returnRate != null ? (Number(row.returnRate) * 100).toFixed(1) + '%' : '' },
  { title: '海外在途', key: 'overseasOnway', width: 120,
    render: (row) => row.overseasOnway ?? '' },
  { title: '海外可售', key: 'overseasSellable', width: 120,
    render: (row) => row.overseasSellable ?? '' },
  { title: '海外总', key: 'overseasTotal', width: 110,
    render: (row) => row.overseasTotal ?? '' },
  { title: '采购待交付', key: 'purchasePendingDelivery', width: 130,
    render: (row) => row.purchasePendingDelivery ?? '' },
  { title: '成都可售', key: 'localSellable', width: 120,
    render: (row) => row.localSellable ?? '' },
  { title: '成都在途', key: 'localOnway', width: 120,
    render: (row) => row.localOnway ?? '' },
  { title: '采购计划', key: 'purchasePlan', width: 130,
    render: (row) => row.purchasePlan != null ? row.purchasePlan : 0 },
  { title: '待出库', key: 'lockNum', width: 110,
    render: (row) => row.lockNum ?? '' },
  { title: '总库存', key: 'totalInventory', width: 110,
    render: (row) => row.totalInventory ?? '' },
  { title: '近7天销量', key: 'last7DaysSales', width: 130,
    render: (row) => row.last7DaysSales ?? '' },
  { title: '近30天销量', key: 'last30DaysSales', width: 140,
    render: (row) => row.last30DaysSales ?? '' },
  { title: '近3月均销量', key: 'last90DaysSales', width: 140,
    render: (row) => {
      if (row.last90DaysSales == null || row.last90DaysSales === '') return ''
      const v = Number(row.last90DaysSales)
      if (!Number.isFinite(v)) return ''
      const avg = v / 3
      return Number.isInteger(avg) ? String(avg) : avg.toFixed(2).replace(/\.?0+$/, '')
    } },
  { title: '历史最大月销', key: 'maxMonthlySales', width: 150,
    render: (row) => row.maxMonthlySales ?? '' },
  { title: '海外在库库销比', key: 'overseasInStockRatio', width: 160,
    render: (row) => renderRatioTag(row.overseasInStockRatio) },
  { title: '海外总库销比', key: 'overseasTotalRatio', width: 150,
    render: (row) => renderRatioTag(row.overseasTotalRatio) },
  { title: '总库存库销比', key: 'totalInventoryRatio', width: 150,
    render: (row) => renderRatioTag(row.totalInventoryRatio) },
  { title: '最近成都出库', key: 'lastLocalOutboundTime', width: 160, ellipsis: true,
    render: (row) => row.lastLocalOutboundTime ?? '' },
  { title: '出库天数', key: 'outboundDays', width: 130,
    render: (row) => row.outboundDays ?? '' },
  { title: '采购周期', key: 'purchaseCycle', width: 130,
    render: (row) => row.purchaseCycle ?? '' },
  { title: '采购数量', key: 'purchaseQuantity', width: 130,
    render: (row) => row.purchaseQuantity ?? '' },
  { title: '最大月销补货量', key: 'maxMonthlyReplenish', width: 170,
    render: (row) => row.maxMonthlyReplenish != null ? row.maxMonthlyReplenish : 0 },
  { title: '负责人', key: 'owner', width: 100,
    render: (row) => row.owner ?? '' },
].map((c) => ({ ...c, resizable: false, minWidth: 70 }))

// ===== 列配置（必须在替换 title 之前，保存原始字符串标题）=====
const allColumnMap = replenishColumns.reduce((m, c) => {
  if (c.key) m[c.key] = c.title
  return m
}, {})

// 替换 title 为渲染函数（添加筛选/排序图标）
replenishColumns.forEach((c) => {
  if (!c.key) return
  const needSort = NUMERIC_FIELDS.has(c.key)
  const origTitle = c.title
  c.title = () => h('span', { style: { display: 'inline-flex', alignItems: 'center', justifyContent: 'center', gap: '10px' } }, [
    renderFilterIcon(c.key),
    origTitle,
    needSort ? renderSortIcon(c.key) : null,
  ].filter(Boolean))
})

const replenishScrollX = replenishColumns.reduce((s, c) => s + (Number(c?.width) || 100), 0)
const replenishMaxHeight = 680
const {
  showDrawer, visibleKeys, editingKeys, leftCols, selCols, isAllChecked,
  init: initColConfig, openDrawer, apply: applyColConfig,
  toggleAll, toggleColumn, onDragStart, onDragOver, onDrop, onDragEnd,
} = useColumnConfig(['warehouseNames', 'sku'], allColumnMap)
const selectionColumn = replenishColumns[0]  // type: 'selection'
const colByKey = replenishColumns.reduce((m, c) => { if (c.key) m[c.key] = c; return m }, {})
const visibleColumns = computed(() => {
  const ordered = visibleKeys.value.map(k => colByKey[k]).filter(Boolean)
  return [selectionColumn, ...ordered]
})
const visibleScrollX = computed(() => visibleColumns.value.reduce((s, c) => s + (Number(c?.width) || 100), 0))

async function loadInventoryOverview() {
  loading.value = true
  const seq = ++loadSeq

  try {
    const body = { page: query.page, size: query.size }
    if (sortField.value) { body.sortField = sortField.value; body.sortOrder = sortOrder.value }
    if (activeFilters.value.length) {
      body.filters = activeFilters.value.map(f => ({ field: f.field, value: f.value }))
    }
    const list = await searchInventoryOverview(body)
    if (seq !== loadSeq) return
    replenishRows.value = list?.records || (Array.isArray(list) ? list : [])
    totalRecords.value = list?.total || 0
    updatedAt.value = formatDateTime(new Date())
  } catch (error) {
    if (seq !== loadSeq) return
    message.error(error instanceof Error ? error.message : '加载运营组数据失败')
  } finally {
    if (seq === loadSeq) loading.value = false
  }
}

onMounted(async () => {
  await initColConfig('dashboard')
  loadInventoryOverview()
  document.addEventListener('click', onDocClick)
})
onUnmounted(() => {
  document.removeEventListener('click', onDocClick)
})
function onDocClick() {
  if (showFilter.value) showFilter.value = false
}

async function handleRecalc() {
  if (loading.value) return
  loading.value = true
  try {
    await refreshSnapshot()
    await loadInventoryOverview()
    message.success('刷新完成')
  } catch (e) {
    message.error(e instanceof Error ? e.message : '刷新失败')
  } finally {
    loading.value = false
  }
}

async function handleSyncAll() {
  if (syncing.value) return
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
  if (importExportLoading.value) return
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

const checkedRowKeys = ref([])

const importExportOptions = [
  { label: '导出 Excel', key: 'exportExcel' },
  { label: '导入销量报表', key: 'uploadSales' },
  { label: '导入利润率', key: 'uploadProfitRate' },
  { label: '导入退货率', key: 'uploadReturnRate' },
]

function handleUploadProfitRate() {
  if (importExportLoading.value) return
  const input = document.createElement('input')
  input.type = 'file'; input.accept = '.xlsx,.xls'
  input.onchange = async (e) => {
    const file = e.target.files[0]
    if (!file) return
    importExportLoading.value = true
    try {
      const form = new FormData(); form.append('file', file)
      const token = JSON.parse(localStorage.getItem('inventory-auth-session') || '{}').token || ''
      const resp = await fetch('/api/goodcang/import-profit-rate', { method: 'POST', headers: { Authorization: `Bearer ${token}` }, body: form })
      const data = await resp.json()
      message.success('导入完成：共' + data.total + '条，更新' + data.updated + '，跳过' + data.skipped)
    } catch (err) { message.error('导入失败') }
    finally { importExportLoading.value = false }
  }
  input.click()
}

function handleUploadReturnRate() {
  if (importExportLoading.value) return
  const input = document.createElement('input')
  input.type = 'file'; input.accept = '.xlsx,.xls'
  input.onchange = async (e) => {
    const file = e.target.files[0]
    if (!file) return
    importExportLoading.value = true
    try {
      const form = new FormData(); form.append('file', file)
      const token = JSON.parse(localStorage.getItem('inventory-auth-session') || '{}').token || ''
      const resp = await fetch('/api/goodcang/import-return-rate', { method: 'POST', headers: { Authorization: `Bearer ${token}` }, body: form })
      const data = await resp.json()
      message.success('导入完成：共' + data.total + '条，更新' + data.updated + '，跳过' + data.skipped)
    } catch (err) { message.error('导入失败') }
    finally { importExportLoading.value = false }
  }
  input.click()
}

function handleDropdownSelect(key) {
  if (key === 'exportExcel') handleExportExcel()
  else if (key === 'uploadSales') handleUploadExcel()
  else if (key === 'uploadProfitRate') handleUploadProfitRate()
  else if (key === 'uploadReturnRate') handleUploadReturnRate()
}

async function handleExportExcel() {
  if (importExportLoading.value) return
  importExportLoading.value = true
  try {
    const colKeys = visibleKeys.value
    const colTitles = colKeys.map(k => allColumnMap[k] || k)
    const body = { colKeys, colTitles }
    if (activeFilters.value.length) {
      body.filters = activeFilters.value.map(f => ({ field: f.field, value: f.value }))
    }
    // 只有部分选中才导出指定行，全选则不传 rowKeys → 导出全部筛选数据
    if (checkedRowKeys.value.length && checkedRowKeys.value.length < filteredRows.length) {
      body.rowKeys = checkedRowKeys.value
    }
    const token = JSON.parse(localStorage.getItem('inventory-auth-session') || '{}').token || ''
    const base = import.meta.env.VITE_API_BASE_URL || window.location.origin
    const resp = await fetch(base + '/api/inventory-overview/export', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
      body: JSON.stringify(body),
    })
    if (!resp.ok) throw new Error('导出失败')
    const blob = await resp.blob()
    const a = document.createElement('a')
    a.href = URL.createObjectURL(blob)
    a.download = '补货_' + new Date().toISOString().slice(0,19).replace(/[:T]/g,'-') + '.xlsx'
    a.click()
    message.success('导出成功')
  } catch (e) {
    message.error(e.message || '导出失败')
  } finally {
    importExportLoading.value = false
  }
}

</script>

<template>
  <div class="dashboard-page">
<!--    ===== KPI 数据卡片 ===== -->
<!--    <div class="kpi-grid">-->
<!--      <div-->
<!--        v-for="(card, idx) in kpiCards"-->
<!--        :key="card.label"-->
<!--        class="kpi-card"-->
<!--        :style="{ animationDelay: idx * 0.1 + 's' }"-->
<!--      >-->
<!--        <div class="kpi-icon" :style="{ background: card.bg, color: card.color }">-->
<!--          <svg v-if="idx === 0" viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2"><path d="M4 20h16M4 20V4m0 16 4-4v4m4 0V10m0 10 4-8v8m4 0V6m0 14 4-12v12"/></svg>-->
<!--          <svg v-else-if="idx === 1" viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2"><rect x="1" y="3" width="15" height="13"/><polygon points="16 8 20 8 23 11 23 16 16 16 16 8"/><circle cx="5.5" cy="18.5" r="2.5"/><circle cx="18.5" cy="18.5" r="2.5"/></svg>-->
<!--          <svg v-else-if="idx === 2" viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"/><polyline points="3.27 6.96 12 12.01 20.73 6.96"/><line x1="12" y1="22.08" x2="12" y2="12"/></svg>-->
<!--          <svg v-else viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2"><polyline points="22 12 18 12 15 21 9 3 6 12 2 12"/></svg>-->
<!--        </div>-->
<!--        <div class="kpi-info">-->
<!--          <span class="kpi-value animate-count">{{ card.value }}</span>-->
<!--          <span class="kpi-label">{{ card.label }}</span>-->
<!--        </div>-->
<!--      </div>-->
<!--    </div>-->

    <!-- ===== 主表格卡片 ===== -->
    <div class="table-card-wrap">
    <NCard title="库存总览" size="large">
      <template #header-extra>
        <NSpace align="center" size="small">
          <NTag type="info" :bordered="false" size="small">更新 {{ updatedAt || '-' }}</NTag>
          <NTag size="small" :bordered="false" type="default">共 {{ totalRecords }} 条</NTag>
          <NButton size="small" secondary :loading="loading" @click="handleRecalc">
            <template #icon>
              <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2"><polyline points="23 4 23 10 17 10"/><path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"/></svg>
            </template>
            刷新
          </NButton>
          <NButton v-if="isAdmin" size="small" type="warning" :loading="syncing" @click="handleSyncAll">
            拉取最新数据
          </NButton>
          <NDropdown trigger="click" :options="importExportOptions" @select="handleDropdownSelect">
            <NButton size="small" type="info" :loading="importExportLoading">导入导出</NButton>
          </NDropdown>
          <NButton size="small" round @click="openDrawer" title="列设置">
            <template #icon>
              <svg viewBox="0 0 1024 1024" width="14" height="14"><path fill="currentColor" d="M512 938.666667a32.032 32.032 0 0 1-15.648-4.085334l-352-197.333333A32 32 0 0 1 128 709.333333v-394.666666a32 32 0 0 1 16.352-27.914667l352-197.333333a32 32 0 0 1 31.296 0l352 197.333333A32 32 0 0 1 896 314.666667v394.666666a32 32 0 0 1-16.352 27.914667l-352 197.333333A32 32 0 0 1 512 938.666667zM192 690.581333L512 869.973333l320-179.392V333.408L512 154.016 192 333.408v357.173333zM512 682.666667c-94.101333 0-170.666667-76.565333-170.666667-170.666667S417.898667 341.333333 512 341.333333s170.666667 76.565333 170.666667 170.666667-76.565333 170.666667-170.666667 170.666667z m0-277.333334c-58.816 0-106.666667 47.850667-106.666667 106.666667s47.850667 106.666667 106.666667 106.666667 106.666667-47.850667 106.666667-106.666667S570.816 405.333333 512 405.333333z"/></svg>
            </template>
          </NButton>
        </NSpace>
      </template>

      <!-- 筛选标签 -->
      <div v-if="activeFilters.length" class="filter-tags">
        <NTag v-for="f in activeFilters" :key="f.field" closable size="small" type="info" @close="activeFilters = activeFilters.filter(af => af.field !== f.field); query.page = 1; loadInventoryOverview()">{{ f.display }}</NTag>
        <NButton size="tiny" text type="error" @click="clearAllFilters">清除全部</NButton>
      </div>

      <NDataTable
        remote
        :loading="loading"
        :columns="visibleColumns"
        :data="filteredRows"
        :bordered="false"
        :scroll-x="visibleScrollX"
        :max-height="replenishMaxHeight"
        :row-key="(row) => `${String(row?.warehouseNames ?? '').trim()}|${String(row?.sku ?? '').trim()}`"
        v-model:checked-row-keys="checkedRowKeys"
        :pagination="{
          page: query.page,
          pageSize: query.size,
          itemCount: totalRecords,
          showSizePicker: true,
          pageSizes: [20, 50, 100, 200],
          onUpdatePage: (p) => { query.page = p; loadInventoryOverview() },
          onUpdatePageSize: (s) => { query.size = s; query.page = 1; loadInventoryOverview() },
        }"
        striped
      />
    </NCard>
    </div>

    <!-- ===== 列配置抽屉 ===== -->
    <NDrawer v-model:show="showDrawer" :width="480" placement="right">
      <NDrawerContent title="列设置" closable>
        <div class="col-config-body">
          <div class="col-config-left">
            <NSpace vertical size="small">
              <NCheckbox :checked="isAllChecked" @update:checked="toggleAll">全选</NCheckbox>
              <NCheckbox
                v-for="c in leftCols"
                :key="c.key"
                :checked="c.checked"
                :disabled="c.disabled"
                @update:checked="toggleColumn(c.key)"
              >{{ c.title }}</NCheckbox>
            </NSpace>
          </div>
          <div class="col-config-right">
            <div class="col-config-right-title">已选字段（拖拽排序）</div>
            <div
              v-for="(c, idx) in selCols"
              :key="c.key"
              class="col-config-item"
              :class="{ 'col-config-item-fixed': c.fixed }"
              :draggable="!c.fixed"
              @dragstart="onDragStart(idx)"
              @dragover="onDragOver"
              @drop="onDrop(idx)"
              @dragend="onDragEnd"
            >
              <span class="col-config-drag" :style="{ visibility: c.fixed ? 'hidden' : 'visible' }">⠿</span>
              <span>{{ c.title }}</span>
            </div>
          </div>
        </div>
        <template #footer>
          <NSpace justify="end">
            <NButton @click="showDrawer = false">取消</NButton>
            <NButton type="primary" @click="applyColConfig('dashboard', message)">应用</NButton>
          </NSpace>
        </template>
      </NDrawerContent>
    </NDrawer>

    <!-- ===== 筛选弹窗 ===== -->
    <Teleport to="body">
      <div v-if="showFilter" class="filter-popover" :style="{ left: filterX + 'px', top: filterY + 'px' }" @click.stop>
        <div class="filter-popover-inner">
          <!-- 文本字段：单一搜索框 -->
          <input v-if="!NUMERIC_FIELDS.has(filterField)" ref="filterRawRef" :value="filterInputVal" class="filter-raw-input" placeholder="搜索关键词..." @input="filterInputVal = $event.target.value" @keyup.enter="applyFilter()" />
          <!-- 数值字段：符号下拉 + 数值输入 -->
          <div v-else class="filter-num-row">
            <select v-model="filterNumOpVal" class="filter-op-select">
              <option v-for="op in ['>','>=','=','<=','<']" :key="op" :value="op">{{ op }}</option>
            </select>
            <input ref="filterRawRef" :value="filterNumInputVal" class="filter-raw-input" style="flex:1" type="number" placeholder="数值" @input="filterNumInputVal = $event.target.value" @keyup.enter="applyFilter()" />
          </div>
          <div v-if="!filterInputVal && distinctFilterValues.length" class="filter-distinct-list">
            <div class="filter-distinct-title">当前页可选值</div>
            <div v-for="v in distinctFilterValues" :key="v" class="filter-distinct-item">
              <NCheckbox size="small" :checked="filterChecked.includes(v)" @update:checked="toggleFilterCheck(v)" />
              <span @click="toggleFilterCheck(v)" class="filter-item-label">{{ v }}</span>
            </div>
          </div>
          <div v-if="filterInputVal && filterSearchResults.length" class="filter-distinct-list">
            <div class="filter-distinct-title">全量匹配（{{ filterSearchResults.length }} 条）</div>
            <div v-for="v in filterSearchResults" :key="v" class="filter-distinct-item">
              <NCheckbox size="small" :checked="filterChecked.includes(v)" @update:checked="toggleFilterCheck(v)" />
              <span @click="toggleFilterCheck(v)" class="filter-item-label">{{ v }}</span>
            </div>
          </div>
          <NSpace size="small" style="margin-top: 8px" justify="end">
            <NButton size="tiny" @click="clearFilter">清除</NButton>
            <NButton size="tiny" type="primary" @click="applyFilter()">确定</NButton>
          </NSpace>
        </div>
      </div>
    </Teleport>
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

@media (max-width: 1200px) { .kpi-grid { grid-template-columns: repeat(2, 1fr); } }
@media (max-width: 640px) { .kpi-grid { grid-template-columns: 1fr; } }

/* ===== 列配置抽屉 ===== */
.col-config-body {
  display: flex;
  gap: 16px;
  min-height: 300px;
}
.col-config-left {
  width: 160px;
  flex-shrink: 0;
  padding: 8px;
  border-right: 1px solid #eee;
  max-height: 400px;
  overflow-y: auto;
}
.col-config-right {
  flex: 1;
  padding: 8px;
  max-height: 400px;
  overflow-y: auto;
}
.col-config-right-title {
  font-size: 12px;
  color: #999;
  margin-bottom: 8px;
}
.col-config-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 10px;
  margin-bottom: 4px;
  background: #fafafa;
  border-radius: 4px;
  cursor: grab;
  font-size: 13px;
  border: 1px solid #eee;
  transition: background 0.15s;
}
.col-config-item:hover {
  background: #e6f4ff;
}
.col-config-item-fixed {
  background: #f5f5f5;
  color: #999;
  cursor: default;
}
.col-config-drag {
  color: #bbb;
  font-size: 14px;
  user-select: none;
}

/* ===== 筛选弹窗 ===== */
.filter-popover {
  position: fixed;
  z-index: 2000;
}
.filter-popover-inner {
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 6px 16px rgba(0,0,0,0.12);
  padding: 12px;
  min-width: 220px;
  max-width: 300px;
}
.filter-raw-input {
  width: 100%; box-sizing: border-box;
  height: 30px; padding: 0 8px;
  border: 1px solid #d9d9d9; border-radius: 4px;
  font-size: 13px; outline: none;
}
.filter-raw-input:focus { border-color: #1677ff; box-shadow: 0 0 0 2px rgba(22,119,255,0.1); }
.filter-num-row { display: flex; align-items: center; gap: 8px; }
.filter-op-select { height: 30px; width: 60px; border: 1px solid #d9d9d9; border-radius: 4px; font-size: 14px; text-align: center; cursor: pointer; outline: none; padding-left: 2px; padding-right: 0; }
.filter-op-select:focus { border-color: #1677ff; }
.filter-tags { display: flex; flex-wrap: wrap; align-items: center; gap: 6px; padding: 4px 0 8px; flex-shrink: 0; }
.filter-distinct-list {
  max-height: 180px;
  overflow-y: auto;
  margin-top: 8px;
  border-top: 1px solid #eee;
  padding-top: 4px;
}
.filter-distinct-title {
  font-size: 11px;
  color: #999;
  padding: 4px 0;
}
.filter-distinct-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  padding: 3px 8px;
  cursor: pointer;
  border-radius: 3px;
}
.filter-distinct-item:hover {
  background: #e6f4ff;
  color: #1677ff;
}
.filter-item-label {
  flex: 1; cursor: pointer; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
</style>
