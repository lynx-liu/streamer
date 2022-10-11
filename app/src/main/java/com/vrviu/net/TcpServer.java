package com.vrviu.net;

import android.os.Looper;
import android.util.Log;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public abstract class TcpServer extends Thread{
    private final int port;
    private ServerSocket serverSocket;
    public TcpServer(final int port){
        this.port=port;
    }

    @Override
    public void run() {
        super.run();
        Looper.prepare();
        try {
            serverSocket = new ServerSocket(port);
            while (!isInterrupted()) {
                Socket client = serverSocket.accept();
                onAccept(client);
                try{
                    client.close();
                }catch (IOException e) {
                    Log.d("llx",e.toString());
                }
            }
        }catch (IOException e) {
            Log.d("llx",e.toString());
        } finally {
            try {
                serverSocket.close();
            } catch (Exception e) {
                Log.d("llx",e.toString());
            }
        }
        Log.d("llx","TcpServer exit");
        Looper.loop();
    }

    public abstract void onAccept(Socket client);
}
