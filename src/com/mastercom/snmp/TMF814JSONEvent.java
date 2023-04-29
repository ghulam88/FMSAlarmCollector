package com.mastercom.snmp;


public class TMF814JSONEvent {

	// String alarmId; // Ignore - RP confirmed
	private String id; // notificationId
	private long eventId; // Uses nanoTime to generate unique ID
	private String eventType; //Mapped to Event Type of StructuredEvent
	private String objectType; // objectType
	private String vendor; // To be hard coded based on where it is coming from.
	private String emsName; // EMS
	private String severity; // perceivedSeverity
	private String fullName; // derived from objectName as <<CTP>>_<<PTP till /port=
	// (including)>>_<<ManagedElement>>_<<EMS>>
	// Ex. objectName for reference -
	// objectName={EMS=TejEMSEAST-2}{ManagedElement=172.22.42.193}{PTP=/shelf=1/slot=1/port=19/rate=25/nativeEMSName=STM1-1-1-19/MediumType=1}{CTP=/sts3c_au4-j=1/vt2_tu12-k=1-l=5-m=2}
	// So fullName would be -
	// /sts3c_au4-j=1/vt2_tu12-k=1-l=5-m=2_/shelf=1/slot=1/port=19_172.22.42.193_TejEMSEAST-2
	// String modifyTime; // Can't be determined //Ignore - RP confirmed
	private String standardName; // Also called SAN (Standard Alarm Name) ?? Using probableCause value, this
	// value is generated. Ex: TU-AIS is always marked as TU_AIS. probableCause -
	// Link Down is marked as ETHERNET_LINK_DOWN or VCG_LINK_DOWN depending on
	// layerRate in the case of Tejas but in the case of CIENA, it looks different.
	// Similarly, probableCause TX_DEGRADE is marked as LOW_TX_POWER.
	// String sysUpTime; // ?? This is taken from the standard system-mib MIB, so
	// can't be populated //Ignore - RP confirmed
	private String probableCauseQualifier; // probableCauseQualifier
	private String nativeProbableCause;
	private String layerRate; // layerRate
	// String MoID; // ?? //Ignore - RP confirmed
	private String type; // In all the cases in te events dump, I see only communicationsAlarm as the
					// value
	private String additionalText; // additionalText
	private String probableCause; // probableCause - resolve from the generated values
	// String state; // Ignore - RP confirmed
	private String emsTime; // EMSReportTime from additionalInfo
	// String creationTime; //Ignore ??
	// String snmpTrapOID; //Ignore - RP confirmed
	private String neTime;
	private String nbiEmsName;
	private String nativeEMSName;
	private String additionalInformation; // different from additionalInfo field. Request from Ritesh on 30/09/22
	private String aid; // Request from Ritesh on 30/09/22

	public String getLayerRate() {
		return layerRate;
	}

	public void setLayerRate(String layerRate) {
		this.layerRate = layerRate;
	}

	public String getProbableCauseQualifier() {
		return probableCauseQualifier;
	}

	public void setProbableCauseQualifier(String probableCauseQualifier) {
		this.probableCauseQualifier = probableCauseQualifier;
	}

	public String getStandardName() {
		return standardName;
	}

	public void setStandardName(String standardName) {
		this.standardName = standardName;
	}

	public String getFullName() {
		return fullName;
	}

	public void setFullName(String fullName) {
		this.fullName = fullName;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getSeverity() {
		return severity;
	}

	public void setSeverity(String severity) {
		this.severity = severity;
	}

	public String getAdditionalText() {
		return additionalText;
	}

	public void setAdditionalText(String additionalText) {
		this.additionalText = additionalText;
	}

	public String getEmsName() {
		return emsName;
	}

	public void setEmsName(String emsName) {
		this.emsName = emsName;
	}

	public String getVendor() {
		return vendor;
	}

	public void setVendor(String vendor) {
		this.vendor = vendor;
	}

	public String getObjectType() {
		return objectType;
	}

	public void setObjectType(String objectType) {
		this.objectType = objectType;
	}

	public String getEmsTime() {
		return emsTime;
	}

	public void setEmsTime(String emsTime) {
		this.emsTime = emsTime;
	}

	public long getEventId() {
		return eventId;
	}

	public void setEventId(long l) {
		this.eventId = l;
	}

	public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getProbableCause() {
		return probableCause;
	}

	public void setProbableCause(String probableCause) {
		this.probableCause = probableCause;
	}

	public String getNeTime() {
		return neTime;
	}

	public void setNeTime(String neTime) {
		this.neTime = neTime;
	}

    public String getNbiEmsName() {
        return nbiEmsName;
    }

    public void setNbiEmsName(String nbiEmsName) {
        this.nbiEmsName = nbiEmsName;
    }

    public String getNativeEMSName() {
        return nativeEMSName;
    }

    public void setNativeEMSName(String nativeEMSName) {
        this.nativeEMSName = nativeEMSName;
    }

    public String getNativeProbableCause() {
        return nativeProbableCause;
    }

    public void setNativeProbableCause(String nativeProbableCause) {
        this.nativeProbableCause = nativeProbableCause;
    }

    public String getAdditionalInformation() {
        return additionalInformation;
    }

    public void setAdditionalInformation(String additionalInformation) {
        this.additionalInformation = additionalInformation;
    }

    public String getAid() {
        return aid;
    }

    public void setAid(String aid) {
        this.aid = aid;
    }
}
