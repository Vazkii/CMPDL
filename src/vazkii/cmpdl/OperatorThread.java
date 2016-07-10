package vazkii.cmpdl;

public class OperatorThread extends Thread {

	String url;

	public OperatorThread(String url) {
		this.url = url;
		setName("Operator");
		setDaemon(true);
		start();
	}

	@Override
	public void run() {
		try {
			CMPDL.downloadFromURL(url);
		} catch(Exception ex) {
			Interface.addLogLine("Error: " + ex.getLocalizedMessage());
			for(StackTraceElement e : ex.getStackTrace())
				Interface.addLogLine(e.toString());

			ex.printStackTrace();

			CMPDL.downloading = false;
			Interface.setStatus("Errored");
		}

		Interface.operatorThread = null;
	}

}
