describe('Central Agreste Link', () => {
  beforeEach(() => {
      // Faz login antes de cada teste
    cy.visit('https://agrestelink.sgp.tsmx.com.br/accounts/central/login?next=/central/home/')
  })

  it('Login com sucesso', () => {
    // Apenas verifica se o login foi bem-sucedido
    cy.get('#cpfcnpj').should('be.visible').click(); // Aguarda o campo CPF/CNPJ aparecer
    cy.get('#cpfcnpj').type("49840018442");
    cy.get('form > .btn').should('be.visible').click();
    cy.get('.filter-option-inner-inner').should('be.visible').click();
    cy.get('#bs-select-1-1 > .text').should('be.visible').click();
    cy.get('.btn-primary').should('be.visible').click();
  })

  it('Login com sucesso e botão contatos', () => {
    cy.get('#cpfcnpj').should('be.visible').click(); // Aguarda o campo CPF/CNPJ aparecer
    cy.get('#cpfcnpj').type("49840018442");
    cy.get('form > .btn').should('be.visible').click();
    cy.get('.filter-option-inner-inner').should('be.visible').click();
    cy.get('#bs-select-1-1 > .text').should('be.visible').click();
    cy.get('.btn-primary').should('be.visible').click();
    cy.get(':nth-child(19) > .nav-link').click()
    
  })

})

     
    

