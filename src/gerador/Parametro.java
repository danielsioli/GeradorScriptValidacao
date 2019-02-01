/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package gerador;

import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author danieloliveira
 */
public class Parametro {
    private String nome;
    private List<String> valor;

    public Parametro(String nome) {
        this.nome = nome;
        valor = new ArrayList();
    }
    
    public String getNome() {
        return nome;
    }

    public void addValor(String valor){
        this.valor.add(valor);
    }
    
    public List<String> getValor() {
        return valor;
    }
    
}
