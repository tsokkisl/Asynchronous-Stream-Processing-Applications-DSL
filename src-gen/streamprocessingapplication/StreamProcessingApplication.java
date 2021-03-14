package streamprocessingapplication;

public class StreamProcessingApplication {
	
		
	protected WordCounter WordCounter = createWordCounter();
		
		
	protected Decompressor Decompressor = createDecompressor();
		
		
	protected FileSource FileSource = createFileSource();
		
		
	protected ResultSink ResultSink = createResultSink();
		
	
	public static void main(String[] args) {
		new StreamProcessingApplication().run();
	}
	
	public void run() {
		getFileSource().run(this);	
	}
	
		
	protected WordCounter createWordCounter() {
		return new WordCounter();
	}
	
	protected WordCounter getWordCounter() {
		return WordCounter;
	}
	
		
	protected Decompressor createDecompressor() {
		return new Decompressor();
	}
	
	protected Decompressor getDecompressor() {
		return Decompressor;
	}
	
		
	protected FileSource createFileSource() {
		return new FileSource();
	}
	
	protected FileSource getFileSource() {
		return FileSource;
	}
	
		
	protected ResultSink createResultSink() {
		return new ResultSink();
	}
	
	protected ResultSink getResultSink() {
		return ResultSink;
	}
	
	
}
