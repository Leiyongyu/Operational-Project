<script setup>
import { h, onMounted, ref } from 'vue'
import {
  NButton, NCard, NDataTable, NDropdown, NInput, NSpace, NTag, useMessage,
} from 'naive-ui'
import { fetchDailyPriceTracking, refreshDailyPriceTrackingSnapshot, exportDailyPriceTracking, importLowestPrice, saveOe, saveRemark } from '@/api/dailyPriceTracking'
import { useDataTable } from '@/composables/useDataTable'

const message = useMessage()

const { loading, records, total, query, loadData } = useDataTable(
  fetchDailyPriceTracking,
  {},
  { pageSize: 100, loadOnMount: false },
)

// ===== 备注编辑状态（非受控 NInput 模式） =====
const remarkInputs = {}  // key: "site|sku" → 当前输入值
const oeInputs = {}       // key: "site|sku" → 当前 OE 输入值

const tableMaxHeight = 750

onMounted(() => {
  loadData()
})

async function handleRefresh() {
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
  { label: '同步谷仓商品', key: 'syncGoodcang' },
]

function handleDropdownSelect(key) {
  if (key === 'exportExcel') handleExport()
  else if (key === 'importPrice') handleImportPrice()
  else if (key === 'uploadPrice') handleUploadPrice()
  else if (key === 'syncGoodcang') handleSyncGoodcangProducts()
}

async function handleUploadPrice() {
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
      const resp = await fetch('/api/goodcang/import-price', { method: 'POST', body: form })
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

async function handleSyncGoodcangProducts() {
  importExportLoading.value = true
  try {
    const resp = await fetch('/api/goodcang/sync-product', { method: 'POST' })
    const data = await resp.json()
    message.success(`同步完成：共${data.total}条，新增${data.inserted}，更新${data.updated}`)
  } catch (e) {
    message.error('同步失败')
  } finally {
    importExportLoading.value = false
  }
}

function handleExportGoodcangProducts() {
  const base = import.meta.env.VITE_API_BASE_URL || window.location.origin
  const a = document.createElement('a')
  a.href = `${base}/api/goodcang/export-product-list`
  a.download = '谷仓商品列表.xlsx'
  a.click()
}

async function handleImportPrice() {
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

async function handleExport() { importExportLoading.value = true
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
  { title: 'SKU等级', key: 'skuLevel', width: 90, fixed: 'left' },
  { title: 'SKU', key: 'sku', width: 140, fixed: 'left', ellipsis: { tooltip: true } },
  { title: '最低价', key: 'ourLowestPrice', width: 90, align: 'center',
    render: (row) => row.ourLowestPrice ?? '' },
  { title: '跟卖价格', key: 'trackingPrice', width: 110, align: 'center',
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
            const resp = await fetch('/api/goodcang/calc-tracking', {
              method: 'POST',
              headers: { 'Content-Type': 'application/json' },
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
  { title: '跟卖利润率', key: 'trackingProfitMargin', width: 100, align: 'center',
    render: (row) => row.trackingProfitMargin != null ? (row.trackingProfitMargin * 100).toFixed(1) + '%' : '' },
  { title: '底线价', key: 'floorPrice', width: 90, align: 'center',
    render: (row) => row.floorPrice ?? '' },
  { title: '退货率', key: 'returnRate', width: 80, align: 'center',
    render: (row) => row.returnRate != null ? row.returnRate + '%' : '' },
  { title: '近3天销量', key: 'last3DaysSales', width: 100, align: 'center',
    render: (row) => row.last3DaysSales ?? '' },
  { title: '近7天销量', key: 'last7DaysSales', width: 100, align: 'center',
    render: (row) => row.last7DaysSales ?? '' },
  { title: '近30天销量', key: 'last30DaysSales', width: 110, align: 'center',
    render: (row) => row.last30DaysSales ?? '' },
  { title: '近90天销量', key: 'last90DaysSales', width: 110, align: 'center',
    render: (row) => row.last90DaysSales ?? '' },
  { title: '历史最大月销', key: 'maxMonthlySales', width: 120, align: 'center',
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
  { title: '海外仓库存', key: 'overseasWarehouseStock', width: 110, align: 'center',
    render: (row) => row.overseasWarehouseStock ?? '' },
  { title: '海外仓库龄', key: 'overseasWarehouseAge', width: 110, align: 'center',
    render: (row) => row.overseasWarehouseAge != null ? row.overseasWarehouseAge + '天' : '' },
  { title: '库销比', key: 'stockSalesRatio', width: 90, align: 'center',
    render: (row) => row.stockSalesRatio != null ? row.stockSalesRatio + '%' : '' },
  { title: '预估补货量', key: 'estimatedReplenish', width: 110, align: 'center',
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

const scrollX = columns.reduce((s, c) => s + (Number(c?.width) || 100), 0)
const checkedRowKeys = ref([])
</script>

<template>
  <div class="dashboard-page">
    <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:4px">
      <h2 class="users-title" style="margin:0">每日跟价</h2>
      <NSpace>
        <NButton @click="handleRefresh" :loading="loading">刷新</NButton>
        <NDropdown trigger="click" :options="importExportOptions" @select="handleDropdownSelect">
          <NButton type="primary" :loading="importExportLoading">导入导出</NButton>
        </NDropdown>
      </NSpace>
    </div>

    <div class="table-card-wrap">
    <NCard size="small">
      <template #header-extra>
        <NTag size="small" :bordered="false">共 {{ total }} 条</NTag>
      </template>

      <NDataTable
        remote
        :loading="loading"
        :columns="columns"
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
          pageSizes: [10, 20, 50, 100],
          onUpdatePage: (p) => { query.page = p; loadData() },
          onUpdatePageSize: (s) => { query.size = s; query.page = 1; loadData() },
        }"
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
</style>
