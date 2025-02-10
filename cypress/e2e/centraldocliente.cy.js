describe('template spec', () => {
    it('passes', () => {
      cy.visit('https://agrestelink.com.br')
      cy.get('.elementor-element-b3aa229 > .elementor-element > .elementor-widget-container > .elementor-button-wrapper > .elementor-button').click()
      
    })
  })