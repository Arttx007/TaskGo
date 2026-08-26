/** Página do painel para onde cada tipo de conta é enviado após o login (padrão: painel-cliente.html). */
const DESTINOS_POR_TIPO = { PRESTADOR: 'painel-profissional.html', ADMIN: 'painel-administrador.html' };

document.addEventListener('DOMContentLoaded', () => {
  if (redirecionarSeJaLogado()) return;

  const toggleBtn = document.getElementById('togglePassword');
  const passwordInput = document.getElementById('password');
  const loginForm = document.getElementById('loginForm');

  if (toggleBtn && passwordInput) {
    toggleBtn.addEventListener('click', () => {
      const isPassword = passwordInput.type === 'password';
      passwordInput.type = isPassword ? 'text' : 'password';

      const icon = isPassword
        ? '<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24"><g fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="2"><path d="M10.733 5.076a10.744 10.744 0 0 1 11.205 6.575a1 1 0 0 1 0 .696a10.8 10.8 0 0 1-1.444 2.49m-6.41-.679a3 3 0 0 1-4.242-4.242"/><path d="M17.479 17.499a10.75 10.75 0 0 1-15.417-5.151a1 1 0 0 1 0-.696a10.75 10.75 0 0 1 4.446-5.143M2 2l20 20"/></g></svg>'
        : '<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24"><g fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="2"><path d="M2.062 12.348a1 1 0 0 1 0-.696a10.75 10.75 0 0 1 19.876 0a1 1 0 0 1 0 .696a10.75 10.75 0 0 1-19.876 0"/><circle cx="12" cy="12" r="3"/></g></svg>';

      toggleBtn.innerHTML = icon;
      toggleBtn.setAttribute('aria-label', isPassword ? 'Esconder senha' : 'Mostrar senha');
    });
  }

  if (loginForm) {
    loginForm.addEventListener(
      'invalid',
      (e) => {
        e.target.classList.add('has-error');
      },
      true
    );

    if (passwordInput) {
      passwordInput.addEventListener('input', () => {
        if (passwordInput.checkValidity()) {
          passwordInput.classList.remove('has-error');
        }
      });
    }

    const emailInput = document.getElementById('email');
    if (emailInput) {
      emailInput.addEventListener('input', () => {
        if (emailInput.checkValidity()) {
          emailInput.classList.remove('has-error');
        }
      });
    }
  }
});

/**
 * Se já existe uma sessão válida (login feito e token ainda não expirado), pula a tela de login e
 * manda o usuário direto para o painel do seu tipo de conta — assim ele só precisa logar de novo
 * se tiver clicado em "Sair da Conta" ou se o token tiver expirado.
 *
 * @returns {boolean} true se o redirecionamento foi iniciado (o chamador deve interromper a configuração da página)
 */
function redirecionarSeJaLogado() {
  const sessao = TaskGoAPI.getSessaoValida();
  if (!sessao || !sessao.usuario) return false;

  window.location.replace(DESTINOS_POR_TIPO[sessao.usuario.tipo] || 'painel-cliente.html');
  return true;
}

/**
 * Autentica o usuário e redireciona para o painel correspondente ao tipo de conta.
 *
 * @param {SubmitEvent} event evento de submit do formulário de login
 * @returns {Promise<void>}
 */
async function fazerLogin(event) {
  event.preventDefault();

  const form = event.target;
  if (!validarFormulario(form)) return;

  const email = document.getElementById('email').value.trim();
  const senha = document.getElementById('password').value;
  const tipoUsuario = form.querySelector('input[name="tipoUsuario"]:checked').value;

  const botao = document.getElementById('thq_button_Z0ao');
  const spanBotao = botao.querySelector('span');
  const textoOriginal = spanBotao.textContent;
  const erroEl = document.getElementById('loginErro');
  erroEl.textContent = '';

  spanBotao.innerHTML = '<i class="fas fa-circle-notch fa-spin"></i> Autenticando...';
  botao.disabled = true;
  botao.style.opacity = '0.7';

  try {
    const resposta = await TaskGoAPI.login(email, senha, tipoUsuario);
    TaskGoAPI.salvarSessao(resposta.token, {
      id: resposta.id,
      nome: resposta.nome,
      tipo: resposta.tipoUsuario,
    });

    window.location.href = DESTINOS_POR_TIPO[resposta.tipoUsuario] || 'painel-cliente.html';
  } catch (erro) {
    erroEl.textContent = erro instanceof TaskGoAPI.ApiError ? erro.message : 'Não foi possível entrar. Tente novamente.';
    spanBotao.textContent = textoOriginal;
    botao.disabled = false;
    botao.style.opacity = '1';
  }
}
