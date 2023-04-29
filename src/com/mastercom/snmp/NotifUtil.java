package com.mastercom.snmp;

import java.util.concurrent.atomic.AtomicLong;


public class NotifUtil {
	 private static AtomicLong id = new AtomicLong(System.nanoTime());

	   public static String getObjectType(String objectType) {
	        if (objectType != null && !(objectType.isEmpty())) {
	            if (objectType.equals("OT_CONNECTION_TERMINATION_POINT"))
	                return "CTP";
	            else if (objectType.equals("OT_PHYSICAL_TERMINATION_POINT"))
	                return "PTP";
	            else if (objectType.equals("OT_EQUIPMENT"))
	                return "Equipment";
	            else if (objectType.equals("OT_MANAGED_ELEMENT"))
	                return "ME";
	            else if (objectType.equals("OT_TOPOLOGICAL_LINK"))
	                return "TOPOLOGICAL LINK";
	            else if (objectType.equals("OT_EMS"))
	                return "EMS";
	        }
	        return "UNKNOWN";
	    }
	
	   public static String getSeverity(String severity) {
	        //LOG.debug("In getSeverity method - Severity = {}", severity);
	        if (severity != null && !(severity.isEmpty())) {
	            if (severity.equals("PS_MAJOR"))
	                return "MAJOR";
	            if (severity.equals("PS_MINOR"))
	                return "MINOR";
	            if (severity.equals("PS_CRITICAL"))
	                return "CRITICAL";
	            if (severity.equals("PS_WARNING"))
	                return "WARNING";
	            if (severity.equals("PS_CLEARED"))
	                return "CLEAR";
	        }
	        return "UNKNOWN";
	    }
	   public static long getEventId() {
	        return id.incrementAndGet();
	    }
}
