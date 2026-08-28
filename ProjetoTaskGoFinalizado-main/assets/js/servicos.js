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
      document.body.style.overflow = 'hidden'; // Impede o fundo de rolar
    });

    mobileCloseBtn.addEventListener('click', () => {
      mobileOverlay.classList.remove('show-overlay');
      document.body.style.overflow = '';
    });
  }

  // ==========================================
  // 2. FILTROS E BUSCA
  // ==========================================
  // As NodeLists são consultadas a cada uso, e não capturadas uma única vez: o bloco de outras
  // categorias injeta cards depois do DOMContentLoaded, e uma lista capturada aqui os ignoraria.
  const filterBtns = document.querySelectorAll('.filter-btn');

  /** @returns {HTMLElement[]} todos os cards de serviço presentes no DOM agora */
  function cardsDeServico() {
    return Array.from(document.querySelectorAll('.service-card'));
  }

  /**
   * @param {HTMLElement} card card de serviço
   * @returns {string} as categorias do card, ou string vazia quando o atributo não existe
   */
  function categoriasDoCard(card) {
    return card.getAttribute('data-category') || '';
  }

  filterBtns.forEach((btn) => {
    btn.addEventListener('click', () => {
      filterBtns.forEach((b) => b.classList.remove('active'));
      btn.classList.add('active');

      const filterValue = btn.getAttribute('data-filter');

      cardsDeServico().forEach((card) => {
        if (filterValue === 'todos' || categoriasDoCard(card).includes(filterValue)) {
          card.classList.remove('hidden');
          setTimeout(() => { card.style.opacity = '1'; }, 50);
        } else {
          card.classList.add('hidden');
          card.style.opacity = '0';
        }
      });
    });
  });

  // ==========================================
  // 3. BARRA DE PESQUISA
  // ==========================================
  // Ao digitar, filtra o catálogo de categorias visível. Ao enviar, leva à busca real — antes o
  // campo prometia "Buscar serviço (ex: Trocar chuveiro)" e só filtrava oito títulos no DOM.
  const searchInput = document.getElementById('searchInput');

  if (searchInput) {
    searchInput.addEventListener('input', (e) => {
      const termo = e.target.value.toLowerCase();

      filterBtns.forEach((b) => b.classList.remove('active'));
      const botaoTodos = document.querySelector('[data-filter="todos"]');
      if (botaoTodos) botaoTodos.classList.add('active');

      cardsDeServico().forEach((card) => {
        const titulo = (card.querySelector('h3') || {}).innerText || '';
        const descricao = (card.querySelector('p') || {}).innerText || '';
        const casa = titulo.toLowerCase().includes(termo) || descricao.toLowerCase().includes(termo);
        card.classList.toggle('hidden', !casa);
      });
    });

    searchInput.addEventListener('keydown', (e) => {
      if (e.key !== 'Enter') return;
      e.preventDefault();
      irParaBusca(searchInput.value);
    });
  }

  /**
   * Leva o visitante à busca real de profissionais para o termo digitado.
   *
   * @param {string} termo categoria ou serviço digitado
   * @returns {void}
   */
  function irParaBusca(termo) {
    const limpo = (termo || '').trim();
    if (!limpo) return;
    window.location.href = 'profissionais-prximos.html?service=' + encodeURIComponent(limpo);
  }

  // ==========================================
  // 4. RECONCILIAÇÃO COM O CATÁLOGO REAL
  // ==========================================

  /**
   * Confronta o grid curado com o que o backend realmente oferece.
   *
   * O grid de cards é mantido porque é a única fonte de vocabulário controlado do produto —
   * `categoria` é texto livre no backend. Mas ele sozinho mente em duas direções: mostra categoria
   * sem oferta alguma, e esconde categoria que existe e não foi prevista no grid. Esta função
   * corrige as duas.
   *
   * @returns {Promise<void>}
   */
  async function reconciliarCategorias() {
    let categorias;
    try {
      categorias = await TaskGoAPI.listarCategorias();
    } catch {
      return; // Sem rede, o grid curado continua servindo como navegação estática.
    }

    const porNome = new Map(categorias.map((c) => [c.categoria.toLowerCase(), c]));
    const curadas = new Set();

    cardsDeServico().forEach((card) => {
      const slug = slugDoCard(card);
      if (!slug) return;
      curadas.add(slug);

      const encontrada = casarCategoria(slug, porNome);
      anotarDisponibilidade(card, encontrada);
    });

    const naoPrevistas = categorias.filter((c) => !casaComAlgumSlug(c.categoria, curadas));
    renderizarOutrasCategorias(naoPrevistas);
  }

  /**
   * @param {HTMLElement} card card curado
   * @returns {string} o valor de `service` do link do card, que é a categoria que ele busca
   */
  function slugDoCard(card) {
    const href = card.getAttribute('href') || '';
    const match = href.match(/service=([^&]+)/);
    return match ? decodeURIComponent(match[1]).toLowerCase() : '';
  }

  /**
   * Reduz o texto a letras e dígitos, sem acento nem espaço.
   *
   * O prestador digita a categoria livremente, então o card curado "arcondicionado" precisa casar
   * com "Instalação de Ar condicionado". Sem normalizar, a diferença de um espaço marcaria o card
   * como indisponível enquanto a oferta existe.
   *
   * @param {string} texto categoria ou slug
   * @returns {string} forma comparável
   */
  function normalizar(texto) {
    return texto
      .toLowerCase()
      .normalize('NFD')
      .replace(/[̀-ͯ]/g, '')
      .replace(/[^a-z0-9]/g, '');
  }

  /**
   * @param {string} slug categoria do card curado
   * @param {Map<string, {categoria: string, totalServicos: number}>} porNome categorias reais
   * @returns {{categoria: string, totalServicos: number}|null} a categoria real correspondente
   */
  function casarCategoria(slug, porNome) {
    if (porNome.has(slug)) return porNome.get(slug);

    const alvo = normalizar(slug);
    for (const [nome, dados] of porNome) {
      const candidato = normalizar(nome);
      if (candidato.includes(alvo) || alvo.includes(candidato)) return dados;
    }
    return null;
  }

  /**
   * @param {string} nomeCategoria nome real vindo do backend
   * @param {Set<string>} slugs slugs dos cards curados
   * @returns {boolean} true se algum card curado já representa essa categoria
   */
  function casaComAlgumSlug(nomeCategoria, slugs) {
    const nome = normalizar(nomeCategoria);
    return Array.from(slugs).some((slug) => {
      const alvo = normalizar(slug);
      return nome.includes(alvo) || alvo.includes(nome);
    });
  }

  /**
   * Anota no card quantos profissionais existem, ou o marca como indisponível.
   *
   * Card sem oferta deixa de ser link: levar a uma busca que já se sabe vazia é o tipo de promessa
   * falsa que esta página tinha.
   *
   * @param {HTMLElement} card card curado
   * @param {{totalServicos: number}|null} categoria categoria real correspondente, ou null
   * @returns {void}
   */
  function anotarDisponibilidade(card, categoria) {
    const link = card.querySelector('.service-link');
    if (!link) return;

    if (!categoria) {
      card.classList.add('service-card--indisponivel');
      card.removeAttribute('href');
      link.textContent = 'Nenhum profissional cadastrado ainda';
      return;
    }

    const rotulo = categoria.totalServicos === 1 ? 'profissional' : 'profissionais';
    link.textContent = 'Ver ' + categoria.totalServicos + ' ' + rotulo;
  }

  /**
   * Renderiza as categorias que existem no backend e não estão no grid curado.
   *
   * Sem este bloco, um prestador que publicou em categoria não prevista fica inalcançável pela
   * navegação — invisível por escolha de markup, não por falta de oferta.
   *
   * @param {Array<{categoria: string, totalServicos: number}>} categorias categorias não previstas
   * @returns {void}
   */
  function renderizarOutrasCategorias(categorias) {
    const secao = document.getElementById('outrasCategorias');
    const grid = document.getElementById('outrasCategoriasGrid');
    if (!secao || !grid) return;

    if (!categorias.length) {
      secao.hidden = true;
      return;
    }

    grid.innerHTML = '';
    categorias.forEach((item) => {
      const card = document.createElement('a');
      card.className = 'service-card';
      card.setAttribute('data-category', 'outros');
      card.href = 'profissionais-prximos.html?service=' + encodeURIComponent(item.categoria);

      const rotulo = item.totalServicos === 1 ? 'profissional' : 'profissionais';
      card.innerHTML = [
        '<div class="service-icon icon-indigo"><i class="fas fa-toolbox"></i></div>',
        '<h3>' + item.categoria + '</h3>',
        '<p>Categoria cadastrada por profissionais da plataforma.</p>',
        '<span class="service-link">Ver ' + item.totalServicos + ' ' + rotulo + '</span>',
      ].join('');
      grid.appendChild(card);
    });

    secao.hidden = false;
  }

  reconciliarCategorias();

  // ==========================================
  // 5. ANIMAÇÕES DE SCROLL
  // ==========================================
  const observerOptions = {
    threshold: 0.1,
    rootMargin: '0px 0px -40px 0px',
  };

  const scrollObserver = new IntersectionObserver((entries, observer) => {
    entries.forEach((entry) => {
      if (entry.isIntersecting) {
        entry.target.classList.add('visible');
        observer.unobserve(entry.target);
      }
    });
  }, observerOptions);

  document.querySelectorAll('.reveal-on-scroll').forEach((el, index) => {
    if (el.classList.contains('service-card')) {
      el.style.transitionDelay = ((index % 4) * 0.15) + 's';
    }
    scrollObserver.observe(el);
  });
});
