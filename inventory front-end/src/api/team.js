import { apiDelete, apiGet, apiPost } from '@/api/request'

/** 获取组长的所有组员 */
export function fetchMembers(leader) {
  return apiGet('/api/team/members', { leader })
}

/** 添加组员 */
export function addMember(leader, member) {
  return apiPost('/api/team', { leader, member })
}

/** 移除组员 */
export function removeMember(leader, member) {
  return apiDelete(`/api/team?leader=${encodeURIComponent(leader)}&member=${encodeURIComponent(member)}`)
}

/** 设为组长 */
export function setLeader(ownerName) {
  return apiPost('/api/team/set-leader', { ownerName })
}

/** 取消组长 */
export function cancelLeader(leader) {
  return apiDelete(`/api/team/leader?leader=${encodeURIComponent(leader)}`)
}

/** 检查是否是组长 */
export function isLeader(ownerName) {
  return apiGet('/api/team/is-leader', { ownerName })
}

/** 获取可添加为组员的人员列表 */
export function fetchAvailableMembers(excludeLeader) {
  return apiGet('/api/team/available-members', { excludeLeader })
}

/** 获取全部团队关系 */
export function fetchAllTeams() {
  return apiGet('/api/team/all')
}
