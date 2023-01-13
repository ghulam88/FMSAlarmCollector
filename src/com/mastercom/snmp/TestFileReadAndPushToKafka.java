package com.mastercom.snmp;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.text.SimpleDateFormat;
import java.util.Properties;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFFormulaEvaluator;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

public class TestFileReadAndPushToKafka {

	private static Producer<String, String> producer = null;


	static int trapCounter = 0;
	static BufferedWriter writer = null;


	public static void main(String[] args) {

		try {

			String args1 = args[0];

			// writer = new BufferedWriter(new FileWriter("D:\\traps.txt"));
			//writer = new BufferedWriter(new FileWriter("/home/alarm-collector/traps.txt"));
			
			initKafka();
			System.out.println("File PATH: " + args1);
			// String filePath = "D:\\SampleAlarmData.xlsx";
			// String filePath = "/home/SampleAlarmData.xlsx";
			String filePath = args1;
			JSONArray data = readExcelFileAsJsonObject_RowWise(filePath);
			System.out.println(data.toJSONString());
			trapCounter++;
			PushMessageToKafka("sampletraps", trapCounter + "", data.toJSONString());
			System.out.println("Message witten to Kafka topic: sampletraps ");
			
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	public static void initKafka() {

		Properties props = new Properties();

		props.put("bootstrap.servers", "localhost:9092,localhost:9093");
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
		ProducerRecord<String, String> record = new ProducerRecord<>(topicName, key, value);
		producer.send(record);
	}




	@SuppressWarnings("unchecked")
	private static JSONArray readExcelFileAsJsonObject_RowWise(String filePath) {
		DataFormatter dataFormatter = new DataFormatter();
		JSONObject workbookJson = new JSONObject();
		JSONArray sheetJson = new JSONArray();
		JSONObject rowJson = new JSONObject();
		try {

			String oldFormat = "dd/MM/yy HH:mm";
			String newFormat = "yyyy-MM-dd HH:mm:ss";

			SimpleDateFormat sdf1 = new SimpleDateFormat(oldFormat);
			SimpleDateFormat sdf2 = new SimpleDateFormat(newFormat);

			FileInputStream excelFile = new FileInputStream(new File(filePath));
			Workbook workbook = new XSSFWorkbook(excelFile);
			FormulaEvaluator formulaEvaluator = new XSSFFormulaEvaluator((XSSFWorkbook) workbook);

			Sheet sheet = workbook.getSheetAt(0);

			sheetJson = new JSONArray();
			int lastRowNum = sheet.getLastRowNum();
			int lastColumnNum = sheet.getRow(0).getLastCellNum();
			Row firstRowAsKeys = sheet.getRow(0); // first row as a json keys

			for (int i = 1; i <= lastRowNum; i++) {
				rowJson = new JSONObject();
				Row row = sheet.getRow(i);
				String dateValue1 = dataFormatter.formatCellValue(row.getCell(0), formulaEvaluator);
				if(dateValue1.isEmpty()) {
					continue;
				}
				
				
				if (row != null) {
					for (int j = 0; j < lastColumnNum; j++) {
						formulaEvaluator.evaluate(row.getCell(j));
						
						if(j==1 || j == 2) {
							String dateValue = dataFormatter.formatCellValue(row.getCell(j), formulaEvaluator);
							if(dateValue.isEmpty()) continue;
							if(firstRowAsKeys.getCell(j).getStringCellValue().length()==0) continue;
							rowJson.put(firstRowAsKeys.getCell(j).getStringCellValue(),sdf2.format(sdf1.parse(dateValue))
									);
						}else {
							if(firstRowAsKeys.getCell(j).getStringCellValue().length()==0) continue;
							rowJson.put(firstRowAsKeys.getCell(j).getStringCellValue(),
									dataFormatter.formatCellValue(row.getCell(j), formulaEvaluator));
						}
						
						
					}
					sheetJson.add(rowJson);
				}
			}
			// workbookJson.put(sheet.getSheetName(), sheetJson);

		} catch (Exception e) {
			e.printStackTrace();
		}
		return sheetJson;
	}

}