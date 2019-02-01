/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package gerador;

import gerador.sas.SASAction;
import gerador.sas.SASActionListener;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author danieloliveira
 */
public class Testador  implements SASActionListener{
    public void validarArquivo(String args[]) {
            try {
                //GeradorScriptSAS geradorScriptSAS = new GeradorScriptSAS("wisaspdin01.anatel.gov.br", 8591, "sasdevel", "Anatel123$");
                GeradorScriptSAS geradorScriptSAS = new GeradorScriptSAS("wisaspdin01.anatel.gov.br", 8591, "danieloliveira", "be51kitas.2");
                List<File> arquivosCSV = new ArrayList<>();
                arquivosCSV.add(new File(args[0]));
                geradorScriptSAS.addSASActionListener(this);
                List<Erro> erros = geradorScriptSAS.gerarScriptSAS("danieloliveira@anatel.gov.br",arquivosCSV, new File(args[1]));

                if (erros.size() > 0) {
                    System.err.println("Quantidade de erros encontrados: " + erros.size());
                    for (Erro erro : erros) {
                        System.err.println("método : " + erro.getMetodo() + " ; arquivo: " + erro.getArquivo() + " ; parametros : " + erro.getParametros() + " ; mensagem : " + erro.getMensagem() + " ; aceitavel : " + erro.isAceitavel());
                    }
                } else {
                    System.err.println("Nenhum erro encontrado");
                }

            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }

        @Override
        public void actionPerformed(SASAction e) {
            System.err.println("Ação: " + e.getAction());
            if(e.getAction() == 2){
                System.err.println(e.getScript());
            }
        }
}
