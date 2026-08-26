/**
 * Validação de formulário reutilizável, generalizando o padrão já usado em login.html
 * (`.has-error` + `checkValidity()`) para todos os formulários novos, com mensagem de erro
 * inline em vez de `alert()`.
 */

/**
 * Valida um único campo e atualiza sua exibição de erro.
 *
 * @param {HTMLInputElement|HTMLTextAreaElement|HTMLSelectElement} input campo a validar
 * @param {Object<string,string>} [mensagens] mensagens customizadas por chave de `ValidityState` (ex.: `{valueMissing: '...'}`)
 * @returns {boolean} true se o campo é válido
 */
function validarCampo(input, mensagens = {}) {
  const valido = input.checkValidity();

  input.classList.toggle('has-error', !valido);

  let erroEl = input.parentElement.querySelector('.field-error');
  if (!erroEl) {
    erroEl = document.createElement('span');
    erroEl.className = 'field-error';
    input.parentElement.appendChild(erroEl);
  }

  if (valido) {
    erroEl.textContent = '';
    return true;
  }

  const chaveInvalida = Object.keys(input.validity).find((chave) => input.validity[chave] && chave !== 'valid');
  erroEl.textContent = (chaveInvalida && mensagens[chaveInvalida]) || input.validationMessage;
  return false;
}

/**
 * Valida todos os campos elegíveis (`required`, `pattern`, `min`, `max`, `minlength`) de um formulário.
 *
 * @param {HTMLFormElement} form
 * @returns {boolean} true se todos os campos são válidos
 */
function validarFormulario(form) {
  const campos = form.querySelectorAll('[required], [pattern], [min], [max], [minlength]');
  let todosValidos = true;

  campos.forEach((campo) => {
    if (!validarCampo(campo)) {
      todosValidos = false;
    }
  });

  return todosValidos;
}

/**
 * Aplica erros de validação vindos do backend (`ApiError.fieldErrors`) aos mesmos elementos de
 * erro usados pela validação client-side.
 *
 * @param {HTMLFormElement} form
 * @param {Object<string,string>} fieldErrors mapa de nome de campo -> mensagem
 * @returns {void}
 */
function aplicarErrosDoServidor(form, fieldErrors) {
  Object.entries(fieldErrors || {}).forEach(([nomeCampo, mensagem]) => {
    const input = form.querySelector(`[name="${nomeCampo}"]`);
    if (!input) return;

    input.classList.add('has-error');
    let erroEl = input.parentElement.querySelector('.field-error');
    if (!erroEl) {
      erroEl = document.createElement('span');
      erroEl.className = 'field-error';
      input.parentElement.appendChild(erroEl);
    }
    erroEl.textContent = mensagem;
  });
}
