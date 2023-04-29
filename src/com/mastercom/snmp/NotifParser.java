package com.mastercom.snmp;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

public class NotifParser {
	private static final Logger LOG = LoggerFactory.getLogger(NotifParser.class);

	private static final String VENDOR_NAME = "CIENA";
	private static ObjectMapper eventMapper;
	static Producer<String, String> producer;
	public static void main(String args[]) throws Exception {
		Properties eventProps = new Properties();
		
		//jsonEventProducer = new JSONEventProducerKafka();
		eventMapper = new ObjectMapper();
		initKafka();
		Thread.sleep(1500);
		PushMessageToKafka("test1", "1332", "hellow");
//		try {
//			// BufferedReader br = new BufferedReader(new
//			// FileReader("/opt/TMF814Alarms.txt"));
//			BufferedReader br = new BufferedReader(new FileReader("D:\\TMF814Events.txt"));
//			String line = br.readLine();
//			int i=0;
//			while (line != null) {
//				
//				System.out.println(line);
//				String[] keyValue = line.split(":");
//				eventProps.setProperty(keyValue[0].trim(), keyValue[1].trim());
//				line = br.readLine();
//				
//				if(line != null && line.equalsIgnoreCase("########START#########")) {
//					line = br.readLine();
//					TMF814JSONEventObject jsonEventObj = generateJsonEvent(eventProps);
//					 String jsonEventStr = eventMapper.writeValueAsString(jsonEventObj.jsonEvent);
//					 i++;
//					PushMessageToKafka("test1", i+"", jsonEventStr);
//					eventProps.clear();
//				}
//				
//			}
//
//			
//
//			br.close();
//		} catch (FileNotFoundException e) {
//			System.out.println("File not found: " + e.getMessage());
//		} catch (IOException e) {
//			System.out.println("Error reading file: " + e.getMessage());
//		}

	}

	public static void initKafka() throws Exception {
		System.out.println("init kafka");
		Properties props = new Properties();
		props.put("bootstrap.servers", "localhost:9092");
		//props.put("bootstrap.servers", "192.168.4.33:9092,192.168.4.33:9093");
		props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
		props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
		props.put("enable.idempotence", true);
		props.put("acks", "all");
		props.put("retries", 5);
		props.put("batch.size", 16384);
		props.put("linger.ms", 1);

		producer = new KafkaProducer<>(props);
		
		if (producer.partitionsFor("test1").isEmpty()) {
			System.out.println("Kafka producer is not connected!");
		} else {
			System.out.println("Kafka producer is connected!");
		}
	}
	public static void PushMessageToKafka(String topicName, String key, String value) {
		try {
			System.out.println(value);
			System.out.println(topicName);
			ProducerRecord<String, String> record = new ProducerRecord<>(topicName, key, value);
			producer.send(record);
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}
	public static TMF814JSONEventObject generateJsonEvent(Properties eventProps) {
		TMF814JSONEventObject jsonEvntObject = new TMF814JSONEventObject();

		TMF814JSONEvent jsonEvent = new TMF814JSONEvent();
		jsonEvent.setEventType(eventProps.getProperty("EventType"));
		jsonEvent.setProbableCauseQualifier(eventProps.getProperty("probableCauseQualifier"));
		jsonEvent.setEmsName(eventProps.getProperty("EMS"));
		jsonEvent.setNbiEmsName(eventProps.getProperty("NBIEMSNAME"));
		jsonEvent.setProbableCause(eventProps.getProperty("probableCause"));
		jsonEvent.setObjectType(NotifUtil.getObjectType(eventProps.getProperty("objectType")));
		jsonEvent.setAdditionalText(eventProps.getProperty("additionalText"));
		jsonEvent.setSeverity(NotifUtil.getSeverity(eventProps.getProperty("perceivedSeverity")));
		jsonEvent.setEmsTime(eventProps.getProperty("emsTime"));
		jsonEvent.setNeTime(eventProps.getProperty("neTime"));
		jsonEvent.setFullName(getFullName(eventProps));
		jsonEvent.setEventId(NotifUtil.getEventId());
		jsonEvent.setId(eventProps.getProperty("notificationId"));
		jsonEvent.setLayerRate(getLayerRate(eventProps.getProperty("layerRate"), eventProps.getProperty("CTP")));
		jsonEvent.setVendor(VENDOR_NAME);
		jsonEvent.setStandardName(getStandardName(eventProps.getProperty("probableCause"),
				eventProps.getProperty("probableCauseQualifier"), eventProps.getProperty("layerRate"),
				eventProps.getProperty("additionalText")));
		jsonEvent.setType("communicationsAlarm");
		jsonEvent.setNativeEMSName(eventProps.getProperty("nativeEMSName"));
		jsonEvent.setNativeProbableCause(eventProps.getProperty("nativeProbableCause"));

		if (eventProps.getProperty("objectType").equals("OT_CONNECTION_TERMINATION_POINT")) {
			jsonEvntObject.eventObjectType = "CTP";
		} else {
			jsonEvntObject.eventObjectType = "NONCTP";
		}
		jsonEvntObject.jsonEvent = jsonEvent;

		return jsonEvntObject;
	}

	private static String getLayerRate(String layerRate, String ctp) {
		if (layerRate != null) {
			if (layerRate.equals("98") || (ctp != null && ctp.equals("/encapsulation=1")))
				return "Encapsulation";
			if (layerRate.equals("5"))
				return "E1_PDH";
			if (layerRate.equals("11"))
				return "VC12";
			if (layerRate.equals("13"))
				return "VC3";
			if (layerRate.equals("15"))
				return "VC4";
			if (layerRate.equals("96"))
				return "Ethernet";
		}
		return "N/A";
	}

	private static String getStandardName(String probableCause, String probableCauseQualifier, String layerRate,
			String additionalText) {
		if (probableCause != null) { // Not heartbeat event
			if (probableCause.equals("TU-AIS"))
				return "TU_AIS";
			if (probableCause.equals("LOS") && probableCauseQualifier.equals("493"))
				return "VCG_LINK_DOWN";
			if (probableCause.equals("AIS") && probableCauseQualifier.equals("203"))
				return "PDH_AIS";
			if (probableCause.equals("RAI")) {
				if (probableCauseQualifier.equals("409")) {
					return "VCG_FAR_END_CLIENT_SIGNAL_FAILURE";
				}
				return "RAI";
			}
			if (probableCause.equals("ENV"))
				return "GENERIC_ENVIRONMENTAL_ALARM_ME";
			if (probableCause.equals("UNEQ"))
				return "UNEQUIPPED";
			if (probableCause.equals("BER_SF"))
				return "VC_BER_SF";
			if (probableCause.equals("LOM"))
				return "LOM";
			if (probableCause.equals("BER_SD")) {
				if (probableCauseQualifier.equals("519"))
					return "ETHERNET_BER_SD";
				if (probableCauseQualifier.equals("496"))
					return "VCG_BER_SD";
			}

			if (probableCause.equals("BER_SD") && probableCauseQualifier.equals("DEG"))
				return "VC_BER_SD";
			if (probableCause.equals("TX_DEGRADE"))
				return "LOW_RX_POWER";
			if (probableCause.equals("LOF"))
				return "LOSS_OF_FRAME_DE-LINEATION";
			if (probableCause.equals("PLM"))
				return "PLM";
			if (probableCause.equals("UNIDENTIFIED")) {
				if (probableCauseQualifier.equals("402"))
					return "LINK_INTEGRITY_ON";
				if (probableCauseQualifier.equals("305"))
					return "ETHERNET_LINK_DOWN";
			}
			if (probableCause.equals("AU-AIS"))
				return "AU_AIS";
			if (probableCause.equals("EQPT") && additionalText.equals("Fan Failed"))
				return "FAN_FAULT";
			if (probableCause.equals("EMS") && additionalText.equals("Node Not Reachable"))
				return "NE_UNREACHABLE";
		}
		return "";
	}

	private static String getFullName(Properties eventProps) {
		String fullName = "";
		LOG.debug("Object Type = {}, ManagedElement = {}, EMS = {}", eventProps.getProperty("objectType"),
				eventProps.getProperty("ManagedElement"), eventProps.getProperty("EMS"), eventProps.get(fullName));
		if (eventProps.getProperty("objectType") != null) {
			if (eventProps.getProperty("objectType").equals("OT_CONNECTION_TERMINATION_POINT")) {
				fullName = eventProps.getProperty("CTP") + "_" + formatPTP(eventProps.getProperty("PTP")) + "_"
						+ eventProps.getProperty("ManagedElement") + "_" + eventProps.getProperty("EMS");
				LOG.debug("Full Name is {}", fullName);
				return fullName;
			}
			if (eventProps.getProperty("objectType").equals("OT_PHYSICAL_TERMINATION_POINT")) {
				fullName = (eventProps.getProperty("PTP") != null ? formatPTP(eventProps.getProperty("PTP")) + "_" : "")
						+ eventProps.getProperty("ManagedElement") + "_" + eventProps.getProperty("EMS");
				LOG.debug("Full Name is {}", fullName);
				return fullName;
			}
			if (eventProps.getProperty("objectType").equals("OT_MANAGED_ELEMENT")) {
				fullName = eventProps.getProperty("ManagedElement") + "_" + eventProps.getProperty("EMS");
				LOG.debug("Full Name is {}", fullName);
				return fullName;
			}
			if (eventProps.getProperty("objectType").equals("OT_EQUIPMENT")) {
				fullName = eventProps.getProperty("Equipment") + "_" + eventProps.getProperty("EquipmentHolder") + "_"
						+ eventProps.getProperty("ManagedElement") + "_" + eventProps.getProperty("EMS");
				LOG.debug("Full Name is {}", fullName);
				return fullName;
			}
		}
		return "";
	}

	private static String formatPTP(String ptpName) {
		if (ptpName != null) { // Duplicate check. Remove later
			Pattern pattern = Pattern.compile("(.*?)\\/rate=");
			Matcher matcher = pattern.matcher(ptpName);
			if (matcher.find()) {
				return matcher.group(1);
			}
		}
		return ptpName;
	}
}
