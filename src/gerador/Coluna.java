/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package gerador;

/**
 * Descreve as características de uma coluna na coleta de dados.
 * @author danieloliveira
 */
public class Coluna {
    //
    /**
     * Tipo caracter
     */
    public static final int TIPO_CHAR = 0;
    /**
     * Tipo número usando ponto para separar decimais
     */
    public static final int TIPO_NUMERO_PONTO = 1;
    /**
     * Tipo número usando vírgula para separar decimais
     */
    public static final int TIPO_NUMERO_VIRGULA = 2;
    /**
     * Tipo número sem ponto nem vírgula
     */
    public static final int TIPO_NUMERO = 3;
    /**
     * Tipo número sem ponto nem vírgula
     */
    public static final int TIPO_TIME = 4;
    
    /**
     * Variável para indicar que a coluna é um dado
     */
    public static final int USO_DADO = 0;
    /**
     * Variável para indicar que a coluna deve ser usada nas consolidações
     */
    public static final int USO_CONSOLIDACAO = 1;
    /**
     * Variável para indicar que a coluna deve ser ignorada nas consolidações
     */
    public static final int USO_IGNORAR = 3;
    
    /**
     * Variável para indicar que a coluna é um CNPJ ou CPF
     */
    public static final int CLASSE_CNPJ_CPF = 0;
    /**
     * Variável para indicar que a coluna é um código de município
     */
    public static final int CLASSE_MUNICIPIO = 1;
    /**
     * Variável para indicar que a coluna é um código de município
     */
    public static final int CLASSE_CEP = 2;
    /**
     * Variável para indicar que a coluna é um código de município
     */
    public static final int CLASSE_LOCALIDADE = 3;
    /**
     * Variável para indicar que a coluna é uma ano
     */
    public static final int CLASSE_ANO = 4;
    /**
     * Variável para indicar que a coluna é um mes
     */
    public static final int CLASSE_MES = 5;
    /**
     * Variável para indicar que a coluna é um dia
     */
    public static final int CLASSE_DIA = 6;
    /**
     * Variável para indicar que a coluna é um periodo
     */
    public static final int CLASSE_PERIODO = 7;
    /**
     * Variável para indicar que a coluna é tem uso indefinido
     */
    public static final int CLASSE_OUTROS = 8;
    /**
     * Variável para indicar que a coluna é um CN
     */
    public static final int CLASSE_CN = 9;
    
    /**
     * Nome da coluna
     */
    private String nome;
    /**
     * Tipo da coluna
     */
    private int tipo;
    /**
     * Tamanho da coluna
     */
    private int tamanho;
    /**
     * Uso da coluna
     */
    private int uso;
    /**
     * Classe da coluna
     */
    private int classe;
    
    /**
     * Domínio da Coluna
     */
    private String leiauteDominio;
    
    /**
     * Coluna do Domínio da Coluna
     */
    private String colunaDominio;
    
    /**
     * Expressão regular para os valores dessa coluna
     */
    private String regex;
    
    /**
     * Determina se essa coluna deve ser utilizada para verificar atualização dos dados.
     */
    private boolean chaveAtualizacao;

    /**
     * 
     */
    public Coluna() {
    }

    /**
     * 
     * @param nome
     * @param tipo
     * @param tamanho
     * @param uso
     * @param classe
     * @param leiauteDominio
     * @param colunaDominio
     * @throws Exception 
     */
    public Coluna(String nome, int tipo, int tamanho, int uso, int classe, String leiauteDominio, String colunaDominio, String regex, boolean chaveAtualizacao) throws Exception {
        this.nome = nome;
        if(tipo >= 0 && tipo <= 4)
            this.tipo = tipo;
        else
            throw new Exception("Tipo de Coluna não reconhecido");

        this.tamanho = tamanho;
        
        if(uso >= 0 && uso <= 3)
            this.uso=uso;
        else
            this.uso = 3;
        
        if(classe >= 0 && classe <= 9)
            this.classe = classe;
        else
            this.classe = 8;
        
        this.leiauteDominio = leiauteDominio;
        this.colunaDominio = colunaDominio;
        this.regex = regex;
        this.chaveAtualizacao = chaveAtualizacao;
    }

    /**
     * 
     * @param nome 
     */
    public void setNome(String nome) {
        this.nome = nome;
    }

    /**
     * 
     * @param tipo
     * @throws Exception 
     */
    public void setTipo(int tipo) throws Exception {
        if(tipo >= 0 && tipo <= 3)
            this.tipo = tipo;
        else
            throw new Exception("Tipo de Coluna não reconhecido");
    }

    /**
     * 
     * @param tamanho 
     */
    public void setTamanho(int tamanho) {
        this.tamanho = tamanho;
    }

    /**
     * 
     * @param uso 
     */
    public void setUso(int uso) {
        if(uso >= 0 && uso <= 3)
            this.uso=uso;
        else
            this.uso = 3;
    }

    /**
     * 
     * @param classe 
     */
    public void setClasse(int classe) {
        if(classe >= 0 && classe <= 9)
            this.classe = classe;
        else
            this.classe = 8;
    }
    
    /**
     * 
     * @return 
     */
    public String getNome() {
        return nome;
    }

    /**
     * 
     * @return 
     */
    public int getTipo() {
        return tipo;
    }

    /**
     * 
     * @return 
     */
    public int getTamanho() {
        return tamanho;
    }
    
    /**
     * 
     * @return 
     */
    public int getUso() {
        return uso;
    }

    /**
     * 
     * @return 
     */
    public int getClasse() {
        return classe;
    }

    /**
     * 
     * @return 
     */
    public String getLeiauteDominio() {
        return leiauteDominio;
    }

    /**
     * 
     * @return 
     */
    public String getColunaDominio() {
        return colunaDominio;
    }
    
    /**
     * 
     * @return 
     */
    public String getRegex() {
        return regex;
    }
    
    public boolean isChaveAtualizacao(){
        return chaveAtualizacao;
    }
    
}
