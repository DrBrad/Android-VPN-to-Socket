package vpntosocket.shadowrouter.org.vpntosocket.VPN;

import android.content.Intent;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;

import vpntosocket.shadowrouter.org.vpntosocket.VPN.dns.DnsPacket;
import vpntosocket.shadowrouter.org.vpntosocket.VPN.tcpip.CommonMethods;
import vpntosocket.shadowrouter.org.vpntosocket.VPN.tcpip.IPHeader;
import vpntosocket.shadowrouter.org.vpntosocket.VPN.tcpip.TCPHeader;
import vpntosocket.shadowrouter.org.vpntosocket.VPN.tcpip.UDPHeader;

public class VPNService extends VpnService {

    /*
    TCP IP

    0                                                            32
    +------------------------------------------------------------+
    |      4B       |     4B    |     8B    |        16B         |
    |    Version    |    IHL    |    TOS    |    Total Length    |
    +------------------------------------------------------------+
    |             16B               |      3B     |     13B      |
    |        Identification         |    Flags    |    Offset    |
    +------------------------------------------------------------+
    |     8B    |       8B       |              16B              |
    |    TTL    |    Protocol    |           Checksum            |
    +------------------------------------------------------------+
    |                             32B                            |
    |                       Source Address                       |
    +------------------------------------------------------------+
    |                             32B                            |
    |                    Destination Address                     |
    +------------------------------------------------------------+
    |                             32B                            |
    |                          Options                           |
    +------------------------------------------------------------+
    |                                                            |
    |                            DATA                            |
    |                                                            |
    +------------------------------------------------------------+
    */

    public static Thread vpnThread;
    private ParcelFileDescriptor mInterface;

    private Builder builder = new Builder();

    private int localAddress;
    private FileOutputStream vpnout;

    private DnsProxy dnsProxy;
    private Proxy proxy;

    private byte[] packet;
    private IPHeader ipHeader;
    private TCPHeader tcpHeader;
    private UDPHeader udpHeader;
    private ByteBuffer dnsBuffer;

    private static ArrayList<onStatusChangedListener> statusChangedListeners = new ArrayList<>();

    @Override
    public int onStartCommand(Intent intent, int flags, int startId){
        localAddress = CommonMethods.ipStringToInt("10.8.0.2");

        packet = new byte[20000];
        ipHeader = new IPHeader(packet, 0);
        tcpHeader = new TCPHeader(packet, 20);
        udpHeader = new UDPHeader(packet, 20);
        dnsBuffer = ((ByteBuffer) ByteBuffer.wrap(packet).position(28)).slice();

        proxy = new Proxy(this, 0);
        proxy.start();
        dnsProxy = new DnsProxy(this);
        dnsProxy.start();

        vpnThread = new Thread(new Runnable(){
            @Override
            public void run(){
                mInterface = builder.setSession("VPNtoSocket")
                        .setMtu(20000)
                        .addAddress("10.8.0.2", 32)
                        .addDnsServer("8.8.8.8")
                        .addRoute("0.0.0.0", 0)
                        //.addRoute("172.25.0.1", 16)
                        .establish();

                FileInputStream vpnin = new FileInputStream(mInterface.getFileDescriptor());
                vpnout = new FileOutputStream(mInterface.getFileDescriptor());

                onStatusChanged(true);

                try{
                    int size = 0;
                    while(size != -1 && !vpnThread.isInterrupted()){
                        while((size = vpnin.read(packet)) > 0 && !vpnThread.isInterrupted()){
                            if(dnsProxy.isInterrupted() || proxy.isInterrupted()){ //KILL SWITCH
                                vpnin.close();
                                throw new Exception("Server stopped.");
                            }
                            onIPPacketReceived(ipHeader, size);
                        }
                        Thread.sleep(20);
                    }
                }catch(Exception e){
                    e.printStackTrace();
                }finally{
                    try{
                        vpnin.close();
                        vpnout.close();
                        if(mInterface != null){
                            mInterface.close();
                            mInterface = null;
                        }

                        kill();
                    }catch(Exception e){
                    }
                }
            }

        }, "VPNtoSocket");
        vpnThread.start();

        return super.onStartCommand(intent, flags, startId);//START_STICKY;
    }

    public void onIPPacketReceived(IPHeader ipHeader, int size)throws IOException {
        switch(ipHeader.getProtocol()){
            case IPHeader.TCP:
                TCPHeader tcpHeader = this.tcpHeader;
                tcpHeader.m_Offset = ipHeader.getHeaderLength();

                if(ipHeader.getSourceIP() == localAddress){
                    if(tcpHeader.getSourcePort() == proxy.port){
                        NatSessionManager.NatSession session = NatSessionManager.getSession(tcpHeader.getDestinationPort());

                        if(session != null){
                            ipHeader.setSourceIP(ipHeader.getDestinationIP());
                            tcpHeader.setSourcePort(session.remotePort);
                            ipHeader.setDestinationIP(localAddress);

                            CommonMethods.ComputeTCPChecksum(ipHeader, tcpHeader);
                            vpnout.write(ipHeader.m_Data, ipHeader.m_Offset, size);
                        }else{
                            System.out.printf("NoSession: %s %s\n", ipHeader.toString(), tcpHeader.toString());
                        }

                    }else{
                        int portKey = tcpHeader.getSourcePort();
                        NatSessionManager.NatSession session = NatSessionManager.getSession(portKey);
                        if(session == null || session.remoteAddress != ipHeader.getDestinationIP() || session.remotePort != tcpHeader.getDestinationPort()){
                            session = NatSessionManager.createSession(portKey, ipHeader.getDestinationIP(), tcpHeader.getDestinationPort());
                        }

                        session.lastNanoTime = System.nanoTime();
                        session.packetsSent++;//注意顺序

                        int tcpDataSize = ipHeader.getDataLength()-tcpHeader.getHeaderLength();
                        if(session.packetsSent == 2 && tcpDataSize == 0){
                            return;
                        }

                        if(session.bytesSent == 0 && tcpDataSize > 10){
                            int dataOffset = tcpHeader.m_Offset+tcpHeader.getHeaderLength();
                            String host = HttpHostHeaderParser.parseHost(tcpHeader.m_Data, dataOffset, tcpDataSize);
                            if(host != null){
                                session.remoteHost = host;
                            }else{
                                System.out.printf("No host name found: %s", session.remoteHost);
                            }
                        }

                        ipHeader.setSourceIP(ipHeader.getDestinationIP());
                        ipHeader.setDestinationIP(localAddress);
                        tcpHeader.setDestinationPort(proxy.port);

                        CommonMethods.ComputeTCPChecksum(ipHeader, tcpHeader);
                        vpnout.write(ipHeader.m_Data, ipHeader.m_Offset, size);
                        session.bytesSent += tcpDataSize;
                    }
                }
                break;

            case IPHeader.UDP:
                UDPHeader udpHeader = this.udpHeader;
                udpHeader.m_Offset = ipHeader.getHeaderLength();
                if(ipHeader.getSourceIP() == localAddress && udpHeader.getDestinationPort() == 53){
                    dnsBuffer.clear();
                    dnsBuffer.limit(ipHeader.getDataLength()-8);
                    DnsPacket dnsPacket = DnsPacket.FromBytes(dnsBuffer);
                    if(dnsPacket != null && dnsPacket.Header.QuestionCount > 0){
                        dnsProxy.onDnsRequestReceived(ipHeader, udpHeader, dnsPacket);
                    }
                }
                break;
        }
    }

    public void sendUDPPacket(IPHeader ipHeader, UDPHeader udpHeader){
        try{
            CommonMethods.ComputeUDPChecksum(ipHeader, udpHeader);
            vpnout.write(ipHeader.m_Data, ipHeader.m_Offset, ipHeader.getTotalLength());
        }catch(IOException e){
            e.printStackTrace();
        }
    }

    @Override
    public void onDestroy(){
        kill();
        super.onDestroy();
    }

    public void kill(){
        if(vpnThread != null){
            vpnThread.interrupt();
            vpnThread = null;
        }

        if(proxy != null){
            proxy.interrupt();
            proxy = null;
        }

        if(dnsProxy != null){
            dnsProxy.interrupt();
            dnsProxy = null;
        }
        stopSelf();
        onStatusChanged(false);
    }

    public interface onStatusChangedListener {
        void onStatusChanged(boolean status);
    }

    public static void addOnStatusChangedListener(onStatusChangedListener listener){
        if(!statusChangedListeners.contains(listener)){
            statusChangedListeners.add(listener);
        }
    }

    public static void removeOnStatusChangedListener(onStatusChangedListener listener){
        if(statusChangedListeners.contains(listener)){
            statusChangedListeners.remove(listener);
        }
    }

    private void onStatusChanged(boolean status){
        for(onStatusChangedListener statusChangedListener : statusChangedListeners){
            statusChangedListener.onStatusChanged(status);
        }
    }
}
