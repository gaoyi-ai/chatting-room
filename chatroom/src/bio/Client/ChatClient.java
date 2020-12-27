package bio.Client;

import java.io.*;
import java.net.Socket;

public class ChatClient {
    private String DEFAULT_SERVER_HOST = "127.0.0.1";
    private int DEFAULT_PORT = 8888;
    private final String QUIT = "quit";

    private Socket socket;
    private BufferedReader reader;
    private BufferedWriter writer;

    //发送消息给服务器
    public void sendMsg(String msg) throws IOException {
        //输出流没有关闭的情况
        if (!socket.isOutputShutdown()) {
            writer.write(msg + "\n");
            writer.flush();
        }
    }

    //接受消息从服务器
    public String receiveMsg() throws IOException {
        if (!socket.isInputShutdown()) {
            // blocking in read()
            return reader.readLine();
        }
        return null;
    }

    //检查是否退出
    public boolean readyToQuit(String msg) {
        return QUIT.equals(msg);
    }

    //关闭
    public void close() {
        try {
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //启动开关
    public void start() {
        try {
            //创建socket
            socket = new Socket(DEFAULT_SERVER_HOST, DEFAULT_PORT);
            //创建io流
            writer = new BufferedWriter(
                    new OutputStreamWriter(socket.getOutputStream())
            );
            reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream())
            );

            //处理用户的输入的线程
            new Thread(new UserInputHandler(this)).start();

            //监听从服务器来的消息
            String msg = null;
            while ((msg = receiveMsg()) != null) {
                System.out.println(msg);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            close();
        }
    }


    public static void main(String[] args) {
        ChatClient chatClient = new ChatClient();
        chatClient.start();
    }
}

