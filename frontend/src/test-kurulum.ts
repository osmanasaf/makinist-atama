// Vitest ortak kurulum. jsdom <dialog>.showModal()/close() sağlamıyor: yerel diyaloglar için en küçük yerine koyma.
// Gerçek tarayıcı davranışı taklit edilir: modal açıkken dışındaki öğeler "inert"tir (focus() sessizce etkisiz) ve
// showModal odağı diyalogun içine alır. Böyle olmazsa "kapanınca odak geri döner" testleri gerçeği yansıtmaz (QA BUG-1).
if (typeof HTMLDialogElement !== 'undefined') {
  const odakla = HTMLElement.prototype.focus
  HTMLElement.prototype.focus = function focus(this: HTMLElement, secenek?: FocusOptions) {
    const modal = document.querySelector('dialog[open]') // açık (modal) diyalog: dışındakiler inert
    if (modal && !modal.contains(this)) return
    odakla.call(this, secenek)
  }
  HTMLDialogElement.prototype.showModal = function showModal(this: HTMLDialogElement) {
    this.setAttribute('open', '')
    this.querySelector<HTMLElement>(
      '[autofocus], button, input, select, textarea, a[href], [tabindex]',
    )?.focus()
  }
  HTMLDialogElement.prototype.close = function close(this: HTMLDialogElement) {
    if (!this.hasAttribute('open')) return
    this.removeAttribute('open')
    this.dispatchEvent(new Event('close'))
  }
}
