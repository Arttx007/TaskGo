let usuarioAtual = null;

document.addEventListener('DOMContentLoaded', async () => {
  usuarioAtual = exigirSessao('PRESTADOR');
  if (!usuarioAtual) return;

  const btnNovaEspecialidade = document.getElementById('btnNovaEspecialidade');
  btnNovaEspecialidade.addEventListener('click', abrirModalNovaEsp);

  document.querySelector('.welcome-text h1').textContent = `Olá, ${usuarioAtual.nome}! 👋`;

  const checkStatus = document.getElementById('check-status');
  checkStatus.checked = false;
  mudarStatus(checkStatus);

  await Promise.all([carregarPerfilEKyc(), carregarSolicitacoes(), carregarServicos(), carregarSaldo()]);
});

// ---------------------------------------------------------------------
// Navegação entre abas (inalterado)
// ---------------------------------------------------------------------
function mudarAba(event, idAba) {
  event.preventDefault();
  document.querySelectorAll('.sidebar-nav .nav-item').forEach((l) => l.classList.remove('active'));
  event.currentTarget.classList.add('active');

  document.querySelectorAll('.conteudo-aba').forEach((a) => a.classList.remove('active'));

  const abaAtiva = document.getElementById(idAba);
  abaAtiva.style.animation = 'none';
  abaAtiva.offsetHeight;
  abaAtiva.style.animation = null;
  abaAtiva.classList.add('active');
}

function mudarStatus(checkbox) {
  const t = document.getElementById('texto-disponibilidade');
  if (checkbox.checked) {
    t.innerText = 'Disponível';
    t.style.color = 'var(--success-green)';
    showToast('Você está ONLINE para novos pedidos!', 'success');
  } else {
    t.innerText = 'Ocupado';
    t.style.color = 'var(--danger-red)';
  }
}

function showToast(message, type = 'success') {
  const container = document.getElementById('toast-container');
  const toast = document.createElement('div');
  toast.style.cssText = `background: var(--card-bg); border: 1px solid var(--border-color); border-left: 4px solid ${type === 'error' ? 'var(--danger-red)' : 'var(--success-green)'}; color: white; padding: 16px 24px; border-radius: 12px; box-shadow: 0 10px 30px rgba(0,0,0,0.5); display: flex; align-items: center; gap: 12px; transform: translateX(120%); opacity: 0; transition: 0.4s; font-weight: 600; font-size: 14px;`;
  const icone = type === 'error' ? 'fa-circle-exclamation' : 'fa-check-circle';
  const cor = type === 'error' ? 'var(--danger-red)' : 'var(--success-green)';
  toast.innerHTML = `<i class="fas ${icone}" style="color: ${cor}; font-size: 20px;"></i> <span>${message}</span>`;
  container.appendChild(toast);
  setTimeout(() => { toast.style.transform = 'translateX(0)'; toast.style.opacity = '1'; }, 10);
  setTimeout(() => { toast.style.transform = 'translateX(120%)'; toast.style.opacity = '0'; setTimeout(() => toast.remove(), 400); }, 3500);
}

function mensagemDeErro(erro) {
  return erro instanceof TaskGoAPI.ApiError ? erro.message : 'Ocorreu um erro inesperado. Tente novamente.';
}

function formatarMoeda(valor) {
  return 'R$ ' + Number(valor || 0).toFixed(2).replace('.', ',');
}

// ---------------------------------------------------------------------
// Perfil e KYC (US-01, RN04)
// ---------------------------------------------------------------------
async function carregarPerfilEKyc() {
  try {
    const prestador = await TaskGoAPI.obterPrestador(usuarioAtual.id);
    const abaServicos = document.getElementById('aba-servicos');
    let banner = document.getElementById('kycBanner');

    if (prestador.statusKyc !== 'APROVADO') {
      if (!banner) {
        banner = document.createElement('div');
        banner.id = 'kycBanner';
        banner.className = 'glass-card';
        banner.style.cssText = 'margin-bottom: 24px; border-left: 4px solid var(--warning-yellow); display: flex; align-items: center; gap: 16px;';
        abaServicos.insertBefore(banner, abaServicos.firstChild);
      }
      const mensagem = prestador.statusKyc === 'REJEITADO'
        ? 'Seu cadastro foi rejeitado na verificação. Reenvie seus documentos para publicar serviços.'
        : 'Seu cadastro está em análise. Você poderá publicar serviços assim que a verificação (KYC) for aprovada.';
      banner.innerHTML = `<i class="fas fa-clock" style="color: var(--warning-yellow); font-size: 22px;"></i>
        <div><strong>Verificação pendente</strong><p style="color: var(--text-gray); font-size: 13px; margin-top: 4px;">${mensagem}</p></div>
        <a href="cadastro-kyc.html?prestadorId=${usuarioAtual.id}" class="btn-outline" style="margin-left: auto; text-decoration:none;">Reenviar documentos</a>`;

      const btnNovaEsp = document.getElementById('btnNovaEspecialidade');
      if (btnNovaEsp) {
        btnNovaEsp.dataset.kycBlocked = 'true';
        btnNovaEsp.setAttribute('aria-disabled', 'true');
        btnNovaEsp.title = 'Conclua a verificação do cadastro para adicionar especialidades.';
        btnNovaEsp.setAttribute('aria-describedby', 'kycBanner');
      }
    } else if (banner) {
      banner.remove();
      const btnNovaEsp = document.getElementById('btnNovaEspecialidade');
      if (btnNovaEsp) {
        delete btnNovaEsp.dataset.kycBlocked;
        btnNovaEsp.removeAttribute('aria-disabled');
        btnNovaEsp.removeAttribute('title');
        btnNovaEsp.removeAttribute('aria-describedby');
      }
    }
  } catch (erro) {
    showToast(mensagemDeErro(erro), 'error');
  }
}

// ---------------------------------------------------------------------
// Solicitações (US-05, US-07, US-10)
// ---------------------------------------------------------------------
async function carregarSolicitacoes() {
  try {
    const solicitacoes = await TaskGoAPI.listarSolicitacoes();
    renderizarSolicitacoesNovas(solicitacoes.filter((s) => s.status === 'SOLICITADO'));
    renderizarHistorico(solicitacoes.filter((s) => s.status !== 'SOLICITADO'));
  } catch (erro) {
    showToast(mensagemDeErro(erro), 'error');
  }
}

function renderizarSolicitacoesNovas(lista) {
  const container = document.getElementById('requests-container');
  const contador = document.getElementById('request-count');

  contador.style.display = '';
  contador.innerText = lista.length;

  if (lista.length === 0) {
    container.innerHTML = `
      <div style="grid-column: 1 / -1; text-align: center; padding: 40px; background: rgba(255,255,255,0.02); border-radius: 16px; border: 1px dashed var(--border-color);">
        <i class="fas fa-check-circle" style="font-size: 40px; color: var(--success-green); margin-bottom: 15px;"></i>
        <h3 style="font-size: 18px; margin-bottom: 5px; color: white;">Tudo limpo!</h3>
        <p style="color: var(--text-gray);">Você não tem mais novas solicitações pendentes.</p>
      </div>`;
    contador.style.display = 'none';
    return;
  }

  container.innerHTML = '';
  lista.forEach((solicitacao) => {
    const card = document.createElement('div');
    card.className = 'request-card';
    card.innerHTML = `
      <div class="req-header"><span class="req-status"><i class="fas fa-bolt"></i> NOVO</span></div>
      <h3>${solicitacao.categoria || 'Serviço'}</h3>
      <div class="req-details"><p><i class="fas fa-user"></i> ${solicitacao.clienteNome}</p></div>
      <div class="ia-price-box"><i class="fas fa-tag"></i> ${formatarMoeda(solicitacao.valor)}</div>
      <div class="req-actions">
        <button class="btn-primary btn-aceitar"><i class="fas fa-check"></i> Aceitar Pedido</button>
        <button class="btn-outline btn-recusar">Recusar</button>
      </div>`;

    card.querySelector('.btn-aceitar').addEventListener('click', (e) => aceitarServico(e.target, solicitacao.id));
    card.querySelector('.btn-recusar').addEventListener('click', (e) => recusarServico(e.target, solicitacao.id));
    container.appendChild(card);
  });
}

async function aceitarServico(botao, solicitacaoId) {
  botao.disabled = true;
  botao.innerHTML = '<i class="fas fa-circle-notch fa-spin"></i>';
  try {
    await TaskGoAPI.aceitarSolicitacao(solicitacaoId);
    showToast('Serviço aceito! O pedido foi movido para o histórico em andamento.', 'success');
    await carregarSolicitacoes();
  } catch (erro) {
    showToast(mensagemDeErro(erro), 'error');
    botao.disabled = false;
    botao.innerHTML = '<i class="fas fa-check"></i> Aceitar Pedido';
  }
}

async function recusarServico(botao, solicitacaoId) {
  botao.disabled = true;
  try {
    await TaskGoAPI.recusarSolicitacao(solicitacaoId);
    showToast('Solicitação recusada.', 'success');
    await carregarSolicitacoes();
  } catch (erro) {
    showToast(mensagemDeErro(erro), 'error');
    botao.disabled = false;
  }
}

const ROTULOS_STATUS = {
  ACEITO: { texto: 'Em Andamento', classe: 'pill-progress' },
  CONCLUIDO: { texto: 'Concluído', classe: 'pill-success' },
  AVALIADO: { texto: 'Concluído', classe: 'pill-success' },
  CANCELADO: { texto: 'Cancelado', classe: 'pill-danger' },
  RECUSADO: { texto: 'Recusado', classe: 'pill-danger' },
};

function renderizarHistorico(lista) {
  const corpo = document.querySelector('#history-table tbody');
  corpo.innerHTML = '';

  if (lista.length === 0) {
    corpo.innerHTML = '<tr><td colspan="5" style="text-align:center; color: var(--text-gray);">Nenhum atendimento no histórico ainda.</td></tr>';
    return;
  }

  lista.forEach((solicitacao) => {
    const rotulo = ROTULOS_STATUS[solicitacao.status] || { texto: solicitacao.status, classe: '' };
    const linha = document.createElement('tr');

    const acao = solicitacao.status === 'ACEITO'
      ? `<button class="btn-primary btn-concluir" style="padding: 6px 12px; font-size: 12px;">Concluir Atendimento</button>`
      : '';

    linha.innerHTML = `<td>${solicitacao.categoria || 'Serviço'}</td><td>${solicitacao.clienteNome}</td><td>—</td><td><strong>${formatarMoeda(solicitacao.valor)}</strong></td><td><span class="status-pill ${rotulo.classe}">${rotulo.texto}</span> ${acao}</td>`;

    const btnConcluir = linha.querySelector('.btn-concluir');
    if (btnConcluir) {
      btnConcluir.addEventListener('click', () => concluirAtendimento(btnConcluir, solicitacao.id));
    }

    corpo.appendChild(linha);
  });
}

async function concluirAtendimento(botao, solicitacaoId) {
  botao.disabled = true;
  botao.innerHTML = '<i class="fas fa-circle-notch fa-spin"></i>';
  try {
    await TaskGoAPI.concluirSolicitacao(solicitacaoId);
    showToast('Atendimento concluído! O valor foi creditado no seu saldo.', 'success');
    await Promise.all([carregarSolicitacoes(), carregarSaldo()]);
  } catch (erro) {
    showToast(mensagemDeErro(erro), 'error');
    botao.disabled = false;
    botao.innerHTML = 'Concluir Atendimento';
  }
}

// ---------------------------------------------------------------------
// Meus Serviços / Portfólio (US-02)
// ---------------------------------------------------------------------
async function carregarServicos() {
  try {
    const servicos = await TaskGoAPI.listarMeusServicos();
    renderizarServicos(servicos);
  } catch (erro) {
    showToast(mensagemDeErro(erro), 'error');
  }
}

function renderizarServicos(lista) {
  const grid = document.querySelector('.portfolio-grid');
  grid.innerHTML = '';

  if (lista.length === 0) {
    grid.innerHTML = `<div style="grid-column: 1 / -1; text-align: center; padding: 40px; color: var(--text-gray);">Você ainda não publicou nenhum serviço. Clique em "Nova Especialidade" para começar.</div>`;
    return;
  }

  lista.forEach((servico) => grid.appendChild(criarCardServico(servico)));
}

function criarCardServico(servico) {
  const card = document.createElement('div');
  card.className = 'portfolio-card manage-card' + (servico.status === 'INATIVO' ? ' disabled-card' : '');
  card.dataset.servicoId = servico.id;

  card.innerHTML = `
    <div class="srv-card-top">
      <div class="srv-icon-bg srv-icon-blue"><i class="fas fa-briefcase"></i></div>
      <div class="srv-header-content">
        <div class="srv-header-topline">
          <div class="srv-badges"><span class="service-badge badge-manutencao">${servico.categoria}</span></div>
          <label class="switch-sm"><input type="checkbox" class="input-ativo" ${servico.status === 'ATIVO' ? 'checked' : ''}><span class="slider-sm"></span></label>
        </div>
        <h3 class="srv-title">${servico.categoria}</h3>
        <p class="srv-desc">${servico.descricao || ''}</p>
      </div>
    </div>
    <div class="srv-inputs-grid">
      <div class="srv-input-col">
        <label>Valor Base</label>
        <div class="srv-input-wrapper price-input"><span>R$</span><input type="number" class="input-preco" value="${servico.preco}" min="0.01" step="0.01"></div>
      </div>
    </div>
    <div class="srv-actions">
      <button class="btn-edit-service"><i class="fas fa-save"></i> Salvar Preço</button>
      <button class="btn-trash" title="Excluir especialidade"><i class="fas fa-trash-alt"></i></button>
    </div>`;

  card.querySelector('.input-ativo').addEventListener('change', (e) => toggleServico(e.target, servico));
  card.querySelector('.btn-edit-service').addEventListener('click', () => salvarEdicaoServico(card, servico));
  card.querySelector('.btn-trash').addEventListener('click', () => excluirServico(card, servico));

  return card;
}

async function toggleServico(checkbox, servico) {
  const card = checkbox.closest('.manage-card');
  const ativo = checkbox.checked;
  checkbox.disabled = true;
  try {
    await TaskGoAPI.alternarServicoAtivo(servico.id, ativo);
    card.classList.toggle('disabled-card', !ativo);
    servico.status = ativo ? 'ATIVO' : 'INATIVO';
  } catch (erro) {
    checkbox.checked = !ativo;
    showToast(mensagemDeErro(erro), 'error');
  } finally {
    checkbox.disabled = false;
  }
}

async function salvarEdicaoServico(card, servico) {
  const preco = parseFloat(card.querySelector('.input-preco').value);
  if (!preco || preco <= 0) {
    showToast('Informe um preço válido.', 'error');
    return;
  }
  try {
    await TaskGoAPI.atualizarServico(servico.id, {
      categoria: servico.categoria,
      descricao: servico.descricao,
      preco,
    });
    servico.preco = preco;
    showToast('Preço atualizado.', 'success');
  } catch (erro) {
    showToast(mensagemDeErro(erro), 'error');
  }
}

async function excluirServico(card, servico) {
  if (!confirm(`Remover "${servico.categoria}" do seu portfólio?`)) return;
  try {
    await TaskGoAPI.excluirServico(servico.id);
    card.remove();
    showToast('Especialidade removida.', 'success');
  } catch (erro) {
    showToast(mensagemDeErro(erro), 'error');
  }
}

function abrirModalNovaEsp() {
  const btnNovaEsp = document.getElementById('btnNovaEspecialidade');
  if (btnNovaEsp?.dataset.kycBlocked === 'true') {
    showToast('Conclua a verificação do cadastro para adicionar especialidades.', 'error');
    return;
  }

  const modal = document.getElementById('modalNovaEsp');
  if (!modal) {
    showToast('Não foi possível abrir o formulário. Atualize a página e tente novamente.', 'error');
    return;
  }

  modal.style.display = 'flex';
  modal.querySelector('select')?.focus();
}

function closeModal(id) {
  document.getElementById(id).style.display = 'none';
}

window.onclick = function (e) {
  if (e.target.classList.contains('modal-overlay')) e.target.style.display = 'none';
};

async function salvarNovaEspecialidade(event) {
  event.preventDefault();
  const form = event.target;
  const btn = form.querySelector('button[type="submit"]');
  const [categoriaSelect, nomeInput, descricaoInput] = form.querySelectorAll('.form-group select, .form-group input[type="text"], .form-group textarea');
  const precoInput = form.querySelector('.form-row input[type="number"]');

  const categoria = nomeInput.value.trim() || categoriaSelect.value;
  const descricao = descricaoInput.value.trim();
  const preco = parseFloat(precoInput.value);

  if (!categoria || !preco || preco <= 0) {
    showToast('Preencha categoria e um valor base válido.', 'error');
    return;
  }

  btn.innerHTML = '<i class="fas fa-circle-notch fa-spin"></i> Salvando...';
  btn.disabled = true;

  try {
    const novoServico = await TaskGoAPI.criarServico({ categoria, descricao, preco });
    const grid = document.querySelector('.portfolio-grid');
    if (grid.children.length === 1 && grid.textContent.includes('ainda não publicou')) {
      grid.innerHTML = '';
    }
    grid.appendChild(criarCardServico(novoServico));

    closeModal('modalNovaEsp');
    showToast('Nova especialidade adicionada ao seu portfólio!', 'success');
    form.reset();
  } catch (erro) {
    showToast(mensagemDeErro(erro), 'error');
  } finally {
    btn.innerHTML = '<i class="fas fa-save"></i> Salvar Especialidade';
    btn.disabled = false;
  }
}

// ---------------------------------------------------------------------
// Foto de perfil (simulado — fora do escopo do MVP)
// ---------------------------------------------------------------------
let cropper = null;
const fileInput = document.getElementById('fileInput');
const modalCrop = document.getElementById('cropModal');
const imageToCrop = document.getElementById('imageToCrop');
const picPreview = document.getElementById('picPreview');
const picIcon = document.getElementById('picIcon');
const picImage = document.getElementById('picImage');
const uploadStatus = document.getElementById('uploadStatus');
const spinner = document.getElementById('uploadSpinner');
const successIcon = document.getElementById('uploadSuccess');
const btnAlterar = document.getElementById('btnAlterarFoto');
const btnRemover = document.getElementById('btnRemoverFoto');
const headerProfilePic = document.getElementById('headerProfilePic');

function triggerFileUpload() {
  if (spinner.style.display === 'block') return;
  fileInput.click();
}

fileInput.addEventListener('change', (e) => {
  const files = e.target.files;
  if (files && files.length > 0) {
    const file = files[0];
    if (file.size > 5 * 1024 * 1024) {
      showToast('A imagem excede o limite de 5MB.', 'error');
      fileInput.value = '';
      return;
    }

    const reader = new FileReader();
    reader.onload = (event) => {
      imageToCrop.src = event.target.result;
      modalCrop.style.display = 'flex';

      if (cropper) cropper.destroy();
      cropper = new Cropper(imageToCrop, {
        aspectRatio: 1,
        viewMode: 1,
        dragMode: 'move',
        guides: false,
        cropBoxResizable: false,
        toggleDragModeOnDblclick: false,
        ready: function () {
          document.querySelector('.cropper-view-box').style.borderRadius = '50%';
          document.querySelector('.cropper-face').style.borderRadius = '50%';
        },
      });
    };
    reader.readAsDataURL(file);
  }
});

function closeCropModal() {
  modalCrop.style.display = 'none';
  if (cropper) cropper.destroy();
  fileInput.value = '';
}

function cropAndSave() {
  if (!cropper) return;
  const canvas = cropper.getCroppedCanvas({ width: 200, height: 200 });
  const base64Image = canvas.toDataURL('image/jpeg');
  closeCropModal();

  uploadStatus.style.display = 'flex';
  spinner.style.display = 'block';
  successIcon.style.display = 'none';
  picPreview.style.filter = 'blur(1px)';

  setTimeout(() => {
    spinner.style.display = 'none';
    picIcon.style.display = 'none';
    picImage.src = base64Image;
    picImage.style.display = 'block';
    picPreview.style.filter = 'none';
    successIcon.style.display = 'block';

    headerProfilePic.innerHTML = `<img src="${base64Image}" style="width:100%; height:100%; object-fit:cover; border-radius:50%;">`;
    headerProfilePic.style.border = '2px solid var(--primary-blue)';

    btnAlterar.innerText = 'Alterar Foto';
    btnRemover.style.display = 'flex';

    setTimeout(() => {
      uploadStatus.style.display = 'none';
      successIcon.style.display = 'none';
    }, 1500);
  }, 2000);
}

function removerFoto() {
  if (confirm('Atenção: Deseja realmente remover sua foto de perfil?')) {
    picImage.style.display = 'none';
    picIcon.style.display = 'block';

    headerProfilePic.innerHTML = `<i class="fas fa-user"></i>`;
    headerProfilePic.style.border = '2px solid var(--border-color)';

    btnAlterar.innerText = 'Adicionar Foto';
    btnRemover.style.display = 'none';
  }
}

// ---------------------------------------------------------------------
// Financeiro / Saque Pix (US-08)
// ---------------------------------------------------------------------
let saldoAtual = 0;

async function carregarSaldo() {
  try {
    const carteira = await TaskGoAPI.obterSaldoPrestador(usuarioAtual.id);
    saldoAtual = Number(carteira.saldoDisponivel || 0);
    document.getElementById('saldo-dashboard').innerText = formatarMoeda(saldoAtual);
  } catch (erro) {
    showToast(mensagemDeErro(erro), 'error');
  }
}

function abrirModalSaque() {
  if (saldoAtual <= 0) {
    showToast('Seu saldo está zerado. Não há valores disponíveis para sacar.', 'error');
    return;
  }
  document.getElementById('saldo-modal-texto').innerText = formatarMoeda(saldoAtual);
  document.getElementById('modalSaque').style.display = 'flex';
  document.getElementById('valorSaque').value = '';
}

function preencherValorMaximo() {
  document.getElementById('valorSaque').value = saldoAtual.toFixed(2);
}

async function processarSaquePix() {
  const btn = document.getElementById('btnConfirmarSaque');
  const valorInput = parseFloat(document.getElementById('valorSaque').value);

  if (!valorInput || isNaN(valorInput) || valorInput <= 0) {
    showToast('Digite um valor válido para sacar.', 'error');
    return;
  }

  btn.innerHTML = '<i class="fas fa-circle-notch fa-spin"></i> Processando PIX...';
  btn.disabled = true;

  try {
    const resultado = await TaskGoAPI.solicitarSaque(usuarioAtual.id, valorInput);
    saldoAtual = Number(resultado.saldoRestante);

    document.getElementById('saldo-dashboard').innerText = formatarMoeda(saldoAtual);
    document.getElementById('saldo-modal-texto').innerText = formatarMoeda(saldoAtual);

    showToast(`Saque de ${formatarMoeda(valorInput)} realizado com sucesso!`, 'success');
    setTimeout(() => {
      document.getElementById('modalSaque').style.display = 'none';
      btn.innerHTML = '<i class="fas fa-paper-plane"></i> Transferir Agora';
      btn.disabled = false;
    }, 1000);
  } catch (erro) {
    showToast(mensagemDeErro(erro), 'error');
    btn.innerHTML = '<i class="fas fa-paper-plane"></i> Transferir Agora';
    btn.disabled = false;
  }
}

// ---------------------------------------------------------------------
// FAQ, chat IA (decorativo — fora do escopo do MVP)
// ---------------------------------------------------------------------
function toggleFaq(element) {
  const faqItem = element.parentElement;
  const isActive = faqItem.classList.contains('active');
  document.querySelectorAll('.faq-item').forEach((item) => item.classList.remove('active'));
  if (!isActive) faqItem.classList.add('active');
}

function abrirChatIA() {
  document.getElementById('modalChatIA').style.display = 'flex';
}

function enviarMensagemIA() {
  const input = document.getElementById('inputChatIA');
  const mensagem = input.value.trim();
  if (!mensagem) return;

  const history = document.getElementById('chatHistoryIA');

  history.innerHTML += `
    <div style="align-self: flex-end; background: var(--primary-blue); color: white; padding: 12px 16px; border-radius: 12px 0 12px 12px; max-width: 85%; font-size: 13px; line-height: 1.5; animation: popIn 0.3s;">
      ${mensagem}
    </div>
  `;
  input.value = '';
  history.scrollTop = history.scrollHeight;

  const typingId = 'typing-ia-' + Date.now();
  history.innerHTML += `
    <div id="${typingId}" style="align-self: flex-start; background: var(--card-bg); border: 1px solid var(--border-color); padding: 12px 16px; border-radius: 0 12px 12px 12px; max-width: 85%; font-size: 13px; color: var(--text-gray); animation: popIn 0.3s;">
      <i class="fas fa-circle-notch fa-spin" style="color: var(--primary-blue);"></i> Gerando resposta...
    </div>
  `;
  history.scrollTop = history.scrollHeight;

  setTimeout(() => {
    document.getElementById(typingId).remove();

    history.innerHTML += `
      <div style="align-self: flex-start; background: var(--card-bg); border: 1px solid var(--border-color); padding: 12px 16px; border-radius: 0 12px 12px 12px; max-width: 85%; font-size: 13px; line-height: 1.5; color: var(--text-white); animation: popIn 0.3s;">
        Entendi que você tem uma dúvida sobre <strong>"${mensagem}"</strong>. <br><br>Como sou um assistente virtual em demonstração, ainda estou aprendendo! Mas você pode encontrar respostas sobre taxas e saques na seção de Dúvidas Frequentes logo aqui atrás. Posso ajudar com mais alguma coisa? 🤖
      </div>
    `;
    history.scrollTop = history.scrollHeight;
  }, 1500);
}
