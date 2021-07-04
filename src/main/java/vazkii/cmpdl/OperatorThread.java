package vazkii.cmpdl;

import java.nio.file.Path;

public class OperatorThread extends Thread {

    String url;
    Path file;

    public OperatorThread(String url) {
        this.url = url;
        setName("CMPDL Operator");
        setDaemon(true);
        start();
    }

    public OperatorThread(Path file) {
        this.file = file;
        setName("CMPDL Operator");
        setDaemon(true);
        start();
    }

    @Override
    public void run() {
        try {
            if (file != null) {
                CMPDL.setupFromLocalFile(file);
            } else {
                CMPDL.downloadFromURL(url);
            }
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
