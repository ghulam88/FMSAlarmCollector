package com.mastercom.snmp;

import java.io.BufferedWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Date;
import java.util.Properties;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.log4j.DailyRollingFileAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.json.simple.JSONObject;
import org.snmp4j.CommandResponder;
import org.snmp4j.CommandResponderEvent;
import org.snmp4j.MessageDispatcherImpl;
import org.snmp4j.Snmp;
import org.snmp4j.TransportMapping;
import org.snmp4j.mp.MPv2c;
import org.snmp4j.mp.MPv3;
import org.snmp4j.security.SecurityModels;
import org.snmp4j.security.SecurityProtocols;
import org.snmp4j.security.USM;
import org.snmp4j.smi.Address;
import org.snmp4j.smi.GenericAddress;
import org.snmp4j.smi.OctetString;
import org.snmp4j.smi.TcpAddress;
import org.snmp4j.smi.UdpAddress;
import org.snmp4j.transport.DefaultTcpTransportMapping;
import org.snmp4j.transport.DefaultUdpTransportMapping;
import org.snmp4j.util.MultiThreadedMessageDispatcher;
import org.snmp4j.util.ThreadPool;

public class SNMPReceiver implements CommandResponder {

	private static final Logger logger = Logger.getLogger(SNMPReceiver.class.getName());
	private MultiThreadedMessageDispatcher dispatcher;
	private static Producer<String, String> producer = null;
	private Snmp snmp = null;
	private Address listenAddress;
	private ThreadPool threadPool;
	private static String emsName;
	private static String emsType;
	static int trapCounter = 0;
	static BufferedWriter writer = null;

	public SNMPReceiver() {

	}

	public static void main(String[] args) {

		try {			
			emsName = args[0];
			emsType = args[1];
			
			initLog4J(); // Initializes log4J logger library.
			initKafka(); // Initializes Kafka
			logger.info("Trap receiving program started for "+emsName);
			new SNMPReceiver().run();
			System.out.println("receiver started for :"+emsName);
		} catch (Exception ex) {
			ex.printStackTrace();
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			ex.printStackTrace(pw);
			String errorString = sw.toString();
			logger.info(errorString);
		}
	}

	/**
	 * run method will be called once from the main method. It is starting point of
	 * trap receiver program.
	 */
	private void run() {
		try {
			init();
			snmp.addCommandResponder(this);
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	/**
	 * Initializes trap receiver program and make it ready to receive trap from the
	 * device.
	 */
	private void init() {

		try {
			// Creating pool of threads to receive traps.
			threadPool = ThreadPool.create("Trap", 25);
			dispatcher = new MultiThreadedMessageDispatcher(threadPool, new MessageDispatcherImpl());
			// Set port address on which trap will be received from the device.
			listenAddress = GenericAddress.parse(System.getProperty("snmp4j.listenAddress", "udp:0.0.0.0/162"));
			TransportMapping transport;

			if (listenAddress instanceof UdpAddress) {
				transport = new DefaultUdpTransportMapping((UdpAddress) listenAddress);
			} else {
				transport = new DefaultTcpTransportMapping((TcpAddress) listenAddress);
			}

			USM usm = new USM(SecurityProtocols.getInstance(), new OctetString(MPv3.createLocalEngineID()), 0);
			SecurityModels.getInstance().addSecurityModel(usm);

			snmp = new Snmp(dispatcher, transport);
			snmp.getMessageDispatcher().addMessageProcessingModel(new MPv2c());
			snmp.listen();
			logger.info("waiting for traps......");

		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	/**
	 * This method will be called by the program whenever a trap is received. For
	 * each and every traps this method will be called.
	 */
	@Override
	public synchronized void processPdu(CommandResponderEvent event) {

		try {
			JSONObject trapObject = new JSONObject();
			trapCounter++;
			logger.info("Received trap Count:" + trapCounter);
			System.out.println("Trap received: "+trapCounter);			
			JSONObject trapObj = new JSONObject();
			trapObj.put("emsName", emsName);
			trapObj.put("emsType", emsType);
			trapObj.put("notificationTime", new Date().getTime());
			trapObj.put("rawTrap", event.toString());
			
			logger.info("Writing to file and pushing to Kafka: " + trapObj.toJSONString());
			System.out.println("Writing to Kafka");
			PushMessageToKafka("raw-alarms", trapCounter + "", trapObj.toJSONString());
			System.out.println("Written to Kafka");
//			writer.append(event.toString());
//			writer.newLine();
//			writer.flush();

		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	public static void initKafka() throws Exception {

		Properties props = new Properties();
		//props.put("bootstrap.servers", "localhost:9092,localhost:9093");
		props.put("bootstrap.servers", "192.168.4.33:9092,192.168.4.33:9093");
		props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
		props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
		props.put("enable.idempotence", true);
		props.put("acks", "all");
		props.put("retries", 5);
		props.put("batch.size", 16384);
		props.put("linger.ms", 1);

		producer = new KafkaProducer<>(props);

	}

	public static void PushMessageToKafka(String topicName, String key, String value) {
		try {
		ProducerRecord<String, String> record = new ProducerRecord<>(topicName, key, value);
		producer.send(record);
		}catch(Exception ex) {
			ex.printStackTrace();
		}
	}

	/**
	 * Initialize the logger. Logger keep on logging all the logs of the program.
	 */
	private static void initLog4J() {

		PatternLayout layout = new PatternLayout();
		String conversionPattern = "[%p] %d %c %M - %m%n";
		layout.setConversionPattern(conversionPattern);

		// creates daily rolling file appender
		DailyRollingFileAppender rollingAppender = new DailyRollingFileAppender();
		//rollingAppender.setFile("D:\\alltraps.log"); // Daily
		rollingAppender.setFile("/home/AlarmReceiverAdaptor/alltraps.log"); // Daily
		rollingAppender.setDatePattern("'.'yyyy-MM-dd"); // Pattern of daily log
															// file.
		rollingAppender.setLayout(layout);
		rollingAppender.activateOptions();

		// configures the root logger
		Logger rootLogger = Logger.getRootLogger();
		rootLogger.setLevel(Level.DEBUG);
		rootLogger.addAppender(rollingAppender);

	}
}