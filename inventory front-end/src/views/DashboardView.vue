<script setup>
import { computed, h, onMounted, reactive, ref } from 'vue'
import {
  NButton,
  NCard,
  NCheckbox,
  NDataTable,
  NForm,
  NFormItem,
  NInput,
  NSelect,
  NSpace,
  NTag,
  useMessage,
} from 'naive-ui'
import { fetchInventoryOverview, fetchInventoryOverviewWarehouses } from '@/api/inventoryOverview'
import { syncAll } from '@/api/sync'
import { uploadEbaySales } from '@/api/ebaySales'
import { useAuthStore } from '@/stores/auth'


const message = useMessage()
const authStore = useAuthStore()
const isAdmin = computed(() => authStore.isAdmin)

const loading = ref(false)
const syncing = ref(false)
const uploading = ref(false)
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

// 数据由后端完成筛选，前端直接使用返回结果
const filteredRows = computed(() => replenishRows.value)

function renderRatioTag(value) {
  if (value === undefined || value === null || value === '') {
    return ''
  }

  return String(value)
}

const replenishColumns = [
  {
    title: '站点',
    key: 'warehouseNames',
    width: 220,
    ellipsis: true,
    fixed: 'left',
  },
  {
    title: 'sku',
    key: 'sku',
    width: 150,
    ellipsis: true,
    fixed: 'left',
  },
  {
    title: '产品名称',
    key: 'productName',
    width: 180,
    ellipsis: true,
    fixed: 'left',
  },
  {
    title: '近30利润',
    key: 'last30DaysProfit',
    width: 140,
    render: (row) =>
      row.last30DaysProfit === null || row.last30DaysProfit === undefined
        ? ''
        : row.last30DaysProfit,
  },
  {
    title: '海外在途',
    key: 'overseasOnway',
    width: 110,
    render: (row) =>
      row.overseasOnway === null || row.overseasOnway === undefined ? '' : row.overseasOnway,
  },
  {
    title: '海外可售',
    key: 'overseasSellable',
    width: 110,
    render: (row) =>
      row.overseasSellable === null || row.overseasSellable === undefined
        ? ''
        : row.overseasSellable,
  },
  {
    title: '海外总库存',
    key: 'overseasTotal',
    width: 120,
    render: (row) =>
      row.overseasTotal === null || row.overseasTotal === undefined ? '' : row.overseasTotal,
  },
  {
    title: '采购待交付',
    key: 'purchasePendingDelivery',
    width: 110,
    render: (row) =>
      row.purchasePendingDelivery === null || row.purchasePendingDelivery === undefined
        ? ''
        : row.purchasePendingDelivery,
  },
  {
    title: '成都可售',
    key: 'localSellable',
    width: 110,
    render: (row) =>
      row.localSellable === null || row.localSellable === undefined ? '' : row.localSellable,
  },
  {
    title: '成都在途',
    key: 'localOnway',
    width: 110,
    render: (row) =>
      row.localOnway === null || row.localOnway === undefined ? '' : row.localOnway,
  },
  {
    title: '采购计划',
    key: 'purchasePlan',
    width: 120,
    ellipsis: true,
    render: (row) =>
      row.purchasePlan === null || row.purchasePlan === undefined ? '' : row.purchasePlan,
  },
  {
    title: '待出库',
    key: 'lockNum',
    width: 100,
    render: (row) => (row.lockNum === null || row.lockNum === undefined ? '' : row.lockNum),
  },
  {
    title: '整个周期总库存',
    key: 'totalInventory',
    width: 140,
    render: (row) =>
      row.totalInventory === null || row.totalInventory === undefined ? '' : row.totalInventory,
  },
  {
    title: '近7天销量',
    key: 'last7DaysSales',
    width: 110,
    render: (row) =>
      row.last7DaysSales === null || row.last7DaysSales === undefined ? '' : row.last7DaysSales,
  },
  {
    title: '近30天销量',
    key: 'last30DaysSales',
    width: 110,
    render: (row) =>
      row.last30DaysSales === null || row.last30DaysSales === undefined ? '' : row.last30DaysSales,
  },
  {
    title: '近3月均销量',
    key: 'last90DaysSales',
    width: 120,
    render: (row) => {
      if (
        row.last90DaysSales === null ||
        row.last90DaysSales === undefined ||
        row.last90DaysSales === ''
      ) {
        return ''
      }

      const value = Number(row.last90DaysSales)
      if (!Number.isFinite(value)) {
        return ''
      }

      const avg = value / 3
      return Number.isInteger(avg) ? String(avg) : avg.toFixed(2).replace(/\.?0+$/, '')
    },
  },
  {
    title: '历史1个月最大月销',
    key: 'maxMonthlySales',
    width: 170,
    render: (row) =>
      row.maxMonthlySales === null || row.maxMonthlySales === undefined ? '' : row.maxMonthlySales,
  },
  {
    title: '海外在库库销比',
    key: 'overseasInStockRatio',
    width: 140,
    render: (row) => renderRatioTag(row.overseasInStockRatio),
  },
  {
    title: '海外总库销比',
    key: 'overseasTotalRatio',
    width: 140,
    render: (row) => renderRatioTag(row.overseasTotalRatio),
  },
  {
    title: '总库存库销比',
    key: 'totalInventoryRatio',
    width: 140,
    render: (row) => renderRatioTag(row.totalInventoryRatio),
  },
  {
    title: '最近成都仓出库创建时间',
    key: 'lastLocalOutboundTime',
    width: 200,
    ellipsis: true,
    render: (row) =>
      row.lastLocalOutboundTime === null || row.lastLocalOutboundTime === undefined
        ? ''
        : row.lastLocalOutboundTime,
  },
  {
    title: '出库天数',
    key: 'outboundDays',
    width: 260,
    render: (row) =>
      row.outboundDays === null || row.outboundDays === undefined ? '' : row.outboundDays,
  },
  {
    title: '采购周期',
    key: 'purchaseCycle',
    width: 360,
    render: (row) =>
      row.purchaseCycle === null || row.purchaseCycle === undefined ? '' : row.purchaseCycle,
  },
  {
    title: '采购数量',
    key: 'purchaseQuantity',
    width: 300,
    render: (row) =>
      row.purchaseQuantity === null || row.purchaseQuantity === undefined
        ? ''
        : row.purchaseQuantity,
  },
  {
    title: '最大月销预估补货量',
    key: 'maxMonthlyReplenish',
    width: 180,
    render: (row) =>
      row.maxMonthlyReplenish === null || row.maxMonthlyReplenish === undefined
        ? ''
        : row.maxMonthlyReplenish,
  },
  {
    title: '负责人',
    key: 'owner',
    width: 120,
    render: (row) => (row.owner === null || row.owner === undefined ? '' : row.owner),
  },
].map((column) => ({
  ...column,
  resizable: true,
  minWidth: 80,
}))

const replenishScrollX = replenishColumns.reduce((sum, column) => {
  const width = Number(column?.width)
  return sum + (Number.isFinite(width) ? width : 120)
}, 0)

const replenishMaxHeight = 560

async function loadWarehouseOptions() {
  warehouseLoading.value = true

  try {
    const list = await fetchInventoryOverviewWarehouses()
    warehouseOptions.value = Array.isArray(list)
      ? list
          .map((item) => {
            const label = item?.label ? String(item.label) : ''
            if (!label) return null
            return { label, value: label }
          })
          .filter(Boolean)
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
  const warehouseParam =
    selected.length > 0 && selected.length < optionCount ? selected.join(',') : undefined

  try {
    const list = await fetchInventoryOverview({
      sku: filters.sku.trim() || undefined,
      warehouse: warehouseParam,
    })
    if (seq !== loadSeq) return
    replenishRows.value = Array.isArray(list) ? list : []
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

async function handleSyncAll() {
  syncing.value = true
  try {
    const res = await syncAll()
    message.success('全量同步完成，刷新页面查看最新数据')
    console.log('sync result:', res)
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
    uploading.value = true
    try {
      const res = await uploadEbaySales(file)
      message.success(`上传成功，新增${res.inserted}条，更新${res.updated}条`)
    } catch (err) {
      message.error(err instanceof Error ? err.message : '上传失败')
    } finally {
      uploading.value = false
    }
  }
  input.click()
}

function handleReset() {
  filters.warehouseNames = []
  filters.sku = ''
  loadInventoryOverview()
}

function renderWarehouseOption({ node, option, selected }) {
  return h(
    'div',
    {
      style: { display: 'flex', alignItems: 'center', gap: '8px', width: '100%' },
      onClick: node?.props?.onClick,
      onMouseenter: node?.props?.onMouseenter,
      onMousemove: node?.props?.onMousemove,
    },
    [
      h(NCheckbox, { checked: selected, style: { pointerEvents: 'none' } }),
      h('span', { style: { flex: 1, minWidth: 0 } }, option.label || option.value),
    ],
  )
}
</script>

<template>
  <section class="dashboard-page">
    <NCard title="补货" size="large" class="dashboard-card">
      <template #header-extra>
        <NSpace align="center" size="small">
          <NTag type="info" :bordered="false">更新时间 {{ updatedAt || '-' }}</NTag>
          <NTag size="small" :bordered="false">共 {{ filteredRows.length }} 条</NTag>
          <NButton size="small" secondary :loading="loading" @click="loadInventoryOverview">
            刷新
          </NButton>
          <NButton v-if="isAdmin" size="small" type="warning" :loading="syncing" @click="handleSyncAll">
            拉取最新数据
          </NButton>
          <NButton size="small" type="info" :loading="uploading" @click="handleUploadExcel">
            上传销量报表
          </NButton>

        </NSpace>
      </template>

      <NForm inline :model="filters">
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
            style="width: 100px"
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
      <div style="height: 12px"></div>
      <NDataTable
        :loading="loading"
        :columns="replenishColumns"
        :data="filteredRows"
        :bordered="false"
        :scroll-x="replenishScrollX"
        :max-height="replenishMaxHeight"
        :row-key="(row) => `${String(row?.warehouseNames ?? '').trim()}|${String(row?.sku ?? '').trim()}`"
        :pagination="{ pageSize: 100 }"
      />
    </NCard>
  </section>
</template>

<style scoped src="../assets/styles/dashboard-view.css"></style>
