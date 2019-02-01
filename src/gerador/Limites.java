/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package gerador;

import java.util.ArrayList;
import java.util.List;

/**
 * Define os limites a serem utilizados na validação dos dados
 * @author danieloliveira
 */
public class Limites {

    /**
     * Array dos limites a serem utilizados
     */
    private List<Limite> limites = new ArrayList<>();

    /**
     * Adiciona um limite a lista de limites. O dado que estiver entre inicio e fim deverá ter um crescimento entre crescimentoMaximo e crescimentoMinimo
     * @param inicio
     * @param fim
     * @param crescimentoMaximo
     * @param crescimentoMinimo
     * @throws Exception 
     */
    public void addLimite(double inicio, double fim, double crescimentoMaximo, double crescimentoMinimo) throws Exception {
        if (inicio >= fim) {
            throw new Exception("Inicio deve ser menor ou igual a fim");
        }
        if (limites.size() > 0) {
            Limite limiteAnterior = limites.get(limites.size() - 1);
            if (inicio != limiteAnterior.getFim()) {
                throw new Exception("Inicio do novo limite deve ser igual que fim do limite anterior: Fim anterior = " + limiteAnterior.getFim() + "; Novo inicio = " + inicio);
            }
            limites.add(new Limite(limiteAnterior.getCod()+1, inicio, fim, crescimentoMaximo, crescimentoMinimo));
        } else {
            limites.add(new Limite(2, inicio, fim, crescimentoMaximo, crescimentoMinimo));
        }
    }

    /**
     * Retorna a quantidade de limites adicionados
     * @return 
     */
    public int size() {
        return limites.size();
    }

    /**
     * Retorna o código de identificação de um limite
     * @param i
     * @return 
     */
    public int getCod(int i) {
        return limites.get(i).getCod();
    }

    /**
     * Retorna o inicio do limite
     * @param i
     * @return 
     */
    public double getInicio(int i) {
        return limites.get(i).getInicio();
    }

    /**
     * Retorna o fim do inicio
     * @param i
     * @return 
     */
    public double getFim(int i) {
        return limites.get(i).getFim();
    }

    /**
     * Retorna o crescimento máximo do limite
     * @param i
     * @return 
     */
    public double getCrescimentoMaximo(int i) {
        return limites.get(i).getValorMaximo();
    }

    /**
     * retorna o crescimento mínimo do limite
     * @param i
     * @return 
     */
    public double getCrescimentoMinimo(int i) {
        return limites.get(i).getValorMinimo();
    }

    /**
     * Um limite a ser adicionado a lista de limites.
     */
    private class Limite {

        /**
         * Indentificação do limite
         */
        private int cod;
        /**
         * Patamar minimo para o dado ser comparado ao limite
         */
        private double inicio;
        /**
         * Patamar máximo para o dado ser comparado ao limite
         */
        private double fim;
        /**
         * Valor máximo a aceito para o dado
         */
        private double valorMaximo;
        /**
         * Valor mínimo aceito para o dado
         */
        private double valorMinimo;

        public Limite(int cod, double inicio, double fim, double valorMaximo, double valorMinimo) {
            this.cod = cod;
            this.inicio = inicio;
            this.fim = fim;
            this.valorMaximo = valorMaximo;
            this.valorMinimo = valorMinimo;
        }

        public int getCod() {
            return cod;
        }

        public double getInicio() {
            return inicio;
        }

        public double getFim() {
            return fim;
        }

        public double getValorMaximo() {
            return valorMaximo;
        }

        public double getValorMinimo() {
            return valorMinimo;
        }
    }

}
