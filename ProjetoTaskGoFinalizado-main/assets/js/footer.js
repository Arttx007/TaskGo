document.addEventListener('DOMContentLoaded', () => {
  const footerLinks = document.querySelectorAll('.footer-nav-link, .footer-legal-link');
  footerLinks.forEach((link) => {
    link.addEventListener('mouseenter', () => {
      link.style.transition = 'all 0.3s cubic-bezier(0.4, 0, 0.2, 1)';
    });
  });

  const socialIcons = document.querySelectorAll('.footer-social-icon');
  socialIcons.forEach((icon) => {
    icon.addEventListener('click', () => {
      const ripple = document.createElement('span');
      ripple.classList.add('ripple-effect');
      icon.appendChild(ripple);
      setTimeout(() => ripple.remove(), 600);
    });
  });
});



