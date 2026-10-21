/**
 * 群聊 @提及 工具（纯函数，便于单测）。
 *
 * - 输入框里插入的是公开名 `@群昵称/昵称/用户名 `，好友备注只用于检索和在候选列表中提示，
 *   这样其他成员看到的文本与发送者无关。
 * - 发送时 at_users 只保留文本里仍然存在 `@名字` 的成员（删掉文字即取消提及）。
 * - 服务端会再做一次清洗（只保留当前群成员、去掉自己、最多 50 个）。
 * - @所有人 尚未实现：以后可在候选列表顶部加入一个 user_id 为 MENTION_ALL_ID 的条目，
 *   服务端 MentionUtil 中同样预留了位置。
 */

export const MENTION_ALL_ID = 'all'
export const MAX_MENTION_QUERY = 32

export interface MentionCandidate {
  user_id: string
  /** 插入到文本中的公开名 */
  label: string
  username?: string
  nickname?: string
  group_nickname?: string
  /** 当前用户给该好友的备注，只用于检索/提示 */
  remark?: string
  avatar_url?: string
  role?: string
}

export interface GroupMemberLike {
  user_info?: { user_id?: string; username?: string; nickname?: string; avatar_url?: string }
  group_nickname?: string | null
  role?: string
}

const clean = (value?: string | null) => (typeof value === 'string' ? value.trim() : '')

/** 群成员 → 候选项（排除自己）；remarks: user_id → 好友备注 */
export function buildMentionCandidates(
  members: GroupMemberLike[] | undefined | null,
  selfId: string | undefined,
  remarks: Record<string, string | undefined> = {},
): MentionCandidate[] {
  const seen = new Set<string>()
  const result: MentionCandidate[] = []
  for (const member of members || []) {
    const info = member.user_info
    const userId = clean(info?.user_id)
    if (!userId || userId === selfId || seen.has(userId)) continue
    seen.add(userId)
    const groupNickname = clean(member.group_nickname)
    const nickname = clean(info?.nickname)
    const username = clean(info?.username)
    const label = (groupNickname || nickname || username || userId).replace(/\s+/gu, '')
    result.push({
      user_id: userId,
      label,
      username: username || undefined,
      nickname: nickname || undefined,
      group_nickname: groupNickname || undefined,
      remark: clean(remarks[userId]) || undefined,
      avatar_url: info?.avatar_url || undefined,
      role: member.role,
    })
  }
  return result
}

export interface MentionQuery {
  /** '@' 在文本中的下标 */
  start: number
  /** '@' 之后、光标之前的检索词 */
  query: string
}

/**
 * 光标前是否正在输入 @检索词：'@' 之前不能是字母数字（避免邮箱），
 * '@' 到光标之间不能有空白或另一个 '@'。
 */
export function findMentionQuery(text: string, caret: number): MentionQuery | null {
  if (!text || caret <= 0) return null
  const before = text.slice(0, caret)
  const at = before.lastIndexOf('@')
  if (at < 0) return null
  const query = before.slice(at + 1)
  if (query.length > MAX_MENTION_QUERY || /[\s@]/u.test(query)) return null
  const prev = at > 0 ? before[at - 1] : ''
  if (prev && /[A-Za-z0-9._%+-]/u.test(prev)) return null
  return { start: at, query }
}

/** 按检索词过滤：群昵称/昵称/备注/用户名，前缀命中排在包含命中之前 */
export function filterMentionCandidates(candidates: MentionCandidate[], query: string, limit = 50): MentionCandidate[] {
  const q = query.trim().toLowerCase()
  if (!q) return candidates.slice(0, limit)
  const prefix: MentionCandidate[] = []
  const contains: MentionCandidate[] = []
  for (const candidate of candidates) {
    const fields = [candidate.label, candidate.group_nickname, candidate.nickname, candidate.remark, candidate.username]
      .filter((field): field is string => !!field)
      .map(field => field.toLowerCase())
    if (fields.some(field => field.startsWith(q))) prefix.push(candidate)
    else if (fields.some(field => field.includes(q))) contains.push(candidate)
  }
  return [...prefix, ...contains].slice(0, limit)
}

/** 用 `@label ` 替换 [start, caret) 的 @检索词，返回新文本与新光标位置 */
export function insertMention(text: string, start: number, caret: number, label: string): { text: string; caret: number } {
  const insert = `@${label} `
  const next = `${text.slice(0, start)}${insert}${text.slice(caret)}`
  return { text: next, caret: start + insert.length }
}

function containsMention(text: string, label: string): boolean {
  if (!label) return false
  const token = `@${label}`
  let index = text.indexOf(token)
  while (index >= 0) {
    const after = text.charAt(index + token.length)
    // 名字之后必须是结尾、空白或标点，避免 @张三 匹配到 @张三丰
    if (!after || /[\s@,，。.!！?？:：;；)）\]】]/u.test(after)) return true
    index = text.indexOf(token, index + 1)
  }
  return false
}

/** 发送前：只保留文本中仍有 `@label` 的已选成员 */
export function collectAtUsers(text: string, picked: Map<string, string>): string[] | undefined {
  const ids: string[] = []
  for (const [userId, label] of picked) {
    if (!ids.includes(userId) && containsMention(text, label)) ids.push(userId)
  }
  return ids.length ? ids : undefined
}

export interface MentionSegment {
  text: string
  /** 被提及成员的 user_id；普通文本为空 */
  userId?: string
}

/**
 * 气泡高亮：把 content 中属于 at_users 的 `@名字` 切成独立片段。
 * names: user_id → 该成员可能被插入的名字（群昵称/昵称/用户名）。
 */
export function splitMentionSegments(
  content: string,
  atUsers: string[] | undefined | null,
  names: Record<string, string[] | undefined>,
): MentionSegment[] {
  if (!content || !atUsers?.length) return [{ text: content }]
  const tokens: { token: string; userId: string }[] = []
  for (const userId of atUsers) {
    for (const name of names[userId] || []) {
      const label = clean(name).replace(/\s+/gu, '')
      if (label) tokens.push({ token: `@${label}`, userId })
    }
  }
  if (!tokens.length) return [{ text: content }]
  tokens.sort((a, b) => b.token.length - a.token.length)
  const segments: MentionSegment[] = []
  let plain = ''
  let i = 0
  while (i < content.length) {
    const hit = content[i] === '@' ? tokens.find(t => content.startsWith(t.token, i)) : undefined
    if (hit) {
      if (plain) segments.push({ text: plain })
      plain = ''
      segments.push({ text: hit.token, userId: hit.userId })
      i += hit.token.length
    } else {
      plain += content[i]
      i += 1
    }
  }
  if (plain) segments.push({ text: plain })
  return segments
}
