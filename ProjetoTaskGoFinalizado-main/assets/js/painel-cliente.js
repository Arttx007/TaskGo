let usuarioAtual = null;
let avaliacaoEmAndamentoId = null;

/** @type {Object|null} perfil do cliente autenticado (`ClientePerfilResponse`) */
let perfilCliente = null;

/** @type {Object[]} endereços ativos do cliente; a busca e a solicitação usam o marcado como padrão */
let enderecosCliente = [];

document.addEventListener('DOMContentLoaded', () => {
  usuarioAtual = exigirSessao('CLIENTE');
  if (!usuarioAtual) return;

  document.getElementById('nomeClienteHeader').textContent = `${usuarioAtual.nome}!`;
  registrarListenersDoPainel();

  carregarPerfil();
  carregarEnderecos().then(atualizarLocalidadeDoCabecalho);
  carregarPedidos();
  renderizarTagsCategorias();
  carregarAvisos();
});

/**
 * Registra em JS os listeners dos controles que não são criados dinamicamente, mantendo a página
 * livre de `onclick=` inline nos fluxos novos.
 *
 * @returns {void}
 */
function registrarListenersDoPainel() {
  document.getElementById('btnBuscar').addEventListener('click', buscarPorTermoDigitado);
  document.getElementById('buscaTermo').addEventListener('keydown', (evento) => {
    if (evento.key === 'Enter') {
      evento.preventDefault();
      buscarPorTermoDigitado();
    }
  });

  const formPerfil = document.getElementById('formPerfilCliente');
  if (formPerfil) formPerfil.addEventListener('submit', salvarPerfil);

  const inputFoto = document.getElementById('inputFotoPerfil');
  if (inputFoto) inputFoto.addEventListener('change', enviarFotoPerfil);

  const btnFoto = document.getElementById('btnTrocarFoto');
  if (btnFoto) btnFoto.addEventListener('click', () => document.getElementById('inputFotoPerfil').click());

  const btnExcluir = document.getElementById('btnExcluirConta');
  if (btnExcluir) btnExcluir.addEventListener('click', desativarConta);

  const btnBuscarAjuda = document.getElementById('btnBuscarAjuda');
  if (btnBuscarAjuda) btnBuscarAjuda.addEventListener('click', filtrarAjuda);

  const ajudaBusca = document.getElementById('ajudaBusca');
  if (ajudaBusca) ajudaBusca.addEventListener('input', filtrarAjuda);

  const btnNovoEndereco = document.getElementById('btnNovoEndereco');
  if (btnNovoEndereco) btnNovoEndereco.addEventListener('click', () => abrirModalEndereco(null));

  const formEndereco = document.getElementById('formEndereco');
  if (formEndereco) formEndereco.addEventListener('submit', salvarEndereco);
}

/**
 * Carrega o perfil do cliente e reflete nome e foto no cabeçalho.
 *
 * @returns {Promise<void>}
 */
async function carregarPerfil() {
  try {
    perfilCliente = await TaskGoAPI.obterMeuPerfilCliente();
    aplicarPerfilNaTela(perfilCliente);
  } catch (erro) {
    showToast(mensagemDeErro(erro), 'error');
  }
}

/**
 * @param {Object} perfil `ClientePerfilResponse`
 * @returns {void}
 */
function aplicarPerfilNaTela(perfil) {
  document.getElementById('nomeClienteHeader').textContent = `${perfil.nome}!`;
  document.getElementById('perfilPillNome').textContent = perfil.nome;

  const foto = document.getElementById('perfilPillFoto');
  const iniciais = document.getElementById('perfilPillIniciais');

  if (perfil.fotoUrl) {
    foto.src = perfil.fotoUrl;
    foto.style.display = 'block';
    iniciais.style.display = 'none';
  } else {
    foto.style.display = 'none';
    iniciais.style.display = 'flex';
    iniciais.textContent = iniciaisDoNome(perfil.nome);
  }

  preencherFormularioPerfil(perfil);
}

/**
 * @param {string} nome nome completo
 * @returns {string} até duas iniciais, usadas quando o cliente não tem foto
 */
function iniciaisDoNome(nome) {
  return String(nome || '')
    .trim()
    .split(/\s+/)
    .slice(0, 2)
    .map((parte) => parte.charAt(0).toUpperCase())
    .join('');
}

function mensagemDeErro(erro) {
  return erro instanceof TaskGoAPI.ApiError ? erro.message : 'Ocorreu um erro inesperado. Tente novamente.';
}

function formatarMoeda(valor) {
  return 'R$ ' + Number(valor || 0).toFixed(2).replace('.', ',');
}

/**
 * Escapa texto vindo da API antes de interpolá-lo em HTML.
 *
 * @param {*} valor conteúdo a escapar; `null`/`undefined` viram string vazia
 * @returns {string} texto seguro para interpolação em conteúdo de elemento
 */
function escaparTexto(valor) {
  if (valor === null || valor === undefined) return '';
  return String(valor).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

/**
 * Como `escaparTexto`, mas também escapa as aspas — para uso dentro de atributo HTML.
 *
 * @param {*} valor conteúdo a escapar
 * @returns {string} texto seguro para interpolação em valor de atributo
 */
function escaparAtributo(valor) {
  return escaparTexto(valor).replace(/"/g, '&quot;').replace(/'/g, '&#39;');
}

/**
 * @param {string|null} iso data-hora ISO devolvida pela API
 * @returns {string} data e hora em pt-BR, ou string vazia quando o momento não ocorreu
 */
function formatarDataHora(iso) {
  if (!iso) return '';
  return new Date(iso).toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' });
}

/**
 * @param {string|null} iso data-hora ISO
 * @returns {string} apenas a hora em pt-BR, ou string vazia
 */
function formatarHora(iso) {
  if (!iso) return '';
  return new Date(iso).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' });
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

  if (idAba === 'aba-pedidos') carregarPedidos();
  if (idAba === 'aba-pagamentos') carregarExtrato();
  if (idAba === 'aba-favoritos') carregarFavoritos();
  if (idAba === 'aba-configuracoes') {
    carregarPerfil();
    carregarEnderecos();
  }

  if (idAba === 'aba-rastreamento') {
    carregarAcompanhamento();
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

/**
 * Confirmação em modal próprio, em vez de `confirm()` nativo — o diálogo nativo não comporta os
 * valores que o cliente precisa ver antes de cancelar um atendimento (quanto é retido, quanto volta).
 *
 * @param {{titulo: string, texto: string, detalhesHtml?: string, rotuloConfirmar?: string,
 *          perigo?: boolean}} opcoes `detalhesHtml` recebe conteúdo já escapado por quem chama
 * @returns {Promise<boolean>} true quando o usuário confirma
 */
function confirmar(opcoes) {
  return new Promise((resolver) => {
    const modal = document.getElementById('modalConfirmar');
    document.getElementById('confirmarTitulo').textContent = opcoes.titulo;
    document.getElementById('confirmarTexto').textContent = opcoes.texto;

    const detalhes = document.getElementById('confirmarDetalhes');
    if (opcoes.detalhesHtml) {
      detalhes.innerHTML = opcoes.detalhesHtml;
      detalhes.style.display = 'block';
    } else {
      detalhes.innerHTML = '';
      detalhes.style.display = 'none';
    }

    const btnOk = document.getElementById('confirmarOk');
    const btnCancelar = document.getElementById('confirmarCancelar');

    btnOk.textContent = opcoes.rotuloConfirmar || 'Confirmar';
    btnOk.className = opcoes.perigo ? 'btn-danger-outline' : 'btn-primary';
    btnOk.style.flex = '1';
    btnOk.style.padding = '14px';

    function encerrar(resposta) {
      btnOk.removeEventListener('click', aoConfirmar);
      btnCancelar.removeEventListener('click', aoCancelar);
      modal.style.display = 'none';
      resolver(resposta);
    }
    function aoConfirmar() {
      encerrar(true);
    }
    function aoCancelar() {
      encerrar(false);
    }

    btnOk.addEventListener('click', aoConfirmar);
    btnCancelar.addEventListener('click', aoCancelar);
    modal.style.display = 'flex';
  });
}

window.onclick = function (e) {
  if (e.target.classList.contains('modal-overlay')) e.target.style.display = 'none';
};

// =====================================================================
// BUSCA DE SERVIÇOS (US-03) — dados reais
// =====================================================================
/** @type {Array<{categoria: string, totalServicos: number}>|null} cache das categorias com oferta */
let categoriasConhecidas = null;

/** @type {{lat: number|null, lon: number|null, cidade: string}} localidade usada na busca */
let localidadeAtual = { lat: null, lon: null, cidade: '' };

const ICONES_CATEGORIA = {
  ELETRICA: { icone: 'fa-bolt', cor: 'var(--warning-yellow)' },
  LIMPEZA: { icone: 'fa-broom', cor: 'var(--success-green)' },
  CLIMATIZACAO: { icone: 'fa-snowflake', cor: 'var(--primary-blue)' },
  MONTAGEM: { icone: 'fa-hammer', cor: 'var(--text-gray)' },
  PINTURA: { icone: 'fa-paint-roller', cor: 'var(--warning-yellow)' },
  ENCANADOR: { icone: 'fa-faucet', cor: 'var(--primary-blue)' },
  HIDRAULICA: { icone: 'fa-faucet', cor: 'var(--primary-blue)' },
  JARDINAGEM: { icone: 'fa-seedling', cor: 'var(--success-green)' },
};

/**
 * Reduz uma categoria a uma chave comparável — sem acento, sem caixa e sem espaço nas pontas.
 *
 * Usada tanto para escolher o ícone da tag quanto para reconciliar o texto digitado na busca livre
 * contra as categorias que existem, já que `categoria` é texto livre no backend.
 *
 * @param {string} texto categoria ou termo digitado
 * @returns {string} chave normalizada em maiúsculas
 */
function normalizarCategoria(texto) {
  return String(texto || '')
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .trim()
    .toUpperCase();
}

/**
 * Categorias com ao menos um serviço disponível, com cache em memória.
 *
 * @returns {Promise<Array<{categoria: string, totalServicos: number}>>}
 */
async function carregarCategorias() {
  if (categoriasConhecidas) return categoriasConhecidas;
  categoriasConhecidas = (await TaskGoAPI.listarCategorias()) || [];
  return categoriasConhecidas;
}

/**
 * Renderiza as tags de categoria a partir do que os prestadores realmente ofertam — nenhuma
 * categoria é fixa na página.
 *
 * @returns {Promise<void>}
 */
async function renderizarTagsCategorias() {
  const container = document.getElementById('buscaCategorias');
  if (!container) return;

  try {
    const categorias = await carregarCategorias();
    container.innerHTML = '';

    if (!categorias.length) {
      container.innerHTML = '<span class="section-content">Nenhuma categoria disponível no momento.</span>';
      return;
    }

    categorias.forEach((item) => {
      const estilo = ICONES_CATEGORIA[normalizarCategoria(item.categoria)] || { icone: 'fa-tools', cor: 'var(--primary-blue)' };
      const botao = document.createElement('button');
      botao.className = 'quick-tag';
      botao.innerHTML = `<i class="fas ${estilo.icone}" style="color: ${estilo.cor};"></i> ${escaparTexto(item.categoria)}`;
      botao.addEventListener('click', () => {
        document.getElementById('buscaTermo').value = item.categoria;
        buscarPorCategoria(item.categoria);
      });
      container.appendChild(botao);
    });
  } catch (erro) {
    container.innerHTML = `<span class="section-content">${escaparTexto(mensagemDeErro(erro))}</span>`;
  }
}

/**
 * Reconcilia o texto digitado contra as categorias que existem e dispara a busca.
 *
 * O backend exige `categoria` exata na busca, então busca livre sem reconciliação devolveria vazio
 * silencioso. Quando nada casa, apresentamos as categorias disponíveis em vez de "nenhum resultado".
 *
 * @returns {Promise<void>}
 */
async function buscarPorTermoDigitado() {
  const termo = document.getElementById('buscaTermo').value.trim();
  const container = document.getElementById('buscaResultados');

  if (!termo) {
    showToast('Digite o serviço que você precisa ou escolha uma categoria.', 'error');
    return;
  }

  let categorias;
  try {
    categorias = await carregarCategorias();
  } catch (erro) {
    container.innerHTML = `<p class="section-content" style="grid-column: 1 / -1;">${escaparTexto(mensagemDeErro(erro))}</p>`;
    return;
  }

  const chave = normalizarCategoria(termo);
  const exata = categorias.find((c) => normalizarCategoria(c.categoria) === chave);
  const parcial = categorias.find(
    (c) => normalizarCategoria(c.categoria).includes(chave) || chave.includes(normalizarCategoria(c.categoria))
  );
  const escolhida = exata || parcial;

  if (!escolhida) {
    const disponiveis = categorias.map((c) => escaparTexto(c.categoria)).join(', ');
    container.innerHTML = `<p class="section-content" style="grid-column: 1 / -1;">Não encontramos a categoria "${escaparTexto(termo)}". ${
      disponiveis ? `Categorias disponíveis hoje: ${disponiveis}.` : 'Nenhuma categoria está disponível no momento.'
    }</p>`;
    ocultarContadorBusca();
    return;
  }

  buscarPorCategoria(escolhida.categoria);
}

/**
 * Busca profissionais de uma categoria, tentando a geolocalização do navegador e caindo no endereço
 * padrão do cliente quando a permissão é negada ou indisponível.
 *
 * @param {string} categoria categoria exata, já reconciliada
 * @returns {void}
 */
function buscarPorCategoria(categoria) {
  const fallback = localidadeDoEnderecoPadrao();

  if (!navigator.geolocation) {
    executarBuscaServicos(categoria, fallback);
    return;
  }

  navigator.geolocation.getCurrentPosition(
    (posicao) => executarBuscaServicos(categoria, { lat: posicao.coords.latitude, lon: posicao.coords.longitude, cidade: '' }),
    () => executarBuscaServicos(categoria, fallback),
    { timeout: 8000 }
  );
}

/**
 * @returns {{lat: number|null, lon: number|null, cidade: string}} localidade do endereço padrão do
 *          cliente, ou tudo vazio quando ele não tem endereço cadastrado
 */
function localidadeDoEnderecoPadrao() {
  const padrao = (enderecosCliente || []).find((e) => e.padrao);
  if (!padrao) return { lat: null, lon: null, cidade: '' };
  return { lat: padrao.latitude, lon: padrao.longitude, cidade: padrao.cidade || '' };
}

/**
 * Executa a busca e renderiza os resultados.
 *
 * @param {string} categoria
 * @param {{lat: number|null, lon: number|null, cidade: string}} localidade
 * @returns {Promise<void>}
 */
async function executarBuscaServicos(categoria, localidade) {
  localidadeAtual = localidade;
  atualizarLocalidadeDoCabecalho();

  const container = document.getElementById('buscaResultados');
  container.innerHTML = '<p class="section-content" style="grid-column: 1 / -1;">Buscando profissionais...</p>';
  ocultarContadorBusca();

  try {
    const resposta = await TaskGoAPI.buscarServicos({
      categoria,
      lat: localidade.lat,
      lon: localidade.lon,
      cidade: localidade.cidade,
    });
    renderizarResultadosBusca(resposta);
  } catch (erro) {
    container.innerHTML = `<p class="section-content" style="grid-column: 1 / -1;">${escaparTexto(mensagemDeErro(erro))}</p>`;
  }
}

/**
 * @param {{resultados: Object[], mensagem: string|null}} resposta resposta de `/buscar`
 * @returns {void}
 */
function renderizarResultadosBusca(resposta) {
  const container = document.getElementById('buscaResultados');
  const resultados = (resposta && resposta.resultados) || [];
  container.innerHTML = '';

  if (!resultados.length) {
    const mensagem = (resposta && resposta.mensagem) || 'Nenhum profissional disponível para esta categoria na sua região.';
    container.innerHTML = `<p class="section-content" style="grid-column: 1 / -1;">${escaparTexto(mensagem)}</p>`;
    ocultarContadorBusca();
    return;
  }

  resultados.forEach((item) => container.appendChild(criarCardResultado(item)));

  const contador = document.getElementById('buscaContador');
  contador.style.display = 'inline';
  contador.textContent = `${resultados.length} ${
    resultados.length === 1 ? 'profissional disponível' : 'profissionais disponíveis'
  } perto de você`;
}

/** @returns {void} esconde o contador de resultados enquanto não houver busca com retorno */
function ocultarContadorBusca() {
  const contador = document.getElementById('buscaContador');
  if (contador) {
    contador.style.display = 'none';
    contador.textContent = '';
  }
}

/**
 * Apresenta no cabeçalho a localidade efetivamente usada na busca — coordenadas do navegador quando
 * concedidas, cidade do endereço padrão quando não.
 *
 * @returns {void}
 */
function atualizarLocalidadeDoCabecalho() {
  const alvo = document.getElementById('localidadeHeader');
  if (!alvo) return;

  if (localidadeAtual.lat != null && localidadeAtual.lon != null) {
    alvo.textContent = 'Sua localização atual';
    return;
  }

  const padrao = (enderecosCliente || []).find((e) => e.padrao);
  if (padrao) {
    alvo.textContent = `${padrao.cidade}, ${padrao.uf}`;
    return;
  }

  alvo.textContent = 'Localização não informada';
}

/**
 * @param {Object} item item de `BuscaServicoResponse`
 * @returns {HTMLElement} card do profissional, com os listeners registrados em JS
 */
function criarCardResultado(item) {
  const card = document.createElement('div');
  card.className = 'client-pro-card';

  const nota = item.notaMediaPrestador != null ? Number(item.notaMediaPrestador).toFixed(1) : null;
  const distancia = item.distanciaKm != null ? `${Number(item.distanciaKm).toFixed(1)} km` : '—';

  card.innerHTML = `
    <div class="client-pro-avatar" style="display: flex; align-items: center; justify-content: center; background: var(--card-bg); color: var(--text-gray); font-size: 32px;"><i class="fas fa-user"></i></div>
    <div class="pro-name-verified">${escaparTexto(item.prestadorNome)}</div>
    <span class="client-pro-tag">${escaparTexto(item.categoria)}</span>
    <div class="client-pro-stats-complex">
      <div class="stat-complex-item"><span>Avaliação</span><strong>${
        nota ? `<i class="fas fa-star" style="color: var(--warning-yellow);"></i> ${nota}` : 'Sem avaliações'
      }</strong></div>
      <div class="stat-complex-item"><span>Valor</span><strong>${formatarMoeda(item.preco)}</strong></div>
      <div class="stat-complex-item"><span>Distância</span><strong>${distancia}</strong></div>
    </div>
    <div style="display: flex; gap: 8px;">
      <button class="btn-full-width btn-contratar"><i class="fas fa-handshake"></i> Contratar</button>
      <button class="btn-outline btn-favoritar" title="Adicionar aos favoritos" style="flex: 0 0 auto;"><i class="far fa-heart"></i></button>
    </div>
  `;

  card.querySelector('.btn-contratar').addEventListener('click', (evento) => contratarServico(item.servicoOfertadoId, evento.currentTarget));
  card.querySelector('.btn-favoritar').addEventListener('click', (evento) => favoritarPrestador(item.prestadorId, evento.currentTarget));

  return card;
}

/**
 * Cria a solicitação para o serviço escolhido, informando o endereço padrão do cliente quando houver.
 *
 * @param {number} servicoOfertadoId serviço a contratar
 * @param {HTMLButtonElement} botao botão acionado, desabilitado durante a chamada
 * @returns {Promise<void>}
 */
async function contratarServico(servicoOfertadoId, botao) {
  const padrao = (enderecosCliente || []).find((e) => e.padrao);
  botao.disabled = true;

  try {
    await TaskGoAPI.criarSolicitacao(servicoOfertadoId, padrao ? padrao.id : undefined);
    showToast('Solicitação enviada! Acompanhe em "Meus Pedidos".', 'success');
    await carregarPedidos();
  } catch (erro) {
    showToast(mensagemDeErro(erro), 'error');
  } finally {
    botao.disabled = false;
  }
}

// =====================================================================
// MEUS PEDIDOS (US-04, US-06, US-09, US-10) — dados reais
// =====================================================================
const ROTULOS_PEDIDO = {
  SOLICITADO: { texto: 'AGUARDANDO PROFISSIONAL', classe: 'warning' },
  ACEITO: { texto: 'CONFIRMADO', classe: 'warning' },
  EM_ANDAMENTO: { texto: 'EM ATENDIMENTO', classe: 'warning' },
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

  const emCurso = lista.find((p) => p.status === 'ACEITO' || p.status === 'EM_ANDAMENTO');
  const menuAndamento = document.getElementById('menuAndamento');
  if (menuAndamento) menuAndamento.style.display = emCurso ? 'flex' : 'none';
  solicitacaoAcompanhadaId = emCurso ? emCurso.id : null;

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
  const podeCancelar = pedido.status === 'SOLICITADO' || pedido.status === 'ACEITO' || pedido.status === 'EM_ANDAMENTO';
  const podeAcompanhar = pedido.status === 'ACEITO' || pedido.status === 'EM_ANDAMENTO';

  let acoesHtml = '';
  if (precisaPagar) acoesHtml += '<button class="btn-blue-clean btn-pagar"><i class="fas fa-credit-card"></i> Pagar</button>';
  if (podeAvaliar) acoesHtml += '<button class="btn-blue-clean btn-avaliar"><i class="fas fa-star"></i> Avaliar Serviço</button>';
  if (podeAcompanhar) acoesHtml += '<button class="btn-blue-clean btn-acompanhar"><i class="fas fa-route"></i> Acompanhar</button>';
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
  if (btnCancelar) btnCancelar.addEventListener('click', () => cancelarPedido(pedido, btnCancelar));

  const btnAcompanhar = card.querySelector('.btn-acompanhar');
  if (btnAcompanhar) btnAcompanhar.addEventListener('click', () => irParaAcompanhamento(pedido.id));

  return card;
}

/**
 * Cancela uma solicitação, avisando antes quanto seria retido e quanto voltaria.
 *
 * `taxaCancelamentoPrevista` vem de `GET /servicos/{id}` — dentro da carência ela é zero e a
 * devolução é integral; fora dela parte do valor fica retida como taxa de cancelamento e é creditada
 * ao prestador (RN03). Sem esse aviso, o cliente confirmaria sem saber o que perde.
 *
 * @param {Object} pedido solicitação a cancelar
 * @param {HTMLButtonElement} botao botão acionado
 * @returns {Promise<void>}
 */
async function cancelarPedido(pedido, botao) {
  botao.disabled = true;

  let detalhe = null;
  try {
    detalhe = await TaskGoAPI.obterSolicitacao(pedido.id);
  } catch {
    // sem o detalhe seguimos com o aviso genérico — o backend continua sendo a autoridade
  }

  const taxa = detalhe && detalhe.taxaCancelamentoPrevista != null ? Number(detalhe.taxaCancelamentoPrevista) : 0;
  const valor = Number((detalhe && detalhe.valor) || pedido.valor || 0);
  const pago = (detalhe || pedido).statusPagamento === 'RETIDO';

  let detalhesHtml = '';
  if (pago && taxa > 0) {
    detalhesHtml = `
      <div style="display: flex; justify-content: space-between; font-size: 13px; color: var(--text-gray); margin-bottom: 8px;"><span>Valor pago</span><strong style="color: white;">${formatarMoeda(valor)}</strong></div>
      <div style="display: flex; justify-content: space-between; font-size: 13px; color: var(--text-gray); margin-bottom: 8px;"><span>Taxa de cancelamento retida</span><strong style="color: var(--danger-red);">${formatarMoeda(taxa)}</strong></div>
      <div style="display: flex; justify-content: space-between; font-size: 13px; color: var(--text-gray); border-top: 1px dashed var(--border-color); padding-top: 8px;"><span>Volta para você</span><strong style="color: var(--success-green);">${formatarMoeda(valor - taxa)}</strong></div>
    `;
  } else if (pago) {
    detalhesHtml = `
      <div style="display: flex; justify-content: space-between; font-size: 13px; color: var(--text-gray);"><span>Devolução integral</span><strong style="color: var(--success-green);">${formatarMoeda(valor)}</strong></div>
    `;
  }

  const confirmado = await confirmar({
    titulo: 'Cancelar solicitação',
    texto:
      pago && taxa > 0
        ? 'O atendimento já começou e o prazo de carência passou. Parte do valor pago fica com o profissional como taxa de cancelamento.'
        : pago
          ? 'O valor pago volta integralmente para você.'
          : 'Tem certeza que deseja cancelar esta solicitação?',
    detalhesHtml,
    rotuloConfirmar: 'Cancelar solicitação',
    perigo: true,
  });

  if (!confirmado) {
    botao.disabled = false;
    return;
  }

  try {
    await TaskGoAPI.cancelarSolicitacao(pedido.id);
    showToast('Solicitação cancelada.', 'success');
    await carregarPedidos();
    await carregarAvisos();
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
// CHAT COM O PROFISSIONAL — dados reais (ver assets/js/chat-servico.js)
// =====================================================================
/**
 * Abre a conversa da solicitação. A implementação é compartilhada com o painel do prestador.
 *
 * @param {Object} solicitacao solicitação cuja conversa deve ser aberta
 * @returns {void}
 */
function abrirChatDoServico(solicitacao) {
  ChatServico.abrir(solicitacao);
}

// =====================================================================
// FAVORITOS — dados reais
// =====================================================================
/**
 * Carrega e renderiza os favoritos do cliente.
 *
 * @returns {Promise<void>}
 */
async function carregarFavoritos() {
  const container = document.getElementById('favoritosContainer');
  if (!container) return;

  container.innerHTML = '<p class="section-content" style="grid-column: 1 / -1;">Carregando favoritos...</p>';

  try {
    const favoritos = (await TaskGoAPI.listarMeusFavoritos()) || [];
    container.innerHTML = '';

    if (!favoritos.length) {
      container.innerHTML = '<p class="section-content" style="grid-column: 1 / -1;">Você ainda não favoritou nenhum profissional. Use o coração nos resultados da busca.</p>';
      return;
    }

    favoritos.forEach((favorito) => container.appendChild(criarCardFavorito(favorito)));
  } catch (erro) {
    container.innerHTML = `<p class="section-content" style="grid-column: 1 / -1;">${escaparTexto(mensagemDeErro(erro))}</p>`;
  }
}

/**
 * Monta o card de um favorito. Prestador que deixou de estar aprovado permanece na lista, sinalizado
 * como indisponível e sem a ação de contratar — desaparecer da lista esconderia do cliente que ele
 * ainda tem aquele profissional favoritado.
 *
 * @param {Object} favorito `FavoritoResponse`
 * @returns {HTMLElement}
 */
function criarCardFavorito(favorito) {
  const card = document.createElement('div');
  card.className = 'fav-card-pro';

  const nota = favorito.notaMedia != null ? Number(favorito.notaMedia).toFixed(1) : null;

  card.innerHTML = `
    <button class="fav-heart-btn btn-desfavoritar" title="Remover dos favoritos"><i class="fas fa-heart"></i></button>

    <div class="fav-profile-area">
      <div class="fav-avatar-wrapper">
        <div style="width: 100%; height: 100%; border-radius: 50%; background: var(--card-bg); display: flex; align-items: center; justify-content: center; color: var(--text-gray); font-size: 24px;"><i class="fas fa-user"></i></div>
        <div class="status-dot" style="background: ${favorito.disponivel ? 'var(--success-green)' : 'var(--text-muted)'};" title="${favorito.disponivel ? 'Disponível' : 'Indisponível'}"></div>
      </div>
      <div class="fav-info">
        <h3>${escaparTexto(favorito.nome)}</h3>
        <span>${escaparTexto(favorito.especialidade || 'Sem especialidade informada')}</span>
      </div>
    </div>

    <div class="fav-stats-box">
      <div class="fav-stat">
        <span>Avaliação</span>
        <strong>${nota ? `<i class="fas fa-star" style="color: var(--warning-yellow);"></i> ${nota}` : '—'}</strong>
      </div>
      <div class="fav-stat">
        <span>Serviços ativos</span>
        <strong>${favorito.servicosAtivos}</strong>
      </div>
      <div class="fav-stat">
        <span>Cidade</span>
        <strong>${escaparTexto(favorito.cidade || '—')}</strong>
      </div>
    </div>

    ${
      favorito.disponivel
        ? `<div class="fav-actions">
             <button class="btn-primary btn-contratar-favorito"><i class="fas fa-handshake"></i> Contratar</button>
           </div>`
        : `<p style="font-size: 12px; color: var(--warning-yellow); font-weight: 700; display: flex; align-items: center; gap: 8px; margin-top: 4px;"><i class="fas fa-circle-exclamation"></i> Indisponível para contratação no momento</p>`
    }
  `;

  card.querySelector('.btn-desfavoritar').addEventListener('click', (evento) => desfavoritarPrestador(favorito, evento.currentTarget));

  const btnContratar = card.querySelector('.btn-contratar-favorito');
  if (btnContratar) btnContratar.addEventListener('click', () => abrirCatalogoDoPrestador(favorito));

  return card;
}

/**
 * Marca um prestador como favorito a partir do card de resultado da busca.
 *
 * @param {number} prestadorId
 * @param {HTMLButtonElement} botao botão do coração
 * @returns {Promise<void>}
 */
async function favoritarPrestador(prestadorId, botao) {
  botao.disabled = true;
  try {
    await TaskGoAPI.marcarFavorito(prestadorId);
    botao.innerHTML = '<i class="fas fa-heart" style="color: var(--danger-red);"></i>';
    botao.title = 'Já está nos seus favoritos';
    showToast('Profissional adicionado aos favoritos.', 'success');
  } catch (erro) {
    showToast(mensagemDeErro(erro), 'error');
    botao.disabled = false;
  }
}

/**
 * @param {Object} favorito favorito a remover
 * @param {HTMLButtonElement} botao botão acionado
 * @returns {Promise<void>}
 */
async function desfavoritarPrestador(favorito, botao) {
  botao.disabled = true;
  try {
    await TaskGoAPI.removerFavorito(favorito.prestadorId);
    showToast(`${favorito.nome} removido dos favoritos.`, 'success');
    await carregarFavoritos();
  } catch (erro) {
    showToast(mensagemDeErro(erro), 'error');
    botao.disabled = false;
  }
}

/**
 * Abre o catálogo de um favorito para escolher qual serviço contratar.
 *
 * A rota devolve lista vazia tanto para prestador sem oferta quanto para prestador não aprovado
 * (RN04), então o aviso é o mesmo nos dois casos: não há serviço a contratar agora.
 *
 * @param {Object} favorito `FavoritoResponse`
 * @returns {Promise<void>}
 */
async function abrirCatalogoDoPrestador(favorito) {
  const lista = document.getElementById('catalogoLista');
  document.getElementById('catalogoTitulo').textContent = `Serviços de ${favorito.nome}`;
  lista.innerHTML = '<p class="section-content">Carregando serviços...</p>';
  openModal('modalCatalogoPrestador');

  try {
    const servicos = (await TaskGoAPI.listarServicosDoPrestador(favorito.prestadorId)) || [];
    lista.innerHTML = '';

    if (!servicos.length) {
      lista.innerHTML = '<p class="section-content">Este profissional não tem serviço disponível para contratação no momento.</p>';
      return;
    }

    servicos.forEach((servico) => {
      const item = document.createElement('div');
      item.style.cssText = 'display: flex; justify-content: space-between; align-items: center; gap: 16px; background: var(--bg-dark); border: 1px solid var(--border-color); border-radius: 12px; padding: 16px;';
      item.innerHTML = `
        <div>
          <h4 style="color: white; font-size: 14px; margin-bottom: 4px;">${escaparTexto(servico.categoria)}</h4>
          <p style="color: var(--text-gray); font-size: 12px;">${escaparTexto(servico.descricao || 'Sem descrição')}</p>
        </div>
        <div style="text-align: right; flex-shrink: 0;">
          <strong style="color: var(--success-green); display: block; margin-bottom: 8px;">${formatarMoeda(servico.preco)}</strong>
          <button class="btn-primary btn-contratar-catalogo" style="padding: 8px 14px; font-size: 12px;">Contratar</button>
        </div>
      `;
      item.querySelector('.btn-contratar-catalogo').addEventListener('click', async (evento) => {
        await contratarServico(servico.id, evento.currentTarget);
        closeModal('modalCatalogoPrestador');
      });
      lista.appendChild(item);
    });
  } catch (erro) {
    lista.innerHTML = `<p class="section-content">${escaparTexto(mensagemDeErro(erro))}</p>`;
  }
}

// =====================================================================
// CONTA E ENDEREÇOS DO CLIENTE — dados reais
// =====================================================================
/** @type {number|null} endereço em edição no modal, ou null quando é cadastro novo */
let enderecoEmEdicaoId = null;

/**
 * @param {Object} perfil `ClientePerfilResponse`
 * @returns {void}
 */
function preencherFormularioPerfil(perfil) {
  const form = document.getElementById('formPerfilCliente');
  if (!form) return;

  document.getElementById('perfilNome').value = perfil.nome || '';
  document.getElementById('perfilEmail').value = perfil.email || '';
  document.getElementById('perfilTelefone').value = perfil.telefone || '';
  document.getElementById('perfilCidade').value = perfil.cidade || '';
  document.getElementById('perfilIdade').value = perfil.idade != null ? perfil.idade : '';

  const foto = document.getElementById('perfilFoto');
  const semFoto = document.getElementById('perfilSemFoto');

  if (perfil.fotoUrl) {
    foto.src = perfil.fotoUrl;
    foto.style.display = 'block';
    semFoto.style.display = 'none';
  } else {
    foto.style.display = 'none';
    semFoto.style.display = 'flex';
    semFoto.textContent = iniciaisDoNome(perfil.nome);
  }
}

/**
 * Salva o perfil, aplicando erro por campo nos 400 `VALIDACAO` (em vez de um toast genérico) e
 * mantendo o formulário intacto quando o e-mail já pertence a outra conta.
 *
 * @param {SubmitEvent} evento submit do formulário de perfil
 * @returns {Promise<void>}
 */
async function salvarPerfil(evento) {
  evento.preventDefault();
  const form = evento.currentTarget;

  limparErrosDoFormulario(form);
  if (!validarFormulario(form)) return;

  const idade = document.getElementById('perfilIdade').value;
  const dados = {
    nome: document.getElementById('perfilNome').value.trim(),
    email: document.getElementById('perfilEmail').value.trim(),
    telefone: document.getElementById('perfilTelefone').value.trim() || undefined,
    cidade: document.getElementById('perfilCidade').value.trim() || undefined,
    idade: idade ? Number(idade) : undefined,
  };

  const botao = document.getElementById('btnSalvarPerfil');
  botao.disabled = true;

  try {
    perfilCliente = await TaskGoAPI.atualizarMeuPerfilCliente(dados);
    aplicarPerfilNaTela(perfilCliente);
    showToast('Perfil atualizado.', 'success');
  } catch (erro) {
    if (erro instanceof TaskGoAPI.ApiError && Object.keys(erro.fieldErrors).length) {
      aplicarErrosDoServidor(form, erro.fieldErrors);
    } else {
      showToast(mensagemDeErro(erro), 'error');
    }
    if (perfilCliente) preencherFormularioPerfil(perfilCliente);
  } finally {
    botao.disabled = false;
  }
}

/**
 * @param {HTMLFormElement} form formulário cujos erros de campo devem ser limpos
 * @returns {void}
 */
function limparErrosDoFormulario(form) {
  form.querySelectorAll('.has-error').forEach((campo) => campo.classList.remove('has-error'));
  form.querySelectorAll('.field-error').forEach((erro) => (erro.textContent = ''));
}

/**
 * Envia a foto de perfil. O backend aplica a mesma validação de tipo e tamanho do KYC.
 *
 * @param {Event} evento change do input de arquivo
 * @returns {Promise<void>}
 */
async function enviarFotoPerfil(evento) {
  const arquivo = evento.currentTarget.files[0];
  if (!arquivo) return;

  try {
    perfilCliente = await TaskGoAPI.atualizarMinhaFotoCliente(arquivo);
    aplicarPerfilNaTela(perfilCliente);
    showToast('Foto atualizada.', 'success');
  } catch (erro) {
    showToast(mensagemDeErro(erro), 'error');
  } finally {
    evento.currentTarget.value = '';
  }
}

/**
 * Carrega os endereços do cliente e os renderiza. O endereço padrão é o que a busca e a solicitação
 * usam, então esta lista é mais do que informativa.
 *
 * @returns {Promise<void>}
 */
async function carregarEnderecos() {
  const container = document.getElementById('enderecosContainer');

  try {
    enderecosCliente = (await TaskGoAPI.listarMeusEnderecos()) || [];
  } catch (erro) {
    if (container) container.innerHTML = `<p class="section-content">${escaparTexto(mensagemDeErro(erro))}</p>`;
    return;
  }

  if (!container) return;
  container.innerHTML = '';

  if (!enderecosCliente.length) {
    container.innerHTML = '<p class="section-content">Você ainda não cadastrou endereços. O primeiro que você cadastrar será o padrão.</p>';
    return;
  }

  enderecosCliente.forEach((endereco) => container.appendChild(criarItemEndereco(endereco)));
}

/**
 * @param {Object} endereco `EnderecoClienteResponse`
 * @returns {HTMLElement} item da lista de endereços, com editar, remover e marcar padrão
 */
function criarItemEndereco(endereco) {
  const item = document.createElement('div');
  item.className = 'st-address-item' + (endereco.padrao ? ' default' : '');

  const complemento = endereco.complemento ? ` - ${escaparTexto(endereco.complemento)}` : '';
  const bairro = endereco.bairro ? `${escaparTexto(endereco.bairro)}, ` : '';

  item.innerHTML = `
    <div style="display: flex;">
      <div class="st-address-icon"><i class="fas fa-map-marker-alt"></i></div>
      <div>
        <h4 style="font-size: 15px; color: white; margin-bottom: 4px; display: flex; align-items: center; gap: 8px;">${escaparTexto(endereco.apelido)}${
          endereco.padrao
            ? ' <span style="background: var(--primary-blue); color: white; font-size: 9px; padding: 2px 6px; border-radius: 4px; font-weight: 800;">PADRÃO</span>'
            : ''
        }</h4>
        <p style="color: var(--text-gray); font-size: 13px; line-height: 1.4;">${escaparTexto(endereco.rua)}, ${escaparTexto(endereco.numero)}${complemento}<br>${bairro}${escaparTexto(endereco.cidade)} - ${escaparTexto(endereco.uf)}</p>
      </div>
    </div>
    <div style="display: flex; gap: 4px; align-items: flex-start;">
      ${
        endereco.padrao
          ? ''
          : '<button class="btn-outline btn-padrao" title="Usar como padrão" style="padding: 8px; border: none; color: var(--text-muted);"><i class="fas fa-star"></i></button>'
      }
      <button class="btn-outline btn-editar" title="Editar" style="padding: 8px; border: none; color: var(--text-muted);"><i class="fas fa-pen"></i></button>
      <button class="btn-outline btn-remover" title="Remover" style="padding: 8px; border: none; color: var(--danger-red);"><i class="fas fa-trash-alt"></i></button>
    </div>
  `;

  item.querySelector('.btn-editar').addEventListener('click', () => abrirModalEndereco(endereco));
  item.querySelector('.btn-remover').addEventListener('click', (evento) => removerEndereco(endereco, evento.currentTarget));

  const btnPadrao = item.querySelector('.btn-padrao');
  if (btnPadrao) btnPadrao.addEventListener('click', (evento) => marcarEnderecoPadrao(endereco, evento.currentTarget));

  return item;
}

/**
 * @param {Object|null} endereco endereço a editar, ou null para cadastro novo
 * @returns {void}
 */
function abrirModalEndereco(endereco) {
  const form = document.getElementById('formEndereco');
  limparErrosDoFormulario(form);
  form.reset();

  enderecoEmEdicaoId = endereco ? endereco.id : null;
  document.getElementById('tituloModalEndereco').textContent = endereco ? 'Editar Endereço' : 'Novo Endereço';

  if (endereco) {
    document.getElementById('endApelido').value = endereco.apelido || '';
    document.getElementById('endCep').value = endereco.cep || '';
    document.getElementById('endRua').value = endereco.rua || '';
    document.getElementById('endNumero').value = endereco.numero || '';
    document.getElementById('endComplemento').value = endereco.complemento || '';
    document.getElementById('endBairro').value = endereco.bairro || '';
    document.getElementById('endCidade').value = endereco.cidade || '';
    document.getElementById('endUf').value = endereco.uf || '';
    document.getElementById('endLatitude').value = endereco.latitude != null ? endereco.latitude : '';
    document.getElementById('endLongitude').value = endereco.longitude != null ? endereco.longitude : '';
    document.getElementById('endPadrao').checked = !!endereco.padrao;
  }

  openModal('modalEndereco');
}

/**
 * @param {SubmitEvent} evento submit do formulário de endereço
 * @returns {Promise<void>}
 */
async function salvarEndereco(evento) {
  evento.preventDefault();
  const form = evento.currentTarget;

  limparErrosDoFormulario(form);
  if (!validarFormulario(form)) return;

  const latitude = document.getElementById('endLatitude').value;
  const longitude = document.getElementById('endLongitude').value;

  const dados = {
    apelido: document.getElementById('endApelido').value.trim(),
    cep: document.getElementById('endCep').value.trim(),
    rua: document.getElementById('endRua').value.trim(),
    numero: document.getElementById('endNumero').value.trim(),
    complemento: document.getElementById('endComplemento').value.trim() || undefined,
    bairro: document.getElementById('endBairro').value.trim() || undefined,
    cidade: document.getElementById('endCidade').value.trim(),
    uf: document.getElementById('endUf').value.trim().toUpperCase(),
    latitude: latitude ? Number(latitude) : undefined,
    longitude: longitude ? Number(longitude) : undefined,
    padrao: document.getElementById('endPadrao').checked,
  };

  const botao = document.getElementById('btnSalvarEndereco');
  botao.disabled = true;

  try {
    if (enderecoEmEdicaoId) {
      await TaskGoAPI.atualizarMeuEndereco(enderecoEmEdicaoId, dados);
    } else {
      await TaskGoAPI.criarMeuEndereco(dados);
    }
    closeModal('modalEndereco');
    showToast('Endereço salvo.', 'success');
    await carregarEnderecos();
    atualizarLocalidadeDoCabecalho();
  } catch (erro) {
    if (erro instanceof TaskGoAPI.ApiError && Object.keys(erro.fieldErrors).length) {
      aplicarErrosDoServidor(form, erro.fieldErrors);
    } else {
      showToast(mensagemDeErro(erro), 'error');
    }
  } finally {
    botao.disabled = false;
  }
}

/**
 * Marca um endereço como padrão. O backend garante que apenas um permaneça padrão.
 *
 * @param {Object} endereco endereço a promover
 * @param {HTMLButtonElement} botao botão acionado
 * @returns {Promise<void>}
 */
async function marcarEnderecoPadrao(endereco, botao) {
  botao.disabled = true;
  try {
    await TaskGoAPI.atualizarMeuEndereco(endereco.id, {
      apelido: endereco.apelido,
      cep: endereco.cep,
      rua: endereco.rua,
      numero: endereco.numero,
      complemento: endereco.complemento || undefined,
      bairro: endereco.bairro || undefined,
      cidade: endereco.cidade,
      uf: endereco.uf,
      latitude: endereco.latitude != null ? endereco.latitude : undefined,
      longitude: endereco.longitude != null ? endereco.longitude : undefined,
      padrao: true,
    });
    await carregarEnderecos();
    atualizarLocalidadeDoCabecalho();
    showToast('Endereço padrão atualizado.', 'success');
  } catch (erro) {
    showToast(mensagemDeErro(erro), 'error');
    botao.disabled = false;
  }
}

/**
 * @param {Object} endereco endereço a remover
 * @param {HTMLButtonElement} botao botão acionado
 * @returns {Promise<void>}
 */
async function removerEndereco(endereco, botao) {
  const confirmado = await confirmar({
    titulo: 'Remover endereço',
    texto: `Remover "${endereco.apelido}" da sua lista? As solicitações que já usam este endereço continuam válidas.`,
    rotuloConfirmar: 'Remover',
    perigo: true,
  });
  if (!confirmado) return;

  botao.disabled = true;
  try {
    await TaskGoAPI.removerMeuEndereco(endereco.id);
    showToast('Endereço removido.', 'success');
    await carregarEnderecos();
    atualizarLocalidadeDoCabecalho();
  } catch (erro) {
    showToast(mensagemDeErro(erro), 'error');
    botao.disabled = false;
  }
}

/**
 * Desativa a conta do cliente. O backend recusa com 409 quando há atendimento em curso — nesse caso
 * a conta permanece ativa e a recusa é exibida.
 *
 * @returns {Promise<void>}
 */
async function desativarConta() {
  const confirmado = await confirmar({
    titulo: 'Excluir minha conta',
    texto: 'Você deixará de conseguir entrar na plataforma. O histórico dos serviços já realizados é preservado. Deseja continuar?',
    rotuloConfirmar: 'Excluir conta',
    perigo: true,
  });
  if (!confirmado) return;

  const botao = document.getElementById('btnExcluirConta');
  botao.disabled = true;

  try {
    await TaskGoAPI.desativarMinhaConta();
    TaskGoAPI.logout();
    window.location.href = 'login.html';
  } catch (erro) {
    showToast(mensagemDeErro(erro), 'error');
    botao.disabled = false;
  }
}

// =====================================================================
// EXTRATO DE PAGAMENTOS E COMPROVANTE — dados reais
// =====================================================================
/** @type {Object[]} último extrato carregado, indexado pela ordem das linhas da tabela */
let extratoCliente = [];

const ROTULOS_PAGAMENTO = {
  RETIDO: { texto: 'Em custódia', classe: 'pending' },
  LIBERADO: { texto: 'Concluído', classe: 'paid' },
  ESTORNADO: { texto: 'Estornado', classe: 'refund' },
  ESTORNADO_PARCIAL: { texto: 'Estornado em parte', classe: 'refund' },
  FALHOU: { texto: 'Falhou', classe: 'refund' },
};

const ICONES_TX = {
  ELETRICA: { icone: 'fa-bolt', cor: 'var(--primary-blue)' },
  LIMPEZA: { icone: 'fa-broom', cor: 'var(--success-green)' },
  CLIMATIZACAO: { icone: 'fa-snowflake', cor: 'var(--primary-blue)' },
  MONTAGEM: { icone: 'fa-hammer', cor: 'var(--warning-yellow)' },
};

/**
 * Carrega o extrato do cliente autenticado e o renderiza na tabela de transações.
 *
 * @returns {Promise<void>}
 */
async function carregarExtrato() {
  const corpo = document.getElementById('extratoCorpo');
  if (!corpo) return;

  corpo.innerHTML = '<tr><td colspan="5" class="section-content">Carregando transações...</td></tr>';

  try {
    extratoCliente = (await TaskGoAPI.listarMeuExtrato()) || [];
    renderizarExtrato(extratoCliente);
  } catch (erro) {
    corpo.innerHTML = `<tr><td colspan="5" class="section-content">${escaparTexto(mensagemDeErro(erro))}</td></tr>`;
  }
}

/**
 * @param {Object[]} lançamentos lista de `PagamentoExtratoResponse`
 * @returns {void}
 */
function renderizarExtrato(lancamentos) {
  const corpo = document.getElementById('extratoCorpo');
  corpo.innerHTML = '';

  if (!lancamentos.length) {
    corpo.innerHTML = '<tr><td colspan="5" class="section-content">Você ainda não tem pagamentos. Eles aparecem aqui depois que você contrata e paga um serviço.</td></tr>';
    return;
  }

  lancamentos.forEach((item, indice) => corpo.appendChild(criarLinhaExtrato(item, indice)));
}

/**
 * Monta uma linha do extrato. Estorno — integral ou parcial — é apresentado com badge própria e
 * valor riscado, para não se confundir com pagamento efetivado.
 *
 * @param {Object} item `PagamentoExtratoResponse`
 * @param {number} indice posição no extrato, usada para abrir o comprovante correspondente
 * @returns {HTMLTableRowElement}
 */
function criarLinhaExtrato(item, indice) {
  const linha = document.createElement('tr');
  const rotulo = ROTULOS_PAGAMENTO[item.status] || { texto: item.status, classe: '' };
  const estorno = item.status === 'ESTORNADO' || item.status === 'ESTORNADO_PARCIAL';
  const estilo = ICONES_TX[normalizarCategoria(item.categoria)] || { icone: 'fa-tools', cor: 'var(--text-gray)' };

  const valorApresentado = item.status === 'ESTORNADO_PARCIAL' && item.valorEstornado != null
    ? formatarMoeda(item.valorEstornado)
    : formatarMoeda(item.valorBruto);

  linha.innerHTML = `
    <td>
      <div class="tx-info">
        <div class="tx-icon" style="color: ${estilo.cor};"><i class="fas ${estilo.icone}"></i></div>
        <div class="tx-details">
          <h4>${escaparTexto(item.categoria || 'Serviço')}</h4>
          <p>${
            estorno
              ? '<i class="fas fa-rotate-left" style="color: var(--danger-red);"></i> Valor devolvido'
              : `<i class="fas fa-user" style="color: var(--text-gray);"></i> ${escaparTexto(item.prestadorNome)}`
          }</p>
        </div>
      </div>
    </td>
    <td>
      <span style="display:block; color: white; font-weight: 700; font-size: 13px; margin-bottom: 4px;">${escaparTexto(formatarDataHora(item.criadoEm))}</span>
      <span style="color: var(--text-gray); font-size: 11px; font-weight: 600;"><i class="fas fa-credit-card"></i> ${escaparTexto(item.metodoPagamento || '—')}</span>
    </td>
    <td><span class="badge-tx ${rotulo.classe}">${escaparTexto(rotulo.texto)}</span></td>
    <td style="text-align: right;">
      <div class="tx-amount"${estorno ? ' style="color: var(--text-gray); text-decoration: line-through;"' : ''}>${valorApresentado}</div>
      ${
        item.status === 'ESTORNADO_PARCIAL' && item.valorTaxaCancelamento != null
          ? `<div style="font-size: 11px; color: var(--danger-red); font-weight: 700;">Taxa retida ${formatarMoeda(item.valorTaxaCancelamento)}</div>`
          : ''
      }
    </td>
    <td style="text-align: center;">
      <button class="btn-outline btn-comprovante" style="padding: 8px; border: none; color: var(--text-gray);" title="Ver comprovante"><i class="fas fa-file-invoice"></i></button>
    </td>
  `;

  linha.querySelector('.btn-comprovante').addEventListener('click', () => abrirComprovante(indice));
  return linha;
}

/**
 * Monta o comprovante inteiramente a partir do lançamento do extrato — nenhum valor é calculado na
 * página, e a taxa exibida é a que o backend apurou no momento do pagamento.
 *
 * @param {number} indice posição do lançamento no extrato carregado
 * @returns {void}
 */
function abrirComprovante(indice) {
  const item = extratoCliente[indice];
  if (!item) return;

  const parcial = item.status === 'ESTORNADO_PARCIAL';
  const estorno = item.status === 'ESTORNADO' || parcial;
  const rotulo = ROTULOS_PAGAMENTO[item.status] || { texto: item.status };

  document.getElementById('reciboSolicitacao').textContent = item.solicitacaoId;
  document.getElementById('reciboData').textContent = formatarDataHora(item.criadoEm);
  document.getElementById('reciboPro').textContent = item.prestadorNome || '—';
  document.getElementById('reciboServico').textContent = item.categoria || 'Serviço';
  document.getElementById('reciboMetodo').textContent = item.metodoPagamento || '—';
  document.getElementById('reciboValorBruto').textContent = formatarMoeda(item.valorBruto);
  document.getElementById('reciboTaxa').textContent = formatarMoeda(item.valorTaxa);
  document.getElementById('reciboSituacao').textContent = `Situação do pagamento: ${rotulo.texto}`;

  const selo = document.getElementById('reciboSelo');
  selo.textContent = estorno ? 'ESTORNADO' : 'PAGO';
  selo.style.color = estorno ? 'var(--danger-red)' : '';
  selo.style.borderColor = estorno ? 'var(--danger-red)' : '';

  const linhaDevolvido = document.getElementById('reciboLinhaDevolvido');
  const linhaRetido = document.getElementById('reciboLinhaRetido');

  if (parcial) {
    linhaDevolvido.style.display = 'flex';
    linhaRetido.style.display = 'flex';
    document.getElementById('reciboValorEstornado').textContent = formatarMoeda(item.valorEstornado);
    document.getElementById('reciboTaxaCancelamento').textContent = formatarMoeda(item.valorTaxaCancelamento);
    document.getElementById('reciboTotalRotulo').textContent = 'Devolvido a você';
    document.getElementById('reciboValorTotal').textContent = formatarMoeda(item.valorEstornado);
  } else if (item.status === 'ESTORNADO') {
    linhaDevolvido.style.display = 'flex';
    linhaRetido.style.display = 'none';
    document.getElementById('reciboValorEstornado').textContent = formatarMoeda(item.valorBruto);
    document.getElementById('reciboTotalRotulo').textContent = 'Devolvido a você';
    document.getElementById('reciboValorTotal').textContent = formatarMoeda(item.valorBruto);
  } else {
    linhaDevolvido.style.display = 'none';
    linhaRetido.style.display = 'none';
    document.getElementById('reciboTotalRotulo').textContent = 'Total Pago';
    document.getElementById('reciboValorTotal').textContent = formatarMoeda(item.valorBruto);
  }

  openModal('modalRecibo');
}

// =====================================================================
// ACOMPANHAMENTO DO ATENDIMENTO — dados reais
// =====================================================================
window.meuMapa = null;

/** @type {number|null} solicitação em curso (ACEITO ou EM_ANDAMENTO) apresentada na aba */
let solicitacaoAcompanhadaId = null;

/** @type {Object|null} detalhe da solicitação acompanhada */
let solicitacaoAcompanhada = null;

/** @type {L.Marker|null} marcador do endereço do atendimento */
let marcadorEndereco = null;

/**
 * Leva o cliente à aba de acompanhamento de uma solicitação específica.
 *
 * @param {number} solicitacaoId
 * @returns {void}
 */
function irParaAcompanhamento(solicitacaoId) {
  solicitacaoAcompanhadaId = solicitacaoId;

  document.querySelectorAll('.sidebar-nav .nav-item').forEach((l) => l.classList.remove('active'));
  const menuAndamento = document.getElementById('menuAndamento');
  if (menuAndamento) {
    menuAndamento.style.display = 'flex';
    menuAndamento.classList.add('active');
  }

  mudarAba(null, 'aba-rastreamento');
}

/**
 * Carrega o detalhe da solicitação acompanhada e monta painel, código, timeline e mapa a partir dele.
 *
 * @returns {Promise<void>}
 */
async function carregarAcompanhamento() {
  const vazio = document.getElementById('acompanhamentoVazio');
  const conteudo = document.getElementById('acompanhamentoConteudo');
  if (!vazio || !conteudo) return;

  if (!solicitacaoAcompanhadaId) {
    try {
      const pedidos = (await TaskGoAPI.listarSolicitacoes()) || [];
      const emCurso = pedidos.find((p) => p.status === 'ACEITO' || p.status === 'EM_ANDAMENTO');
      solicitacaoAcompanhadaId = emCurso ? emCurso.id : null;
    } catch (erro) {
      vazio.style.display = 'block';
      vazio.textContent = mensagemDeErro(erro);
      conteudo.style.display = 'none';
      return;
    }
  }

  if (!solicitacaoAcompanhadaId) {
    vazio.style.display = 'block';
    vazio.textContent = 'Você não tem atendimento em curso. Uma solicitação aceita ou em andamento aparece aqui.';
    conteudo.style.display = 'none';
    return;
  }

  try {
    solicitacaoAcompanhada = await TaskGoAPI.obterSolicitacao(solicitacaoAcompanhadaId);
  } catch (erro) {
    vazio.style.display = 'block';
    vazio.textContent = mensagemDeErro(erro);
    conteudo.style.display = 'none';
    return;
  }

  vazio.style.display = 'none';
  conteudo.style.display = 'grid';

  const pedido = solicitacaoAcompanhada;
  document.getElementById('acompPrestador').textContent = pedido.prestadorNome || '—';
  document.getElementById('acompCategoria').textContent = pedido.categoria || 'Serviço';
  document.getElementById('acompValor').textContent = formatarMoeda(pedido.valor);

  const blocoPin = document.getElementById('acompPinBloco');
  if (pedido.pinConfirmacao) {
    blocoPin.style.display = 'block';
    document.getElementById('acompPin').textContent = pedido.pinConfirmacao;
  } else {
    blocoPin.style.display = 'none';
  }

  const btnChat = document.getElementById('btnAbrirChatServico');
  btnChat.onclick = () => abrirChatDoServico(pedido);

  renderizarTimeline(pedido);
  atualizarMapaDoAtendimento(pedido);
}

/**
 * Monta a timeline a partir do estado e dos momentos reais. Etapa não cumprida aparece sem horário —
 * a página não inventa previsão de chegada, porque a plataforma não rastreia deslocamento.
 *
 * @param {Object} pedido `SolicitacaoResponse`
 * @returns {void}
 */
function renderizarTimeline(pedido) {
  const container = document.getElementById('acompTimeline');
  const etapas = [
    {
      titulo: 'Solicitação enviada',
      momento: pedido.criadoEm,
      pendente: 'Aguardando envio.',
    },
    {
      titulo: 'Aceita pelo profissional',
      momento: pedido.aceitoEm,
      pendente: 'Aguardando o profissional aceitar.',
    },
    {
      titulo: 'Atendimento iniciado',
      momento: pedido.iniciadoEm,
      pendente: 'Aguardando o profissional informar o código de segurança no local.',
    },
    {
      titulo: 'Atendimento concluído',
      momento: pedido.concluidoEm,
      pendente: 'Aguardando conclusão pelo profissional.',
    },
  ];

  const primeiraPendente = etapas.findIndex((e) => !e.momento);

  container.innerHTML = etapas
    .map((etapa, indice) => {
      const cumprida = !!etapa.momento;
      const atual = indice === primeiraPendente;
      const classe = cumprida ? 't-step completed' : atual ? 't-step current' : 't-step';
      const icone = cumprida
        ? '<i class="fas fa-check"></i>'
        : atual
          ? '<div class="pulse-dot"></div>'
          : '';

      return `
        <div class="${classe}">
          <div class="t-step-icon">${icone}</div>
          <div class="t-step-content">
            <h5>${escaparTexto(etapa.titulo)}</h5>
            <p>${cumprida ? escaparTexto(formatarDataHora(etapa.momento)) : escaparTexto(etapa.pendente)}</p>
          </div>
        </div>
      `;
    })
    .join('');
}

/**
 * Apresenta no mapa apenas o endereço do atendimento. Não há marcador do profissional, linha entre
 * pontos nem previsão de chegada, porque a plataforma não recebe a posição dele.
 *
 * @param {Object} pedido `SolicitacaoResponse`
 * @returns {void}
 */
function atualizarMapaDoAtendimento(pedido) {
  const secao = document.getElementById('acompanhamentoMapaSecao');
  const endereco = pedido.enderecoAtendimento;
  const temCoordenadas = endereco && endereco.latitude != null && endereco.longitude != null;

  if (!temCoordenadas) {
    secao.style.display = 'none';
    return;
  }

  secao.style.display = 'block';
  initMap();
  if (!window.meuMapa) return;

  const ponto = [endereco.latitude, endereco.longitude];
  window.meuMapa.setView(ponto, 15);

  if (marcadorEndereco) {
    marcadorEndereco.setLatLng(ponto);
  } else {
    const iconeLocal = L.divIcon({
      html: '<i class="fas fa-map-marker-alt" style="color: #1DA1F2; font-size: 30px; text-shadow: 0 2px 5px rgba(0,0,0,0.3);"></i>',
      className: 'custom-div-icon',
      iconSize: [30, 30],
      iconAnchor: [15, 30],
    });
    marcadorEndereco = L.marker(ponto, { icon: iconeLocal }).addTo(window.meuMapa);
  }

  marcadorEndereco.bindPopup(`${escaparTexto(endereco.rua)}, ${escaparTexto(endereco.numero)}`);
  window.meuMapa.invalidateSize(true);
}

/**
 * Cria o mapa Leaflet uma única vez. O centro real é definido por `atualizarMapaDoAtendimento`.
 *
 * @returns {void}
 */
function initMap() {
  const divMapa = document.getElementById('map-real');
  if (!divMapa) return;

  if (window.meuMapa !== null) {
    window.meuMapa.invalidateSize();
    return;
  }

  window.meuMapa = L.map('map-real', { zoomControl: false });
  L.tileLayer('https://{s}.basemaps.cartocdn.com/light_all/{z}/{x}/{y}{r}.png', { maxZoom: 19 }).addTo(window.meuMapa);
  window.meuMapa.setView([0, 0], 2);
}

// =====================================================================
// AVISOS DE ATIVIDADE — apurados do estado (US-04..US-10)
// =====================================================================
/** @type {Object[]} avisos carregados na última consulta */
let avisosCliente = [];

const ICONES_AVISO = {
  PAGAMENTO_PENDENTE: { icone: 'fa-credit-card', fundo: 'bg-yellow' },
  ATENDIMENTO_EM_ANDAMENTO: { icone: 'fa-route', fundo: 'bg-blue' },
  AVALIACAO_PENDENTE: { icone: 'fa-star', fundo: 'bg-yellow' },
  SOLICITACAO_RECUSADA: { icone: 'fa-times-circle', fundo: 'bg-red' },
  CANCELAMENTO_ESTORNADO: { icone: 'fa-rotate-left', fundo: 'bg-red' },
  MENSAGENS_NAO_LIDAS: { icone: 'fa-comment-dots', fundo: 'bg-blue' },
};

/**
 * Carrega os avisos de atividade e reflete a contagem no badge do cabeçalho.
 *
 * Os avisos são apurados do estado atual pelo backend — não há marcação de leitura, então o aviso
 * desaparece quando o fato que o originou é resolvido, e não quando o cliente o vê.
 *
 * @returns {Promise<void>}
 */
async function carregarAvisos() {
  try {
    avisosCliente = (await TaskGoAPI.listarMinhasNotificacoes()) || [];
  } catch {
    avisosCliente = [];
  }

  const badge = document.getElementById('avisosBadge');
  if (badge) {
    if (avisosCliente.length) {
      badge.textContent = String(avisosCliente.length);
      badge.style.display = 'flex';
    } else {
      badge.style.display = 'none';
    }
  }
}

/**
 * Abre a lista de avisos, recarregando-a — a contagem exibida no badge pode ter envelhecido.
 *
 * @returns {Promise<void>}
 */
async function abrirNotificacoes() {
  const corpo = document.getElementById('avisosCorpo');
  corpo.innerHTML = '<p class="section-content">Carregando atividades...</p>';
  openModal('modalNotificacoes');

  await carregarAvisos();
  renderizarAvisos(avisosCliente);
}

/**
 * @param {Object[]} avisos lista de `NotificacaoResponse`
 * @returns {void}
 */
function renderizarAvisos(avisos) {
  const corpo = document.getElementById('avisosCorpo');
  corpo.innerHTML = '';

  if (!avisos.length) {
    corpo.innerHTML = '<p class="section-content">Nenhuma pendência no momento.</p>';
    return;
  }

  avisos.forEach((aviso) => {
    const estilo = ICONES_AVISO[aviso.tipo] || { icone: 'fa-bell', fundo: 'bg-blue' };
    const card = document.createElement('div');
    card.className = 'notif-card-pro';
    card.innerHTML = `
      <div class="notif-avatar-container">
        <div style="width: 100%; height: 100%; border-radius: 50%; background: var(--bg-dark); border: 2px solid var(--border-color); display: flex; justify-content: center; align-items: center; font-size: 18px; color: var(--text-gray);">
          <i class="fas ${estilo.icone}"></i>
        </div>
        <div class="notif-badge-icon ${estilo.fundo}"><i class="fas ${estilo.icone}"></i></div>
      </div>
      <div class="notif-content-pro">
        <h4>${escaparTexto(aviso.texto)} <span class="notif-time-pro">${escaparTexto(formatarDataHora(aviso.momento))}</span></h4>
        ${
          aviso.solicitacaoId
            ? '<div class="notif-actions-pro"><button class="btn-notif-primary btn-ver-pedido"><i class="fas fa-clipboard-list"></i> Ver pedido</button></div>'
            : ''
        }
      </div>
    `;

    const btnVer = card.querySelector('.btn-ver-pedido');
    if (btnVer) {
      btnVer.addEventListener('click', () => {
        closeModal('modalNotificacoes');
        mudarAba(null, 'aba-pedidos');
        document.querySelectorAll('.sidebar-nav .nav-item').forEach((l) => l.classList.remove('active'));
      });
    }

    corpo.appendChild(card);
  });
}

/**
 * Abre o simulador de custo. As categorias vêm de `/servicos-ofertados/categorias` — o simulador
 * não conhece categoria alguma por conta própria.
 *
 * @returns {Promise<void>}
 */
async function abrirSimulacao() {
  document.getElementById('simResultado').style.display = 'none';
  openModal('modalSimulacao');

  const select = document.getElementById('simServico');
  select.innerHTML = '<option value="">Carregando categorias...</option>';

  try {
    const categorias = await carregarCategorias();
    if (!categorias.length) {
      select.innerHTML = '<option value="">Nenhuma categoria disponível no momento</option>';
      return;
    }
    select.innerHTML = categorias
      .map((c) => `<option value="${escaparAtributo(c.categoria)}">${escaparTexto(c.categoria)}</option>`)
      .join('');
  } catch (erro) {
    select.innerHTML = '<option value="">Não foi possível carregar as categorias</option>';
    showToast(mensagemDeErro(erro), 'error');
  }
}

/**
 * Consulta a faixa de preço realmente praticada na categoria escolhida.
 *
 * Com amostra menor que três o backend devolve apenas `mensagem` e valores nulos — nesse caso
 * exibimos a mensagem e **nenhum** valor, para não inventar faixa a partir de uma amostra que a
 * plataforma considera insuficiente.
 *
 * @returns {Promise<void>}
 */
async function calcularSimulacao() {
  const categoria = document.getElementById('simServico').value;
  if (!categoria) {
    showToast('Escolha uma categoria.', 'error');
    return;
  }

  const btn = document.getElementById('btnSimular');
  const rotuloOriginal = btn.innerHTML;
  btn.innerHTML = '<i class="fas fa-circle-notch fa-spin"></i> Consultando preços...';
  btn.disabled = true;

  const resultado = document.getElementById('simResultado');
  const faixa = document.getElementById('simFaixa');
  const aviso = document.getElementById('simAviso');

  try {
    const estimativa = await TaskGoAPI.obterEstimativa(categoria);
    resultado.style.display = 'block';

    if (estimativa.mediana == null) {
      faixa.style.display = 'none';
      aviso.style.display = 'block';
      aviso.textContent = estimativa.mensagem || 'Amostra insuficiente para apurar a faixa de preço desta categoria.';
    } else {
      aviso.style.display = 'none';
      faixa.style.display = 'block';
      document.getElementById('simPreco').textContent = formatarMoeda(estimativa.mediana);
      document.getElementById('simMinimo').textContent = formatarMoeda(estimativa.minimo);
      document.getElementById('simMaximo').textContent = formatarMoeda(estimativa.maximo);
      document.getElementById('simAmostra').textContent = `${estimativa.amostra} ${estimativa.amostra === 1 ? 'serviço publicado' : 'serviços publicados'}`;
    }
  } catch (erro) {
    showToast(mensagemDeErro(erro), 'error');
  } finally {
    btn.innerHTML = rotuloOriginal;
    btn.disabled = false;
  }
}

/**
 * Fecha o simulador e leva o cliente à busca real da categoria consultada — o simulador informa a
 * faixa, contratar é a busca.
 *
 * @returns {void}
 */
function buscarCategoriaDoSimulador() {
  const categoria = document.getElementById('simServico').value;
  closeModal('modalSimulacao');
  if (!categoria) return;
  document.getElementById('buscaTermo').value = categoria;
  mudarAba(null, 'aba-home');
  document.querySelectorAll('.sidebar-nav .nav-item').forEach((l) => l.classList.remove('active'));
  const primeiro = document.querySelector('.sidebar-nav .nav-item');
  if (primeiro) primeiro.classList.add('active');
  buscarPorCategoria(categoria);
}

// =====================================================================
// CENTRAL DE AJUDA (FAQ) e CHAT COM IA (decorativo — fora do escopo do MVP)
// =====================================================================
/**
 * Filtra as dúvidas frequentes da própria página pelo termo digitado.
 *
 * A plataforma não tem base de artigos de ajuda, então a busca atua sobre o que existe — o FAQ da
 * página — em vez de apenas confirmar uma consulta que nunca acontece.
 *
 * @returns {void}
 */
function filtrarAjuda() {
  const termo = normalizarCategoria(document.getElementById('ajudaBusca').value);
  const itens = document.querySelectorAll('.faq-item');
  let visiveis = 0;

  itens.forEach((item) => {
    const casa = !termo || normalizarCategoria(item.textContent).includes(termo);
    item.style.display = casa ? 'block' : 'none';
    if (casa) visiveis += 1;
  });

  const semResultado = document.getElementById('ajudaSemResultado');
  if (semResultado) semResultado.style.display = visiveis ? 'none' : 'block';
}

function toggleFaq(element) {
  const faqItem = element.parentElement;
  const isActive = faqItem.classList.contains('active');
  document.querySelectorAll('.faq-item').forEach((item) => item.classList.remove('active'));
  if (!isActive) faqItem.classList.add('active');
}
