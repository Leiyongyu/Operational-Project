<script setup>
import { h, onMounted, reactive, ref } from 'vue'
import {
  NButton, NCard, NDataTable, NForm, NFormItem, NInput, NSelect, NSpace, NTag, useMessage,
} from 'naive-ui'
import { fetchDailyPriceTracking, uploadDailyPriceTracking, exportDailyPriceTracking } from '@/api/dailyPriceTracking'

const message = useMessage()

const loading = ref(false)
const uploading = ref(false)
const records = ref([])
const total = ref(0)

const filters = reactive({ site: '', sku: '', brand: '', operator: '' })
const query = reactive({ page: 1, size: 20 })

function handleSearch() {
  query.page = 1
  loadData()
}

function handleReset() {
  filters.site = ''
  filters.sku = ''
  filters.brand = ''
  filters.operator = ''
  query.page = 1
  loadData()
}

async function loadData() {
  loading.value = true
  try {
    const result = await fetchDailyPriceTracking({
      page: query.page, size: query.size,
      site: filters.site.trim() || undefined,
      sku: filters.sku.trim() || undefined,
      brand: filters.brand.trim() || undefined,
      operator: filters.operator.trim() || undefined,
    })
    records.value = result?.records || []
    total.value = Number(result?.total || 0)
  } catch (e) {
    message.error(e instanceof Error ? e.message : '加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(() => { loadData() })

async function handleUpload() {
  const input = document.createElement('input')
  input.type = 'file'
  input.accept = '.xlsx,.xls'
  input.onchange = async (e) => {
    const file = e.target.files[0]
    if (!file) return
    uploading.value = true
    try {
      const res = await uploadDailyPriceTracking(file)
      message.success(res?.message || '导入成功')
      loadData()
    } catch (err) {
      message.error(err instanceof Error ? err.message : '导入失败')
    } finally {
      uploading.value = false
    }
  }
  input.click()
}

async function handleExport() {
  try {
    await exportDailyPriceTracking({
      site: filters.site.trim() || undefined,
      sku: filters.sku.trim() || undefined,
      brand: filters.brand.trim() || undefined,
      operator: filters.operator.trim() || undefined,
    })
    message.success('导出成功')
  } catch (e) {
    message.error(e instanceof Error ? e.message : '导出失败')
  }
}

function renderLink(url) {
  if (!url) return ''
  return h('a', { href: url, target: '_blank', style: { color: '#1677ff' } }, url.length > 40 ? url.substring(0, 40) + '...' : url)
}

const columns = [
  { title: '站点', key: 'site', width: 100, fixed: 'left' },
  { title: 'SKU等级', key: 'skuLevel', width: 90 },
  { title: 'SKU', key: 'sku', width: 140, fixed: 'left', ellipsis: { tooltip: true } },
  { title: '最低价', key: 'ourLowestPrice', width: 90, align: 'right',
    render: (row) => row.ourLowestPrice ?? '' },
  { title: '跟卖价格', key: 'trackingPrice', width: 100, align: 'right',
    render: (row) => row.trackingPrice ?? '' },
  { title: '跟卖利润率', key: 'trackingProfitMargin', width: 100, align: 'right',
    render: (row) => row.trackingProfitMargin != null ? row.trackingProfitMargin + '%' : '' },
  { title: '底线价', key: 'floorPrice', width: 90, align: 'right',
    render: (row) => row.floorPrice ?? '' },
  { title: '退货率', key: 'returnRate', width: 80, align: 'right',
    render: (row) => row.returnRate != null ? row.returnRate + '%' : '' },
  { title: '近3天销量', key: 'last3DaysSales', width: 100, align: 'right',
    render: (row) => row.last3DaysSales ?? '' },
  { title: '近7天销量', key: 'last7DaysSales', width: 100, align: 'right',
    render: (row) => row.last7DaysSales ?? '' },
  { title: '近30天销量', key: 'last30DaysSales', width: 110, align: 'right',
    render: (row) => row.last30DaysSales ?? '' },
  { title: '近90天销量', key: 'last90DaysSales', width: 110, align: 'right',
    render: (row) => row.last90DaysSales ?? '' },
  { title: '历史最大月销', key: 'maxMonthlySales', width: 120, align: 'right',
    render: (row) => row.maxMonthlySales ?? '' },
  { title: 'eBay前台首页', key: 'ebayFrontpageUrl', width: 150, ellipsis: { tooltip: true },
    render: (row) => renderLink(row.ebayFrontpageUrl) },
  { title: '前台已售页面', key: 'frontpageSoldUrl', width: 150, ellipsis: { tooltip: true },
    render: (row) => renderLink(row.frontpageSoldUrl) },
  { title: '海外仓库存', key: 'overseasWarehouseStock', width: 110, align: 'right',
    render: (row) => row.overseasWarehouseStock ?? '' },
  { title: '海外仓库龄', key: 'overseasWarehouseAge', width: 110, align: 'right',
    render: (row) => row.overseasWarehouseAge != null ? row.overseasWarehouseAge + '天' : '' },
  { title: '库销比', key: 'stockSalesRatio', width: 90, align: 'right',
    render: (row) => row.stockSalesRatio ?? '' },
  { title: '预估补货量', key: 'estimatedReplenish', width: 110, align: 'right',
    render: (row) => row.estimatedReplenish ?? '' },
  { title: '品牌', key: 'brand', width: 100 },
  { title: '操作员', key: 'operator', width: 100 },
  { title: '备注', key: 'remark', width: 180, ellipsis: { tooltip: true },
    render: (row) => row.remark || '' },
].map((c) => ({ ...c, resizable: true, minWidth: 70 }))

const scrollX = columns.reduce((s, c) => s + (Number(c?.width) || 100), 0)
</script>

<template>
  <section class="dashboard-page">
    <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:16px">
      <h2 class="users-title" style="margin:0">每日跟价</h2>
      <NSpace>
        <NButton @click="loadData" :loading="loading">刷新</NButton>
        <NButton type="primary" @click="handleExport">导出 Excel</NButton>
        <NButton type="info" :loading="uploading" @click="handleUpload">上传导入</NButton>
      </NSpace>
    </div>

    <!-- 搜索栏 -->
    <NCard size="small" class="dashboard-card" style="margin-bottom:16px">
      <NForm inline :model="filters" @keyup.enter="handleSearch">
        <NFormItem label="站点">
          <NInput v-model:value="filters.site" clearable placeholder="站点" style="width:120px" />
        </NFormItem>
        <NFormItem label="SKU">
          <NInput v-model:value="filters.sku" clearable placeholder="SKU模糊搜索" style="width:180px" />
        </NFormItem>
        <NFormItem label="品牌">
          <NInput v-model:value="filters.brand" clearable placeholder="品牌" style="width:120px" />
        </NFormItem>
        <NFormItem label="操作员">
          <NInput v-model:value="filters.operator" clearable placeholder="操作员" style="width:120px" />
        </NFormItem>
        <NFormItem>
          <NSpace size="small">
            <NButton type="primary" secondary @click="handleSearch" :loading="loading">查询</NButton>
            <NButton @click="handleReset">重置</NButton>
          </NSpace>
        </NFormItem>
      </NForm>
    </NCard>

    <NCard size="small" class="dashboard-card">
      <template #header-extra>
        <NTag size="small" :bordered="false">共 {{ total }} 条</NTag>
      </template>

      <NDataTable
        remote
        :loading="loading"
        :columns="columns"
        :data="records"
        :row-key="(row) => row.id"
        :scroll-x="scrollX"
        :max-height="510"
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
  </section>
</template>

<style scoped src="../assets/styles/user-management.css"></style>
