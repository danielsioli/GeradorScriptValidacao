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
public class TestaGeradorScript {

    /**
     * Uso: java -jar SASValidacao.jar <arquivo_coleta_xml> <1|0>
     *
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println("Uso: java -jar SASValidacao.jar <arquivo_csv> <arquivo_coleta_xml>");
        } else {
            new Testador().validarArquivo(args);
        }
    }
}
