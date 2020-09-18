package vpntosocket.shadowrouter.org.vpntosocket.VPN;

import android.util.SparseArray;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

import vpntosocket.shadowrouter.org.vpntosocket.VPN.dns.DnsPacket;
import vpntosocket.shadowrouter.org.vpntosocket.VPN.tcpip.CommonMethods;
import vpntosocket.shadowrouter.org.vpntosocket.VPN.tcpip.IPHeader;
import vpntosocket.shadowrouter.org.vpntosocket.VPN.tcpip.UDPHeader;

public class DnsProxy extends Thread {

    private SparseArray<QueryState> queryArray = new SparseArray<>();
    private final long QUERY_TIMEOUT_NS = 10*1000000000L;
    private DatagramSocket socket;
    private short queryId;

    private VPNService service;

    public DnsProxy(VPNService service){
        this.service = service;
        try{
            socket = new DatagramSocket(0);
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    private class QueryState {
        public short clientQueryId, clientPort, remotePort;
        public int clientAddress, remoteAddress;
        public long queryNanoTime;
    }

    @Override
    public void run(){
        try{
            byte[] RECEIVE_BUFFER = new byte[2000];
            IPHeader ipHeader = new IPHeader(RECEIVE_BUFFER, 0);
            ipHeader.Default();
            UDPHeader udpHeader = new UDPHeader(RECEIVE_BUFFER, 20);

            ByteBuffer dnsBuffer = ByteBuffer.wrap(RECEIVE_BUFFER);
            dnsBuffer.position(28);
            dnsBuffer = dnsBuffer.slice();

            DatagramPacket packet = new DatagramPacket(RECEIVE_BUFFER, 28, RECEIVE_BUFFER.length-28);

            while(socket != null && !socket.isClosed()){
                packet.setLength(RECEIVE_BUFFER.length-28);
                socket.receive(packet);

                dnsBuffer.clear();
                dnsBuffer.limit(packet.getLength());
                try{
                    DnsPacket dnsPacket = DnsPacket.FromBytes(dnsBuffer);
                    if(dnsPacket != null){
                        OnDnsResponseReceived(ipHeader, udpHeader, dnsPacket);
                    }
                }catch(Exception e){
                    e.printStackTrace();
                }
            }
        }catch(Exception e){
            e.printStackTrace();
        }finally{
            socket.close();
        }
    }


    private void OnDnsResponseReceived(IPHeader ipHeader, UDPHeader udpHeader, DnsPacket dnsPacket){
        QueryState state;
        synchronized(queryArray){
            state = queryArray.get(dnsPacket.Header.ID);
            if(state != null){
                queryArray.remove(dnsPacket.Header.ID);
            }
        }

        if(state != null){
            dnsPacket.Header.setID(state.clientQueryId);
            ipHeader.setSourceIP(state.remoteAddress);
            ipHeader.setDestinationIP(state.clientAddress);
            ipHeader.setProtocol(IPHeader.UDP);
            ipHeader.setTotalLength(20+8+dnsPacket.Size);
            udpHeader.setSourcePort(state.remotePort);
            udpHeader.setDestinationPort(state.clientPort);
            udpHeader.setTotalLength(8+dnsPacket.Size);

            service.sendUDPPacket(ipHeader, udpHeader);
        }
    }

    private void clearExpiredQueries(){
        long now = System.nanoTime();
        for(int i = queryArray.size()-1; i >= 0; i--){
            QueryState state = queryArray.valueAt(i);
            if((now-state.queryNanoTime) > QUERY_TIMEOUT_NS){
                queryArray.removeAt(i);
            }
        }
    }

    public void onDnsRequestReceived(IPHeader ipHeader, UDPHeader udpHeader, DnsPacket dnsPacket){
        QueryState state = new QueryState();
        state.clientQueryId = dnsPacket.Header.ID;
        state.queryNanoTime = System.nanoTime();
        state.clientAddress = ipHeader.getSourceIP();
        state.clientPort = udpHeader.getSourcePort();
        state.remoteAddress = ipHeader.getDestinationIP();
        state.remotePort = udpHeader.getDestinationPort();

        queryId++;
        dnsPacket.Header.setID(queryId);

        synchronized(queryArray){
            clearExpiredQueries();
            queryArray.put(queryId, state);
        }

        InetSocketAddress remoteAddress = new InetSocketAddress(CommonMethods.ipIntToInet4Address(state.remoteAddress), state.remotePort);
        DatagramPacket packet = new DatagramPacket(udpHeader.m_Data, udpHeader.m_Offset + 8, dnsPacket.Size);
        packet.setSocketAddress(remoteAddress);

        try{
            service.protect(socket);
            socket.send(packet);
        }catch(IOException e){
            e.printStackTrace();
        }
    }
}
