import { describe, expect, it } from 'vitest'
import { opcoesEstadoCadastro } from './constants'

describe('estado nos cadastros', () => {
  it('oferece operacional e manutencao, sem falha, para ativos novos ou fora de falha', () => {
    expect(opcoesEstadoCadastro().map((o) => o.valor)).toEqual(['OPERACIONAL', 'MANUTENCAO'])
    expect(opcoesEstadoCadastro('MANUTENCAO').map((o) => o.valor)).toEqual(['OPERACIONAL', 'MANUTENCAO'])
  })

  it('mantem falha sem alternativa para um ativo em falha', () => {
    expect(opcoesEstadoCadastro('FALHA')).toEqual([{ valor: 'FALHA', label: 'Falha' }])
  })
})
