/**
 * Protege páginas que exigem login. Deve ser carregado depois de `api.js` e antes de qualquer
 * script que dependa da sessão.
 */

/**
 * Garante que há uma sessão válida do tipo esperado; caso contrário, redireciona para o login.
 *
 * @param {'CLIENTE'|'PRESTADOR'|'ADMIN'} tipoEsperado tipo de conta exigido nesta página
 * @returns {{id: number, nome: string, tipo: string, statusKyc?: string}|null} o usuário autenticado, ou null (já redirecionando)
 */
function exigirSessao(tipoEsperado) {
  const sessao = TaskGoAPI.getSessaoValida();

  if (!sessao || !sessao.usuario || sessao.usuario.tipo !== tipoEsperado) {
    window.location.replace('login.html');
    return null;
  }

  return sessao.usuario;
}
