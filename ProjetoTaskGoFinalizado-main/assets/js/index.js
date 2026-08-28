document.addEventListener('DOMContentLoaded', () => {
  // ==========================================
  // 1. LÓGICA DO CARROSSEL
  // ==========================================
  const initCarousel = (id) => {
    const wrapper = document.getElementById(id);
    if (!wrapper) return;

    const track = wrapper.querySelector('.carousel-track') || wrapper.querySelector('.testimonials-track');
    const nextBtn = wrapper.querySelector('.next');
    const prevBtn = wrapper.querySelector('.prev');

    if (!track || !nextBtn || !prevBtn) return;

    let scrollAmount = 0;
    const step = 320; // Largura do card + gap (ajuste conforme seu CSS)

    nextBtn.addEventListener('click', () => {
      const maxScroll = track.scrollWidth - wrapper.clientWidth;
      if (scrollAmount < maxScroll) {
        scrollAmount += step;
        track.style.transform = `translateX(-${scrollAmount}px)`;
      }
    });

    prevBtn.addEventListener('click', () => {
      if (scrollAmount > 0) {
        scrollAmount -= step;
        track.style.transform = `translateX(-${scrollAmount}px)`;
      }
    });
  };

  // Inicializa o carrossel de categorias
  initCarousel('pros-carousel');

  // ==========================================
  // 2. ANIMAÇÕES DA TELA (Timeline, Bento, Cards)
  // ==========================================
  const steps = document.querySelectorAll('.sim-step');
  const arrows = document.querySelectorAll('.sim-arrow');

  if (steps.length > 0) {
    const runCycle = () => {
      steps.forEach((step, i) => {
        setTimeout(() => {
          step.style.opacity = '1';
          step.style.transform = 'translateY(0)';
          if (arrows[i]) arrows[i].style.opacity = '1';
        }, i * 800);
      });

      setTimeout(() => {
        steps.forEach((step) => { step.style.opacity = '0.3'; });
        arrows.forEach((arrow) => { arrow.style.opacity = '0.3'; });
        setTimeout(runCycle, 1000);
      }, 5000);
    };

    steps.forEach((step) => {
      step.style.transition = 'all 0.5s ease';
      step.style.opacity = '0.3';
    });

    arrows.forEach((arrow) => {
      arrow.style.transition = 'all 0.5s ease';
      arrow.style.opacity = '0.3';
    });

    runCycle();
  }

  const items = document.querySelectorAll('.timeline-item, .bento-card, .pro-card');
  if (items.length > 0) {
    const observer = new IntersectionObserver((entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            entry.target.style.opacity = '1';
            entry.target.style.transform = 'translateY(0)';
          }
        });
      }, { threshold: 0.1 }
    );

    items.forEach((item) => {
      item.style.opacity = '0';
      item.style.transform = 'translateY(30px)';
      item.style.transition = 'all 0.6s ease-out';
      observer.observe(item);
    });
  }

  // ==========================================
  // 3. FAIXA DE PREÇO PRATICADA (busca do hero)
  // ==========================================
  // O formulário do hero NÃO tem handler de submit, de propósito: o markup já aponta para
  // pages/profissionais-prximos.html com os campos `service` e `radius`, que a página de
  // profissionais lê para disparar a busca real. Um `preventDefault()` aqui era exatamente o que
  // impedia a home de chegar à busca.
  const seletorServico = document.getElementById('service-type');
  if (seletorServico) {
    seletorServico.addEventListener('change', () => exibirFaixaDePreco(seletorServico.value));
  }

  /**
   * Consulta e exibe a faixa de preço praticada na categoria escolhida.
   *
   * Nenhum valor é calculado aqui: quando o backend informa amostra insuficiente, exibe a mensagem
   * dele em vez de inventar uma faixa.
   *
   * @param {string} categoria categoria escolhida no seletor do hero
   * @returns {Promise<void>}
   */
  async function exibirFaixaDePreco(categoria) {
    const caixa = obterCaixaDeFaixa();

    if (!categoria || categoria === 'true') {
      caixa.hidden = true;
      return;
    }

    caixa.hidden = false;
    caixa.textContent = 'Consultando preços praticados...';

    try {
      const faixa = await TaskGoAPI.obterEstimativa(categoria);

      if (faixa.mensagem) {
        caixa.textContent = faixa.mensagem;
        return;
      }

      caixa.innerHTML = [
        '<span class="faixa-preco__rotulo">Preço praticado em ' + categoria + '</span>',
        '<strong class="faixa-preco__valor">' + formatarReal(faixa.minimo) + ' a ' + formatarReal(faixa.maximo) + '</strong>',
        '<span class="faixa-preco__nota">Mediana ' + formatarReal(faixa.mediana) + ', sobre ' + faixa.amostra + ' profissionais.</span>',
      ].join('');
    } catch (erro) {
      caixa.textContent = erro instanceof TaskGoAPI.ApiError
        ? erro.message
        : 'Não foi possível consultar os preços agora.';
    }
  }

  /** @returns {HTMLElement} a caixa da faixa de preço, criada na primeira chamada */
  function obterCaixaDeFaixa() {
    let caixa = document.getElementById('faixaPrecoPraticado');
    if (!caixa) {
      caixa = document.createElement('div');
      caixa.id = 'faixaPrecoPraticado';
      caixa.className = 'faixa-preco';
      document.querySelector('.search-bar-wrapper').appendChild(caixa);
    }
    return caixa;
  }

  /**
   * @param {number|string|null} valor valor em reais devolvido pela API
   * @returns {string} valor formatado como moeda, ou traço quando ausente
   */
  function formatarReal(valor) {
    if (valor === null || valor === undefined) return '—';
    return 'R$ ' + Number(valor).toFixed(2).replace('.', ',');
  }

  // ==========================================
  // 3b. VITRINE DE CATEGORIAS
  // ==========================================

  /**
   * Preenche o carrossel com as categorias que realmente têm serviço disponível, cada uma levando à
   * busca daquela categoria.
   *
   * Não exibe prestador individual: destacar prestador é US-14, fora do escopo atual, e a busca
   * exige categoria e localidade que o visitante ainda não informou.
   *
   * @returns {Promise<void>}
   */
  async function carregarCategorias() {
    const track = document.getElementById('categoriasCarrossel');
    if (!track) return;

    try {
      const categorias = await TaskGoAPI.listarCategorias();

      if (!categorias.length) {
        const secao = track.closest('.nearby-pros');
        if (secao) secao.hidden = true;
        return;
      }

      track.innerHTML = '';
      categorias.forEach((categoria) => track.appendChild(criarCardCategoria(categoria)));
      revelarImediatamente(track.children);
    } catch {
      track.innerHTML = '<p class="section-content">Não foi possível carregar as categorias agora.</p>';
    }
  }

  /**
   * @param {{categoria: string, totalServicos: number}} item categoria devolvida pela API
   * @returns {HTMLAnchorElement} card que leva à busca real da categoria
   */
  function criarCardCategoria(item) {
    const card = document.createElement('a');
    card.className = 'pro-card categoria-card';
    card.href = 'pages/profissionais-prximos.html?service=' + encodeURIComponent(item.categoria);

    const rotulo = item.totalServicos === 1 ? 'profissional' : 'profissionais';
    card.innerHTML = [
      '<div class="pro-info">',
      '  <div class="pro-meta"><span class="pro-category">Categoria</span></div>',
      '  <h3>' + item.categoria + '</h3>',
      '  <div class="pro-pricing">',
      '    <span class="price-label">Disponíveis</span>',
      '    <span class="price-value">' + item.totalServicos + ' ' + rotulo + '</span>',
      '  </div>',
      '  <span class="service-link">Ver profissionais</span>',
      '</div>',
    ].join('');
    return card;
  }

  // ==========================================
  // 3c. DEPOIMENTOS REAIS
  // ==========================================

  /**
   * Preenche os depoimentos com avaliações reais.
   *
   * Sem avaliação com comentário, a seção inteira permanece escondida — moldura vazia seria pior que
   * ausência, e depoimento de exemplo é o que esta mudança removeu.
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
      avaliacoes.forEach((avaliacao) => track.appendChild(criarCardDepoimento(avaliacao)));
      secao.hidden = false;
      revelarImediatamente(track.children);
    } catch {
      // Sem prova social a seção simplesmente não aparece.
    }
  }

  /**
   * @param {{nota: number, comentario: string, clientePrimeiroNome: string, categoria: string|null,
   *          cidade: string|null}} avaliacao avaliação real devolvida pela API
   * @returns {HTMLElement} card de depoimento
   */
  function criarCardDepoimento(avaliacao) {
    const card = document.createElement('div');
    card.className = 'test-card';

    const estrelas = '★'.repeat(avaliacao.nota) + '☆'.repeat(5 - avaliacao.nota);
    const origem = [avaliacao.categoria, avaliacao.cidade].filter(Boolean).join(' · ');

    card.innerHTML = [
      '<div class="test-rating" aria-label="' + avaliacao.nota + ' de 5">' + estrelas + '</div>',
      '<p class="test-text">' + avaliacao.comentario + '</p>',
      '<div class="test-user">',
      '  <div class="user-info">',
      '    <strong>' + (avaliacao.clientePrimeiroNome || 'Cliente') + '</strong>',
      '    <span>' + origem + '</span>',
      '  </div>',
      '</div>',
    ].join('');
    return card;
  }

  /**
   * Torna visíveis elementos injetados depois do `DOMContentLoaded`.
   *
   * O `IntersectionObserver` da seção 2 roda uma única vez e zera a opacidade dos `.pro-card` que
   * existiam naquele instante; card criado depois nunca seria observado e ficaria invisível.
   *
   * @param {HTMLCollection} elementos elementos recém-inseridos
   * @returns {void}
   */
  function revelarImediatamente(elementos) {
    Array.from(elementos).forEach((elemento) => {
      elemento.style.opacity = '1';
      elemento.style.transform = 'translateY(0)';
    });
  }

  carregarCategorias();
  carregarDepoimentos();

  // ==========================================
  // 4. MENU MOBILE (Hambúrguer)
  // ==========================================
  const mobileToggleBtn = document.getElementById('navigation-mobile-toggle');
  const mobileOverlay = document.getElementById('navigation-mobile-overlay');
  const mobileCloseBtn = document.getElementById('navigation-mobile-close');

  if (mobileToggleBtn && mobileOverlay && mobileCloseBtn) {
    // Abrir o menu
    mobileToggleBtn.addEventListener('click', () => {
      mobileOverlay.classList.add('show-overlay');
      document.body.style.overflow = 'hidden'; // Trava o scroll da página no fundo
    });

    // Fechar o menu no botão X
    mobileCloseBtn.addEventListener('click', () => {
      mobileOverlay.classList.remove('show-overlay');
      document.body.style.overflow = ''; // Devolve o scroll da página
    });

    // Fechar o menu automaticamente se a pessoa clicar em algum link
    const mobileLinks = mobileOverlay.querySelectorAll('a');
    mobileLinks.forEach(link => {
      link.addEventListener('click', () => {
        mobileOverlay.classList.remove('show-overlay');
        document.body.style.overflow = '';
      });
    });
  }
});
