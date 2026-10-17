// Vitest ortak kurulum. jsdom <dialog>.showModal()/close() sağlamıyor: yerel diyaloglar için en küçük yerine koyma.
if (typeof HTMLDialogElement !== 'undefined') {
  HTMLDialogElement.prototype.showModal = function showModal() {
    this.setAttribute('open', '')
  }
  HTMLDialogElement.prototype.close = function close() {
    this.removeAttribute('open')
    this.dispatchEvent(new Event('close'))
  }
}
