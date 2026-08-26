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

  // Inicializa o carrossel de profissionais
  initCarousel('pros-carousel');
  // Se você colocar botões "prev/next" nos depoimentos, é só descomentar a linha abaixo:
  // initCarousel('test-carousel');

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
  // 3. ESTIMATIVA IA (Busca Hero)
  // ==========================================
  const searchForm = document.querySelector('.search-form');

  if (searchForm) {
    searchForm.addEventListener('submit', (e) => {
      e.preventDefault(); 
      const serviceType = document.getElementById('service-type').value;
      const proximity = document.getElementById('proximity').value;

      if (serviceType === 'true' || !serviceType) {
        alert('Por favor, selecione qual serviço você precisa.');
        return;
      }

      let basePrice = 0;
      switch (serviceType) {
        case 'eletricista': basePrice = 150; break;
        case 'encanador': basePrice = 120; break;
        case 'pedreiro': basePrice = 200; break;
        case 'pintor': basePrice = 100; break;
        default: basePrice = 100;
      }

      const distanceTax = parseInt(proximity) * 2.5; 
      const finalAverage = basePrice + distanceTax;
      const minPrice = Math.floor(finalAverage * 0.85);
      const maxPrice = Math.ceil(finalAverage * 1.15); 

      showEstimateResult(minPrice, maxPrice, serviceType);
    });
  }

  function showEstimateResult(min, max, service) {
    let resultContainer = document.getElementById('ai-estimate-result');

    if (!resultContainer) {
      resultContainer = document.createElement('div');
      resultContainer.id = 'ai-estimate-result';
      resultContainer.className = 'animate__animated animate__fadeInUp'; 
      
      resultContainer.style.marginTop = '20px';
      resultContainer.style.padding = '15px';
      resultContainer.style.backgroundColor = 'rgba(255, 255, 255, 0.9)';
      resultContainer.style.borderRadius = '8px';
      resultContainer.style.borderLeft = '4px solid #4CAF50';
      resultContainer.style.color = '#333';
      resultContainer.style.boxShadow = '0 4px 6px rgba(0,0,0,0.1)';

      const searchWrapper = document.querySelector('.search-bar-wrapper');
      searchWrapper.appendChild(resultContainer);
    }

    const serviceName = service.charAt(0).toUpperCase() + service.slice(1);
    
    resultContainer.innerHTML = `
      <div style="display: flex; align-items: center; gap: 8px; margin-bottom: 8px;">
        <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#4CAF50" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <path d="M12 18V5m3 8a4.17 4.17 0 0 1-3-4a4.17 4.17 0 0 1-3 4m8.598-6.5A3 3 0 1 0 12 5a3 3 0 1 0-5.598 1.5"></path>
          <path d="M17.997 5.125a4 4 0 0 1 2.526 5.77"></path>
          <path d="M18 18a4 4 0 0 0 2-7.464"></path>
          <path d="M19.967 17.483A4 4 0 1 1 12 18a4 4 0 1 1-7.967-.517"></path>
          <path d="M6 18a4 4 0 0 1-2-7.464"></path>
          <path d="M6.003 5.125a4 4 0 0 0-2.526 5.77"></path>
        </svg>
        <strong style="color: #4CAF50;">Estimativa IA para ${serviceName}</strong>
      </div>
      <p style="font-size: 1.5rem; font-weight: 800; margin: 0;">R$ ${min} - R$ ${max}</p>
      <p style="font-size: 0.85rem; color: #666; margin-top: 4px;">Valor estimado com base na distância e média de mercado local.</p>
    `;
  }
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