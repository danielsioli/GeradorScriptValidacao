/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package gerador;

/**
 * Substitui as funcionalidades do StringBuffer
 */
public class ScriptBuilder {

    /**
     *
     */
    private final StringBuilder sasProgram = new StringBuilder();
    /**
     *
     */
    public static final boolean LINE_SEPARATOR = true;

    /**
     *
     * @param tab
     * @param texto
     * @param lineSeparator
     * @return
     */
    public ScriptBuilder append(int tab, String texto, boolean lineSeparator) {
        for (int i = 0; i < tab; i++) {
            sasProgram.append("\t");
        }
        sasProgram.append(texto);
        if (lineSeparator) {
            sasProgram.append(System.getProperty("line.separator"));
        }
        return this;
    }

    /**
     *
     * @param tab
     * @param texto
     * @return
     */
    public ScriptBuilder append(int tab, String texto) {
        for (int i = 0; i < tab; i++) {
            sasProgram.append("\t");
        }
        sasProgram.append(texto);
        return this;
    }

    /**
     *
     * @param texto
     * @param lineSeparator
     * @return
     */
    public ScriptBuilder append(String texto, boolean lineSeparator) {
        sasProgram.append(texto);
        if (lineSeparator) {
            sasProgram.append(System.getProperty("line.separator"));
        }
        return this;
    }

    /**
     *
     * @param texto
     * @return
     */
    public ScriptBuilder append(String texto) {
        sasProgram.append(texto);
        return this;
    }

    /**
     *
     * @param lineSeparator
     * @return
     */
    public ScriptBuilder append(boolean lineSeparator) {
        if (lineSeparator) {
            sasProgram.append(System.getProperty("line.separator"));
        }
        return this;
    }

    /**
     *
     * @param numero
     * @return
     */
    public ScriptBuilder append(int numero) {
        sasProgram.append(numero);
        return this;
    }

    /**
     *
     * @param numero
     * @return
     */
    public ScriptBuilder append(double numero) {
        sasProgram.append(numero);
        return this;
    }

    /**
     *
     * @param numero
     * @return
     */
    public ScriptBuilder append(float numero) {
        sasProgram.append(numero);
        return this;
    }

    /**
     *
     * @param numero
     * @return
     */
    public ScriptBuilder append(long numero) {
        sasProgram.append(numero);
        return this;
    }

    /**
     *
     * @param tab
     * @param numero
     * @return
     */
    public ScriptBuilder append(int tab, int numero) {
        for (int i = 0; i < tab; i++) {
            sasProgram.append("\t");
        }
        sasProgram.append(numero);
        return this;
    }

    /**
     *
     * @param tab
     * @param numero
     * @return
     */
    public ScriptBuilder append(int tab, double numero) {
        for (int i = 0; i < tab; i++) {
            sasProgram.append("\t");
        }
        sasProgram.append(numero);
        return this;
    }

    /**
     *
     * @param tab
     * @param numero
     * @return
     */
    public ScriptBuilder append(int tab, float numero) {
        for (int i = 0; i < tab; i++) {
            sasProgram.append("\t");
        }
        sasProgram.append(numero);
        return this;
    }

    /**
     *
     * @param tab
     * @param numero
     * @return
     */
    public ScriptBuilder append(int tab, long numero) {
        for (int i = 0; i < tab; i++) {
            sasProgram.append("\t");
        }
        sasProgram.append(numero);
        return this;
    }

    /**
     *
     * @param numero
     * @param lineSeparator
     * @return
     */
    public ScriptBuilder append(int numero, boolean lineSeparator) {
        sasProgram.append(numero);
        if (lineSeparator) {
            sasProgram.append(System.getProperty("line.separator"));
        }
        return this;
    }

    /**
     *
     * @param numero
     * @param lineSeparator
     * @return
     */
    public ScriptBuilder append(double numero, boolean lineSeparator) {
        sasProgram.append(numero);
        if (lineSeparator) {
            sasProgram.append(System.getProperty("line.separator"));
        }
        return this;
    }

    /**
     *
     * @param numero
     * @param lineSeparator
     * @return
     */
    public ScriptBuilder append(float numero, boolean lineSeparator) {
        sasProgram.append(numero);
        if (lineSeparator) {
            sasProgram.append(System.getProperty("line.separator"));
        }
        return this;
    }

    /**
     *
     * @param numero
     * @param lineSeparator
     * @return
     */
    public ScriptBuilder append(long numero, boolean lineSeparator) {
        sasProgram.append(numero);
        if (lineSeparator) {
            sasProgram.append(System.getProperty("line.separator"));
        }
        return this;
    }

    /**
     *
     * @return
     */
    @Override
    public String toString() {
        return sasProgram.toString();
    }
}
