/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package gerador;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Descreve uma coleta criada no Sistema de Coleta
 *
 * @author danieloliveira
 */
public class Coleta {

    /**
     * Variável para indicar que a coleta é mensal
     */
    public static final int TIPO_PERIODO_MENSAL = 0;
    /**
     * Variável para indicar que a coleta é trimestral
     */
    public static final int TIPO_PERIODO_TRIMESTRAL = 1;
    /**
     * Variável para indicar que a coleta é semestral
     */
    public static final int TIPO_PERIODO_SEMESTRAL = 2;
    /**
     * Variável para indicar que a coleta é anual
     */
    public static final int TIPO_PERIODO_ANUAL = 3;
    /**
     * Variável para indicar que a coleta é eventual
     */
    public static final int TIPO_PERIODO_EVENTUAL = 4;

    /**
     * Pasta onde os arquivos a serem validados estão salvos
     */
    private Arquivo pasta;
    /**
     * Lista de arquivos a serem validados
     */
    private List<Arquivo> arquivos;
    /**
     * Colunas do arquivo a ser validado
     */
    private List<Coluna> colunas;
    /**
     * Banco onde estão os dados históricos da coleta
     */
    private Banco banco;

    /**
     * Variável para indicar o tipo de período
     */
    private int tipoPeriodo;

    /**
     *
     */
    public Coleta() {
        colunas = new ArrayList<Coluna>();
    }

    /**
     *
     * @param arquivos
     * @param colunas
     * @param banco
     * @param tipoPeriodo Inteiro identificador do tipo de período da coleta
     * podendo ser TIPO_PERIODO_MENSAL, TIPO_PERIODO_TRIMESTRAL,
     * TIPO_PERIODO_SEMESTRAL ou TIPO_PERIODO_ANUAL.
     * @throws Exception
     */
    public Coleta(List<Arquivo> arquivos, List<Coluna> colunas, Banco banco, int tipoPeriodo) throws Exception {
        for (Arquivo arquivo : arquivos) {
            File arquivoCSV = new File(arquivo.getEndereco());
            if (!arquivoCSV.isFile()) {
                throw new Exception(arquivo.getEndereco() + " não é um arquivo. Para encaminhar pastas, use o outro construtor");
            }
        }
        this.arquivos = arquivos;
        this.colunas = colunas;
        //TODO: verificar se o banco realmente existe
        this.banco = banco;
        if (tipoPeriodo >= 0 && tipoPeriodo <= 4) {
            this.tipoPeriodo = tipoPeriodo;
        } else {
            throw new Exception("Tipo de Período não reconhecido");
        }
    }

    /**
     *
     * @param pasta
     * @param colunas
     * @param banco
     * @param tipoPeriodo
     */
    public Coleta(Arquivo pasta, List<Coluna> colunas, Banco banco, int tipoPeriodo) throws Exception {
            File arquivoCSV = new File(pasta.getEndereco());
            if (!arquivoCSV.isDirectory()) {
                throw new Exception(pasta.getEndereco() + " não é uma pasta. Para encaminhar arquivos, use o outro construtor");
            }
            this.pasta = pasta;
            this.colunas = colunas;
            this.banco = banco;
            this.tipoPeriodo = tipoPeriodo;

    }

    /**
     *
     * @return
     */
    public List<Arquivo> getArquivos() {
        return arquivos;
    }

    /**
     *
     * @param i
     * @return
     */
    public Arquivo getArquivo(int i) {
        return arquivos.get(i);
    }

    /**
     *
     * @return
     */
    public List<Coluna> getColunas() {
        return colunas;
    }

    /**
     *
     * @param i
     * @return
     */
    public Coluna getColuna(int i) {
        return colunas.get(i);
    }

    /**
     *
     * @param colunas
     */
    public void setColunas(List<Coluna> colunas) {
        this.colunas = colunas;
    }

    /**
     *
     * @param coluna
     */
    public void addColuna(Coluna coluna) {
        colunas.add(coluna);
    }

    /**
     *
     * @return
     */
    public Banco getBanco() {
        return banco;
    }

    /**
     *
     * @param banco
     */
    public void setBanco(Banco banco) {
        //TODO: verificar se o banco realmente existe
        this.banco = banco;
    }

    /**
     *
     * @return
     */
    public int getTipoPeriodo() {
        return tipoPeriodo;
    }

    /**
     *
     * @param tipoPeriodo
     */
    public void setTipoPeriodo(int tipoPeriodo) throws Exception {
        if (tipoPeriodo >= 0 && tipoPeriodo <= 3) {
            this.tipoPeriodo = tipoPeriodo;
        } else {
            throw new Exception("Tipo de Período não reconhecido");
        }
    }

    public Arquivo getPasta() {
        return pasta;
    }

}
