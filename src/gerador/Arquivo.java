/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package gerador;

import java.io.File;
import java.io.FileFilter;

/**
 *
 * @author danieloliveira
 */
public class Arquivo {

    /**
     * Endereço do arquivo
     */
    private String endereco;
    /**
     * Nome do leiaute do arquivo que será usado para filtrar os arquivos na pasta indicada
     */
    private String leiaute;

    /**
     *
     * @param endereco
     * @throws Exception
     */
    public Arquivo(String endereco) throws Exception {
        File arquivoCSV = new File(endereco);
        if (!arquivoCSV.exists()) {
            throw new Exception("Arquivo: " + endereco + " não encontrado");
        }
        if (!arquivoCSV.isFile()) {
            throw new Exception(endereco + " não é um arquivo. Para encaminhar pastas, use o construtor Arquivo(String endereco, String usuarioLogin, String filtro)");
        }
        this.endereco = endereco;
    }

    /**
     *
     * @param endereco
     * @param leiaute
     * @throws Exception
     */
    public Arquivo(String endereco, String leiaute) throws Exception {
        File arquivoCSV = new File(endereco);
        if (!arquivoCSV.exists()) {
            throw new Exception("Arquivo: " + endereco + " não encontrado");
        }
        if (!arquivoCSV.isDirectory()) {
            throw new Exception(endereco + " não é uma pasta. Para encaminhar arquivos, use o construtor Arquivo(String endereco, String usuarioLogin)");
        }
        
        if (leiaute != null && arquivoCSV.listFiles(new ColetaFileFilter(leiaute)).length == 0) {
            throw new Exception("Não foram encontrados nenhum arquivo com os parâmetros indicados na pasta " + endereco);
        }
        this.endereco = endereco;
        this.leiaute = leiaute;
    }

    /**
     *
     * @return
     */
    public String getEndereco() {
        return endereco;
    }

    /**
     *
     * @param endereco
     * @throws Exception
     */
    public void setEndereco(String endereco) throws Exception {
        File arquivoCSV = new File(endereco);
        if (!arquivoCSV.exists()) {
            throw new Exception("Arquivo: " + endereco + " não encontrado");
        }
        this.endereco = endereco;
    }

    /**
     *
     * @return
     */
    public String getLeiaute() {
        return leiaute;
    }

    public class ColetaFileFilter implements FileFilter {

        private final String[] okFileExtensions
                = new String[]{"csv"};

        private String filtro;
        
        public ColetaFileFilter(String filtro) {
            this.filtro = filtro;
        }
        
        public boolean accept(File file) {
            for (String extension : okFileExtensions) {
                if (file.getName().toLowerCase().endsWith(extension) && file.getName().toLowerCase().contains(this.filtro.toLowerCase())) {
                    return true;
                }
            }
            return false;
        }
    }

}
