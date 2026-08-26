let ultimaBusca = { categoria: '', lat: null, lon: null, cidade: '' };

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
  // 2. RECUPERAR BUSCA DA HOME (pela URL) e disparar busca automática
  // ==========================================
  const urlParams = new URLSearchParams(window.location.search);
  const serviceParam = urlParams.get('service');
  const servicoInput = document.getElementById('thq_servico_zv-1');

  if (serviceParam && servicoInput) {
    servicoInput.value = serviceParam.charAt(0).toUpperCase() + serviceParam.slice(1);
    buscarComGeolocalizacao(serviceParam);
  }

  // ==========================================
  // 3. SLIDER DE RAIO — texto dinâmico + re-busca real (US-03)
  // ==========================================
  const distanceSlider = document.getElementById('thq_textinput_U--D');
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
  // 4. LÓGICA DA ESCALA (cosmético, mantido)
  // ==========================================
  const scaleSelect = document.querySelector('select[name="escala"]');
  if (scaleSelect) {
    scaleSelect.addEventListener('change', (e) => {
      const value = e.target.value;
      const btn = document.querySelector('.busca-rapida__form button');
      if (!btn) return;

      if (value === 'grande') {
        btn.style.backgroundColor = 'var(--color-accent)';
        btn.innerText = 'Ver Projetos de Grande Escala';
      } else {
        btn.style.backgroundColor = 'var(--color-primary)';
        btn.innerText = 'Visualizar Profissionais';
      }
    });
  }

  // ==========================================
  // 5. BUSCA REAL (US-03) — geolocalização, com fallback por endereço/cidade
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
  // 6. ANIMAÇÕES DE SCROLL EM CASCATA (elementos estáticos da página)
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
    '.lista-profissionais__header, .mapa-interativo__container, .filtros-avancados__container, .destaque-perfil__container, .testimonial-card, .bento-item, .cta-final__container'
  );
  elementsToAnimate.forEach((el) => {
    el.style.opacity = '0';
    el.style.transform = 'translateY(40px)';
    el.style.transition = 'all 0.6s ease-out';
    scrollObserver.observe(el);
  });
});

/**
 * Tenta obter a localização do navegador; sem permissão, cai no fallback por endereço/cidade
 * digitado (US-03, cenário de exceção).
 *
 * @param {string} categoria termo buscado no campo "Que serviço você precisa?"
 * @returns {void}
 */
function buscarComGeolocalizacao(categoria) {
  const enderecoDigitado = (document.getElementById('thq_localizacao_ehOt') || {}).value || '';

  if (!navigator.geolocation) {
    executarBusca({ categoria, lat: null, lon: null, cidade: enderecoDigitado });
    return;
  }

  navigator.geolocation.getCurrentPosition(
    (posicao) => {
      executarBusca({
        categoria,
        lat: posicao.coords.latitude,
        lon: posicao.coords.longitude,
        cidade: '',
      });
    },
    () => {
      executarBusca({ categoria, lat: null, lon: null, cidade: enderecoDigitado });
    },
    { timeout: 8000 }
  );
}

/**
 * Executa a busca por serviços e renderiza os resultados (US-03).
 *
 * @param {{categoria: string, lat: number|null, lon: number|null, cidade: string, raioKm?: number}} filtro
 * @returns {Promise<void>}
 */
async function executarBusca(filtro) {
  ultimaBusca = filtro;
  const container = document.getElementById('resultadosContainer');
  container.innerHTML = '<p class="section-content" style="grid-column: 1 / -1;">Buscando profissionais...</p>';

  try {
    const resultado = await TaskGoAPI.buscarServicos(filtro);
    renderizarResultados(resultado);
  } catch (erro) {
    container.innerHTML = `<p class="section-content" style="grid-column: 1 / -1;">Não foi possível buscar agora. ${erro instanceof TaskGoAPI.ApiError ? erro.message : ''}</p>`;
  }
}

function renderizarResultados(resultado) {
  const container = document.getElementById('resultadosContainer');

  if (!resultado.resultados || resultado.resultados.length === 0) {
    container.innerHTML = `<p class="section-content" style="grid-column: 1 / -1;">${resultado.mensagem || 'Nenhum resultado encontrado nesta região.'}</p>`;
    return;
  }

  container.innerHTML = '';
  resultado.resultados.forEach((item) => container.appendChild(criarProfCard(item)));
}

function criarProfCard(item) {
  const article = document.createElement('article');
  article.className = 'prof-card';

  const distancia = item.distanciaKm != null ? `${item.distanciaKm.toFixed(1)} km` : '';
  const nota = item.notaMediaPrestador != null ? Number(item.notaMediaPrestador).toFixed(1) : 'Sem avaliações';

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
      <div class="prof-card__rating"><span>⭐ ${nota}</span></div>
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
