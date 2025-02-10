describe('Central Agreste Link', () => {
  beforeEach(() => {
    // Faz login antes de cada teste
    cy.visit('https://agrestelink.sgp.tsmx.com.br/accounts/central/login?next=/central/home/')
    cy.get('#cpfcnpj').should('be.visible').click(); // Aguarda o campo CPF/CNPJ aparecer
    cy.get('#cpfcnpj').type("49840018442");
    cy.get('form > .btn').should('be.visible').click();
    cy.get('.filter-option-inner-inner').should('be.visible').click();
    cy.get('#bs-select-1-1 > .text').should('be.visible').click();
    cy.get('.btn-primary').should('be.visible').click();
  });

  it('Login com sucesso', () => {
    // Apenas verifica se o login foi bem-sucedido
    cy.get(':nth-child(5) > .nav-link').should('be.visible').click();
  });

  it('Verifica se abre a tela de faturas', () => {
    // Agora o login já foi feito no beforeEach(), então podemos navegar sem erro
    cy.get(':nth-child(4) > .nav-link').should('be.visible').click();
    
    // Voltar para a tela inicial
    cy.get('#menu > :nth-child(2) > .nav-link').should('be.visible').click();
    
    // Verificar faturas pagas
    cy.get(':nth-child(4) > .nav-link').should('be.visible').click();
    
    // Tela de faturas
    cy.get('[href="#pagos"]').should('be.visible').click();
  });
});
