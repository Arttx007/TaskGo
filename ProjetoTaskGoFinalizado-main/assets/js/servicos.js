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
  // 2. LÓGICA DE FILTROS (Botões)
  // ==========================================
  const filterBtns = document.querySelectorAll('.filter-btn');
  const serviceCards = document.querySelectorAll('.service-card');

  filterBtns.forEach(btn => {
    btn.addEventListener('click', () => {
      // Remove a classe 'active' de todos os botões e coloca no clicado
      filterBtns.forEach(b => b.classList.remove('active'));
      btn.classList.add('active');

      const filterValue = btn.getAttribute('data-filter');

      // Mostra ou esconde os cards baseado na categoria
      serviceCards.forEach(card => {
        if (filterValue === 'todos' || card.getAttribute('data-category').includes(filterValue)) {
          card.classList.remove('hidden');
          // Força um pequeno reflow para a animação reiniciar bonitinha
          setTimeout(() => card.style.opacity = '1', 50);
        } else {
          card.classList.add('hidden');
          card.style.opacity = '0';
        }
      });
    });
  });

  // ==========================================
  // 3. BARRA DE PESQUISA EM TEMPO REAL
  // ==========================================
  const searchInput = document.getElementById('searchInput');
  
  if (searchInput) {
    searchInput.addEventListener('input', (e) => {
      const term = e.target.value.toLowerCase();
      
      // Reseta os botões de filtro para "Todos" ao usar a pesquisa
      filterBtns.forEach(b => b.classList.remove('active'));
      document.querySelector('[data-filter="todos"]').classList.add('active');

      serviceCards.forEach(card => {
        const title = card.querySelector('h3').innerText.toLowerCase();
        const desc = card.querySelector('p').innerText.toLowerCase();
        
        // Se o título ou a descrição tiverem a palavra digitada, mostra o card
        if (title.includes(term) || desc.includes(term)) {
          card.classList.remove('hidden');
        } else {
          card.classList.add('hidden');
        }
      });
    });
  }

  // ==========================================
  // 4. ANIMAÇÕES DE SCROLL
  // ==========================================
  const observerOptions = {
    threshold: 0.1, 
    rootMargin: "0px 0px -40px 0px" 
  };

  const scrollObserver = new IntersectionObserver((entries, observer) => {
    entries.forEach((entry) => {
      if (entry.isIntersecting) {
        entry.target.classList.add('visible');
        observer.unobserve(entry.target); 
      }
    });
  }, observerOptions);

  // Aplica o observador em tudo que tem a classe 'reveal-on-scroll'
  document.querySelectorAll('.reveal-on-scroll').forEach((el, index) => {
    // Se for um card de serviço, adiciona um atraso dinâmico para dar o efeito de cascata (onda)
    if(el.classList.contains('service-card')) {
      let delay = (index % 4) * 0.15; // Atraso de 0s, 0.15s, 0.3s, 0.45s
      el.style.transitionDelay = `${delay}s`;
    }
    scrollObserver.observe(el);
  });
});