import { apiDelete, apiGet, apiPost } from '@/api/request'

export function fetchLinkTemplates() {
  return apiGet('/api/daily-price-tracking/link-templates')
}

export function saveLinkTemplate(site, presaleUrl, soldUrl) {
  return apiPost('/api/daily-price-tracking/link-template', { site, presaleUrl, soldUrl })
}

export function deleteLinkTemplate(site) {
  return apiDelete('/api/daily-price-tracking/link-template', { body: { site } })
}
