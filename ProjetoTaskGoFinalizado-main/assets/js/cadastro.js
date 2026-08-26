/**
 * Cadastra o usuário (cliente ou prestador) e faz login automático em seguida, já que o
 * endpoint de cadastro não emite token — evita pedir a senha de novo logo após criá-la.
 *
 * @param {SubmitEvent} event evento de submit do formulário de cadastro
 * @returns {Promise<void>}
 */
async function fazerCadastro(event) {
    event.preventDefault();

    const form = event.target;
    if (!validarFormulario(form)) return;

    const tipoConta = document.querySelector('input[name="tipoConta"]:checked').value;
    const nome = document.getElementById('nome').value.trim();
    const email = document.getElementById('email').value.trim();
    const senha = document.getElementById('senha').value;

    const botao = document.getElementById('btnCadastro');
    const textoOriginal = botao.innerHTML;
    const erroEl = document.getElementById('cadastroErro');
    erroEl.textContent = '';

    botao.innerHTML = '<i class="fas fa-circle-notch fa-spin"></i> Criando conta...';
    botao.disabled = true;

    try {
        const tipoUsuario = tipoConta === 'profissional' ? 'PRESTADOR' : 'CLIENTE';

        const cadastrado = tipoUsuario === 'PRESTADOR'
            ? await TaskGoAPI.registrarPrestador({ nome, email, senha })
            : await TaskGoAPI.registrarCliente({ nome, email, senha });

        const sessao = await TaskGoAPI.login(email, senha, tipoUsuario);
        TaskGoAPI.salvarSessao(sessao.token, { id: sessao.id, nome: sessao.nome, tipo: sessao.tipoUsuario });

        if (tipoUsuario === 'PRESTADOR') {
            // sessionStorage é a fonte primária (não a query string): servidores estáticos como
            // `serve` (sugerido no CLAUDE.md) redirecionam .html?query para uma URL "limpa" sem
            // querystring, o que perderia o prestadorId antes de cadastro-kyc.html conseguir lê-lo.
            sessionStorage.setItem('taskgo_kyc_prestador_id', cadastrado.idPrestador);
            window.location.href = `cadastro-kyc.html?prestadorId=${cadastrado.idPrestador}`;
        } else {
            window.location.href = 'painel-cliente.html';
        }
    } catch (erro) {
        if (erro instanceof TaskGoAPI.ApiError) {
            aplicarErrosDoServidor(form, erro.fieldErrors);
            erroEl.textContent = erro.message;
        } else {
            erroEl.textContent = 'Não foi possível concluir o cadastro. Tente novamente.';
        }
        botao.innerHTML = textoOriginal;
        botao.disabled = false;
    }
}

/**
 * Alterna a visibilidade do campo de senha.
 *
 * @returns {void}
 */
function mostrarOcultarSenha() {
    const inputSenha = document.getElementById('senha');
    const icone = document.getElementById('togglePassword');

    if (inputSenha.type === 'password') {
        inputSenha.type = 'text';
        icone.classList.remove('fa-eye');
        icone.classList.add('fa-eye-slash');
    } else {
        inputSenha.type = 'password';
        icone.classList.remove('fa-eye-slash');
        icone.classList.add('fa-eye');
    }
}
