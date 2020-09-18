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
                    //clientIn = socket.getInputStream();
                    //clientOut = socket.getOutputStream();

                    InetSocketAddress destination = new InetSocketAddress(socket.getInetAddress(), session.remotePort & 0xFFFF);

                    Log.e("info", "CONNECTION:  "+socket.getInetAddress()+":"+session.remotePort+"   -HOST-   "+session.remoteHost);


                    //EVERYTHING PAST THIS WILL BE YOUR PROXY OR WHAT EVER YOU WISH TO DO...

                    server = new Socket();
                    server.bind(new InetSocketAddress(0));
                    service.protect(server);
                    server.setSoTimeout(5000);
                    server.connect(destination, 5000);
                    //serverIn = server.getInputStream();
                    //serverOut = server.getOutputStream();


                    new Thread(new Runnable(){
                        @Override
                        public void run(){
                            forwardData(server, socket);
                        }
                    }).start();

                    forwardData(socket, server);

                }
            }catch(Exception e){
                //e.printStackTrace();
            }finally{
                quickClose(socket);
                quickClose(server);
            }
        }

        public void forwardData(Socket inputSocket, Socket outputSocket){
            try{
                InputStream inputStream = inputSocket.getInputStream();
                try{
                    OutputStream outputStream = outputSocket.getOutputStream();
                    try{
                        byte[] buffer = new byte[4096];//4096
                        int read;
                        do{
                            read = inputStream.read(buffer);
                            if(read > 0){
                                outputStream.write(buffer, 0, read);
                                if(inputStream.available() < 1){
                                    outputStream.flush();
                                }
                            }
                        }while(read >= 0);
                    }catch(Exception e){
                    }finally{
                        if(!outputSocket.isOutputShutdown()){
                            outputSocket.shutdownOutput();
                        }
                    }
                }finally{
                    if(!inputSocket.isInputShutdown()){
                        inputSocket.shutdownInput();
                    }
                }
            }catch(IOException e){
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
