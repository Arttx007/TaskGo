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
   * @param {string} [method] verbo HTTP; `POST` por padrão
   * @returns {Promise<any>} corpo da resposta já convertido de JSON
   * @throws {ApiError} quando a resposta não é 2xx
   */
  async function requestMultipart(path, formData, method = 'POST') {
    const headers = {};
    const sessao = getSessaoAtual();
    if (sessao && sessao.token) {
      headers['Authorization'] = `Bearer ${sessao.token}`;
    }

    const resposta = await fetch(BASE_URL + path, {
      method,
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
   * Busca serviços por categoria, com filtros opcionais aplicados pelo backend.
   *
   * Monta a query string a partir das chaves do objeto recebido, descartando valores vazios — por
   * isso aceita parâmetros novos sem alteração de código.
   *
   * @param {{categoria: string, lat?: number, lon?: number, raioKm?: number, cidade?: string,
   *          notaMinima?: number, precoMin?: number, precoMax?: number, apenasSemAvaliacao?: boolean}} filtro
   *        `notaMinima` descarta prestador sem nota; `precoMin`/`precoMax` são inclusivos;
   *        `apenasSemAvaliacao` devolve só prestador ainda não avaliado e **não pode** vir junto de
   *        `notaMinima` (o backend responde 400 `VALIDACAO`).
   * @returns {Promise<{resultados: Object[], mensagem: string|null}>} resultados ordenados por
   *          proximidade, cada um com `latitude`/`longitude` aproximadas quando houver coordenadas
   */
  function buscarServicos(filtro) {
    const params = new URLSearchParams();
    Object.entries(filtro).forEach(([chave, valor]) => {
      if (valor !== undefined && valor !== null && valor !== '') params.set(chave, valor);
    });
    return request(`/servicos-ofertados/buscar?${params.toString()}`, { auth: false });
  }

  /**
   * Categorias que têm ao menos um serviço disponível ao público, com a contagem de cada uma.
   *
   * Como `categoria` é texto livre no backend, esta lista reflete o que os prestadores cadastraram —
   * é a fonte de verdade sobre disponibilidade, não o grid curado das páginas.
   *
   * @returns {Promise<Array<{categoria: string, totalServicos: number}>>} da mais ofertada para a
   *          menos; lista vazia quando não há oferta alguma
   */
  function listarCategorias() {
    return request('/servicos-ofertados/categorias', { auth: false });
  }

  /**
   * Faixa de preço praticada numa categoria, apurada sobre os serviços publicados.
   *
   * Não é predição: são os preços que os prestadores cobram. Com amostra menor que três, o backend
   * devolve os valores nulos e apenas `mensagem` — nesse caso exiba a mensagem, não uma faixa.
   *
   * @param {string} categoria categoria a consultar
   * @returns {Promise<{categoria: string, minimo: number|null, mediana: number|null,
   *          maximo: number|null, amostra: number, mensagem: string|null}>}
   */
  function obterEstimativa(categoria) {
    return request(`/servicos-ofertados/estimativa?categoria=${encodeURIComponent(categoria)}`, { auth: false });
  }

  /**
   * Avaliações reais mais recentes, para a prova social das páginas públicas.
   *
   * Identifica quem avaliou apenas pelo primeiro nome. Lista vazia significa que não há depoimento a
   * exibir — esconda a seção em vez de mostrar moldura vazia.
   *
   * @param {number} [limite] quantidade desejada; acima do teto do backend é truncada, nunca recusada
   * @returns {Promise<Array<{nota: number, comentario: string, clientePrimeiroNome: string,
   *          categoria: string|null, cidade: string|null, data: string}>>}
   */
  function listarAvaliacoesRecentes(limite) {
    const query = limite ? `?limite=${encodeURIComponent(limite)}` : '';
    return request(`/avaliacoes/recentes${query}`, { auth: false });
  }

  // --- Ciclo de vida da solicitação (US-04, US-05, US-07, US-09, US-10) ---

  /**
   * @param {number} servicoOfertadoId
   * @param {number} [enderecoClienteId] endereço do cliente onde o atendimento ocorrerá; quando
   *        omitido a solicitação nasce sem endereço e a tela de acompanhamento não exibe mapa
   * @returns {Promise<Object>} SolicitacaoResponse (status=SOLICITADO)
   */
  function criarSolicitacao(servicoOfertadoId, enderecoClienteId) {
    const body = { servicoOfertadoId };
    if (enderecoClienteId !== undefined && enderecoClienteId !== null) {
      body.enderecoClienteId = enderecoClienteId;
    }
    return request('/servicos', { method: 'POST', body });
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

  // --- Conta e endereços do cliente ---

  /**
   * Perfil do cliente autenticado. A rota se resolve pelo token, não por id na URL — o cliente só
   * alcança a própria conta.
   *
   * @returns {Promise<{idCliente: number, nome: string, email: string, telefone: string|null,
   *          idade: number|null, cidade: string|null, tipoCliente: string|null, fotoUrl: string|null}>}
   * @throws {ApiError} 401 sem token válido
   */
  function obterMeuPerfilCliente() {
    return request('/clientes/me');
  }

  /**
   * @param {{nome: string, email: string, telefone?: string, idade?: number, cidade?: string}} dados
   *        campos do perfil; `nome` e `email` são obrigatórios
   * @returns {Promise<Object>} ClientePerfilResponse já atualizado
   * @throws {ApiError} 400 `VALIDACAO` com `fieldErrors`, ou 409 quando o e-mail já pertence a outra conta
   */
  function atualizarMeuPerfilCliente(dados) {
    return request('/clientes/me', { method: 'PUT', body: dados });
  }

  /**
   * Substitui a foto de perfil do cliente. Enviar uma segunda foto descarta a referência à primeira.
   *
   * @param {File} foto imagem nos tipos aceitos pelo backend (mesma validação do KYC)
   * @returns {Promise<Object>} ClientePerfilResponse com `fotoUrl` novo
   * @throws {ApiError} 400 `ARQUIVO_INVALIDO` para tipo ou tamanho recusado
   */
  function atualizarMinhaFotoCliente(foto) {
    const formData = new FormData();
    formData.append('foto', foto);
    return requestMultipart('/clientes/me/foto', formData, 'PUT');
  }

  /**
   * Desativa a própria conta (exclusão lógica — o histórico é preservado). Depois disto o login
   * passa a ser recusado com 401.
   *
   * @returns {Promise<void>}
   * @throws {ApiError} 409 `ESTADO_INVALIDO` com solicitação em SOLICITADO, ACEITO ou EM_ANDAMENTO
   */
  function desativarMinhaConta() {
    return request('/clientes/me', { method: 'DELETE' });
  }

  /**
   * @returns {Promise<Array<{id: number, apelido: string, cep: string, rua: string, numero: string,
   *          complemento: string|null, bairro: string|null, cidade: string, uf: string,
   *          latitude: number|null, longitude: number|null, padrao: boolean}>>} endereços ativos do
   *          cliente; exatamente um tem `padrao: true` quando a lista não está vazia
   */
  function listarMeusEnderecos() {
    return request('/clientes/me/enderecos');
  }

  /**
   * @param {{apelido: string, cep: string, rua: string, numero: string, complemento?: string,
   *          bairro?: string, cidade: string, uf: string, latitude?: number, longitude?: number,
   *          padrao?: boolean}} dados o primeiro endereço do cliente nasce padrão mesmo sem `padrao`
   * @returns {Promise<Object>} EnderecoClienteResponse
   * @throws {ApiError} 400 `VALIDACAO` (CEP e UF são validados por formato)
   */
  function criarMeuEndereco(dados) {
    return request('/clientes/me/enderecos', { method: 'POST', body: dados });
  }

  /**
   * @param {number} enderecoId
   * @param {Object} dados mesmos campos de `criarMeuEndereco`; marcar `padrao: true` desmarca o anterior
   * @returns {Promise<Object>} EnderecoClienteResponse atualizado
   * @throws {ApiError} 403 `ACESSO_NEGADO` para endereço de outra conta, 404 se não existir
   */
  function atualizarMeuEndereco(enderecoId, dados) {
    return request(`/clientes/me/enderecos/${enderecoId}`, { method: 'PUT', body: dados });
  }

  /**
   * Remoção lógica: o endereço sai da lista, mas as solicitações que já o referenciam continuam
   * resolvendo por ele.
   *
   * @param {number} enderecoId
   * @returns {Promise<void>}
   * @throws {ApiError} 403 `ACESSO_NEGADO` para endereço de outra conta, 404 se não existir
   */
  function removerMeuEndereco(enderecoId) {
    return request(`/clientes/me/enderecos/${enderecoId}`, { method: 'DELETE' });
  }

  // --- Extrato, favoritos, catálogo de um prestador e avisos do cliente ---

  /**
   * Extrato de pagamentos do cliente autenticado, do mais recente para o mais antigo.
   *
   * `valorTaxa` é a taxa **apurada no momento do pagamento**, não recalculada — alterar
   * `taxa.fixa` em `/admin/parametros` não muda lançamento já existente. `valorEstornado` e
   * `valorTaxaCancelamento` vêm preenchidos apenas em estorno parcial (`ESTORNADO_PARCIAL`).
   *
   * @returns {Promise<Array<{solicitacaoId: number, categoria: string, prestadorNome: string,
   *          valorBruto: number, valorTaxa: number, status: string, metodoPagamento: string,
   *          criadoEm: string, valorEstornado: number|null, valorTaxaCancelamento: number|null}>>}
   */
  function listarMeuExtrato() {
    return request('/clientes/me/pagamentos');
  }

  /**
   * Prestadores favoritados pelo cliente. Prestador que perdeu a aprovação **continua na lista**,
   * com `disponivel: false` e `servicosAtivos: 0` — a tela o sinaliza em vez de escondê-lo.
   *
   * @returns {Promise<Array<{prestadorId: number, nome: string, especialidade: string|null,
   *          cidade: string|null, notaMedia: number|null, servicosAtivos: number,
   *          disponivel: boolean, favoritadoEm: string}>>}
   */
  function listarMeusFavoritos() {
    return request('/clientes/me/favoritos');
  }

  /**
   * @param {number} prestadorId
   * @returns {Promise<Object>} FavoritoResponse do favorito criado
   * @throws {ApiError} 409 `ESTADO_INVALIDO` se já era favorito, 404 se o prestador não existe
   */
  function marcarFavorito(prestadorId) {
    return request('/clientes/me/favoritos', { method: 'POST', body: { prestadorId } });
  }

  /**
   * @param {number} prestadorId
   * @returns {Promise<void>}
   * @throws {ApiError} 404 `RECURSO_NAO_ENCONTRADO` se o prestador não estava favoritado
   */
  function removerFavorito(prestadorId) {
    return request(`/clientes/me/favoritos/${prestadorId}`, { method: 'DELETE' });
  }

  /**
   * Serviços ativos de um prestador. Rota **autenticada** (ao contrário de `/buscar`).
   *
   * Devolve lista vazia quando o prestador não está `APROVADO` — RN04 preserva o estado de
   * verificação, então "vazio" não distingue prestador sem oferta de prestador não aprovado.
   *
   * @param {number} prestadorId
   * @returns {Promise<Object[]>} lista de ServicoOfertadoResponse; nunca traz contato do prestador
   * @throws {ApiError} 401 sem token
   */
  function listarServicosDoPrestador(prestadorId) {
    return request(`/prestadores/${prestadorId}/servicos-ofertados`);
  }

  /**
   * Avisos de atividade do cliente, apurados do estado atual (não há tabela nem marcação de
   * leitura): solicitação aceita e não paga, pagamento retido, concluído sem avaliação, recusada ou
   * cancelada, e mensagens não lidas. O aviso desaparece quando o fato que o originou é resolvido.
   *
   * @returns {Promise<Array<{tipo: string, texto: string, solicitacaoId: number|null, momento: string}>>}
   *          do mais recente para o mais antigo; lista vazia quando não há pendência
   */
  function listarMinhasNotificacoes() {
    return request('/clientes/me/notificacoes');
  }

  // --- Detalhe, início de atendimento e mensagens da solicitação ---

  /**
   * Detalhe de uma solicitação, restrito às duas partes envolvidas.
   *
   * `pinConfirmacao` vem preenchido **apenas para o cliente** — é o código que ele informa ao
   * prestador presente. Os quatro momentos (`criadoEm`, `aceitoEm`, `iniciadoEm`, `concluidoEm`)
   * vêm nulos enquanto a etapa não ocorreu. `taxaCancelamentoPrevista` diz quanto seria retido se o
   * cliente cancelasse agora (zero dentro da carência).
   *
   * @param {number} solicitacaoId
   * @returns {Promise<Object>} SolicitacaoResponse completo
   * @throws {ApiError} 403 `ACESSO_NEGADO` para quem não participa, 404 se não existir
   */
  function obterSolicitacao(solicitacaoId) {
    return request(`/servicos/${solicitacaoId}`);
  }

  /**
   * Inicia o atendimento (ACEITO → EM_ANDAMENTO). Só o prestador chama, com o pagamento já retido
   * em custódia e o código informado pelo cliente presente. Código errado não altera nada.
   *
   * @param {number} solicitacaoId
   * @param {string} pin código de quatro dígitos exibido ao cliente
   * @returns {Promise<Object>} SolicitacaoResponse (status=EM_ANDAMENTO)
   * @throws {ApiError} 403 `ACESSO_NEGADO` para código errado, 409 `ESTADO_INVALIDO` sem pagamento
   *         retido ou fora de `ACEITO`
   */
  function iniciarAtendimento(solicitacaoId, pin) {
    return request(`/servicos/${solicitacaoId}/iniciar`, { method: 'PUT', body: { pin } });
  }

  /**
   * Conversa de uma solicitação, da mensagem mais antiga para a mais recente.
   *
   * @param {number} solicitacaoId
   * @returns {Promise<Array<{id: number, conteudo: string, remetenteTipo: string,
   *          remetenteNome: string, criadoEm: string, lida: boolean}>>}
   * @throws {ApiError} 403 `ACESSO_NEGADO` para quem não é o cliente nem o prestador da solicitação
   */
  function listarMensagens(solicitacaoId) {
    return request(`/servicos/${solicitacaoId}/mensagens`);
  }

  /**
   * @param {number} solicitacaoId
   * @param {string} conteudo texto não vazio, dentro do limite do backend
   * @returns {Promise<Object>} MensagemResponse da mensagem criada (nasce não lida)
   * @throws {ApiError} 400 `VALIDACAO` para conteúdo vazio ou acima do limite, 409
   *         `ESTADO_INVALIDO` em solicitação já encerrada (RECUSADO, CANCELADO ou AVALIADO)
   */
  function enviarMensagem(solicitacaoId, conteudo) {
    return request(`/servicos/${solicitacaoId}/mensagens`, { method: 'POST', body: { conteudo } });
  }

  /**
   * Marca como lidas as mensagens escritas pela **outra** parte — as próprias permanecem como estão.
   *
   * @param {number} solicitacaoId
   * @returns {Promise<void>}
   * @throws {ApiError} 403 `ACESSO_NEGADO` para quem não participa da solicitação
   */
  function marcarMensagensLidas(solicitacaoId) {
    return request(`/servicos/${solicitacaoId}/mensagens/lidas`, { method: 'PUT' });
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
    listarCategorias,
    obterEstimativa,
    listarAvaliacoesRecentes,
    criarSolicitacao,
    listarSolicitacoes,
    aceitarSolicitacao,
    recusarSolicitacao,
    cancelarSolicitacao,
    concluirSolicitacao,
    enviarAvaliacao,
    criarPagamento,
    obterMeuPerfilCliente,
    atualizarMeuPerfilCliente,
    atualizarMinhaFotoCliente,
    desativarMinhaConta,
    listarMeusEnderecos,
    criarMeuEndereco,
    atualizarMeuEndereco,
    removerMeuEndereco,
    listarMeuExtrato,
    listarMeusFavoritos,
    marcarFavorito,
    removerFavorito,
    listarServicosDoPrestador,
    listarMinhasNotificacoes,
    obterSolicitacao,
    iniciarAtendimento,
    listarMensagens,
    enviarMensagem,
    marcarMensagensLidas,
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
