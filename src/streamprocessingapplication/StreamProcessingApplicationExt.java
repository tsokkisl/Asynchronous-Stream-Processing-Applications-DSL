package streamprocessingapplication;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Scanner;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import org.apache.activemq.ActiveMQConnection;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.javatuples.Triplet;

public class StreamProcessingApplicationExt extends StreamProcessingApplication {
	
	protected static StreamProcessingApplication spa;
	
	public static void main(String[] args) {
		new StreamProcessingApplicationExt().run();	
	}
	
	protected static StreamProcessingApplication getStreamProcessingApplication() {
		return spa;
	}
	
	protected static void setStreamProcessingApplication(StreamProcessingApplication _spa) {
		spa = _spa;
	}
	
	@Override
	public FileSource createFileSource() {
		return new FileSource() {
			@Override
			public void run(StreamProcessingApplication spa) {
				StreamProcessingApplicationExt.setStreamProcessingApplication(spa);
				/* Start Decompressor and WordCounter consumers on 2 different threads */
				thread(spa.getDecompressor(), false);
				thread(spa.getWordCounter(), false);

				try {

					String topic = "files";

					/* Getting JMS connection from the server and starting it */
					ConnectionFactory connectionFactory = new ActiveMQConnectionFactory(ActiveMQConnection.DEFAULT_BROKER_URL);
					Connection connection = connectionFactory.createConnection();
					connection.start();

					/* Creating a non transaction session to send/receive JMS message */
					Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

					/* The queue will be created automatically on the server */
					Destination destination = session.createTopic(topic);

					/* MessageProducer is used for sending messages to the queue. */
					MessageProducer producer = session.createProducer(destination);

					TextMessage message;

					/* Read directory path */
					System.out.print("Enter directory path : ");
					Scanner input = new Scanner(System.in);
					String dir = input.nextLine();
					input.close();

					/* Get all files in the directory */
					File folder = new File(dir);
					File[] listOfFiles = folder.listFiles();

					/* Publish file paths to the 'files' topic at the ActiveMQ broker */
					for (int i = 0; i < listOfFiles.length; i++) {
						message = session.createTextMessage(String.valueOf(listOfFiles[i].getAbsolutePath()));
						System.out.println("File: " + message.getText() + " sent");
						producer.send(message);
					}
					System.out.println("======================\nEND OF FILE STREAM\n========================");
					connection.close();

				} catch (Exception e) {
					System.out.println("Caught: " + e);
					e.printStackTrace();
				}
			}
		};	
	}
	
	@Override
	public WordCounter createWordCounter() {
		return new WordCounter() { 
			@Override
			public void run() {
				thread(StreamProcessingApplicationExt.getStreamProcessingApplication().getResultSink(), false);

				try {

					String consumeTopic = "files";
					String publishTopic = "word-frequency";
					boolean flag = true;
					HashMap<String, Integer> fileNames = new HashMap<>();
					ArrayList<ArrayList<Triplet<String, Integer, String>>> messages = new ArrayList<ArrayList<Triplet<String, Integer, String>>>();

					/* Getting JMS connection from the server */
					ConnectionFactory connectionFactory = new ActiveMQConnectionFactory(ActiveMQConnection.DEFAULT_BROKER_URL);
					Connection connection = connectionFactory.createConnection();
					connection.start();

					/* Creating session for sending messages */
					Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

					/* Getting the topic */
					Destination destination = session.createTopic(consumeTopic);

					/* MessageConsumer is used for receiving (consuming) messages */
					MessageConsumer consumer = session.createConsumer(destination);

					/* Receive the message */
					Message message = consumer.receive();

					while (message instanceof TextMessage && flag) {
						TextMessage textMessage = (TextMessage) message;
						String extension = textMessage.getText().substring(textMessage.getText().length() - 3);
						if (extension.equals("txt")) {
							System.out.println("Message: " + textMessage.getText() + " received from WordCounter");

							String fileName = textMessage.getText().substring(textMessage.getText().lastIndexOf("/") + 1);

							if (!fileNames.containsKey(fileName)) {
								fileNames.put(fileName, 1);
								/* Calculate the occurrence of each of the words in the file */
								Scanner file = new Scanner(new File(textMessage.getText())).useDelimiter("[^a-zA-Z]+");
								HashMap<String, Integer> map = new HashMap<>();
								ArrayList<Triplet<String, Integer, String>> temp = new ArrayList<Triplet<String, Integer, String>>();

								while (file.hasNext()) {
									String word = file.next().toLowerCase();
									if (map.containsKey(word)) {
										map.put(word, map.get(word) + 1);
									} else {
										map.put(word, 1);
									}
								}
								/* Encapsulate the (word, frequency) tuples */
								map.forEach((key, value) -> {
									Triplet<String, Integer, String> triplet = new Triplet<String, Integer, String>(key,
											Integer.valueOf(value), fileName);
									temp.add(triplet);
								});

								messages.add(temp);
							}
							message = consumer.receive(3000);
							if (message == null)
								flag = false;

						} else {
							message = consumer.receive(3000);
							if (message == null)
								flag = false;
						}
					}

					/* Publish messages to the word-frequency topic */
					try {

						/* The queue will be created automatically on the server */
						destination = session.createTopic(publishTopic);

						/* MessageProducer is used for sending messages to the queue */
						MessageProducer producer = session.createProducer(destination);

						TextMessage msg;

						/* Publish messages to the 'word-frequency' topic at the ActiveMQ broker */
						for (int i = 0; i < messages.size(); i++) {
							for (int j = 0; j < messages.get(i).size(); j++) {
								msg = session.createTextMessage(
										messages.get(i).get(j).getValue2() + "," + messages.get(i).get(j).getValue0() + ","
												+ String.valueOf(messages.get(i).get(j).getValue1()));
								System.out.println("Message: " + msg.getText() + "sent");
								producer.send(msg);
							}
						}

					} catch (Exception e) {
						System.out.println("Caught: " + e);
						e.printStackTrace();
					}

					System.out.println("======================\nEND OF WORDCOUNTER\n========================");
					/* connection.close(); */

				} catch (Exception e) {
					System.out.println("Caught: " + e);
					e.printStackTrace();
				}
			}
		};
	}
	
	@Override
	public Decompressor createDecompressor() {
		return new Decompressor() { 
			@Override
			public void run() {
				try {
					String topic = "files";
					boolean flag = true;
					// Getting JMS connection from the server
					ConnectionFactory connectionFactory = new ActiveMQConnectionFactory(ActiveMQConnection.DEFAULT_BROKER_URL);
					Connection connection = connectionFactory.createConnection();

					connection.start();

					/* Creating session for sending messages */
					Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

					/* Getting the topic */
					Destination destination = session.createTopic(topic);

					/* MessageConsumer is used for receiving (consuming) messages */
					MessageConsumer consumer = session.createConsumer(destination);

					/* MessageProducer is used for sending messages to the queue */
					MessageProducer producer = session.createProducer(destination);

					/* Receive the message */
					Message message = consumer.receive();

					while (message instanceof TextMessage && flag) {
						TextMessage textMessage = (TextMessage) message;
						String extension = textMessage.getText().substring(textMessage.getText().length() - 3);
						/*
						 * If the file is a zip, unzip it else skip it and consume the next message in
						 * the topic
						 */
						if (extension.equals("zip")) {
							System.out.println("Unzipping " + textMessage.getText());
							byte[] buffer = new byte[1024];
							FileInputStream fis = new FileInputStream(textMessage.getText());
							ZipInputStream zis = new ZipInputStream(fis);
							ZipEntry ze = zis.getNextEntry();
							String dir = textMessage.getText().substring(0, textMessage.getText().lastIndexOf("/"));
							while (ze != null && !ze.isDirectory()) {
								String fileName = dir + "/" + ze.getName();
								File newFile = new File(fileName);
								FileOutputStream fos = new FileOutputStream(newFile);
								int len;
								while ((len = zis.read(buffer)) > 0)
									fos.write(buffer, 0, len);
								fos.close();
								zis.closeEntry();
								ze = zis.getNextEntry();
								TextMessage m = session.createTextMessage(String.valueOf(fileName));
								producer.send(m);
								System.out.println("File: " + m.getText() + " sent");
							}
							zis.closeEntry();
							zis.close();
							fis.close();
							message = consumer.receive(3000);
							if (message == null)
								flag = false;
						} else {
							message = consumer.receive(3000);
							if (message == null)
								flag = false;
						}
					}
					System.out.println("======================\nEND OF DECOMPRESSOR\n========================");
					/* connection.close(); */
				} catch (Exception e) {
					System.out.println("Caught: " + e);
					e.printStackTrace();
				}
			}
		};
	}
	@Override
	public ResultSink createResultSink() {
		return new ResultSink() { 
			@Override
			public void run() {
				try {

					String topic = "word-frequency";
					String dir = "/Users/lenos/desktop/University of York/MSc Advanced Computer Science/Model-Driven Engineering - MODE/Assessment/test";

					/* Getting JMS connection from the server */
					ConnectionFactory connectionFactory = new ActiveMQConnectionFactory(ActiveMQConnection.DEFAULT_BROKER_URL);
					Connection connection = connectionFactory.createConnection();

					connection.start();

					/* Creating session for sending messages */
					Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

					/* Getting the topic */
					Destination destination = session.createTopic(topic);

					/* MessageConsumer is used for receiving (consuming) messages */
					MessageConsumer consumer = session.createConsumer(destination);

					/* Receive the message */
					Message message = consumer.receive();
					TextMessage textMessage = (TextMessage) message;
					String[] tmp = textMessage.getText().split(",");
					String file = tmp[0];

					ArrayList<ArrayList<String>> data = new ArrayList<ArrayList<String>>();

					while (message instanceof TextMessage) {

						System.out.println("Message: " + textMessage.getText() + " received from ResultSink");

						/* Get file name */
						String f = tmp[0];

						ArrayList<String> d = new ArrayList<String>();
						d.add(tmp[1]);
						d.add(tmp[2]);

						/* Check file name */
						if (!file.equals(f)) {

							System.out.println("======================\nCreating " + file.substring(0, file.lastIndexOf("."))
									+ ".csv" + "\n========================");

							/* Create CSV file */
							FileWriter writer = new FileWriter(
									new File(dir + "/" + file.substring(0, file.lastIndexOf(".")) + ".csv"));

							writer.append("Word");
							writer.append(", ");
							writer.append("Frequency");
							writer.append('\n');

							for (int i = 0; i < data.size(); i++) {
								try {
									writer.append(data.get(i).get(0));
									writer.append(", ");
									writer.append(data.get(i).get(1));
									writer.append('\n');
								} catch (IOException e) {
									// TODO Auto-generated catch block
									e.printStackTrace();
								}
							}
							writer.flush();
							writer.close();

							file = f;
							data = new ArrayList<ArrayList<String>>();
							data.add(d);

						} else {

							data.add(d);
						}

						message = consumer.receive(3000);
						if (message == null)
							break;
						textMessage = (TextMessage) message;
						tmp = textMessage.getText().split(",");
					}
					System.out.println("======================\nCreating " + file.substring(0, file.lastIndexOf(".")) + ".csv"
							+ "\n========================");

					/* Create CSV file for the last message */
					FileWriter writer = new FileWriter(new File(dir + "/" + file.substring(0, file.lastIndexOf(".")) + ".csv"));

					writer.append("Word");
					writer.append(", ");
					writer.append("Frequency");
					writer.append('\n');

					for (int i = 0; i < data.size(); i++) {
						try {
							writer.append(data.get(i).get(0));
							writer.append(", ");
							writer.append(data.get(i).get(1));
							writer.append('\n');
						} catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}
					writer.flush();
					writer.close();

					System.out.println("======================\nEND OF RESULT SINK\n========================");
					connection.close();

				} catch (Exception e) {
					System.out.println("Caught: " + e);
					e.printStackTrace();
				}
			}
		};
	}
}
