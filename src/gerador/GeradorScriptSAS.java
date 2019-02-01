/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package gerador;

import com.sas.iom.SAS.ILanguageServicePackage.LineType;
import gerador.sas.ConexaoSAS;
import gerador.sas.SASAction;
import gerador.sas.SASActionListener;
import gerador.sas.SASException;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 *
 * @author danieloliveira
 */
public final class GeradorScriptSAS {

    /**
     * Variável para coletas mensais
     */
    public static int TIPO_PERIODO_MENSAL = 0;
    /**
     * Variável para coletas trimestrais
     */
    public static int TIPO_PERIODO_TRIMESTRAL = 1;
    /**
     * Variável para coletas semestrais
     */
    public static int TIPO_PERIODO_SEMESTRAL = 2;
    /**
     * Variável para coletas anuais
     */
    public static int TIPO_PERIODO_ANUAL = 3;

    /**
     * Variável para cálculo de densidade por população
     */
    public static int DENOMINADOR_POPULACAO = 0;
    /**
     * Variável para cálculo de densidade por domicílio
     */
    public static int DENOMINADOR_DOMICILIO = 1;

    /**
     * Realiza as funcionalidades do StrinbBuilder
     */
    private ScriptBuilder scriptSAS;
    /**
     * Objeto contendo as definições da coleta
     */
    private Coleta coleta;
    /**
     * Endereço do arquivo .sas que será criado.
     */
    private String arquivoSAS;
    /**
     * Nome do leiaute a ser validado
     */
    private String leiaute;

    private final String coletaXSD = "\\\\Servicesdata\\prpe$\\Dados\\Projeto Piloto\\xml\\coleta.xsd";
    //private final String coletaXSD = "http://sistemasds.anatel.gov.br/dici/coleta.xsd";

    private String user;
    private String password;
    private String sasServer;
    private int sasPort;
    private SASActionListener sasActionListener;

    public GeradorScriptSAS(String sasServer, int sasPort, String user, String password) {
        this.user = user;
        this.password = password;
        this.sasServer = sasServer;
        this.sasPort = sasPort;
    }

    public void addSASActionListener(SASActionListener sASActionListener) {
        this.sasActionListener = sASActionListener;
    }

    /**
     * Lê um arquivo XML com os detalhes da coleta e gera um script SAS com base
     * nesse arquivo. O arquivo XML deve obedecer as regras descritas no arquivo
     * coleta.xsd
     *
     * @param arquivosCSV
     * @param coletaXML Arquivo XML com os detalhes da coleta
     * @return
     * @throws Exception Caso tenha ocorrido algum problema na geração do script
     * SAS
     */
    public List<Erro> gerarScriptSAS(String usuario, List<File> arquivosCSV, File coletaXML) throws SegurancaException, Exception {
        scriptSAS = new ScriptBuilder();
        if (!coletaXML.isFile()) {
            throw new Exception("Arquivo XML da Coleta não encontrado");
        }

        //lê arquivos
        List<Arquivo> arquivos = new ArrayList<>();
        for (File arquivoCSV : arquivosCSV) {
            if (!arquivoCSV.isFile()) {
                throw new Exception("Arquivo CSV não encontrado");
            }
            Arquivo arquivo = new Arquivo(arquivoCSV.getAbsolutePath());
            arquivos.add(arquivo);
        }

        //URL schemaFile = new URL(coletaXSD);
        File schemaFile = new File(coletaXSD);
        Source xmlFile = new StreamSource(coletaXML);
        SchemaFactory schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        Schema schema = schemaFactory.newSchema(schemaFile);
        Validator validator = schema.newValidator();
        validator.validate(xmlFile);

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();

        dbf.setNamespaceAware(false);

        DocumentBuilder docBuilder = dbf.newDocumentBuilder();

        Document doc = docBuilder.parse(coletaXML);
        Element coletaTag = doc.getDocumentElement();

        //le colunas
        NodeList colunasTag = ((Element) coletaTag.getElementsByTagName("colunas").item(0)).getElementsByTagName("coluna");
        List<Coluna> colunas = new ArrayList<>();

        BufferedReader br = null;
        String cabecalho = "";

        for (int i = 0; i < colunasTag.getLength(); i++) {
            Element colunaTag = (Element) colunasTag.item(i);
            int tipo = Coluna.TIPO_CHAR;
            int classe = Coluna.CLASSE_OUTROS;
            switch (colunaTag.getAttribute("tipo")) {
                case "NUMERO":
                    tipo = Coluna.TIPO_NUMERO;
                    break;
                case "NUMERO_PONTO":
                    tipo = Coluna.TIPO_NUMERO_PONTO;
                    break;
                case "NUMERO_VIRGULA":
                    tipo = Coluna.TIPO_NUMERO_VIRGULA;
                    break;
                case "CHAR":
                    tipo = Coluna.TIPO_CHAR;
                    break;
                case "TIME":
                    tipo = Coluna.TIPO_TIME;
                    break;
                default:
                    throw new Exception("Tipo de coluna não reconhecido");

            }
            switch (colunaTag.getAttribute("classe")) {
                case "CNPJ_CPF":
                    classe = Coluna.CLASSE_CNPJ_CPF;
                    break;
                case "MUNICIPIO":
                    classe = Coluna.CLASSE_MUNICIPIO;
                    break;
                case "CEP":
                    classe = Coluna.CLASSE_CEP;
                    break;
                case "LOCALIDADE":
                    classe = Coluna.CLASSE_LOCALIDADE;
                    break;
                case "ANO":
                    classe = Coluna.CLASSE_ANO;
                    break;
                case "MES":
                    classe = Coluna.CLASSE_MES;
                    break;
                case "DIA":
                    classe = Coluna.CLASSE_DIA;
                    break;
                case "PERIODO":
                    classe = Coluna.CLASSE_PERIODO;
                    break;
                case "OUTROS":
                    classe = Coluna.CLASSE_OUTROS;
                    break;
                case "CN":
                    classe = Coluna.CLASSE_CN;
                    break;
                default:
                    throw new Exception("Classe de coluna não reconhecido");
            }

            Element dominioTag = (Element) colunaTag.getElementsByTagName("dominio").item(0);

            colunas.add(new Coluna(colunaTag.getAttribute("nome"), tipo, Integer.parseInt(colunaTag.getAttribute("tamanho")), Coluna.USO_IGNORAR, classe, (dominioTag != null ? dominioTag.getAttribute("leiaute") : null), (dominioTag != null ? dominioTag.getAttribute("coluna") : null), colunaTag.getAttribute("regex"), colunaTag.getAttribute("atualizacao").equals("SIM")));
        }

        for (File arquivoCSV : arquivosCSV) {
            br = new BufferedReader(new FileReader(arquivoCSV));
            if ((cabecalho = br.readLine()) != null) {
                String[] colunasCSV = cabecalho.split(";");
                if (colunasCSV.length != colunas.size()) {
                    throw new Exception("O arquivo CSV enviado é incompatível com o leiaute informado.<br>Verifique o formato do arquivo CSV.");
                }
                for (int i = 0; i < colunasCSV.length; i++) {
                    if (!colunasCSV[i].toUpperCase().equals(colunas.get(i).getNome().toUpperCase())) {
                        throw new Exception("A coluna " + colunasCSV[i] + " no arquivo CSV não foi reconhecida para o leiaute informado.<br>Verifique o formato do arquivo CSV.");
                    }
                }
            } else {
                throw new Exception("O arquivo CSV enviado é incompatível com o leiaute informado.<br>Verifique o formato do arquivo CSV.");
            }
        }

        //le banco
        Element bancoTag = (Element) coletaTag.getElementsByTagName("banco").item(0);
        int tipo = Banco.TIPO_ARQUIVO_SAS;
        switch (bancoTag.getAttribute("tipo")) {
            case "ARQUIVO_SAS":
                tipo = Banco.TIPO_ARQUIVO_SAS;
                break;
            case "BIBLIOTECA_SAS":
                tipo = Banco.TIPO_BIBLIOTECA_SAS;
                break;
            case "BANCO_DE_DADOS":
                tipo = Banco.TIPO_BANCO_DE_DADOS;
                break;
            default:
                throw new Exception("Tipo de banco não reconhecido");
        }
        Banco banco = new Banco(tipo, bancoTag.getAttribute("endereco"), bancoTag.getAttribute("tabela"));

        Element tipoPeriodoTag = (Element) coletaTag.getElementsByTagName("tipoPeriodo").item(0);
        int tipoPeriodo = -1;
        switch (tipoPeriodoTag.getTextContent()) {
            case "MENSAL":
                tipoPeriodo = Coleta.TIPO_PERIODO_MENSAL;
                break;
            case "TRIMESTRAL":
                tipoPeriodo = Coleta.TIPO_PERIODO_TRIMESTRAL;
                break;
            case "SEMESTRAL":
                tipoPeriodo = Coleta.TIPO_PERIODO_SEMESTRAL;
                break;
            case "ANUAL":
                tipoPeriodo = Coleta.TIPO_PERIODO_ANUAL;
                break;
            case "EVENTUAL":
                tipoPeriodo = Coleta.TIPO_PERIODO_EVENTUAL;
                break;
            default:
                throw new Exception("valor da tag tipoPeriodo inválido");

        }

        coleta = new Coleta(arquivos, colunas, banco, tipoPeriodo);

        Element scriptTag = (Element) coletaTag.getElementsByTagName("script").item(0);

        this.arquivoSAS = scriptTag.getAttribute("endereco");
        this.leiaute = scriptTag.getAttribute("leiaute");
        //TODO: selecionar o tipo de script a ser criado
        String tipoScript = scriptTag.getAttribute("tipo");

        inicializarScriptSAS();

        verificaAcesso(usuario, arquivosCSV, coletaXML);

        for (Coluna coluna : colunas) {
            if (coluna.getLeiauteDominio() != null) {

                File dominioXML = new File(coletaXML.getParent() + "\\dominios\\dominio_" + coluna.getLeiauteDominio() + ".xml");

                Source dominioXmlFile = new StreamSource(dominioXML);
                validator.validate(dominioXmlFile);

                DocumentBuilderFactory dominioDbf = DocumentBuilderFactory.newInstance();

                dominioDbf.setNamespaceAware(false);

                DocumentBuilder dominioDocBuilder = dominioDbf.newDocumentBuilder();

                Document dominioDoc = dominioDocBuilder.parse(dominioXML);
                Element dominioColetaTag = dominioDoc.getDocumentElement();

                Element dominioBancoTag = (Element) dominioColetaTag.getElementsByTagName("banco").item(0);
                int dominioTipo = Banco.TIPO_ARQUIVO_SAS;
                switch (dominioBancoTag.getAttribute("tipo")) {
                    case "ARQUIVO_SAS":
                        dominioTipo = Banco.TIPO_ARQUIVO_SAS;
                        break;
                    case "BIBLIOTECA_SAS":
                        dominioTipo = Banco.TIPO_BIBLIOTECA_SAS;
                        break;
                    case "BANCO_DE_DADOS":
                        dominioTipo = Banco.TIPO_BANCO_DE_DADOS;
                        break;
                    default:
                        throw new Exception("Tipo de banco não reconhecido");
                }
                Banco dominioBanco = new Banco(dominioTipo, dominioBancoTag.getAttribute("endereco"), dominioBancoTag.getAttribute("tabela"));

                if (dominioBanco.getTipo() == Banco.TIPO_ARQUIVO_SAS) {
                    scriptSAS.append("LIBNAME dominios base \"").append(dominioBanco.getEndereco()).append("\";", ScriptBuilder.LINE_SEPARATOR);
                } else if (dominioBanco.getTipo() == Banco.TIPO_BIBLIOTECA_SAS) {
                    scriptSAS.append("LIBNAME dominios META LIBRARY=").append(dominioBanco.getEndereco()).append(";", ScriptBuilder.LINE_SEPARATOR);
                    scriptSAS.append("LIBNAME ").append(dominioBanco.getEndereco()).append(" META LIBRARY=").append(dominioBanco.getEndereco()).append(";", ScriptBuilder.LINE_SEPARATOR);
                }

                scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

                scriptSAS.append("PROC SQL;", ScriptBuilder.LINE_SEPARATOR);
                scriptSAS.append(1, "create table dominio as", ScriptBuilder.LINE_SEPARATOR);
                scriptSAS.append(2, "select distinct t1.").append(coluna.getNome()).append(",", ScriptBuilder.LINE_SEPARATOR);
                scriptSAS.append(3, "t1.nome_arquivo", ScriptBuilder.LINE_SEPARATOR);
                scriptSAS.append(1, "from work.dados t1;", ScriptBuilder.LINE_SEPARATOR);
                scriptSAS.append("quit;", ScriptBuilder.LINE_SEPARATOR);
                scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

                scriptSAS.append("PROC SQL;", ScriptBuilder.LINE_SEPARATOR);
                scriptSAS.append(1, "create table mensagem_validacao as", ScriptBuilder.LINE_SEPARATOR);
                scriptSAS.append(1, "SELECT distinct \"verificarDominio\" as metodo,", ScriptBuilder.LINE_SEPARATOR);
                scriptSAS.append(2, "SCAN(t1.nome_arquivo,1,'_') AS arquivo, ", ScriptBuilder.LINE_SEPARATOR);
                scriptSAS.append(2, "/* parametro */", ScriptBuilder.LINE_SEPARATOR);
                scriptSAS.append(2, "cat('").append(coluna.getNome()).append(" = ',t1.").append(coluna.getNome()).append(") as parametros,", ScriptBuilder.LINE_SEPARATOR);
                scriptSAS.append(2, "'Valor do campo não reconhecido' as mensagem,", ScriptBuilder.LINE_SEPARATOR);
                scriptSAS.append(2, "'false' as aceitavel", ScriptBuilder.LINE_SEPARATOR);
                scriptSAS.append(2, "from dominio t1", ScriptBuilder.LINE_SEPARATOR);

                Element queryTag = (Element) dominioBancoTag.getElementsByTagName("query").item(0);
                if (queryTag != null && !queryTag.getTextContent().isEmpty()) {
                    scriptSAS.append(2, "left join (").append(queryTag.getTextContent()).append(") t2 on t1.").append(coluna.getNome()).append(" = t2.").append(coluna.getColunaDominio(), ScriptBuilder.LINE_SEPARATOR);
                } else {
                    scriptSAS.append(2, "left join dominios.").append(dominioBancoTag.getAttribute("tabela")).append(" t2 on t1.").append(coluna.getNome()).append(" = t2.").append(coluna.getColunaDominio(), ScriptBuilder.LINE_SEPARATOR);
                }

                scriptSAS.append(2, "where t2.").append(coluna.getColunaDominio()).append(" is missing", ScriptBuilder.LINE_SEPARATOR);
                scriptSAS.append(1, "UNION ALL select * from mensagem_validacao;", ScriptBuilder.LINE_SEPARATOR);
                scriptSAS.append("QUIT;", ScriptBuilder.LINE_SEPARATOR);
                scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);
            }
        }
        scriptSAS.append("PROC SQL; DROP TABLE dominio;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append("QUIT;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

        scriptSAS.append("data mensagem_validacao (reuse=yes compress=yes);", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "set mensagem_validacao;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append("run;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

        //Escolhe quais validações devem ser invocadas
        NodeList validacoesNodeList = coletaTag.getElementsByTagName("validacoes");
        if (validacoesNodeList.getLength() > 0) {
            NodeList validacoesTag = ((Element) validacoesNodeList.item(0)).getElementsByTagName("validacao");
            for (int i = 0; i < validacoesTag.getLength(); i++) {
                Element validacao = (Element) validacoesTag.item(i);
                String nomeValidacao = validacao.getAttribute("nome");
                if (nomeValidacao.equals("validaCNPJ")) {
                    validaCNPJ();
                }
                if (nomeValidacao.equals("verificaCadastro")) {
                    verificaCadastro();
                }
                if (nomeValidacao.equals("validaCodigoMunicipio")) {
                    validaCodigoMunicipio();
                }
                if (nomeValidacao.equals("validaChaveUnica")) {
                    Element colunasConsolidacaoTag = ((Element) validacao.getElementsByTagName("colunas").item(0));
                    if (colunasConsolidacaoTag != null) {
                        for (int j = 0; j < colunasConsolidacaoTag.getElementsByTagName("coluna").getLength(); j++) {
                            Element colunaConsolidacaoTag = (Element) colunasConsolidacaoTag.getElementsByTagName("coluna").item(j);
                            int uso;
                            switch (colunaConsolidacaoTag.getAttribute("uso")) {
                                case "DADO":
                                    uso = Coluna.USO_DADO;
                                    break;
                                case "CONSOLIDACAO":
                                    uso = Coluna.USO_CONSOLIDACAO;
                                    break;
                                case "IGNORAR":
                                    uso = Coluna.USO_IGNORAR;
                                    break;
                                default:
                                    throw new Exception("Uso de coluna não reconhecido");

                            }
                            alteraUsoColuna(colunaConsolidacaoTag.getAttribute("nome"), uso);
                        }
                    } else {
                        throw new Exception("tag <colunas> no metodo verificaDuplicados é obrigatório");
                    }
                    validarChaveUnica();
                }
                if (nomeValidacao.equals("verificaDuplicados")) {
                    Element colunasConsolidacaoTag = ((Element) validacao.getElementsByTagName("colunas").item(0));
                    if (colunasConsolidacaoTag != null) {
                        for (int j = 0; j < colunasConsolidacaoTag.getElementsByTagName("coluna").getLength(); j++) {
                            Element colunaConsolidacaoTag = (Element) colunasConsolidacaoTag.getElementsByTagName("coluna").item(j);
                            int uso;
                            switch (colunaConsolidacaoTag.getAttribute("uso")) {
                                case "DADO":
                                    uso = Coluna.USO_DADO;
                                    break;
                                case "CONSOLIDACAO":
                                    uso = Coluna.USO_CONSOLIDACAO;
                                    break;
                                case "IGNORAR":
                                    uso = Coluna.USO_IGNORAR;
                                    break;
                                default:
                                    throw new Exception("Uso de coluna não reconhecido");

                            }
                            alteraUsoColuna(colunaConsolidacaoTag.getAttribute("nome"), uso);
                        }
                    } else {
                        throw new Exception("tag <colunas> no metodo verificaDuplicados é obrigatório");
                    }
                    verificaDuplicados();
                    for (Coluna coluna : colunas) {
                        alteraUsoColuna(coluna.getNome(), coluna.getUso());
                    }
                }

                if (nomeValidacao.equals("validaCEP")) {
                    validaCEP();
                }
                if (nomeValidacao.equals("validaLocalidadeSGMU")) {
                    validaLocalidadeSGMU();
                }
                if (nomeValidacao.equals("validaCPF")) {
                    validaCPF();
                }
                if (nomeValidacao.equals("verificaValoresEmBranco")) {
                    verificaValoresEmBranco();
                }
                if (nomeValidacao.equals("media")) {
                    Element periodos = (Element) validacao.getElementsByTagName("periodos").item(0);
                    Element desviosPadroes = (Element) validacao.getElementsByTagName("desviosPadroes").item(0);
                    media(Integer.parseInt(periodos.getTextContent()), Double.parseDouble(desviosPadroes.getTextContent()));
                }
                if (nomeValidacao.equals("densidadeGeografica")) {
                    Element denominador = (Element) validacao.getElementsByTagName("denominador").item(0);
                    Element multiplicadorTag = (Element) validacao.getElementsByTagName("multiplicador").item(0);
                    if (multiplicadorTag == null) {
                        throw new Exception("A tag <multiplicador> é obrigatória para o método densidadeGeografica");
                    }
                    switch (denominador.getTextContent()) {
                        case "DOMICILIO":
                            densidadeGeografica(DENOMINADOR_DOMICILIO, Double.parseDouble(multiplicadorTag.getTextContent()));
                            break;
                        case "POPULACAO":
                            densidadeGeografica(DENOMINADOR_POPULACAO, Double.parseDouble(multiplicadorTag.getTextContent()));
                            break;
                        default:
                            throw new Exception("Valor inválido na tag denominador");
                    }
                }
                if (nomeValidacao.equals("crescimento")) {
                    Element colunasConsolidacaoTag = ((Element) validacao.getElementsByTagName("colunas").item(0));
                    if (colunasConsolidacaoTag != null) {
                        for (int j = 0; j < colunasConsolidacaoTag.getElementsByTagName("coluna").getLength(); j++) {
                            Element colunaConsolidacaoTag = (Element) colunasConsolidacaoTag.getElementsByTagName("coluna").item(j);
                            int uso;
                            switch (colunaConsolidacaoTag.getAttribute("uso")) {
                                case "DADO":
                                    uso = Coluna.USO_DADO;
                                    break;
                                case "CONSOLIDACAO":
                                    uso = Coluna.USO_CONSOLIDACAO;
                                    break;
                                case "IGNORAR":
                                    uso = Coluna.USO_IGNORAR;
                                    break;
                                default:
                                    throw new Exception("Uso de coluna não reconhecido");

                            }
                            alteraUsoColuna(colunaConsolidacaoTag.getAttribute("nome"), uso);
                        }
                    } else {
                        throw new Exception("tag <colunas> no metodo crescimentoAoUltimoPeriodo é obrigatório");
                    }
                    NodeList limitesTag = ((Element) validacao.getElementsByTagName("limites").item(0)).getElementsByTagName("limite");
                    Limites limites = new Limites();
                    for (int j = 0; j < limitesTag.getLength(); j++) {
                        Element limiteTag = (Element) limitesTag.item(j);
                        double inicio;
                        double fim;
                        if (limiteTag.getAttribute("inicio").equals("")) {
                            inicio = Double.NEGATIVE_INFINITY;
                        } else {
                            inicio = Double.parseDouble(limiteTag.getAttribute("inicio"));
                        }
                        if (limiteTag.getAttribute("fim").equals("")) {
                            fim = Double.POSITIVE_INFINITY;
                        } else {
                            fim = Double.parseDouble(limiteTag.getAttribute("fim"));
                        }
                        limites.addLimite(inicio, fim, Double.parseDouble(limiteTag.getAttribute("valor_maximo")), Double.parseDouble(limiteTag.getAttribute("valor_minimo")));
                    }
                    crescimento(limites);
                    for (Coluna coluna : colunas) {
                        alteraUsoColuna(coluna.getNome(), coluna.getUso());
                    }
                }
                if (nomeValidacao.equals("comparaDados")) {
                    Element queryTag = ((Element) validacao.getElementsByTagName("query").item(0));
                    comparaDados(queryTag.getTextContent());
                }
            }
        }
        //cria final do script SAS
        finalizarScriptSAS();

        //tenta rodar o script SAS
        String rodar = scriptTag.getAttribute("rodar");
        List<Erro> erros = new ArrayList<>();
        if (rodar.equals("SIM")) {
            //roda o script
            erros = rodarScriptSAS();
        } else {
            //grava o script em um arquivo
            salvarScriptSAS();
        }
        scriptSAS = null;

        return erros;
    }

    /**
     * Valida se os dados informados são compatíveis com a expressão regular
     */
    private void validarExpressaoRegular() {
        final StackTraceElement[] ste = Thread.currentThread().getStackTrace();
        List<Coluna> colunas = coleta.getColunas();
        /*for (Coluna coluna : colunas) {
         scriptSAS.append("PROC SQL;", ScriptBuilder.LINE_SEPARATOR);
         scriptSAS.append(1, "create table mensagem_validacao as", ScriptBuilder.LINE_SEPARATOR);
         scriptSAS.append(1, "SELECT distinct \"").append(ste[1].getMethodName()).append("\" as metodo,", ScriptBuilder.LINE_SEPARATOR);
         scriptSAS.append(2, "scan(t1.nome_arquivo,1,'_') as arquivo,", ScriptBuilder.LINE_SEPARATOR);
         scriptSAS.append(2, "cat('").append(coluna.getNome()).append(" = ',compress(t1.").append(coluna.getNome()).append(",' ')) as parametros,", ScriptBuilder.LINE_SEPARATOR);
         scriptSAS.append(2, "'Valor inválido encontrado. Verifique os valores permitidos para cada campo.' as mensagem,", ScriptBuilder.LINE_SEPARATOR);
         scriptSAS.append(2, "'false' as aceitavel", ScriptBuilder.LINE_SEPARATOR);
         scriptSAS.append(1, "from dados t1", ScriptBuilder.LINE_SEPARATOR);
         scriptSAS.append(1, "where", ScriptBuilder.LINE_SEPARATOR);
         scriptSAS.append(2, "prxmatch(prxparse('").append(coluna.getRegex()).append("'),compress(t1.").append(coluna.getNome()).append(",' ')) = 0").append(System.getProperty("line.separator"));
         scriptSAS.append(1, "UNION ALL select * from mensagem_validacao;", ScriptBuilder.LINE_SEPARATOR);
         scriptSAS.append("QUIT;", ScriptBuilder.LINE_SEPARATOR);
         scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

         scriptSAS.append("data mensagem_validacao (reuse=yes compress=yes);", ScriptBuilder.LINE_SEPARATOR);
         scriptSAS.append(1, "set mensagem_validacao;", ScriptBuilder.LINE_SEPARATOR);
         scriptSAS.append("run;", ScriptBuilder.LINE_SEPARATOR);
         scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);
         }
         */
        scriptSAS.append("/*Verifica se houve algum erro de expressão regular.*/", ScriptBuilder.LINE_SEPARATOR);

        scriptSAS.append("%let dsempty=0;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append("data _null_;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "if eof then", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "do;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(3, "call symput('dsempty',1);", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(3, "put 'NOTE: Nenhum erro de expressão regular';", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "end;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "stop;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "set mensagem_validacao end=eof;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append("run;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append("%macro continua;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "%if &dsempty. %then %do;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

        scriptSAS.append("PROC SQL;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "create table dados as", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "select", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "t1.nome_arquivo,", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "t1.myfilename,", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "t1.caminho,", ScriptBuilder.LINE_SEPARATOR);
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder = new StringBuilder();
        for (Coluna coluna : colunas) {
            /*if (coluna.getTipo() == Coluna.TIPO_CHAR) {
                stringBuilder.append("\t\tcompress(t1.").append(coluna.getNome()).append(",' ') as ").append(coluna.getNome()).append(",").append(System.getProperty("line.separator"));
            } else if (coluna.getTipo() == Coluna.TIPO_NUMERO) {
                stringBuilder.append("\t\tinput(compress(t1.").append(coluna.getNome()).append(",' '),best").append(coluna.getTamanho()).append(".) as ").append(coluna.getNome()).append(",").append(System.getProperty("line.separator"));
            } else if (coluna.getTipo() == Coluna.TIPO_NUMERO_PONTO) {
                stringBuilder.append("\t\tinput(compress(t1.").append(coluna.getNome()).append(",' '),comma").append(coluna.getTamanho()).append(".) as ").append(coluna.getNome()).append(",").append(System.getProperty("line.separator"));
            } else if (coluna.getTipo() == Coluna.TIPO_NUMERO_VIRGULA) {
                stringBuilder.append("\t\tinput(compress(t1.").append(coluna.getNome()).append(",' '),commax").append(coluna.getTamanho()).append(".) as ").append(coluna.getNome()).append(",").append(System.getProperty("line.separator"));
            } else if (coluna.getTipo() == Coluna.TIPO_TIME) {
                stringBuilder.append("\t\tinput(compress(t1.").append(coluna.getNome()).append(",' '),PTGDFMY7.) as ").append(coluna.getNome()).append(",").append(System.getProperty("line.separator"));
            }*/
            if (coluna.getTipo() == Coluna.TIPO_CHAR) {
                stringBuilder.append("\t\tt1.").append(coluna.getNome()).append(" as ").append(coluna.getNome()).append(",").append(System.getProperty("line.separator"));
            } else if (coluna.getTipo() == Coluna.TIPO_NUMERO) {
                stringBuilder.append("\t\tinput(t1.").append(coluna.getNome()).append(",best").append(coluna.getTamanho()).append(".) as ").append(coluna.getNome()).append(",").append(System.getProperty("line.separator"));
            } else if (coluna.getTipo() == Coluna.TIPO_NUMERO_PONTO) {
                stringBuilder.append("\t\tinput(t1.").append(coluna.getNome()).append(",comma").append(coluna.getTamanho()).append(".) as ").append(coluna.getNome()).append(",").append(System.getProperty("line.separator"));
            } else if (coluna.getTipo() == Coluna.TIPO_NUMERO_VIRGULA) {
                stringBuilder.append("\t\tinput(t1.").append(coluna.getNome()).append(",commax").append(coluna.getTamanho()).append(".) as ").append(coluna.getNome()).append(",").append(System.getProperty("line.separator"));
            } else if (coluna.getTipo() == Coluna.TIPO_TIME) {
                stringBuilder.append("\t\tinput(t1.").append(coluna.getNome()).append(",PTGDFMY7.) as ").append(coluna.getNome()).append(",").append(System.getProperty("line.separator"));
            }
        }
        scriptSAS.append(stringBuilder.toString().substring(0, stringBuilder.toString().length() - 3), ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "from dados t1;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append("QUIT;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

        scriptSAS.append("data dados (reuse=yes compress=yes);", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "set dados;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append("run;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);
    }

    /**
     * Gera a parte inicial do Script SAS
     *
     * @throws Exception
     */
    private void inicializarScriptSAS() throws Exception {
        /*File arquivoCSV = new File(requestResponseFolder);
         if (!arquivoCSV.exists()) {
         throw new Exception("Diretório: " + requestResponseFolder + " não encontrado");
         }
         if (!arquivoCSV.isDirectory()) {
         throw new Exception(requestResponseFolder + " não é um diretorio.");
         }*/

        scriptSAS.append("%let leiaute ").append(leiaute).append(";", ScriptBuilder.LINE_SEPARATOR);
        //scriptSAS.append("%let requestFile ").append(requestResponseFolder).append("request_&leiaute..xml;", ScriptBuilder.LINE_SEPARATOR);
        //scriptSAS.append("%let responseFile ").append(requestResponseFolder).append("response_&leiaute..xml;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append("%let total 0;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

        if (coleta.getBanco().getTipo() == Banco.TIPO_ARQUIVO_SAS) {
            scriptSAS.append("LIBNAME historic base \"").append(coleta.getBanco().getEndereco()).append("\";", ScriptBuilder.LINE_SEPARATOR);
        } else if (coleta.getBanco().getTipo() == Banco.TIPO_BIBLIOTECA_SAS) {
            scriptSAS.append("LIBNAME historic META LIBRARY=").append(coleta.getBanco().getEndereco()).append(";", ScriptBuilder.LINE_SEPARATOR);
        }

        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

        scriptSAS.append("data mensagem_validacao;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "input metodo $ arquivo $ parametros $ mensagem $ aceitavel $;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "datalines;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, ";", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append("run;", ScriptBuilder.LINE_SEPARATOR);

        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

        if (coleta.getArquivos() == null) {

            scriptSAS.append("%let dirconforme ").append(coleta.getPasta().getEndereco()).append(";", ScriptBuilder.LINE_SEPARATOR);

            scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

            scriptSAS.append("DATA arquivos;", ScriptBuilder.LINE_SEPARATOR);
            scriptSAS.append(1, "DROP rc did i;", ScriptBuilder.LINE_SEPARATOR);
            scriptSAS.append(1, "rc=FILENAME(\"r_dlist\",\"&dirconforme\");", ScriptBuilder.LINE_SEPARATOR);
            scriptSAS.append(1, "did=DOPEN(\"r_dlist\");", ScriptBuilder.LINE_SEPARATOR);
            scriptSAS.append(1, "IF did > 0 THEN do;", ScriptBuilder.LINE_SEPARATOR);
            scriptSAS.append(2, "do i=1 to DNUM(did);", ScriptBuilder.LINE_SEPARATOR);
            scriptSAS.append(3, "nome_arquivo=DREAD(did,i);", ScriptBuilder.LINE_SEPARATOR);
            scriptSAS.append(3, "OUTPUT;", ScriptBuilder.LINE_SEPARATOR);
            scriptSAS.append(2, "END;", ScriptBuilder.LINE_SEPARATOR);
            scriptSAS.append(2, "rc=DCLOSE(did);", ScriptBuilder.LINE_SEPARATOR);
            scriptSAS.append(1, "END;", ScriptBuilder.LINE_SEPARATOR);
            scriptSAS.append(1, "ELSE PUT \'Could not open directory\';", ScriptBuilder.LINE_SEPARATOR);/*TODO: enviar mensagem de erro pelo arquivo de erro*/

            scriptSAS.append("run;", ScriptBuilder.LINE_SEPARATOR);

            if (coleta.getPasta().getLeiaute() != null && !coleta.getPasta().getLeiaute().isEmpty()) {
                scriptSAS.append("", ScriptBuilder.LINE_SEPARATOR);
                scriptSAS.append("proc sql;", ScriptBuilder.LINE_SEPARATOR);
                scriptSAS.append(1, "create table arquivos as", ScriptBuilder.LINE_SEPARATOR);
                scriptSAS.append(1, "select *", ScriptBuilder.LINE_SEPARATOR);
                scriptSAS.append(1, "from arquivos", ScriptBuilder.LINE_SEPARATOR);
                scriptSAS.append(1, "where UPCASE(nome_arquivo) contains UPCASE(\"&leiaute\");", ScriptBuilder.LINE_SEPARATOR);
                scriptSAS.append("QUIT;", ScriptBuilder.LINE_SEPARATOR);
            }

        } else {

            scriptSAS.append("DATA arquivos;", ScriptBuilder.LINE_SEPARATOR);
            scriptSAS.append(1, "infile datalines delimiter=',';", ScriptBuilder.LINE_SEPARATOR);
            scriptSAS.append(1, "input caminho  : $400. nome_arquivo : $400.;", ScriptBuilder.LINE_SEPARATOR);
            scriptSAS.append(1, "datalines;", ScriptBuilder.LINE_SEPARATOR);
            for (Arquivo arquivo : coleta.getArquivos()) {
                File file = new File(arquivo.getEndereco());
                scriptSAS.append(file.getAbsolutePath().trim()).append(",").append(file.getName().trim(), ScriptBuilder.LINE_SEPARATOR);
            }
            scriptSAS.append(1, ";", ScriptBuilder.LINE_SEPARATOR);
            scriptSAS.append("run;", ScriptBuilder.LINE_SEPARATOR);
        }
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

        scriptSAS.append("data _null_;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "set arquivos end=lastrec;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "if lastrec then do;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "call symput('total',_n_);", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "end;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append("run; ", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

        /*scriptSAS.append("%macro existArquivos;", ScriptBuilder.LINE_SEPARATOR);
         scriptSAS.append(1, "%put &total;", ScriptBuilder.LINE_SEPARATOR);
         scriptSAS.append(1, "%if &total > 0 %then %do;", ScriptBuilder.LINE_SEPARATOR);
         scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);*/
        scriptSAS.append("%macro assign_names;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "%do n=1 %to &total;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "%global nome_arquivo&n;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "data _null_;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(3, "set arquivos;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(3, "if &n=_n_; /* keep only the nth data record */", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(3, "call symput(\"nome_arquivo&n\",nome_arquivo);", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "run;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "%end; /* end of the %do-loop */", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append("%mend assign_names;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append("%assign_names;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

        scriptSAS.append("data dados;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "length", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "myfilename $250", ScriptBuilder.LINE_SEPARATOR);
        for (Coluna coluna : coleta.getColunas()) {
            scriptSAS.append(1, coluna.getNome()).append(" $").append(coluna.getTamanho()).append(ScriptBuilder.LINE_SEPARATOR);
        }
        scriptSAS.append(1, ";", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

        scriptSAS.append(1, "format", ScriptBuilder.LINE_SEPARATOR);
        for (Coluna coluna : coleta.getColunas()) {
            scriptSAS.append(1, coluna.getNome()).append(" $CHAR").append(coluna.getTamanho()).append(".", ScriptBuilder.LINE_SEPARATOR);
        }
        scriptSAS.append(1, ";", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

        scriptSAS.append(1, "set arquivos;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "filepath = ").append((coleta.getArquivos() == null ? "\"&dirconforme\"||nome_arquivo;" : "caminho;"), ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "infile dummy filevar = filepath length=reclen end=done missover lrecl=1048576", ScriptBuilder.LINE_SEPARATOR);
        ByteBuffer byteBuffer = ByteBuffer.wrap(Files.readAllBytes(Paths.get(coleta.getArquivo(0).getEndereco())));
        String encoding = "UTF8";
        if (isEncodingOk(byteBuffer, "UTF8")) {
            encoding = "UTF8";
        } else if (isEncodingOk(byteBuffer, "Cp1252")) {
            encoding = "ANSI";
        } else {
            throw new Exception("O arquivo de dados deve ter codificação UTF-8 ou ANSI para ser lido pelo Cassini.");
        }

        /*        try {
         r = new InputStreamReader(new FileInputStream(in));
         encoding = r.getEncoding();
         } catch (FileNotFoundException ex) {
         }*/
        scriptSAS.append("ENCODING=\"").append(encoding).append("\"", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "firstobs=2", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "DELIMITER=\';\'", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "MISSOVER", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "DSD;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "do while(not done);", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "myfilename = filepath;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "input", ScriptBuilder.LINE_SEPARATOR);
        for (Coluna coluna : coleta.getColunas()) {
            scriptSAS.append(3, "").append(coluna.getNome());
            scriptSAS.append(" : ?? $CHAR").append(coluna.getTamanho()).append(".", ScriptBuilder.LINE_SEPARATOR);
        }
        scriptSAS.append(1, ";", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "output;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "end;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append("run;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

        scriptSAS.append("data dados (reuse=yes compress=yes);", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "set dados;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append("run;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

        scriptSAS.append("PROC SQL; DROP TABLE arquivos;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append("QUIT;", ScriptBuilder.LINE_SEPARATOR);

        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);
        validarExpressaoRegular();
        boolean validarPeriodo = false;
        for (Coluna coluna : coleta.getColunas()) {
            switch (coluna.getClasse()) {
                case Coluna.CLASSE_ANO:
                case Coluna.CLASSE_MES:
                case Coluna.CLASSE_DIA:
                case Coluna.CLASSE_PERIODO:
                    validarPeriodo = true;
                    break;
            }
        }
        if (validarPeriodo) {
            //validarPeriodo();
        }
    }

    /**
     *
     * @param bytes
     * @return
     * @throws InvalidDataException
     */
    public static boolean isEncodingOk(ByteBuffer bytes, String encoding) throws Exception {
        CharsetDecoder decode = Charset.forName(encoding).newDecoder();
        decode.onMalformedInput(CodingErrorAction.REPORT);
        decode.onUnmappableCharacter(CodingErrorAction.REPORT);
        // decode.replaceWith( "X" );
        try {
            bytes.mark();
            decode.decode(bytes).toString();
            bytes.reset();
            return true;
        } catch (CharacterCodingException e) {
            return false;
        }

    }

    private void validarPeriodo() throws Exception {

        Coluna colunaCNPJ_CPF = null;
        Coluna colunaAno = null;
        Coluna colunaMes = null;
        Coluna colunaDia = null;
        Coluna colunaPeriodo = null;
        Coluna colunaCN = null;
        for (Coluna coluna : coleta.getColunas()) {
            switch (coluna.getClasse()) {
                case Coluna.CLASSE_ANO:
                    colunaAno = coluna;
                    break;
                case Coluna.CLASSE_CNPJ_CPF:
                    colunaCNPJ_CPF = coluna;
                    break;
                case Coluna.CLASSE_DIA:
                    colunaDia = coluna;
                    break;
                case Coluna.CLASSE_MES:
                    colunaMes = coluna;
                    break;
                case Coluna.CLASSE_MUNICIPIO:
                    break;
                case Coluna.CLASSE_CEP:
                    break;
                case Coluna.CLASSE_LOCALIDADE:
                    break;
                case Coluna.CLASSE_PERIODO:
                    colunaPeriodo = coluna;
                    break;
                case Coluna.CLASSE_CN:
                    colunaCN = coluna;
                    break;
            }
        }

        int delay = 0;
        String tipoPeriodo;
        String meses;
        switch (coleta.getTipoPeriodo()) {
            case Coleta.TIPO_PERIODO_ANUAL:
                delay = 12;
                tipoPeriodo = "anual";
                meses = "12";
                break;
            case Coleta.TIPO_PERIODO_SEMESTRAL:
                tipoPeriodo = "semestral";
                meses = "6 e 12";
                delay = 6;
                break;
            case Coleta.TIPO_PERIODO_TRIMESTRAL:
                tipoPeriodo = "trimestral";
                meses = "3, 6, 9 e 12";
                delay = 3;
                break;
            case Coleta.TIPO_PERIODO_MENSAL:
                tipoPeriodo = "mensal";
                meses = "1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11 e 12";
                delay = 1;
                break;
            case Coleta.TIPO_PERIODO_EVENTUAL:
                tipoPeriodo = "eventual";
                meses = "qualquer";
                delay = 1;
                break;
            default:
                throw new Exception("Tipo de período inválido");
        }

        scriptSAS.append("PROC SQL;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "CREATE TABLE WORK.PROVEDORA_E_PERIODO AS", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "SELECT DISTINCT", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "t1.myfilename,", ScriptBuilder.LINE_SEPARATOR);

        for (Coluna coluna : coleta.getColunas()) {
            if (coluna.isChaveAtualizacao()) {
                if (coluna.getClasse() != Coluna.CLASSE_ANO && coluna.getClasse() != Coluna.CLASSE_MES && coluna.getClasse() != Coluna.CLASSE_DIA && coluna.getClasse() != Coluna.CLASSE_PERIODO) {
                    scriptSAS.append(2, "t1.").append(coluna.getNome()).append(",", ScriptBuilder.LINE_SEPARATOR);
                }
            }
        }

        if (colunaPeriodo != null) {
            scriptSAS.append(2, "t1.").append(colunaPeriodo.getNome()).append(",", ScriptBuilder.LINE_SEPARATOR);
            scriptSAS.append(2, "t1.").append(colunaPeriodo.getNome()).append(" FORMAT=ptgdfmy7. as periodo,", ScriptBuilder.LINE_SEPARATOR);
        } else if (colunaDia != null && colunaAno != null && colunaMes != null) {
            scriptSAS.append(2, "t1.").append(colunaAno.getNome()).append(",", ScriptBuilder.LINE_SEPARATOR);
            scriptSAS.append(2, "t1.").append(colunaMes.getNome()).append(",", ScriptBuilder.LINE_SEPARATOR);
            scriptSAS.append(2, "t1.").append(colunaDia.getNome()).append(",", ScriptBuilder.LINE_SEPARATOR);
            scriptSAS.append(2, "mdy(t1.").append(colunaMes.getNome()).append(",t1.").append(colunaDia.getNome()).append(",t1.").append(colunaAno.getNome()).append(") FORMAT=ptgdfmy7. AS periodo,", ScriptBuilder.LINE_SEPARATOR);
        } else if (colunaAno != null && colunaMes != null) {
            scriptSAS.append(2, "t1.").append(colunaAno.getNome()).append(",", ScriptBuilder.LINE_SEPARATOR);
            scriptSAS.append(2, "t1.").append(colunaMes.getNome()).append(",", ScriptBuilder.LINE_SEPARATOR);
            scriptSAS.append(2, "mdy(t1.").append(colunaMes.getNome()).append(",1,t1.").append(colunaAno.getNome()).append(") FORMAT=ptgdfmy7. AS periodo,", ScriptBuilder.LINE_SEPARATOR);
        } else if (colunaAno != null) {
            scriptSAS.append(2, "t1.").append(colunaAno.getNome()).append(",", ScriptBuilder.LINE_SEPARATOR);
            scriptSAS.append(2, "mdy(12,1,t1.").append(colunaAno.getNome()).append(") FORMAT=ptgdfmy7. AS periodo,", ScriptBuilder.LINE_SEPARATOR);
        }

        scriptSAS.append(2, "t1.nome_arquivo", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "FROM WORK.DADOS t1;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append("QUIT;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

        scriptSAS.append("data PROVEDORA_E_PERIODO (reuse=yes compress=yes);", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "set PROVEDORA_E_PERIODO;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append("run;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

        scriptSAS.append("PROC SQL;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "CREATE TABLE WORK.PROVEDORA_E_PERIODO AS", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "SELECT DISTINCT", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "t1.myfilename,", ScriptBuilder.LINE_SEPARATOR);

        for (Coluna coluna : coleta.getColunas()) {
            if (coluna.isChaveAtualizacao()) {
                if (coluna.getClasse() != Coluna.CLASSE_ANO && coluna.getClasse() != Coluna.CLASSE_MES && coluna.getClasse() != Coluna.CLASSE_DIA && coluna.getClasse() != Coluna.CLASSE_PERIODO) {
                    scriptSAS.append(2, "t1.").append(coluna.getNome()).append(",", ScriptBuilder.LINE_SEPARATOR);
                }
            }
        }

        if (colunaDia != null && colunaAno != null && colunaMes != null) {
            scriptSAS.append(2, "t1.").append(colunaAno.getNome()).append(",", ScriptBuilder.LINE_SEPARATOR);
            scriptSAS.append(2, "t1.").append(colunaMes.getNome()).append(",", ScriptBuilder.LINE_SEPARATOR);
            scriptSAS.append(2, "t1.").append(colunaDia.getNome()).append(",", ScriptBuilder.LINE_SEPARATOR);
        } else if (colunaAno != null && colunaMes != null) {
            scriptSAS.append(2, "t1.").append(colunaAno.getNome()).append(",", ScriptBuilder.LINE_SEPARATOR);
            scriptSAS.append(2, "t1.").append(colunaMes.getNome()).append(",", ScriptBuilder.LINE_SEPARATOR);
        } else if (colunaAno != null) {
            scriptSAS.append(2, "t1.").append(colunaAno.getNome()).append(",", ScriptBuilder.LINE_SEPARATOR);
        }
        scriptSAS.append(2, "t1.periodo,", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "t1.nome_arquivo,", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "count(t1.periodo) as quantidade", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "FROM WORK.PROVEDORA_E_PERIODO t1", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "group by", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "t1.myfilename,", ScriptBuilder.LINE_SEPARATOR);

        for (Coluna coluna : coleta.getColunas()) {
            if (coluna.isChaveAtualizacao()) {
                if (coluna.getClasse() != Coluna.CLASSE_ANO && coluna.getClasse() != Coluna.CLASSE_MES && coluna.getClasse() != Coluna.CLASSE_DIA && coluna.getClasse() != Coluna.CLASSE_PERIODO) {
                    scriptSAS.append(2, "t1.").append(coluna.getNome()).append(",", ScriptBuilder.LINE_SEPARATOR);
                }
            }
        }

        scriptSAS.append(2, "t1.nome_arquivo;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append("QUIT;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

        scriptSAS.append("data PROVEDORA_E_PERIODO (reuse=yes compress=yes);", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "set PROVEDORA_E_PERIODO;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append("run;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

        final StackTraceElement[] ste = Thread.currentThread().getStackTrace();
        scriptSAS.append("PROC SQL;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "create table mensagem_validacao as", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "SELECT distinct \"").append(ste[1].getMethodName()).append("\" as metodo,", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "scan(t1.nome_arquivo,1,'_') as arquivo,", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "cat('");

        for (Coluna coluna : coleta.getColunas()) {
            if (coluna.isChaveAtualizacao()) {
                if (coluna.getClasse() != Coluna.CLASSE_ANO && coluna.getClasse() != Coluna.CLASSE_MES && coluna.getClasse() != Coluna.CLASSE_DIA && coluna.getClasse() != Coluna.CLASSE_PERIODO) {
                    scriptSAS.append(coluna.getNome()).append("', ' = ', t1.").append(coluna.getNome()).append(",';");
                }
            }
        }

        if (colunaPeriodo != null) {
            scriptSAS.append(colunaPeriodo.getNome()).append("', ' = ', t1.").append(colunaPeriodo.getNome()).append(") as parametros,", ScriptBuilder.LINE_SEPARATOR);
        } else {
            if (colunaDia != null) {
                scriptSAS.append(colunaDia.getNome()).append("', ' = ', t1.").append(colunaDia.getNome()).append(",';");
            }
            if (colunaMes != null) {
                scriptSAS.append(colunaMes.getNome()).append("', ' = ', t1.").append(colunaMes.getNome()).append(",';");
            }
            if (colunaAno != null) {
                scriptSAS.append(colunaAno.getNome()).append("', ' = ', t1.").append(colunaAno.getNome()).append(") as parametros,", ScriptBuilder.LINE_SEPARATOR);
            }
        }
        scriptSAS.append(2, "cat('Há ',t1.quantidade,' periodos no arquivo para uma mesma provedora. Só é permitido 1 período por provedora por arquivo.') as mensagem,", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "'false' as aceitavel", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "from PROVEDORA_E_PERIODO t1", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "where quantidade > 1", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "UNION ALL select * from mensagem_validacao;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append("quit;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

        scriptSAS.append("PROC SQL;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "create table mensagem_validacao as", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "SELECT distinct \"").append(ste[1].getMethodName()).append("\" as metodo,", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "scan(t1.nome_arquivo,1,'_') as arquivo,", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "cat('");

        for (Coluna coluna : coleta.getColunas()) {
            if (coluna.isChaveAtualizacao()) {
                if (coluna.getClasse() != Coluna.CLASSE_ANO && coluna.getClasse() != Coluna.CLASSE_MES && coluna.getClasse() != Coluna.CLASSE_DIA && coluna.getClasse() != Coluna.CLASSE_PERIODO) {
                    scriptSAS.append(coluna.getNome()).append("',' = ', t1.").append(coluna.getNome()).append(",';");
                }
            }
        }

        if (colunaPeriodo != null) {
            scriptSAS.append(colunaPeriodo.getNome()).append("',' = ', t1.").append(colunaPeriodo.getNome()).append(") as parametros,", ScriptBuilder.LINE_SEPARATOR);
        } else {
            if (colunaDia != null) {
                scriptSAS.append(colunaDia.getNome()).append("', ' = ', t1.").append(colunaDia.getNome()).append(",';");
            }
            if (colunaMes != null) {
                scriptSAS.append(colunaMes.getNome()).append("', ' = ', t1.").append(colunaMes.getNome()).append(",';");
            }
            if (colunaAno != null) {
                scriptSAS.append(colunaAno.getNome()).append("', ' = ', t1.").append(colunaAno.getNome()).append(") as parametros,", ScriptBuilder.LINE_SEPARATOR);
            }
        }
        scriptSAS.append(2, "'Esta é uma coleta ").append(tipoPeriodo).append(", portanto apenas são aceitos valores para os seguintes meses: ").append(meses).append("' as mensagem,", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "'false' as aceitavel", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "from PROVEDORA_E_PERIODO t1", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "where mod(month(t1.periodo),").append(delay).append(") ^= 0", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "UNION ALL select * from mensagem_validacao;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append("quit;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

        scriptSAS.append("proc sql;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "create table proximo_periodo as", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "select distinct", ScriptBuilder.LINE_SEPARATOR);

        for (Coluna coluna : coleta.getColunas()) {
            if (coluna.isChaveAtualizacao()) {
                if (coluna.getClasse() != Coluna.CLASSE_ANO && coluna.getClasse() != Coluna.CLASSE_MES && coluna.getClasse() != Coluna.CLASSE_DIA && coluna.getClasse() != Coluna.CLASSE_PERIODO) {
                    scriptSAS.append(2, "t1.").append(coluna.getNome()).append(",", ScriptBuilder.LINE_SEPARATOR);
                }
            }
        }

        if (colunaPeriodo != null) {
            scriptSAS.append(2, "max(t1.").append(colunaPeriodo.getNome()).append("),", ScriptBuilder.LINE_SEPARATOR);
            scriptSAS.append(2, "intnx('month',max(t1.").append(colunaPeriodo.getNome()).append("),").append(delay).append(") as proximo_periodo", ScriptBuilder.LINE_SEPARATOR);
        } else if (colunaDia != null && colunaAno != null && colunaMes != null) {
            scriptSAS.append(2, "max(t1.").append(colunaAno.getNome()).append("),", ScriptBuilder.LINE_SEPARATOR);
            scriptSAS.append(2, "max(t1.").append(colunaMes.getNome()).append("),", ScriptBuilder.LINE_SEPARATOR);
            scriptSAS.append(2, "max(t1.").append(colunaDia.getNome()).append("),", ScriptBuilder.LINE_SEPARATOR);
            scriptSAS.append(2, "intnx('month',mdy(max(t1.").append(colunaMes.getNome()).append("),max(").append(colunaDia.getNome()).append("),max(t1.").append(colunaAno.getNome()).append(")),").append(delay).append(") AS proximo_periodo", ScriptBuilder.LINE_SEPARATOR);
        } else if (colunaAno != null && colunaMes != null) {
            scriptSAS.append(2, "max(t1.").append(colunaAno.getNome()).append("),", ScriptBuilder.LINE_SEPARATOR);
            scriptSAS.append(2, "max(t1.").append(colunaMes.getNome()).append("),", ScriptBuilder.LINE_SEPARATOR);
            scriptSAS.append(2, "intnx('month',mdy(max(t1.").append(colunaMes.getNome()).append("),1,max(t1.").append(colunaAno.getNome()).append(")),").append(delay).append(") AS proximo_periodo", ScriptBuilder.LINE_SEPARATOR);
        } else if (colunaAno != null) {
            scriptSAS.append(2, "max(t1.").append(colunaAno.getNome()).append("),", ScriptBuilder.LINE_SEPARATOR);
            scriptSAS.append(2, "intnx('month',mdy(12,1,max(t1.").append(colunaAno.getNome()).append(")),").append(delay).append(") AS proximo_periodo", ScriptBuilder.LINE_SEPARATOR);
        }
        scriptSAS.append(1, "FROM historic.").append(coleta.getBanco().getTabela()).append(" t1;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append("QUIT;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

        if (coleta.getTipoPeriodo() != Coleta.TIPO_PERIODO_EVENTUAL) {

            scriptSAS.append("PROC SQL;", ScriptBuilder.LINE_SEPARATOR);
            scriptSAS.append(1, "create table mensagem_validacao as", ScriptBuilder.LINE_SEPARATOR);
            scriptSAS.append(1, "SELECT distinct \"").append(ste[1].getMethodName()).append("\" as metodo,", ScriptBuilder.LINE_SEPARATOR);
            scriptSAS.append(2, "scan(t1.nome_arquivo,1,'_') as arquivo,", ScriptBuilder.LINE_SEPARATOR);
            scriptSAS.append(2, "cat('");

            for (Coluna coluna : coleta.getColunas()) {
                if (coluna.isChaveAtualizacao()) {
                    if (coluna.getClasse() != Coluna.CLASSE_ANO && coluna.getClasse() != Coluna.CLASSE_MES && coluna.getClasse() != Coluna.CLASSE_DIA && coluna.getClasse() != Coluna.CLASSE_PERIODO) {
                        scriptSAS.append(coluna.getNome()).append("=', t1.").append(coluna.getNome()).append(",';");
                    }
                }
            }

            if (colunaPeriodo != null) {
                scriptSAS.append(colunaPeriodo.getNome()).append("=', t1.").append(colunaPeriodo.getNome()).append(") as parametros,", ScriptBuilder.LINE_SEPARATOR);
            } else {
                if (colunaDia != null) {
                    scriptSAS.append(colunaDia.getNome()).append("=', t1.").append(colunaDia.getNome()).append(",';");
                }
                if (colunaMes != null) {
                    scriptSAS.append(colunaMes.getNome()).append("=', t1.").append(colunaMes.getNome()).append(",';");
                }
                if (colunaAno != null) {
                    scriptSAS.append(colunaAno.getNome()).append("=', t1.").append(colunaAno.getNome()).append(") as parametros,", ScriptBuilder.LINE_SEPARATOR);
                }
            }
            scriptSAS.append(2, "cat('Só é possível informar dados para até o próximo período = ',day(t2.proximo_periodo),'/',month(t2.proximo_periodo),'/',year(t2.proximo_periodo)) as mensagem,", ScriptBuilder.LINE_SEPARATOR);
            scriptSAS.append(2, "'false' as aceitavel", ScriptBuilder.LINE_SEPARATOR);
            scriptSAS.append(1, "FROM PROVEDORA_E_PERIODO t1", ScriptBuilder.LINE_SEPARATOR);
            scriptSAS.append(1, "inner join proximo_periodo t2 on ");

            for (Coluna coluna : coleta.getColunas()) {
                if (coluna.isChaveAtualizacao()) {
                    if (coluna.getClasse() != Coluna.CLASSE_ANO && coluna.getClasse() != Coluna.CLASSE_MES && coluna.getClasse() != Coluna.CLASSE_DIA && coluna.getClasse() != Coluna.CLASSE_PERIODO) {
                        scriptSAS.append("t1.").append(coluna.getNome()).append(" = t2.").append(coluna.getNome()).append(" and ");
                    }
                }
            }

            scriptSAS.append("t1.periodo > t2.proximo_periodo", ScriptBuilder.LINE_SEPARATOR);
            scriptSAS.append(1, "where t2.proximo_periodo not is missing", ScriptBuilder.LINE_SEPARATOR);
            scriptSAS.append(1, "UNION ALL select * from mensagem_validacao;", ScriptBuilder.LINE_SEPARATOR);
            scriptSAS.append("quit;", ScriptBuilder.LINE_SEPARATOR);
            scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

        }

        scriptSAS.append("PROC SQL; DROP TABLE proximo_periodo;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append("QUIT;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

        scriptSAS.append("PROC SQL; DROP TABLE PROVEDORA_E_PERIODO;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append("QUIT;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

        scriptSAS.append("data mensagem_validacao (reuse=yes compress=yes);", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "set mensagem_validacao;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append("run;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

    }

    /**
     * Gera a parte final do script SAS.
     *
     * @throws Exception
     */
    private void finalizarScriptSAS() throws Exception {

        //Envia resultado para o SOAP
        /*scriptSAS.append("%macro call_web_service;", ScriptBuilder.LINE_SEPARATOR);
         scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);
         scriptSAS.append(1, "FILENAME request \"&requestFile\";", ScriptBuilder.LINE_SEPARATOR);
         scriptSAS.append(1, "FILENAME response \"&responseFile\";", ScriptBuilder.LINE_SEPARATOR);
         scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);
         scriptSAS.append(1, "DATA _NULL_;", ScriptBuilder.LINE_SEPARATOR);
         scriptSAS.append(2, "url=\"http://sistemasds.anatel.gov.br/dici/ValidacaoService?wsdl\";", ScriptBuilder.LINE_SEPARATOR);
         scriptSAS.append(2, "**SOAPACTION='GetCitiesByCountry';", ScriptBuilder.LINE_SEPARATOR);
         scriptSAS.append(2, "rc=SOAPWEB(\"request\",url,\"response\",,,,,,,,);", ScriptBuilder.LINE_SEPARATOR);
         scriptSAS.append(1, "RUN;", ScriptBuilder.LINE_SEPARATOR);
         scriptSAS.append("%mend call_web_service;", ScriptBuilder.LINE_SEPARATOR);
         scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);
         scriptSAS.append("%macro create_soap_envelope;", ScriptBuilder.LINE_SEPARATOR);
         scriptSAS.append(1, "%do k=1 %to &total;", ScriptBuilder.LINE_SEPARATOR);
         scriptSAS.append(2, "proc sql;", ScriptBuilder.LINE_SEPARATOR);
         scriptSAS.append(3, "create table arquivo_&k as", ScriptBuilder.LINE_SEPARATOR);
         scriptSAS.append(3, "select", ScriptBuilder.LINE_SEPARATOR);
         scriptSAS.append(4, "*", ScriptBuilder.LINE_SEPARATOR);
         scriptSAS.append(3, "from mensagem_validacao t1", ScriptBuilder.LINE_SEPARATOR);
         scriptSAS.append(3, "where t1.arquivo = cat('         <numeroArquivo>',SCAN(\"&&nome_arquivo&k\",1,'_'),'</numeroArquivo>');", ScriptBuilder.LINE_SEPARATOR);
         scriptSAS.append(2, "quit;", ScriptBuilder.LINE_SEPARATOR);
         scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);
         scriptSAS.append("data arquivo_&k (reuse=yes compress=yes);", ScriptBuilder.LINE_SEPARATOR);
         scriptSAS.append(1, "set arquivo_&k;", ScriptBuilder.LINE_SEPARATOR);
         scriptSAS.append("run;", ScriptBuilder.LINE_SEPARATOR);
         scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);
         scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);
         scriptSAS.append(2, "FILENAME request \"&requestFile\";", ScriptBuilder.LINE_SEPARATOR);
         scriptSAS.append(2, "FILENAME response \"&responseFile\";", ScriptBuilder.LINE_SEPARATOR);
         scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);
         scriptSAS.append(2, "data _null_;", ScriptBuilder.LINE_SEPARATOR);
         scriptSAS.append(3, "FILE request;", ScriptBuilder.LINE_SEPARATOR);
         scriptSAS.append(3, "if _N_ = 1 then do;", ScriptBuilder.LINE_SEPARATOR);
         scriptSAS.append(4, "**PUT '<?xml version=\"1.0\" encoding=\"windows - 1252\" ?>';", ScriptBuilder.LINE_SEPARATOR);
         scriptSAS.append(4, "PUT '<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:ws=\"http://ws.dici.anatel.gov.br/\">';", ScriptBuilder.LINE_SEPARATOR);
         scriptSAS.append(4, "PUT '   <soapenv:Header/>';", ScriptBuilder.LINE_SEPARATOR);
         scriptSAS.append(4, "PUT '   <soapenv:Body>';", ScriptBuilder.LINE_SEPARATOR);
         scriptSAS.append(4, "PUT '      <ws:reportarErros>';", ScriptBuilder.LINE_SEPARATOR);
         scriptSAS.append(4, "PUT '         <erros>';", ScriptBuilder.LINE_SEPARATOR);
         scriptSAS.append(3, "end;", ScriptBuilder.LINE_SEPARATOR);
         scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);
         scriptSAS.append(3, "do until (EOF_ERROS);", ScriptBuilder.LINE_SEPARATOR);
         scriptSAS.append(4, "SET arquivo_&k END=EOF_ERROS;", ScriptBuilder.LINE_SEPARATOR);
         scriptSAS.append(4, "if mensagem ^= '               <mensagem>Ok</mensagem>' then do;", ScriptBuilder.LINE_SEPARATOR);
         scriptSAS.append(5, "PUT '            <erro>';", ScriptBuilder.LINE_SEPARATOR);
         scriptSAS.append(5, "PUT mensagem;", ScriptBuilder.LINE_SEPARATOR);
         scriptSAS.append(5, "PUT metodo;", ScriptBuilder.LINE_SEPARATOR);
         scriptSAS.append(5, "PUT parametro;", ScriptBuilder.LINE_SEPARATOR);
         scriptSAS.append(5, "PUT aceitavel;", ScriptBuilder.LINE_SEPARATOR);
         scriptSAS.append(5, "PUT '            </erro>';", ScriptBuilder.LINE_SEPARATOR);
         scriptSAS.append(4, "end;", ScriptBuilder.LINE_SEPARATOR);
         scriptSAS.append(3, "end;", ScriptBuilder.LINE_SEPARATOR);
         scriptSAS.append(3, "PUT '         </erros>';", ScriptBuilder.LINE_SEPARATOR);
         scriptSAS.append(3, "PUT arquivo;", ScriptBuilder.LINE_SEPARATOR);
         scriptSAS.append(3, "PUT '      </ws:reportarErros>';", ScriptBuilder.LINE_SEPARATOR);
         scriptSAS.append(3, "PUT '   </soapenv:Body>';", ScriptBuilder.LINE_SEPARATOR);
         scriptSAS.append(3, "PUT '</soapenv:Envelope>';", ScriptBuilder.LINE_SEPARATOR);
         //scriptSAS.append(3, "%call_web_service;", ScriptBuilder.LINE_SEPARATOR);
         scriptSAS.append(2, "run;", ScriptBuilder.LINE_SEPARATOR);
         scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);
         scriptSAS.append(2, "PROC SQL; DROP TABLE arquivo_&k;", ScriptBuilder.LINE_SEPARATOR);
         scriptSAS.append(2, "QUIT;", ScriptBuilder.LINE_SEPARATOR);
         scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);
         scriptSAS.append(2, "data _null_;", ScriptBuilder.LINE_SEPARATOR);
         scriptSAS.append(3, "fname=\"tempfile\";", ScriptBuilder.LINE_SEPARATOR);
         scriptSAS.append(3, "rc=filename(fname,\"&requestFile\");", ScriptBuilder.LINE_SEPARATOR);
         scriptSAS.append(3, "if rc = 0 and fexist(fname) then", ScriptBuilder.LINE_SEPARATOR);
         scriptSAS.append(4, "rc=fdelete(fname);", ScriptBuilder.LINE_SEPARATOR);
         scriptSAS.append(3, "rc=filename(fname,\"&responseFile\");", ScriptBuilder.LINE_SEPARATOR);
         scriptSAS.append(3, "if rc = 0 and fexist(fname) then", ScriptBuilder.LINE_SEPARATOR);
         scriptSAS.append(4, "rc=fdelete(fname);", ScriptBuilder.LINE_SEPARATOR);
         scriptSAS.append(3, "rc=filename(fname);", ScriptBuilder.LINE_SEPARATOR);
         scriptSAS.append(2, "run;", ScriptBuilder.LINE_SEPARATOR);
         scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);
         scriptSAS.append(1, "%end;", ScriptBuilder.LINE_SEPARATOR);
         scriptSAS.append("%mend create_soap_envelope;", ScriptBuilder.LINE_SEPARATOR);
         scriptSAS.append("%create_soap_envelope;", ScriptBuilder.LINE_SEPARATOR);
         scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);*/
        scriptSAS.append("PROC SQL; DROP TABLE DADOS;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append("QUIT;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);
        /*scriptSAS.append("PROC SQL; DROP TABLE MENSAGEM_VALIDACAO;", ScriptBuilder.LINE_SEPARATOR);
         scriptSAS.append("QUIT;", ScriptBuilder.LINE_SEPARATOR);*/

        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

        scriptSAS.append(1, "%end;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append("%mend continua2;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append("%continua2;", ScriptBuilder.LINE_SEPARATOR);

        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

        scriptSAS.append(1, "%end;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append("%mend continua;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append("%continua;", ScriptBuilder.LINE_SEPARATOR);

        /*scriptSAS.append(1, "%end;", ScriptBuilder.LINE_SEPARATOR);
         scriptSAS.append(1, "%else %do;", ScriptBuilder.LINE_SEPARATOR);
         scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);
         scriptSAS.append(2, "PROC SQL; DROP TABLE ARQUIVOS;", ScriptBuilder.LINE_SEPARATOR);
         scriptSAS.append(2, "QUIT;", ScriptBuilder.LINE_SEPARATOR);
         scriptSAS.append(1, "%end;", ScriptBuilder.LINE_SEPARATOR);
         scriptSAS.append("%mend existArquivos;", ScriptBuilder.LINE_SEPARATOR);
         scriptSAS.append("%existArquivos;", ScriptBuilder.LINE_SEPARATOR);*/
    }

    /**
     * Salva o script SAS em um arquivo .sas
     *
     * @throws Exception
     */
    private void salvarScriptSAS() throws Exception {
        try {
            File sasProgramFile = new File(this.arquivoSAS);
            Writer writer = new OutputStreamWriter(new FileOutputStream(sasProgramFile), "Cp1252");
            writer.write(scriptSAS.toString());
            writer.flush();
            writer.close();
        } catch (Exception e) {
            e.printStackTrace();
            throw new Exception("Erro ao tentar salvar script em arquivo");
        }
    }

    /**
     * Tenta rodar o script SAS.
     *
     * @throws Exception
     */
    private List<Erro> rodarScriptSAS() throws Exception {
        ConexaoSAS sasc = new ConexaoSAS();
        sasc.addActionListener(sasActionListener);
        sasc.conectar(sasServer, sasPort, user, password);
        Connection conn = sasc.executarProcessFlow(scriptSAS.toString());

        LineType[] lineTypes = sasc.getLogLineTypes();
        String[] log = sasc.getLog();
        for (int i = 0; i < lineTypes.length; i++) {
            LineType lineType = lineTypes[i];
            if (lineType.value() == LineType._LineTypeError || log[i].startsWith("ERROR:")) {
                conn.close();
                sasActionListener.actionPerformed(new SASAction(SASAction.DESCONECTAR));
                throw new SASException(log[i]);
            }
        }

        ResultSet rs = conn.createStatement().executeQuery("SELECT * FROM MENSAGEM_VALIDACAO WHERE mensagem <> 'Ok'");
        List<Erro> erros = new ArrayList<>();
        while (rs.next()) {
            erros.add(new Erro(rs.getString("metodo").trim(), rs.getString("mensagem").trim(), rs.getString("arquivo").trim(), rs.getString("parametros").trim(), rs.getString("aceitavel").trim().equals("true")));
        }
        conn.close();
        conn = sasc.executarProcessFlow("PROC SQL; DROP TABLE MENSAGEM_VALIDACAO;QUIT;");
        conn.close();
        sasActionListener.actionPerformed(new SASAction(SASAction.DESCONECTAR));
        return erros;
    }

    /**
     * Apaga o script SAS. Útil para quando o Java for capaz de invocar a
     * execução do script.
     *
     * @throws Exception
     */
    private void apagaScriptSAS() throws Exception {
        try {

            File sasProgramFile = new File(this.arquivoSAS);
            Writer writer = new OutputStreamWriter(new FileOutputStream(sasProgramFile), "Cp1252");
            //write contents of StringBuffer to a file
            writer.write("**Nenhuma solicitação de validação pendente");
            //flush the stream
            writer.flush();
            //close the stream
            writer.close();

        } catch (Exception e) {
            throw new Exception("Erro ao tentar salvar script em arquivo");
        }
    }

    /**
     * Calcula a média e o desvio padrão dos últimos períodos e verifica se o
     * novo dado está dentro de 1 desvio_padrão da média.
     *
     * @param periodos
     * @throws Exception
     */
    private void media(int periodos, double desvioPadrao) throws Exception {
        scriptSAS.append("/*Validação da média*/", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);
    }

    private void maximo(double maximo) {
        scriptSAS.append("/*Validação de valor máximo*/", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);
    }

    private void minimo(double minimo) {
        scriptSAS.append("/*Validação de valor mínimo*/", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);
    }

    /**
     * Calcula o crescimento do novo dado em relação ao período anterior e
     * verifica se está entre um valor máximo e um valor mínimo de crescimento.
     *
     * @param limites
     * @throws Exception
     */
    private void crescimento(Limites limites) throws Exception {
        scriptSAS.append("/*Validação do crescimento*/", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

        scriptSAS.append("PROC SQL;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "CREATE TABLE WORK.DADOS_CONSOLIDADO AS ", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "SELECT t1.myfilename, ", ScriptBuilder.LINE_SEPARATOR);

        Coluna colunaCNPJ_CPF = null;
        Coluna colunaMunicipio = null;
        Coluna colunaCEP = null;
        Coluna colunaLocalidade = null;
        Coluna colunaDado = null;
        List<Coluna> colunasConsolidacao = new ArrayList<>();
        Coluna colunaAno = null;
        Coluna colunaMes = null;
        Coluna colunaDia = null;
        Coluna colunaPeriodo = null;
        Coluna colunaCN = null;
        for (Coluna coluna : coleta.getColunas()) {
            switch (coluna.getUso()) {
                case Coluna.USO_DADO:
                    if (colunaDado != null) {
                        throw new Exception("Não é permitido ter mais de uma coluna de dado. Você pode criar novo script de validação alterando o USO da coluna, mantendo sempre apenas uma coluna de dado.");
                    }
                    colunaDado = coluna;
                    break;
                case Coluna.USO_CONSOLIDACAO:
                    colunasConsolidacao.add(coluna);
                    switch (coluna.getClasse()) {
                        case Coluna.CLASSE_ANO:
                            colunaAno = coluna;
                            break;
                        case Coluna.CLASSE_CNPJ_CPF:
                            colunaCNPJ_CPF = coluna;
                            break;
                        case Coluna.CLASSE_DIA:
                            colunaDia = coluna;
                            break;
                        case Coluna.CLASSE_MES:
                            colunaMes = coluna;
                            break;
                        case Coluna.CLASSE_MUNICIPIO:
                            colunaMunicipio = coluna;
                            break;
                        case Coluna.CLASSE_CEP:
                            colunaCEP = coluna;
                            break;
                        case Coluna.CLASSE_LOCALIDADE:
                            colunaLocalidade = coluna;
                            break;
                        case Coluna.CLASSE_PERIODO:
                            colunaPeriodo = coluna;
                            break;
                        case Coluna.CLASSE_CN:
                            colunaCN = coluna;
                            break;
                    }
                    break;

            }
        }

        if (colunaDado == null) {
            throw new Exception("Nenhuma coluna de dado foi encontrada.");
        }

        int delay = 0;
        switch (coleta.getTipoPeriodo()) {
            case Coleta.TIPO_PERIODO_ANUAL:
                delay = 12;
                break;
            case Coleta.TIPO_PERIODO_SEMESTRAL:
                delay = 6;
                break;
            case Coleta.TIPO_PERIODO_TRIMESTRAL:
                delay = 3;
                break;
            case Coleta.TIPO_PERIODO_MENSAL:
                delay = 1;
                break;
            default:
                throw new Exception("Tipo de período inválido");
        }

        for (Coluna coluna : colunasConsolidacao) {
            scriptSAS.append(2, "t1.").append(coluna.getNome()).append(",", ScriptBuilder.LINE_SEPARATOR);
        }

        if (colunaPeriodo != null) {
            scriptSAS.append(2, "(INTNX('month', t1.").append(colunaPeriodo.getNome()).append(", -").append(delay).append(")) FORMAT=ptgdfmy7. as hitmonlee,", ScriptBuilder.LINE_SEPARATOR);
        } else if (colunaDia != null && colunaAno != null && colunaMes != null) {
            scriptSAS.append(2, "(INTNX('month', mdy(t1.").append(colunaMes.getNome()).append(",").append(colunaDia.getNome()).append(",t1.").append(colunaAno.getNome()).append("), -").append(delay).append(")) FORMAT=ptgdfmy7. AS hitmonlee,", ScriptBuilder.LINE_SEPARATOR);
        } else if (colunaAno != null && colunaMes != null) {
            scriptSAS.append(2, "(INTNX('month', mdy(t1.").append(colunaMes.getNome()).append(",1,t1.").append(colunaAno.getNome()).append("), -").append(delay).append(")) FORMAT=ptgdfmy7. AS hitmonlee,", ScriptBuilder.LINE_SEPARATOR);
        } else if (colunaAno != null) {
            scriptSAS.append(2, "(INTNX('month', mdy(12,1,t1.").append(colunaAno.getNome()).append("), -").append(delay).append(")) FORMAT=ptgdfmy7. AS hitmonlee,", ScriptBuilder.LINE_SEPARATOR);
        }

        scriptSAS.append(2, "(SUM(t1.").append(colunaDado.getNome()).append(")) FORMAT=BEST20. AS ").append(colunaDado.getNome()).append(",", ScriptBuilder.LINE_SEPARATOR);

        scriptSAS.append(2, "t1.nome_arquivo", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "FROM WORK.DADOS t1", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "GROUP BY t1.myfilename,", ScriptBuilder.LINE_SEPARATOR);
        for (Coluna coluna : coleta.getColunas()) {
            if (coluna.getUso() == Coluna.USO_CONSOLIDACAO) {
                scriptSAS.append(2, "t1.").append(coluna.getNome()).append(",", ScriptBuilder.LINE_SEPARATOR);
            }
        }
        if (colunaAno != null) {
            scriptSAS.append(2, "(CALCULATED hitmonlee),", ScriptBuilder.LINE_SEPARATOR);
        } else if (colunaPeriodo != null) {
            scriptSAS.append(2, "periodo,", ScriptBuilder.LINE_SEPARATOR);
        }
        scriptSAS.append(2, "t1.nome_arquivo;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append("QUIT;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

        scriptSAS.append("data DADOS_CONSOLIDADO (reuse=yes compress=yes);", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "set DADOS_CONSOLIDADO;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append("run;", ScriptBuilder.LINE_SEPARATOR);

        scriptSAS.append("PROC SQL;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "CREATE TABLE WORK.PROVEDORA_E_PERIODO AS", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "SELECT DISTINCT", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "t1.myfilename,", ScriptBuilder.LINE_SEPARATOR);

        for (Coluna coluna : coleta.getColunas()) {
            if (coluna.isChaveAtualizacao()) {
                if (coluna.getClasse() != Coluna.CLASSE_ANO && coluna.getClasse() != Coluna.CLASSE_MES && coluna.getClasse() != Coluna.CLASSE_DIA && coluna.getClasse() != Coluna.CLASSE_PERIODO) {
                    scriptSAS.append(2, "t1.").append(coluna.getNome()).append(",", ScriptBuilder.LINE_SEPARATOR);
                }
            }
        }

        if (colunaPeriodo != null) {
            scriptSAS.append(2, "(INTNX('month', t1.").append(colunaPeriodo.getNome()).append(", -").append(delay).append(")) FORMAT=ptgdfmy7. as hitmonlee,", ScriptBuilder.LINE_SEPARATOR);
        } else if (colunaDia != null && colunaAno != null && colunaMes != null) {
            scriptSAS.append(2, "(INTNX('month', mdy(t1.").append(colunaMes.getNome()).append(",").append(colunaDia.getNome()).append(",t1.").append(colunaAno.getNome()).append("), -").append(delay).append(")) FORMAT=ptgdfmy7. AS hitmonlee,", ScriptBuilder.LINE_SEPARATOR);
        } else if (colunaAno != null && colunaMes != null) {
            scriptSAS.append(2, "(INTNX('month', mdy(t1.").append(colunaMes.getNome()).append(",1,t1.").append(colunaAno.getNome()).append("), -").append(delay).append(")) FORMAT=ptgdfmy7. AS hitmonlee,", ScriptBuilder.LINE_SEPARATOR);
        } else if (colunaAno != null) {
            scriptSAS.append(2, "(INTNX('month', mdy(12,1,t1.").append(colunaAno.getNome()).append("), -").append(delay).append(")) FORMAT=ptgdfmy7. AS hitmonlee,", ScriptBuilder.LINE_SEPARATOR);
        }

        scriptSAS.append(2, "t1.nome_arquivo", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "FROM WORK.DADOS t1;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append("QUIT;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

        scriptSAS.append("data PROVEDORA_E_PERIODO (reuse=yes compress=yes);", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "set PROVEDORA_E_PERIODO;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append("run;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

        scriptSAS.append("PROC SQL;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "CREATE TABLE WORK.HISTORICO_CONSOLIDADO AS ", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "SELECT", ScriptBuilder.LINE_SEPARATOR);

        StringBuilder stringBuilder = new StringBuilder();

        for (Coluna coluna : colunasConsolidacao) {
            stringBuilder.append("t1.").append(coluna.getNome()).append(",");
        }

        if (colunaPeriodo != null) {
            stringBuilder.append("t1.").append(colunaPeriodo.getNome()).append(" as hitmonlee,");
        } else if (colunaDia != null && colunaAno != null && colunaMes != null) {
            stringBuilder.append("mdy(t1.").append(colunaMes.getNome()).append(",").append(colunaDia.getNome()).append(",t1.").append(colunaAno.getNome()).append(") FORMAT=ptgdfmy7. AS hitmonlee,");
        } else if (colunaAno != null && colunaMes != null) {
            stringBuilder.append("mdy(t1.").append(colunaMes.getNome()).append(",1,t1.").append(colunaAno.getNome()).append(") FORMAT=ptgdfmy7. AS hitmonlee,");
        } else if (colunaAno != null) {
            stringBuilder.append("mdy(12,1,t1.").append(colunaAno.getNome()).append(") FORMAT=ptgdfmy7. AS hitmonlee,");
        }

        stringBuilder.append("(SUM(t1.").append(colunaDado.getNome()).append(")) FORMAT=BEST20. AS ").append(colunaDado.getNome()).append(",");

        scriptSAS.append(2, stringBuilder.toString().substring(0, stringBuilder.toString().length() - 1), ScriptBuilder.LINE_SEPARATOR);

        scriptSAS.append(1, "FROM HISTORIC.").append(coleta.getBanco().getTabela()).append(" t1, WORK.PROVEDORA_E_PERIODO t2", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "WHERE ");

        for (Coluna coluna : coleta.getColunas()) {
            if (coluna.isChaveAtualizacao()) {
                if (coluna.getClasse() != Coluna.CLASSE_ANO && coluna.getClasse() != Coluna.CLASSE_MES && coluna.getClasse() != Coluna.CLASSE_DIA && coluna.getClasse() != Coluna.CLASSE_PERIODO) {
                    scriptSAS.append(2, "t1.").append(coluna.getNome()).append(" = t2.").append(coluna.getNome()).append(" AND ", ScriptBuilder.LINE_SEPARATOR);
                }
            }
        }

        stringBuilder = new StringBuilder();
        if (colunaPeriodo != null) {
            scriptSAS.append(stringBuilder.toString()).append("t1.").append(colunaPeriodo.getNome()).append(" = t2.hitmonlee").append(ScriptBuilder.LINE_SEPARATOR);
        } else {
            scriptSAS.append(stringBuilder.toString()).append("(CALCULATED hitmonlee) = t2.hitmonlee").append(ScriptBuilder.LINE_SEPARATOR);
        }
        scriptSAS.append(1, "GROUP BY", ScriptBuilder.LINE_SEPARATOR);
        for (Coluna coluna : coleta.getColunas()) {
            if (coluna.getUso() == Coluna.USO_CONSOLIDACAO) {
                scriptSAS.append(2, "t1.").append(coluna.getNome()).append(",", ScriptBuilder.LINE_SEPARATOR);
            }
        }
        if (colunaAno != null) {
            scriptSAS.append(2, "(CALCULATED hitmonlee)", ScriptBuilder.LINE_SEPARATOR);
        } else if (colunaPeriodo != null) {
            scriptSAS.append(2, "hitmonlee", ScriptBuilder.LINE_SEPARATOR);
        }
        scriptSAS.append(2, ";", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append("QUIT;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

        scriptSAS.append("data HISTORICO_CONSOLIDADO (reuse=yes compress=yes);", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "set HISTORICO_CONSOLIDADO;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append("run;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

        scriptSAS.append("PROC SQL;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "CREATE TABLE WORK.COMPARACAO_1 AS ", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "SELECT t1.myfilename, ", ScriptBuilder.LINE_SEPARATOR);

        for (Coluna coluna : colunasConsolidacao) {
            scriptSAS.append(2, "t1.").append(coluna.getNome()).append(",", ScriptBuilder.LINE_SEPARATOR);
        }

        scriptSAS.append(2, "t2.").append(colunaDado.getNome()).append(" AS dado_anterior, ", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "t1.").append(colunaDado.getNome()).append(" AS dado_novo, ", ScriptBuilder.LINE_SEPARATOR);

        if (colunaPeriodo != null || (colunaAno != null && colunaMes != null)) {
            scriptSAS.append(2, "t1.hitmonlee as hitmonlee,", ScriptBuilder.LINE_SEPARATOR);
        }

        scriptSAS.append(2, "t1.nome_arquivo, ", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "/* crescimento */", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "(case when t2.").append(colunaDado.getNome()).append(" = 0 and t1.").append(colunaDado.getNome()).append(" = 0 then 0 else", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "case when t2.").append(colunaDado.getNome()).append(" not is missing and t1.").append(colunaDado.getNome()).append(" not is missing then t1.").append(colunaDado.getNome()).append("/t2.").append(colunaDado.getNome()).append("-1 else", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "case when t1.").append(colunaDado.getNome()).append(" is missing then -1 else", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "case when t2.").append(colunaDado.getNome()).append(" is missing then 0 end", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "end", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "end", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "end) AS crescimento,", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "(case when t2.").append(colunaDado.getNome()).append(" is missing or t2.").append(colunaDado.getNome()).append(" = 0 then 1 else", ScriptBuilder.LINE_SEPARATOR);
        for (int i = 0; i < limites.size(); i++) {
            double inicio = limites.getInicio(i);
            double fim = limites.getFim(i);
            int cod = limites.getCod(i);
            if (inicio > Double.NEGATIVE_INFINITY && fim < Double.POSITIVE_INFINITY) {
                scriptSAS.append(2, "case when t2.").append(colunaDado.getNome()).append(" >= ").append(inicio).append(" and t2.").append(colunaDado.getNome()).append(" < ").append(fim).append(" then ").append(cod).append(" else", ScriptBuilder.LINE_SEPARATOR);
            }
            if (inicio == Double.NEGATIVE_INFINITY && fim < Double.POSITIVE_INFINITY) {
                scriptSAS.append(2, "case when t2.").append(colunaDado.getNome()).append(" < ").append(fim).append(" then ").append(cod).append(" else", ScriptBuilder.LINE_SEPARATOR);
            }
            if (inicio > Double.NEGATIVE_INFINITY && fim == Double.POSITIVE_INFINITY) {
                scriptSAS.append(2, "case when t2.").append(colunaDado.getNome()).append(" >= ").append(inicio).append(" then ").append(cod, ScriptBuilder.LINE_SEPARATOR);
            }
            if (inicio == Double.NEGATIVE_INFINITY && fim == Double.POSITIVE_INFINITY) {
                scriptSAS.append(2, "").append(cod, ScriptBuilder.LINE_SEPARATOR);
            }
        }
        scriptSAS.append(2, "end", ScriptBuilder.LINE_SEPARATOR);
        for (int i = 0; i < limites.size(); i++) {
            scriptSAS.append(2, "end", ScriptBuilder.LINE_SEPARATOR);
        }
        scriptSAS.append(2, ") as cod", ScriptBuilder.LINE_SEPARATOR);

        scriptSAS.append(1, "FROM WORK.DADOS_CONSOLIDADO t1", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "LEFT JOIN WORK.HISTORICO_CONSOLIDADO t2 ON", ScriptBuilder.LINE_SEPARATOR);

        for (Coluna coluna : colunasConsolidacao) {
            if (coluna.getClasse() != Coluna.CLASSE_ANO && coluna.getClasse() != Coluna.CLASSE_MES && coluna.getClasse() != Coluna.CLASSE_DIA) {
                scriptSAS.append(2, "t1.").append(coluna.getNome()).append(" = t2.").append(coluna.getNome()).append(" AND ", ScriptBuilder.LINE_SEPARATOR);
            }
        }

        scriptSAS.append(2, "t1.hitmonlee = t2.hitmonlee;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append("QUIT;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

        scriptSAS.append("data COMPARACAO_1 (reuse=yes compress=yes);", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "set COMPARACAO_1;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append("run;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

        scriptSAS.append("PROC SQL;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append("CREATE TABLE WORK.COMPARACAO_2 AS ", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append("SELECT", ScriptBuilder.LINE_SEPARATOR);

        for (Coluna coluna : colunasConsolidacao) {
            if (coluna.getClasse() != Coluna.CLASSE_ANO && coluna.getClasse() != Coluna.CLASSE_MES && coluna.getClasse() != Coluna.CLASSE_DIA) {
                scriptSAS.append(2, "t2.").append(coluna.getNome()).append(",", ScriptBuilder.LINE_SEPARATOR);
            }
        }

        scriptSAS.append(2, "t2.hitmonlee,", ScriptBuilder.LINE_SEPARATOR);
        if (colunaPeriodo != null) {
            scriptSAS.append(2, "(year(INTNX('month', t2.hitmonlee,").append(delay).append("))) as ").append(colunaPeriodo.getNome()).append(",", ScriptBuilder.LINE_SEPARATOR);
        }
        if (colunaAno != null) {
            scriptSAS.append(2, "(year(INTNX('month', t2.hitmonlee,").append(delay).append("))) as ").append(colunaAno.getNome()).append(",", ScriptBuilder.LINE_SEPARATOR);
        }
        if (colunaMes != null) {
            scriptSAS.append(2, "(month(INTNX('month', t2.hitmonlee,").append(delay).append("))) as ").append(colunaMes.getNome()).append(",", ScriptBuilder.LINE_SEPARATOR);
        }
        if (colunaDia != null) {
            scriptSAS.append(2, "(day(INTNX('month', t2.hitmonlee,").append(delay).append("))) as ").append(colunaDia.getNome()).append(",", ScriptBuilder.LINE_SEPARATOR);
        }

        scriptSAS.append(2, "t2.").append(colunaDado.getNome()).append(" AS dado_anterior,", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "t1.").append(colunaDado.getNome()).append(" AS dado_novo,", ScriptBuilder.LINE_SEPARATOR);

        scriptSAS.append(2, "/* crescimento */", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "(case when t2.").append(colunaDado.getNome()).append(" = 0 and t1.").append(colunaDado.getNome()).append(" = 0 then 0 else", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "case when t2.").append(colunaDado.getNome()).append(" not is missing and t1.").append(colunaDado.getNome()).append(" not is missing then t1.").append(colunaDado.getNome()).append("/t2.").append(colunaDado.getNome()).append("-1 else", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "case when t1.").append(colunaDado.getNome()).append(" is missing then -1 else", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "case when t2.").append(colunaDado.getNome()).append(" is missing then 0 end", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "end", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "end", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "end) AS crescimento,", ScriptBuilder.LINE_SEPARATOR);

        scriptSAS.append(2, "/* cod */", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "(case when t2.").append(colunaDado.getNome()).append(" is missing or t2.").append(colunaDado.getNome()).append(" = 0 then 1 else", ScriptBuilder.LINE_SEPARATOR);
        for (int i = 0; i < limites.size(); i++) {
            double inicio = limites.getInicio(i);
            double fim = limites.getFim(i);
            int cod = limites.getCod(i);
            if (inicio > Double.NEGATIVE_INFINITY && fim < Double.POSITIVE_INFINITY) {
                scriptSAS.append(2, "case when t2.").append(colunaDado.getNome()).append(" >= ").append(inicio).append(" and t2.").append(colunaDado.getNome()).append(" < ").append(fim).append(" then ").append(cod).append(" else", ScriptBuilder.LINE_SEPARATOR);
            }
            if (inicio == Double.NEGATIVE_INFINITY && fim < Double.POSITIVE_INFINITY) {
                scriptSAS.append(2, "case when t2.").append(colunaDado.getNome()).append(" < ").append(fim).append(" then ").append(cod).append(" else", ScriptBuilder.LINE_SEPARATOR);
            }
            if (inicio > Double.NEGATIVE_INFINITY && fim == Double.POSITIVE_INFINITY) {
                scriptSAS.append(2, "case when t2.").append(colunaDado.getNome()).append(" >= ").append(inicio).append(" then ").append(cod, ScriptBuilder.LINE_SEPARATOR);
            }
            if (inicio == Double.NEGATIVE_INFINITY && fim == Double.POSITIVE_INFINITY) {
                scriptSAS.append(2, "").append(cod, ScriptBuilder.LINE_SEPARATOR);
            }
        }
        scriptSAS.append(2, "end", ScriptBuilder.LINE_SEPARATOR);
        for (int i = 0; i < limites.size(); i++) {
            scriptSAS.append(2, "end", ScriptBuilder.LINE_SEPARATOR);
        }
        scriptSAS.append(2, ") as cod", ScriptBuilder.LINE_SEPARATOR);

        scriptSAS.append(1, "FROM WORK.HISTORICO_CONSOLIDADO t2", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "LEFT JOIN WORK.DADOS_CONSOLIDADO t1 ON ", ScriptBuilder.LINE_SEPARATOR);

        for (Coluna coluna : colunasConsolidacao) {
            if (coluna.getClasse() != Coluna.CLASSE_ANO && coluna.getClasse() != Coluna.CLASSE_MES && coluna.getClasse() != Coluna.CLASSE_DIA) {
                scriptSAS.append(2, "t1.").append(coluna.getNome()).append(" = t2.").append(coluna.getNome()).append(" AND ", ScriptBuilder.LINE_SEPARATOR);
            }
        }

        scriptSAS.append(2, "t1.hitmonlee = t2.hitmonlee", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "WHERE ");
        stringBuilder = new StringBuilder();
        stringBuilder.append("t1.").append(colunaDado.getNome()).append(" IS MISSING AND ");

        scriptSAS.append(2, stringBuilder.toString().substring(0, stringBuilder.toString().length() - 5)).append(";", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append("QUIT;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

        scriptSAS.append("data COMPARACAO_2 (reuse=yes compress=yes);", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "set COMPARACAO_2;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append("run;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

        scriptSAS.append("PROC SQL;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "CREATE TABLE WORK.COMPARACAO_2 AS", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "SELECT t2.myfilename,t1.*,t2.nome_arquivo", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "FROM WORK.COMPARACAO_2 t1, WORK.PROVEDORA_E_PERIODO t2 WHERE ", ScriptBuilder.LINE_SEPARATOR);
        stringBuilder = new StringBuilder();
        for (Coluna coluna : coleta.getColunas()) {
            if (coluna.isChaveAtualizacao()) {
                if (coluna.getClasse() != Coluna.CLASSE_ANO && coluna.getClasse() != Coluna.CLASSE_MES && coluna.getClasse() != Coluna.CLASSE_DIA && coluna.getClasse() != Coluna.CLASSE_PERIODO) {
                    stringBuilder.append("t1.").append(coluna.getNome()).append(" = t2.").append(coluna.getNome()).append(" AND ");
                }
            }
        }
        if (stringBuilder.toString().isEmpty()) {
            scriptSAS.append(1, "t1.hitmonlee = t2.hitmonlee;", ScriptBuilder.LINE_SEPARATOR);
        } else {
            scriptSAS.append(1, stringBuilder.toString()).append("t1.hitmonlee = t2.hitmonlee;");
        }
        scriptSAS.append("QUIT;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

        scriptSAS.append("data COMPARACAO_2 (reuse=yes compress=yes);", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "set COMPARACAO_2;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append("run;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

        scriptSAS.append("PROC SQL; DROP TABLE PROVEDORA_E_PERIODO;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append("QUIT;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

        /*scriptSAS.append("PROC SQL;", ScriptBuilder.LINE_SEPARATOR);
         scriptSAS.append("CREATE TABLE WORK.COMPARACAO_2 AS ", ScriptBuilder.LINE_SEPARATOR);
         scriptSAS.append("SELECT DISTINCT t1.myfilename, ", ScriptBuilder.LINE_SEPARATOR);

         for (Coluna coluna : colunasConsolidacao) {
         scriptSAS.append(2, "t2.").append(coluna.getNome()).append(",", ScriptBuilder.LINE_SEPARATOR);
         }

         scriptSAS.append(2, "t2.hitmonlee, ", ScriptBuilder.LINE_SEPARATOR);
         scriptSAS.append(2, "t2.dado_anterior,", ScriptBuilder.LINE_SEPARATOR);
         scriptSAS.append(2, "t2.dado_novo, ", ScriptBuilder.LINE_SEPARATOR);
         scriptSAS.append(2, "t2.crescimento,", ScriptBuilder.LINE_SEPARATOR);
         scriptSAS.append(2, "t2.cod,", ScriptBuilder.LINE_SEPARATOR);

         scriptSAS.append(2, "t1.nome_arquivo", ScriptBuilder.LINE_SEPARATOR);
         scriptSAS.append(1, "FROM WORK.COMPARACAO_2 t2, WORK.DADOS_CONSOLIDADO t1", ScriptBuilder.LINE_SEPARATOR);
         scriptSAS.append(1, "WHERE", ScriptBuilder.LINE_SEPARATOR);

         stringBuilder = new StringBuilder();
         if (colunaCNPJ_CPF != null) {
         stringBuilder.append("t1.").append(colunaCNPJ_CPF.getNome()).append(" = t2.").append(colunaCNPJ_CPF.getNome()).append(" AND ");
         }
         if (colunaAno != null) {
         stringBuilder.append("t1.").append(colunaAno.getNome()).append(" = t2.").append(colunaAno.getNome()).append(" AND ");
         }
         if (colunaMes != null) {
         stringBuilder.append("t1.").append(colunaMes.getNome()).append(" = t2.").append(colunaMes.getNome()).append(" AND ");
         }
         if (colunaDia != null) {
         stringBuilder.append("t1.").append(colunaDia.getNome()).append(" = t2.").append(colunaDia.getNome()).append(" AND ");
         }
         if (colunaPeriodo != null) {
         stringBuilder.append("t1.").append(colunaPeriodo.getNome()).append(" = t2.").append(colunaPeriodo.getNome()).append(" AND ");
         }

         scriptSAS.append(2, stringBuilder.toString().substring(0, stringBuilder.toString().length() - 5)).append(";", ScriptBuilder.LINE_SEPARATOR);

         scriptSAS.append("QUIT;", ScriptBuilder.LINE_SEPARATOR);
         scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

         scriptSAS.append("data COMPARACAO_2 (reuse=yes compress=yes);", ScriptBuilder.LINE_SEPARATOR);
         scriptSAS.append(1,"set COMPARACAO_2;", ScriptBuilder.LINE_SEPARATOR);
         scriptSAS.append("run;", ScriptBuilder.LINE_SEPARATOR);
         scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);*/
        scriptSAS.append("PROC SQL; DROP TABLE DADOS_CONSOLIDADO;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append("QUIT;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append("PROC SQL; DROP TABLE HISTORICO_CONSOLIDADO;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append("QUIT;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append("PROC SQL;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "CREATE TABLE WORK.COMPARACAO AS ", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "SELECT * FROM WORK.COMPARACAO_1", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "OUTER UNION CORR ", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "SELECT * FROM WORK.COMPARACAO_2", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, ";", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append("Quit;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

        scriptSAS.append("data COMPARACAO (reuse=yes compress=yes);", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "set COMPARACAO;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append("run;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

        scriptSAS.append("PROC SQL; DROP TABLE COMPARACAO_1;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append("QUIT;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append("PROC SQL; DROP TABLE COMPARACAO_2;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append("QUIT;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

        scriptSAS.append("data limites (reuse=yes compress=yes);", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "length cod 8 inicial 8 final 8 maximo_per 8 minimo_per 8;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "cod=1;inicial=.;final=.;maximo_per=100.00;minimo_per=0.00;output;", ScriptBuilder.LINE_SEPARATOR);

        for (int i = 0; i < limites.size(); i++) {
            double inicio = limites.getInicio(i);
            double fim = limites.getFim(i);
            double crescimentoMaximo = limites.getCrescimentoMaximo(i);
            double crescimentoMinimo = limites.getCrescimentoMinimo(i);
            int cod = limites.getCod(i);

            if (inicio > Double.NEGATIVE_INFINITY && fim < Double.POSITIVE_INFINITY) {
                scriptSAS.append(1, "cod=").append(cod).append(";").append("inicial=").append(inicio).append(";").append("final=").append(fim).append(";").append("maximo_per=").append(crescimentoMaximo).append(";").append("minimo_per=").append(crescimentoMinimo).append(";output;", ScriptBuilder.LINE_SEPARATOR);
            }
            if (inicio == Double.NEGATIVE_INFINITY && fim < Double.POSITIVE_INFINITY) {
                scriptSAS.append(1, "cod=").append(cod).append(";").append("inicial=").append(".").append(";").append("final=").append(fim).append(";").append("maximo_per=").append(crescimentoMaximo).append(";").append("minimo_per=").append(crescimentoMinimo).append(";output;", ScriptBuilder.LINE_SEPARATOR);
            }
            if (inicio > Double.NEGATIVE_INFINITY && fim == Double.POSITIVE_INFINITY) {
                scriptSAS.append(1, "cod=").append(cod).append(";").append("inicial=").append(inicio).append(";").append("final=").append(".").append(";").append("maximo_per=").append(crescimentoMaximo).append(";").append("minimo_per=").append(crescimentoMinimo).append(";output;", ScriptBuilder.LINE_SEPARATOR);
            }
            if (inicio == Double.NEGATIVE_INFINITY && fim == Double.POSITIVE_INFINITY) {
                scriptSAS.append(1, "cod=").append(cod).append(";").append("inicial=").append(".").append(";").append("final=").append(".").append(";").append("maximo_per=").append(crescimentoMaximo).append(";").append("minimo_per=").append(crescimentoMinimo).append(";output;", ScriptBuilder.LINE_SEPARATOR);
            }
        }

        scriptSAS.append("run;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

        final StackTraceElement[] ste = Thread.currentThread().getStackTrace();
        scriptSAS.append("PROC SQL;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "CREATE TABLE WORK.mensagem_validacao AS ", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "SELECT distinct \"").append(ste[1].getMethodName()).append("\" as metodo,", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "SCAN(t2.nome_arquivo,1,'_') AS arquivo, ", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "/* parametro */", ScriptBuilder.LINE_SEPARATOR);

        scriptSAS.append(2, "(cat(catx(';',");
        stringBuilder = new StringBuilder();
        for (Coluna coluna : colunasConsolidacao) {
            if (coluna.getClasse() == Coluna.CLASSE_PERIODO) {
                stringBuilder.append("cat('").append(coluna.getNome()).append("=',put(t2.").append(coluna.getNome()).append(",ptgdfmy7.)),");
            } else {
                stringBuilder.append("cat('").append(coluna.getNome()).append("=',t2.").append(coluna.getNome()).append("),");
            }
        }
        scriptSAS.append(stringBuilder.toString().substring(0, stringBuilder.length() - 1)).append("))) AS parametros,", ScriptBuilder.LINE_SEPARATOR);

        scriptSAS.append(2, "/* mensagem_erro */", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "(case when t2.crescimento > t1.maximo_per then cat('").append(colunaDado.getNome()).append(" cresceu mais que ',t1.maximo_per*100,'%: ").append(colunaDado.getNome()).append(" anterior = ',t2.dado_anterior,'; ").append(colunaDado.getNome()).append(" novo = ',t2.dado_novo) else", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "case when t2.crescimento < t1.minimo_per then cat('").append(colunaDado.getNome()).append(" diminuiu mais que ',-t1.minimo_per*100,'%: ").append(colunaDado.getNome()).append(" anterior = ',t2.dado_anterior,'; ").append(colunaDado.getNome()).append(" novo = ',t2.dado_novo) else 'Ok'", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "end", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "end", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, ") AS mensagem,", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "'true' AS aceitavel", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "FROM WORK.COMPARACAO t2", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "INNER JOIN WORK.LIMITES t1 ON (t2.cod = t1.cod)", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "WHERE (CALCULATED mensagem) not contains 'Ok'", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "UNION ALL select * from mensagem_validacao;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append("QUIT;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

        scriptSAS.append("data mensagem_validacao (reuse=yes compress=yes);", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "set mensagem_validacao;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append("run;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

        scriptSAS.append("PROC SQL; DROP TABLE COMPARACAO;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append("QUIT;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append("PROC SQL; DROP TABLE LIMITES;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append("QUIT;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

    }

    /**
     * Verifica se o dado informado é maior que o denominador utilizado para o
     * cálculo de densidade. Verifica também se o dado da coleta anterior
     * consolidado por município é maior que o denominador utilizado.
     *
     * @param denominador
     * @param limites
     * @throws Exception
     */
    private void densidadeGeografica(int denominador, double multiplicador) throws Exception {
        scriptSAS.append("/*Validação da densidade demográfica*/", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

        Coluna colunaCNPJ_CPF = null;
        Coluna colunaMunicipio = null;
        Coluna colunaCEP = null;
        Coluna colunaLocalidade = null;
        Coluna colunaDado = null;
        List<Coluna> colunasConsolidacao = new ArrayList<>();
        Coluna colunaAno = null;
        Coluna colunaMes = null;
        Coluna colunaDia = null;
        Coluna colunaPeriodo = null;
        Coluna colunaCN = null;
        for (Coluna coluna : coleta.getColunas()) {
            switch (coluna.getUso()) {
                case Coluna.USO_DADO:
                    if (colunaDado != null) {
                        throw new Exception("Não é permitido ter mais de uma coluna de dado. Você pode criar novo script de validação alterando o USO da coluna, mantendo sempre apenas uma coluna de dado.");
                    }
                    colunaDado = coluna;
                    break;
                case Coluna.USO_CONSOLIDACAO:
                    colunasConsolidacao.add(coluna);
                    switch (coluna.getClasse()) {
                        case Coluna.CLASSE_ANO:
                            colunaAno = coluna;
                            break;
                        case Coluna.CLASSE_CNPJ_CPF:
                            colunaCNPJ_CPF = coluna;
                            break;
                        case Coluna.CLASSE_DIA:
                            colunaDia = coluna;
                            break;
                        case Coluna.CLASSE_MES:
                            colunaMes = coluna;
                            break;
                        case Coluna.CLASSE_MUNICIPIO:
                            colunaMunicipio = coluna;
                            break;
                        case Coluna.CLASSE_CEP:
                            colunaCEP = coluna;
                            break;
                        case Coluna.CLASSE_LOCALIDADE:
                            colunaLocalidade = coluna;
                            break;
                        case Coluna.CLASSE_PERIODO:
                            colunaPeriodo = coluna;
                            break;
                        case Coluna.CLASSE_CN:
                            colunaCN = coluna;
                            break;
                    }
                    break;

            }
        }

        if (colunaDado == null) {
            throw new Exception("Nenhuma coluna de dado foi encontrada.");
        }

        int delay = 0;
        switch (coleta.getTipoPeriodo()) {
            case Coleta.TIPO_PERIODO_ANUAL:
                delay = 12;
                break;
            case Coleta.TIPO_PERIODO_SEMESTRAL:
                delay = 6;
                break;
            case Coleta.TIPO_PERIODO_TRIMESTRAL:
                delay = 3;
                break;
            case Coleta.TIPO_PERIODO_MENSAL:
                delay = 1;
                break;
            default:
                throw new Exception("Tipo de período inválido");
        }

        scriptSAS.append("LIBNAME DB_DW META LIBRARY=DB_DW;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

        scriptSAS.append("proc sql;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "create table densidade_1 as", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "select distinct", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "t1.myfilename,", ScriptBuilder.LINE_SEPARATOR);

        if (colunaCNPJ_CPF == null) {
            throw new Exception("Nenhuma coluna de CNPJ ou CPF foi encontrada");
        }
        scriptSAS.append(2, "t1.").append(colunaCNPJ_CPF.getNome()).append(",", ScriptBuilder.LINE_SEPARATOR);

        if (colunaMunicipio == null) {
            throw new Exception("Nenhuma coluna de município foi encontrada");
        }
        scriptSAS.append(2, "t1.").append(colunaMunicipio.getNome()).append(",", ScriptBuilder.LINE_SEPARATOR);

        if (colunaPeriodo != null) {
            scriptSAS.append(2, "t1.").append(colunaPeriodo.getNome()).append(",", ScriptBuilder.LINE_SEPARATOR);
            scriptSAS.append(2, "intnx('month',t1.").append(colunaPeriodo.getNome()).append(",-").append(delay).append(") format=ptgdfmy7. as hitmonlee,", ScriptBuilder.LINE_SEPARATOR);
        } else if (colunaDia != null && colunaMes != null && colunaAno != null) {
            scriptSAS.append(2, "t1.").append(colunaAno.getNome()).append(",", ScriptBuilder.LINE_SEPARATOR);
            scriptSAS.append(2, "t1.").append(colunaMes.getNome()).append(",", ScriptBuilder.LINE_SEPARATOR);
            scriptSAS.append(2, "t1.").append(colunaDia.getNome()).append(",", ScriptBuilder.LINE_SEPARATOR);
            scriptSAS.append(2, "intnx('month',mdy(t1.").append(colunaMes.getNome()).append(",t1.").append(colunaDia.getNome()).append(",t1.").append(colunaAno.getNome()).append("),-").append(delay).append(") format=ptgdfmy7. as hitmonlee,", ScriptBuilder.LINE_SEPARATOR);
        } else if (colunaMes != null && colunaAno != null) {
            scriptSAS.append(2, "t1.").append(colunaAno.getNome()).append(",", ScriptBuilder.LINE_SEPARATOR);
            scriptSAS.append(2, "t1.").append(colunaMes.getNome()).append(",", ScriptBuilder.LINE_SEPARATOR);
            scriptSAS.append(2, "intnx('month',mdy(t1.").append(colunaMes.getNome()).append(",1,t1.").append(colunaAno.getNome()).append("),-").append(delay).append(") format=ptgdfmy7. as hitmonlee,", ScriptBuilder.LINE_SEPARATOR);
        } else if (colunaAno != null) {
            scriptSAS.append(2, "t1.").append(colunaAno.getNome()).append(",", ScriptBuilder.LINE_SEPARATOR);
            scriptSAS.append(2, "intnx('month',mdy(12,1,t1.").append(colunaAno.getNome()).append("),-").append(delay).append(") format=ptgdfmy7. as hitmonlee,", ScriptBuilder.LINE_SEPARATOR);
        }

        scriptSAS.append(2, "sum(t1.").append(colunaDado.getNome()).append(") as ").append(colunaDado.getNome()).append(",", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "t1.nome_arquivo", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "from work.dados t1", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "group by", ScriptBuilder.LINE_SEPARATOR);

        scriptSAS.append(2, "t1.").append(colunaCNPJ_CPF.getNome()).append(",", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "t1.").append(colunaMunicipio.getNome()).append(",", ScriptBuilder.LINE_SEPARATOR);

        StringBuilder stringBuilder = new StringBuilder();
        if (colunaPeriodo != null) {
            stringBuilder.append("t1.").append(colunaPeriodo.getNome()).append(",");
        }
        if (colunaDia != null) {
            stringBuilder.append("t1.").append(colunaDia.getNome()).append(",");
        }
        if (colunaMes != null) {
            stringBuilder.append("t1.").append(colunaMes.getNome()).append(",");
        }
        if (colunaAno != null) {
            stringBuilder.append("t1.").append(colunaAno.getNome()).append(",");
        }

        scriptSAS.append(2, stringBuilder.toString().substring(0, stringBuilder.toString().length() - 1)).append(";", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append("quit;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

        scriptSAS.append("data densidade_1 (reuse=yes compress=yes);", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "set densidade_1;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append("run;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

        scriptSAS.append("proc sql;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "create table densidade_2 as", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "select distinct", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "t3.cod_municipio_ibge as ").append(colunaMunicipio.getNome()).append(",", ScriptBuilder.LINE_SEPARATOR);

        if (colunaPeriodo != null) {
            scriptSAS.append(2, "mdy(t2.num_mes,t2.num_dia,t2.num_ano) format=ptgdfmy7. as hitmonlee,", ScriptBuilder.LINE_SEPARATOR);
        } else if (colunaDia != null && colunaMes != null && colunaAno != null) {
            scriptSAS.append(2, "mdy(t2.num_mes,t2.num_dia,t2.num_ano) format=ptgdfmy7. as hitmonlee,", ScriptBuilder.LINE_SEPARATOR);
        } else if (colunaMes != null && colunaAno != null) {
            scriptSAS.append(2, "mdy(t2.num_mes,1,t2.num_ano) format=ptgdfmy7. as hitmonlee,", ScriptBuilder.LINE_SEPARATOR);
        } else if (colunaAno != null) {
            scriptSAS.append(2, "mdy(12,1,t2.num_ano) format=ptgdfmy7. as hitmonlee,", ScriptBuilder.LINE_SEPARATOR);
        }

        scriptSAS.append(2, "sum(t1.total_").append(denominador == DENOMINADOR_DOMICILIO ? "domicilio" : "populacao").append(") as denominador", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "from db_dw.fato_ibge_estimada t1", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "inner join db_dw.dim_tempo t2 on t1.id_tempo = t2.id_tempo", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "inner join db_dw.dim_municipio t3 on t1.id_municipio = t3.id_municipio", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "group by", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "t3.cod_municipio_ibge,", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "t1.id_tempo;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append("quit;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

        scriptSAS.append("data densidade_2 (reuse=yes compress=yes);", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "set densidade_2;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append("run;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

        scriptSAS.append("proc sql;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "create table densidade as", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "select distinct", ScriptBuilder.LINE_SEPARATOR);

        scriptSAS.append(2, "t1.myfilename,", ScriptBuilder.LINE_SEPARATOR);

        if (colunaCNPJ_CPF == null) {
            throw new Exception("Nenhuma coluna de CNPJ ou CPF foi encontrada");
        }
        scriptSAS.append(2, "t1.").append(colunaCNPJ_CPF.getNome()).append(",", ScriptBuilder.LINE_SEPARATOR);

        if (colunaMunicipio == null) {
            throw new Exception("Nenhuma coluna de município foi encontrada");
        }
        scriptSAS.append(2, "t1.").append(colunaMunicipio.getNome()).append(",", ScriptBuilder.LINE_SEPARATOR);

        if (colunaPeriodo != null) {
            scriptSAS.append(2, "t1.").append(colunaPeriodo.getNome()).append(",", ScriptBuilder.LINE_SEPARATOR);
        } else if (colunaDia != null && colunaMes != null && colunaAno != null) {
            scriptSAS.append(2, "t1.").append(colunaAno.getNome()).append(",", ScriptBuilder.LINE_SEPARATOR);
            scriptSAS.append(2, "t1.").append(colunaMes.getNome()).append(",", ScriptBuilder.LINE_SEPARATOR);
            scriptSAS.append(2, "t1.").append(colunaDia.getNome()).append(",", ScriptBuilder.LINE_SEPARATOR);
        } else if (colunaMes != null && colunaAno != null) {
            scriptSAS.append(2, "t1.").append(colunaAno.getNome()).append(",", ScriptBuilder.LINE_SEPARATOR);
            scriptSAS.append(2, "t1.").append(colunaMes.getNome()).append(",", ScriptBuilder.LINE_SEPARATOR);
        } else if (colunaAno != null) {
            scriptSAS.append(2, "t1.").append(colunaAno.getNome()).append(",", ScriptBuilder.LINE_SEPARATOR);
        }
        scriptSAS.append(2, "t1.hitmonlee,", ScriptBuilder.LINE_SEPARATOR);

        scriptSAS.append(2, "t1.").append(colunaDado.getNome()).append(",", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "t2.denominador,", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "t1.nome_arquivo", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "from densidade_1 t1", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "left join densidade_2 t2 on t1.").append(colunaMunicipio.getNome()).append(" = t2.").append(colunaMunicipio.getNome()).append(" and t1.hitmonlee = t2.hitmonlee;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append("quit;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

        scriptSAS.append("data densidade (reuse=yes compress=yes);", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "set densidade;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append("run;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

        scriptSAS.append("PROC SQL; DROP table densidade_1;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append("QUIT;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

        scriptSAS.append("PROC SQL; DROP table densidade_2;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append("QUIT;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

        scriptSAS.append("PROC SQL;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "CREATE TABLE WORK.PERIODO AS", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "SELECT DISTINCT", ScriptBuilder.LINE_SEPARATOR);

        if (colunaPeriodo != null) {
            scriptSAS.append(2, "intnx('month',t1.").append(colunaPeriodo.getNome()).append(",-").append(delay).append(") format=ptgdfmy7. as hitmonlee", ScriptBuilder.LINE_SEPARATOR);
        } else if (colunaDia != null && colunaMes != null && colunaAno != null) {
            scriptSAS.append(2, "intnx('month',mdy(t1.").append(colunaMes.getNome()).append(",t1.").append(colunaDia.getNome()).append(",t1.").append(colunaAno.getNome()).append("),-").append(delay).append(") format=ptgdfmy7. as hitmonlee", ScriptBuilder.LINE_SEPARATOR);
        } else if (colunaMes != null && colunaAno != null) {
            scriptSAS.append(2, "intnx('month',mdy(t1.").append(colunaMes.getNome()).append(",1,t1.").append(colunaAno.getNome()).append("),-").append(delay).append(") format=ptgdfmy7. as hitmonlee", ScriptBuilder.LINE_SEPARATOR);
        } else if (colunaAno != null) {
            scriptSAS.append(2, "intnx('month',mdy(12,1,t1.").append(colunaAno.getNome()).append("),-").append(delay).append(") format=ptgdfmy7. as hitmonlee", ScriptBuilder.LINE_SEPARATOR);
        }

        scriptSAS.append(1, "FROM WORK.DADOS t1;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append("QUIT;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

        scriptSAS.append("data PERIODO (reuse=yes compress=yes);", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "set PERIODO;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append("run;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

        scriptSAS.append("PROC SQL;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "CREATE TABLE WORK.market_share_1 AS ", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "SELECT", ScriptBuilder.LINE_SEPARATOR);

        scriptSAS.append(2, "t1.").append(colunaMunicipio.getNome()).append(",", ScriptBuilder.LINE_SEPARATOR);

        if (colunaPeriodo != null) {
            scriptSAS.append(2, "t1.").append(colunaPeriodo.getNome()).append(",", ScriptBuilder.LINE_SEPARATOR);
            scriptSAS.append(2, "t1.").append(colunaPeriodo.getNome()).append(" format=ptgdfmy7. as hitmonlee,", ScriptBuilder.LINE_SEPARATOR);
        } else if (colunaDia != null && colunaMes != null && colunaAno != null) {
            scriptSAS.append(2, "t1.").append(colunaAno.getNome()).append(",", ScriptBuilder.LINE_SEPARATOR);
            scriptSAS.append(2, "t1.").append(colunaMes.getNome()).append(",", ScriptBuilder.LINE_SEPARATOR);
            scriptSAS.append(2, "t1.").append(colunaDia.getNome()).append(",", ScriptBuilder.LINE_SEPARATOR);
            scriptSAS.append(2, "mdy(t1.").append(colunaMes.getNome()).append(",t1.").append(colunaDia.getNome()).append(",t1.").append(colunaAno.getNome()).append(") format=ptgdfmy7. as hitmonlee,", ScriptBuilder.LINE_SEPARATOR);
        } else if (colunaMes != null && colunaAno != null) {
            scriptSAS.append(2, "t1.").append(colunaAno.getNome()).append(",", ScriptBuilder.LINE_SEPARATOR);
            scriptSAS.append(2, "t1.").append(colunaMes.getNome()).append(",", ScriptBuilder.LINE_SEPARATOR);
            scriptSAS.append(2, "mdy(t1.").append(colunaMes.getNome()).append(",1,t1.").append(colunaAno.getNome()).append(") format=ptgdfmy7. as hitmonlee,", ScriptBuilder.LINE_SEPARATOR);
        } else if (colunaAno != null) {
            scriptSAS.append(2, "t1.").append(colunaAno.getNome()).append(",", ScriptBuilder.LINE_SEPARATOR);
            scriptSAS.append(2, "mdy(12,1,t1.").append(colunaAno.getNome()).append(") format=ptgdfmy7. as hitmonlee,", ScriptBuilder.LINE_SEPARATOR);
        }

        scriptSAS.append(2, "/* acesso */", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "(SUM(t1.").append(colunaDado.getNome()).append(")) FORMAT=COMMA12. AS ").append(colunaDado.getNome()).append("", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "FROM HISTORIC.").append(coleta.getBanco().getTabela()).append(" t1, WORK.PERIODO t2", ScriptBuilder.LINE_SEPARATOR);
        if (colunaPeriodo != null) {
            scriptSAS.append(1, "WHERE t1.").append(colunaPeriodo.getNome()).append(" = t2.hitmonlee", ScriptBuilder.LINE_SEPARATOR);
        } else {
            scriptSAS.append(1, "WHERE (CALCULATED hitmonlee) = t2.hitmonlee", ScriptBuilder.LINE_SEPARATOR);
        }
        scriptSAS.append(1, "GROUP BY", ScriptBuilder.LINE_SEPARATOR);

        scriptSAS.append(2, "t1.").append(colunaMunicipio.getNome()).append(",", ScriptBuilder.LINE_SEPARATOR);

        stringBuilder = new StringBuilder();
        if (colunaPeriodo != null) {
            stringBuilder.append("t1.").append(colunaPeriodo.getNome()).append(",");
        }
        if (colunaDia != null) {
            stringBuilder.append("t1.").append(colunaDia.getNome()).append(",");
        }
        if (colunaMes != null) {
            stringBuilder.append("t1.").append(colunaMes.getNome()).append(",");
        }
        if (colunaAno != null) {
            stringBuilder.append("t1.").append(colunaAno.getNome()).append(",");
        }

        scriptSAS.append(2, stringBuilder.toString().substring(0, stringBuilder.toString().length() - 1)).append(";", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append("QUIT;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

        scriptSAS.append("data market_share_1 (reuse=yes compress=yes);", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "set market_share_1;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append("run;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

        scriptSAS.append("PROC SQL;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "CREATE TABLE WORK.market_share_2 AS ", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "SELECT", ScriptBuilder.LINE_SEPARATOR);

        scriptSAS.append(2, "t1.").append(colunaCNPJ_CPF.getNome()).append(",", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "t1.").append(colunaMunicipio.getNome()).append(",", ScriptBuilder.LINE_SEPARATOR);

        if (colunaPeriodo != null) {
            scriptSAS.append(2, "t1.").append(colunaPeriodo.getNome()).append(",", ScriptBuilder.LINE_SEPARATOR);
            scriptSAS.append(2, "t1.").append(colunaPeriodo.getNome()).append(" format=ptgdfmy7. as hitmonlee,", ScriptBuilder.LINE_SEPARATOR);
        } else if (colunaDia != null && colunaMes != null && colunaAno != null) {
            scriptSAS.append(2, "t1.").append(colunaAno.getNome()).append(",", ScriptBuilder.LINE_SEPARATOR);
            scriptSAS.append(2, "t1.").append(colunaMes.getNome()).append(",", ScriptBuilder.LINE_SEPARATOR);
            scriptSAS.append(2, "t1.").append(colunaDia.getNome()).append(",", ScriptBuilder.LINE_SEPARATOR);
            scriptSAS.append(2, "mdy(t1.").append(colunaMes.getNome()).append(",t1.").append(colunaDia.getNome()).append(",t1.").append(colunaAno.getNome()).append(") format=ptgdfmy7. as hitmonlee,", ScriptBuilder.LINE_SEPARATOR);
        } else if (colunaMes != null && colunaAno != null) {
            scriptSAS.append(2, "t1.").append(colunaAno.getNome()).append(",", ScriptBuilder.LINE_SEPARATOR);
            scriptSAS.append(2, "t1.").append(colunaMes.getNome()).append(",", ScriptBuilder.LINE_SEPARATOR);
            scriptSAS.append(2, "mdy(t1.").append(colunaMes.getNome()).append(",1,t1.").append(colunaAno.getNome()).append(") format=ptgdfmy7. as hitmonlee,", ScriptBuilder.LINE_SEPARATOR);
        } else if (colunaAno != null) {
            scriptSAS.append(2, "t1.").append(colunaAno.getNome()).append(",", ScriptBuilder.LINE_SEPARATOR);
            scriptSAS.append(2, "mdy(12,1,t1.").append(colunaAno.getNome()).append(") format=ptgdfmy7. as hitmonlee,", ScriptBuilder.LINE_SEPARATOR);
        }

        scriptSAS.append(2, "/* acesso */", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "(SUM(t1.").append(colunaDado.getNome()).append(")) FORMAT=COMMA12. AS ").append(colunaDado.getNome()).append("", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "FROM HISTORIC.").append(coleta.getBanco().getTabela()).append(" t1, WORK.PERIODO t2", ScriptBuilder.LINE_SEPARATOR);
        if (colunaPeriodo != null) {
            scriptSAS.append(1, "WHERE t1.").append(colunaPeriodo.getNome()).append(" = t2.hitmonlee", ScriptBuilder.LINE_SEPARATOR);
        } else {
            scriptSAS.append(1, "WHERE (CALCULATED hitmonlee) = t2.hitmonlee", ScriptBuilder.LINE_SEPARATOR);
        }

        scriptSAS.append(1, "GROUP BY", ScriptBuilder.LINE_SEPARATOR);

        scriptSAS.append(2, "t1.").append(colunaCNPJ_CPF.getNome()).append(",", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "t1.").append(colunaMunicipio.getNome()).append(",", ScriptBuilder.LINE_SEPARATOR);

        stringBuilder = new StringBuilder();
        if (colunaPeriodo != null) {
            stringBuilder.append("t1.").append(colunaPeriodo.getNome()).append(",");
        }
        if (colunaDia != null) {
            stringBuilder.append("t1.").append(colunaDia.getNome()).append(",");
        }
        if (colunaMes != null) {
            stringBuilder.append("t1.").append(colunaMes.getNome()).append(",");
        }
        if (colunaAno != null) {
            stringBuilder.append("t1.").append(colunaAno.getNome()).append(",");
        }

        scriptSAS.append(2, stringBuilder.toString().substring(0, stringBuilder.toString().length() - 1)).append(";", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append("QUIT;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

        scriptSAS.append("data market_share_2 (reuse=yes compress=yes);", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "set market_share_2;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append("run;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

        scriptSAS.append("PROC SQL; DROP TABLE PERIODO;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append("QUIT;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

        scriptSAS.append("PROC SQL;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "CREATE TABLE WORK.market_share AS ", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "SELECT t2.*, ", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "t1.").append(colunaDado.getNome()).append(" AS ").append(colunaDado.getNome()).append("_municipio, ", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "/* market_share */", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "t2.").append(colunaDado.getNome()).append(" AS ").append(colunaDado.getNome()).append("_provedora", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "FROM WORK.market_share_2 t2, WORK.market_share_1 t1", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "WHERE", ScriptBuilder.LINE_SEPARATOR);

        scriptSAS.append(2, "t2.").append(colunaMunicipio.getNome()).append(" = t1.").append(colunaMunicipio.getNome()).append(" AND ", ScriptBuilder.LINE_SEPARATOR);

        stringBuilder = new StringBuilder();
        if (colunaPeriodo != null) {
            stringBuilder.append("t2.").append(colunaPeriodo.getNome()).append(" = t1.").append(colunaPeriodo.getNome()).append(" AND ");
        }
        if (colunaDia != null) {
            stringBuilder.append("t2.").append(colunaDia.getNome()).append(" = t1.").append(colunaDia.getNome()).append(" AND ");
        }
        if (colunaMes != null) {
            stringBuilder.append("t2.").append(colunaMes.getNome()).append(" = t1.").append(colunaMes.getNome()).append(" AND ");
        }
        if (colunaAno != null) {
            stringBuilder.append("t2.").append(colunaAno.getNome()).append(" = t1.").append(colunaAno.getNome()).append(" AND ");
        }

        scriptSAS.append(2, stringBuilder.toString().substring(0, stringBuilder.toString().length() - 5)).append(";", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append("QUIT;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

        scriptSAS.append("data market_share (reuse=yes compress=yes);", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "set market_share;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append("run;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

        scriptSAS.append("PROC SQL; DROP TABLE market_share_1;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append("QUIT;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

        scriptSAS.append("PROC SQL; DROP TABLE market_share_2;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append("QUIT;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

        scriptSAS.append("proc sql;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "create table linhas_market_share as", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "select count(1) as linhas", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "from market_share t1;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append("quit;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

        scriptSAS.append("data _null_;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "set linhas_market_share;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "call symput(\"linhas\",linhas);", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append("run;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

        scriptSAS.append("PROC SQL; DROP TABLE linhas_market_share;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append("QUIT;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

        scriptSAS.append("%macro market_share;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "%if &linhas > 0 %then %do;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

        final StackTraceElement[] ste = Thread.currentThread().getStackTrace();
        scriptSAS.append("PROC SQL;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "CREATE TABLE WORK.mensagem_validacao AS ", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "SELECT distinct \"").append(ste[1].getMethodName()).append("\" as metodo,", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "SCAN(t1.nome_arquivo,1,'_') AS arquivo, ", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "/* parametro */", ScriptBuilder.LINE_SEPARATOR);

        scriptSAS.append(2, "(cat(catx(';',cat(");

        scriptSAS.append(2, "'").append(colunaCNPJ_CPF.getNome()).append(" = ',t1.").append(colunaCNPJ_CPF.getNome()).append("),cat(");
        scriptSAS.append(2, "'").append(colunaMunicipio.getNome()).append(" = ',t1.").append(colunaMunicipio.getNome()).append("),cat(");

        stringBuilder = new StringBuilder();
        if (colunaPeriodo != null) {
            stringBuilder.append("'").append(colunaPeriodo.getNome()).append(" = ',put(t1.").append(colunaPeriodo.getNome()).append(",ptgdfmy7.)),cat(");
        }
        if (colunaDia != null) {
            stringBuilder.append("'").append(colunaDia.getNome()).append(" = ',t1.").append(colunaDia.getNome()).append("),cat(");
        }
        if (colunaMes != null) {
            stringBuilder.append("'").append(colunaMes.getNome()).append(" = ',t1.").append(colunaMes.getNome()).append("),cat(");
        }
        if (colunaAno != null) {
            stringBuilder.append("'").append(colunaAno.getNome()).append(" = ',t1.").append(colunaAno.getNome()).append("),cat(");
        }

        scriptSAS.append(2, stringBuilder.toString().substring(0, stringBuilder.toString().length() - 5)).append("))) as parametros,", ScriptBuilder.LINE_SEPARATOR);

        scriptSAS.append(2, "case when t1.").append(colunaDado.getNome()).append(" > ").append(multiplicador).append("*t1.denominador then cat('").append(colunaDado.getNome()).append(" incompatível com ").append(denominador == DENOMINADOR_DOMICILIO ? "domicílio" : "habitante").append(": ").append(colunaDado.getNome()).append(" = ', t1.").append(colunaDado.getNome()).append(",'; ").append(denominador == DENOMINADOR_DOMICILIO ? "domicílio" : "habitante").append(" = ', t1.denominador) else", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "case when t1.").append(colunaDado.getNome()).append(" > 0.05*t2.").append(colunaDado.getNome()).append("_municipio AND t2.").append(colunaDado.getNome()).append("_municipio > ").append(multiplicador).append("*t1.denominador then cat('").append(colunaDado.getNome()).append(" total no município incompatível com ").append(denominador == DENOMINADOR_DOMICILIO ? "domicílio" : "habitante").append(": ").append(colunaDado.getNome()).append(" total = ', t2.").append(colunaDado.getNome()).append("_municipio,'; ").append(colunaDado.getNome()).append(" = ', t1.").append(colunaDado.getNome()).append(",'; ").append(denominador == DENOMINADOR_DOMICILIO ? "domicílio" : "habitante").append(" = ', t1.denominador) else 'Ok' end end as mensagem,", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "'true' AS aceitavel", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "from densidade t1", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "inner join market_share t2 on", ScriptBuilder.LINE_SEPARATOR);

        scriptSAS.append(2, "t1.").append(colunaCNPJ_CPF.getNome()).append(" = t2.").append(colunaCNPJ_CPF.getNome()).append(" AND").append(ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "t1.").append(colunaMunicipio.getNome()).append(" = t2.").append(colunaMunicipio.getNome()).append(" AND").append(ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "t1.hitmonlee = t2.hitmonlee").append(ScriptBuilder.LINE_SEPARATOR);

        scriptSAS.append(1, "where (CALCULATED mensagem) not contains 'Ok'", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "UNION ALL select * from mensagem_validacao;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append("quit;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

        scriptSAS.append(1, "%end;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "%else %do;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

        scriptSAS.append("PROC SQL;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "CREATE TABLE WORK.mensagem_validacao AS ", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "SELECT distinct \"").append(ste[1].getMethodName()).append("\" as metodo,", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "SCAN(t1.nome_arquivo,1,'_') AS arquivo, ", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "/* parametro */", ScriptBuilder.LINE_SEPARATOR);

        scriptSAS.append(2, "(cat(catx(';',cat(");

        scriptSAS.append(2, "'").append(colunaCNPJ_CPF.getNome()).append(" = ',t1.").append(colunaCNPJ_CPF.getNome()).append("),cat(");
        scriptSAS.append(2, "'").append(colunaMunicipio.getNome()).append(" = ',t1.").append(colunaMunicipio.getNome()).append("),cat(");

        stringBuilder = new StringBuilder();
        if (colunaPeriodo != null) {
            stringBuilder.append("'").append(colunaPeriodo.getNome()).append(" = ',put(t1.").append(colunaPeriodo.getNome()).append(",ptgdfmy7.)),cat(");
        }
        if (colunaDia != null) {
            stringBuilder.append("'").append(colunaDia.getNome()).append(" = ',t1.").append(colunaDia.getNome()).append("),cat(");
        }
        if (colunaMes != null) {
            stringBuilder.append("'").append(colunaMes.getNome()).append(" = ',t1.").append(colunaMes.getNome()).append("),cat(");
        }
        if (colunaAno != null) {
            stringBuilder.append("'").append(colunaAno.getNome()).append(" = ',t1.").append(colunaAno.getNome()).append("),cat(");
        }

        scriptSAS.append(2, stringBuilder.toString().substring(0, stringBuilder.toString().length() - 5)).append("))) as parametros,", ScriptBuilder.LINE_SEPARATOR);

        scriptSAS.append(2, "case when t1.").append(colunaDado.getNome()).append(" > ").append(multiplicador).append("*t1.denominador then cat('").append(colunaDado.getNome()).append(" incompatível com ").append(denominador == DENOMINADOR_DOMICILIO ? "domicílio" : "habitante").append(": ").append(colunaDado.getNome()).append(" = ', t1.").append(colunaDado.getNome()).append(",'; ").append(denominador == DENOMINADOR_DOMICILIO ? "domicílio" : "habitante").append(" = ', t1.denominador) else 'Ok' end as mensagem,", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "'true' AS aceitavel", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "from densidade t1", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "where (CALCULATED mensagem) not contains 'Ok'", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "UNION ALL select * from mensagem_validacao;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append("quit;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

        scriptSAS.append(1, "%end;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append("%mend market_share;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append("%market_share;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

        scriptSAS.append("data mensagem_validacao (reuse=yes compress=yes);", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "set mensagem_validacao;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append("run;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

        scriptSAS.append("PROC SQL; DROP TABLE market_share;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append("QUIT;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

        scriptSAS.append("PROC SQL; DROP TABLE densidade;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append("QUIT;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

    }

    /**
     * Verifica se o CNPJ informado é válido
     *
     * @throws Exception
     */
    private void validaCNPJ() throws Exception {//TODO: validar CPF
        scriptSAS.append("/*Validação dos CNPJs*/", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

        Coluna colunaCNPJ = null;
        for (Coluna coluna : coleta.getColunas()) {
            if (coluna.getClasse() == Coluna.CLASSE_CNPJ_CPF) {
                colunaCNPJ = coluna;
            }
        }
        if (colunaCNPJ == null) {
            throw new Exception("Nenhuma coluna CNPJ foi informada");
        }

        scriptSAS.append("PROC SQL;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "CREATE TABLE WORK.mensagem_validacao AS", ScriptBuilder.LINE_SEPARATOR);

        final StackTraceElement[] ste = Thread.currentThread().getStackTrace();
        scriptSAS.append(1, "SELECT distinct \"").append(ste[1].getMethodName()).append("\" as metodo,", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "SCAN(t1.nome_arquivo,1,'_') as arquivo,", ScriptBuilder.LINE_SEPARATOR);

        scriptSAS.append(2, "cat(cat('").append(colunaCNPJ.getNome()).append("=',t1.").append(colunaCNPJ.getNome()).append(")) as parametros,", ScriptBuilder.LINE_SEPARATOR);

        //Validação do CNPJ
        scriptSAS.append(2, "(case when LENGTHC(t1.").append(colunaCNPJ.getNome()).append(")=14 then", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "(case when", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(3, "((case when", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(4, "MOD(input(substrn(t1.").append(colunaCNPJ.getNome()).append(",1,1),1.)*5+", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(4, "input(substrn(t1.").append(colunaCNPJ.getNome()).append(",2,1),1.)*4+", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(4, "input(substrn(t1.").append(colunaCNPJ.getNome()).append(",3,1),1.)*3+", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(4, "input(substrn(t1.").append(colunaCNPJ.getNome()).append(",4,1),1.)*2+", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(4, "input(substrn(t1.").append(colunaCNPJ.getNome()).append(",5,1),1.)*9+", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(4, "input(substrn(t1.").append(colunaCNPJ.getNome()).append(",6,1),1.)*8+", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(4, "input(substrn(t1.").append(colunaCNPJ.getNome()).append(",7,1),1.)*7+", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(4, "input(substrn(t1.").append(colunaCNPJ.getNome()).append(",8,1),1.)*6+", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(4, "input(substrn(t1.").append(colunaCNPJ.getNome()).append(",9,1),1.)*5+", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(4, "input(substrn(t1.").append(colunaCNPJ.getNome()).append(",10,1),1.)*4+", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(4, "input(substrn(t1.").append(colunaCNPJ.getNome()).append(",11,1),1.)*3+", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(4, "input(substrn(t1.").append(colunaCNPJ.getNome()).append(",12,1),1.)*2,11) < 2 then 0", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(3, "else 11 -", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(4, "MOD(input(substrn(t1.").append(colunaCNPJ.getNome()).append(",1,1),1.)*5+", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(4, "input(substrn(t1.").append(colunaCNPJ.getNome()).append(",2,1),1.)*4+", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(4, "input(substrn(t1.").append(colunaCNPJ.getNome()).append(",3,1),1.)*3+", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(4, "input(substrn(t1.").append(colunaCNPJ.getNome()).append(",4,1),1.)*2+", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(4, "input(substrn(t1.").append(colunaCNPJ.getNome()).append(",5,1),1.)*9+", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(4, "input(substrn(t1.").append(colunaCNPJ.getNome()).append(",6,1),1.)*8+", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(4, "input(substrn(t1.").append(colunaCNPJ.getNome()).append(",7,1),1.)*7+", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(4, "input(substrn(t1.").append(colunaCNPJ.getNome()).append(",8,1),1.)*6+", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(4, "input(substrn(t1.").append(colunaCNPJ.getNome()).append(",9,1),1.)*5+", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(4, "input(substrn(t1.").append(colunaCNPJ.getNome()).append(",10,1),1.)*4+", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(4, "input(substrn(t1.").append(colunaCNPJ.getNome()).append(",11,1),1.)*3+", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(4, "input(substrn(t1.").append(colunaCNPJ.getNome()).append(",12,1),1.)*2,11) end) = input(substrn(t1.").append(colunaCNPJ.getNome()).append(",13,1),1.)", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append("", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(3, "and", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(3, "case when", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(4, "MOD(input(substrn(t1.").append(colunaCNPJ.getNome()).append(",1,1),1.)*6+", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(4, "input(substrn(t1.").append(colunaCNPJ.getNome()).append(",2,1),1.)*5+", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(4, "input(substrn(t1.").append(colunaCNPJ.getNome()).append(",3,1),1.)*4+", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(4, "input(substrn(t1.").append(colunaCNPJ.getNome()).append(",4,1),1.)*3+", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(4, "input(substrn(t1.").append(colunaCNPJ.getNome()).append(",5,1),1.)*2+", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(4, "input(substrn(t1.").append(colunaCNPJ.getNome()).append(",6,1),1.)*9+", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(4, "input(substrn(t1.").append(colunaCNPJ.getNome()).append(",7,1),1.)*8+", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(4, "input(substrn(t1.").append(colunaCNPJ.getNome()).append(",8,1),1.)*7+", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(4, "input(substrn(t1.").append(colunaCNPJ.getNome()).append(",9,1),1.)*6+", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(4, "input(substrn(t1.").append(colunaCNPJ.getNome()).append(",10,1),1.)*5+", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(4, "input(substrn(t1.").append(colunaCNPJ.getNome()).append(",11,1),1.)*4+", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(4, "input(substrn(t1.").append(colunaCNPJ.getNome()).append(",12,1),1.)*3+", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(4, "(", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(4, "(case when", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(5, "MOD(input(substrn(t1.").append(colunaCNPJ.getNome()).append(",1,1),1.)*5+", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(5, "input(substrn(t1.").append(colunaCNPJ.getNome()).append(",2,1),1.)*4+", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(5, "input(substrn(t1.").append(colunaCNPJ.getNome()).append(",3,1),1.)*3+", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(5, "input(substrn(t1.").append(colunaCNPJ.getNome()).append(",4,1),1.)*2+", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(5, "input(substrn(t1.").append(colunaCNPJ.getNome()).append(",5,1),1.)*9+", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(5, "input(substrn(t1.").append(colunaCNPJ.getNome()).append(",6,1),1.)*8+", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(5, "input(substrn(t1.").append(colunaCNPJ.getNome()).append(",7,1),1.)*7+", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(5, "input(substrn(t1.").append(colunaCNPJ.getNome()).append(",8,1),1.)*6+", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(5, "input(substrn(t1.").append(colunaCNPJ.getNome()).append(",9,1),1.)*5+", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(5, "input(substrn(t1.").append(colunaCNPJ.getNome()).append(",10,1),1.)*4+", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(5, "input(substrn(t1.").append(colunaCNPJ.getNome()).append(",11,1),1.)*3+", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(5, "input(substrn(t1.").append(colunaCNPJ.getNome()).append(",12,1),1.)*2,11) < 2 then 0", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(4, "else 11 -", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(5, "MOD(input(substrn(t1.").append(colunaCNPJ.getNome()).append(",1,1),1.)*5+", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(5, "input(substrn(t1.").append(colunaCNPJ.getNome()).append(",2,1),1.)*4+", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(5, "input(substrn(t1.").append(colunaCNPJ.getNome()).append(",3,1),1.)*3+", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(5, "input(substrn(t1.").append(colunaCNPJ.getNome()).append(",4,1),1.)*2+", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(5, "input(substrn(t1.").append(colunaCNPJ.getNome()).append(",5,1),1.)*9+", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(5, "input(substrn(t1.").append(colunaCNPJ.getNome()).append(",6,1),1.)*8+", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(5, "input(substrn(t1.").append(colunaCNPJ.getNome()).append(",7,1),1.)*7+", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(5, "input(substrn(t1.").append(colunaCNPJ.getNome()).append(",8,1),1.)*6+", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(5, "input(substrn(t1.").append(colunaCNPJ.getNome()).append(",9,1),1.)*5+", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(5, "input(substrn(t1.").append(colunaCNPJ.getNome()).append(",10,1),1.)*4+", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(5, "input(substrn(t1.").append(colunaCNPJ.getNome()).append(",11,1),1.)*3+", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(5, "input(substrn(t1.").append(colunaCNPJ.getNome()).append(",12,1),1.)*2,11) end)", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(5, ")*2,11) < 2 then 0", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(4, "else 11 - MOD(input(substrn(t1.").append(colunaCNPJ.getNome()).append(",1,1),1.)*6+", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(5, "input(substrn(t1.").append(colunaCNPJ.getNome()).append(",2,1),1.)*5+", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(5, "input(substrn(t1.").append(colunaCNPJ.getNome()).append(",3,1),1.)*4+", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(5, "input(substrn(t1.").append(colunaCNPJ.getNome()).append(",4,1),1.)*3+", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(5, "input(substrn(t1.").append(colunaCNPJ.getNome()).append(",5,1),1.)*2+", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(5, "input(substrn(t1.").append(colunaCNPJ.getNome()).append(",6,1),1.)*9+", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(5, "input(substrn(t1.").append(colunaCNPJ.getNome()).append(",7,1),1.)*8+", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(5, "input(substrn(t1.").append(colunaCNPJ.getNome()).append(",8,1),1.)*7+", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(5, "input(substrn(t1.").append(colunaCNPJ.getNome()).append(",9,1),1.)*6+", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(5, "input(substrn(t1.").append(colunaCNPJ.getNome()).append(",10,1),1.)*5+", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(5, "input(substrn(t1.").append(colunaCNPJ.getNome()).append(",11,1),1.)*4+", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(5, "input(substrn(t1.").append(colunaCNPJ.getNome()).append(",12,1),1.)*3+", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(5, "(", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(5, "(case when", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(6, "MOD(input(substrn(t1.").append(colunaCNPJ.getNome()).append(",1,1),1.)*5+", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(6, "input(substrn(t1.").append(colunaCNPJ.getNome()).append(",2,1),1.)*4+", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(6, "input(substrn(t1.").append(colunaCNPJ.getNome()).append(",3,1),1.)*3+", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(6, "input(substrn(t1.").append(colunaCNPJ.getNome()).append(",4,1),1.)*2+", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(6, "input(substrn(t1.").append(colunaCNPJ.getNome()).append(",5,1),1.)*9+", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(6, "input(substrn(t1.").append(colunaCNPJ.getNome()).append(",6,1),1.)*8+", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(6, "input(substrn(t1.").append(colunaCNPJ.getNome()).append(",7,1),1.)*7+", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(6, "input(substrn(t1.").append(colunaCNPJ.getNome()).append(",8,1),1.)*6+", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(6, "input(substrn(t1.").append(colunaCNPJ.getNome()).append(",9,1),1.)*5+", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(6, "input(substrn(t1.").append(colunaCNPJ.getNome()).append(",10,1),1.)*4+", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(6, "input(substrn(t1.").append(colunaCNPJ.getNome()).append(",11,1),1.)*3+", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(6, "input(substrn(t1.").append(colunaCNPJ.getNome()).append(",12,1),1.)*2,11) < 2 then 0", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(5, "else 11 -", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(6, "MOD(input(substrn(t1.").append(colunaCNPJ.getNome()).append(",1,1),1.)*5+", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(6, "input(substrn(t1.").append(colunaCNPJ.getNome()).append(",2,1),1.)*4+", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(6, "input(substrn(t1.").append(colunaCNPJ.getNome()).append(",3,1),1.)*3+", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(6, "input(substrn(t1.").append(colunaCNPJ.getNome()).append(",4,1),1.)*2+", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(6, "input(substrn(t1.").append(colunaCNPJ.getNome()).append(",5,1),1.)*9+", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(6, "input(substrn(t1.").append(colunaCNPJ.getNome()).append(",6,1),1.)*8+", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(6, "input(substrn(t1.").append(colunaCNPJ.getNome()).append(",7,1),1.)*7+", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(6, "input(substrn(t1.").append(colunaCNPJ.getNome()).append(",8,1),1.)*6+", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(6, "input(substrn(t1.").append(colunaCNPJ.getNome()).append(",9,1),1.)*5+", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(6, "input(substrn(t1.").append(colunaCNPJ.getNome()).append(",10,1),1.)*4+", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(6, "input(substrn(t1.").append(colunaCNPJ.getNome()).append(",11,1),1.)*3+", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(6, "input(substrn(t1.").append(colunaCNPJ.getNome()).append(",12,1),1.)*2,11) end)", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(6, ")*2,11) end = input(substrn(t1.").append(colunaCNPJ.getNome()).append(",14,1),1.)) = 1 then \"Ok\" else \"CNPJ Inválido\" end)", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "else \"CNPJ Inválido\" end) AS mensagem,", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "'false' AS aceitavel", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "FROM WORK.DADOS t1", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "WHERE (CALCULATED mensagem) ^= 'Ok'", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "UNION ALL select * from mensagem_validacao;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append("QUIT;", ScriptBuilder.LINE_SEPARATOR);

        scriptSAS.append("data mensagem_validacao (reuse=yes compress=yes);", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "set mensagem_validacao;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append("run;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

    }

    /**
     * Verifica se o CPF informado é válido
     *
     * @throws Exception
     */
    private void validaCPF() throws Exception {
        scriptSAS.append("/*Verifica CPF*/", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);
    }

    /**
     * Verifica se o código de município informado é válido
     *
     * @throws Exception
     */
    private void validaCodigoMunicipio() throws Exception {
        scriptSAS.append("/*Validação dos municípios*/", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append("LIBNAME SITARWEB META LIBRARY=SITARWEB;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

        final StackTraceElement[] ste = Thread.currentThread().getStackTrace();

        Coluna colunaCNPJ_CPF = null;
        Coluna colunaMunicipio = null;
        Coluna colunaCEP = null;
        Coluna colunaLocalidade = null;
        Coluna colunaDado = null;
        List<Coluna> colunasConsolidacao = new ArrayList<>();
        Coluna colunaAno = null;
        Coluna colunaMes = null;
        Coluna colunaDia = null;
        Coluna colunaPeriodo = null;
        Coluna colunaCN = null;
        for (Coluna coluna : coleta.getColunas()) {
            switch (coluna.getClasse()) {
                case Coluna.CLASSE_ANO:
                    colunaAno = coluna;
                    break;
                case Coluna.CLASSE_CNPJ_CPF:
                    colunaCNPJ_CPF = coluna;
                    break;
                case Coluna.CLASSE_DIA:
                    colunaDia = coluna;
                    break;
                case Coluna.CLASSE_MES:
                    colunaMes = coluna;
                    break;
                case Coluna.CLASSE_MUNICIPIO:
                    colunaMunicipio = coluna;
                    break;
                case Coluna.CLASSE_CEP:
                    colunaCEP = coluna;
                    break;
                case Coluna.CLASSE_LOCALIDADE:
                    colunaLocalidade = coluna;
                    break;
                case Coluna.CLASSE_PERIODO:
                    colunaPeriodo = coluna;
                    break;
                case Coluna.CLASSE_CN:
                    colunaCN = coluna;
                    break;
            }
        }

        if (colunaMunicipio == null) {
            throw new Exception("Nenhuma coluna município foi encontrada");
        }

        scriptSAS.append("PROC SQL;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "CREATE TABLE WORK.MUNICIPIOS AS ", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "SELECT t1.nome_arquivo, t1.CodMunicipio", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "FROM (", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "SELECT DISTINCT t1.nome_arquivo, t1.").append(colunaMunicipio.getNome()).append(" as CodMunicipio", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "FROM WORK.DADOS t1", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, ") t1", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "LEFT JOIN (", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "SELECT DISTINCT t1.CodMunicipio", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "FROM SITARWEB.Municipio t1", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, ") t2 ON (t1.CodMunicipio = t2.CodMunicipio)", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "WHERE t2.CodMunicipio IS MISSING;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append("QUIT;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

        scriptSAS.append("data MUNICIPIOS (reuse=yes compress=yes);", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "set MUNICIPIOS;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append("run;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

        scriptSAS.append("PROC SQL;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "CREATE TABLE mensagem_validacao AS ", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "SELECT distinct \"").append(ste[1].getMethodName()).append("\" as metodo,", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "SCAN(t1.nome_arquivo,1,'_') AS arquivo, ", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "/* parametro */", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "(cat(cat('").append(colunaMunicipio.getNome()).append("=',").append("t1.CodMunicipio))) as parametros,", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "/* mensagem */", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "cat('O código de município ',t1.CodMunicipio,' não foi identificado em nossas bases.') AS mensagem,", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "'false' AS aceitavel", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "FROM WORK.MUNICIPIOS t1", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "WHERE t1.CodMunicipio <> '-1' and t1.CodMunicipio <> '4300001' and t1.CodMunicipio <> '4300002'", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "UNION ALL select * from mensagem_validacao;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append("QUIT;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

        scriptSAS.append("data mensagem_validacao (reuse=yes compress=yes);", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "set mensagem_validacao;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append("run;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

        scriptSAS.append("PROC SQL; DROP TABLE MUNICIPIOS;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append("QUIT;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

    }

    /**
     * Verifica se o CEP encaminhado está na base da Anatel
     *
     * @throws Exception
     */
    private void validaCEP() throws Exception {
        scriptSAS.append("/*Verifica CEP*/", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);
    }

    /**
     * Verifica se o código de localidade existe.
     *
     * @throws Exception
     */
    private void validaLocalidadeSGMU() throws Exception {
        scriptSAS.append("/*Verifica Localidade do SGMU*/", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append("LIBNAME SITARWEB META LIBRARY=SITARWEB;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

        final StackTraceElement[] ste = Thread.currentThread().getStackTrace();

        Coluna colunaCNPJ_CPF = null;
        Coluna colunaMunicipio = null;
        Coluna colunaCEP = null;
        Coluna colunaLocalidade = null;
        Coluna colunaDado = null;
        List<Coluna> colunasConsolidacao = new ArrayList<>();
        Coluna colunaAno = null;
        Coluna colunaMes = null;
        Coluna colunaDia = null;
        Coluna colunaPeriodo = null;
        Coluna colunaCN = null;
        for (Coluna coluna : coleta.getColunas()) {
            switch (coluna.getUso()) {
                case Coluna.USO_DADO:
                    if (colunaDado != null) {
                        throw new Exception("Não é permitido ter mais de uma coluna de dado. Você pode criar novo script de validação alterando o USO da coluna, mantendo sempre apenas uma coluna de dado.");
                    }
                    colunaDado = coluna;
                    break;
                default:
                    colunasConsolidacao.add(coluna);
                    switch (coluna.getClasse()) {
                        case Coluna.CLASSE_ANO:
                            colunaAno = coluna;
                            break;
                        case Coluna.CLASSE_CNPJ_CPF:
                            colunaCNPJ_CPF = coluna;
                            break;
                        case Coluna.CLASSE_DIA:
                            colunaDia = coluna;
                            break;
                        case Coluna.CLASSE_MES:
                            colunaMes = coluna;
                            break;
                        case Coluna.CLASSE_MUNICIPIO:
                            colunaMunicipio = coluna;
                            break;
                        case Coluna.CLASSE_CEP:
                            colunaCEP = coluna;
                            break;
                        case Coluna.CLASSE_LOCALIDADE:
                            colunaLocalidade = coluna;
                            break;
                        case Coluna.CLASSE_PERIODO:
                            colunaPeriodo = coluna;
                            break;
                        case Coluna.CLASSE_CN:
                            colunaCN = coluna;
                            break;
                    }
                    break;

            }
        }

        if (colunaLocalidade == null) {
            throw new Exception("Não foi encontrada nenhuma coluna de localidade");
        }
        if (colunaMunicipio == null) {
            throw new Exception("Não foi encontrada nenhuma coluna de municipio");
        }

        scriptSAS.append("PROC SQL;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "CREATE TABLE WORK.LOCALIDADES AS ", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "SELECT DISTINCT t1.nome_arquivo, t1.CodigoCNL, t1.CodMunicipio", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "FROM (", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "SELECT DISTINCT t1.nome_arquivo,", ScriptBuilder.LINE_SEPARATOR);

        scriptSAS.append(3, "compress(t1.").append(colunaLocalidade.getNome()).append(",' ') as CodigoCNL, t1.").append(colunaMunicipio.getNome()).append(" as CodMunicipio");

        scriptSAS.append(2, "FROM WORK.DADOS t1", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, ") t1", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "LEFT JOIN (", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "SELECT DISTINCT t1.CodigoCNL, t1.CodMunicipio", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "FROM SITARWEB.LOCALIDADECOMPLEMENTO_AREAAREA t1", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, ") t2 ON (t1.CodigoCNL = t2.CodigoCNL)", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "WHERE (t2.CodigoCNL IS MISSING AND t1.CodigoCNL ^= '-1') OR (t2.CodMunicipio NOT IS MISSING AND t1.CodMunicipio ^= t2.CodMunicipio);", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append("QUIT;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

        scriptSAS.append("data LOCALIDADES (reuse=yes compress=yes);", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "set LOCALIDADES;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append("run;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

        scriptSAS.append("PROC SQL;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "CREATE TABLE mensagem_validacao AS ", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "SELECT distinct \"").append(ste[1].getMethodName()).append("\" as metodo,", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "SCAN(t1.nome_arquivo,1,'_') AS arquivo, ", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "/* parametro */", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "(cat('").append(colunaMunicipio.getNome()).append(" = ',t1.CodMunicipio,';").append(colunaLocalidade.getNome()).append(" = ',t1.CodigoCNL)) as parametros,", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "/* mensagem */", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "cat('O código de localidade ',t1.CodigoCNL,' não foi identificado em nossas bases para o municipio ',t1.CodMunicipio,'.') AS mensagem,", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "'false' AS aceitavel", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "FROM WORK.LOCALIDADES t1", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "UNION ALL select * from mensagem_validacao;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append("QUIT;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

        scriptSAS.append("data mensagem_validacao (reuse=yes compress=yes);", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "set mensagem_validacao;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append("run;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

        scriptSAS.append("PROC SQL; DROP TABLE LOCALIDADES;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append("QUIT;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

    }

    /**
     * Altera o uso de uma coluna. Útil se o curador quiser realizar a mesma
     * validação com formas de consolidação diferentes
     *
     * @param indiceColuna
     * @param uso
     * @throws Exception
     */
    private void alteraUsoColuna(int indiceColuna, int uso) throws Exception {
        coleta.getColuna(indiceColuna).setUso(uso);
    }

    /**
     * Verifica se o CNPJ informado tem outorga para o serviço informado.
     *
     * @param servico
     * @throws Exception
     */
    private void verificaOutorga(List<String> servico) throws Exception {
        scriptSAS.append("/*Verifica Outorga*/", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);
    }

    /**
     * Verifica se o CNPJ informado está cadastrado na Anatel
     *
     * @throws Exception
     */
    private void verificaCadastro() throws Exception {
        scriptSAS.append("/*Verifica Cadastro*/", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);
        final StackTraceElement[] ste = Thread.currentThread().getStackTrace();
        scriptSAS.append("LIBNAME SITARWEB META LIBRARY=SITARWEB;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

        Coluna colunaCNPJCPF = null;
        for (Coluna coluna : coleta.getColunas()) {
            if (coluna.getClasse() == Coluna.CLASSE_CNPJ_CPF) {
                colunaCNPJCPF = coluna;
            }
        }
        if (colunaCNPJCPF == null) {
            throw new Exception("Nenhuma coluna CNPJ foi informada");
        }

        scriptSAS.append("PROC SQL;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "CREATE TABLE mensagem_validacao AS ", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "SELECT distinct \"").append(ste[1].getMethodName()).append("\" as metodo,", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "SCAN(t1.nome_arquivo,1,'_') AS arquivo, ", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "/* parametro */", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "(cat('").append(colunaCNPJCPF.getNome()).append("=', t1.").append(colunaCNPJCPF.getNome()).append(")) AS parametros, ", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "/* mensagem */", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "(case when t2.NumCnpjCpf is missing then 'Provedora de Dados não cadastrada.' else 'Ok' end) AS mensagem,", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "'false' AS aceitavel", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "FROM WORK.DADOS t1", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "LEFT JOIN (", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "SELECT DISTINCT t1.NumCnpjCpf", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "FROM SITARWEB.ENTIDADE t1", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, ") t2 ON (t1.").append(colunaCNPJCPF.getNome()).append(" = t2.NumCnpjCpf)", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "WHERE (CALCULATED mensagem) ^= 'Ok'", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "UNION ALL select * from mensagem_validacao;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append("QUIT;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

        scriptSAS.append("data mensagem_validacao (reuse=yes compress=yes);", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "set mensagem_validacao;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append("run;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);
    }

    /**
     * Verifica se há valores em branco no arquivo CSV
     *
     * @throws Exception
     */
    private void verificaValoresEmBranco() throws Exception {
        scriptSAS.append("/*Verifica valores em branco*/", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);
        final StackTraceElement[] ste = Thread.currentThread().getStackTrace();
        scriptSAS.append("LIBNAME SITARWEB META LIBRARY=SITARWEB;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

        scriptSAS.append("PROC SQL;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "CREATE TABLE VALORES_BRANCOS AS", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "SELECT t1.myfilename, ", ScriptBuilder.LINE_SEPARATOR);

        for (Coluna coluna : coleta.getColunas()) {
            scriptSAS.append(2, "t1.").append(coluna.getNome()).append(",", ScriptBuilder.LINE_SEPARATOR);
        }

        scriptSAS.append(2, "t1.nome_arquivo", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "FROM WORK.DADOS t1", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "WHERE ", ScriptBuilder.LINE_SEPARATOR);

        StringBuilder stringBuilder = new StringBuilder();
        for (Coluna coluna : coleta.getColunas()) {
            stringBuilder.append("t1.").append(coluna.getNome()).append(" is missing OR ");
        }

        scriptSAS.append(2, stringBuilder.toString().substring(0, stringBuilder.toString().length() - 4)).append(";", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append("QUIT;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

        scriptSAS.append("data VALORES_BRANCOS (reuse=yes compress=yes);", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "set VALORES_BRANCOS;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append("run;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

        scriptSAS.append("PROC SQL;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "CREATE TABLE mensagem_validacao AS ", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "SELECT distinct \"").append(ste[1].getMethodName()).append("\" as metodo,", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "SCAN(t1.nome_arquivo,1,'_') AS arquivo, ", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "/* parametro */", ScriptBuilder.LINE_SEPARATOR);

        stringBuilder = new StringBuilder();
        stringBuilder.append("(catx(';',cat('");
        for (Coluna coluna : coleta.getColunas()) {
            if (coluna.getClasse() == Coluna.CLASSE_PERIODO) {
                stringBuilder.append(coluna.getNome()).append("=', put(t1.").append(coluna.getNome()).append(",ptgdfmy7.)),cat('");
            } else {
                stringBuilder.append(coluna.getNome()).append("=', t1.").append(coluna.getNome()).append("),cat('");
            }
        }
        scriptSAS.append(2, stringBuilder.toString().substring(0, stringBuilder.toString().length() - 7)).append("))) AS parametros,", ScriptBuilder.LINE_SEPARATOR);

        scriptSAS.append(2, "/* mensagem */", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "('Algumas colunas estão sem valores') AS mensagem,", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "'false' AS aceitavel", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "FROM WORK.VALORES_BRANCOS t1", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "UNION ALL select * from mensagem_validacao;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append("QUIT;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

        scriptSAS.append("data mensagem_validacao (reuse=yes compress=yes);", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "set mensagem_validacao;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append("run;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

        scriptSAS.append("PROC SQL; DROP TABLE VALORES_BRANCOS;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append("QUIT;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

    }

    /**
     * Verifica se foram enviados registros repetidos no arquivo CSV
     *
     * @throws Exception
     */
    private void verificaDuplicados() throws Exception {
        final StackTraceElement[] ste = Thread.currentThread().getStackTrace();

        Coluna colunaCNPJ_CPF = null;
        Coluna colunaMunicipio = null;
        Coluna colunaCEP = null;
        Coluna colunaLocalidade = null;
        Coluna colunaDado = null;
        List<Coluna> colunasConsolidacao = new ArrayList<>();
        Coluna colunaAno = null;
        Coluna colunaMes = null;
        Coluna colunaDia = null;
        Coluna colunaPeriodo = null;
        Coluna colunaCN = null;
        for (Coluna coluna : coleta.getColunas()) {
            switch (coluna.getUso()) {
                case Coluna.USO_DADO:
                    if (colunaDado != null) {
                        throw new Exception("Não é permitido ter mais de uma coluna de dado. Você pode criar novo script de validação alterando o USO da coluna, mantendo sempre apenas uma coluna de dado.");
                    }
                    colunaDado = coluna;
                    break;
                case Coluna.USO_CONSOLIDACAO:
                    colunasConsolidacao.add(coluna);
                    switch (coluna.getClasse()) {
                        case Coluna.CLASSE_ANO:
                            colunaAno = coluna;
                            break;
                        case Coluna.CLASSE_CNPJ_CPF:
                            colunaCNPJ_CPF = coluna;
                            break;
                        case Coluna.CLASSE_DIA:
                            colunaDia = coluna;
                            break;
                        case Coluna.CLASSE_MES:
                            colunaMes = coluna;
                            break;
                        case Coluna.CLASSE_MUNICIPIO:
                            colunaMunicipio = coluna;
                            break;
                        case Coluna.CLASSE_CEP:
                            colunaCEP = coluna;
                            break;
                        case Coluna.CLASSE_LOCALIDADE:
                            colunaLocalidade = coluna;
                            break;
                        case Coluna.CLASSE_PERIODO:
                            colunaPeriodo = coluna;
                            break;
                        case Coluna.CLASSE_CN:
                            colunaCN = coluna;
                            break;
                    }
                    break;

            }
        }

        scriptSAS.append("/*Verifica valores duplicados*/", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

        scriptSAS.append("PROC SQL;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "CREATE TABLE WORK.DUPLICADOS AS ", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "SELECT t1.myfilename, ", ScriptBuilder.LINE_SEPARATOR);

        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("(COUNT(cat(");
        for (Coluna coluna : colunasConsolidacao) {
            scriptSAS.append(2, "t1.").append(coluna.getNome()).append(",", ScriptBuilder.LINE_SEPARATOR);
            stringBuilder.append(coluna.getNome()).append(",");
        }
        scriptSAS.append(2, "t1.nome_arquivo,", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, stringBuilder.toString().substring(0, stringBuilder.toString().length() - 1)).append("))) AS linhas", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "FROM WORK.DADOS t1", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "GROUP BY t1.myfilename,", ScriptBuilder.LINE_SEPARATOR);
        for (Coluna coluna : colunasConsolidacao) {
            scriptSAS.append(2, "t1.").append(coluna.getNome()).append(",", ScriptBuilder.LINE_SEPARATOR);
        }
        scriptSAS.append(2, "t1.nome_arquivo;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append("QUIT;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

        scriptSAS.append("data DUPLICADOS (reuse=yes compress=yes);", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "set DUPLICADOS;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append("run;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

        scriptSAS.append("PROC SQL;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "CREATE TABLE mensagem_validacao AS ", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "SELECT distinct \"").append(ste[1].getMethodName()).append("\" as metodo,", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "SCAN(t1.nome_arquivo,1,'_') AS arquivo, ", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "/* parametro */", ScriptBuilder.LINE_SEPARATOR);
        stringBuilder = new StringBuilder();
        stringBuilder.append("(cat(catx(';',");
        for (Coluna coluna : colunasConsolidacao) {
            if (coluna.getClasse() == Coluna.CLASSE_PERIODO) {
                stringBuilder.append("cat('").append(coluna.getNome()).append("=', put(t1.").append(coluna.getNome()).append(",ptgdfmy7.)),");
            } else {
                stringBuilder.append("cat('").append(coluna.getNome()).append("=',").append("t1.").append(coluna.getNome()).append("),");
            }
        }
        scriptSAS.append(2, stringBuilder.toString().substring(0, stringBuilder.length() - 1)).append("))) AS parametros,", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "/* mensagem */", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "(case when t1.linhas > 1 then cat('Registro aparece ',t1.linhas,' vezes no arquivo CSV para os parâmetros indicados.') else 'Ok' end) AS mensagem,", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "'false' AS aceitavel", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "FROM WORK.DUPLICADOS t1", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "WHERE t1.linhas > 1", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "UNION ALL select * from mensagem_validacao;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append("QUIT;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

        scriptSAS.append("data mensagem_validacao (reuse=yes compress=yes);", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "set mensagem_validacao;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append("run;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

        scriptSAS.append("PROC SQL; DROP TABLE DUPLICADOS;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append("QUIT;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);
    }

    /**
     * Altera o uso de uma coluna. Útil se o curador quiser realizar a mesma
     * validação com formas de consolidação diferentes
     *
     * @param nomeColuna
     * @param uso
     * @throws Exception
     */
    private void alteraUsoColuna(String nomeColuna, int uso) throws Exception {
        for (int i = 0; i < coleta.getColunas().size(); i++) {
            Coluna coluna = coleta.getColuna(i);
            if (coluna.getNome().equals(nomeColuna)) {
                alteraUsoColuna(i, uso);
            }
        }
    }

    private void validarChaveUnica() throws Exception {
        final StackTraceElement[] ste = Thread.currentThread().getStackTrace();

        Coluna colunaCNPJ_CPF = null;
        Coluna colunaMunicipio = null;
        Coluna colunaCEP = null;
        Coluna colunaLocalidade = null;
        Coluna colunaDado = null;
        List<Coluna> colunasConsolidacao = new ArrayList<>();
        Coluna colunaAno = null;
        Coluna colunaMes = null;
        Coluna colunaDia = null;
        Coluna colunaPeriodo = null;
        Coluna colunaCN = null;
        for (Coluna coluna : coleta.getColunas()) {
            switch (coluna.getUso()) {
                case Coluna.USO_DADO:
                    if (colunaDado != null) {
                        throw new Exception("Não é permitido ter mais de uma coluna de dado. Você pode criar novo script de validação alterando o USO da coluna, mantendo sempre apenas uma coluna de dado.");
                    }
                    colunaDado = coluna;
                    break;
                case Coluna.USO_CONSOLIDACAO:
                    colunasConsolidacao.add(coluna);
                    switch (coluna.getClasse()) {
                        case Coluna.CLASSE_ANO:
                            colunaAno = coluna;
                            break;
                        case Coluna.CLASSE_CNPJ_CPF:
                            colunaCNPJ_CPF = coluna;
                            break;
                        case Coluna.CLASSE_DIA:
                            colunaDia = coluna;
                            break;
                        case Coluna.CLASSE_MES:
                            colunaMes = coluna;
                            break;
                        case Coluna.CLASSE_MUNICIPIO:
                            colunaMunicipio = coluna;
                            break;
                        case Coluna.CLASSE_CEP:
                            colunaCEP = coluna;
                            break;
                        case Coluna.CLASSE_LOCALIDADE:
                            colunaLocalidade = coluna;
                            break;
                        case Coluna.CLASSE_PERIODO:
                            colunaPeriodo = coluna;
                            break;
                        case Coluna.CLASSE_CN:
                            colunaCN = coluna;
                            break;
                    }
                    break;

            }
        }
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

        //verificaDuplicados();
        //verificaValoresEmBranco();
        scriptSAS.append("/*Verifica chave única*/", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

        scriptSAS.append("PROC SQL;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "CREATE TABLE WORK.chave_existe AS ", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "SELECT *", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "FROM dados t1", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "inner join historic.").append(coleta.getBanco().getTabela()).append(" t2 on ");
        StringBuilder stringBuilder = new StringBuilder();
        for (Coluna coluna : colunasConsolidacao) {
            stringBuilder.append("t1.").append(coluna.getNome()).append("  = t2.").append(coluna.getNome()).append(" and ");
        }
        scriptSAS.append(2, stringBuilder.toString().substring(0, stringBuilder.length() - 5), ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "WHERE", ScriptBuilder.LINE_SEPARATOR);
        stringBuilder = new StringBuilder();
        for (Coluna coluna : colunasConsolidacao) {
            stringBuilder.append("t1.").append(coluna.getNome()).append("  is not missing and ");
        }
        scriptSAS.append(2, stringBuilder.toString().substring(0, stringBuilder.length() - 5)).append(";", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append("QUIT;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

        scriptSAS.append("data chave_existe (reuse=yes compress=yes);", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "set chave_existe;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append("run;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

        scriptSAS.append("PROC SQL;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "CREATE TABLE mensagem_validacao AS ", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "SELECT distinct \"").append(ste[1].getMethodName()).append("\" as metodo,", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "SCAN(t1.nome_arquivo,1,'_') AS arquivo, ", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "/* parametro */", ScriptBuilder.LINE_SEPARATOR);
        stringBuilder = new StringBuilder();
        stringBuilder.append("(cat(catx(';',");
        for (Coluna coluna : colunasConsolidacao) {
            if (coluna.getClasse() == Coluna.CLASSE_PERIODO) {
                stringBuilder.append("cat('").append(coluna.getNome()).append("=', put(t1.").append(coluna.getNome()).append(",ptgdfmy7.)),");
            } else {
                stringBuilder.append("cat('").append(coluna.getNome()).append("=',").append("t1.").append(coluna.getNome()).append("),");
            }
        }
        scriptSAS.append(2, stringBuilder.toString().substring(0, stringBuilder.length() - 1)).append("))) AS parametros,", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "/* mensagem */", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "'Os valores informados já existem no banco e não podem ser duplicados.' AS mensagem,", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "'false' AS aceitavel", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "FROM WORK.chave_existe t1", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "UNION ALL select * from mensagem_validacao;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append("QUIT;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

        scriptSAS.append("data mensagem_validacao (reuse=yes compress=yes);", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "set mensagem_validacao;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append("run;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

        scriptSAS.append("PROC SQL; DROP TABLE chave_existe;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append("QUIT;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

    }

    private void verificaAcesso(String usuario, List<File> arquivosCSV, File coletaXML) throws SegurancaException {

        File pastaXML = coletaXML.getParentFile();
        if (pastaXML.getAbsolutePath().endsWith("dominios")) {
            pastaXML = pastaXML.getParentFile();
        }
        File[] permissoes = pastaXML.listFiles(new FileFilter() {

            @Override
            public boolean accept(File pathname) {
                if (pathname.getName().endsWith("_permissoes.xml")) {
                    return true;
                }
                return false;
            }
        });

        if (permissoes != null && permissoes.length == 1) {
            String enderecoPermissoes = "";
            String tabelaPermissoes = "";
            try {
                //URL schemaFile = new URL(coletaXSD);
                File schemaFile = new File(coletaXSD);
                SchemaFactory schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
                Schema schema = schemaFactory.newSchema(schemaFile);
                Source xmlFile = new StreamSource(permissoes[0]);
                Validator validator = schema.newValidator();
                validator.validate(xmlFile);
                DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                dbf.setNamespaceAware(false);
                DocumentBuilder docBuilder = dbf.newDocumentBuilder();
                Document doc = docBuilder.parse(permissoes[0]);
                Element coletaTag = doc.getDocumentElement();
                Element scriptTag = (Element) coletaTag.getElementsByTagName("script").item(0);
                Element bancoTag = (Element) coletaTag.getElementsByTagName("banco").item(0);
                enderecoPermissoes = bancoTag.getAttribute("endereco");
                tabelaPermissoes = bancoTag.getAttribute("tabela");
                String nomeLeiaute = scriptTag.getAttribute("leiaute");
                if (nomeLeiaute.toUpperCase().equals("PERMISSOES")) {
                    NodeList colunasTag = ((Element) coletaTag.getElementsByTagName("colunas").item(0)).getElementsByTagName("coluna");
                    if (colunasTag.getLength() == 4) {
                        Element usuarioColunaTag = (Element) colunasTag.item(0);
                        Element leiauteColunaTag = (Element) colunasTag.item(1);
                        Element parametroColunaTag = (Element) colunasTag.item(2);
                        Element valorColunaTag = (Element) colunasTag.item(3);
                        if (usuarioColunaTag.getAttribute("nome") == "usuario"
                                || leiauteColunaTag.getAttribute("nome") == "leiaute"
                                || parametroColunaTag.getAttribute("nome") == "parametro"
                                || valorColunaTag.getAttribute("nome") == "valor") {
                            throw new SegurancaException("Permissões não identificadas.");
                        }
                    } else {
                        throw new SegurancaException("Permissões não identificadas.");
                    }

                } else {
                    throw new SegurancaException("Permissões não identificadas.");
                }
            } catch (Exception ex) {
                throw new SegurancaException("Permissões não identificadas.");
            }

            try {
                ConexaoSAS sasc = new ConexaoSAS();
                sasc.addActionListener(sasActionListener);
                sasc.conectar(sasServer, sasPort, user, password);

                StringBuilder libname = new StringBuilder();
                if (coleta.getBanco().getTipo() == Banco.TIPO_ARQUIVO_SAS) {
                    libname.append("LIBNAME historic base \"").append(enderecoPermissoes).append("\";");
                } else if (coleta.getBanco().getTipo() == Banco.TIPO_BIBLIOTECA_SAS) {
                    libname.append("LIBNAME historic META LIBRARY=").append(enderecoPermissoes).append(";");
                }

                Connection conn = sasc.executarProcessFlow(libname.toString() + "\n"
                        + "PROC SQL;\n"
                        + "   CREATE TABLE WORK.QUERY_FOR_PERMISSOES AS \n"
                        + "   SELECT t1.usuario, \n"
                        + "          t1.leiaute, \n"
                        + "          t1.parametro, \n"
                        + "          t1.valor\n"
                        + "      FROM HISTORIC." + tabelaPermissoes + " t1\n"
                        + "      WHERE t1.usuario = '" + usuario + "' AND t1.leiaute = '" + leiaute + "';\n"
                        + "QUIT;");

                LineType[] lineTypes = sasc.getLogLineTypes();
                String[] log = sasc.getLog();
                for (int i = 0; i < lineTypes.length; i++) {
                    LineType lineType = lineTypes[i];
                    if (lineType.value() == LineType._LineTypeError || log[i].startsWith("ERROR:")) {
                        conn.close();
                        sasActionListener.actionPerformed(new SASAction(SASAction.DESCONECTAR));
                        throw new SASException(log[i]);
                    }
                }

                ResultSet rs = conn.createStatement().executeQuery("SELECT * FROM WORK.QUERY_FOR_PERMISSOES");
                List<Parametro> parametros = new ArrayList();
                while (rs.next()) {
                    boolean parametroNovo = true;
                    for (int i = 0; i < parametros.size(); i++) {
                        if (parametros.get(i).getNome().equals(rs.getString("parametro").replaceAll("^\\s+|\\s+$", ""))) {
                            Parametro parametro = parametros.get(i);
                            parametros.remove(i);
                            parametro.addValor(rs.getString("valor").replaceAll("^\\s+|\\s+$", ""));
                            parametros.add(parametro);
                            parametroNovo = false;
                        }
                    }
                    if (parametroNovo) {
                        Parametro parametro = new Parametro(rs.getString("parametro").trim());
                        parametro.addValor(rs.getString("valor").replaceAll("^\\s+|\\s+$", ""));
                        parametros.add(parametro);
                    }
                }

                conn.close();
                conn = sasc.executarProcessFlow("PROC SQL; DROP TABLE WORK.QUERY_FOR_PERMISSOES;QUIT;");
                conn.close();
                sasActionListener.actionPerformed(new SASAction(SASAction.DESCONECTAR));

                if (parametros.size() > 0) {
                    for (Parametro parametro : parametros) {

                        if (!parametro.getNome().equals("*")) {
                            scriptSAS.append("PROC SQL;").append(ScriptBuilder.LINE_SEPARATOR);
                            scriptSAS.append(1, "CREATE TABLE WORK.USUARIO_ENVIO AS").append(ScriptBuilder.LINE_SEPARATOR);
                            scriptSAS.append(2, "SELECT DISTINCT cat(t1.").append(parametro.getNome()).append(",'') as ").append(parametro.getNome()).append(",", ScriptBuilder.LINE_SEPARATOR);
                            scriptSAS.append(2, "t1.nome_arquivo").append(ScriptBuilder.LINE_SEPARATOR);
                            scriptSAS.append(2, "FROM DADOS t1;").append(ScriptBuilder.LINE_SEPARATOR);
                            scriptSAS.append("QUIT;").append(ScriptBuilder.LINE_SEPARATOR);
                            scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

                            scriptSAS.append("PROC SQL;").append(ScriptBuilder.LINE_SEPARATOR);
                            scriptSAS.append(1, "CREATE TABLE usuario_permissoes AS").append(ScriptBuilder.LINE_SEPARATOR);

                            StringBuilder stringBuilder = new StringBuilder();
                            boolean todosValores = false;
                            for (String valor : parametro.getValor()) {
                                stringBuilder.append("SELECT DISTINCT '").append(valor).append("' as ").append(parametro.getNome()).append("\n");
                                stringBuilder.append("FROM sashelp.class").append("\n");
                                stringBuilder.append("union all").append("\n");
                                if (valor.equals("*")) {
                                    todosValores = true;
                                }
                            }

                            scriptSAS.append(stringBuilder.toString().substring(0, stringBuilder.toString().length() - 10)).append(";", ScriptBuilder.LINE_SEPARATOR);
                            scriptSAS.append("quit;").append(ScriptBuilder.LINE_SEPARATOR);
                            scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

                            if (!todosValores) {
                                final StackTraceElement[] ste = Thread.currentThread().getStackTrace();
                                scriptSAS.append("PROC SQL;", ScriptBuilder.LINE_SEPARATOR);
                                scriptSAS.append(1, "CREATE TABLE mensagem_validacao AS ", ScriptBuilder.LINE_SEPARATOR);
                                scriptSAS.append(1, "SELECT distinct \"").append(ste[1].getMethodName()).append("\" as metodo,", ScriptBuilder.LINE_SEPARATOR);
                                scriptSAS.append(2, "SCAN(t1.nome_arquivo,1,'_') AS arquivo, ", ScriptBuilder.LINE_SEPARATOR);
                                scriptSAS.append(2, "/* parametro */", ScriptBuilder.LINE_SEPARATOR);
                                scriptSAS.append(2, "cat('").append(parametro.getNome()).append(" = ',trim(t1.").append(parametro.getNome()).append(")) as parametros,", ScriptBuilder.LINE_SEPARATOR);
                                scriptSAS.append(2, "/* mensagem */", ScriptBuilder.LINE_SEPARATOR);
                                scriptSAS.append(2, "'Usuário não tem permissão para enviar dados para o parâmetro indicado' AS mensagem,", ScriptBuilder.LINE_SEPARATOR);
                                scriptSAS.append(2, "'false' AS aceitavel", ScriptBuilder.LINE_SEPARATOR);
                                scriptSAS.append(1, "FROM USUARIO_ENVIO t1", ScriptBuilder.LINE_SEPARATOR);
                                scriptSAS.append(1, "LEFT JOIN WORK.USUARIO_PERMISSOES t2 ON (t1.").append(parametro.getNome()).append(" = t2.").append(parametro.getNome()).append(")", ScriptBuilder.LINE_SEPARATOR);
                                scriptSAS.append(1, "WHERE t2.").append(parametro.getNome()).append(" is missing", ScriptBuilder.LINE_SEPARATOR);
                                scriptSAS.append(1, "UNION ALL select * from mensagem_validacao;", ScriptBuilder.LINE_SEPARATOR);
                                scriptSAS.append("QUIT;", ScriptBuilder.LINE_SEPARATOR);
                                scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);
                            }
                        }
                    }

                } else {

                    final StackTraceElement[] ste = Thread.currentThread().getStackTrace();
                    scriptSAS.append("PROC SQL;", ScriptBuilder.LINE_SEPARATOR);
                    scriptSAS.append(1, "CREATE TABLE mensagem_validacao AS ", ScriptBuilder.LINE_SEPARATOR);
                    scriptSAS.append(1, "SELECT distinct \"").append(ste[1].getMethodName()).append("\" as metodo,", ScriptBuilder.LINE_SEPARATOR);
                    scriptSAS.append(2, "SCAN(t1.nome_arquivo,1,'_') AS arquivo, ", ScriptBuilder.LINE_SEPARATOR);
                    scriptSAS.append(2, "/* parametro */", ScriptBuilder.LINE_SEPARATOR);
                    scriptSAS.append(2, "'' as parametros,", ScriptBuilder.LINE_SEPARATOR);
                    scriptSAS.append(2, "/* mensagem */", ScriptBuilder.LINE_SEPARATOR);
                    scriptSAS.append(2, "'Usuário não tem permissão para enviar dados para o parâmetro indicado' AS mensagem,", ScriptBuilder.LINE_SEPARATOR);
                    scriptSAS.append(2, "'false' AS aceitavel", ScriptBuilder.LINE_SEPARATOR);
                    scriptSAS.append(1, "FROM DADOS t1", ScriptBuilder.LINE_SEPARATOR);
                    scriptSAS.append(1, "UNION ALL select * from mensagem_validacao;", ScriptBuilder.LINE_SEPARATOR);
                    scriptSAS.append("QUIT;", ScriptBuilder.LINE_SEPARATOR);
                    scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

                }

                scriptSAS.append("/*Verifica se houve algum erro de permissão de acesso.*/", ScriptBuilder.LINE_SEPARATOR);

                scriptSAS.append("%let dsempty=0;", ScriptBuilder.LINE_SEPARATOR);
                scriptSAS.append("data _null_;", ScriptBuilder.LINE_SEPARATOR);
                scriptSAS.append(1, "if eof then", ScriptBuilder.LINE_SEPARATOR);
                scriptSAS.append(2, "do;", ScriptBuilder.LINE_SEPARATOR);
                scriptSAS.append(3, "call symput('dsempty',1);", ScriptBuilder.LINE_SEPARATOR);
                scriptSAS.append(3, "put 'NOTE: Nenhum erro de permissão de acesso';", ScriptBuilder.LINE_SEPARATOR);
                scriptSAS.append(2, "end;", ScriptBuilder.LINE_SEPARATOR);
                scriptSAS.append(1, "stop;", ScriptBuilder.LINE_SEPARATOR);
                scriptSAS.append(1, "set mensagem_validacao end=eof;", ScriptBuilder.LINE_SEPARATOR);
                scriptSAS.append("run;", ScriptBuilder.LINE_SEPARATOR);
                scriptSAS.append("%macro continua2;", ScriptBuilder.LINE_SEPARATOR);
                scriptSAS.append(1, "%if &dsempty. %then %do;", ScriptBuilder.LINE_SEPARATOR);
                scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

            } catch (Exception ex) {
                ex.printStackTrace();
                throw new SegurancaException("Permissões não identificadas.");
            }

        }
    }

    private void comparaDados(String textContent) {
        scriptSAS.append("/*Verifica chave única*/", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

        scriptSAS.append(textContent, ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

        final StackTraceElement[] ste = Thread.currentThread().getStackTrace();
        scriptSAS.append("PROC SQL;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "CREATE TABLE mensagem_validacao AS ", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "SELECT distinct \"").append(ste[1].getMethodName()).append("\" as metodo,", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "SCAN(t1.nome_arquivo,1,'_') AS arquivo, ", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "/* parametro */", ScriptBuilder.LINE_SEPARATOR);
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("(cat(catx(';',");
        for (Coluna coluna : coleta.getColunas()) {
            if (coluna.getClasse() == Coluna.CLASSE_PERIODO) {
                stringBuilder.append("cat('").append(coluna.getNome()).append("=', put(t1.").append(coluna.getNome()).append(",ptgdfmy7.)),");
            } else {
                stringBuilder.append("cat('").append(coluna.getNome()).append("=',").append("t1.").append(coluna.getNome()).append("),");
            }
        }
        scriptSAS.append(2, stringBuilder.toString().substring(0, stringBuilder.length() - 1)).append("))) AS parametros,", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "/* mensagem */", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "t1.mensagem,", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(2, "'true' AS aceitavel", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "FROM resultado t1", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(1, "UNION ALL select * from mensagem_validacao;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append("QUIT;", ScriptBuilder.LINE_SEPARATOR);
        scriptSAS.append(ScriptBuilder.LINE_SEPARATOR);

    }

}
