/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package gerador;

/**
 *
 * @author danieloliveira
 */
public class Erro {
    private String mensagem;
    private String arquivo;
    private String parametros;
    private String metodo;
    private boolean aceitavel;

    public Erro(String metodo, String mensagem, String arquivo, String parametros, boolean aceitavel) {
        this.metodo = metodo;
        this.mensagem = mensagem;
        this.arquivo = arquivo;
        this.parametros = parametros;
        this.aceitavel = aceitavel;
    }

    public String getMetodo() {
        return metodo;
    }
    
    public String getMensagem() {
        return mensagem;
    }

    public String getArquivo() {
        return arquivo;
    }

    public String getParametros() {
        return parametros;
    }

    public boolean isAceitavel() {
        return aceitavel;
    }
    
}
