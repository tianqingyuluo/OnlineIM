function asHtmlElement(el: Element): HTMLElement | null {
  return el instanceof HTMLElement ? el : null
}

/** 折叠过渡：进入时撑开到内容高度 */
export function slideEnter(el: Element) {
  const node = asHtmlElement(el)
  if (!node) return
  node.style.height = `${node.scrollHeight}px`
}

/** 动画结束后清掉内联高度，避免把后续内容裁死 */
export function slideAfterEnter(el: Element) {
  const node = asHtmlElement(el)
  if (!node) return
  node.style.height = ''
}

export function slideBeforeLeave(el: Element) {
  const node = asHtmlElement(el)
  if (!node) return
  node.style.height = `${node.scrollHeight}px`
}

export function slideLeave(el: Element) {
  const node = asHtmlElement(el)
  if (!node) return
  node.style.height = '0'
}
