package com.mastercom.snmp;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import org.apache.log4j.Logger;
import org.snmp4j.CommunityTarget;
import org.snmp4j.PDU;
import org.snmp4j.PDUv1;
import org.snmp4j.Snmp;
import org.snmp4j.TransportMapping;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.smi.IpAddress;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.OctetString;
import org.snmp4j.smi.UdpAddress;
import org.snmp4j.smi.VariableBinding;
import org.snmp4j.transport.DefaultUdpTransportMapping;

public class SNMPSender {

	private static final Logger logger = Logger.getLogger(SNMPSender.class.getName());
	public static final String community = "public";

	static int hpForwardCount = 0;
	static int sevOneForwardCount = 0;

	static HashMap<String, String> configValues = new HashMap<>();
	static HashMap<String, String> OIDConfigMapping = new HashMap<>();
	static HashMap<String, String> deviceInfoMapping = new HashMap<>();

	public void init(HashMap<String, String> trapConfigValues, HashMap<String, String> OIDConfigMapping,
			HashMap<String, String> deviceInfoMapping) {

		this.configValues = trapConfigValues;
		this.OIDConfigMapping = OIDConfigMapping;
		this.deviceInfoMapping = deviceInfoMapping;

	}

	/**
	 * Filters and forwards the trap. For each trap it will check a match in HP
	 * and SevOne NMS. If match is successful it will forward the trap.
	 * 
	 * @param trapOIDList
	 * @param bindings
	 * @param sourceIp
	 */
	public void filterAndForwardTrap(ArrayList<String> trapOIDList, VariableBinding[] bindings, String sourceIp,
			int pduType, HashMap<String, String> v1TrapDetails) {

		try {
			filterOID(trapOIDList, bindings, sourceIp, pduType, v1TrapDetails);
		} catch (Exception ex) {
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			ex.printStackTrace(pw);

			String errorString = sw.toString();
			logger.info(errorString);
		}
	}

	/**
	 * Filters OID for HP NNM. Check if Trap OID has a match with allowed HP
	 * OIDs or not.
	 * 
	 * @param trapOIDList
	 * @param bindings
	 * @param sourceIp
	 */
	private void filterOID(ArrayList<String> trapOIDList, VariableBinding[] bindings, String sourceIp, int pduType,
			HashMap<String, String> v1TrapDetails) {

		logger.info("Checking match for OID from mapping file");
		
		try {
			if (pduType == PDU.V1TRAP) {

				String enterpriseOID = v1TrapDetails.get("enterprise_oid");
				int genricTrap = Integer.parseInt(v1TrapDetails.get("generic_trap"));
				int specificTrap = Integer.parseInt(v1TrapDetails.get("specific_trap"));
			

				if (genricTrap == 6) {
					
					String OIDVal = enterpriseOID + ".0." + specificTrap;
					boolean oidIsPresent = false;
					logger.info("v1 enterprise specific match for OID value=."+OIDVal);
					// Check for OId match in Existing vendorOIDmapping hashmap.
					for (int i = 0; i < OIDConfigMapping.size(); i++) {

						oidIsPresent = OIDConfigMapping.containsKey(OIDVal.trim() + "_" + i);
						// If OID match is found, get vendor details to forward
						// V2
						// trap.
						if (oidIsPresent) {
							logger.info("V1 OID match SUCCESS in mapping for oid value=" + OIDVal);
							getTrapDetails(pduType, v1TrapDetails, OIDVal, bindings, sourceIp, i);
						}
					}

					if (!oidIsPresent) {
						logger.info("no vendor match found in mapping for v1 trap oid value =" + OIDVal);
					}
				}
				
				if(specificTrap == 0){
					genricTrap = genricTrap +1;
					String OIDVal = enterpriseOID + "." + genricTrap;
					boolean oidIsPresent = false;
					logger.info("v1 generic trap check for oid value="+OIDVal);	
					// Check for OId match in Existing vendorOIDmapping hashmap.
					for (int i = 0; i < OIDConfigMapping.size(); i++) {

						oidIsPresent = OIDConfigMapping.containsKey(OIDVal.trim() + "_" + i);
						// If OID match is found, get vendor details to forward
						// V2
						// trap.
						if (oidIsPresent) {
							logger.info("V1 OID match SUCCESS in mapping for oid value=" + OIDVal);
							getTrapDetails(pduType, v1TrapDetails, OIDVal, bindings, sourceIp, i);
						}
					}

					if (!oidIsPresent) {
						logger.info("no vendor match found in mapping for v1 trap oid value =" + OIDVal);
					}
					
				}
				
				
			} else {

				for (int j = 0; j < trapOIDList.size(); j++) {
					String OIDVal = trapOIDList.get(j);
					// logger.info("oid vl="+OIDVal);
					boolean oidIsPresent = false;

					// Check for OId match in Existing vendorOIDmapping hashmap.
					for (int i = 0; i < OIDConfigMapping.size(); i++) {

						oidIsPresent = OIDConfigMapping.containsKey(OIDVal.trim() + "_" + i);
						// If OID match is found, get vendor details to forward
						// V2
						// trap.
						if (oidIsPresent) {
							logger.info("OID match SUCCESS in hp mapping for oid value=" + OIDVal);
							getTrapDetails(pduType, v1TrapDetails, OIDVal, bindings, sourceIp, i);
						}
					}

					if (!oidIsPresent) {
						logger.info("no vendor match found in hp mapping for oid value =" + OIDVal);
					}
				}

			}

		} catch (Exception ex) {
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			ex.printStackTrace(pw);

			String errorString = sw.toString();
			logger.info(errorString);
		}

	}

	/**
	 * Get trap details for forwarding trap for HP.
	 * 
	 * @param oidVal
	 * @param bindings
	 * @param sourceIp
	 * @param index
	 */
	private void getTrapDetails(int pduType, HashMap<String, String> v1TrapDetails, String oidVal,
			VariableBinding[] bindings, String sourceIp, int index) {

		try {

			String vendorDetail[] = OIDConfigMapping.get(oidVal + "_" + index).split("=@=");

			String destinationName = vendorDetail[0];
			String vendorIP = vendorDetail[1];
			String vendorPort = vendorDetail[2];
			String trapType = vendorDetail[3];

			logger.info("HP forward trap details:- destination ip=" + vendorIP + " destination port=" + vendorPort
					+ " type=" + trapType);

			SNMPSender trapV2 = new SNMPSender();

			if (vendorIP == null || trapType == null) {
				logger.info("Vendor info is not found or null");
				return;
			}

			// Check if trap is custom then send the custom trap to HP.
			if (trapType.equalsIgnoreCase("custom")) {
				if (pduType == PDU.V1TRAP) {
					trapV2.sendSnmpV1TrapToHP(vendorIP, vendorPort, bindings, sourceIp, v1TrapDetails);
				} else {
					trapV2.ForwardTrap2HP(vendorIP, vendorPort, bindings, sourceIp);
				}
			} else {
				if (pduType == PDU.V1TRAP) {
					trapV2.sendSnmpV1TrapToSevOne(vendorIP, vendorPort, bindings, sourceIp, v1TrapDetails);
				} else {
					enableIPTables(sourceIp, vendorIP, vendorPort);
					trapV2.ForwardTrap2SevOne(vendorIP, vendorPort, bindings, sourceIp);
					disableIPTables(sourceIp, vendorIP, vendorPort);
				}
			}
		} catch (Exception ex) {
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			ex.printStackTrace(pw);

			String errorString = sw.toString();
			logger.info(errorString);
		}
	}

	/**
	 * This method is for making entry in linux iptables to do IP spoofing.
	 * 
	 * @param sourceIP
	 * @param destinationIP
	 * @param destinationPort
	 */
	private void enableIPTables(String sourceIP, String destinationIP, String destinationPort) {

		// iptables -t nat -A POSTROUTING -d $TRAP_RECEIVER -p udp --dport 162
		// -j SNAT --to $SRC
		String command = "iptables -t nat -A POSTROUTING -d " + destinationIP + " -p udp --dport " + destinationPort
				+ " -j SNAT --to " + sourceIP;

		logger.info(" iptable insert cmd =" + command);

		Process p;
		try {

			p = Runtime.getRuntime().exec(new String[] { "/bin/bash", "-c", command });
			p.waitFor();

		} catch (Exception ex) {
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			ex.printStackTrace(pw);

			String errorString = sw.toString();
			logger.info(errorString);
		}
	}

	/**
	 * This method is for removing entry from linux iptables. For SevOne entry
	 * in iptable will be made and removed afterwards.
	 * 
	 * @param sourceIP
	 * @param destinationIP
	 * @param destinationPort
	 */
	private void disableIPTables(String sourceIP, String destinationIP, String destinationPort) {

		// iptables -t nat -D POSTROUTING -d ${TRAP_RECEIVER} -p udp --dport 162
		// -j SNAT --to $SRC
		String command = "iptables -t nat -D POSTROUTING -d " + destinationIP + " -p udp --dport " + destinationPort
				+ " -j SNAT --to " + sourceIP;
		logger.info(" iptable cmd =" + command);

		Process p;
		try {
			p = Runtime.getRuntime().exec(new String[] { "/bin/bash", "-c", command });
			p.waitFor();
		} catch (Exception ex) {
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			ex.printStackTrace(pw);

			String errorString = sw.toString();
			logger.info(errorString);
		}

	}

	/**
	 * This method forwards trap to SevOne if OID match is successful.
	 * 
	 * @param destinationIPVal
	 * @param destinationPortVal
	 * @param trapBindingsList
	 * @param sourceDeviceIp
	 */
	public void ForwardTrap2SevOne(String destinationIPVal, String destinationPortVal,
			VariableBinding[] trapBindingsList, String sourceDeviceIp) {

		try {

			// Create Transport Mapping
			TransportMapping transport = new DefaultUdpTransportMapping();
			transport.listen();
			String community = configValues.get("community");

			// Create Target for V2 trap
			CommunityTarget cTarget = new CommunityTarget();
			cTarget.setCommunity(new OctetString(community));
			cTarget.setVersion(SnmpConstants.version2c);
			cTarget.setAddress(new UdpAddress(destinationIPVal + "/" + destinationPortVal));
			cTarget.setRetries(3);
			cTarget.setTimeout(5000);

			// Create PDU for V2
			PDU pdu = new PDU();

			// Add all bindings which was received from trap.
			pdu.addAll(trapBindingsList);
			sevOneForwardCount++;

			logger.info("SevOne trap count=" + sevOneForwardCount);
			logger.info("========Sending trap to SevOne with PDU=======================" + pdu.toString());

			// Send the PDU
			pdu.setType(PDU.TRAP);
			
			Snmp snmp = new Snmp(transport);
			snmp.send(pdu, cTarget);
			snmp.close();

		} catch (Exception ex) {
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			ex.printStackTrace(pw);

			String errorString = sw.toString();
			logger.info(errorString);
		}

	}

	/**
	 * This method forwards trap to HP NNM when OID is match is successful.
	 * 
	 * @param destinationIPVal
	 * @param destinationPortVal
	 * @param trapBindingsList
	 * @param sourceDeviceIp
	 */
	public void ForwardTrap2HP(String destinationIPVal, String destinationPortVal, VariableBinding[] trapBindingsList,
			String sourceDeviceIp) {

		String deviceName = "";
		String circiutId = "";
		try {

			// Check for OId match in Existing vendorOIDmapping hashmap.
			for (int i = 0; i < deviceInfoMapping.size(); i++) {

				boolean ipMatch = deviceInfoMapping.containsKey(sourceDeviceIp.trim() + "_" + i);
				if (ipMatch) {
					logger.info("device info match SUCCESS in mapping file for ip value=" + sourceDeviceIp);
					String strVal = deviceInfoMapping.get(sourceDeviceIp.trim() + "_" + i);

					logger.info("device info str value=" + strVal);
					String arr[] = strVal.split("=@=");

					deviceName = arr[0];
					circiutId = arr[1];
					break;
				}
			}

			if (deviceName.isEmpty()) {
				logger.info("No device info found. Not forwarding to HP");
				return;
			}

			String deviceIpStr = deviceName + "-?-" + sourceDeviceIp + "-?-" + circiutId;

			// Create Transport Mapping
			TransportMapping transport = new DefaultUdpTransportMapping();
			transport.listen();
			String community = configValues.get("community");

			// Create Target for V2 trap
			CommunityTarget cTarget = new CommunityTarget();
			cTarget.setCommunity(new OctetString(community));
			cTarget.setVersion(SnmpConstants.version2c);
			cTarget.setAddress(new UdpAddress(destinationIPVal + "/" + destinationPortVal));
			cTarget.setRetries(3);
			cTarget.setTimeout(5000);

			// Create PDU for V2
			PDU pdu = new PDU();

			// Add all bindings which was received from trap.
			pdu.addAll(trapBindingsList);

			// IpAddress addr = new IpAddress(sourceDeviceIp);
			String customOID = configValues.get("custom_oid");
			pdu.add(new VariableBinding(new OID(customOID), new OctetString(deviceIpStr)));

			pdu.setType(PDU.TRAP);
			hpForwardCount++;
			logger.info("HP trap count=" + hpForwardCount);
			logger.info("========Sending trap to HP with PDU=======================" + pdu.toString());

			// Send the PDU
			Snmp snmp = new Snmp(transport);
			snmp.send(pdu, cTarget);
			snmp.close();

		} catch (Exception ex) {
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			ex.printStackTrace(pw);

			String errorString = sw.toString();
			logger.info(errorString);
		}

	}

	/**
	 * This methods sends the V1 trap to the HP/
	 */
	public void sendSnmpV1TrapToHP(String destinationIPVal, String destinationPortVal,
			VariableBinding[] trapBindingsList, String sourceDeviceIp, HashMap<String, String> v1TrapDetails) {

		String deviceName = "";
		String circiutId = "";
		try {

			// Check for OId match in Existing vendorOIDmapping hashmap.
			for (int i = 0; i < deviceInfoMapping.size(); i++) {

				boolean ipMatch = deviceInfoMapping.containsKey(sourceDeviceIp.trim() + "_" + i);
				if (ipMatch) {
					logger.info("device info match SUCCESS in mapping file for ip value=" + sourceDeviceIp);
					String strVal = deviceInfoMapping.get(sourceDeviceIp.trim() + "_" + i);

					logger.info("device info str value=" + strVal);
					String arr[] = strVal.split("=@=");

					deviceName = arr[0];
					circiutId = arr[1];
					break;
				}
			}

			if (deviceName.isEmpty()) {
				logger.info("No device info found. Not forwarding to HP");
				return;
			}

			String deviceIpStr = deviceName + "-?-" + sourceDeviceIp + "-?-" + circiutId;

			// Create Transport Mapping
			TransportMapping transport = new DefaultUdpTransportMapping();
			transport.listen();
			String community = configValues.get("community");

			// Create Target for V2 trap
			CommunityTarget cTarget = new CommunityTarget();
			cTarget.setCommunity(new OctetString(community));
			cTarget.setVersion(SnmpConstants.version1);
			cTarget.setAddress(new UdpAddress(destinationIPVal + "/" + destinationPortVal));
			cTarget.setRetries(3);
			cTarget.setTimeout(5000);

			// Create PDU for V2
			PDUv1 pdu = new PDUv1();

			// Add all bindings which was received from trap.
			pdu.addAll(trapBindingsList);

			// IpAddress addr = new IpAddress(sourceDeviceIp);
			String customOID = configValues.get("custom_oid");
			pdu.add(new VariableBinding(new OID(customOID), new OctetString(deviceIpStr)));

			pdu.setType(PDU.V1TRAP);

			String enterpriseOID = v1TrapDetails.get("enterprise_oid");
			int genricTrap = Integer.parseInt(v1TrapDetails.get("generic_trap"));
			int specificTrap = Integer.parseInt(v1TrapDetails.get("specific_trap"));
			String agentAddress = v1TrapDetails.get("agent_address");

			pdu.setEnterprise(new OID(enterpriseOID));
			pdu.setGenericTrap(genricTrap);
			pdu.setSpecificTrap(specificTrap);
			pdu.setAgentAddress(new IpAddress(agentAddress));

			hpForwardCount++;
			logger.info("HP trap count=" + hpForwardCount);
			logger.info("========Sending V1 trap to HP with PDU=======================" + pdu.toString());

			// Send the PDU
			Snmp snmp = new Snmp(transport);
			snmp.send(pdu, cTarget);
			snmp.close();

		} catch (Exception ex) {
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			ex.printStackTrace(pw);

			String errorString = sw.toString();
			logger.info(errorString);
		}

	}

	/**
	 * This methods sends the V1 trap to the HP/
	 */
	public void sendSnmpV1TrapToSevOne(String destinationIPVal, String destinationPortVal,
			VariableBinding[] trapBindingsList, String sourceDeviceIp, HashMap<String, String> v1TrapDetails) {

		try {

			// Create Transport Mapping
			TransportMapping transport = new DefaultUdpTransportMapping();
			transport.listen();
			String community = configValues.get("community");

			// Create Target for V2 trap
			CommunityTarget cTarget = new CommunityTarget();
			cTarget.setCommunity(new OctetString(community));
			cTarget.setVersion(SnmpConstants.version1);
			cTarget.setAddress(new UdpAddress(destinationIPVal + "/" + destinationPortVal));
			cTarget.setRetries(3);
			cTarget.setTimeout(5000);

			// Create PDU for V2
			PDUv1 pdu = new PDUv1();

			// Add all bindings which was received from trap.
			pdu.addAll(trapBindingsList);
			sevOneForwardCount++;

			

			// Send the PDU
			pdu.setType(PDU.V1TRAP);
			String enterpriseOID = v1TrapDetails.get("enterprise_oid");
			int genricTrap = Integer.parseInt(v1TrapDetails.get("generic_trap"));
			int specificTrap = Integer.parseInt(v1TrapDetails.get("specific_trap"));
			String agentAddress = v1TrapDetails.get("agent_address");

			pdu.setEnterprise(new OID(enterpriseOID));
			pdu.setGenericTrap(genricTrap);
			pdu.setSpecificTrap(specificTrap);
			pdu.setAgentAddress(new IpAddress(agentAddress));

			logger.info("SevOne trap count=" + sevOneForwardCount);
			logger.info("========Sending V1 trap to SevOne with PDU=======================" + pdu.toString());
			
			Snmp snmp = new Snmp(transport);
			snmp.send(pdu, cTarget);
			snmp.close();

		} catch (Exception ex) {
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			ex.printStackTrace(pw);

			String errorString = sw.toString();
			logger.info(errorString);
		}

	}

}