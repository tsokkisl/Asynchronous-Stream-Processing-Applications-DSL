package streamprocessingapplication;

/* protected region Imports on begin */
/* protected region Imports end */

public class WordCounter implements Runnable {
	
	/* protected region Task on begin */
	public static void thread(Runnable runnable, boolean daemon) {
		Thread brokerThread = new Thread(runnable);
		brokerThread.setDaemon(daemon);
		brokerThread.start();
	}
	
	public void run() {
		
	}
	
	/* protected region Task end */
	
}