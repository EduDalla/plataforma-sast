import { render, screen } from '@testing-library/react'
import '@testing-library/jest-dom/vitest'
import { describe, expect, it } from 'vitest'
import { App } from './main'

describe('formulário da análise', () => {
  it('exibe os campos e o botão de análise', () => {
    render(<App />)

    expect(screen.getByLabelText('URL do GitHub')).toBeInTheDocument()
    expect(screen.getByLabelText('Referência')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Analisar repositório' })).toBeEnabled()
  })
})
