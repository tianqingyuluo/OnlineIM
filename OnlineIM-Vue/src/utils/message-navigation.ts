export function focusMessageElement(
  container: HTMLElement,
  messageId: string,
  highlightMs = 2000,
): boolean {
  const element = [...container.querySelectorAll<HTMLElement>('[data-message-id]')]
    .find(candidate => candidate.dataset.messageId === messageId);
  if (!element) return false;

  element.scrollIntoView({ behavior: 'smooth', block: 'center' });
  element.classList.add('reply-focus-highlight');
  window.setTimeout(() => element.classList.remove('reply-focus-highlight'), highlightMs);
  return true;
}
