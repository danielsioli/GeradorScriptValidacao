/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package gerador.sas;

/**
 * Exceção retornada em caso de erros de execução na classe {@link ConexaoSAS}.
 */
@SuppressWarnings("serial")
public class SASException extends Exception {

    private String msg;

    public SASException(String msg) {
        this.msg = msg;
    }

    @Override
    public String getMessage() {
        return msg;
    }
}
