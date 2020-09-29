package vpntosocket.shadowrouter.org.vpntosocket.VPN;

import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

public class Proxy extends Thread {

    private VPNService service;
    public short port;

    public Proxy(VPNService service, int port){
        this.service = service;
        this.port = (short) port;
    }

    @Override
    public void run(){
        try{
            Socket socket;
            ServerSocket serverSocket = new ServerSocket(port);
            port = (short) (serverSocket.getLocalPort() & 0xFFFF);
            Log.e("info", "VPNtoSocket VPN started on port: "+serverSocket.getLocalPort());

            while((socket = serverSocket.accept()) != null && !isInterrupted()){
                (new Tunnel(socket)).start();
            }
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    public class Tunnel extends Thread {

        private Socket socket, server;
        //private InputStream clientIn, serverIn;
        //private OutputStream clientOut, serverOut;

        public Tunnel(Socket socket){
            this.socket = socket;
        }

        @Override
        public void run(){
            try{
                NatSessionManager.NatSession session = NatSessionManager.getSession((short) socket.getPort());
                if(session != null){
                    clientIn = socket.getInputStream();
                    clientOut = socket.getOutputStream();

                    InetSocketAddress destination = new InetSocketAddress(socket.getInetAddress(), session.remotePort & 0xFFFF);

                    Log.e("info", "CONNECTION:  "+socket.getInetAddress()+":"+session.remotePort+"   -HOST-   "+session.remoteHost);


                    //EVERYTHING PAST THIS WILL BE YOUR PROXY OR WHAT EVER YOU WISH TO DO...

                    server = new Socket();
                    server.bind(new InetSocketAddress(0));
                    service.protect(server);
                    server.setSoTimeout(5000);
                    server.connect(destination, 5000);
                    serverIn = server.getInputStream();
                    serverOut = server.getOutputStream();

                    relay();

                }
            }catch(Exception e){
                //e.printStackTrace();
            }finally{
                quickClose(socket);
                quickClose(server);
            }
        }

        public void relay(){
            Thread thread = new Thread(new Runnable(){
                @Override
                public void run(){
                    try{
                        while(!socket.isClosed() && !server.isClosed() && !socket.isInputShutdown() && !server.isOutputShutdown() && !isInterrupted()){
                            byte[] buffer = new byte[4096];
                            int length;

                            try{
                                length = clientIn.read(buffer);
                            }catch(InterruptedIOException e){
                                length = 0;
                            }catch(IOException e){
                                length = -1;
                            }catch(Exception e){
                                length = -1;
                            }

                            if(length < 0){
                                socket.shutdownInput();
                                server.shutdownOutput();
                                break;
                            }else if(length > 0){
                                try{
                                    serverOut.write(buffer, 0, length);
                                    serverOut.flush();
                                }catch(Exception e){
                                }
                            }
                        }
                    }catch(Exception e){
                    }
                }
            });

            thread.start();

            try{
                byte[] buffer = new byte[4096];
                int length;

                while(!socket.isClosed() && !server.isClosed() && !server.isInputShutdown() && !socket.isOutputShutdown() && !thread.isInterrupted()){
                    try{
                        length = serverIn.read(buffer);
                    }catch(InterruptedIOException e){
                        length = 0;
                    }catch(IOException e){
                        length = -1;
                    }catch(Exception e){
                        length = -1;
                    }

                    if(length < 0){
                        server.shutdownInput();
                        socket.shutdownOutput();
                        break;
                    }else if(length > 0){
                        try{
                            clientOut.write(buffer, 0, length);
                            clientOut.flush();
                        }catch(Exception e){
                        }
                    }
                }

                thread.interrupt();
            }catch(Exception e){
            }
        }

        public void quickClose(Socket socket){
            try{
                if(!socket.isOutputShutdown()){
                    socket.shutdownOutput();
                }
                if(!socket.isInputShutdown()){
                    socket.shutdownInput();
                }

                socket.close();
            }catch(Exception e){
                //e.printStackTrace();
            }
        }
    }
}
