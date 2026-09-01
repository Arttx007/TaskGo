/**
 * Conversa entre cliente e prestador de uma solicitação, usada pelos dois painéis.
 *
 * Vive em arquivo próprio porque as duas pontas da conversa são telas diferentes e a implementação é
 * a mesma: quem abre o modal é sempre uma das duas partes da solicitação, e o backend recusa
 * qualquer terceiro com 403. O módulo cria o próprio modal em tempo de execução, então nenhuma das
 * páginas precisa carregar a marcação da conversa.
 *
 * A atualização é por consulta periódica enquanto o modal está aberto — não há conexão persistente,
 * e o temporizador é encerrado ao fechar.
 */
const ChatServico = (() => {
  /** Intervalo entre consultas da conversa, em milissegundos. */
  const INTERVALO_CONSULTA_MS = 5000;

  /** @type {number|null} id da solicitação cuja conversa está aberta */
  let solicitacaoAbertaId = null;

  /** @type {number|null} handle do `setInterval` da consulta periódica */
  let temporizador = null;

  /** @type {number|null} id da mensagem mais recente já renderizada */
  let ultimaMensagemId = null;

  /** @type {HTMLElement|null} modal criado sob demanda */
  let modal = null;

  /**
   * Avisa o usuário. Usa o `showToast` da página quando existe, para manter a aparência de cada painel.
   *
   * @param {string} mensagem texto a exibir
   * @param {'success'|'error'} [tipo] natureza do aviso
   * @returns {void}
   */
  function avisar(mensagem, tipo = 'success') {
    if (typeof showToast === 'function') {
      showToast(mensagem, tipo);
      return;
    }
    console.warn('[ChatServico]', mensagem);
  }

  /**
   * @param {*} erro erro capturado
   * @returns {string} mensagem legível
   */
  function mensagemDeErro(erro) {
    return erro instanceof TaskGoAPI.ApiError ? erro.message : 'Não foi possível carregar a conversa.';
  }

  /**
   * @param {string|null} iso data-hora ISO
   * @returns {string} hora em pt-BR, ou string vazia
   */
  function formatarHora(iso) {
    if (!iso) return '';
    return new Date(iso).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' });
  }

  /**
   * Cria o modal da conversa uma única vez e o devolve.
   *
   * @returns {HTMLElement} elemento do modal, já inserido no documento
   */
  function garantirModal() {
    if (modal) return modal;

    modal = document.createElement('div');
    modal.className = 'modal-overlay';
    modal.id = 'modalChatServico';
    modal.innerHTML = `
      <div class="modal-content" style="padding: 0; max-width: 420px; overflow: hidden; display: flex; flex-direction: column; height: 540px;">
        <div style="padding: 18px 20px; border-bottom: 1px solid var(--border-color); display: flex; justify-content: space-between; align-items: center; gap: 12px;">
          <div>
            <h4 style="color: white; font-size: 15px; margin: 0 0 2px;" id="chatServicoTitulo">Conversa</h4>
            <span style="color: var(--text-gray); font-size: 12px;" id="chatServicoSubtitulo"></span>
          </div>
          <button type="button" style="background: none; border: none; color: var(--text-gray); font-size: 18px; cursor: pointer;" id="chatServicoFechar"><i class="fas fa-times"></i></button>
        </div>

        <div style="flex: 1; overflow-y: auto; padding: 20px; display: flex; flex-direction: column; gap: 12px;" id="chatServicoHistorico"></div>

        <div style="padding: 16px 20px; border-top: 1px solid var(--border-color); display: flex; gap: 10px;">
          <input type="text" id="chatServicoInput" placeholder="Escreva sua mensagem..." maxlength="1000"
                 style="flex: 1; background: var(--bg-dark); border: 1px solid var(--border-color); border-radius: 10px; padding: 12px 14px; color: white; font-size: 13px; font-family: inherit;">
          <button type="button" id="chatServicoEnviar"
                  style="background: var(--primary-blue); border: none; border-radius: 10px; width: 44px; color: white; cursor: pointer; font-size: 15px;"><i class="fas fa-paper-plane"></i></button>
        </div>
      </div>
    `;

    document.body.appendChild(modal);

    modal.querySelector('#chatServicoFechar').addEventListener('click', fechar);
    modal.querySelector('#chatServicoEnviar').addEventListener('click', enviar);
    modal.querySelector('#chatServicoInput').addEventListener('keydown', (evento) => {
      if (evento.key === 'Enter') {
        evento.preventDefault();
        enviar();
      }
    });
    modal.addEventListener('click', (evento) => {
      if (evento.target === modal) fechar();
    });

    return modal;
  }

  /**
   * Abre a conversa de uma solicitação, marca as mensagens da outra parte como lidas e passa a
   * consultar periodicamente enquanto o modal estiver aberto.
   *
   * @param {{id: number, clienteNome?: string, prestadorNome?: string, categoria?: string}} solicitacao
   *        solicitação cuja conversa deve ser aberta
   * @returns {Promise<void>}
   */
  async function abrir(solicitacao) {
    garantirModal();

    solicitacaoAbertaId = solicitacao.id;
    ultimaMensagemId = null;

    const sessao = TaskGoAPI.getSessaoAtual();
    const souCliente = sessao && sessao.usuario && sessao.usuario.tipo === 'CLIENTE';
    const outraParte = souCliente ? solicitacao.prestadorNome : solicitacao.clienteNome;

    modal.querySelector('#chatServicoTitulo').textContent = outraParte || 'Conversa';
    modal.querySelector('#chatServicoSubtitulo').textContent = solicitacao.categoria || `Solicitação #${solicitacao.id}`;
    modal.querySelector('#chatServicoHistorico').textContent = 'Carregando conversa...';
    modal.querySelector('#chatServicoInput').value = '';
    modal.style.display = 'flex';

    await atualizar();

    // Abrir a conversa é o ato de lê-la: a contagem de não lidas cai na próxima consulta de avisos.
    try {
      await TaskGoAPI.marcarMensagensLidas(solicitacaoAbertaId);
    } catch {
      // falhar em marcar leitura não deve impedir a conversa de funcionar
    }

    if (temporizador) clearInterval(temporizador);
    temporizador = setInterval(atualizar, INTERVALO_CONSULTA_MS);
  }

  /**
   * Fecha o modal e encerra a consulta periódica — sem isto as requisições continuariam em segundo
   * plano depois de o usuário sair da conversa.
   *
   * @returns {void}
   */
  function fechar() {
    if (temporizador) {
      clearInterval(temporizador);
      temporizador = null;
    }
    solicitacaoAbertaId = null;
    if (modal) modal.style.display = 'none';
  }

  /**
   * Consulta a conversa e a renderiza. Só redesenha quando há mensagem nova, para não interromper a
   * rolagem de quem está lendo.
   *
   * @returns {Promise<void>}
   */
  async function atualizar() {
    if (!solicitacaoAbertaId) return;

    try {
      const mensagens = (await TaskGoAPI.listarMensagens(solicitacaoAbertaId)) || [];
      const ultimoId = mensagens.length ? mensagens[mensagens.length - 1].id : null;
      if (ultimoId === ultimaMensagemId && mensagens.length) return;

      ultimaMensagemId = ultimoId;
      renderizar(mensagens);
    } catch (erro) {
      const historico = modal.querySelector('#chatServicoHistorico');
      historico.textContent = mensagemDeErro(erro);
      fechar();
      avisar(mensagemDeErro(erro), 'error');
    }
  }

  /**
   * Renderiza a conversa construindo nós de texto — o conteúdo é escrito pela outra parte, então
   * nunca é interpretado como HTML.
   *
   * @param {Array<{id: number, conteudo: string, remetenteTipo: string, remetenteNome: string,
   *          criadoEm: string, lida: boolean}>} mensagens conversa em ordem cronológica
   * @returns {void}
   */
  function renderizar(mensagens) {
    const historico = modal.querySelector('#chatServicoHistorico');
    historico.textContent = '';

    if (!mensagens.length) {
      const vazio = document.createElement('p');
      vazio.style.cssText = 'color: var(--text-gray); font-size: 13px; text-align: center; margin: auto;';
      vazio.textContent = 'Nenhuma mensagem ainda. Escreva a primeira.';
      historico.appendChild(vazio);
      return;
    }

    const sessao = TaskGoAPI.getSessaoAtual();
    const meuTipo = sessao && sessao.usuario ? sessao.usuario.tipo : null;

    mensagens.forEach((mensagem) => {
      const minha = mensagem.remetenteTipo === meuTipo;

      const wrapper = document.createElement('div');
      wrapper.style.cssText = `display: flex; flex-direction: column; align-items: ${minha ? 'flex-end' : 'flex-start'}; gap: 4px;`;

      const bolha = document.createElement('div');
      bolha.style.cssText = `max-width: 85%; padding: 10px 14px; font-size: 13px; line-height: 1.5; border-radius: ${
        minha ? '12px 0 12px 12px' : '0 12px 12px 12px'
      }; background: ${minha ? 'var(--primary-blue)' : 'var(--card-bg)'}; color: white; ${
        minha ? '' : 'border: 1px solid var(--border-color);'
      }`;
      bolha.textContent = mensagem.conteudo;

      const rodape = document.createElement('span');
      rodape.style.cssText = 'font-size: 10px; color: var(--text-muted);';
      rodape.textContent = `${mensagem.remetenteNome} • ${formatarHora(mensagem.criadoEm)}`;

      wrapper.appendChild(bolha);
      wrapper.appendChild(rodape);
      historico.appendChild(wrapper);
    });

    historico.scrollTop = historico.scrollHeight;
  }

  /**
   * Envia a mensagem digitada. O backend recusa solicitação já encerrada com 409.
   *
   * @returns {Promise<void>}
   */
  async function enviar() {
    if (!solicitacaoAbertaId) return;

    const input = modal.querySelector('#chatServicoInput');
    const conteudo = input.value.trim();
    if (!conteudo) return;

    const botao = modal.querySelector('#chatServicoEnviar');
    botao.disabled = true;

    try {
      await TaskGoAPI.enviarMensagem(solicitacaoAbertaId, conteudo);
      input.value = '';
      ultimaMensagemId = null;
      await atualizar();
    } catch (erro) {
      avisar(mensagemDeErro(erro), 'error');
    } finally {
      botao.disabled = false;
      input.focus();
    }
  }

  return { abrir, fechar };
})();
