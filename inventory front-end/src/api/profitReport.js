import { apiPost } from '@/api/request'

export async function uploadProfitReport(file) {
  const form = new FormData()
  form.append('file', file)
  return apiPost('/api/profit-report/upload', form)
}
