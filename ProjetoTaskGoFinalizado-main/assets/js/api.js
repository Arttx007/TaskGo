/**
 * Camada única de acesso à API do TaskGo. Centraliza toda chamada de rede do frontend, conforme
 * convenção do projeto (ver CLAUDE.md) — nenhum outro arquivo deve chamar `fetch` diretamente.
 */
const TaskGoAPI = (() => {
  const BASE_URL = window.TASKGO_API_BASE_URL || 'http://localhost:8080';
  const CHAVE_SESSAO = 'taskgo_session';

  /**
   * Erro normalizado de uma chamada de API, com o status HTTP e os erros de campo (quando houver).
   */
  class ApiError extends Error {
    /**
     * @param {number} status código HTTP retornado
     * @param {string} message mensagem legível do erro
     * @param {Object<string,string>} fieldErrors erros de validação por campo (vazio se não houver)
     */
    constructor(status, message, fieldErrors) {
      super(message);
      this.status = status;
      this.fieldErrors = fieldErrors || {};
    }
  }

  /** @returns {{token: string, usuario: {id: number, nome: string, tipo: string, statusKyc?: string}}|null} a sessão salva, ou null se não houver login */
  function getSessaoAtual() {
    const bruto = localStorage.getItem(CHAVE_SESSAO);
    if (!bruto) return null;
    try {
      return JSON.parse(bruto);
    } catch {
      return null;
    }
  }

  /**
   * @param {string} token JWT emitido pelo backend
   * @param {{id: number, nome: string, tipo: string, statusKyc?: string}} usuario dados básicos do usuário autenticado
   * @returns {void}
   */
  function salvarSessao(token, usuario) {
    localStorage.setItem(CHAVE_SESSAO, JSON.stringify({ token, usuario }));
  }

  /** @returns {void} encerra a sessão local */
  function logout() {
    localStorage.removeItem(CHAVE_SESSAO);
  }

  /**
   * @param {string} token JWT no formato header.payload.assinatura
   * @returns {boolean} true se o token estiver ausente, malformado ou com a claim `exp` vencida
   */
  function tokenExpirado(token) {
    if (!token) return true;
    try {
      const payload = JSON.parse(atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')));
      return !payload.exp || Date.now() >= payload.exp * 1000;
    } catch {
      return true;
    }
  }

  /**
   * Como `getSessaoAtual`, mas descarta (e limpa) sessões cujo token já expirou — assim a página
   * nunca trata um login vencido como válido só porque ainda está no localStorage.
   *
   * @returns {{token: string, usuario: {id: number, nome: string, tipo: string, statusKyc?: string}}|null}
   */
  function getSessaoValida() {
    const sessao = getSessaoAtual();
    if (!sessao || tokenExpirado(sessao.token)) {
      if (sessao) logout();
      return null;
    }
    return sessao;
  }

  /**
   * @param {string} path caminho relativo (ex.: "/servicos")
   * @param {{method?: string, body?: any, auth?: boolean}} [opcoes]
   * @returns {Promise<any>} corpo da resposta já convertido de JSON (ou null se vazio)
   * @throws {ApiError} quando a resposta não é 2xx
   */
  async function request(path, opcoes = {}) {
    const { method = 'GET', body, auth = true } = opcoes;
    const headers = { 'Content-Type': 'application/json' };

    if (auth) {
      const sessao = getSessaoAtual();
      if (sessao && sessao.token) {
        headers['Authorization'] = `Bearer ${sessao.token}`;
      }
    }

    const resposta = await fetch(BASE_URL + path, {
      method,
      headers,
      body: body !== undefined ? JSON.stringify(body) : undefined,
    });

    return tratarResposta(resposta);
  }

  /**
   * Como `request`, mas envia um `FormData` (multipart) em vez de JSON — usado para upload de
   * documentos de KYC.
   *
   * @param {string} path caminho relativo
   * @param {FormData} formData dados do formulário, incluindo arquivos
   * @returns {Promise<any>} corpo da resposta já convertido de JSON
   * @throws {ApiError} quando a resposta não é 2xx
   */
  async function requestMultipart(path, formData) {
    const headers = {};
    const sessao = getSessaoAtual();
    if (sessao && sessao.token) {
      headers['Authorization'] = `Bearer ${sessao.token}`;
    }

    const resposta = await fetch(BASE_URL + path, {
      method: 'POST',
      headers,
      body: formData,
    });

    return tratarResposta(resposta);
  }

  async function tratarResposta(resposta) {
    const texto = await resposta.text();
    const corpo = texto ? JSON.parse(texto) : null;

    if (!resposta.ok) {
      const mensagem = (corpo && corpo.message) || 'Ocorreu um erro inesperado';
      const fieldErrors = (corpo && corpo.fieldErrors) || {};
      throw new ApiError(resposta.status, mensagem, fieldErrors);
    }

    return corpo;
  }

  // --- Autenticação e cadastro (US-01) ---

  /**
   * @param {string} email
   * @param {string} senha
   * @param {'CLIENTE'|'PRESTADOR'|'ADMIN'} tipoUsuario
   * @returns {Promise<{token: string, tipoUsuario: string, id: number, nome: string}>}
   */
  function login(email, senha, tipoUsuario) {
    return request('/auth/login', { method: 'POST', body: { email, senha, tipoUsuario }, auth: false });
  }

  /**
   * @param {{nome: string, email: string, senha: string, cidade?: string, idade?: number, tipoCliente?: string}} dados
   * @returns {Promise<Object>} ClienteResponse
   */
  function registrarCliente(dados) {
    return request('/clientes', { method: 'POST', body: dados, auth: false });
  }

  /**
   * @param {{nome: string, email: string, senha: string, especialidade?: string, cidade?: string}} dados
   * @returns {Promise<Object>} PrestadorResponse (statusKyc=PENDENTE)
   */
  function registrarPrestador(dados) {
    return request('/prestadores', { method: 'POST', body: dados, auth: false });
  }

  /**
   * @param {number} prestadorId
   * @returns {Promise<Object>} PrestadorResponse, inclui statusKyc
   */
  function obterPrestador(prestadorId) {
    return request(`/prestadores/${prestadorId}`);
  }

  /**
   * @param {number} prestadorId
   * @param {File} documentoIdentidade
   * @param {File} comprovantePix
   * @returns {Promise<Object>} PrestadorResponse com statusKyc atualizado para PENDENTE
   */
  function enviarDocumentosKyc(prestadorId, documentoIdentidade, comprovantePix) {
    const formData = new FormData();
    formData.append('documentoIdentidade', documentoIdentidade);
    formData.append('comprovantePix', comprovantePix);
    return requestMultipart(`/prestadores/${prestadorId}/documentos`, formData);
  }

  // --- Catálogo de serviços do prestador (US-02) ---

  /**
   * @param {{categoria: string, descricao?: string, preco: number, localizacaoId?: number}} dados
   * @returns {Promise<Object>} ServicoOfertadoResponse
   */
  function criarServico(dados) {
    return request('/servicos-ofertados', { method: 'POST', body: dados });
  }

  /** @returns {Promise<Object[]>} lista de ServicoOfertadoResponse do prestador autenticado */
  function listarMeusServicos() {
    return request('/servicos-ofertados/meus');
  }

  /**
   * @param {number} servicoId
   * @param {{categoria: string, descricao?: string, preco: number, localizacaoId?: number}} dados
   * @returns {Promise<Object>} ServicoOfertadoResponse atualizado
   */
  function atualizarServico(servicoId, dados) {
    return request(`/servicos-ofertados/${servicoId}`, { method: 'PUT', body: dados });
  }

  /**
   * @param {number} servicoId
   * @param {boolean} ativo
   * @returns {Promise<Object>} ServicoOfertadoResponse atualizado
   */
  function alternarServicoAtivo(servicoId, ativo) {
    return request(`/servicos-ofertados/${servicoId}/ativo?ativo=${ativo}`, { method: 'PUT' });
  }

  /**
   * @param {number} servicoId
   * @returns {Promise<void>}
   */
  function excluirServico(servicoId) {
    return request(`/servicos-ofertados/${servicoId}`, { method: 'DELETE' });
  }

  // --- Busca por geolocalização (US-03) ---

  /**
   * @param {{categoria: string, lat?: number, lon?: number, raioKm?: number, cidade?: string}} filtro
   * @returns {Promise<{resultados: Object[], mensagem: string|null}>}
   */
  function buscarServicos(filtro) {
    const params = new URLSearchParams();
    Object.entries(filtro).forEach(([chave, valor]) => {
      if (valor !== undefined && valor !== null && valor !== '') params.set(chave, valor);
    });
    return request(`/servicos-ofertados/buscar?${params.toString()}`, { auth: false });
  }

  // --- Ciclo de vida da solicitação (US-04, US-05, US-07, US-09, US-10) ---

  /**
   * @param {number} servicoOfertadoId
   * @returns {Promise<Object>} SolicitacaoResponse (status=SOLICITADO)
   */
  function criarSolicitacao(servicoOfertadoId) {
    return request('/servicos', { method: 'POST', body: { servicoOfertadoId } });
  }

  /** @returns {Promise<Object[]>} lista de SolicitacaoResponse do usuário autenticado (cliente ou prestador) */
  function listarSolicitacoes() {
    return request('/servicos/minhas');
  }

  /** @param {number} solicitacaoId @returns {Promise<Object>} SolicitacaoResponse (status=ACEITO) */
  function aceitarSolicitacao(solicitacaoId) {
    return request(`/servicos/${solicitacaoId}/aceitar`, { method: 'PUT' });
  }

  /** @param {number} solicitacaoId @returns {Promise<Object>} SolicitacaoResponse (status=RECUSADO) */
  function recusarSolicitacao(solicitacaoId) {
    return request(`/servicos/${solicitacaoId}/recusar`, { method: 'PUT' });
  }

  /** @param {number} solicitacaoId @returns {Promise<Object>} SolicitacaoResponse (status=CANCELADO) */
  function cancelarSolicitacao(solicitacaoId) {
    return request(`/servicos/${solicitacaoId}/cancelar`, { method: 'PUT' });
  }

  /** @param {number} solicitacaoId @returns {Promise<Object>} SolicitacaoResponse (status=CONCLUIDO) */
  function concluirSolicitacao(solicitacaoId) {
    return request(`/servicos/${solicitacaoId}/concluir`, { method: 'PUT' });
  }

  /**
   * @param {number} solicitacaoId
   * @param {number} nota 1 a 5
   * @param {string} [comentario]
   * @returns {Promise<Object>} SolicitacaoResponse (status=AVALIADO)
   */
  function enviarAvaliacao(solicitacaoId, nota, comentario) {
    return request(`/servicos/${solicitacaoId}/avaliar`, { method: 'PUT', body: { nota, comentario } });
  }

  // --- Pagamento (US-06) ---

  /**
   * @param {number} solicitacaoId
   * @param {string} metodoPagamento identificador do método (mock — sem PSP real)
   * @param {boolean} [simularFalha]
   * @returns {Promise<Object>} PagamentoResponse (status=RETIDO)
   */
  function criarPagamento(solicitacaoId, metodoPagamento, simularFalha = false) {
    return request(`/servicos/${solicitacaoId}/pagamento`, {
      method: 'POST',
      body: { metodoPagamento, simularFalha },
    });
  }

  // --- Saldo e saque Pix do prestador (US-08) ---

  /** @param {number} prestadorId @returns {Promise<{saldoDisponivel: number}>} */
  function obterSaldoPrestador(prestadorId) {
    return request(`/prestadores/${prestadorId}/saldo`);
  }

  /**
   * @param {number} prestadorId
   * @param {number} valor
   * @returns {Promise<{id: number, valor: number, status: string, saldoRestante: number}>}
   */
  function solicitarSaque(prestadorId, valor) {
    return request(`/prestadores/${prestadorId}/saques`, { method: 'POST', body: { valor } });
  }

  // --- Painel administrativo (RN01, RN04) ---

  /** @returns {Promise<{totalServicos: number, totalClientes: number, totalPrestadores: number}>} */
  function obterDashboard() {
    return request('/dashboard');
  }

  /** @returns {Promise<{totalServicos: number, agendados: number, cancelados: number, concluidos: number}>} */
  function obterRelatorio() {
    return request('/relatorio');
  }

  /**
   * @returns {Promise<Object[]>} lista de PrestadorResponse (`idPrestador`, `nome`, `especialidade`,
   * `notaMedia`, `cidade`, `email`, `statusKyc`) com `statusKyc !== 'APROVADO'`
   */
  function listarPrestadoresPendentes() {
    return request('/admin/prestadores/pendentes');
  }

  /** @param {number} prestadorId @returns {Promise<Object>} PrestadorResponse com statusKyc='APROVADO' */
  function aprovarKycPrestador(prestadorId) {
    return request(`/admin/prestadores/${prestadorId}/kyc/aprovar`, { method: 'PUT' });
  }

  /**
   * @param {number} prestadorId
   * @param {string} [motivo] motivo da rejeição, opcional
   * @returns {Promise<Object>} PrestadorResponse com statusKyc='REJEITADO'
   */
  function rejeitarKycPrestador(prestadorId, motivo) {
    return request(`/admin/prestadores/${prestadorId}/kyc/rejeitar`, {
      method: 'PUT',
      body: motivo ? { motivo } : undefined,
    });
  }

  /**
   * Busca um documento de KYC autenticado e devolve uma URL de objeto local (`blob:`) pronta para
   * abrir em uma nova aba — o endpoint exige o header `Authorization`, então não pode ser um link
   * `<a href>` comum.
   *
   * @param {number} prestadorId
   * @param {'identidade'|'pix'} tipo
   * @returns {Promise<string>} URL de objeto válida apenas nesta sessão do navegador
   * @throws {ApiError} quando a resposta não é 2xx
   */
  async function obterUrlDocumentoKyc(prestadorId, tipo) {
    const sessao = getSessaoAtual();
    const headers = {};
    if (sessao && sessao.token) headers['Authorization'] = `Bearer ${sessao.token}`;

    const resposta = await fetch(`${BASE_URL}/admin/prestadores/${prestadorId}/documentos/${tipo}`, { headers });

    if (!resposta.ok) {
      let mensagem = 'Não foi possível carregar o documento.';
      try {
        const corpo = await resposta.json();
        mensagem = corpo.message || mensagem;
      } catch {
        // resposta sem corpo JSON (ex.: 404 puro) — mantém a mensagem padrão
      }
      throw new ApiError(resposta.status, mensagem, {});
    }

    const blob = await resposta.blob();
    return URL.createObjectURL(blob);
  }

  /** @returns {Promise<Object[]>} lista de ParametroNegocioResponse (`chave`, `valor`, `descricao`) */
  function listarParametros() {
    return request('/admin/parametros');
  }

  /**
   * @param {string} chave
   * @param {number} valor
   * @returns {Promise<Object>} ParametroNegocioResponse atualizado
   */
  function atualizarParametro(chave, valor) {
    return request(`/admin/parametros/${chave}`, { method: 'PUT', body: { valor } });
  }

  return {
    ApiError,
    getSessaoAtual,
    getSessaoValida,
    salvarSessao,
    logout,
    login,
    registrarCliente,
    registrarPrestador,
    obterPrestador,
    enviarDocumentosKyc,
    criarServico,
    listarMeusServicos,
    atualizarServico,
    alternarServicoAtivo,
    excluirServico,
    buscarServicos,
    criarSolicitacao,
    listarSolicitacoes,
    aceitarSolicitacao,
    recusarSolicitacao,
    cancelarSolicitacao,
    concluirSolicitacao,
    enviarAvaliacao,
    criarPagamento,
    obterSaldoPrestador,
    solicitarSaque,
    obterDashboard,
    obterRelatorio,
    listarPrestadoresPendentes,
    aprovarKycPrestador,
    rejeitarKycPrestador,
    obterUrlDocumentoKyc,
    listarParametros,
    atualizarParametro,
  };
})();
