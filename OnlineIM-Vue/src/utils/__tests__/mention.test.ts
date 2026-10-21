import { describe, expect, it } from 'vitest'
import {
  buildMentionCandidates,
  collectAtUsers,
  filterMentionCandidates,
  findMentionQuery,
  insertMention,
  splitMentionSegments,
} from '@/utils/mention'

const members = [
  { user_info: { user_id: 'usr_me', username: 'me', nickname: '我' }, group_nickname: '' },
  { user_info: { user_id: 'usr_a', username: 'alice', nickname: 'Alice Wang' }, group_nickname: '' },
  { user_info: { user_id: 'usr_b', username: 'bob', nickname: '鲍勃' }, group_nickname: '群主大人' },
  { user_info: { user_id: 'usr_c', username: 'carol', nickname: '' }, group_nickname: null },
  { user_info: { user_id: 'usr_a', username: 'alice', nickname: 'dup' } },
]

describe('mention utils', () => {
  it('builds candidates without self/duplicates and prefers group nickname > nickname > username', () => {
    const list = buildMentionCandidates(members, 'usr_me', { usr_c: '小卡' })
    expect(list.map(c => [c.user_id, c.label, c.remark])).toEqual([
      ['usr_a', 'AliceWang', undefined],
      ['usr_b', '群主大人', undefined],
      ['usr_c', 'carol', '小卡'],
    ])
  })

  it('detects an @ query before the caret but not inside e-mail addresses or after whitespace', () => {
    expect(findMentionQuery('hi @', 4)).toEqual({ start: 3, query: '' })
    expect(findMentionQuery('你好@群主', 5)).toEqual({ start: 2, query: '群主' })
    expect(findMentionQuery('mail a@b.com', 12)).toBeNull()
    expect(findMentionQuery('@bob done', 9)).toBeNull()
    expect(findMentionQuery('@bob done', 4)).toEqual({ start: 0, query: 'bob' })
    expect(findMentionQuery('no mention', 10)).toBeNull()
  })

  it('filters by group nickname, nickname, remark and username with prefix matches first', () => {
    const list = buildMentionCandidates(members, 'usr_me', { usr_c: '小卡' })
    expect(filterMentionCandidates(list, '').length).toBe(3)
    expect(filterMentionCandidates(list, '鲍').map(c => c.user_id)).toEqual(['usr_b'])
    expect(filterMentionCandidates(list, '小卡').map(c => c.user_id)).toEqual(['usr_c'])
    expect(filterMentionCandidates(list, 'ALI').map(c => c.user_id)).toEqual(['usr_a'])
    expect(filterMentionCandidates(list, 'o').map(c => c.user_id)).toEqual(['usr_b', 'usr_c'])
    expect(filterMentionCandidates(list, 'zzz')).toEqual([])
  })

  it('inserts the label and keeps only mentions still present in the text', () => {
    const inserted = insertMention('hi @gr there', 3, 6, '群主大人')
    expect(inserted).toEqual({ text: 'hi @群主大人  there', caret: 9 })
    const picked = new Map([['usr_b', '群主大人'], ['usr_a', 'AliceWang'], ['usr_c', 'carol']])
    expect(collectAtUsers('hi @群主大人 和 @carol，看看', picked)).toEqual(['usr_b', 'usr_c'])
    expect(collectAtUsers('hi @群主大人2', new Map([['usr_b', '群主大人']]))).toBeUndefined()
    expect(collectAtUsers('nothing', picked)).toBeUndefined()
  })

  it('splits content into highlighted mention segments, longest name first', () => {
    const names = { usr_a: ['张三丰'], usr_b: ['张三'], usr_x: [] }
    expect(splitMentionSegments('@张三丰 和 @张三 好', ['usr_a', 'usr_b'], names)).toEqual([
      { text: '@张三丰', userId: 'usr_a' },
      { text: ' 和 ' },
      { text: '@张三', userId: 'usr_b' },
      { text: ' 好' },
    ])
    expect(splitMentionSegments('@张三 好', null, names)).toEqual([{ text: '@张三 好' }])
    expect(splitMentionSegments('@张三 好', ['usr_x'], names)).toEqual([{ text: '@张三 好' }])
  })
})
