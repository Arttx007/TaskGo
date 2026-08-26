let usuarioAtual = null;

document.addEventListener('DOMContentLoaded', async () => {
  usuarioAtual = exigirSessao('ADMIN');
  if (!usuarioAtual) return;

  document.getElementById('boas-vindas').textContent = `Bem-vindo, ${usuarioAtual.nome}. Aqui está o resumo da operação.`;

  await Promise.all([carregarDashboard(), carregarKyc(), carregarParametros()]);
});

// ---------------------------------------------------------------------
// Navegação entre abas
// ---------------------------------------------------------------------

/**
 * Alterna a aba visível do painel.
 *
 * @param {MouseEvent} event evento de clique no item da sidebar
 * @param {string} idAba id do elemento `.conteudo-aba` a exibir
 * @returns {void}
 */
function mudarAba(event, idAba) {
  event.preventDefault();
  document.querySelectorAll('.sidebar-nav .nav-item').forEach((l) => l.classList.remove('active'));
  event.currentTarget.classList.add('active');

  document.querySelectorAll('.conteudo-aba').forEach((a) => a.classList.remove('active'));
  document.getElementById(idAba).classList.add('active');
}

/**
 * Exibe uma notificação temporária no canto da tela.
 *
 * @param {string} message texto da notificação
 * @param {'success'|'error'} [type] estilo visual da notificação
 * @returns {void}
 */
function showToast(message, type = 'success') {
  const container = document.getElementById('toast-container');
  const toast = document.createElement('div');
  toast.style.cssText = `background: var(--card-bg); border: 1px solid var(--border-color); border-left: 4px solid ${type === 'error' ? 'var(--danger-red)' : 'var(--success-green)'}; color: white; padding: 16px 24px; border-radius: 12px; box-shadow: 0 10px 30px rgba(0,0,0,0.5); display: flex; align-items: center; gap: 12px; transform: translateX(120%); opacity: 0; transition: 0.4s; font-weight: 600; font-size: 14px;`;
  const icone = type === 'error' ? 'fa-circle-exclamation' : 'fa-check-circle';
  const cor = type === 'error' ? 'var(--danger-red)' : 'var(--success-green)';
  toast.innerHTML = `<i class="fas ${icone}" style="color: ${cor}; font-size: 20px;"></i> <span>${message}</span>`;
  container.appendChild(toast);
  setTimeout(() => { toast.style.transform = 'translateX(0)'; toast.style.opacity = '1'; }, 10);
  setTimeout(() => { toast.style.transform = 'translateX(120%)'; toast.style.opacity = '0'; setTimeout(() => toast.remove(), 400); }, 3500);
}

/**
 * @param {unknown} erro erro capturado de uma chamada à API
 * @returns {string} mensagem legível para exibir ao usuário
 */
function mensagemDeErro(erro) {
  return erro instanceof TaskGoAPI.ApiError ? erro.message : 'Ocorreu um erro inesperado. Tente novamente.';
}

// ---------------------------------------------------------------------
// Dashboard (contagens gerais)
// ---------------------------------------------------------------------

/** @returns {Promise<void>} carrega e exibe as contagens de clientes/prestadores/serviços */
async function carregarDashboard() {
  try {
    const [dashboard, relatorio] = await Promise.all([TaskGoAPI.obterDashboard(), TaskGoAPI.obterRelatorio()]);
    definirEstatistica('stat-clientes', dashboard.totalClientes);
    definirEstatistica('stat-prestadores', dashboard.totalPrestadores);
    definirEstatistica('stat-servicos', dashboard.totalServicos);
    definirEstatistica('stat-agendados', relatorio.agendados);
    definirEstatistica('stat-concluidos', relatorio.concluidos);
    definirEstatistica('stat-cancelados', relatorio.cancelados);
  } catch (erro) {
    showToast(mensagemDeErro(erro), 'error');
  }
}

/**
 * @param {string} idElemento id do elemento `<h3>` da estatística
 * @param {number|undefined} valor valor numérico a exibir
 * @returns {void}
 */
function definirEstatistica(idElemento, valor) {
  document.getElementById(idElemento).textContent = valor ?? 0;
}

// ---------------------------------------------------------------------
// Verificação de KYC dos prestadores
// ---------------------------------------------------------------------

const ROTULOS_KYC = {
  PENDENTE: { texto: 'Pendente', classe: 'pill-progress' },
  APROVADO: { texto: 'Aprovado', classe: 'pill-success' },
  REJEITADO: { texto: 'Rejeitado', classe: 'pill-danger' },
};

/** @returns {Promise<void>} carrega a lista de prestadores com KYC pendente/rejeitado */
async function carregarKyc() {
  try {
    const lista = await TaskGoAPI.listarPrestadoresPendentes();
    renderizarKyc(lista);
  } catch (erro) {
    showToast(mensagemDeErro(erro), 'error');
    document.getElementById('kyc-container').innerHTML =
      '<div class="empty-state"><i class="fas fa-triangle-exclamation" style="color: var(--danger-red);"></i><h3>Não foi possível carregar</h3><p>Tente novamente mais tarde.</p></div>';
  }
}

/**
 * @param {Object[]} lista lista de PrestadorResponse retornada pela API
 * @returns {void}
 */
function renderizarKyc(lista) {
  const container = document.getElementById('kyc-container');
  atualizarBadgeKyc(lista.length);

  if (lista.length === 0) {
    container.innerHTML = '<div class="empty-state"><i class="fas fa-check-circle"></i><h3>Tudo em dia!</h3><p>Não há prestadores aguardando verificação no momento.</p></div>';
    return;
  }

  container.innerHTML = '';
  lista.forEach((prestador) => container.appendChild(criarCardKyc(prestador)));
}

/**
 * @param {number} quantidade quantidade de prestadores pendentes
 * @returns {void}
 */
function atualizarBadgeKyc(quantidade) {
  const badge = document.getElementById('kyc-count');
  badge.textContent = quantidade;
  badge.style.display = quantidade > 0 ? '' : 'none';
}

/**
 * @param {{idPrestador: number, nome: string, especialidade: string, cidade: string, email: string, statusKyc: string}} prestador
 * @returns {HTMLElement} card de verificação pronto para inserir no DOM
 */
function criarCardKyc(prestador) {
  const rotulo = ROTULOS_KYC[prestador.statusKyc] || { texto: prestador.statusKyc, classe: '' };

  const card = document.createElement('div');
  card.className = 'glass-card kyc-card';
  card.innerHTML = `
    <div class="kyc-card-header">
      <div>
        <h3>${prestador.nome}</h3>
        <p class="kyc-sub">${prestador.especialidade || 'Sem especialidade informada'} · ${prestador.cidade || '—'}</p>
      </div>
      <span class="status-pill ${rotulo.classe}">${rotulo.texto}</span>
    </div>
    <div class="kyc-info"><i class="fas fa-envelope"></i> ${prestador.email}</div>
    <div class="kyc-docs">
      <button type="button" class="btn-outline btn-doc" data-tipo="identidade"><i class="fas fa-id-card"></i> Ver Documento de Identidade</button>
      <button type="button" class="btn-outline btn-doc" data-tipo="pix"><i class="fas fa-receipt"></i> Ver Comprovante PIX</button>
    </div>
    <div class="kyc-actions">
      <button type="button" class="btn-primary btn-aprovar"><i class="fas fa-check"></i> Aprovar</button>
      <button type="button" class="btn-danger btn-rejeitar"><i class="fas fa-times"></i> Rejeitar</button>
    </div>`;

  card.querySelectorAll('.btn-doc').forEach((botao) => {
    botao.addEventListener('click', () => abrirDocumentoKyc(prestador.idPrestador, botao.dataset.tipo, botao));
  });
  card.querySelector('.btn-aprovar').addEventListener('click', (e) => aprovarPrestador(prestador.idPrestador, card, e.currentTarget));
  card.querySelector('.btn-rejeitar').addEventListener('click', (e) => rejeitarPrestador(prestador.idPrestador, card, e.currentTarget));

  return card;
}

/**
 * @param {number} prestadorId
 * @param {'identidade'|'pix'} tipo
 * @param {HTMLButtonElement} botao botão clicado, usado para o estado de carregamento
 * @returns {Promise<void>}
 */
async function abrirDocumentoKyc(prestadorId, tipo, botao) {
  const original = botao.innerHTML;
  botao.disabled = true;
  botao.innerHTML = '<i class="fas fa-circle-notch fa-spin"></i> Abrindo…';
  try {
    const url = await TaskGoAPI.obterUrlDocumentoKyc(prestadorId, tipo);
    window.open(url, '_blank');
  } catch (erro) {
    showToast(mensagemDeErro(erro), 'error');
  } finally {
    botao.disabled = false;
    botao.innerHTML = original;
  }
}

/**
 * @param {number} prestadorId
 * @param {HTMLElement} card card do prestador, removido da tela após sucesso
 * @param {HTMLButtonElement} botao botão clicado, usado para o estado de carregamento
 * @returns {Promise<void>}
 */
async function aprovarPrestador(prestadorId, card, botao) {
  botao.disabled = true;
  botao.innerHTML = '<i class="fas fa-circle-notch fa-spin"></i>';
  try {
    await TaskGoAPI.aprovarKycPrestador(prestadorId);
    showToast('Prestador aprovado com sucesso.', 'success');
    removerCardKyc(card);
  } catch (erro) {
    showToast(mensagemDeErro(erro), 'error');
    botao.disabled = false;
    botao.innerHTML = '<i class="fas fa-check"></i> Aprovar';
  }
}

/**
 * @param {number} prestadorId
 * @param {HTMLElement} card card do prestador, removido da tela após sucesso
 * @param {HTMLButtonElement} botao botão clicado, usado para o estado de carregamento
 * @returns {Promise<void>}
 */
async function rejeitarPrestador(prestadorId, card, botao) {
  const motivo = window.prompt('Motivo da rejeição (opcional):') || undefined;

  botao.disabled = true;
  botao.innerHTML = '<i class="fas fa-circle-notch fa-spin"></i>';
  try {
    await TaskGoAPI.rejeitarKycPrestador(prestadorId, motivo);
    showToast('Cadastro rejeitado.', 'success');
    removerCardKyc(card);
  } catch (erro) {
    showToast(mensagemDeErro(erro), 'error');
    botao.disabled = false;
    botao.innerHTML = '<i class="fas fa-times"></i> Rejeitar';
  }
}

/**
 * @param {HTMLElement} card card a remover da grade de KYC
 * @returns {void}
 */
function removerCardKyc(card) {
  card.remove();
  const restantes = document.querySelectorAll('#kyc-container .kyc-card').length;
  atualizarBadgeKyc(restantes);
  if (restantes === 0) {
    document.getElementById('kyc-container').innerHTML =
      '<div class="empty-state"><i class="fas fa-check-circle"></i><h3>Tudo em dia!</h3><p>Não há prestadores aguardando verificação no momento.</p></div>';
  }
}

// ---------------------------------------------------------------------
// Parâmetros de negócio
// ---------------------------------------------------------------------

/** @returns {Promise<void>} carrega a tabela de parâmetros de negócio ajustáveis */
async function carregarParametros() {
  try {
    const parametros = await TaskGoAPI.listarParametros();
    renderizarParametros(parametros);
  } catch (erro) {
    showToast(mensagemDeErro(erro), 'error');
    document.querySelector('#parametros-table tbody').innerHTML =
      '<tr><td colspan="3" style="text-align:center; color: var(--danger-red);">Não foi possível carregar os parâmetros.</td></tr>';
  }
}

/**
 * @param {{chave: string, valor: number, descricao: string}[]} lista
 * @returns {void}
 */
function renderizarParametros(lista) {
  const corpo = document.querySelector('#parametros-table tbody');
  corpo.innerHTML = '';

  if (lista.length === 0) {
    corpo.innerHTML = '<tr><td colspan="3" style="text-align:center; color: var(--text-gray);">Nenhum parâmetro cadastrado.</td></tr>';
    return;
  }

  lista.forEach((parametro) => corpo.appendChild(criarLinhaParametro(parametro)));
}

/**
 * @param {{chave: string, valor: number, descricao: string}} parametro
 * @returns {HTMLElement} linha `<tr>` pronta para inserir na tabela
 */
function criarLinhaParametro(parametro) {
  const linha = document.createElement('tr');
  linha.innerHTML = `
    <td><strong>${parametro.chave}</strong><div class="param-descricao">${parametro.descricao || ''}</div></td>
    <td><input type="number" step="0.0001" class="param-input input-valor" value="${parametro.valor}"></td>
    <td><button type="button" class="btn-salvar-parametro">Salvar</button></td>`;

  linha.querySelector('.btn-salvar-parametro').addEventListener('click', (e) => salvarParametro(parametro.chave, linha, e.currentTarget));
  return linha;
}

/**
 * @param {string} chave chave do parâmetro a atualizar
 * @param {HTMLElement} linha linha `<tr>` contendo o input de valor
 * @param {HTMLButtonElement} botao botão clicado, usado para o estado de carregamento
 * @returns {Promise<void>}
 */
async function salvarParametro(chave, linha, botao) {
  const input = linha.querySelector('.input-valor');
  const valor = parseFloat(input.value);

  if (isNaN(valor)) {
    showToast('Informe um valor numérico válido.', 'error');
    return;
  }

  const textoOriginal = botao.textContent;
  botao.disabled = true;
  botao.textContent = 'Salvando…';

  try {
    const atualizado = await TaskGoAPI.atualizarParametro(chave, valor);
    input.value = atualizado.valor;
    showToast(`Parâmetro "${chave}" atualizado.`, 'success');
  } catch (erro) {
    showToast(mensagemDeErro(erro), 'error');
  } finally {
    botao.disabled = false;
    botao.textContent = textoOriginal;
  }
}
