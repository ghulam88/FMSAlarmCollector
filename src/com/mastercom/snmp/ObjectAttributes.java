package com.mastercom.snmp;

import java.util.ArrayList;

public class ObjectAttributes {

	private String status;
	private String syntax;
	private String maxAccess;
	private String description;
	private String oid;
	private String type;
	private ArrayList<String> objectsList;
	
	public ArrayList<String> getObjectsList() {
		return objectsList;
	}

	public void setObjectsList(ArrayList<String> objectsList) {
		this.objectsList = objectsList;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public String getOid() {
		return oid;
	}

	public void setOid(String oid) {
		this.oid = oid;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public String getSyntax() {
		return syntax;
	}

	public void setSyntax(String syntax) {
		this.syntax = syntax;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getMaxAccess() {
		return maxAccess;
	}

	public void setMaxAccess(String maxAccess) {
		this.maxAccess = maxAccess;
	}

	
}
