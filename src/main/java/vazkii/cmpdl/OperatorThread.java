package vazkii.cmpdl;

import java.nio.file.Path;

public class OperatorThread extends Thread {

    String url;
    String version;
    Path file;

    public OperatorThread(String url, String version) {
        this.url = url;
        this.version = version;
        setName("Operator");
        setDaemon(true);
        start();
    }

    public OperatorThread(Path file) {
        this.file = file;
        setName("Operator");
        setDaemon(true);
        start();
    }

    @Override
    public void run() {
        try {
            if (file != null) {
                CMPDL.setupFromLocalFile(file);
            }
            CMPDL.downloadFromURL(url, version);
        } catch (Exception ex) {
            Interface.addLogLine("Error: " + ex.getClass().toString() + ": " + ex.getLocalizedMessage());
            for (StackTraceElement e : ex.getStackTrace()) {
                Interface.addLogLine(e.toString());
            }

            ex.printStackTrace();

            Interface.error();
        }

        Interface.operatorThread = null;
    }

}
