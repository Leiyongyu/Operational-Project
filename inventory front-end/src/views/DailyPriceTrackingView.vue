<script setup>
import { computed, h, onMounted, onUnmounted, ref, watch } from 'vue'
import {
  NButton, NCard, NCheckbox, NDataTable, NDrawer, NDrawerContent, NDropdown, NInput, NSpace, NTag, useMessage,
} from 'naive-ui'
import { fetchDailyPriceTracking, fetchDistinctValues, refreshDailyPriceTrackingSnapshot, exportDailyPriceTracking, importLowestPrice, saveOe, saveRemark } from '@/api/dailyPriceTracking'
import { useDataTable } from '@/composables/useDataTable'
import { useColumnConfig } from '@/composables/useColumnConfig'

const message = useMessage()

const { loading, records, total, query, filters, loadData: _loadData } = useDataTable(
  fetchDailyPriceTracking,
  {},
  { pageSize: 100, loadOnMount: false },
)
async function loadData() {
  loading.value = true
  try {
    const body = { page: query.page, size: query.size }
    for (const [k, v] of Object.entries(filters)) { if (v && String(v).trim()) body[k] = String(v).trim() }
    if (activeFilters.value.length) body.filters = activeFilters.value.map(f => ({ field: f.field, value: f.value }))
    const result = await fetchDailyPriceTracking(body)
    records.value = result?.records || []
    total.value = Number(result?.total || 0)
    updatedAt.value = new Date().toISOString().slice(0,19).replace('T',' ')
  } finally { loading.value = false }
}

// ===== 筛选 =====
const activeFilters = ref([])
const filterField = ref('')
const filterInputVal = ref('')
const filterNumOpVal = ref('>')
const filterNumInputVal = ref('')
const filterChecked = ref([])
const filterSearchResults = ref([])
const filterRawRef = ref(null)
const showFilter = ref(false)
const filterX = ref(0)
const filterY = ref(0)
let filterTimer = null
const TEXT_KEYS = new Set(['site','sku','skuLevel','productName','oeNumber','ebayFrontpageUrl','frontpageSoldUrl','brand','operator','remark'])

const distinctValues = computed(() => {
  if (!filterField.value) return []
  const set = new Set()
  for (const row of records.value) { const v = row[filterField.value]; if (v != null && String(v).trim()) set.add(String(v).trim()) }
  return [...set].sort().slice(0, 50)
})

watch(filterInputVal, (val) => {
  if (!showFilter.value || !filterField.value) return
  clearTimeout(filterTimer)
  filterTimer = setTimeout(async () => {
    if (!val || !val.trim()) { filterSearchResults.value = []; return }
    try { filterSearchResults.value = await fetchDistinctValues(filterField.value, val.trim()) || [] }
    catch { filterSearchResults.value = [] }
  }, 200)
})

function openFilter(key, e) {
  filterField.value = key; filterInputVal.value = ''; filterNumOpVal.value = '>'; filterNumInputVal.value = ''; filterSearchResults.value = []
  const exist = activeFilters.value.find(f => f.field === key)
  filterChecked.value = exist && !NUMERIC_KEYS.has(key) ? exist.value.split(',').filter(Boolean) : []
  if (exist) {
    if (NUMERIC_KEYS.has(key)) { const m = exist.value.match(/^(>=|<=|>|<|=)(.+)$/); if (m) { filterNumOpVal.value = m[1]; filterNumInputVal.value = m[2] } }
    else filterInputVal.value = exist.value
  }
  const rect = e.currentTarget.getBoundingClientRect(); filterX.value = rect.left; filterY.value = rect.bottom + 4; showFilter.value = true
}
function toggleFilterCheck(val) { const i = filterChecked.value.indexOf(val); if (i >= 0) filterChecked.value.splice(i, 1); else filterChecked.value.push(val) }
function applyFilter() {
  const f = filterField.value
  const raw = NUMERIC_KEYS.has(f) ? (filterNumInputVal.value ? filterNumOpVal.value + filterNumInputVal.value.trim() : '') : filterInputVal.value
  const v = filterChecked.value.length ? filterChecked.value.join(',') : (raw && raw.trim() ? raw.trim() : '')
  activeFilters.value = activeFilters.value.filter(af => af.field !== f)
  if (v) activeFilters.value.push({ field: f, value: v, display: (allColumnMap[f] || f) + ':' + v })
  showFilter.value = false; query.page = 1; loadData()
}
function clearFilter() { activeFilters.value = activeFilters.value.filter(af => af.field !== filterField.value); filterInputVal.value = ''; filterNumOpVal.value = '>'; filterNumInputVal.value = ''; filterChecked.value = []; showFilter.value = false; query.page = 1; loadData() }
function clearAllFilters() { activeFilters.value = []; filterField.value = ''; query.page = 1; loadData() }
function onDocClick() { if (showFilter.value) showFilter.value = false }

// ===== 排序（通过 filters 传递 sortField/sortOrder）=====
const NUMERIC_KEYS = new Set([
  'ourLowestPrice', 'trackingProfitMargin', 'floorPrice', 'returnRate',
  'last3DaysSales', 'last7DaysSales', 'last30DaysSales', 'last90DaysSales',
  'maxMonthlySales', 'overseasWarehouseStock', 'overseasWarehouseAge',
  'stockSalesRatio', 'estimatedReplenish', 'trackingPrice',
])

function handleSortClick(key) {
  if (!NUMERIC_KEYS.has(key)) return
  if (filters.sortField !== key) { filters.sortField = key; filters.sortOrder = 'desc' }
  else if (filters.sortOrder === 'desc') { filters.sortOrder = 'asc' }
  else if (filters.sortOrder === 'asc') { filters.sortField = ''; filters.sortOrder = '' }
  else { filters.sortField = key; filters.sortOrder = 'desc' }
  query.page = 1
  loadData()
}
function renderSortIcon(key) {
  if (!NUMERIC_KEYS.has(key)) return ''
  const isActive = filters.sortField === key
  const color = isActive && filters.sortOrder ? '#1677ff' : '#bfbfbf'
  const isAsc = isActive && filters.sortOrder === 'asc'
  const triangle = isAsc
    ? 'M863.764 270.458c32.518-35.975 85.239-35.975 117.757 0l401.018 443.652c32.519 35.977 19.57 65.14-28.913 65.14h-861.969c-48.485 0-61.43-29.164-28.913-65.14l401.018-443.652z'
    : 'M855.721 739.856c30.486 33.728 79.913 33.728 110.397 0l375.954-415.921c30.487-33.729 18.347-61.069-27.103-61.069h-808.099c-45.455 0-57.591 27.341-27.106 61.068l375.955 415.923z'
  return h('svg', {
    viewBox: '0 0 1824 1024', width: '12', height: '12',
    style: { color, marginLeft: '0', cursor: 'pointer', flexShrink: 0, verticalAlign: 'middle' },
    onClick: (e) => { e.stopPropagation(); handleSortClick(key) },
  }, [h('path', { fill: 'currentColor', d: triangle })])
}

// ===== 备注编辑状态（非受控 NInput 模式） =====
const remarkInputs = {}  // key: "site|sku" → 当前输入值
const oeInputs = {}       // key: "site|sku" → 当前 OE 输入值

const updatedAt = ref('')
const tableMaxHeight = 680

onMounted(async () => {
  await initColConfig('daily-price-tracking')
  loadData()
  document.addEventListener('click', onDocClick)
})
onUnmounted(() => { document.removeEventListener('click', onDocClick) })

async function handleRefresh() {
  if (loading.value) return
  loading.value = true
  try {
    await refreshDailyPriceTrackingSnapshot()
    await loadData()
    message.success('刷新完成')
  } catch (e) {
    message.error(e instanceof Error ? e.message : '刷新失败')
  } finally {
    loading.value = false
  }
}

const importExportLoading = ref(false)

const importExportOptions = [
  { label: '导出 Excel', key: 'exportExcel' },
  { label: '导入最低价', key: 'importPrice' },
  { label: '导入商品单价', key: 'uploadPrice' },
]

function handleDropdownSelect(key) {
  if (key === 'exportExcel') handleExport()
  else if (key === 'importPrice') handleImportPrice()
  else if (key === 'uploadPrice') handleUploadPrice()
}

async function handleUploadPrice() {
  if (importExportLoading.value) return
  const input = document.createElement('input')
  input.type = 'file'
  input.accept = '.xlsx,.xls'
  input.onchange = async (e) => {
    const file = e.target.files[0]
    if (!file) return
    importExportLoading.value = true
    try {
      const form = new FormData()
      form.append('file', file)
      const token = JSON.parse(localStorage.getItem('inventory-auth-session') || '{}').token || ''
      const resp = await fetch('/api/goodcang/import-price', { method: 'POST', headers: { Authorization: `Bearer ${token}` }, body: form })
      const data = await resp.json()
      message.success(`导入完成：共${data.total}条，更新${data.updated}，跳过${data.skipped}`)
    } catch (err) {
      message.error('导入失败')
    } finally {
      importExportLoading.value = false
    }
  }
  input.click()
}

function handleExportGoodcangProducts() {
  const base = import.meta.env.VITE_API_BASE_URL || window.location.origin
  const a = document.createElement('a')
  a.href = `${base}/api/goodcang/export-product-list`
  a.download = '谷仓商品列表.xlsx'
  a.click()
}

async function handleImportPrice() {
  if (importExportLoading.value) return
  const input = document.createElement('input')
  input.type = 'file'
  input.accept = '.xlsx,.xls'
  input.onchange = async (e) => {
    const file = e.target.files[0]
    if (!file) return
    importExportLoading.value = true
    try {
      const res = await importLowestPrice(file)
      message.success(`导入完成: 共${res.total}条, 新增${res.inserted}, 更新${res.updated}, 跳过${res.skipped}`)
      loadData()
    } catch (err) {
      message.error(err instanceof Error ? err.message : '导入失败')
    } finally {
      importExportLoading.value = false
    }
  }
  input.click()
}

async function handleExport() { if (importExportLoading.value) return; importExportLoading.value = true
  try {
    const selected = checkedRowKeys.value.length > 0 ? checkedRowKeys.value : null
    await exportDailyPriceTracking({
      ids: selected ? selected.join(',') : undefined,
    })
    message.success(selected ? `已导出 ${selected.length} 条` : '导出成功')
  } catch (e) {
    importExportLoading.value = false; message.error(e.message || "导出失败")
  }
}

function renderLink(url) {
  if (!url) return ''
  return h('a', { href: url, target: '_blank', style: { color: '#1677ff' } }, url.length > 40 ? url.substring(0, 40) + '...' : url)
}

const columns = [
  { type: 'selection', multiple: true, width: 40, fixed: 'left' },
  { title: '站点', key: 'site', width: 100, fixed: 'left' },
  { title: 'SKU', key: 'sku', width: 140, fixed: 'left', ellipsis: { tooltip: true } },
  { title: '等级', key: 'skuLevel', width: 110, fixed: 'left' },
  { title: '最低价', key: 'ourLowestPrice', width: 110, align: 'center',
    render: (row) => row.ourLowestPrice ?? '' },
  { title: '跟卖价', key: 'trackingPrice', width: 120, align: 'center',
    render: (row) => {
      const tpKey = 'tp_' + row.site + '|' + row.sku
      if (!(tpKey in remarkInputs)) remarkInputs[tpKey] = row.trackingPrice ?? ''
      return h(NInput, {
        defaultValue: remarkInputs[tpKey] != null ? String(remarkInputs[tpKey]) : '',
        size: 'tiny',
        placeholder: '跟卖价',
        onUpdateValue: (v) => { remarkInputs[tpKey] = v },
        onBlur: async () => {
          const v = remarkInputs[tpKey]
          if (v === (row.trackingPrice != null ? String(row.trackingPrice) : '')) return
          try {
            const price = v && !isNaN(parseFloat(v)) ? v : ''
            const token = JSON.parse(localStorage.getItem('inventory-auth-session') || '{}').token || ''
            const resp = await fetch('/api/goodcang/calc-tracking', {
              method: 'POST',
              headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
              body: JSON.stringify({ site: row.site, sku: row.sku, trackingPrice: price })
            })
            const data = await resp.json()
            row.trackingPrice = data.trackingPrice
            row.trackingProfitMargin = data.trackingProfitMargin
            row.floorPrice = data.floorPrice
            message.success('已保存')
          } catch (e) { message.error('保存失败') }
        },
      })
    },
  },
  { title: '跟卖利润率', key: 'trackingProfitMargin', width: 140, align: 'center',
    render: (row) => row.trackingProfitMargin != null ? (row.trackingProfitMargin * 100).toFixed(1) + '%' : '' },
  { title: '底线价', key: 'floorPrice', width: 110, align: 'center',
    render: (row) => row.floorPrice ?? '' },
  { title: '退货率', key: 'returnRate', width: 110, align: 'center',
    render: (row) => row.returnRate != null ? (Number(row.returnRate) * 100).toFixed(1) + '%' : '' },
  { title: '近3天销量', key: 'last3DaysSales', width: 130, align: 'center',
    render: (row) => row.last3DaysSales ?? '' },
  { title: '近7天销量', key: 'last7DaysSales', width: 130, align: 'center',
    render: (row) => row.last7DaysSales ?? '' },
  { title: '近30天销量', key: 'last30DaysSales', width: 150, align: 'center',
    render: (row) => row.last30DaysSales ?? '' },
  { title: '近90天销量', key: 'last90DaysSales', width: 150, align: 'center',
    render: (row) => row.last90DaysSales ?? '' },
  { title: '历史最大月销', key: 'maxMonthlySales', width: 150, align: 'center',
    render: (row) => row.maxMonthlySales ?? '' },
  { title: 'OE号', key: 'oeNumber', width: 140, ellipsis: { tooltip: true },
    render: (row) => {
      const key = `${row.site}|${row.sku}`
      if (!(key in oeInputs)) oeInputs[key] = row.oeNumber || ''
      return h(NInput, {
        defaultValue: oeInputs[key],
        size: 'tiny',
        placeholder: '输入OE号',
        clearable: true,
        onUpdateValue: (v) => { oeInputs[key] = v },
        onBlur: async () => {
          const newVal = oeInputs[key] || ''
          if (newVal === (row.oeNumber || '')) return
          try {
            const res = await saveOe(row.site, row.sku, newVal)
            // 只更新当前行的链接，不刷新全页
            row.oeNumber = res.oeNumber || ''
            row.ebayFrontpageUrl = res.ebayFrontpageUrl || null
            row.frontpageSoldUrl = res.ebayFrontpageUrl ? res.frontpageSoldUrl : null
            message.success('OE已保存')
          } catch (e) {
            message.error('保存失败')
          }
        },
        onClear: () => {
          oeInputs[key] = ''
          saveOe(row.site, row.sku, '').then(res => {
            row.oeNumber = ''
            row.ebayFrontpageUrl = null
            row.frontpageSoldUrl = null
          }).catch(() => {})
        },
      })
    },
  },
  { title: '售前链接', key: 'ebayFrontpageUrl', width: 150, ellipsis: { tooltip: true },
    render: (row) => renderLink(row.ebayFrontpageUrl) },
  { title: '售后链接', key: 'frontpageSoldUrl', width: 150, ellipsis: { tooltip: true },
    render: (row) => renderLink(row.frontpageSoldUrl) },
  { title: '海外仓库存', key: 'overseasWarehouseStock', width: 140, align: 'center',
    render: (row) => row.overseasWarehouseStock ?? '' },
  { title: '海外仓库龄', key: 'overseasWarehouseAge', width: 140, align: 'center',
    render: (row) => row.overseasWarehouseAge != null ? row.overseasWarehouseAge + '天' : '' },
  { title: '库销比', key: 'stockSalesRatio', width: 110, align: 'center',
    render: (row) => row.stockSalesRatio != null ? row.stockSalesRatio + '%' : '' },
  { title: '预估补货量', key: 'estimatedReplenish', width: 140, align: 'center',
    render: (row) => row.estimatedReplenish ?? '' },
  { title: '品牌', key: 'brand', width: 100 },
  { title: '操作员', key: 'operator', width: 100 },
  { title: '备注', key: 'remark', width: 200,
    render: (row) => {
      const key = `${row.site}|${row.sku}`
      // 初始化：取已保存的备注作为初始值
      if (!(key in remarkInputs)) {
        remarkInputs[key] = row.remark || ''
      }
      return h(NInput, {
        defaultValue: remarkInputs[key],
        size: 'tiny',
        placeholder: '输入备注，失焦自动保存',
        clearable: true,
        onUpdateValue: (v) => { remarkInputs[key] = v },
        onBlur: async () => {
          const newVal = remarkInputs[key] || ''
          if (newVal === (row.remark || '')) return
          try {
            await saveRemark(row.site, row.sku, newVal)
            row.remark = newVal
            message.success('备注已保存')
          } catch (e) {
            message.error('保存失败')
          }
        },
        onClear: () => {
          remarkInputs[key] = ''
          saveRemark(row.site, row.sku, '').catch(() => {})
          row.remark = ''
        },
      })
    },
  },
].map((c) => ({ ...c, resizable: false, minWidth: 70 }))

// ===== 列配置（必须在替换 title 前取原始字符串）=====
const dataColumns = columns.filter(c => c.key)
const allColumnMap = dataColumns.reduce((m, c) => { m[c.key] = c.title; return m }, {})
const selectionCol = columns[0]

// 给列标题加筛选/排序图标
function renderFilterIcon(key) {
  const isActive = activeFilters.value.some(f => f.field === key)
  return h('svg', { viewBox: '0 0 1024 1024', width: '12', height: '12',
    style: { color: isActive ? '#1677ff' : '#bfbfbf', marginLeft: '0', cursor: 'pointer', flexShrink: 0 },
    onClick: (e) => { e.stopPropagation(); openFilter(key, e) },
  }, [h('path', { fill: 'currentColor', d: 'M911.2 128H112.8c-52.8 0-80 58.4-41.6 98.4L384 576v288c0 17.6 14.4 32 32 32h192c17.6 0 32-14.4 32-32V576l312.8-349.6c38.4-40 11.2-98.4-41.6-98.4z' })])
}
columns.forEach((c) => {
  if (!c.key) return
  const needSort = NUMERIC_KEYS.has(c.key)
  const origTitle = c.title
  c.title = () => h('span', { style: { display: 'inline-flex', alignItems: 'center', justifyContent: 'center', gap: '10px' } }, [
    renderFilterIcon(c.key),
    origTitle,
    needSort ? renderSortIcon(c.key) : null,
  ].filter(Boolean))
})
const {
  showDrawer, visibleKeys, editingKeys, leftCols, selCols, isAllChecked,
  init: initColConfig, openDrawer, apply: applyColConfig,
  toggleAll, toggleColumn, onDragStart, onDragOver, onDrop, onDragEnd,
} = useColumnConfig(['site', 'sku'], allColumnMap)
const colByKey2 = dataColumns.reduce((m, c) => { m[c.key] = c; return m }, {})
const visibleColumns = computed(() => {
  const ordered = visibleKeys.value.map(k => colByKey2[k]).filter(Boolean)
  return [selectionCol, ...ordered]
})

const scrollX = computed(() => visibleColumns.value.reduce((s, c) => s + (Number(c?.width) || 100), 0))
const checkedRowKeys = ref([])
</script>

<template>
  <div class="dashboard-page">
    <div class="table-card-wrap">
    <NCard title="每日跟价" size="large">
      <template #header-extra>
        <NSpace align="center" size="small">
          <NTag type="info" :bordered="false" size="small">更新 {{ updatedAt || '-' }}</NTag>
          <NTag size="small" :bordered="false" type="default">共 {{ total }} 条</NTag>
          <NButton size="small" secondary :loading="loading" @click="handleRefresh">刷新</NButton>
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
        <NTag v-for="f in activeFilters" :key="f.field" closable size="small" type="info" @close="activeFilters = activeFilters.filter(af => af.field !== f.field); query.page = 1; loadData()">{{ f.display }}</NTag>
        <NButton size="tiny" text type="error" @click="clearAllFilters">清除全部</NButton>
      </div>

      <NDataTable
        remote
        :loading="loading"
        :columns="visibleColumns"
        :data="records"
        :row-key="(row) => `${row.site || ''}|${row.sku || ''}`"
        v-model:checked-row-keys="checkedRowKeys"
        :scroll-x="scrollX"
        :max-height="tableMaxHeight"
        :pagination="{
          page: query.page,
          pageSize: query.size,
          itemCount: total,
          showSizePicker: true,
          pageSizes: [20, 50, 100, 200],
          onUpdatePage: (p) => { query.page = p; loadData() },
          onUpdatePageSize: (s) => { query.size = s; query.page = 1; loadData() },
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
            <NButton type="primary" @click="applyColConfig('daily-price-tracking', message)">应用</NButton>
          </NSpace>
        </template>
      </NDrawerContent>
    </NDrawer>

    <!-- 筛选弹窗 -->
    <Teleport to="body">
      <div v-if="showFilter" class="filter-popover" :style="{ left: filterX + 'px', top: filterY + 'px' }" @click.stop>
        <div class="filter-popover-inner">
          <template v-if="!NUMERIC_KEYS.has(filterField)">
            <input ref="filterRawRef" :value="filterInputVal" class="filter-raw-input" placeholder="搜索关键词..." @input="filterInputVal = $event.target.value" @keyup.enter="applyFilter()" />
          </template>
          <template v-else>
            <div class="filter-num-row">
              <select v-model="filterNumOpVal" class="filter-op-select">
                <option v-for="op in ['>','>=','=','<=','<']" :key="op" :value="op">{{ op }}</option>
              </select>
              <input ref="filterRawRef" :value="filterNumInputVal" class="filter-raw-input" style="flex:1" type="number" placeholder="数值" @input="filterNumInputVal = $event.target.value" @keyup.enter="applyFilter()" />
            </div>
          </template>
          <div v-if="!filterInputVal && !filterNumInputVal && distinctValues.length" class="filter-distinct-list">
            <div class="filter-distinct-title">当前页可选值</div>
            <div v-for="v in distinctValues" :key="v" class="filter-distinct-item">
              <NCheckbox size="small" :checked="filterChecked.includes(v)" @update:checked="toggleFilterCheck(v)" />
              <span @click="toggleFilterCheck(v)" class="filter-item-label">{{ v }}</span>
            </div>
          </div>
          <div v-if="(filterInputVal || filterNumInputVal) && filterSearchResults.length" class="filter-distinct-list">
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
  gap: 6px;
}

/* ===== 标题栏（不伸缩） ===== */
.dashboard-page > div:first-child {
  flex-shrink: 0;
}

/* ===== 筛选卡片（不伸缩） ===== */
.dashboard-card {
  border-radius: 10px;
  overflow: hidden;
}

.dashboard-card :deep(.n-card-header) {
  padding: 10px 16px 0;
  font-weight: 600;
  font-size: 15px;
  color: rgba(0, 0, 0, 0.85);
}

.dashboard-card :deep(.n-card__content) {
  padding: 8px 16px 12px;
}

/* ===== 表格卡片容器：填充剩余空间 ===== */
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
  padding: 8px 16px 12px;
}

.table-card-wrap :deep(.n-card-header) {
  padding: 10px 16px 0;
  font-weight: 600;
  font-size: 15px;
  flex-shrink: 0;
}

/* ===== 表头样式（与补货页一致，居中） ===== */
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

/* ===== 标题 ===== */
.users-title {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
  color: #1a1a2e;
  letter-spacing: -0.01em;
}

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
.col-config-drag { color: #bbb; font-size: 14px; user-select: none; }
.filter-popover { position: fixed; z-index: 2000; }
.filter-popover-inner { background: #fff; border-radius: 8px; box-shadow: 0 6px 16px rgba(0,0,0,0.12); padding: 12px; min-width: 220px; max-width: 300px; }
.filter-raw-input { width: 100%; box-sizing: border-box; height: 30px; padding: 0 8px; border: 1px solid #d9d9d9; border-radius: 4px; font-size: 13px; outline: none; }
.filter-raw-input:focus { border-color: #1677ff; box-shadow: 0 0 0 2px rgba(22,119,255,0.1); }
.filter-op-select { height: 30px; width: 60px; border: 1px solid #d9d9d9; border-radius: 4px; font-size: 14px; text-align: center; cursor: pointer; outline: none; padding-left: 2px; padding-right: 0; }
.filter-num-row { display: flex; align-items: center; gap: 8px; }
.filter-tags { display: flex; flex-wrap: wrap; align-items: center; gap: 6px; padding: 4px 0 8px; flex-shrink: 0; }
.filter-distinct-list { max-height: 180px; overflow-y: auto; margin-top: 8px; border-top: 1px solid #eee; padding-top: 4px; }
.filter-distinct-title { font-size: 11px; color: #999; padding: 4px 0; }
.filter-distinct-item { display: flex; align-items: center; gap: 6px; font-size: 13px; padding: 3px 8px; cursor: pointer; border-radius: 3px; }
.filter-distinct-item:hover { background: #e6f4ff; color: #1677ff; }
.filter-item-label { flex: 1; cursor: pointer; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
</style>
