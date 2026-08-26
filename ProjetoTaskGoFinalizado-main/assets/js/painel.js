// ==========================================
        // CONFIGURAÇÕES GLOBAIS E INICIALIZAÇÃO
        // ==========================================
        document.addEventListener('DOMContentLoaded', () => {
            // Inicializa o status do botão (começar como 'Ocupado' vermelho)
            const checkStatus = document.getElementById('check-status');
            checkStatus.checked = false; // Garante que comece desmarcado
            mudarStatus(checkStatus);
        });

        // ==========================================
        // 1. LÓGICA DE NAVEGAÇÃO DE ABAS (Pode manter sua função existente)
        // ==========================================
        function mudarAba(event, idAba) {
            event.preventDefault();
            document.querySelectorAll('.sidebar-nav .nav-item').forEach(link => { link.classList.remove('active'); });
            event.currentTarget.classList.add('active');
            document.querySelectorAll('.conteudo-aba').forEach(aba => { aba.classList.remove('active'); });
            const abaAtiva = document.getElementById(idAba);
            abaAtiva.style.animation = 'none'; // Reseta animação
            abaAtiva.offsetHeight; // trigger reflow
            abaAtiva.style.animation = null; // Re-aplica animação
            abaAtiva.classList.add('active');
        }

        // ==========================================
        // 2. LÓGICA DO STATUS OCUPADO/DISPONÍVEL (Sua função existente)
        // ==========================================
        function mudarStatus(checkbox) {
            const textoStatus = document.getElementById('texto-disponibilidade');
            if (checkbox.checked) {
                textoStatus.innerText = "Disponível";
                textoStatus.style.color = "var(--success-green)";
            } else {
                textoStatus.innerText = "Ocupado";
                textoStatus.style.color = "var(--danger-red)";
            }
        }

        // ==========================================
        // 3. LÓGICA COMPLEXA DA FOTO DE PERFIL (A Parte Nova)
        // ==========================================
        
        // --- Definições Globais ---
        let cropper = null; // Instância do Cropper.js
        const maxFileSize = 5 * 1024 * 1024; // 5MB em bytes
        const allowedTypes = ['image/jpeg', 'image/png', 'image/gif'];
        
        // Elementos do DOM (Cache para performance)
        const fileInput = document.getElementById('fileInput');
        const modal = document.getElementById('cropModal');
        const imageToCrop = document.getElementById('imageToCrop');
        const picPreview = document.getElementById('picPreview');
        const picIcon = document.getElementById('picIcon');
        const picImage = document.getElementById('picImage');
        const uploadStatus = document.getElementById('uploadStatus');
        const spinner = document.getElementById('uploadSpinner');
        const successIcon = document.getElementById('uploadSuccess');
        const errorIcon = document.getElementById('uploadError');
        const btnAlterar = document.getElementById('btnAlterarFoto');
        const btnRemover = document.getElementById('btnRemoverFoto');

        // --- Função A: Clicar no Circle ou Botão ---
        function triggerFileUpload() {
            // Se já tiver animação de upload rodando, não deixa clicar dnv
            if (spinner.style.display === 'block') return;
            fileInput.click();
        }

        // --- Função B: Evento ao selecionar o arquivo ---
        fileInput.addEventListener('change', (e) => {
            const files = e.target.files;
            
            if (files && files.length > 0) {
                const file = files[0];
                
                // Validação de Tamanho e Tipo (Complexidade)
                if (!allowedTypes.includes(file.type)) {
                    alert('Por favor, selecione uma imagem JPG, PNG ou GIF.');
                    fileInput.value = ''; // Limpa o input
                    return;
                }
                
                if (file.size > maxFileSize) {
                    alert('A imagem é muito grande! O tamanho máximo é 5MB.');
                    fileInput.value = ''; // Limpa o input
                    return;
                }

                // Se passou na validação, abre o modal de corte
                openCropModal(file);
            }
        });

        // --- Função C: Abrir Modal e Ativar Cropper ---
        function openCropModal(file) {
            const reader = new FileReader();
            
            reader.onload = (event) => {
                imageToCrop.src = event.target.result; // Define a imagem no modal
                modal.style.display = 'flex'; // Abre o modal
                
                // Inicializa o Cropper.js na imagem
                // Configuramos para ser uma área de corte circular
                if (cropper) { cropper.destroy(); } // Destroi instância anterior se houver
                
                cropper = new Cropper(imageToCrop, {
                    aspectRatio: 1, // Força corte quadrado (para o círculo ser perfeito)
                    viewMode: 1, // Restringe a imagem dentro do container
                    dragMode: 'move', // Permite arrastar a imagem
                    guides: false, // Esconde guias para visual mais limpo
                    cropBoxResizable: false, // Não permite redimensionar o quadrado
                    toggleDragModeOnDblclick: false,
                    responsive: true,
                    ready: function () {
                        // Isso aqui força a visualização do cropper a ser circular no CSS
                        document.querySelector('.cropper-view-box').style.borderRadius = '50%';
                        document.querySelector('.cropper-face').style.borderRadius = '50%';
                    }
                });
            };
            
            reader.readAsDataURL(file); // Lê o arquivo como base64
        }

        // --- Função D: Cortar e Simular Salvar ---
        function cropAndSave() {
            if (!cropper) return;
            
            // Pega o resultado do corte (em canvas, no formato 100x100)
            const croppedCanvas = cropper.getCroppedCanvas({
                width: 100,
                height: 100
            });
            
            // Converte o canvas para imagem em Base64 para pré-visualização instantânea
            const croppedImageBase64 = croppedCanvas.toDataURL('image/jpeg');
            
            // Fecha o modal e inicia o feedback visual de upload
            closeCropModal();
            iniciarFeedBackUpload(croppedImageBase64);
        }

        // --- Função E: Cancelar Corte ---
        function closeCropModal() {
            modal.style.display = 'none';
            if (cropper) {
                cropper.destroy();
                cropper = null;
            }
            fileInput.value = ''; // Limpa o input
        }

        // --- Função F: Simular Upload e Feedback (Complexidade) ---
        function iniciarFeedBackUpload(imageBase64) {
            // Mostra o spinner
            uploadStatus.style.display = 'flex';
            spinner.style.display = 'block';
            successIcon.style.display = 'none';
            errorIcon.style.display = 'none';
            picPreview.style.filter = "blur(1px)";

            // Simula um delay de upload (ex: 2 segundos)
            setTimeout(() => {
                // Esconde spinner, mostra imagem cortada
                spinner.style.display = 'none';
                
                picIcon.style.display = 'none'; // Esconde ícone padrão
                picImage.src = imageBase64; // Mostra imagem cortada
                picImage.style.display = 'block';
                picPreview.style.filter = "none";
                
                // Mostra check de sucesso
                successIcon.style.display = 'block';
                
                // Atualiza os botões (agora que tem foto)
                btnAlterar.innerText = "Alterar Foto";
                btnRemover.style.display = 'flex';

                // Some com o check de sucesso após 2 segundos
                setTimeout(() => {
                    uploadStatus.style.display = 'none';
                    successIcon.style.display = 'none';
                    alert('✅ Foto de perfil atualizada com sucesso!');
                }, 1500);

            }, 2000); // 2 segundos de delay simulado
        }

        // --- Função G: Remover Foto ---
        document.getElementById('btnRemoverFoto').addEventListener('click', () => {
            if (confirm('Deseja realmente remover sua foto de perfil?')) {
                picImage.style.display = 'none';
                picIcon.style.display = 'block'; // Volta ícone padrão
                
                btnAlterar.innerText = "Adicionar Foto";
                btnRemover.style.display = 'none';
                
                alert('Foto de perfil removida.');
            }
        });