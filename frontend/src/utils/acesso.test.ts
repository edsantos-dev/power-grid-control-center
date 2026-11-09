import { describe, expect, it } from 'vitest'
import type { PerfilAcesso, Usuario } from '../types'
import { podeAcessarTela, podeCadastrarRede } from './acesso'

const como = (perfil: PerfilAcesso): Usuario => ({ id: 1, nome: 'Teste', email: 'teste@powergrid.local', perfil })

describe('telas por perfil', () => {
  it('restringe usinas e relatorios ao gerente', () => {
    for (const caminho of ['/usinas', '/relatorios']) {
      expect(podeAcessarTela(como('GERENTE_OPERACOES'), caminho)).toBe(true)
      expect(podeAcessarTela(como('OPERADOR'), caminho)).toBe(false)
      expect(podeAcessarTela(como('TECNICO_MANUTENCAO'), caminho)).toBe(false)
    }
  })

  it('libera as demais telas a qualquer perfil autenticado e nega sem usuario', () => {
    for (const perfil of ['GERENTE_OPERACOES', 'OPERADOR', 'TECNICO_MANUTENCAO'] as const) {
      expect(podeAcessarTela(como(perfil), '/equipes')).toBe(true)
      expect(podeAcessarTela(como(perfil), '/dashboard')).toBe(true)
    }
    expect(podeAcessarTela(null, '/dashboard')).toBe(false)
  })
})

describe('cadastro da rede', () => {
  it('permite a gerente e operador, nao ao tecnico nem sem usuario', () => {
    expect(podeCadastrarRede(como('GERENTE_OPERACOES'))).toBe(true)
    expect(podeCadastrarRede(como('OPERADOR'))).toBe(true)
    expect(podeCadastrarRede(como('TECNICO_MANUTENCAO'))).toBe(false)
    expect(podeCadastrarRede(null)).toBe(false)
  })
})
