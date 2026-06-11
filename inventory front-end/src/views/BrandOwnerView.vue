<script setup>
import { h, onMounted, reactive, ref } from 'vue'
import {
  NButton,
  NCard,
  NDataTable,
  NForm,
  NFormItem,
  NInput,
  NModal,
  NSelect,
  NSpace,
  NTag,
  useDialog,
  useMessage,
} from 'naive-ui'
import {
  createBrandOwner,
  deleteBrandOwner,
  fetchBrandOwnersPage,
  fetchDistinctBrandCodes,
  fetchDistinctOwnerNames,
  updateBrandOwner,
} from '@/api/brandOwners'

const message = useMessage()
const dialog = useDialog()

const loading = ref(false)
const records = ref([])
const total = ref(0)

const query = reactive({
  page: 1,
  size: 10,
  brandCode: '',
  ownerName: '',
})

// 下拉选项
const brandCodeOptions = ref([])
const ownerNameOptions = ref([])

async function loadOptions() {
  try {
    const [brandCodes, ownerNames] = await Promise.all([
      fetchDistinctBrandCodes(),
      fetchDistinctOwnerNames(),
    ])
    brandCodeOptions.value = (brandCodes || []).map((v) => ({ label: v, value: v }))
    ownerNameOptions.value = (ownerNames || []).map((v) => ({ label: v, value: v }))
  } catch {
    // 静默
  }
}

async function loadData() {
  loading.value = true
  try {
    const result = await fetchBrandOwnersPage({
      page: query.page,
      size: query.size,
      brandCode: query.brandCode.trim() || undefined,
      ownerName: query.ownerName.trim() || undefined,
    })
    records.value = result?.records || []
    total.value = Number(result?.total || 0)
  } catch (e) {
    message.error(e instanceof Error ? e.message : '加载失败')
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  query.page = 1
  loadData()
}

function handleReset() {
  query.brandCode = ''
  query.ownerName = ''
  query.page = 1
  loadData()
}

// ---- 新增 ----
const showCreateModal = ref(false)
const createForm = reactive({ brandCode: '', ownerName: '' })

function openCreateModal() {
  createForm.brandCode = ''
  createForm.ownerName = ''
  showCreateModal.value = true
}

async function submitCreate() {
  const brandCode = createForm.brandCode.trim()
  const ownerName = createForm.ownerName.trim()
  if (!brandCode || !ownerName) {
    message.warning('品牌编码和负责人不能为空')
    return
  }
  try {
    await createBrandOwner({ brandCode, ownerName })
    message.success('新增成功')
    showCreateModal.value = false
    loadOptions()
    loadData()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '新增失败')
  }
}

// ---- 编辑 ----
const showEditModal = ref(false)
const editForm = reactive({ id: '', brandCode: '', ownerName: '' })

function openEditModal(row) {
  editForm.id = row.id
  editForm.brandCode = row.brandCode
  editForm.ownerName = row.ownerName
  showEditModal.value = true
}

async function submitEdit() {
  const brandCode = editForm.brandCode.trim()
  const ownerName = editForm.ownerName.trim()
  if (!brandCode || !ownerName) {
    message.warning('品牌编码和负责人不能为空')
    return
  }
  try {
    await updateBrandOwner(editForm.id, { brandCode, ownerName })
    message.success('更新成功')
    showEditModal.value = false
    loadOptions()
    loadData()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '更新失败')
  }
}

// ---- 删除 ----
function confirmDelete(row) {
  dialog.warning({
    title: '确认删除',
    content: `确认删除 ${row.brandCode} — ${row.ownerName} 吗？`,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await deleteBrandOwner(row.id)
        message.success('删除成功')
        if (records.value.length === 1 && query.page > 1) {
          query.page -= 1
        }
        loadOptions()
        loadData()
      } catch (e) {
        message.error(e instanceof Error ? e.message : '删除失败')
      }
    },
  })
}

// ---- 表格列 ----
const columns = [
  { title: 'ID', key: 'id', width: 80 },
  { title: '品牌编码', key: 'brandCode', width: 180 },
  { title: '负责人', key: 'ownerName', width: 160 },
  {
    title: '操作',
    key: 'actions',
    width: 160,
    render(row) {
      return h(NSpace, { size: 8 }, {
        default: () => [
          h(NButton, { size: 'small', secondary: true, onClick: () => openEditModal(row) }, { default: () => '编辑' }),
          h(NButton, { size: 'small', type: 'error', secondary: true, onClick: () => confirmDelete(row) }, { default: () => '删除' }),
        ],
      })
    },
  },
]

const pagination = reactive({
  page: 1,
  pageSize: 10,
  showSizePicker: true,
  pageSizes: [10, 20, 50],
  'onUpdate:page': (p) => { query.page = p; loadData() },
  'onUpdate:pageSize': (s) => { query.size = s; query.page = 1; loadData() },
})

onMounted(() => {
  loadOptions()
  loadData()
})
</script>

<template>
  <section class="dashboard-page">
    <div class="users-header">
      <h2 class="users-title">品牌管理</h2>
    </div>

    <!-- 搜索区 -->
    <NCard size="small" class="dashboard-card">
      <NForm inline :model="query">
        <NFormItem label="品牌编码">
          <NSelect
            v-model:value="query.brandCode"
            :options="brandCodeOptions"
            filterable
            clearable
            placeholder="选择或输入"
            style="width: 200px"
          />
        </NFormItem>
        <NFormItem label="负责人">
          <NSelect
            v-model:value="query.ownerName"
            :options="ownerNameOptions"
            filterable
            clearable
            placeholder="选择或输入"
            style="width: 160px"
          />
        </NFormItem>
        <NFormItem>
          <NSpace size="small">
            <NButton type="primary" secondary :loading="loading" @click="handleSearch">查询</NButton>
            <NButton :disabled="loading" @click="handleReset">重置</NButton>
          </NSpace>
        </NFormItem>
      </NForm>
    </NCard>

    <!-- 数据区 -->
    <NCard size="small" class="dashboard-card">
      <template #header>
        <span>品牌归属列表</span>
      </template>
      <template #header-extra>
        <NSpace size="small">
          <NTag size="small" :bordered="false">共 {{ total }} 条</NTag>
          <NButton type="primary" size="small" @click="openCreateModal">新增</NButton>
        </NSpace>
      </template>

      <NDataTable
        remote
        :loading="loading"
        :columns="columns"
        :data="records"
        :row-key="(row) => row.id"
        :pagination="{
          ...pagination,
          page: query.page,
          pageSize: query.size,
          itemCount: total,
        }"
      />
    </NCard>

    <!-- 新增弹窗 -->
    <NModal v-model:show="showCreateModal" preset="card" title="新增品牌归属" style="width: 480px">
      <NForm :model="createForm" label-placement="left" label-width="88">
        <NFormItem label="品牌编码">
          <NSelect
            v-model:value="createForm.brandCode"
            :options="brandCodeOptions"
            filterable
            tag
            placeholder="选择或输入品牌编码"
          />
        </NFormItem>
        <NFormItem label="负责人">
          <NSelect
            v-model:value="createForm.ownerName"
            :options="ownerNameOptions"
            filterable
            tag
            placeholder="选择或输入负责人"
          />
        </NFormItem>
      </NForm>
      <template #footer>
        <NSpace justify="end">
          <NButton @click="showCreateModal = false">取消</NButton>
          <NButton type="primary" :loading="loading" @click="submitCreate">保存</NButton>
        </NSpace>
      </template>
    </NModal>

    <!-- 编辑弹窗 -->
    <NModal v-model:show="showEditModal" preset="card" title="编辑品牌归属" style="width: 480px">
      <NForm :model="editForm" label-placement="left" label-width="88">
        <NFormItem label="品牌编码">
          <NSelect
            v-model:value="editForm.brandCode"
            :options="brandCodeOptions"
            filterable
            tag
            placeholder="选择或输入品牌编码"
          />
        </NFormItem>
        <NFormItem label="负责人">
          <NSelect
            v-model:value="editForm.ownerName"
            :options="ownerNameOptions"
            filterable
            tag
            placeholder="选择或输入负责人"
          />
        </NFormItem>
      </NForm>
      <template #footer>
        <NSpace justify="end">
          <NButton @click="showEditModal = false">取消</NButton>
          <NButton type="primary" :loading="loading" @click="submitEdit">保存</NButton>
        </NSpace>
      </template>
    </NModal>
  </section>
</template>

<style scoped src="../assets/styles/user-management.css"></style>
