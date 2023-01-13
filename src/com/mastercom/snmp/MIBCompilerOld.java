package com.mastercom.snmp;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.DailyRollingFileAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

public class MIBCompilerOld {
	private static final Logger logger = Logger.getLogger(MIBCompilerOld.class.getName());
	static ArrayList<String> importsList;
	static ArrayList<String> currentFileImportsList;
	static ArrayList<String> allImportsMIBs;
	static ArrayList<String> allImportsMIBsNew;
	static ArrayList<String> oidNames;
	static String lastParent = "";
	static HashMap<String, String> objectParentMapping;
	static HashMap<String, String> parentOIDsList;
	static HashMap<String, ObjectAttributes> parentOIDsListWithAttributes;
	static HashMap<String, String> objectParentMappingMissing;
	static HashMap<String, String> parentOIDsListMissing;
	static String newOidVal = "";
	static String oid = "";
	static HashMap<String, String> importedMibs;
	static int exceptCount = 0;
	static String exceptMsg = "";
	static String currentRFCName = "";
	static String missingParentName = "";
	static String missingPArentOIDs = "";
	static boolean findMissingParent = false;
	static boolean allImportsCompiled = false;
	static ArrayList<String> missingParents;
	static boolean readingCurrentMIB = false;
	static String spaceCount = " ";

	static int counters = 0;

	public static void main(String args[]) {

		try {
			initLog4J();
			missingParents = new ArrayList<>();
			importsList = new ArrayList<>();
			currentFileImportsList = new ArrayList<>();
			importedMibs = new HashMap<>();
			objectParentMapping = new HashMap<>();
			parentOIDsList = new HashMap<>();
			parentOIDsListWithAttributes = new HashMap<>();
			allImportsMIBs = new ArrayList<>();
			allImportsMIBsNew = new ArrayList<>();

			// OID namaes;
			oidNames = new ArrayList<>();

			// Load current MIB FILE. Then load all imports of the file.
			BufferedReader br = new BufferedReader(
					new FileReader("D:\\MCTS\\mibs\\mibs\\MPLS-VPN-MIB.mib"));

			String tmpVal = br.readLine();
			// Loop through the MIB file to get dependency Import MIBs.
			try {

				StringBuilder sb = new StringBuilder();
				String line = br.readLine();

				while (line != null) {
					sb.append(line);
					sb.append(System.lineSeparator());
					line = br.readLine();
				}

				String everything = sb.toString();
				String[] arr = everything.split("\n\r");

				// Read dependency MIBs in current file.
				for (int i = 0; i < arr.length; i++) {
					readImportsFromCurrentMIB(arr[i]);
					readImportsFromCurrentMIBNew(arr[i]);
				}

				// Import all dependency MIBs of the current MIB file.
				importDependencyMIBs(importsList);

			} finally {
				br.close();
			}

			System.out.println(
					"TOTAL MIBS IMPORTS NEW=" + allImportsMIBsNew.size() + "MIBS=" + allImportsMIBsNew.toString());

			// Load all dependency MIBs.

			ArrayList<String> compiledMibs = readCompiledMibs();
			ArrayList<String> diffMibs;

			if (compiledMibs.size() > 0) {
				diffMibs = checkDifferenceInCompiledMibs(compiledMibs, allImportsMIBsNew);

				if (diffMibs.size() == 0) {
					allImportsCompiled = true;
				}
			} else {
				diffMibs = allImportsMIBsNew;
			}


			loadCompiledOidInMemory();
			
			System.out.println("parent list size=" + parentOIDsList.size());
			System.out.println("object parent mapping list size=" + parentOIDsList.size());

			if (diffMibs.size() > 0) {
				loadImportMIBS(diffMibs);
				writeImportedMibs(diffMibs);
			}

			getParents();
			// loadImportMIBS(allImportsMIBsNew);
			System.out.println("Total exceptions=" + exceptCount);
			System.out.println("Total exceptions msgs=" + exceptMsg);

			System.out.println(objectParentMapping.toString());
			// getParentList("cpmThreadStackSize");
			// getChildrenList("ciscoProcessMIBObjects");

			// for (int j = 0; j < oidNames.size(); j++) {
			//
			// String objName = oidNames.get(j);
			//
			// if (parentOIDsListWithAttributes.containsKey(objName)) {
			//
			// ObjectAttributes atr = parentOIDsListWithAttributes.get(objName);
			// //
			// System.out.println("=================================================");
			// // System.out.println("NAME = " + objName);
			// // System.out.println("TYPE = " + atr.getType());
			// // System.out.println("OID = " + atr.getOid());
			// // System.out.println("STATUS = " + atr.getStatus());
			// // System.out.println("MAX-ACCESS = " + atr.getMaxAccess());
			// // System.out.println("SYNTAX = " + atr.getSyntax());
			// // System.out.println("DESC = " + atr.getDescription());
			//
			// }
			// }
			//

		} catch (Exception ex) {

			exceptCount++;
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			ex.printStackTrace(pw);

			String exc = sw.toString();

			exceptMsg = exceptMsg + "\n\n\n" + exc;
			ex.printStackTrace();

		}

	}

	private static void initLog4J() {

		PatternLayout layout = new PatternLayout();
		String conversionPattern = "[%p] %d %c %M - %m%n";
		layout.setConversionPattern(conversionPattern);

		// creates daily rolling file appender
		DailyRollingFileAppender rollingAppender = new DailyRollingFileAppender();
		rollingAppender.setFile("D:\\mibcompiler.log"); // Daily
																	// trap
																	// log
																	// filename.
		rollingAppender.setDatePattern("'.'yyyy-MM-dd"); // Pattern of daily log
															// file.
		rollingAppender.setLayout(layout);
		rollingAppender.activateOptions();

		// configures the root logger
		Logger rootLogger = Logger.getRootLogger();
		rootLogger.setLevel(Level.DEBUG);
		rootLogger.addAppender(rollingAppender);

	}
	
	/**
	 * Load All imported MIBs. All imported MIBS.
	 */
	private static void loadImportMIBS(ArrayList<String> importMIBs) {

		for (int i = 0; i < importMIBs.size(); i++) {
			String mibName = importMIBs.get(i);
			readRFCFromDirs(mibName);
		}

		readingCurrentMIB = true;
		// Once imported MIBs loading has finished then load current MIB file.
		readRFCFromDirs("ADSL-LINE-MIB-V1SMI.my");

	}

	private static void loadCompiledOidInMemory() {

		String query = "SELECT parent_name, name, oid FROM oid_relationship r JOIN oid_info o ON r.child_id = o.id";
		Connection con = ConnectionManager.getConnection();
		Statement st;
		try {
			st = con.createStatement();

			ResultSet rs = st.executeQuery(query);
			if (rs != null) {

				while (rs.next()) {

					String objName = rs.getString("name");
					String parentName = rs.getString("parent_name");
					String oid = rs.getString("oid");

					parentOIDsList.put(objName, oid);
					objectParentMapping.put(objName, parentName);

				}

			} else {
				System.out.println("Resultset is null");
			}
          con.close();
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private static ArrayList<String> readCompiledMibs() {

		Connection con = ConnectionManager.getConnection();
		ArrayList<String> precompiledMib = new ArrayList<>();

		try {

			Statement st = con.createStatement();
			String query = "SELECT * FROM compiled_mib";

			ResultSet rs = st.executeQuery(query);

			if (rs != null) {
				while (rs.next()) {

					String name = rs.getString("mib_name");
					System.out.println("compiled mib=" + name);
					precompiledMib.add(name);

				}
			} else {
				System.out.println("Resultset is null");
			}
			con.close();
		} catch (SQLException e) {

			e.printStackTrace();
		}

		return precompiledMib;

	}

	private static void writeImportedMibs(ArrayList<String> importsList) {
		
		if (allImportsCompiled) {
			return;
		}
		
		Connection con = ConnectionManager.getConnection();

		try {

			Statement st = con.createStatement();

			for (int i = 0; i < importsList.size(); i++) {
				String mibName = importsList.get(i);
				String query = "INSERT INTO compiled_mib(mib_name) VALUES('" + mibName + "')";

				st.executeUpdate(query);

			}
			con.close();
		} catch (SQLException e) {

			e.printStackTrace();
		}

	}

	private static void writeOidInfo(String name, String oid, String type) {

		if (allImportsCompiled) {
			return;
		}
		Connection con = ConnectionManager.getConnection();

		try {

			Statement st = con.createStatement();

			String query = "INSERT INTO oid_info(name, oid, type) VALUES('" + name + "', '" + oid + "', '" + type
					+ "')";

			st.executeUpdate(query);
			con.close();
		} catch (SQLException e) {

			e.printStackTrace();
		}

	}

	private static String getOIDName(String OIDval){
		
		String oidName = "";
		Connection con = ConnectionManager.getConnection();
		try {
			Statement st = con.createStatement();
			String query = "SELECT * FROM oid_info WHERE oid = '"+OIDval+"'";
			
			ResultSet rs = st.executeQuery(query);
			
			if(rs.next()){
				
				oidName = rs.getString("name");
				
			}
			
			System.out.println("OID name="+oidName);
			con.close();
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		
		
		return oidName;
		
	}
	
	
	private static void getParents() {

		if (allImportsCompiled) {
			return;
		}

		Connection con = ConnectionManager.getConnection();

		try {

			Statement st = con.createStatement();
			String query = "SELECT id, name FROM oid_info";

			ResultSet rs = st.executeQuery(query);

			if (rs != null) {
				while (rs.next()) {

					int id = rs.getInt("id");
					String name = rs.getString("name");

					if (objectParentMapping.containsKey(name)) {

						String parentName = objectParentMapping.get(name);

						int parentId = getParentId(parentName);
						setRelationship(con, parentId, parentName, id);
						// System.out.println("obj name=" + name + " parent
						// name=" + parentName + " obj id=" + id
						// + " par id=" + parentId);

					}
				}
			} else {
				System.out.println("Resultset is null");
			}
			//con.close();
		} catch (SQLException e) {

			e.printStackTrace();
		}

	}

	private static void setRelationship(Connection con, int parentId, String parentName, int childId) {

		Statement st;
		try {
			st = con.createStatement();

			st.executeUpdate("INSERT INTO oid_relationship(parent_id, parent_name, child_id) VALUES(" + parentId + ", '"
					+ parentName + "', " + childId + ")");
			//con.close();
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private static int getParentId(String parentNameArr) {

		String parentName = parentNameArr.split("_")[0];

		Connection con = ConnectionManager.getConnection();
		int parentId = -1;

		try {

			Statement st = con.createStatement();
			String query = "SELECT id FROM oid_info WHERE name='" + parentName + "'";

			ResultSet rs = st.executeQuery(query);

			if (rs != null) {
				while (rs.next()) {

					parentId = rs.getInt("id");

				}

			}
			con.close();
		} catch (SQLException e) {

			e.printStackTrace();
		}

		return parentId;
	}

	private static ArrayList<String> checkDifferenceInCompiledMibs(ArrayList<String> precompiledMibsList,
			ArrayList<String> currentImportList) {

		System.out.println("Compiled size=" + precompiledMibsList.size());
		System.out.println("Current Imp size=" + currentImportList.size());

		ArrayList<String> diffList = new ArrayList<>();

		if (precompiledMibsList.size() > currentImportList.size()) {

			for (int i = 0; i < currentImportList.size(); i++) {

				if (!precompiledMibsList.contains(currentImportList.get(i))) {
					diffList.add(currentImportList.get(i));
				}
			}

		} else {

			for (int i = 0; i < currentImportList.size(); i++) {

				if (i < precompiledMibsList.size()) {
					if (!currentImportList.contains(precompiledMibsList.get(i))) {
						diffList.add(currentImportList.get(i));
					}
				} else {
					diffList.add(currentImportList.get(i));
				}

			}

		}

		if (diffList.size() == 0) {
			System.out.println("No difference");
		} else {
			System.out.println(" difference imports=" + diffList.toString());
		}

		return diffList;

	}

	/**
	 * Write Imported MIBs OIDs to a JSON file. This will be read by the program
	 * to get already imported MIBs.
	 */
	private static void writeOIDsToJSON() {

		ObjectMapper mapper = new ObjectMapper();

		try {
			mapper.writeValue(new File("D:\\MCTS\\oidmappings.json"), parentOIDsList);
		} catch (JsonGenerationException e) {
			e.printStackTrace();
		} catch (JsonMappingException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	/**
	 * Reading import line from current MIB. It reads all the dependency MIB. It
	 * checks line by line and find the import line with dependency MIB.
	 * 
	 * @param line
	 */

	private static void readImportsFromCurrentMIB(String line) {

		Scanner scanner = new Scanner(line);
		// System.out.println("===============================================");

		while (scanner.hasNextLine()) {

			String linevl = scanner.nextLine();

			if (linevl.trim().startsWith("--")) {
				continue;
			}

			if (linevl.contains("FROM")) {

				String onlyFrom = linevl.trim();
				String importName = "";
				if (onlyFrom.equalsIgnoreCase("FROM")) {

					importName = scanner.nextLine();

				} else {

					String importsRFC[] = linevl.split("FROM");

					importName = importsRFC[1].trim();
				}

				if (importName.contains(";")) {
					importName = importName.substring(0, importName.length() - 1);
				}

				if (importName.contains("--")) {
					importName = importName.substring(0, importName.indexOf("--"));
				}

				importName = importName.replaceAll(";", "");
				importsList.add(importName.trim());

			}
		}
	}

	/**
	 * Reading import line from current MIB. It reads all the dependency MIB. It
	 * checks line by line and find the import line with dependency MIB.
	 * 
	 * @param line
	 */
	private static void readImportsFromCurrentMIBNew(String line) {

		Scanner scanner = new Scanner(line);
		// System.out.println("===============================================");

		while (scanner.hasNextLine()) {

			String linevl = scanner.nextLine().trim();

			// If line is a comment line continue.
			if (linevl.trim().startsWith("--")) {
				continue;
			}

			// Replace all tabs with white spaces.
			if (linevl.contains("\t")) {
				linevl = linevl.replaceAll("\t", " ");
			}

			if (linevl.contains("FROM")) {

				String onlyFrom = linevl.trim();
				String importName = "";

				// check if line contains only FROM. then find dependency MIB
				// name in next line. Otherwise split it based on space.

				if (onlyFrom.equalsIgnoreCase("FROM")) {

					importName = scanner.nextLine();

				} else {
					String importsRFC[] = linevl.split("FROM");

					importName = importsRFC[1].trim();
				}
				if (importName.contains(";")) {
					importName = importName.substring(0, importName.length() - 1);
				}

				currentFileImportsList.add(importName);

			}
		}
	}

	private static void readMIBMacros(String line) {

		Scanner scanner = new Scanner(line);
		String prevLine = "";

		while (scanner.hasNextLine()) {

			String nextLine = scanner.nextLine().trim();
			String objName = "";

			// If line is a comment line continue.
			if (nextLine.trim().startsWith("--")) {
				continue;
			}

			// Replace all tabs with white spaces.
			if (nextLine.contains("\t")) {
				nextLine = nextLine.replaceAll("\t", " ");
			}

			if (nextLine.endsWith("OBJECT-TYPE".trim())) {
				if (nextLine.trim().split(" ").length > 1) {
					readOBJ_TYPE(nextLine, scanner);
				}
			} else if (nextLine.endsWith("OBJECT-IDENTITY".trim())) {
				readOBJ_IDENTITY(nextLine, scanner);
			} else if (nextLine.endsWith("NOTIFICATION-TYPE".trim())) {
				readNOTIF_TYPE(nextLine, scanner);
			} else if (nextLine.endsWith("TRAP-TYPE".trim())) {
				readTRAP_TYPE(nextLine, scanner);
			} else if (nextLine.endsWith("MODULE-IDENTITY".trim())) {
				readMOD_IDENTITY(nextLine, scanner);
			} else if (nextLine.contains("OBJECT IDENTIFIER".trim()) && nextLine.contains("::=")
					&& nextLine.contains("}")) {

				String parts[] = nextLine.split("\\}");
				String origArr[] = parts[0].split("\\{");
				objName = origArr[0].split("OBJECT IDENTIFIER")[0].trim();
				String lastLine = origArr[1];

				String[] arrOnlyZeros = lastLine.trim().split(" ");

				if (arrOnlyZeros.length == 2 && arrOnlyZeros[0].equalsIgnoreCase("0")
						&& arrOnlyZeros[1].equalsIgnoreCase("0")) {
				} else {
					readOBJ_IDENTIFIER(nextLine, prevLine, scanner);
				}

			} else if (nextLine.contains("OBJECT IDENTIFIER".trim()) && !nextLine.contains("\"")) {
				// System.out.println("next line 0"+nextLine);
				String linevl = scanner.nextLine();
				String objNm = nextLine.trim().split(" ")[0];
				if (linevl.contains("{") && linevl.contains("}")) {
					readOBJ_IDENTIFIERNextLine(objNm, linevl, scanner);
				}
			}
			prevLine = nextLine;
		}

	}

	private static void readOBJ_TYPE(String objLine, Scanner scanner) {

		String parentName = "";
		String OidVal = "";
		String objName = "";
		String arr1[] = objLine.trim().split(" ");
		objName = arr1[0];
		String statusLine = "";
		String maxAccess = "";
		String syntaxLine = "";
		boolean OIDPresent = false;

		// Get all the desription.
		String description = "";
		String firstLineDesc = "";
		String prevLine = "";

		while (scanner.hasNextLine()) {

			String linevl = scanner.nextLine().trim();

			if (linevl.trim().startsWith("--")) {
				continue;
			}

			if (linevl.contains("\t")) {
				linevl = linevl.replaceAll("\t", " ");
			}

			boolean bracketOIDs = false;

			if (linevl.trim().startsWith("STATUS")) {
				statusLine = linevl.trim().split("STATUS")[1].trim();
			}

			if (linevl.trim().startsWith("MAX-ACCESS")) {
				maxAccess = linevl.trim().split("MAX-ACCESS")[1].trim();
			}

			if (linevl.trim().startsWith("SYNTAX")) {
				syntaxLine = linevl.trim().split("SYNTAX")[1].trim();
			}

			// Loop through lines until ending double qoutes is found for
			// reading description.
			if (prevLine.trim().endsWith("DESCRIPTION")) {

				if (linevl.matches("\".*\"")) {

					description = linevl;

				} else {
					firstLineDesc = linevl;
					String line = "";
					String nextLines = "";
					do {

						if (scanner.hasNextLine()) {
							line = scanner.nextLine();
							nextLines = nextLines + "\n" + line;
							linevl = line;
						}
					} while (!line.contains("\""));
					description = firstLineDesc + nextLines;
				}

			}

			// Reading for Object Identifier macro.
			if (linevl.contains("::".trim()) && linevl.contains("}") && !linevl.contains("OBJECT IDENTIFIER")
					&& !linevl.contains("BEGIN".trim()) && !linevl.contains("MACRO".trim())) {

				if (linevl.contains("--")) {
					linevl = linevl.substring(0, linevl.indexOf("--"));
				}

				String parts[] = linevl.split("\\}");
				String origArr[] = parts[0].split("\\{");
				String lastLine = origArr[1].trim();

				String arrObj[] = lastLine.split(" ");

				parentName = arrObj[0];

				String bracketsLine = "";
				if (linevl.contains("{") && linevl.contains("(")) {

					if (!linevl.contains("}")) {

						do {

							String scnLine = scanner.nextLine();
							bracketsLine += scnLine;

						} while (!bracketsLine.contains("}"));
						bracketsLine = linevl + " " + bracketsLine;

					} else {
						bracketsLine = linevl;
					}

					String oid = "1";
					Matcher m = Pattern.compile("\\((.*?)\\)").matcher(bracketsLine);

					while (m.find()) {
						oid = oid + "." + m.group(1);
					}

					bracketsLine = bracketsLine.replace("{", "");
					bracketsLine = bracketsLine.replace("}", "");
					String arr[] = bracketsLine.trim().split(" ");
					int len = arr.length;
					String lastVal = arr[len - 1];

					if (bracketsLine.contains("iso(")) {
						oid = oid.substring(2, oid.length() - 1);
					}

					oid = oid + "." + lastVal;

					OidVal = oid;
					bracketOIDs = true;

				} else {
					String v1 = "";

					if (arrObj.length == 4) {

						if (!arrObj[1].isEmpty()) {
							v1 = arrObj[1];
						}
						if (!arrObj[2].isEmpty()) {
							v1 = arrObj[2] + "." + v1;
						}
						OidVal = arrObj[3];
					} else if (arrObj.length == 3) {
						if (!arrObj[1].isEmpty()) {
							v1 = arrObj[1];
						}
						OidVal = arrObj[2];
					} else if (arrObj.length == 2) {
						OidVal = arrObj[1];
					}

					OidVal = OidVal + "." + v1;

				}

				if (!parentName.isEmpty()) {
					if (objName.trim().contains("--")) {
						return;
					}
					if (!objectParentMapping.containsKey(objName) && !objName.isEmpty()) {
						if (bracketOIDs) {
							OidVal = reverseOID(OidVal);
						}

						objectParentMapping.put(objName, parentName + "_" + OidVal);
						oid = "";

						String reversedOID = "";
						String parentOIDs = getParentOIDs(objName);

						if (!parentOIDs.isEmpty()) {
							String newOID = "";
							if (parentOIDs.contains("missing")) {
								String[] oidArr = parentOIDs.split("_");
								parentOIDs = oidArr[0];
								newOID = parentOIDs + "." + OidVal;
							} else {
								reversedOID = reverseOID(parentOIDs);
								newOID = "1" + reversedOID;
							}

							parentOIDsList.put(objName, newOID);
							writeOIDsToJSON();
							writeOidInfo(objName, newOID, "OBJECT-TYPE");
							// if (readingCurrentMIB) {
							ObjectAttributes attrib = new ObjectAttributes();
							// System.out.println("" + objName + " =" + newOID);
							attrib.setDescription(description);
							attrib.setOid(newOID);
							attrib.setStatus(statusLine);
							attrib.setMaxAccess(maxAccess);
							attrib.setType("OBJECT-TYPE");
							attrib.setSyntax(syntaxLine.trim());

							if (!oidNames.contains(objName)) {
								oidNames.add(objName);
								parentOIDsListWithAttributes.put(objName, attrib);
							}
							// }

						}
						OIDPresent = true;
					}
				}

			}

			if (linevl.endsWith("OBJECT-IDENTITY".trim())) {
				readOBJ_IDENTITY(linevl, scanner); // Reading for Object
													// Identity macro.
			} else if (linevl.endsWith("NOTIFICATION-TYPE".trim())) {
				readNOTIF_TYPE(linevl, scanner); // Reading Notification type
													// macro.
			} else if (linevl.endsWith("TRAP-TYPE".trim())) {
				readTRAP_TYPE(linevl, scanner);
			} else if (linevl.endsWith("MODULE-IDENTITY".trim())) {
				readMOD_IDENTITY(linevl, scanner); // Reading MODULE-IDENTITY
													// macro.
			} else if (linevl.endsWith("OBJECT-TYPE".trim())) {
				if (linevl.trim().split(" ").length > 1) {
					readOBJ_TYPE(linevl, scanner); // Reading OBJECT-TYPE macro.
				}
			} else if (linevl.contains("OBJECT IDENTIFIER".trim()) && linevl.contains("::=") && linevl.contains("}")) {
				// Check if OBJECT IDENTIFIER MACRO does not contains text like
				// { 0 0 }.
				String parts[] = linevl.split("\\}");
				String origArr[] = parts[0].split("\\{");
				objName = origArr[0].split("OBJECT IDENTIFIER")[0].trim();
				String lastLine = origArr[1];

				String[] arrOnlyZeros = lastLine.trim().split(" ");

				if (arrOnlyZeros.length == 2 && arrOnlyZeros[0].equalsIgnoreCase("0")
						&& arrOnlyZeros[1].equalsIgnoreCase("0")) {
					readOBJ_TYPE(objLine, scanner);
				} else {
					readOBJ_IDENTIFIER(linevl, prevLine, scanner);
				}

			} else if (linevl.contains("OBJECT IDENTIFIER".trim()) && !linevl.contains("\"")) {
				// If OBJECT IDENTIFIER value assignment is in next line.
				String nextLine = scanner.nextLine();
				String objNm = linevl.trim().split(" ")[0];
				if (nextLine.contains("{") && nextLine.contains("}")) {
					readOBJ_IDENTIFIERNextLine(objNm, nextLine, scanner);
				}
			}

			prevLine = linevl;
		}
	}

	private static void readMOD_IDENTITY(String objLine, Scanner scanner) {

		String parentName = "";
		String OidVal = "";
		String objName = "";
		String prevLine = "";
		boolean bracketOIDs = false;
		objName = objLine.trim().split(" ")[0];

		while (scanner.hasNextLine()) {

			String linevl = scanner.nextLine().trim();

			if (linevl.trim().startsWith("--")) {
				continue;
			}

			if (linevl.contains("\t")) {
				linevl = linevl.replaceAll("\t", " ");
			}

			String bracketsLine = "";
			if (linevl.contains("::".trim()) && linevl.contains("{") && !linevl.contains("MACRO".trim())
					&& !linevl.contains("TYPE NOTATION".trim())) {

				if (linevl.contains("--")) {
					linevl = linevl.substring(0, linevl.indexOf("--"));
				}

				String parts[] = linevl.split("\\}");
				String origArr[] = parts[0].split("\\{");
				String lastLine = origArr[1];
				String arrObj[] = lastLine.trim().split(" ");

				parentName = arrObj[0];

				if (linevl.contains("{") && linevl.contains("(")) {

					if (!linevl.contains("}")) {
						do {

							String scnLine = scanner.nextLine();
							bracketsLine += scnLine;

						} while (!bracketsLine.contains("}"));
						bracketsLine = linevl + " " + bracketsLine;
					} else {
						bracketsLine = linevl;
					}

					String oid = "1";
					Matcher m = Pattern.compile("\\((.*?)\\)").matcher(bracketsLine);

					while (m.find()) {
						oid = oid + "." + m.group(1);
					}

					bracketsLine = bracketsLine.replace("{", "");
					bracketsLine = bracketsLine.replace("}", "");
					String arr[] = bracketsLine.trim().split(" ");

					int len = arr.length;
					String lastVal = arr[len - 1];

					if (bracketsLine.contains("iso(")) {
						oid = oid.substring(2, oid.length());
					}

					oid = oid + "." + lastVal;

					OidVal = oid;
					bracketOIDs = true;

				} else {
					String v1 = "";

					if (arrObj.length == 4) {

						if (!arrObj[1].isEmpty()) {
							v1 = arrObj[1];
						}
						if (!arrObj[2].isEmpty()) {
							v1 = arrObj[2] + "." + v1;
						}
						OidVal = arrObj[3];
					} else if (arrObj.length == 3) {
						if (!arrObj[1].isEmpty()) {
							v1 = arrObj[1];
						}
						OidVal = arrObj[2];
					} else if (arrObj.length == 2) {
						OidVal = arrObj[1];
					}

					OidVal = OidVal + "." + v1;

				}

				if (!parentName.isEmpty()) {

					if (objName.trim().contains("--")) {
						return;
					}

					if (!objectParentMapping.containsKey(objName) && !objName.isEmpty()) {
						if (bracketOIDs) {
							OidVal = reverseOID(OidVal);
						}

						objectParentMapping.put(objName, parentName + "_" + OidVal);

						oid = "";
						String reversedOID = "";
						String parentOIDs = getParentOIDs(objName);

						if (!parentOIDs.isEmpty()) {

							String newOID = "";
							if (parentOIDs.contains("missing")) {
								String[] oidArr = parentOIDs.split("_");
								parentOIDs = oidArr[0];
								newOID = parentOIDs + "." + OidVal;
							} else {
								reversedOID = reverseOID(parentOIDs);
								newOID = "1" + reversedOID;
							}

							parentOIDsList.put(objName, newOID);
							writeOIDsToJSON();
							writeOidInfo(objName, newOID, "MODULE-IDENTITY");
							// if (readingCurrentMIB) {
							ObjectAttributes attrib = new ObjectAttributes();
							// System.out.println("" + objName + " =" + newOID);
							attrib.setDescription("");
							attrib.setOid(newOID);
							attrib.setStatus("");
							attrib.setMaxAccess("");
							attrib.setSyntax("");
							attrib.setType("MODULE-IDENTITY");

							if (!oidNames.contains(objName)) {
								oidNames.add(objName);
								parentOIDsListWithAttributes.put(objName, attrib);
							}
							// }

						}
					}
				}
			}

			if (linevl.endsWith("OBJECT-IDENTITY".trim())) {
				readOBJ_IDENTITY(linevl, scanner);
			} else if (linevl.endsWith("NOTIFICATION-TYPE".trim())) {
				readNOTIF_TYPE(linevl, scanner);
			} else if (linevl.endsWith("TRAP-TYPE".trim())) {
				readTRAP_TYPE(linevl, scanner);
			} else if (linevl.contains("OBJECT IDENTIFIER".trim()) && linevl.contains("::=") && linevl.contains("}")) {
				String parts[] = linevl.split("\\}");
				String origArr[] = parts[0].split("\\{");
				objName = origArr[0].split("OBJECT IDENTIFIER")[0].trim();
				String lastLine = origArr[1];
				String[] arrOnlyZeros = lastLine.trim().split(" ");

				if (arrOnlyZeros.length == 2 && arrOnlyZeros[0].equalsIgnoreCase("0")
						&& arrOnlyZeros[1].equalsIgnoreCase("0")) {
					readMOD_IDENTITY(objLine, scanner);
				} else {
					readOBJ_IDENTIFIER(linevl, prevLine, scanner);
				}

			} else if (linevl.endsWith("MODULE-IDENTITY".trim())) {
				readMOD_IDENTITY(linevl, scanner);
			} else if (linevl.endsWith("OBJECT-TYPE".trim())) {
				if (linevl.trim().split(" ").length > 1) {
					readOBJ_TYPE(linevl, scanner);
				}
			} else if (linevl.contains("OBJECT IDENTIFIER".trim()) && !linevl.contains("\"")) {
				String nextLine = scanner.nextLine();
				String objNm = linevl.trim().split(" ")[0];
				if (nextLine.contains("{") && nextLine.contains("}")) {
					readOBJ_IDENTIFIERNextLine(objNm, nextLine, scanner);
				}
			}

			prevLine = linevl;
		}

	}

	private static void readTRAP_TYPE(String objLine, Scanner scanner) {

		String parentName = "";
		String OidVal = "";
		String objName = "";
		String prevLine = "";
		String description = "";
		String firstLineDesc = "";
		boolean bracketOIDs = false;
		objName = objLine.trim().split(" ")[0];
		String statusLine = "";
		String enterpriseLine = "";
//		System.out.println("READIN obj=" + objName);

		while (scanner.hasNextLine()) {

			String linevl = scanner.nextLine().trim();

			if (linevl.trim().startsWith("--")) {
				continue;
			}
			if (objName.equals("TRAP-TYPE")) {
				return;
			}
			if (linevl.contains("\t")) {
				linevl = linevl.replaceAll("\t", " ");
			}

			if(linevl.trim().startsWith("VARIABLES") && linevl.contains("{")){
				String line = "";
				String objects= "";
				do {

					if (scanner.hasNextLine()) {
						line = scanner.nextLine();
						objects = objects +  line+"\n";
						linevl = line;
					}

				} while (!line.contains("}"));
				
				System.out.println("Variables list="+objects.replaceAll("}", "").trim());
				
				if(objects.contains(",")){
					System.out.println("has comma");
				}
				
			}
			
			if (linevl.trim().startsWith("ENTERPRISE")) {
				enterpriseLine = linevl.split("ENTERPRISE")[1];
			}

			if (prevLine.trim().endsWith("DESCRIPTION")) {

				if (linevl.matches("\".*\"")) {
					description = linevl;
				} else {

					firstLineDesc = linevl;
					String line = "";
					String nextLines = "";

					do {

						if (scanner.hasNextLine()) {
							line = scanner.nextLine();
							nextLines = nextLines + "\n" + line;
							linevl = line;
						}

					} while (!line.contains("\""));

					description = firstLineDesc + nextLines;
				}

			}

			if (linevl.contains("::=".trim()) && !linevl.contains("MACRO".trim())
					&& !linevl.contains("TYPE NOTATION".trim())) {

				if (linevl.contains("--")) {
					linevl = linevl.substring(0, linevl.indexOf("--"));
				}

				String arrObj[] = linevl.trim().split("::=");

				parentName = enterpriseLine;

				OidVal = arrObj[1].trim();
//				System.out.println("PARENT=" + parentName);
				if (!parentName.isEmpty()) {
					if (objName.trim().contains("--")) {
						return;
					}
					if (!objectParentMapping.containsKey(objName) && !objName.isEmpty()) {
						if (bracketOIDs) {
							OidVal = reverseOID(OidVal);
						}

						objectParentMapping.put(objName, parentName + "_" + OidVal);

						oid = "";
						String reversedOID = "";
						String parentOIDs = getTrapTypeParentOIDs(parentName);

						if (!parentOIDs.isEmpty()) {
							String newOID = "";
							if (parentOIDs.contains("missing")) {
								String[] oidArr = parentOIDs.split("_");
								parentOIDs = oidArr[0];
								newOID = parentOIDs + "." + OidVal;
							} else {
								reversedOID = parentOIDs;
								newOID = reversedOID+".0."+OidVal;
							}

							parentOIDsList.put(objName, newOID);
							writeOidInfo(objName, newOID, "TRAP-TYPE");
							writeOIDsToJSON();

							// if (readingCurrentMIB) {
							ObjectAttributes attrib = new ObjectAttributes();
							// System.out.println("" + objName + " =" + newOID);
							attrib.setDescription(description);
							attrib.setOid(newOID);
							attrib.setStatus(statusLine);
							attrib.setMaxAccess("");
							attrib.setSyntax("");
							attrib.setType("TRAP-TYPE");
							if (!oidNames.contains(objName)) {
								oidNames.add(objName);
								parentOIDsListWithAttributes.put(objName, attrib);
							}
							// }

						}

					}
				}

			}

			if (linevl.endsWith("OBJECT-IDENTITY".trim())) {
				readOBJ_IDENTITY(linevl, scanner);
			} else if (linevl.endsWith("NOTIFICATION-TYPE".trim())) {
				readNOTIF_TYPE(linevl, scanner);
			} else if (linevl.endsWith("TRAP-TYPE".trim())) {
				readTRAP_TYPE(linevl, scanner);
			} else if (linevl.contains("OBJECT IDENTIFIER".trim()) && linevl.contains("::=") && linevl.contains("}")) {

				String parts[] = linevl.split("\\}");
				String origArr[] = parts[0].split("\\{");
				objName = origArr[0].split("OBJECT IDENTIFIER")[0].trim();
				String lastLine = origArr[1];

				String[] arrOnlyZeros = lastLine.trim().split(" ");

				if (arrOnlyZeros.length == 2 && arrOnlyZeros[0].equalsIgnoreCase("0")
						&& arrOnlyZeros[1].equalsIgnoreCase("0")) {
					readMOD_IDENTITY(objLine, scanner);
				} else {
					readOBJ_IDENTIFIER(linevl, prevLine, scanner);
				}

			} else if (linevl.endsWith("MODULE-IDENTITY".trim())) {
				readMOD_IDENTITY(linevl, scanner);
			} else if (linevl.endsWith("OBJECT-TYPE".trim())) {

				if (linevl.trim().split(" ").length > 1) {
					readOBJ_TYPE(linevl, scanner);
				}

			} else if (linevl.contains("OBJECT IDENTIFIER".trim()) && !linevl.contains("\"")) {

				String nextLine = scanner.nextLine();
				String objNm = linevl.trim().split(" ")[0];
				if (nextLine.contains("{") && nextLine.contains("}")) {
					readOBJ_IDENTIFIERNextLine(objNm, nextLine, scanner);
				}
			}

			prevLine = linevl;
		}

	}

	private static void readNOTIF_TYPE(String objLine, Scanner scanner) {

		String parentName = "";
		String OidVal = "";
		String objName = "";
		String prevLine = "";
		String description = "";
		String firstLineDesc = "";
		boolean bracketOIDs = false;
		objName = objLine.trim().split(" ")[0];
		String statusLine = "";
		String syntaxLine = "";

		while (scanner.hasNextLine()) {

			String linevl = scanner.nextLine().trim();

			if (linevl.trim().startsWith("--")) {
				continue;
			}

			if (linevl.contains("\t")) {
				linevl = linevl.replaceAll("\t", " ");
			}

			if (linevl.trim().startsWith("STATUS")) {
				statusLine = linevl.trim().split("STATUS")[1].trim();
			}

			
//			if(linevl.trim().startsWith("OBJECTS") && linevl.contains("{")){
//				String line = "";
//				String objects= "";
//				do {
//
//					if (scanner.hasNextLine()) {
//						line = scanner.nextLine();
//						objects = objects + "\n" + line;
//						linevl = line;
//					}
//
//				} while (!line.contains("}"));
//				
//				System.out.println("OBJECTS list="+objects);
//				
//			}
			
			if (linevl.trim().startsWith("SYNTAX")) {
				syntaxLine = linevl.trim().split("SYNTAX")[1].trim();
			}

			if (prevLine.trim().endsWith("DESCRIPTION")) {

				if (linevl.matches("\".*\"")) {
					description = linevl;
				} else {

					firstLineDesc = linevl;
					String line = "";
					String nextLines = "";

					do {

						if (scanner.hasNextLine()) {
							line = scanner.nextLine();
							nextLines = nextLines + "\n" + line;
							linevl = line;
						}

					} while (!line.contains("\""));

					description = firstLineDesc + nextLines;
				}

			}

			String bracketsLine = "";
			if (linevl.contains("::".trim()) && linevl.contains("{") && !linevl.contains("MACRO".trim())
					&& !linevl.contains("TYPE NOTATION".trim())) {

				if (linevl.contains("--")) {
					linevl = linevl.substring(0, linevl.indexOf("--"));
				}

				String parts[] = linevl.split("\\}");
				String origArr[] = parts[0].split("\\{");
				String lastLine = origArr[1];
				String arrObj[] = lastLine.trim().split(" ");

				parentName = arrObj[0];

				if (linevl.contains("{") && linevl.contains("(")) {

					if (!linevl.contains("}")) {
						do {

							String scnLine = scanner.nextLine();
							bracketsLine += scnLine;

						} while (!bracketsLine.contains("}"));
						bracketsLine = linevl + " " + bracketsLine;
					} else {
						bracketsLine = linevl;
					}

					String oid = "1";
					Matcher m = Pattern.compile("\\((.*?)\\)").matcher(bracketsLine);

					while (m.find()) {
						oid = oid + "." + m.group(1);
					}

					bracketsLine = bracketsLine.replace("{", "");
					bracketsLine = bracketsLine.replace("}", "");
					String arr[] = bracketsLine.trim().split(" ");
					int len = arr.length;
					String lastVal = arr[len - 1];

					if (bracketsLine.contains("iso(")) {
						oid = oid.substring(2, oid.length());
					}

					oid = oid + "." + lastVal;

					OidVal = oid;
					bracketOIDs = true;

				} else {
					String v1 = "";

					if (arrObj.length == 4) {

						if (!arrObj[1].isEmpty()) {
							v1 = arrObj[1];
						}
						if (!arrObj[2].isEmpty()) {
							v1 = arrObj[2] + "." + v1;
						}
						OidVal = arrObj[3];
					} else if (arrObj.length == 3) {
						if (!arrObj[1].isEmpty()) {
							v1 = arrObj[1];
						}
						OidVal = arrObj[2];
					} else if (arrObj.length == 2) {
						OidVal = arrObj[1];
					}

					OidVal = OidVal + "." + v1;
				}

				if (!parentName.isEmpty()) {
					
					if (objName.trim().contains("--")) {
						return;
					}
					
					if (!objectParentMapping.containsKey(objName) && !objName.isEmpty()) {
						if (bracketOIDs) {
							OidVal = reverseOID(OidVal);
						}

						objectParentMapping.put(objName, parentName + "_" + OidVal);

						oid = "";
						String reversedOID = "";
						String parentOIDs = getParentOIDs(objName);

						if (!parentOIDs.isEmpty()) {
							String newOID = "";
							if (parentOIDs.contains("missing")) {
								String[] oidArr = parentOIDs.split("_");
								parentOIDs = oidArr[0];
								newOID = parentOIDs + "." + OidVal;
							} else {
								reversedOID = reverseOID(parentOIDs);
								newOID = "1" + reversedOID;
							}

							parentOIDsList.put(objName, newOID);
							writeOidInfo(objName, newOID, "NOTIFICATION-TYPE");
							writeOIDsToJSON();
							// if (readingCurrentMIB) {
							ObjectAttributes attrib = new ObjectAttributes();
							// System.out.println("" + objName + " =" + newOID);
							attrib.setDescription(description);
							attrib.setOid(newOID);
							attrib.setStatus(statusLine);
							attrib.setMaxAccess("");
							attrib.setSyntax(syntaxLine);
							attrib.setType("NOTIFICATION-TYPE");
							if (!oidNames.contains(objName)) {
								oidNames.add(objName);
								parentOIDsListWithAttributes.put(objName, attrib);
							}
							// }

						}

					}
				}

			}

			if (linevl.endsWith("OBJECT-IDENTITY".trim())) {
				readOBJ_IDENTITY(linevl, scanner);
			} else if (linevl.endsWith("NOTIFICATION-TYPE".trim())) {
				readNOTIF_TYPE(linevl, scanner);
			} else if (linevl.endsWith("TRAP-TYPE".trim())) {
				readTRAP_TYPE(linevl, scanner);
			} else if (linevl.contains("OBJECT IDENTIFIER".trim()) && linevl.contains("::=") && linevl.contains("}")) {

				String parts[] = linevl.split("\\}");
				String origArr[] = parts[0].split("\\{");
				objName = origArr[0].split("OBJECT IDENTIFIER")[0].trim();
				String lastLine = origArr[1];

				String[] arrOnlyZeros = lastLine.trim().split(" ");

				if (arrOnlyZeros.length == 2 && arrOnlyZeros[0].equalsIgnoreCase("0")
						&& arrOnlyZeros[1].equalsIgnoreCase("0")) {
					readMOD_IDENTITY(objLine, scanner);
				} else {
					readOBJ_IDENTIFIER(linevl, prevLine, scanner);
				}

			} else if (linevl.endsWith("MODULE-IDENTITY".trim())) {
				readMOD_IDENTITY(linevl, scanner);
			} else if (linevl.endsWith("OBJECT-TYPE".trim())) {

				if (linevl.trim().split(" ").length > 1) {
					readOBJ_TYPE(linevl, scanner);
				}

			} else if (linevl.contains("OBJECT IDENTIFIER".trim()) && !linevl.contains("\"")) {

				String nextLine = scanner.nextLine();
				String objNm = linevl.trim().split(" ")[0];
				if (nextLine.contains("{") && nextLine.contains("}")) {
					readOBJ_IDENTIFIERNextLine(objNm, nextLine, scanner);
				}
			}

			prevLine = linevl;
		}

	}

	private static void readOBJ_IDENTITY(String objLine, Scanner scanner) {

		String parentName = "";
		String OidVal = "";
		String objName = objLine.trim().split(" ")[0];
		boolean bracketOIDs = false;
		String prevLine = "";
		String statusLine = "";
		String description = "";
		String firstLineDesc = "";
		String syntaxLine = "";

		while (scanner.hasNextLine()) {

			String linevl = scanner.nextLine().trim();

			if (linevl.trim().startsWith("--")) {
				continue;
			}

			if (linevl.contains("\t")) {
				linevl = linevl.replaceAll("\t", " ");
			}

			if (linevl.trim().startsWith("STATUS")) {
				statusLine = linevl.trim().split("STATUS")[1].trim();
			}

			if (linevl.trim().startsWith("SYNTAX")) {
				syntaxLine = linevl.trim().split("SYNTAX")[1].trim();
			}

			if (prevLine.trim().endsWith("DESCRIPTION")) {

				if (linevl.matches("\".*\"")) {

					description = linevl;

				} else {
					firstLineDesc = linevl;
					String line = "";
					String nextLines = "";
					do {

						if (scanner.hasNextLine()) {
							line = scanner.nextLine();
							nextLines = nextLines + "\n" + line;
							linevl = line;
						}
					} while (!line.contains("\""));
					description = firstLineDesc + nextLines;
				}
			}

			String bracketsLine = "";
			if (linevl.contains("::".trim()) && linevl.contains("{")) {

				if (linevl.contains("--")) {
					linevl = linevl.substring(0, linevl.indexOf("--"));
				}

				String parts[] = linevl.split("\\}");
				String origArr[] = parts[0].split("\\{");
				String lastLine = origArr[1];
				String arrObj[] = lastLine.trim().split(" ");
				parentName = arrObj[0];

				if (linevl.contains("{") && linevl.contains("(")) {

					do {

						String scnLine = scanner.nextLine();
						bracketsLine += scnLine;

					} while (!bracketsLine.contains("}"));

					bracketsLine = linevl + " " + bracketsLine;

					String oid = "1";
					Matcher m = Pattern.compile("\\((.*?)\\)").matcher(bracketsLine);

					while (m.find()) {
						oid = oid + "." + m.group(1);
					}

					bracketsLine = bracketsLine.replace("{", "");
					bracketsLine = bracketsLine.replace("}", "");
					String arr[] = bracketsLine.trim().split(" ");
					int len = arr.length;
					String lastVal = arr[len - 1];

					if (bracketsLine.contains("iso(")) {
						oid = oid.substring(2, oid.length());
					}

					oid = oid + "." + lastVal;

					OidVal = oid;
					bracketOIDs = true;

				} else {
					String v1 = "";

					if (arrObj.length == 4) {

						if (!arrObj[1].isEmpty()) {
							v1 = arrObj[1];
						}
						if (!arrObj[2].isEmpty()) {
							v1 = arrObj[2] + "." + v1;
						}
						OidVal = arrObj[3];
					} else if (arrObj.length == 3) {
						if (!arrObj[1].isEmpty()) {
							v1 = arrObj[1];
						}
						OidVal = arrObj[2];
					} else if (arrObj.length == 2) {
						OidVal = arrObj[1];
					}

					OidVal = OidVal + "." + v1;
				}

				if (!parentName.isEmpty()) {
					if (objName.trim().contains("--")) {
						return;
					}
					if (!objectParentMapping.containsKey(objName) && !objName.isEmpty()) {

						if (bracketOIDs) {
							OidVal = reverseOID(OidVal);
						}

						objectParentMapping.put(objName, parentName + "_" + OidVal);

						oid = "";
						String reversedOID = "";
						String parentOIDs = getParentOIDs(objName);

						if (!parentOIDs.isEmpty()) {
							String newOID = "";
							if (parentOIDs.contains("missing")) {
								String[] oidArr = parentOIDs.split("_");
								parentOIDs = oidArr[0];
								newOID = parentOIDs + "." + OidVal;
							} else {
								reversedOID = reverseOID(parentOIDs);
								newOID = "1" + reversedOID;
							}

							parentOIDsList.put(objName, newOID);
							writeOidInfo(objName, newOID, "OBJECT-IDENTITY");
							writeOIDsToJSON();
							// if (readingCurrentMIB) {
							ObjectAttributes attrib = new ObjectAttributes();

							attrib.setDescription(description);
							attrib.setOid(newOID);
							attrib.setStatus(statusLine);
							attrib.setSyntax(syntaxLine);
							attrib.setMaxAccess("");
							attrib.setType("OBJECT-IDENTITY");
							// System.out.println("" + objName + " =" + newOID);
							if (!oidNames.contains(objName)) {
								oidNames.add(objName);
								parentOIDsListWithAttributes.put(objName, attrib);
							}
							// }
						}

					}
				}

			}

			if (linevl.endsWith("OBJECT-IDENTITY".trim())) {
				readOBJ_IDENTITY(linevl, scanner);
			} else if (linevl.endsWith("NOTIFICATION-TYPE".trim())) {
				readNOTIF_TYPE(linevl, scanner);
			} else if (linevl.endsWith("TRAP-TYPE".trim())) {
				readTRAP_TYPE(linevl, scanner);
			} else if (linevl.contains("OBJECT IDENTIFIER".trim()) && linevl.contains("::=") && linevl.contains("}")) {

				String parts[] = linevl.split("\\}");
				String origArr[] = parts[0].split("\\{");
				objName = origArr[0].split("OBJECT IDENTIFIER")[0].trim();
				String lastLine = origArr[1];

				String[] arrOnlyZeros = lastLine.trim().split(" ");

				if (arrOnlyZeros.length == 2 && arrOnlyZeros[0].equalsIgnoreCase("0")
						&& arrOnlyZeros[1].equalsIgnoreCase("0")) {
					readOBJ_IDENTITY(objLine, scanner);
				} else {
					readOBJ_IDENTIFIER(linevl, prevLine, scanner);
				}

			} else if (linevl.endsWith("MODULE-IDENTITY".trim())) {
				readMOD_IDENTITY(linevl, scanner);
			} else if (linevl.endsWith("OBJECT-TYPE".trim())) {
				if (linevl.trim().split(" ").length > 1) {
					readOBJ_TYPE(linevl, scanner);
				}
			} else if (linevl.contains("OBJECT IDENTIFIER".trim()) && !linevl.contains("\"")) {
				// System.out.println("next line 3"+linevl);
				String nextLine = scanner.nextLine();
				String objNm = linevl.trim().split(" ")[0];
				if (nextLine.contains("{") && nextLine.contains("}")) {
					readOBJ_IDENTIFIERNextLine(objNm, nextLine, scanner);
				}
			}

			prevLine = linevl;
		}

	}

	private static void readOBJ_IDENTIFIER(String objLine, String prevLine, Scanner scanner) {

		String parentName = "";
		String OidVal = "";
		String objName = "";
		boolean flag = false;
		boolean bracketOIDs = false;

		while (scanner.hasNextLine()) {

			String linevl = scanner.nextLine().trim();

			if (linevl.trim().startsWith("--")) {
				continue;
			}

			if (linevl.contains("\t")) {
				linevl = linevl.replaceAll("\t", " ");
			}

			if (objLine.contains("::=".trim()) && !flag) {

				if (objLine.contains("--")) {
					objLine = objLine.substring(0, objLine.indexOf("--"));
				}

				flag = true;
				String bracketsLine = "";

				String parts[] = objLine.split("\\}");
				String origArr[] = parts[0].split("\\{");
				objName = origArr[0].split("OBJECT IDENTIFIER")[0].trim();
				String lastLine = origArr[1].trim();
				String arrObj[] = lastLine.trim().split(" ");

				parentName = arrObj[0];

				if (objName.isEmpty()) {
					objName = prevLine.trim();
				}

				if (objLine.contains("{") && objLine.contains("(")) {

					do {

						String scnLine = scanner.nextLine();
						bracketsLine += scnLine;

					} while (!bracketsLine.contains("}"));

					bracketsLine = objLine + " " + bracketsLine;

					String oid = "1";
					Matcher m = Pattern.compile("\\((.*?)\\)").matcher(bracketsLine);

					while (m.find()) {
						oid = oid + "." + m.group(1);
					}

					bracketsLine = bracketsLine.replace("{", "");
					bracketsLine = bracketsLine.replace("}", "");
					String arr[] = bracketsLine.trim().split(" ");
					int len = arr.length;
					String lastVal = arr[len - 1];

					if (bracketsLine.contains("iso(")) {
						oid = oid.substring(2, oid.length());
					}

					oid = oid + "." + lastVal;

					OidVal = oid;
					bracketOIDs = true;

				} else {

					String v1 = "";

					if (arrObj.length == 4) {

						if (!arrObj[1].isEmpty()) {
							v1 = arrObj[1];
						}
						if (!arrObj[2].isEmpty()) {
							v1 = arrObj[2] + "." + v1;
						}
						OidVal = arrObj[3];

					} else if (arrObj.length == 3) {
						if (!arrObj[1].isEmpty()) {
							v1 = arrObj[1];
						}
						OidVal = arrObj[2];
					} else if (arrObj.length == 2) {
						OidVal = arrObj[1];
					}

					OidVal = OidVal + "." + v1;

				}

				if (!parentName.isEmpty()) {

					if (objName.trim().contains("--")) {
						return;
					}

					if (!objectParentMapping.containsKey(objName) && !objName.isEmpty()) {

						if (bracketOIDs) {
							OidVal = reverseOID(OidVal);
						}

						objectParentMapping.put(objName, parentName + "_" + OidVal);
						oid = "";

						String reversedOID = "";
						String parentOIDs = getParentOIDs(objName);

						if (!parentOIDs.isEmpty()) {

							String newOID = "";

							if (parentOIDs.contains("missing")) {
								String[] oidArr = parentOIDs.split("_");
								parentOIDs = oidArr[0];
								newOID = parentOIDs + "." + OidVal;
							} else {
								reversedOID = reverseOID(parentOIDs);
								newOID = "1" + reversedOID;
							}

							parentOIDsList.put(objName, newOID);
							writeOIDsToJSON();
							writeOidInfo(objName, newOID, "OBJECT IDENTIFIER");
							// if (readingCurrentMIB) {
							ObjectAttributes attrib = new ObjectAttributes();

							attrib.setDescription("");
							attrib.setOid(newOID);
							attrib.setStatus("");
							attrib.setSyntax("");
							attrib.setMaxAccess("");
							attrib.setType("OBJECT IDENTIFIER");

							// System.out.println("" + objName + " =" + newOID);

							if (!oidNames.contains(objName)) {
								oidNames.add(objName);
								parentOIDsListWithAttributes.put(objName, attrib);
							}
							// }

						}
					}
				}
			}

			if (linevl.endsWith("OBJECT-IDENTITY".trim())) {
				readOBJ_IDENTITY(linevl, scanner);
			} else if (linevl.endsWith("NOTIFICATION-TYPE".trim())) {
				readNOTIF_TYPE(linevl, scanner);
			} else if (linevl.endsWith("TRAP-TYPE".trim())) {
				readTRAP_TYPE(linevl, scanner);
			} else if (linevl.contains("OBJECT IDENTIFIER".trim()) && linevl.contains("::=") && linevl.contains("}")) {
				String parts[] = linevl.split("\\}");
				String origArr[] = parts[0].split("\\{");
				objName = origArr[0].split("OBJECT IDENTIFIER")[0].trim();
				String lastLine = origArr[1];

				String[] arrOnlyZeros = lastLine.trim().split(" ");

				if (arrOnlyZeros.length == 2 && arrOnlyZeros[0].equalsIgnoreCase("0")
						&& arrOnlyZeros[1].equalsIgnoreCase("0")) {
					readOBJ_IDENTIFIER(objLine, prevLine, scanner);
				} else {
					readOBJ_IDENTIFIER(linevl, prevLine, scanner);
				}

			} else if (linevl.endsWith("MODULE-IDENTITY".trim())) {
				readMOD_IDENTITY(linevl, scanner);
			} else if (linevl.endsWith("OBJECT-TYPE".trim())) {
				if (linevl.trim().split(" ").length > 1) {
					readOBJ_TYPE(linevl, scanner);
				}
			} else if (linevl.contains("OBJECT IDENTIFIER".trim()) && !linevl.contains("\"")) {

				String nextLine = scanner.nextLine();
				String objNm = linevl.trim().split(" ")[0];
				if (nextLine.contains("{") && nextLine.contains("}")) {
					readOBJ_IDENTIFIERNextLine(objNm, nextLine, scanner);
				}
			}

			prevLine = linevl;
		}
		// scanner.close();

	}

	private static void readOBJ_IDENTIFIERNextLine(String objName, String objLine, Scanner scanner) {

		String parentName = "";
		String OidVal = "";

		boolean flag = false;
		boolean bracketOIDs = false;
		String prevLine = "";

		while (scanner.hasNextLine()) {

			String linevl = scanner.nextLine().trim();
			if (linevl.trim().startsWith("--")) {
				continue;
			}

			if (linevl.contains("\t")) {
				linevl = linevl.replaceAll("\t", " ");
			}

			if (objLine.contains("}") && !flag) {

				flag = true;
				if (objLine.contains("--")) {
					objLine = objLine.substring(0, linevl.indexOf("--"));
				}

				String parts[] = objLine.split("\\}");
				String origArr[] = parts[0].split("\\{");
				String lastLine = origArr[1];
				String arrObj[] = lastLine.trim().split(" ");
				parentName = arrObj[0];

				String bracketsLine = "";

				if (objLine.contains("{") && objLine.contains("(")) {

					do {

						String scnLine = scanner.nextLine();
						bracketsLine += scnLine;

					} while (!bracketsLine.contains("}"));

					bracketsLine = objLine + " " + bracketsLine;

					String oid = "1";
					Matcher m = Pattern.compile("\\((.*?)\\)").matcher(bracketsLine);

					while (m.find()) {
						oid = oid + "." + m.group(1);
					}

					bracketsLine = bracketsLine.replace("{", "");
					bracketsLine = bracketsLine.replace("}", "");
					String arr[] = bracketsLine.trim().split(" ");

					int len = arr.length;
					String lastVal = arr[len - 1];

					if (bracketsLine.contains("iso(")) {
						oid = oid.substring(2, oid.length());
					}

					oid = oid + "." + lastVal;

					OidVal = oid;
					bracketOIDs = true;

				} else {

					String v1 = "";

					if (arrObj.length == 4) {

						if (!arrObj[1].isEmpty()) {
							v1 = arrObj[1];
						}
						if (!arrObj[2].isEmpty()) {
							v1 = arrObj[2] + "." + v1;
						}
						OidVal = arrObj[3];
					} else if (arrObj.length == 3) {
						if (!arrObj[1].isEmpty()) {
							v1 = arrObj[1];
						}
						OidVal = arrObj[2];
					} else if (arrObj.length == 2) {
						OidVal = arrObj[1];
					}

					OidVal = OidVal + "." + v1;

				}

				if (!parentName.isEmpty()) {

					if (objName.trim().contains("--")) {
						return;
					}

					if (!objectParentMapping.containsKey(objName) && !objName.isEmpty()) {

						if (bracketOIDs) {
							OidVal = reverseOID(OidVal);
						}

						objectParentMapping.put(objName, parentName + "_" + OidVal);
						oid = "";

						String reversedOID = "";
						String parentOIDs = getParentOIDs(objName);

						if (!parentOIDs.isEmpty()) {
							String newOID = "";
							if (parentOIDs.contains("missing")) {
								String[] oidArr = parentOIDs.split("_");
								parentOIDs = oidArr[0];
								newOID = parentOIDs + "." + OidVal;
							} else {
								reversedOID = reverseOID(parentOIDs);
								newOID = "1" + reversedOID;
							}

							// Put node name and OID in.
							parentOIDsList.put(objName, newOID);
							writeOIDsToJSON();
							writeOidInfo(objName, newOID, "OBJECT IDENTIFIER");
							// if (readingCurrentMIB) {

							ObjectAttributes attrib = new ObjectAttributes();

							attrib.setDescription("");
							attrib.setOid(newOID);
							attrib.setStatus("");
							attrib.setSyntax("");
							attrib.setMaxAccess("");
							attrib.setType("OBJECT IDENTIFIER");

							// System.out.println("" + objName + " =" + newOID);

							if (!oidNames.contains(objName)) {
								oidNames.add(objName);
								parentOIDsListWithAttributes.put(objName, attrib);
							}

							// }
						}
					}
				}

			}

			if (linevl.endsWith("OBJECT-IDENTITY".trim())) {
				readOBJ_IDENTITY(linevl, scanner);
			} else if (linevl.endsWith("NOTIFICATION-TYPE".trim())) {
				readNOTIF_TYPE(linevl, scanner);
			} else if (linevl.endsWith("TRAP-TYPE".trim())) {
				readTRAP_TYPE(linevl, scanner);
			} else if (linevl.contains("OBJECT IDENTIFIER".trim()) && linevl.contains("::=") && linevl.contains("}")) {

				String parts[] = linevl.split("\\}");
				String origArr[] = parts[0].split("\\{");
				objName = origArr[0].split("OBJECT IDENTIFIER")[0].trim();

				String lastLine = origArr[1];

				String[] arrOnlyZeros = lastLine.trim().split(" ");

				if (arrOnlyZeros.length == 2 && arrOnlyZeros[0].equalsIgnoreCase("0")
						&& arrOnlyZeros[1].equalsIgnoreCase("0")) {
					readOBJ_IDENTIFIERNextLine(linevl, prevLine, scanner);
				} else {
					readOBJ_IDENTIFIER(linevl, prevLine, scanner);
				}

			} else if (linevl.endsWith("MODULE-IDENTITY".trim())) {
				readMOD_IDENTITY(linevl, scanner);
			} else if (linevl.endsWith("OBJECT-TYPE".trim())) {
				if (linevl.trim().split(" ").length > 1) {
					readOBJ_TYPE(linevl, scanner);
				}
			} else if (linevl.contains("OBJECT IDENTIFIER".trim()) && !linevl.contains("\"")) {
				String nextLine = scanner.nextLine();
				String objNm = linevl.trim().split(" ")[0];
				if (nextLine.contains("{") && nextLine.contains("}")) {
					readOBJ_IDENTIFIERNextLine(objNm, nextLine, scanner);
				}
			}

			prevLine = linevl;

		}

	}

	private static String reverseOID(String oids) {

		String s = "";
		String newStr = oids.substring(1).trim();
		String arr[] = newStr.split("\\.");

		if (arr.length > 0) {

			for (int i = arr.length - 1; i >= 0; i--) {
				String oid = arr[i];
				if (!oid.isEmpty())
					s = s + "." + oid;
			}

		}
		return s;

	}

	/**
	 * Get parents of the passed object. It returns OIDs of the parent.
	 * 
	 * @param objectName
	 * @return
	 */
	private static String getParentOIDs(String objectName) {

		boolean flag = false;

		if (objectName.isEmpty()) {
			return oid;
		}

		if (objectParentMapping.containsKey(objectName.trim())) {
			flag = true;
			String parentNameAndOID = objectParentMapping.get(objectName);

			if (parentNameAndOID != null) {
				String arr[] = parentNameAndOID.split("_");

				if (arr.length > 1) {
					String objName = arr[0];
					oid = oid + "." + arr[1];
					if (!objName.equalsIgnoreCase(objectName))
						getParentOIDs(objName);
				}
			}
		} else {
			flag = false;
		}

		// Check if parent is missing. Then look for missing parents.
		if (!flag && !objectName.equalsIgnoreCase("iso") && !objectName.equalsIgnoreCase("0")) {

			findMissingParent = true;
			String oidVal = lookForMissingParentInCurrentMIB(currentRFCName, objectName);

			// System.out.println("missing parents
			// size="+missingParents.size());
			if (missingParents.size() > 0) {
				for (int i = 0; i < missingParents.size(); i++) {
					oidVal = getParentOIDs(missingParents.get(i));
				}
			}

			if (!oidVal.isEmpty()) {
				oid = oidVal + "_missing";
			}
			return oid;
		}

		return oid;
	}

	private static String getTrapTypeParentOIDs(String objectName) {

		String oid = "";
		JSONParser parser = new JSONParser();
		JSONObject jsonObject = null;
		String oidsValues = "";
		try {
			
			Object obj = parser.parse(new FileReader("D:\\MCTS\\oidmappings.json"));
			jsonObject = (JSONObject) obj;
			
			 oid = (String) jsonObject.get(objectName.trim());
			
			
			
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (ParseException e) {
			e.printStackTrace();
		}

		return oid;
	}

	// Get all parents of the particular OID.
	private static void getParentList(String objectName) {

		boolean flag = false;

		if (objectParentMapping.containsKey(objectName.trim())) {
			flag = true;
			String parentNameAndOID = objectParentMapping.get(objectName);
			String arr[] = parentNameAndOID.split("_");

			if (arr.length > 1) {

				String objName = arr[0];
				oid = oid + "." + arr[1];
				if (!objName.equalsIgnoreCase(objectName)) {
					System.out.println(spaceCount + objName);
					spaceCount = spaceCount + " ";
					getParentList(objName);
				} else {
					System.out.println(" " + objName);
				}
			}

		} else {
			flag = false;
		}

	}

	static String prevKey = "";

	// Get list of childrens of a particular OID.
	private static void getChildrenList(String objectName) {

		if (objectParentMapping.containsKey(objectName.trim())) {

			for (int i = 0; i < oidNames.size(); i++) {

				String key = oidNames.get(i);
				String value = objectParentMapping.get(key);

				if (value.contains(objectName + "_")) {

					String arr[] = value.split("_");
					// System.out.println("pr len="+prevKey.length()+"
					// len2="+parentOIDsList.get(key).length());
					if (parentOIDsList.get(key).length() > prevKey.length()) {
						spaceCount = spaceCount + " ";
					}
					// System.out.println(spaceCount + parentOIDsList.get(key));
					System.out.println(spaceCount + key);

					// System.out.println("arr0="+arr[0]+" obj="+objectName+"
					// key="+key);
					if (!arr[0].equalsIgnoreCase(key)) {
						getChildrenList(key);
					}

				}
				prevKey = parentOIDsList.get(key);

			}
		}
	}

	/**
	 * Look for missing parent in current MIB file.
	 * 
	 * @param currentMIbFile
	 * @param objectName
	 * @return
	 */
	private static String lookForMissingParentInCurrentMIB(String currentMIbFile, String objectName) {
		// System.out.println("look for missing parent= " + objectName);
		JSONParser parser = new JSONParser();
		JSONObject jsonObject = null;
		String oidsValues = "";

		counters++;

		try {

			Object obj = parser.parse(new FileReader("D:\\MCTS\\oidmappings.json"));
			jsonObject = (JSONObject) obj;

		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (ParseException e) {
			e.printStackTrace();
		}

		try {

			BufferedReader br = new BufferedReader(
					new FileReader("D:\\MCTS\\mibs\\mibs\\" + currentMIbFile.trim()));

			try {

				StringBuilder sb = new StringBuilder();
				String line = br.readLine();

				while (line != null) {
					sb.append(line);
					sb.append(System.lineSeparator());
					line = br.readLine();
				}

				String everything = sb.toString();

				Scanner scanner = new Scanner(everything);

				while (scanner.hasNextLine()) {

					String nextLine = scanner.nextLine();

					if (nextLine.contains("\t")) {
						nextLine = nextLine.replaceAll("\t", "");
					}

					if (nextLine.trim().startsWith("--")) {
						continue;
					}

					if (nextLine.contains(objectName + " ") && nextLine.endsWith("OBJECT-TYPE")) {
						// System.out.println("missing in obj type");

						String nextVal = "";

						if (!nextLine.contains("::=")) {
							do {
								nextVal = scanner.nextLine();
							} while (!nextVal.contains("::"));
						} else {
							nextVal = nextLine;
						}
						if (nextVal.isEmpty()) {
							nextVal = nextLine;
						}
						if (!nextVal.isEmpty()) {

							if (nextVal.contains("--")) {
								nextVal = nextVal.substring(0, nextVal.indexOf("--"));
							}

							String parts[] = nextVal.split("\\}");
							String origArr[] = parts[0].split("\\{");
							String lastLine = origArr[1];
							String arrObj[] = lastLine.trim().split(" ");

							String parent = arrObj[0];
							String oidVal = arrObj[1];

							if (jsonObject != null) {

								String parentOid = (String) jsonObject.get(parent);

								if (parentOid != null && parentOid.contains(".")) {

									oidsValues = parentOid + "." + oidVal;

									String newOID = oidsValues;
									oidsValues = newOID;
									missingPArentOIDs = oidsValues;
									objectParentMapping.put(objectName, parent + "_" + oidVal);
									System.out.println("missing " + objectName + "   =" + newOID);
									parentOIDsList.put(objectName, newOID);
									writeOIDsToJSON();
									writeOidInfo(objectName, newOID, "OBJECT-TYPE");
									scanner.close();

									if (missingParents.contains(objectName)) {
										missingParents.remove(objectName);
									}

									break;

								} else if (parentOid == null) {
									missingParents.add(objectName);
									lookForMissingParentInCurrentMIB(currentMIbFile, parent);
								}
							}
						}

					} else if (nextLine.contains(objectName + " ") && nextLine.endsWith("OBJECT-IDENTITY")) {
						// System.out.println("missing in obj ident");
						String nextVal = "";

						if (!nextLine.contains("::=")) {
							do {
								nextVal = scanner.nextLine();
							} while (!nextVal.contains("::"));
						} else {
							nextVal = nextLine;
						}
						if (nextVal.isEmpty()) {
							nextVal = nextLine;
						}

						if (!nextVal.isEmpty()) {

							if (nextVal.contains("--")) {
								nextVal = nextVal.substring(0, nextVal.indexOf("--"));
							}

							String parts[] = nextVal.split("\\}");
							String origArr[] = parts[0].split("\\{");
							String lastLine = origArr[1];
							String arrObj[] = lastLine.trim().split(" ");

							String parent = arrObj[0];
							String oidVal = arrObj[1];

							if (jsonObject != null) {

								String parentOid = (String) jsonObject.get(parent);

								if (parentOid != null && parentOid.contains(".")) {

									oidsValues = parentOid + "." + oidVal;

									String newOID = oidsValues;
									oidsValues = newOID;
									missingPArentOIDs = oidsValues;
									objectParentMapping.put(objectName, parent + "_" + oidVal);
									parentOIDsList.put(objectName, newOID);
									writeOIDsToJSON();
									writeOidInfo(objectName, newOID, "OBJECT-IDENTITY");
									System.out.println("missing " + objectName + "   =" + newOID);
									scanner.close();

									if (missingParents.contains(objectName)) {
										missingParents.remove(objectName);
									}

									break;

								} else if (parentOid == null) {
									missingParents.add(objectName);
									lookForMissingParentInCurrentMIB(currentMIbFile, parent);
								}
							}
						}

					} else if (nextLine.contains(objectName + " ") && nextLine.endsWith("MODULE-IDENTITY")) {

						// System.out.println("missing in mod ident");

						String nextVal = "";

						if (!nextLine.contains("::=")) {
							do {
								nextVal = scanner.nextLine();
							} while (!nextVal.contains("::"));
						} else {
							nextVal = nextLine;
						}

						if (nextVal.isEmpty()) {
							nextVal = nextLine;
						}

						if (!nextVal.isEmpty()) {

							if (nextVal.contains("--")) {
								nextVal = nextVal.substring(0, nextVal.indexOf("--"));
							}

							String parts[] = nextVal.split("\\}");
							String origArr[] = parts[0].split("\\{");
							String lastLine = origArr[1];
							String arrObj[] = lastLine.trim().split(" ");

							String parent = arrObj[0];
							String oidVal = arrObj[1];

							if (jsonObject != null) {

								String parentOid = (String) jsonObject.get(parent);

								if (parentOid != null && parentOid.contains(".")) {

									oidsValues = parentOid + "." + oidVal;

									String newOID = oidsValues;
									oidsValues = newOID;
									missingPArentOIDs = oidsValues;
									objectParentMapping.put(objectName, parent + "_" + oidVal);
									parentOIDsList.put(objectName, newOID);
									writeOIDsToJSON();
									writeOidInfo(objectName, newOID, "MODULE-IDENTITY");
									System.out.println("missing " + objectName + "   =" + newOID);
									scanner.close();

									if (missingParents.contains(objectName)) {
										missingParents.remove(objectName);
									}
									break;

								} else if (parentOid == null) {
									missingParents.add(objectName);
									lookForMissingParentInCurrentMIB(currentMIbFile, parent);
								}
							}

						}

					} else if (nextLine.contains("OBJECT IDENTIFIER")
							&& nextLine.split("OBJECT IDENTIFIER")[0].contains(objectName) && nextLine.contains("}")) {

						// System.out.println("missing found in obj
						// identifier");

						String nextVal = "";

						if (!nextLine.contains("::=")) {
							do {
								nextVal = scanner.nextLine();
							} while (!nextVal.contains("::"));
						} else {
							nextVal = nextLine;
						}
						if (nextVal.isEmpty()) {
							nextVal = nextLine;
						}

						if (!nextVal.isEmpty()) {

							if (nextVal.contains("--")) {
								nextVal = nextVal.substring(0, nextVal.indexOf("--"));
							}

							String parts[] = nextVal.split("\\}");
							String origArr[] = parts[0].split("\\{");
							String lastLine = origArr[1];
							String arrObj[] = lastLine.trim().split(" ");

							String[] arrOnlyZeros = lastLine.trim().split(" ");

							if (arrOnlyZeros.length == 2 && arrOnlyZeros[0].equalsIgnoreCase("0")
									&& arrOnlyZeros[1].equalsIgnoreCase("0")) {
								continue;
							}

							String parent = arrObj[0];
							String bracketsLine = "";
							String oidVal = "";

							if (nextVal.contains("{") && nextVal.contains("(")) {

								do {

									String scnLine = scanner.nextLine();
									bracketsLine += scnLine;

								} while (!bracketsLine.contains("}"));

								bracketsLine = nextVal + " " + bracketsLine;

								String oid = "1";
								Matcher m = Pattern.compile("\\((.*?)\\)").matcher(bracketsLine);

								while (m.find()) {
									oid = oid + "." + m.group(1);
								}

								bracketsLine = bracketsLine.replace("{", "");
								bracketsLine = bracketsLine.replace("}", "");
								String arr[] = bracketsLine.trim().split(" ");
								int len = arr.length;
								String lastVal = arr[len - 1];

								if (bracketsLine.contains("iso(")) {
									oid = oid.substring(2, oid.length());
								}

								oid = oid + "." + lastVal;

								oidVal = oid;
							} else {
								oidVal = arrObj[1];
							}

							if (jsonObject != null) {

								String parentOid = (String) jsonObject.get(parent);

								if (parentOid != null && parentOid.contains(".")) {

									oidsValues = parentOid + "." + oidVal;

									String newOID = oidsValues;
									oidsValues = newOID;
									missingPArentOIDs = oidsValues;
									objectParentMapping.put(objectName, parent + "_" + oidVal);
									parentOIDsList.put(objectName, newOID);
									System.out.println("missing " + objectName + "   =" + newOID);
									writeOIDsToJSON();
									writeOidInfo(objectName, newOID, "OBJECT IDENTIFIER");
									scanner.close();

									if (missingParents.contains(objectName)) {
										missingParents.remove(objectName);
									}

									break;

								} else if (parentOid == null) {
									missingParents.add(objectName);
									lookForMissingParentInCurrentMIB(currentMIbFile, parent);

								}
							}

						}

					}

				}
				scanner.close();

			} finally {
				br.close();
			}

		} catch (Exception ex) {

		}

		return missingPArentOIDs;
	}

	private static void importDependencyMIBs(ArrayList<String> importsList) {

		for (int i = 0; i < importsList.size(); i++) {
			String ImportRFCName = importsList.get(i);
			ArrayList<String> currentImportSize = readDependencyOfCurrentMib(ImportRFCName);

			if (currentImportSize != null) {

				for (int j = 0; j < currentImportSize.size(); j++) {

					if (!allImportsMIBsNew.contains(currentImportSize.get(j)))
						allImportsMIBsNew.add(currentImportSize.get(j));

				}

				if (!allImportsMIBsNew.contains(ImportRFCName))
					allImportsMIBsNew.add(ImportRFCName);

			}

		}

	}

	private static ArrayList<String> readDependencyOfCurrentMib(String RFCName) {

		if (allImportsMIBs.contains(RFCName)) {
			return null;
		} else {
			allImportsMIBs.add(RFCName);
		}

		try {

			importsList = new ArrayList<>();

			if (RFCName.contains("--")) {
				RFCName = RFCName.substring(0, RFCName.indexOf("--"));
			}

			RFCName = RFCName.replaceAll(";", "");

			BufferedReader br = new BufferedReader(
					new FileReader("D:\\MCTS\\mibs\\mibs\\" + RFCName.trim()));

			try {

				StringBuilder sb = new StringBuilder();
				String line = br.readLine();

				// Loop through each line.
				while (line != null) {
					sb.append(line);
					sb.append(System.lineSeparator());
					line = br.readLine();
				}

				String everything = sb.toString();

				String[] arr = everything.split("\n\r");

				for (int i = 0; i < arr.length; i++) {
					readImportsFromCurrentMIB(arr[i]);
				}

				if (importsList.size() > 0)
					importDependencyMIBs(importsList);

			} finally {
				br.close();
			}

		} catch (Exception ex) {
			exceptCount++;
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			ex.printStackTrace(pw);

			String exc = sw.toString();

			exceptMsg = exceptMsg + "\n\n\n" + exc;
			ex.printStackTrace();
		}

		return importsList;

	}

	private static void readRFCFromDirs(String rfcName) {

		try {

			importsList = new ArrayList<>();

			if (rfcName.contains("--")) {
				rfcName = rfcName.substring(0, rfcName.indexOf("--"));
			}

			rfcName = rfcName.replaceAll(";", "");

			currentRFCName = rfcName;

			BufferedReader br = new BufferedReader(
					new FileReader("D:\\MCTS\\mibs\\mibs\\" + rfcName.trim()));

			try {

				StringBuilder sb = new StringBuilder();
				String line = br.readLine();

				// Loop through each line.
				while (line != null) {
					sb.append(line);
					sb.append(System.lineSeparator());
					line = br.readLine();
				}

				String everything = sb.toString();
				readMIBMacros(everything);

			} finally {
				br.close();
			}

		} catch (Exception ex) {
			exceptCount++;
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			ex.printStackTrace(pw);

			String exc = sw.toString();

			exceptMsg = exceptMsg + "\n\n\n" + exc;
			ex.printStackTrace();
		}

	}

}
