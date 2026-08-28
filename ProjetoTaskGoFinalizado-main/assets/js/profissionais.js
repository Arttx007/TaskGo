let ultimaBusca = { categoria: '', lat: null, lon: null, cidade: '' };
let mapa = null;
let marcadores = [];

document.addEventListener('DOMContentLoaded', () => {
  // ==========================================
  // 1. MENU MOBILE
  // ==========================================
  const mobileToggleBtn = document.getElementById('navigation-mobile-toggle');
  const mobileOverlay = document.getElementById('navigation-mobile-overlay');
  const mobileCloseBtn = document.getElementById('navigation-mobile-close');

  if (mobileToggleBtn && mobileOverlay && mobileCloseBtn) {
    mobileToggleBtn.addEventListener('click', () => {
      mobileOverlay.classList.add('show-overlay');
      document.body.style.overflow = 'hidden';
    });

    mobileCloseBtn.addEventListener('click', () => {
      mobileOverlay.classList.remove('show-overlay');
      document.body.style.overflow = '';
    });

    const mobileLinks = mobileOverlay.querySelectorAll('a');
    mobileLinks.forEach((link) => {
      link.addEventListener('click', () => {
        mobileOverlay.classList.remove('show-overlay');
        document.body.style.overflow = '';
      });
    });
  }

  // ==========================================
  // 2. RECUPERAR BUSCA DA HOME (pela URL)
  // ==========================================
  const urlParams = new URLSearchParams(window.location.search);
  const serviceParam = urlParams.get('service');
  const radiusParam = urlParams.get('radius');
  const servicoInput = document.getElementById('thq_servico_zv-1');
  const distanceSlider = document.getElementById('thq_textinput_U--D');

  // O raio escolhido na home chega em `radius` e antes era descartado.
  if (radiusParam && distanceSlider) {
    distanceSlider.value = radiusParam;
  }

  if (serviceParam && servicoInput) {
    servicoInput.value = serviceParam.charAt(0).toUpperCase() + serviceParam.slice(1);
    buscarComGeolocalizacao(serviceParam);
  }

  // ==========================================
  // 3. SLIDER DE RAIO — re-busca real (US-03)
  // ==========================================
  if (distanceSlider) {
    const currentValDisplay = document.createElement('div');
    currentValDisplay.style.fontWeight = 'bold';
    currentValDisplay.style.color = 'var(--color-primary, #4CAF50)';
    currentValDisplay.style.marginBottom = '8px';
    currentValDisplay.innerText = `Raio atual: ${distanceSlider.value} km`;
    distanceSlider.parentNode.insertBefore(currentValDisplay, distanceSlider);

    let debounceId = null;
    distanceSlider.addEventListener('input', (e) => {
      currentValDisplay.innerText = `Raio atual: ${e.target.value} km`;
      if (!ultimaBusca.categoria) return;

      clearTimeout(debounceId);
      debounceId = setTimeout(() => executarBusca({ ...ultimaBusca, raioKm: Number(e.target.value) }), 400);
    });
  }

  // ==========================================
  // 4. BUSCA REAL (US-03)
  // ==========================================
  const searchForm = document.querySelector('.busca-rapida__form');
  if (searchForm) {
    searchForm.addEventListener('submit', (e) => {
      e.preventDefault();

      const inputs = searchForm.querySelectorAll('input[required]');
      let valido = true;
      inputs.forEach((input) => {
        if (!validarCampo(input)) valido = false;
      });
      if (!valido) return;

      const categoria = document.getElementById('thq_servico_zv-1').value.trim();
      buscarComGeolocalizacao(categoria);
    });
  }

  // ==========================================
  // 5. PAINEL "REFINE SUA BUSCA" — filtros de servidor
  // ==========================================
  // Nota mínima e faixa de preço são parâmetros da API: escolher um deles refaz a busca, e a
  // resposta já vem sem os resultados excluídos. Nada é apenas ocultado na tela.
  const botoesNota = document.querySelectorAll('.btn-star');
  botoesNota.forEach((botao) => {
    botao.addEventListener('click', () => {
      botoesNota.forEach((b) => b.classList.remove('active'));
      botao.classList.add('active');
      aplicarFiltros();
    });
  });

  const seletorPreco = document.getElementById('thq_select_Vxc3');
  if (seletorPreco) {
    seletorPreco.addEventListener('change', aplicarFiltros);
  }

  const painelFiltros = document.querySelector('form.filter-panel');
  if (painelFiltros) {
    // Sem este handler o botão "Aplicar Filtros" recarregava a página e apagava os resultados.
    painelFiltros.addEventListener('submit', (e) => {
      e.preventDefault();
      aplicarFiltros();
    });
  }

  // ==========================================
  // 6. ALTERNAR MAPA / LISTA
  // ==========================================
  const botaoAlternar = document.getElementById('alternarMapaLista');
  if (botaoAlternar) {
    botaoAlternar.addEventListener('click', () => {
      const secaoMapa = document.getElementById('secaoMapa');
      const secaoLista = document.querySelector('.lista-profissionais');
      if (!secaoMapa || !secaoLista) return;

      const mostrandoMapa = !secaoMapa.hidden;
      secaoMapa.hidden = mostrandoMapa;
      secaoLista.hidden = !mostrandoMapa;
      botaoAlternar.textContent = mostrandoMapa ? 'Alternar para Mapa' : 'Alternar para Lista';

      if (!secaoMapa.hidden && mapa) mapa.invalidateSize();
    });
  }

  // ==========================================
  // 7. ANIMAÇÕES DE SCROLL
  // ==========================================
  const observerOptions = { threshold: 0.01, rootMargin: '0px 0px -20px 0px' };
  const scrollObserver = new IntersectionObserver((entries) => {
    entries.forEach((entry) => {
      if (entry.isIntersecting) {
        entry.target.style.opacity = '1';
        entry.target.style.transform = 'translateY(0)';
        scrollObserver.unobserve(entry.target);
      }
    });
  }, observerOptions);

  const elementsToAnimate = document.querySelectorAll(
    '.lista-profissionais__header, .mapa-interativo__container, .filtros-avancados__container, .bento-item, .cta-final__container'
  );
  elementsToAnimate.forEach((el) => {
    el.style.opacity = '0';
    el.style.transform = 'translateY(40px)';
    el.style.transition = 'all 0.6s ease-out';
    scrollObserver.observe(el);
  });

  carregarDepoimentos();
});

/**
 * Refaz a busca corrente com os filtros escolhidos no painel.
 *
 * @returns {void}
 */
function aplicarFiltros() {
  if (!ultimaBusca.categoria) return;
  executarBusca({ ...ultimaBusca, ...lerFiltrosDoPainel() });
}

/**
 * Lê nota mínima, faixa de preço e raio do painel de refino.
 *
 * @returns {{notaMinima: number|null, precoMin: number|null, precoMax: number|null, raioKm: number|null}}
 */
function lerFiltrosDoPainel() {
  const ativo = document.querySelector('.btn-star.active');
  const notaMinima = ativo && ativo.dataset.nota ? Number(ativo.dataset.nota) : null;

  const seletorPreco = document.getElementById('thq_select_Vxc3');
  let precoMin = null;
  let precoMax = null;
  if (seletorPreco && seletorPreco.value) {
    const [min, max] = seletorPreco.value.split('-');
    precoMin = min ? Number(min) : null;
    precoMax = max ? Number(max) : null;
  }

  const slider = document.getElementById('thq_textinput_U--D');
  const raioKm = slider ? Number(slider.value) : null;

  return { notaMinima, precoMin, precoMax, raioKm };
}

/**
 * Tenta obter a localização do navegador; sem permissão, cai no fallback por endereço/cidade
 * digitado (US-03, cenário de exceção).
 *
 * @param {string} categoria termo buscado no campo "Que serviço você precisa?"
 * @returns {void}
 */
function buscarComGeolocalizacao(categoria) {
  const enderecoDigitado = (document.getElementById('thq_localizacao_ehOt') || {}).value || '';
  const filtros = lerFiltrosDoPainel();

  if (!navigator.geolocation) {
    executarBusca({ categoria, lat: null, lon: null, cidade: enderecoDigitado, ...filtros });
    return;
  }

  navigator.geolocation.getCurrentPosition(
    (posicao) => {
      executarBusca({
        categoria,
        lat: posicao.coords.latitude,
        lon: posicao.coords.longitude,
        cidade: '',
        ...filtros,
      });
    },
    () => {
      executarBusca({ categoria, lat: null, lon: null, cidade: enderecoDigitado, ...filtros });
    },
    { timeout: 8000 }
  );
}

/**
 * Executa a busca por serviços e renderiza os resultados (US-03).
 *
 * Quando há nota mínima ativa, dispara também a busca dos profissionais ainda não avaliados, que o
 * filtro necessariamente excluiu — sem isso, prestador novo ficaria invisível.
 *
 * @param {{categoria: string, lat: number|null, lon: number|null, cidade: string, raioKm?: number,
 *          notaMinima?: number|null, precoMin?: number|null, precoMax?: number|null}} filtro
 * @returns {Promise<void>}
 */
async function executarBusca(filtro) {
  ultimaBusca = filtro;
  const container = document.getElementById('resultadosContainer');
  container.innerHTML = '<p class="section-content" style="grid-column: 1 / -1;">Buscando profissionais...</p>';

  try {
    const resultado = await TaskGoAPI.buscarServicos(filtro);
    renderizarResultados(resultado);
    atualizarMapa(resultado.resultados || []);
    await carregarProfissionaisNovos(filtro);
  } catch (erro) {
    container.innerHTML = `<p class="section-content" style="grid-column: 1 / -1;">Não foi possível buscar agora. ${erro instanceof TaskGoAPI.ApiError ? erro.message : ''}</p>`;
    atualizarMapa([]);
  }
}

/**
 * @param {{resultados: Object[], mensagem: string|null}} resultado resposta da busca
 * @returns {void}
 */
function renderizarResultados(resultado) {
  const container = document.getElementById('resultadosContainer');

  if (!resultado.resultados || resultado.resultados.length === 0) {
    container.innerHTML = `<p class="section-content" style="grid-column: 1 / -1;">${resultado.mensagem || 'Nenhum resultado encontrado nesta região.'}</p>`;
    return;
  }

  container.innerHTML = '';
  resultado.resultados.forEach((item) => container.appendChild(criarProfCard(item)));
}

/**
 * Renderiza a vitrine "Novos na sua região".
 *
 * Só é exibida quando há nota mínima ativa, porque é justamente esse filtro que exclui quem ainda
 * não tem nota. Sem filtro de nota, esses prestadores já estão na lista principal e exibi-los aqui
 * os mostraria duas vezes.
 *
 * @param {Object} filtro filtro da busca corrente
 * @returns {Promise<void>}
 */
async function carregarProfissionaisNovos(filtro) {
  const secao = document.getElementById('secaoNovos');
  const container = document.getElementById('novosContainer');
  if (!secao || !container) return;

  if (!filtro.notaMinima) {
    secao.hidden = true;
    return;
  }

  // A nota mínima é omitida de propósito: combinar os dois critérios é recusado pelo backend.
  const { notaMinima, ...semNota } = filtro;

  try {
    const resultado = await TaskGoAPI.buscarServicos({ ...semNota, apenasSemAvaliacao: true });
    const novos = resultado.resultados || [];

    if (!novos.length) {
      secao.hidden = true;
      return;
    }

    container.innerHTML = '';
    novos.forEach((item) => container.appendChild(criarProfCard(item, { semNota: true })));
    secao.hidden = false;
  } catch {
    secao.hidden = true;
  }
}

/**
 * @param {Object} item item da busca (BuscaServicoResponse)
 * @param {{semNota?: boolean}} [opcoes] `semNota` omite nota e declara ausência de avaliação
 * @returns {HTMLElement} card do profissional
 */
function criarProfCard(item, opcoes = {}) {
  const article = document.createElement('article');
  article.className = 'prof-card';

  const distancia = item.distanciaKm != null ? `${item.distanciaKm.toFixed(1)} km` : '';
  const blocoNota = opcoes.semNota
    ? '<div class="prof-card__rating"><span>Ainda sem avaliações</span></div>'
    : `<div class="prof-card__rating"><span>⭐ ${item.notaMediaPrestador != null ? Number(item.notaMediaPrestador).toFixed(1) : 'Sem avaliações'}</span></div>`;

  article.innerHTML = `
    <div class="prof-card__image">
      <div style="width:100%; height:100%; display:flex; align-items:center; justify-content:center; background: var(--color-surface-elevated); color: var(--color-on-surface-secondary); font-size: 40px;"><i class="fas fa-user"></i></div>
      ${distancia ? `<span class="prof-card__distance">${distancia}</span>` : ''}
    </div>
    <div class="prof-card__body">
      <div class="prof-card__info">
        <h3 class="prof-card__name">${item.prestadorNome}</h3>
        <p class="prof-card__specialty">${item.categoria}</p>
      </div>
      ${blocoNota}
      <div class="prof-card__ia-price">
        <span class="prof-card__label">Valor:</span>
        <span class="prof-card__value">R$ ${Number(item.preco).toFixed(2).replace('.', ',')}</span>
      </div>
      <div class="prof-card__actions">
        <button class="btn btn-primary btn-sm btn-agendar">Solicitar</button>
      </div>
    </div>`;

  article.querySelector('.btn-agendar').addEventListener('click', () => solicitarServico(item));
  return article;
}

/**
 * Plota os resultados no mapa e atualiza os contadores.
 *
 * Serviço sem coordenadas cadastradas não gera marcador, mas continua na lista — por isso há dois
 * contadores distintos.
 *
 * @param {Object[]} resultados itens devolvidos pela busca
 * @returns {void}
 */
function atualizarMapa(resultados) {
  const secao = document.getElementById('secaoMapa');
  const contador = document.getElementById('contadorProfissionais');
  const contadorNoMapa = document.getElementById('contadorNoMapa');
  const comCoordenadas = resultados.filter((r) => r.latitude != null && r.longitude != null);

  if (contador) contador.textContent = String(resultados.length);
  if (contadorNoMapa) contadorNoMapa.textContent = String(comCoordenadas.length);

  if (!secao || typeof L === 'undefined') return;

  if (!comCoordenadas.length) {
    secao.hidden = true;
    return;
  }

  secao.hidden = false;

  if (!mapa) {
    mapa = L.map('mapaProfissionais', { zoomControl: true });
    L.tileLayer('https://{s}.basemaps.cartocdn.com/light_all/{z}/{x}/{y}{r}.png', { maxZoom: 19 }).addTo(mapa);
  }

  marcadores.forEach((m) => mapa.removeLayer(m));
  marcadores = comCoordenadas.map((item) => {
    const marcador = L.marker([item.latitude, item.longitude]).addTo(mapa);
    marcador.bindPopup(`<strong>${item.prestadorNome}</strong><br>${item.categoria}`);
    return marcador;
  });

  mapa.fitBounds(comCoordenadas.map((r) => [r.latitude, r.longitude]), { padding: [40, 40], maxZoom: 15 });
  mapa.invalidateSize();
}

/**
 * Preenche os depoimentos com avaliações reais, escondendo a seção quando não houver nenhuma.
 *
 * @returns {Promise<void>}
 */
async function carregarDepoimentos() {
  const secao = document.getElementById('secaoDepoimentos');
  const track = document.getElementById('depoimentosTrack');
  if (!secao || !track) return;

  try {
    const avaliacoes = await TaskGoAPI.listarAvaliacoesRecentes(6);
    if (!avaliacoes.length) return;

    track.innerHTML = '';
    avaliacoes.forEach((avaliacao) => {
      const card = document.createElement('article');
      card.className = 'testimonial-card';
      const estrelas = '★'.repeat(avaliacao.nota) + '☆'.repeat(5 - avaliacao.nota);
      const origem = [avaliacao.categoria, avaliacao.cidade].filter(Boolean).join(' · ');
      card.innerHTML = `
        <div class="testimonial-card__rating" aria-label="${avaliacao.nota} de 5"><span>${estrelas}</span></div>
        <p class="testimonial-card__text">${avaliacao.comentario}</p>
        <div class="testimonial-card__author">
          <strong>${avaliacao.clientePrimeiroNome || 'Cliente'}</strong>
          <span>${origem}</span>
        </div>`;
      track.appendChild(card);
    });
    secao.hidden = false;
  } catch {
    // Sem prova social a seção simplesmente não aparece.
  }
}

/**
 * Confirma e cria uma solicitação de serviço (US-04). Exige login como cliente.
 *
 * @param {Object} item item da busca (BuscaServicoResponse)
 * @returns {Promise<void>}
 */
async function solicitarServico(item) {
  const sessao = TaskGoAPI.getSessaoAtual();
  if (!sessao || sessao.usuario.tipo !== 'CLIENTE') {
    if (confirm('Você precisa entrar como cliente para solicitar um serviço. Ir para o login agora?')) {
      window.location.href = 'login.html';
    }
    return;
  }

  const confirmar = confirm(`Solicitar "${item.categoria}" com ${item.prestadorNome} por R$ ${Number(item.preco).toFixed(2).replace('.', ',')}?`);
  if (!confirmar) return;

  try {
    await TaskGoAPI.criarSolicitacao(item.servicoOfertadoId);
    alert('Solicitação enviada! Acompanhe em "Meus Pedidos" no seu painel.');
    window.location.href = 'painel-cliente.html';
  } catch (erro) {
    alert(erro instanceof TaskGoAPI.ApiError ? erro.message : 'Não foi possível enviar a solicitação. Tente novamente.');
  }
}
