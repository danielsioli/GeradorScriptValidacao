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
public class Banco {

    /**
     * Constante para indicar que o banco é um arquivo SAS
     */
    public static final int TIPO_ARQUIVO_SAS = 0;
    /**
     * Constante para indicar que o banco é uma biblioteca SAS
     */
    public static final int TIPO_BIBLIOTECA_SAS = 1;
    /**
     * Constante para indicar que o banco é uma banco de dado
     */
    public static final int TIPO_BANCO_DE_DADOS = 2;

    /**
     * Variável para indicar o tip o de banco
     */
    private int tipo;
    /**
     * Endereço do banco
     */
    private String endereco;
    /**
     * Nome da tabela onde estão os dados históricos
     */
    private String tabela;
    /**
     * Query a ser utilizada para obter os dados históricos
     */
    private String sqlQuery;

    public Banco(int tipo, String endereco, String tabela) throws Exception {
        if (tipo >= 0 && tipo <= 1) {
            this.tipo = tipo;
        } else {
            throw new Exception("Tipo de banco não reconhecido");
        }
        this.endereco = endereco;
        this.tabela = tabela;
    }

    /*public Banco(int tipo, String endereco, String sqlQuery) throws Exception {
        if (tipo >= 0 && tipo <= 1) {
            this.tipo = tipo;
        } else {
            throw new Exception("Tipo de banco não reconhecido");
        }
        this.endereco = endereco;
        this.tabela = tabela;
        this.sqlQuery = sqlQuery;
    }*/

    public int getTipo() {
        return tipo;
    }

    public void setTipo(int tipo) throws Exception {
        if (tipo >= 0 && tipo <= 1) {
            this.tipo = tipo;
        } else {
            throw new Exception("Tipo de banco não reconhecido");
        }
    }

    public String getEndereco() {
        return endereco;
    }

    public void setEndereco(String endereco) {
        this.endereco = endereco;
    }

    public String getTabela() {
        return tabela;
    }

    public void setTabela(String tabela) {
        this.tabela = tabela;
    }

    public String getSqlQuery() {
        return sqlQuery;
    }

    public void setSqlQuery(String sqlQuery) {
        this.sqlQuery = sqlQuery;
    }

}
