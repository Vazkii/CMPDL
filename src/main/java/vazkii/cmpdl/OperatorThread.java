package vazkii.cmpdl;

public class OperatorThread extends Thread {

	String url;
	String version;

	public OperatorThread(String url, String version) {
		this.url = url;
		this.version = version;
		setName("Operator");
		setDaemon(true);
		start();
	}

	@Override
	public void run() {
		try {
			CMPDL.downloadFromURL(url, version);
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
