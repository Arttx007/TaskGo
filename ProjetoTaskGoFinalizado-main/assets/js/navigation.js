document.addEventListener('DOMContentLoaded', () => {
  const mobileToggle = document.getElementById('navigation-mobile-toggle');
  const mobileClose = document.getElementById('navigation-mobile-close');
  const mobileOverlay = document.getElementById('navigation-mobile-overlay');
  const mobileLinks = document.querySelectorAll('.navigation-mobile-link');

  if (!mobileToggle || !mobileClose || !mobileOverlay) {
    return;
  }

  const openMenu = () => {
    mobileOverlay.style.display = 'flex';
    setTimeout(() => {
      mobileOverlay.classList.add('is-active');
      mobileToggle.setAttribute('aria-expanded', 'true');
      document.body.style.overflow = 'hidden';
    }, 10);
  };

  const closeMenu = () => {
    mobileOverlay.classList.remove('is-active');
    mobileToggle.setAttribute('aria-expanded', 'false');
    document.body.style.overflow = '';

    setTimeout(() => {
      if (!mobileOverlay.classList.contains('is-active')) {
        mobileOverlay.style.display = 'none';
      }
    }, 400);
  };

  mobileToggle.addEventListener('click', openMenu);
  mobileClose.addEventListener('click', closeMenu);

  mobileLinks.forEach((link) => link.addEventListener('click', closeMenu));

  document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape' && mobileOverlay.classList.contains('is-active')) {
      closeMenu();
    }
  });

  window.addEventListener('scroll', () => {
    const nav = document.querySelector('.navigation-wrapper');
    if (!nav) return;

    if (window.scrollY > 20) {
      nav.style.boxShadow = 'var(--shadow-level-2)';
      nav.style.backgroundColor = 'color-mix(in oklab, var(--color-surface) 95%, transparent)';
      nav.style.backdropFilter = 'blur(8px)';
    } else {
      nav.style.boxShadow = 'none';
      nav.style.backgroundColor = 'var(--color-surface)';
      nav.style.backdropFilter = 'none';
    }
  });
});



