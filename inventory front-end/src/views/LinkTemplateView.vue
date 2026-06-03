<script setup>
import { h, onMounted, ref } from 'vue'
import { NButton, NCard, NDataTable, NInput, NModal, NForm, NFormItem, NPopconfirm, NSpace, NTag, useDialog, useMessage } from 'naive-ui'
import { fetchLinkTemplates, saveLinkTemplate, deleteLinkTemplate } from '@/api/linkTemplates'

const message = useMessage()
const dialog = useDialog()
const loading = ref(false)
const records = ref([])

const showModal = ref(false)
const isNew = ref(false)
const editForm = ref({ site: '', presaleUrl: '', soldUrl: '' })
const saving = ref(false)

async function loadData() {
  loading.value = true
  try {
    records.value = await fetchLinkTemplates() || []
  } catch (e) {
    message.error('加载失败')
  } finally {
    loading.value = false
  }
}

function openAdd() {
  isNew.value = true
  editForm.value = { site: '', presaleUrl: '', soldUrl: '' }
  showModal.value = true
}

function openEdit(row) {
  isNew.value = false
  editForm.value = { site: row.site, presaleUrl: row.presaleUrl || '', soldUrl: row.soldUrl || '' }
  showModal.value = true
}

async function submitSave() {
  if (!editForm.value.site.trim()) { message.warning('请输入站点'); return }
  saving.value = true
  try {
    await saveLinkTemplate(editForm.value.site.trim(), editForm.value.presaleUrl, editForm.value.soldUrl)
    message.success(isNew.value ? '已新增' : '已更新')
    showModal.value = false
    loadData()
  } catch (e) {
    message.error('保存失败')
  } finally {
    saving.value = false
  }
}

function confirmDelete(row) {
  dialog.warning({
    title: '确认删除',
    content: `确定删除 ${row.site} 的链接模板吗？`,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await deleteLinkTemplate(row.site)
        message.success('已删除')
        loadData()
      } catch (e) { message.error('删除失败') }
    },
  })
}

const columns = [
  { title: '站点', key: 'site', width: 100 },
  { title: '售前链接', key: 'presaleUrl', width: 400, ellipsis: { tooltip: true } },
  { title: '售后链接', key: 'soldUrl', width: 400, ellipsis: { tooltip: true } },
  { title: '操作', key: 'actions', width: 140, align: 'center',
    render(row) {
      return h(NSpace, { size: 'small', justify: 'center' }, {
        default: () => [
          h(NButton, { size: 'tiny', onClick: () => openEdit(row) }, { default: () => '编辑' }),
          h(NButton, { size: 'tiny', type: 'error', onClick: () => confirmDelete(row) }, { default: () => '删除' }),
        ],
      })
    },
  },
]

onMounted(() => loadData())
</script>

<template>
  <div class="dashboard-page">
    <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:4px">
      <h2 class="users-title" style="margin:0">eBay 链接管理</h2>
      <NSpace>
        <NButton @click="loadData" :loading="loading">刷新</NButton>
        <NButton type="primary" @click="openAdd">新增国家</NButton>
      </NSpace>
    </div>

    <NCard size="small">
      <NDataTable :loading="loading" :columns="columns" :data="records" :row-key="(row) => row.site" />
    </NCard>

    <NModal v-model:show="showModal" preset="card" :title="isNew ? '新增链接模板' : '编辑链接模板'" style="width: 700px">
      <NForm label-placement="left" label-width="80">
        <NFormItem label="站点">
          <NInput v-model:value="editForm.site" :disabled="!isNew" placeholder="如：法国" />
        </NFormItem>
        <NFormItem label="售前链接">
          <NInput v-model:value="editForm.presaleUrl" placeholder="{oe} 占位符替换为 OE 号" />
        </NFormItem>
        <NFormItem label="售后链接">
          <NInput v-model:value="editForm.soldUrl" placeholder="{oe} 占位符替换为 OE 号" />
        </NFormItem>
      </NForm>
      <template #footer>
        <NSpace justify="end">
          <NButton @click="showModal = false">取消</NButton>
          <NButton type="primary" :loading="saving" @click="submitSave">保存</NButton>
        </NSpace>
      </template>
    </NModal>
  </div>
</template>
