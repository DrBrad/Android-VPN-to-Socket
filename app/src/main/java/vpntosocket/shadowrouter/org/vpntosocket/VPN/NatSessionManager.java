package vpntosocket.shadowrouter.org.vpntosocket.VPN;

import android.util.SparseArray;

import vpntosocket.shadowrouter.org.vpntosocket.VPN.tcpip.CommonMethods;

public class NatSessionManager {

    private static int MAX_SESSION_COUNT = 60;
    private static long SESSION_TIMEOUT_NS = 60*1000000000L;
    private static SparseArray<NatSession> sessions = new SparseArray<>();

    public static NatSession getSession(int portKey){
        NatSession session = sessions.get(portKey);
        if(session != null){
            session.lastNanoTime = System.nanoTime();
        }
        return sessions.get(portKey);
    }

    public static void clearExpiredSessions(){
        long now = System.nanoTime();
        for(int i = sessions.size()-1; i >= 0; i--){
            NatSession session = sessions.valueAt(i);
            if(now-session.lastNanoTime > SESSION_TIMEOUT_NS){
                sessions.removeAt(i);
            }
        }
    }

    public static NatSession createSession(int portKey, int remoteIP, short remotePort){
        if(sessions.size() > MAX_SESSION_COUNT){
            clearExpiredSessions();
        }

        NatSession session = new NatSession();
        session.lastNanoTime = System.nanoTime();
        session.remoteAddress = remoteIP;
        session.remotePort = remotePort;

        if(session.remoteHost == null){
            session.remoteHost = CommonMethods.ipIntToString(remoteIP);
        }
        sessions.put(portKey, session);
        return session;
    }

    public static class NatSession {

        public int remoteAddress;
        public short remotePort;
        public String remoteHost;
        public int bytesSent, packetsSent;
        public long lastNanoTime;
    }
}
