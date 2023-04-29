package com.mastercom.snmp;

import org.snmp4j.CommunityTarget;
import org.snmp4j.PDU;
import org.snmp4j.Snmp;
import org.snmp4j.TransportMapping;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.smi.Address;
import org.snmp4j.smi.GenericAddress;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.OctetString;
import org.snmp4j.smi.VariableBinding;
import org.snmp4j.transport.DefaultUdpTransportMapping;

public class SnmpTrapSender {
    public static final String community = "public";
    public static final String trapOid = "1.3.6.1.4.1.566.1.1.1";
    public static final String ipAddress = "udp:127.0.0.1/162";

    public static void main(String[] args) {
        try {
            // Create TransportMapping and Listen
            TransportMapping transport = new DefaultUdpTransportMapping();
            transport.listen();

            // Create Target
            CommunityTarget cTarget = new CommunityTarget();
            cTarget.setCommunity(new OctetString(community));
            cTarget.setVersion(SnmpConstants.version2c);
            cTarget.setAddress(GenericAddress.parse(ipAddress));
            cTarget.setRetries(2);
            cTarget.setTimeout(1000);

            // Create PDU for V2
            PDU pdu = new PDU();

            // setting the OID and value for trap
            OID oid = new OID(trapOid);
            VariableBinding vb = new VariableBinding(oid);
            vb.setVariable(new OctetString("Trap Generated"));
            pdu.add(vb);

            // Send the PDU
            Snmp snmp = new Snmp(transport);
            System.out.println("Sending V2 Trap to " + ipAddress);
            snmp.send(pdu, cTarget);
            snmp.close();
        } catch (Exception e) {
            System.err.println("Error in Sending V2 Trap to " + ipAddress);
            System.err.println("Error: " + e.getMessage());
        }
    }
}

