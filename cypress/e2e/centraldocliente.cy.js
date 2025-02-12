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
    cy.get('#cpfcnpj').type("49840018442"); // adicionei o cpf
    cy.get('form > .btn').should('be.visible').click(); // clicou em acessar
    cy.get('.filter-option-inner-inner').should('be.visible').click();
    cy.get('#bs-select-1-1 > .text').should('be.visible').click();
    cy.get('.btn-primary').should('be.visible').click();
    cy.get(':nth-child(19) > .nav-link').click();
  })

    it('Login com sucesso + botão termo de contrato e fazer download',() => {
      cy.get('#cpfcnpj').should('be.visible').click(); // Aguarda o campo CPF/CNPJ aparecer
      cy.get('#cpfcnpj').type("49840018442"); // adicionei o cpf
      cy.get('form > .btn').should('be.visible').click(); // clicou em acessar
      cy.get('.filter-option-inner-inner').should('be.visible').click();
      cy.get('#bs-select-1-1 > .text').should('be.visible').click();
      cy.get('.btn-primary').should('be.visible').click();
      cy.get(':nth-child(9) > .nav-link').should('be.visible').click();
      cy.get('.modal-body > .btn').click()
    })

    it.only(" login com sucesso + acessar fatura", () => {
      cy.get('#cpfcnpj').should('be.visible').click(); // Aguarda o campo CPF/CNPJ aparecer
      cy.get('#cpfcnpj').type("49840018442"); // adicionei o cpf
      cy.get('form > .btn').should('be.visible').click(); // clicou em acessar
      cy.get('.filter-option-inner-inner').should('be.visible').click();
      cy.get('#bs-select-1-1 > .text').should('be.visible').click();
      cy.get('.btn-primary').should('be.visible').click();
      cy.get(':nth-child(4) > .nav-link').should('be.visible').click()
      //cy.get('.btn-primary').should('be.visible').click()
      cy.get('.btn-info').scrollIntoView()
      cy.get('.btn-info').click()

    })
    
  

})

     
    

