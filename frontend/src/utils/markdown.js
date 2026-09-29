// Minimal, dependency-free markdown -> HTML for the AI chat.
// Handles bold, italic, inline code, and bullet lists. Escapes HTML first.
export function renderMarkdown(text = '') {
  const esc = text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')

  return esc
    .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
    .replace(/_(.+?)_/g, '<em>$1</em>')
    .replace(/`(.+?)`/g, '<code>$1</code>')
    .replace(/^- (.*)$/gm, '<li>$1</li>')
    .replace(/(<li>.*<\/li>)/s, '<ul class="mb-0">$1</ul>')
    .replace(/\n{2,}/g, '<br/><br/>')
    .replace(/\n/g, '<br/>')
}
