<script setup>
import { h, onMounted, ref } from 'vue'

import {
  NButton,
  NCard,
  NDataTable,
  NDatePicker,
  NInput,
  NInputNumber,
  NPopconfirm,
  NSelect,
  NSpace,
  dateZhCN,
  useMessage,
} from 'naive-ui'
import { useRouter } from 'vue-router'
import { fetchProductInfo, searchSkus, searchStores, searchWarehouses } from '@/api/purchasePlan'
import { submitPlans } from '@/api/purchaseSubmit'
import { useAuthStore } from '@/stores/auth'


const message = useMessage()
const router = useRouter()
const auth = useAuthStore()

function isDateDisabled(ts) {
  return ts < Date.now() - 24 * 60 * 60 * 1000
}

function createEmptyRow() {
  return {
    sku: '',
    sid: null,
    supplier_id: null,
    fnsku: '',
    wid: null,
    purchaser_id: null,
    expect_arrive_time: null,
    quantity_purchase: null,
    quantity_replenish: null,
    quantity_plan: null,
    remark: '',
    is_auto_fill_fnsku: 0,
    is_auto_fill_store: 0,
  }
}

const items = ref([createEmptyRow()])
const submitting = ref(false)

// searchable options
const skuOptions = ref([])
const storeOptions = ref([])
const warehouseOptions = ref([])
const skuLoading = ref(false)
const storeLoading = ref(false)
const warehouseLoading = ref(false)

async function onSkuSearch(keyword) {
  skuLoading.value = true
  try {
    const list = await searchSkus(keyword)
    skuOptions.value = list.map((s) => ({ label: s.sku, value: s.sku }))
  } catch (error) {
    skuOptions.value = []
    message.error(error instanceof Error ? error.message : '加载 SKU 列表失败')
  } finally { skuLoading.value = false }
}

async function onStoreSearch(keyword) {
  storeLoading.value = true
  try {
    const list = await searchStores(keyword)
    storeOptions.value = list.map((s) => ({ label: s.seller_name, value: s.sid }))
  } catch (error) {
    storeOptions.value = []
    message.error(error instanceof Error ? error.message : '加载店铺列表失败')
  } finally { storeLoading.value = false }
}

async function onWarehouseSearch(keyword) {
  warehouseLoading.value = true
  try {
    const list = await searchWarehouses(keyword)
    warehouseOptions.value = list.map((w) => ({ label: w.name, value: w.wid }))
  } catch (error) {
    warehouseOptions.value = []
    message.error(error instanceof Error ? error.message : '加载仓库列表失败')
  } finally { warehouseLoading.value = false }
}

async function autoFillRemark(row) {
  if (!row.sku || row.wid == null) return
  try {
    const info = await fetchProductInfo(row.sku, row.wid)
    if (info) {
      const profit = info.profitRate != null ? info.profitRate + '%' : '—'
      const sales = info.sales ?? 0
      row.remark = `${info.level || '—'} | ${sales} | ${profit}`
      if (info.maxReplenish != null) row.quantity_replenish = info.maxReplenish
      if (info.purchaseQuantity != null) row.quantity_purchase = Number(info.purchaseQuantity)
    }
  } catch {
    // 静默失败，不影响用户操作
  }
}

onMounted(() => {
  onSkuSearch('')
  onStoreSearch('')
  onWarehouseSearch('')
})

function addRow() {
  items.value.push(createEmptyRow())
}

function removeRow(index) {
  if (items.value.length <= 1) {
    items.value = [createEmptyRow()]
    return
  }
  items.value.splice(index, 1)
}

function isRowEmpty(row) {
  return (
    !String(row?.sku || '').trim() &&
    row?.wid == null &&
    row?.quantity_plan == null &&
    !String(row?.remark || '').trim()
  )
}

async function submitAll() {
  const meaningfulRows = items.value.filter((row) => !isRowEmpty(row))
  if (meaningfulRows.length === 0) {
    message.warning('请至少填写一条产品')
    return
  }

  const invalidIndex = meaningfulRows.findIndex(
    (row) =>
      !String(row?.sku || '').trim() ||
      row?.quantity_plan === null || row?.quantity_plan === undefined ||
      row?.wid == null ||
      !String(row?.remark || '').trim(),
  )
  if (invalidIndex >= 0) {
    message.warning(`第 ${invalidIndex + 1} 行 SKU/仓库/计划采购量/产品备注 不能为空`)
    return
  }

  submitting.value = true
  try {
    const payload = meaningfulRows.map((row) => {
      const item = {
        sku: String(row.sku || '').trim(),
        wid: row.wid,
        quantityPlan: row.quantity_plan,
        quantityPurchase: row.quantity_purchase || 0,
        quantityReplenish: row.quantity_replenish || 0,
        remark: String(row.remark || '').trim(),
      }

      if (row.sid) item.sid = row.sid
      if (row.supplier_id != null) item.supplierId = row.supplier_id
      if (row.fnsku) item.fnsku = row.fnsku
      if (row.purchaser_id != null) item.purchaserId = row.purchaser_id
      if (row.expect_arrive_time) item.expectArriveTime = row.expect_arrive_time

      return item
    })

    await submitPlans(payload)
    message.success(`提交成功，共 ${payload.length} 条`)
    items.value = [createEmptyRow()]
  } catch (err) {
    message.error(err instanceof Error ? err.message : '提交失败')
  } finally { submitting.value = false }
}

const columns = [
  {
    title: 'SKU',
    key: 'sku',
    width: 160,
    render: (row) =>
      h(NSelect, {
        value: row.sku,
        size: 'small',
        clearable: true,
        filterable: true,
        consistentMenuWidth: false,
        options: skuOptions.value,
        loading: skuLoading.value,
        placeholder: '搜索SKU',
        style: { width: '100%' },
        onSearch: onSkuSearch,
        'onUpdate:value': (val) => {
          row.sku = val || ''
          autoFillRemark(row)
        },
      }),
  },
  {
    title: '店铺',
    key: 'sid',
    width: 160,
    render: (row) =>
      h(NSelect, {
        value: row.sid,
        size: 'small',
        clearable: true,
        filterable: true,
        consistentMenuWidth: false,
        options: storeOptions.value,
        loading: storeLoading.value,
        placeholder: '搜索店铺',
        style: { width: '100%' },
        onSearch: onStoreSearch,
        'onUpdate:value': (val) => {
          row.sid = val || ''
        },
      }),
  },
  {
    title: '供应商ID',
    key: 'supplier_id',
    render: (row) =>
      h(NInputNumber, {
        value: row.supplier_id,
        size: 'small',
        min: 0,
        showButton: false,
        style: { width: '100%', minWidth: 0 },
        'onUpdate:value': (val) => {
          row.supplier_id = val
        },
      }),
  },
  {
    title: 'FNSKU',
    key: 'fnsku',
    render: (row) =>
      h(NInput, {
        value: row.fnsku,
        size: 'small',
        placeholder: 'FNSKU001',
        style: { width: '100%', minWidth: 0 },
        'onUpdate:value': (val) => {
          row.fnsku = val
        },
      }),
  },
  {
    title: '仓库',
    key: 'wid',
    width: 150,
    render: (row) =>
      h(NSelect, {
        value: row.wid,
        size: 'small',
        clearable: true,
        filterable: true,
        consistentMenuWidth: false,
        options: warehouseOptions.value,
        loading: warehouseLoading.value,
        placeholder: '搜索仓库',
        style: { width: '100%' },
        onSearch: onWarehouseSearch,
        'onUpdate:value': (val) => {
          row.wid = val ?? null
          autoFillRemark(row)
        },
      }),
  },
  {
    title: '采购方ID',
    key: 'purchaser_id',
    render: (row) =>
      h(NInputNumber, {
        value: row.purchaser_id,
        size: 'small',
        min: 0,
        showButton: false,
        style: { width: '100%', minWidth: 0 },
        'onUpdate:value': (val) => {
          row.purchaser_id = val
        },
      }),
  },
  {
    title: '采购员',
    key: 'purchaser_name',
    render: () =>
      h(NInput, { value: auth.ownerName, size: 'small', disabled: true, style: { width: '100%', minWidth: 0 } }),
  },
  {
    title: '采购数量',
    key: 'quantity_purchase',
    render: (row) =>
      h(NInputNumber, {
        value: row.quantity_purchase,
        size: 'small',
        min: 0,
        disabled: true,
        placeholder: '自动填充',
        style: { width: '100%', minWidth: 0 },
      }),
  },
  {
    title: '预估补货量',
    key: 'quantity_replenish',
    render: (row) =>
      h(NInputNumber, {
        value: row.quantity_replenish,
        size: 'small',
        min: 0,
        disabled: true,
        placeholder: '自动填充',
        style: { width: '100%', minWidth: 0 },
      }),
  },
  {
    title: '计划采购量',
    key: 'quantity_plan',
    render: (row) =>
      h(NInputNumber, {
        value: row.quantity_plan,
        size: 'small',
        min: 1,
        placeholder: '100',
        style: { width: '100%', minWidth: 0 },
        'onUpdate:value': (val) => {
          row.quantity_plan = val
        },
      }),
  },
  {
    title: '期望到货时间',
    key: 'expect_arrive_time',
    render: (row) =>
      h(NDatePicker, {
        type: 'date',
        size: 'small',
        locale: dateZhCN,
        valueFormat: 'yyyy-MM-dd',
        formattedValue: row.expect_arrive_time,
        style: { width: '100%', minWidth: 0 },
        isDateDisabled,
        'onUpdate:formattedValue': (val) => {
          row.expect_arrive_time = val ?? null
        },
      }),
  },
  {
    title: '产品备注',
    key: 'remark',
    render: (row) =>
      h(NInput, {
        value: row.remark,
        size: 'small',
        placeholder: '备注',
        style: { width: '100%', minWidth: 0 },
        'onUpdate:value': (val) => {
          row.remark = val
        },
      }),
  },
  {
    title: '自动FNSKU',
    key: 'is_auto_fill_fnsku',
    render: (row) =>
      h(NSelect, {
        value: row.is_auto_fill_fnsku,
        size: 'small',
        options: [
          { label: '否', value: 0 },
          { label: '是', value: 1 },
        ],
        style: { width: '100%', minWidth: 0 },
        'onUpdate:value': (val) => {
          row.is_auto_fill_fnsku = val ?? 0
        },
      }),
  },
  {
    title: '自动填充店铺',
    key: 'is_auto_fill_store',
    render: (row) =>
      h(NSelect, {
        value: row.is_auto_fill_store,
        size: 'small',
        options: [
          { label: '否', value: 0 },
          { label: '是', value: 1 },
        ],
        style: { width: '100%', minWidth: 0 },
        'onUpdate:value': (val) => {
          row.is_auto_fill_store = val ?? 0
        },
      }),
  },
  {
    title: '操作',
    key: 'actions',
    width: 120,
    fixed: 'right',
    render(row, index) {
      return h(NSpace, { size: 12, wrap: false }, {
        default: () => [
          h(
            NButton,
            { size: 'tiny', type: 'info', secondary: true, onClick: addRow, style: { minWidth: '30px' } },
            { default: () => '+' },
          ),
          h(
            NPopconfirm,
            {
              positiveText: '移除',
              negativeText: '取消',
              onPositiveClick: () => removeRow(index),
            },
            {
              default: () => '确认移除该行？',
              trigger: () =>
                h(
                  NButton,
                  { size: 'tiny', type: 'error', secondary: true, style: { minWidth: '30px' } },
                  { default: () => '-' },
                ),
            },
          ),
        ],
      })
    },
  },
]
</script>

<template>
  <section class="dashboard-page">
    <div style="margin-bottom:16px">
      <NButton size="small" @click="router.push({ name: 'purchases' })">← 返回采购列表</NButton>
    </div>
    <NCard title="创建采购计划" size="large" class="dashboard-card">
      <template #header-extra>
        <NSpace>
          <NButton type="success" size="small" :loading="submitting" @click="submitAll">
            提交 ({{ items.filter((row) => !isRowEmpty(row)).length }}条)
          </NButton>
        </NSpace>
      </template>

      <NDataTable
        class="purchase-plan-table"
        :columns="columns"
        :data="items"
        :row-key="(_, i) => i"
        :pagination="{ pageSize: 20 }"
        :max-height="500"
        :scroll-x="1800"
      />
    </NCard>
  </section>
</template>

<style scoped src="../assets/styles/dashboard-view.css"></style>

<style scoped>
.purchase-plan-table :deep(.n-data-table-td),
.purchase-plan-table :deep(.n-data-table-th) {
  padding: 8px 10px;
}

.purchase-plan-table :deep(.n-data-table-table) {
}

.purchase-plan-table :deep(.n-data-table-th__title) {
  white-space: nowrap;
}

.purchase-plan-table :deep(.n-data-table-td) {
  font-size: 12px;
}

.purchase-plan-table :deep(.n-data-table-td .n-input),
.purchase-plan-table :deep(.n-data-table-td .n-input-number),
.purchase-plan-table :deep(.n-data-table-td .n-base-selection) {
  width: 100%;
  min-width: 0;
  max-width: 100%;
}

.purchase-plan-table :deep(.n-data-table-td .n-input__input-el),
.purchase-plan-table :deep(.n-data-table-td .n-base-selection-label) {
  min-width: 0;
}
</style>
