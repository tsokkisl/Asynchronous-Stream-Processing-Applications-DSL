package streamprocessingapplication;

/* protected region Imports on begin */
/* protected region Imports end */

public class FileSource {
	
	/* protected region Source on begin */
	public static void thread(Runnable runnable, boolean daemon) {
		Thread brokerThread = new Thread(runnable);
		brokerThread.setDaemon(daemon);
		brokerThread.start();
	}
	
	public void run(StreamProcessingApplication StreamProcessingApplication) {
		
		
	}
	
	/* protected region Source end */
}