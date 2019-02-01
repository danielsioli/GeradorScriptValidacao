/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package gerador.sas;

import java.io.File;
import java.io.FileNotFoundException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;
import java.util.Scanner;

import com.sas.iom.SAS.IDataService;
import com.sas.iom.SAS.ILanguageService;
import com.sas.iom.SAS.IWorkspace;
import com.sas.iom.SAS.IWorkspaceHelper;
import com.sas.iom.SAS.ILanguageServicePackage.CarriageControl;
import com.sas.iom.SAS.ILanguageServicePackage.CarriageControlSeqHolder;
import com.sas.iom.SAS.ILanguageServicePackage.LineType;
import com.sas.iom.SAS.ILanguageServicePackage.LineTypeSeqHolder;
import com.sas.iom.SASIOMDefs.GenericError;
import com.sas.iom.SASIOMDefs.StringSeqHolder;
import com.sas.rio.MVAConnection;
import com.sas.services.connection.BridgeServer;
import com.sas.services.connection.ConnectionFactoryConfiguration;
import com.sas.services.connection.ConnectionFactoryException;
import com.sas.services.connection.ConnectionFactoryInterface;
import com.sas.services.connection.ConnectionFactoryManager;
import com.sas.services.connection.ConnectionInterface;
import com.sas.services.connection.ManualConnectionFactoryConfiguration;
import com.sas.services.connection.Server;

/**
 * Encapsula a conexão ao servidor SAS, com o objetivo de automatizar a execução
 * de um Process Flow.
 *
 * @author marx
 */
public class ConexaoSAS {

    private ConnectionInterface connectionInterface;
    private CarriageControlSeqHolder logControlSH;
    private LineTypeSeqHolder logTypeSH;
    private StringSeqHolder logStringSH;
    private SASActionListener sasActionListener;
    
    public void addActionListener(SASActionListener sasActionListener){
        this.sasActionListener = sasActionListener;
    }
    
    /**
     * Conecta ao servidor SAS utilizando os parâmetros fornecidos.
     */
    public void conectar(String host, int port, String username, String password) throws SASException {
        Server server = new BridgeServer(Server.CLSID_SAS, host, port);

        ConnectionFactoryConfiguration cfConfig = new ManualConnectionFactoryConfiguration(server);
        ConnectionFactoryManager cfManager = new ConnectionFactoryManager();
        ConnectionFactoryInterface cfInterface;

        try {
            cfInterface = cfManager.getFactory(cfConfig);
            connectionInterface = cfInterface.getConnection(username, password);
        } catch (ConnectionFactoryException e) {
            throw new SASException("Não foi possível conectar a " + host + ": " + e.getMessage());
        }
        
        if(sasActionListener != null){
            sasActionListener.actionPerformed(new SASAction(SASAction.CONECTAR));
        } else {
            throw new SASException("Nenhum SASActionListener Encontrado");
        }
    }
    
    /**
     * Executa um código fonte SAS equivalente a um Process Flow. Para gerar
     * código o SAS no SAS Enterprise Guide, deve ser utilizada a funcionalidade
     * "Export -> Export All Code In Process Flow...".
     *
     * @param scriptSAS
     * @return Um objeto Connection que pode ser usado para leitura dos dados de
     * saída a partir da interface JDBC.
     */
    public synchronized Connection executarProcessFlow(String scriptSAS) throws SASException {
        org.omg.CORBA.Object obj = connectionInterface.getObject();
        IWorkspace workspace = IWorkspaceHelper.narrow(obj);
        ILanguageService sasLanguage = workspace.LanguageService();
        
        if(sasActionListener != null){
            final StackTraceElement[] ste = Thread.currentThread().getStackTrace();
            sasActionListener.actionPerformed(new SASAction(ste,scriptSAS.toString()));
        } else {
            throw new SASException("Nenhum SASActionListener Encontrado");
        }
        
        // Executa o código SAS de entrada
        try {
            sasLanguage.Submit(scriptSAS);
        } catch (Exception e) {
            e.printStackTrace();
            throw new SASException("Erro ao executar o código SAS : " + e.getMessage());
        }

        // Lê o log gerado pelo SAS
        logControlSH = new CarriageControlSeqHolder();
        logTypeSH = new LineTypeSeqHolder();
        logStringSH = new StringSeqHolder();
        try {
            sasLanguage.FlushLogLines(Integer.MAX_VALUE, logControlSH, logTypeSH, logStringSH);
            
            /*for(String log : logLines){
            System.out.println(log);
            }
            
            logControlSH = new CarriageControlSeqHolder();
            logTypeSH = new LineTypeSeqHolder();
            logStringSH = new StringSeqHolder();
            sasLanguage.FlushLogLines(Integer.MAX_VALUE, logControlSH, logTypeSH, logStringSH);
            String[] listLines = logStringSH.value;
            
            for(String log : listLines){
            System.out.println(log);
            }*/
            
        } catch (GenericError e) {
            throw new SASException("Erro ao ler o log de execução do script: " + e.getMessage());
        }

        // Executa o código SQL para obter a conexão
        Connection connection;
        try {
            IDataService dataService = workspace.DataService();
            connection = new MVAConnection(dataService, new Properties());
        } catch (SQLException e) {
            throw new SASException("Não foi possível ler a saída da execução do scipt: " + e.getMessage());
        }

        return connection;
    }

    /**
     * Lê um arquivo de texto e escreve o resultado em uma String simples.
     *
     * @throws FileNotFoundException
     */
    @SuppressWarnings("resource")
    private String readFile(String arquivo) throws FileNotFoundException {
        return new Scanner(new File(arquivo)).useDelimiter(System.getProperty("line.separator")).next();
    }

    /**
     * Retorna um array de objetos CarriageControl, os quais descrevem o fluxo
     * de páginas do log obtido por {@link #getLog()}. Cada objeto no array
     * corresponde à linha de índice equivalente no log.
     *
     * @see
     * <a href="https://support.sas.com/rnd/itech/doc/dist-obj/javadoc/ilngij.html#ilngIfaceCarriageControlV1.0">CarriageControl
     * em http://support.sas.com/</a>
     */
    public CarriageControl[] getLogCarriageControls() {
        return logControlSH.value;
    }

    /**
     * Retorna um array de objetos LineType, os quais descrevem o tipo de cada
     * linha do log obtido por {@link #getLog()}. Cada objeto no array
     * corresponde à linha de índice equivalente no log.
     *
     * @see
     * <a href="https://support.sas.com/rnd/itech/doc/dist-obj/javadoc/ilngij.html#ilngIfaceLineTypeV1.0">LineType
     * em http://support.sas.com/</a>
     */
    public LineType[] getLogLineTypes() {
        return logTypeSH.value;
    }

    /**
     * Retorna o log de execução gerado pelo SAS após uma chamada de
     * {@link #executarProcessFlow(String)}. Os métodos
     * {@link #getLogLineTypes()} e {@link #getLogCarriageControlArray} retornam
     * informações adicionais sobre cada linha do log.
     */
    public String[] getLog() {
        return logStringSH.value;
    }

}
