package bio.Server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;

public class ChatHandler implements Runnable {

    private Socket socket;
    private ChatServer chatServer;

    public ChatHandler(Socket socket, ChatServer chatServer) {
        this.socket = socket;
        this.chatServer = chatServer;
    }

    @Override
    public void run() {
        try {
            //添加对象
            chatServer.addClient(socket);

            //读取用户发送的信息
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream())
            );
            String msg = null;
            //必须要读取到换行符
            while ((msg = reader.readLine()) != null){
                String fwdMsg ="客户端" + socket.getPort() + "：" + msg + "\n";
                chatServer.forwardMessage(socket,fwdMsg);
                System.out.print(fwdMsg);

                //检查是否退出
                if(chatServer.readyToQuit(msg)){
                    break;
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }finally {
            try {
                chatServer.removeClient(socket);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}

