<script setup>
import { computed, h, onActivated, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  NButton, NCard, NDataTable, NForm, NFormItem, NInput, NInputNumber, NModal, NPopconfirm, NSpace, NTag, useDialog, useMessage,
} from 'naive-ui'
import { batchUpdateStatus, deleteSubmit, exportExcel, fetchSubmitPage, updateSubmit } from '@/api/purchaseSubmit'
import { isLeader } from '@/api/team'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const message = useMessage()
const dialog = useDialog()
const auth = useAuthStore()

const loading = ref(false)
const records = ref([])
const total = ref(0)
const checkedRowKeys = ref([])
const canApprove = ref(false)

const query = reactive({ page: 1, size: 10, sku: '', creator: '' })

function handleSearch() {
  query.page = 1
  loadData()
}

function handleReset() {
  query.sku = ''
  query.creator = ''
  query.page = 1
  loadData()
}

async function handleExport() {
  try {
    await exportExcel()
    message.success('导出成功')
  } catch (e) {
    message.error(e instanceof Error ? e.message : '导出失败')
  }
}

async function checkPermission() {
  if (auth.isAdmin) { canApprove.value = true; return }
  const owner = auth.ownerName
  if (owner) {
    try { canApprove.value = await isLeader(owner) === true }
    catch { canApprove.value = false }
  }
}

function formatTime(v) {
  if (!v) return ''
  return String(v).replace('T', ' ').substring(0, 19)
}

async function loadData() {
  loading.value = true
  try {
    const result = await fetchSubmitPage({
      page: query.page, size: query.size,
      sku: query.sku.trim() || undefined,
      creator: query.creator.trim() || undefined,
    })
    records.value = result?.records || []
    total.value = Number(result?.total || 0)
  } catch (e) {
    message.error(e instanceof Error ? e.message : '加载失败')
  } finally {
    loading.value = false
  }
}

// 单条操作
async function handleApprove(row) {
  try {
    await batchUpdateStatus([row.id], '已审批')
    message.success('已审批')
    loadData()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '操作失败')
  }
}

async function handleRevoke(row) {
  dialog.warning({
    title: '确认驳回',
    content: `确定将 ${row.sku} 的采购计划驳回吗？`,
    positiveText: '确定驳回',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await batchUpdateStatus([row.id], '已驳回')
        message.success('已驳回')
        loadData()
      } catch (e) {
        message.error(e instanceof Error ? e.message : '操作失败')
      }
    },
  })
}

// 编辑
const showEditModal = ref(false)
const editForm = reactive({ id: '', quantityPlan: null, remark: '' })
function openEdit(row) {
  editForm.id = row.id
  editForm.quantityPlan = row.quantityPlan
  editForm.remark = row.remark || ''
  showEditModal.value = true
}
async function submitEdit() {
  try {
    await updateSubmit(editForm.id, { quantityPlan: editForm.quantityPlan, remark: editForm.remark })
    message.success('更新成功')
    showEditModal.value = false
    loadData()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '更新失败')
  }
}

// 批量操作
async function batchStatus(status) {
  if (checkedRowKeys.value.length === 0) {
    message.warning('请至少选择一条记录')
    return
  }
  try {
    await batchUpdateStatus(checkedRowKeys.value, status)
    message.success(`已${status} ${checkedRowKeys.value.length} 条`)
    checkedRowKeys.value = []
    loadData()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '操作失败')
  }
}

function confirmBatchDelete() {
  if (checkedRowKeys.value.length === 0) {
    message.warning('请至少选择一条记录')
    return
  }
  dialog.warning({
    title: '确认删除',
    content: `确定删除选中的 ${checkedRowKeys.value.length} 条记录吗？`,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await Promise.all(checkedRowKeys.value.map((id) => deleteSubmit(id)))
        message.success('删除成功')
        checkedRowKeys.value = []
        loadData()
      } catch (e) {
        message.error(e instanceof Error ? e.message : '删除失败')
      }
    },
  })
}

function getStatusType(s) {
  if (s === '已审批' || s === '已通过') return 'success'
  if (s === '已驳回' || s === '已驳回') return 'error'
  return 'info'
}

const columns = [
  { type: 'selection', multiple: true, width: 40 },
  { title: 'SKU', key: 'sku', width: 160, ellipsis: { tooltip: true } },
  { title: '仓库', key: 'warehouseName', width: 160, ellipsis: { tooltip: true } },
  { title: '采购数量', key: 'quantityPurchase', width: 100, align: 'center',
    render(row) { return row.quantityPurchase != null ? row.quantityPurchase : '—' },
  },
  { title: '预估补货量', key: 'quantityReplenish', width: 100, align: 'center' },
  { title: '计划采购量', key: 'quantityPlan', width: 100, align: 'center' },
  {
    title: '备注', key: 'remark', width: 180, ellipsis: { tooltip: true },
    render(row) { return row.remark || '—' },
  },
  {
    title: '状态', key: 'statusText', width: 90, align: 'center',
    render(row) {
      return h(NTag, { size: 'small', type: getStatusType(row.statusText), bordered: false },
        { default: () => row.statusText || '已提交' })
    },
  },
  {
    title: '审批人', key: 'approver', width: 100,
    render(row) { return row.approver || '—' },
  },
  {
    title: '审批时间', key: 'approveTime', width: 170,
    render(row) { return formatTime(row.approveTime) },
  },
  {
    title: '创建人', key: 'creatorOwnerName', width: 120,
    render(row) { return row.creatorOwnerName || row.creatorAccount || '—' },
  },
  {
    title: '提交时间', key: 'submitTime', width: 170,
    render(row) { return formatTime(row.submitTime) },
  },
  {
    title: '期望到货', key: 'expectArriveTime', width: 120,
    render(row) { return row.expectArriveTime || '—' },
  },
  {
    title: '操作', key: 'actions', width: 220, align: 'center', fixed: 'right',
    render(row) {
      if (!canApprove.value) return h('span', { style: { color: '#ccc' } }, '—')
      return h('div', { style: { display: 'flex', justifyContent: 'center', gap: '8px' } }, [
        h(NButton, { size: 'tiny', secondary: true,
          onClick: () => openEdit(row) }, { default: () => '编辑' }),
        h(NButton, { size: 'tiny', type: 'success', secondary: true,
          disabled: row.statusText === '已审批' || row.statusText === '已驳回',
          onClick: () => handleApprove(row) }, { default: () => '审批' }),
        h(NButton, { size: 'tiny', type: 'error', secondary: true,
          disabled: row.statusText === '已驳回',
          onClick: () => handleRevoke(row) }, { default: () => '驳回' }),
      ])
    },
  },
]

onActivated(() => { checkPermission(); loadData() })
</script>

<template>
  <section class="dashboard-page">
    <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:16px">
      <h2 class="users-title" style="margin:0">采购管理</h2>
      <NSpace>
        <NButton @click="loadData" :loading="loading">刷新</NButton>
        <NButton type="primary" @click="handleExport">导出 Excel</NButton>
        <NButton type="primary" @click="router.push({ name: 'purchasePlanCreate' })">创建采购计划</NButton>
      </NSpace>
    </div>

    <!-- 搜索栏 -->
    <NCard size="small" class="dashboard-card" style="margin-bottom:16px">
      <NForm inline :model="query" @keyup.enter="handleSearch">
        <NFormItem label="SKU">
          <NInput v-model:value="query.sku" clearable placeholder="输入SKU模糊搜索" style="width:200px" />
        </NFormItem>
        <NFormItem label="创建人">
          <NInput v-model:value="query.creator" clearable placeholder="输入创建人" style="width:160px" />
        </NFormItem>
        <NFormItem>
          <NSpace size="small">
            <NButton type="primary" secondary @click="handleSearch">查询</NButton>
            <NButton @click="handleReset">重置</NButton>
          </NSpace>
        </NFormItem>
      </NForm>
    </NCard>

    <NCard size="small" class="dashboard-card" v-if="canApprove && checkedRowKeys.length > 0">
      <NSpace align="center" size="small">
        <span style="color:rgba(0,0,0,0.65)">已选 {{ checkedRowKeys.length }} 条</span>
        <NButton size="small" type="success" @click="batchStatus('已审批')">批量审批</NButton>
        <NButton size="small" type="error" @click="batchStatus('已驳回')">批量驳回</NButton>
        <NButton size="small" type="error" @click="confirmBatchDelete">批量删除</NButton>
      </NSpace>
    </NCard>

    <NCard size="small" class="dashboard-card" v-else-if="checkedRowKeys.length > 0">
      <NSpace align="center" size="small">
        <span style="color:rgba(0,0,0,0.65)">已选 {{ checkedRowKeys.length }} 条</span>
        <NButton size="small" type="error" @click="confirmBatchDelete">批量删除</NButton>
      </NSpace>
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
        :scroll-x="1800"
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
    <NModal v-model:show="showEditModal" preset="card" title="编辑采购计划" style="width: 420px">
      <NForm :model="editForm" label-placement="left" label-width="100">
        <NFormItem label="计划采购量">
          <NInputNumber v-model:value="editForm.quantityPlan" min="1" style="width:100%" />
        </NFormItem>
        <NFormItem label="备注">
          <NInput v-model:value="editForm.remark" style="width:100%" />
        </NFormItem>
      </NForm>
      <template #footer>
        <NSpace justify="end">
          <NButton @click="showEditModal = false">取消</NButton>
          <NButton type="primary" @click="submitEdit">保存</NButton>
        </NSpace>
      </template>
    </NModal>
  </section>
</template>

<style scoped src="../assets/styles/user-management.css"></style>
