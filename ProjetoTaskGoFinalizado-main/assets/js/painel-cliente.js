let usuarioAtual = null;
let avaliacaoEmAndamentoId = null;

document.addEventListener('DOMContentLoaded', () => {
  usuarioAtual = exigirSessao('CLIENTE');
  if (!usuarioAtual) return;

  document.getElementById('nomeClienteHeader').textContent = `${usuarioAtual.nome}!`;
  carregarPedidos();
});

function mensagemDeErro(erro) {
  return erro instanceof TaskGoAPI.ApiError ? erro.message : 'Ocorreu um erro inesperado. Tente novamente.';
}

function formatarMoeda(valor) {
  return 'R$ ' + Number(valor || 0).toFixed(2).replace('.', ',');
}

// =====================================================================
// CONTROLE DE ABAS (mantido)
// =====================================================================
function mudarAba(event, idAba) {
  if (event) event.preventDefault();

  document.querySelectorAll('.sidebar-nav .nav-item').forEach((l) => l.classList.remove('active'));
  if (event) event.currentTarget.classList.add('active');

  document.querySelectorAll('.conteudo-aba').forEach((a) => a.classList.remove('active'));
  document.getElementById(idAba).classList.add('active');

  if (idAba === 'aba-rastreamento') {
    setTimeout(() => {
      initMap();
      if (window.meuMapa) {
        window.meuMapa.invalidateSize(true);
        window.dispatchEvent(new Event('resize'));
      }
    }, 50);
    setTimeout(() => {
      if (window.meuMapa) window.meuMapa.invalidateSize(true);
    }, 600);
  }
}

function showToast(message, type = 'success') {
  const container = document.getElementById('toast-container');
  const toast = document.createElement('div');
  toast.style.cssText = `background: var(--card-bg); border: 1px solid var(--border-color); border-left: 4px solid ${type === 'error' ? 'var(--danger-red)' : 'var(--success-green)'}; color: white; padding: 16px 24px; border-radius: 12px; box-shadow: 0 10px 30px rgba(0,0,0,0.5); display: flex; align-items: center; gap: 12px; transform: translateX(120%); opacity: 0; transition: 0.4s; font-weight: 600; font-size: 14px;`;
  const icone = type === 'error' ? 'fa-circle-exclamation' : 'fa-check-circle';
  toast.innerHTML = `<i class="fas ${icone}" style="color: ${type === 'error' ? 'var(--danger-red)' : 'var(--success-green)'}; font-size: 20px;"></i> <span>${message}</span>`;
  container.appendChild(toast);
  setTimeout(() => { toast.style.transform = 'translateX(0)'; toast.style.opacity = '1'; }, 10);
  setTimeout(() => { toast.style.transform = 'translateX(120%)'; toast.style.opacity = '0'; setTimeout(() => toast.remove(), 400); }, 3500);
}

function openModal(id) {
  const modal = document.getElementById(id);
  if (modal) modal.style.display = 'flex';
}

function closeModal(id) {
  const modal = document.getElementById(id);
  if (modal) modal.style.display = 'none';
}

window.onclick = function (e) {
  if (e.target.classList.contains('modal-overlay')) e.target.style.display = 'none';
};

// =====================================================================
// MEUS PEDIDOS (US-04, US-06, US-09, US-10) — dados reais
// =====================================================================
const ROTULOS_PEDIDO = {
  SOLICITADO: { texto: 'AGUARDANDO PROFISSIONAL', classe: 'warning' },
  ACEITO: { texto: 'CONFIRMADO', classe: 'warning' },
  CONCLUIDO: { texto: 'CONCLUÍDO', classe: 'success' },
  AVALIADO: { texto: 'CONCLUÍDO', classe: 'success' },
  CANCELADO: { texto: 'CANCELADO', classe: 'danger' },
  RECUSADO: { texto: 'RECUSADO', classe: 'danger' },
};

async function carregarPedidos() {
  const container = document.getElementById('pedidosContainer');
  try {
    const pedidos = await TaskGoAPI.listarSolicitacoes();
    renderizarPedidos(pedidos);
  } catch (erro) {
    container.innerHTML = `<p class="section-content">${mensagemDeErro(erro)}</p>`;
  }
}

function renderizarPedidos(lista) {
  const container = document.getElementById('pedidosContainer');
  container.innerHTML = '';

  if (lista.length === 0) {
    container.innerHTML = '<p class="section-content">Você ainda não fez nenhuma solicitação. Vá em "Buscar Serviços" para encontrar um profissional.</p>';
    return;
  }

  lista.forEach((pedido) => container.appendChild(criarCardPedido(pedido)));
}

function criarCardPedido(pedido) {
  const rotulo = ROTULOS_PEDIDO[pedido.status] || { texto: pedido.status, classe: '' };
  const card = document.createElement('div');
  card.className = 'order-card-clean';

  const precisaPagar = pedido.status === 'ACEITO' && pedido.statusPagamento !== 'RETIDO' && pedido.statusPagamento !== 'LIBERADO';
  const podeAvaliar = pedido.status === 'CONCLUIDO';
  const podeCancelar = pedido.status === 'SOLICITADO' || pedido.status === 'ACEITO';

  let acoesHtml = '';
  if (precisaPagar) acoesHtml += '<button class="btn-blue-clean btn-pagar"><i class="fas fa-credit-card"></i> Pagar</button>';
  if (podeAvaliar) acoesHtml += '<button class="btn-blue-clean btn-avaliar"><i class="fas fa-star"></i> Avaliar Serviço</button>';
  if (podeCancelar) acoesHtml += '<button class="btn-danger-clean btn-cancelar"><i class="fas fa-times"></i> Cancelar</button>';

  card.innerHTML = `
    <div>
      <span class="badge-clean ${rotulo.classe}">${rotulo.texto}</span>
      <div class="order-title-clean">
        <h3>${pedido.categoria || 'Serviço'}</h3>
      </div>
    </div>
    <div class="pro-block-clean">
      <div class="pro-info-clean">
        <div>
          <h4>${pedido.prestadorNome}</h4>
        </div>
      </div>
      <div class="price-block-clean">
        <span>VALOR</span>
        <strong>${formatarMoeda(pedido.valor)}</strong>
      </div>
    </div>
    <div class="actions-clean">${acoesHtml}</div>
  `;

  const btnPagar = card.querySelector('.btn-pagar');
  if (btnPagar) btnPagar.addEventListener('click', () => abrirPagamentoReal(pedido));

  const btnAvaliar = card.querySelector('.btn-avaliar');
  if (btnAvaliar) btnAvaliar.addEventListener('click', () => abrirModalAvaliacaoReal(pedido));

  const btnCancelar = card.querySelector('.btn-cancelar');
  if (btnCancelar) btnCancelar.addEventListener('click', () => cancelarPedido(pedido.id, btnCancelar));

  return card;
}

async function cancelarPedido(solicitacaoId, botao) {
  if (!confirm('Tem certeza que deseja cancelar esta solicitação?')) return;
  botao.disabled = true;
  try {
    await TaskGoAPI.cancelarSolicitacao(solicitacaoId);
    showToast('Solicitação cancelada.', 'success');
    await carregarPedidos();
  } catch (erro) {
    showToast(mensagemDeErro(erro), 'error');
    botao.disabled = false;
  }
}

// =====================================================================
// PAGAMENTO REAL (US-06) — reaproveita o modal #modalCheckout
// =====================================================================
function abrirPagamentoReal(pedido) {
  document.getElementById('checkoutNome').innerText = pedido.prestadorNome;
  document.getElementById('checkoutEsp').innerText = pedido.categoria || '';
  document.getElementById('checkoutImg').src = 'https://i.pravatar.cc/150?img=11';
  document.getElementById('checkoutValorBase').innerText = formatarMoeda(pedido.valor);
  document.getElementById('checkoutValorTotal').innerText = formatarMoeda(pedido.valor);

  const btn = document.getElementById('btnConfirmarPagamento');
  btn.innerHTML = '<i class="fas fa-lock"></i> Pagar e Contratar';
  btn.style.background = 'var(--primary-blue)';
  btn.disabled = false;
  btn.style.cursor = 'pointer';
  btn.onclick = () => processarPagamentoReal(pedido.id, btn);

  openModal('modalCheckout');
}

async function processarPagamentoReal(solicitacaoId, btn) {
  btn.innerHTML = '<i class="fas fa-circle-notch fa-spin"></i> Processando Cartão...';
  btn.style.background = 'var(--text-muted)';
  btn.disabled = true;
  btn.style.cursor = 'not-allowed';

  try {
    await TaskGoAPI.criarPagamento(solicitacaoId, 'cartao_mock', false);
    btn.innerHTML = '<i class="fas fa-check"></i> Pagamento Aprovado!';
    btn.style.background = 'var(--success-green)';
    showToast('Profissional acionado com sucesso!', 'success');

    setTimeout(() => {
      closeModal('modalCheckout');
      carregarPedidos();
    }, 1200);
  } catch (erro) {
    showToast(mensagemDeErro(erro), 'error');
    btn.innerHTML = '<i class="fas fa-lock"></i> Pagar e Contratar';
    btn.style.background = 'var(--primary-blue)';
    btn.disabled = false;
    btn.style.cursor = 'pointer';
  }
}

// =====================================================================
// AVALIAÇÃO REAL (US-09) — reaproveita o modal #modalAvaliar
// =====================================================================
function abrirModalAvaliacaoReal(pedido) {
  avaliacaoEmAndamentoId = pedido.id;
  document.getElementById('evalProNome').innerText = pedido.prestadorNome;
  document.getElementById('evalProImg').src = 'https://i.pravatar.cc/150?img=11';
  document.querySelectorAll('#starContainer i').forEach((s) => s.classList.remove('active'));
  document.getElementById('evalComentario').value = '';
  openModal('modalAvaliar');
}

function ativarEstrelas(element) {
  document.querySelectorAll('#starContainer i').forEach((s) => s.classList.remove('active'));
  let atual = element;
  while (atual) {
    atual.classList.add('active');
    atual = atual.nextElementSibling;
  }
}

async function enviarAvaliacaoModal() {
  if (!avaliacaoEmAndamentoId) return;

  const nota = document.querySelectorAll('#starContainer i.active').length;
  if (nota === 0) {
    showToast('Selecione ao menos uma estrela.', 'error');
    return;
  }
  const comentario = document.getElementById('evalComentario').value.trim();

  const btn = document.getElementById('btnEnviarAvaliacao');
  btn.disabled = true;
  try {
    await TaskGoAPI.enviarAvaliacao(avaliacaoEmAndamentoId, nota, comentario || undefined);
    closeModal('modalAvaliar');
    showToast('Avaliação enviada com sucesso!', 'success');
    avaliacaoEmAndamentoId = null;
    await carregarPedidos();
  } catch (erro) {
    showToast(mensagemDeErro(erro), 'error');
  } finally {
    btn.disabled = false;
  }
}

// =====================================================================
// CHECKOUT E PAGAMENTO — demonstração (home tab / simulador, fora do escopo do MVP)
// =====================================================================
function abrirCheckout(nome, especialidade, valorBase, img) {
  document.getElementById('checkoutNome').innerText = nome;
  document.getElementById('checkoutEsp').innerText = especialidade;
  document.getElementById('checkoutImg').src = img;
  document.getElementById('checkoutValorBase').innerText = valorBase;

  let valNum = parseFloat(valorBase.replace('R$ ', '').replace('.', '').replace(',', '.'));
  let total = (valNum + 4.50).toFixed(2).replace('.', ',');
  document.getElementById('checkoutValorTotal').innerText = 'R$ ' + total;

  const btn = document.getElementById('btnConfirmarPagamento');
  btn.innerHTML = '<i class="fas fa-lock"></i> Pagar e Contratar';
  btn.style.background = 'var(--primary-blue)';
  btn.disabled = false;
  btn.style.cursor = 'pointer';
  btn.onclick = processarPagamento;

  openModal('modalCheckout');
}

function processarPagamento() {
  const btn = document.getElementById('btnConfirmarPagamento');
  btn.innerHTML = '<i class="fas fa-circle-notch fa-spin"></i> Processando Cartão...';
  btn.style.background = 'var(--text-muted)';
  btn.disabled = true;
  btn.style.cursor = 'not-allowed';

  setTimeout(() => {
    btn.innerHTML = '<i class="fas fa-check"></i> Pagamento Aprovado!';
    btn.style.background = 'var(--success-green)';
    showToast('Profissional acionado com sucesso! (demonstração)', 'success');

    const menuAndamento = document.getElementById('menuAndamento');
    if (menuAndamento) menuAndamento.style.display = 'flex';

    setTimeout(() => {
      closeModal('modalCheckout');
      mudarAba(null, 'aba-rastreamento');
    }, 1200);
  }, 2000);
}

// =====================================================================
// CHAT PREMIUM (decorativo — fora do escopo do MVP)
// =====================================================================
let bancoDeConversas = {};
let chatAbertoAgora = '';

function abrirChat(nome, img) {
  chatAbertoAgora = nome;
  document.getElementById('chatProNome2').innerText = nome;
  let imgEl = document.getElementById('chatProImg2');
  if (imgEl) imgEl.src = img || 'https://i.pravatar.cc/150?img=11';

  if (!bancoDeConversas[nome]) {
    const hora = new Date().toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' });
    bancoDeConversas[nome] = `
      <div style="text-align: center; margin-bottom: 10px;">
        <span style="font-size: 10px; color: var(--text-gray); font-weight: 800; text-transform: uppercase; background: var(--card-bg); padding: 4px 12px; border-radius: 20px; border: 1px solid var(--border-color);">Hoje</span>
      </div>
      <div class="chat-msg-wrapper pro">
        <div class="chat-bubble pro">Olá! Sou o(a) ${nome}, como posso te ajudar?</div>
        <span class="chat-time">${hora}</span>
      </div>
    `;
  }

  const history = document.getElementById('chatHistory');
  if (history) {
    history.innerHTML = bancoDeConversas[nome];
    history.scrollTop = history.scrollHeight;
  }
  openModal('modalChat');
}

function enviarMensagemChat() {
  const input = document.getElementById('chatInput');
  if (!input) return;
  const msg = input.value.trim();
  if (!msg || !chatAbertoAgora) return;

  const history = document.getElementById('chatHistory');
  const time = new Date().toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' });

  const msgCliente = `
    <div class="chat-msg-wrapper user">
      <div class="chat-bubble user">${msg}</div>
      <span class="chat-time">${time}</span>
    </div>
  `;

  history.innerHTML += msgCliente;
  bancoDeConversas[chatAbertoAgora] += msgCliente;

  input.value = '';
  history.scrollTop = history.scrollHeight;

  const typingId = 'typing-' + Date.now();
  history.innerHTML += `
    <div class="chat-msg-wrapper pro" id="${typingId}">
      <div class="typing-indicator">
        <div class="typing-dot"></div><div class="typing-dot"></div><div class="typing-dot"></div>
      </div>
    </div>
  `;
  history.scrollTop = history.scrollHeight;

  const profissionalRespondendo = chatAbertoAgora;

  setTimeout(() => {
    const timePro = new Date().toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' });
    const msgProfissional = `
      <div class="chat-msg-wrapper pro">
        <div class="chat-bubble pro">Tudo bem! Estou indo para o local agora mesmo.</div>
        <span class="chat-time">${timePro}</span>
      </div>
    `;

    bancoDeConversas[profissionalRespondendo] += msgProfissional;

    if (chatAbertoAgora === profissionalRespondendo) {
      const typingIndicator = document.getElementById(typingId);
      if (typingIndicator) typingIndicator.remove();
      history.innerHTML += msgProfissional;
      history.scrollTop = history.scrollHeight;
    }
  }, 2500);
}

// =====================================================================
// RECIBO (decorativo — fora do escopo do MVP)
// =====================================================================
function abrirRecibo(servico, profissional, valorTotal, data) {
  try {
    document.getElementById('reciboID').innerText = Math.floor(Math.random() * 900000) + 100000;
    document.getElementById('reciboServico').innerText = servico;
    document.getElementById('reciboPro').innerText = profissional;
    document.getElementById('reciboValorTotal').innerText = valorTotal;

    let dataEl = document.getElementById('reciboData');
    if (dataEl) dataEl.innerText = data || new Date().toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' });

    let subtotalEl = document.getElementById('reciboSubtotal');
    if (subtotalEl) {
      let valNum = parseFloat(valorTotal.replace('R$ ', '').replace('.', '').replace(',', '.'));
      let subtotal = (valNum - 4.50).toFixed(2).replace('.', ',');
      subtotalEl.innerText = 'R$ ' + subtotal;
    }
  } catch (e) {}
  openModal('modalRecibo');
}

// =====================================================================
// CARTÃO DE CRÉDITO INTERATIVO (captura simulada — sem PSP real, US-06)
// =====================================================================
function updateCardPreview() {
  let numInput = document.getElementById('inputCardNumber');
  let num = numInput.value.replace(/\D/g, '');
  num = num.replace(/(.{4})/g, '$1 ').trim();
  numInput.value = num;
  document.getElementById('previewNumber').innerText = num || '•••• •••• •••• ••••';

  let name = document.getElementById('inputCardName').value.toUpperCase();
  document.getElementById('previewName').innerText = name || 'NOME IMPRESSO';

  let expInput = document.getElementById('inputCardExpiry');
  let exp = expInput.value.replace(/\D/g, '');
  if (exp.length > 2) exp = exp.substring(0, 2) + '/' + exp.substring(2, 4);
  expInput.value = exp;
  document.getElementById('previewExpiry').innerText = exp || 'MM/AA';
}

function salvarCartaoAnimacao() {
  const btn = document.getElementById('btnSalvarCartao');
  btn.innerHTML = '<i class="fas fa-circle-notch fa-spin"></i> Validando no Banco...';
  btn.style.background = 'var(--text-muted)';
  btn.disabled = true;

  setTimeout(() => {
    btn.innerHTML = '<i class="fas fa-check"></i> Cartão Vinculado!';
    btn.style.background = 'var(--success-green)';

    setTimeout(() => {
      closeModal('modalNovoCartao');
      showToast('Novo método de pagamento salvo na carteira.', 'success');

      setTimeout(() => {
        btn.innerHTML = 'Adicionar Cartão';
        btn.style.background = 'var(--primary-blue)';
        btn.disabled = false;
        document.getElementById('inputCardNumber').value = '';
        document.getElementById('inputCardName').value = '';
        document.getElementById('inputCardExpiry').value = '';
        updateCardPreview();
      }, 500);
    }, 1000);
  }, 2000);
}

// =====================================================================
// MAPA LEAFLET (decorativo, coordenadas fixas — fora do escopo do MVP)
// =====================================================================
window.meuMapa = null;

function initMap() {
  const divMapa = document.getElementById('map-real');
  if (!divMapa) return;

  if (window.meuMapa !== null) {
    window.meuMapa.invalidateSize();
    return;
  }

  window.meuMapa = L.map('map-real', { zoomControl: false }).setView([-23.5520, -46.6360], 15);
  L.tileLayer('https://{s}.basemaps.cartocdn.com/light_all/{z}/{x}/{y}{r}.png', { maxZoom: 19 }).addTo(window.meuMapa);

  const iconHome = L.divIcon({
    html: '<i class="fas fa-map-marker-alt" style="color: #1DA1F2; font-size: 30px; text-shadow: 0 2px 5px rgba(0,0,0,0.3);"></i>',
    className: 'custom-div-icon',
    iconSize: [30, 30],
    iconAnchor: [15, 30],
  });
  L.marker([-23.5505, -46.6333], { icon: iconHome }).addTo(window.meuMapa);

  const iconPro = L.divIcon({
    className: 'custom-leaflet-marker',
    html: '<img src="https://i.pravatar.cc/150?img=33" alt="Profissional">',
    iconSize: [50, 50],
    iconAnchor: [25, 25],
  });
  L.marker([-23.5550, -46.6390], { icon: iconPro }).addTo(window.meuMapa);

  L.polyline([[-23.5550, -46.6390], [-23.5505, -46.6333]], { color: '#1DA1F2', weight: 4, dashArray: '10, 10' }).addTo(window.meuMapa);
}

// =====================================================================
// NOTIFICAÇÕES E SIMULADOR (decorativo — fora do escopo do MVP)
// =====================================================================
function abrirNotificacoes() {
  const badge = document.querySelector('.badge-pulse');
  if (badge) {
    badge.style.animation = 'none';
    badge.style.background = 'var(--text-gray)';
    badge.style.borderColor = 'var(--text-gray)';
    badge.innerText = '0';
  }
  openModal('modalNotificacoes');
}

function marcarTodasLidas() {
  document.querySelectorAll('.notif-card-pro.unread').forEach((card) => card.classList.remove('unread'));
  document.querySelectorAll('.unread-indicator').forEach((dot) => (dot.style.display = 'none'));
  const badgeCount = document.getElementById('notifCountBadge');
  if (badgeCount) badgeCount.style.display = 'none';
  showToast('Todas as atividades foram lidas.', 'success');
}

function abrirSimulacao() {
  document.getElementById('simResultado').style.display = 'none';
  const btn = document.getElementById('btnSimular');
  btn.innerHTML = '<i class="fas fa-search"></i> Buscar Profissionais';
  btn.setAttribute('onclick', 'calcularSimulacao()');
  openModal('modalSimulacao');
}

function calcularSimulacao() {
  const btn = document.getElementById('btnSimular');
  btn.innerHTML = '<i class="fas fa-circle-notch fa-spin"></i> Mapeando região...';
  btn.style.background = 'var(--text-muted)';
  btn.disabled = true;

  setTimeout(() => {
    const servico = document.getElementById('simServico').value;
    let preco = 'R$ 150,00';
    let proNome = 'Roberto Almeida';
    let proImg = 'https://i.pravatar.cc/150?img=11';

    if (servico === 'Montagem de Móveis') { preco = 'R$ 180,00'; proNome = 'Carlos Mendes'; proImg = 'https://i.pravatar.cc/150?img=33'; }
    if (servico === 'Limpeza Residencial') { preco = 'R$ 200,00'; proNome = 'Ana Paula Souza'; proImg = 'https://i.pravatar.cc/150?img=5'; }
    if (servico === 'Encanador') { preco = 'R$ 120,00'; proNome = 'Marcos Silva'; proImg = 'https://i.pravatar.cc/150?img=12'; }

    document.getElementById('simPreco').innerText = preco;
    document.getElementById('simResultado').style.display = 'block';

    btn.innerHTML = '<i class="fas fa-bolt"></i> Contratar Agora';
    btn.style.background = 'var(--primary-blue)';
    btn.disabled = false;

    btn.setAttribute('onclick', `irParaCheckoutSimulado('${proNome}', '${servico}', '${preco}', '${proImg}')`);
  }, 1800);
}

function irParaCheckoutSimulado(nome, servico, preco, img) {
  closeModal('modalSimulacao');
  abrirCheckout(nome, servico, preco, img);
}

// =====================================================================
// CENTRAL DE AJUDA (FAQ) e CHAT COM IA (decorativo — fora do escopo do MVP)
// =====================================================================
function toggleFaq(element) {
  const faqItem = element.parentElement;
  const isActive = faqItem.classList.contains('active');
  document.querySelectorAll('.faq-item').forEach((item) => item.classList.remove('active'));
  if (!isActive) faqItem.classList.add('active');
}

function abrirChatIA() {
  document.getElementById('modalChatIA').style.display = 'flex';
}

function enviarMensagemIA() {
  const input = document.getElementById('inputChatIA');
  const mensagem = input.value.trim();
  if (!mensagem) return;

  const history = document.getElementById('chatHistoryIA');

  history.innerHTML += `
    <div style="align-self: flex-end; background: var(--primary-blue); color: white; padding: 12px 16px; border-radius: 12px 0 12px 12px; max-width: 85%; font-size: 13px; line-height: 1.5; animation: popIn 0.3s;">
      ${mensagem}
    </div>
  `;
  input.value = '';
  history.scrollTop = history.scrollHeight;

  const typingId = 'typing-ia-' + Date.now();
  history.innerHTML += `
    <div id="${typingId}" style="align-self: flex-start; background: var(--card-bg); border: 1px solid var(--border-color); padding: 12px 16px; border-radius: 0 12px 12px 12px; max-width: 85%; font-size: 13px; color: var(--text-gray); animation: popIn 0.3s;">
      <i class="fas fa-circle-notch fa-spin" style="color: var(--primary-blue);"></i> Gerando resposta...
    </div>
  `;
  history.scrollTop = history.scrollHeight;

  setTimeout(() => {
    document.getElementById(typingId).remove();

    history.innerHTML += `
      <div style="align-self: flex-start; background: var(--card-bg); border: 1px solid var(--border-color); padding: 12px 16px; border-radius: 0 12px 12px 12px; max-width: 85%; font-size: 13px; line-height: 1.5; color: var(--text-white); animation: popIn 0.3s;">
        Entendi que você tem uma dúvida sobre <strong>"${mensagem}"</strong>. <br><br>Como sou um assistente virtual em demonstração, ainda estou aprendendo! Mas você pode encontrar respostas sobre taxas e pagamentos na seção de Dúvidas Frequentes logo aqui atrás. Posso ajudar com mais alguma coisa? 🤖
      </div>
    `;
    history.scrollTop = history.scrollHeight;
  }, 1500);
}
