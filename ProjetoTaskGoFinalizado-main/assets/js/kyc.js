const KYC_TIPOS_ACEITOS = ['image/png', 'image/jpeg', 'application/pdf'];
const KYC_TAMANHO_MAXIMO = 5 * 1024 * 1024;

let kycPrestadorId = null;
let arquivoIdentidade = null;
let arquivoPix = null;

document.addEventListener('DOMContentLoaded', () => {
  // sessionStorage é a fonte primária: servidores estáticos como `serve` (sugerido no CLAUDE.md)
  // redirecionam .html?query para uma URL "limpa" sem querystring, perdendo o prestadorId antes
  // desta página conseguir lê-lo — a query string fica só como fallback para acesso direto/link.
  const params = new URLSearchParams(window.location.search);
  kycPrestadorId = sessionStorage.getItem('taskgo_kyc_prestador_id') || params.get('prestadorId');

  if (!kycPrestadorId) {
    window.location.replace('cadastro.html');
    return;
  }

  document.getElementById('inputIdentidade').addEventListener('change', (e) => {
    arquivoIdentidade = selecionarArquivo(e, 'uploadIdentidade', 'nomeIdentidade');
  });
  document.getElementById('inputPix').addEventListener('change', (e) => {
    arquivoPix = selecionarArquivo(e, 'uploadPix', 'nomePix');
  });
});

/**
 * Valida o arquivo escolhido e atualiza a zona de upload correspondente.
 *
 * @param {Event} event evento de `change` do `<input type="file">`
 * @param {string} zonaId id do elemento `.kyc-upload`
 * @param {string} nomeId id do elemento onde o nome do arquivo é exibido
 * @returns {File|null} o arquivo válido, ou null se inválido/nenhum selecionado
 */
function selecionarArquivo(event, zonaId, nomeId) {
  const input = event.target;
  const zona = document.getElementById(zonaId);
  const nomeEl = document.getElementById(nomeId);
  const arquivo = input.files && input.files[0];

  if (!arquivo) return null;

  if (!KYC_TIPOS_ACEITOS.includes(arquivo.type)) {
    zona.classList.remove('is-ok');
    nomeEl.textContent = 'Formato inválido. Envie PNG, JPEG ou PDF.';
    input.value = '';
    return null;
  }

  if (arquivo.size > KYC_TAMANHO_MAXIMO) {
    zona.classList.remove('is-ok');
    nomeEl.textContent = 'Arquivo muito grande. Tamanho máximo: 5MB.';
    input.value = '';
    return null;
  }

  zona.classList.add('is-ok');
  nomeEl.textContent = arquivo.name;
  return arquivo;
}

/**
 * Envia os dois documentos de KYC para o backend.
 *
 * @returns {Promise<void>}
 */
async function enviarDocumentos() {
  const erroEl = document.getElementById('kycErro');
  erroEl.textContent = '';

  if (!arquivoIdentidade || !arquivoPix) {
    erroEl.textContent = 'Selecione os dois documentos antes de enviar.';
    return;
  }

  const botao = document.getElementById('btnEnviarKyc');
  const textoOriginal = botao.innerHTML;
  botao.innerHTML = '<i class="fas fa-circle-notch fa-spin"></i> Enviando...';
  botao.disabled = true;

  try {
    await TaskGoAPI.enviarDocumentosKyc(kycPrestadorId, arquivoIdentidade, arquivoPix);
    document.getElementById('kycForm').style.display = 'none';
    document.getElementById('kycStatus').style.display = 'block';
  } catch (erro) {
    erroEl.textContent = erro instanceof TaskGoAPI.ApiError ? erro.message : 'Não foi possível enviar os documentos. Tente novamente.';
    botao.innerHTML = textoOriginal;
    botao.disabled = false;
  }
}
