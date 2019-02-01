/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package gerador.sas;

/**
 *
 * @author danieloliveira
 */
public class SASAction {
    
    public static final int CONECTAR = 0;
    public static final int DESCONECTAR = 1;
    public static final int RODAR_SCRIPT = 2;
    private int action;
    private String script;
    private StackTraceElement[] stackTraceElement;

    public SASAction(StackTraceElement[] stackTraceElement,String script){
        this.action = RODAR_SCRIPT;
        this.script = script;
        this.stackTraceElement = stackTraceElement;
    }
    
    public SASAction(int action) {
        this.action = action;
    }

    public String getScript() {
        return script;
    }

    public StackTraceElement[] getStackTraceElement() {
        return stackTraceElement;
    }
    
    public int getAction(){
        return action;
    }
    
    
}
