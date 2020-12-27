package bio.Server;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ChatServer {
    private int DEFAULT_PORT = 8888;
    private final String QUIT = "quit";

    private ServerSocket serverSocket;
    //存储已经连接的客户们
    private Map<Integer, Writer> connectedClients;
    private ExecutorService executorService;

    public ChatServer() {
        this.connectedClients = new HashMap<>();
        this.executorService = Executors.newFixedThreadPool(10);
    }

    //添加客户端
    public synchronized void addClient(Socket socket) throws IOException {
        if (socket != null) {
            int key = socket.getPort();
            Writer value = new BufferedWriter(
                    new OutputStreamWriter(socket.getOutputStream())
            );
            connectedClients.put(key, value);
            System.out.println("客户" + key + "：已经连接");
        }
    }

    //移除客户端
    public synchronized void removeClient(Socket socket) throws IOException {
        if (socket != null) {
            int port = socket.getPort();
            if (connectedClients.containsKey(port)) {
                //移除用户的时候要进行关闭
                connectedClients.get(port).close();
                connectedClients.remove(port);
                System.out.println("客户端" + port + "：已经断开连接");
            }
        }
    }

    //将消息转发给其他用户
    public synchronized void forwardMessage(Socket socket, String fwdMsg) throws IOException {
        for (Integer port : connectedClients.keySet()) {
            //不转发给自己
            if (!port.equals(socket.getPort())) {
                Writer writer = connectedClients.get(port);
                writer.write(fwdMsg);
                writer.flush();
            }
        }
    }

    //检查是否退出
    public boolean readyToQuit(String msg) {
        return QUIT.equals(msg);
    }

    public void close() {
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    //启动
    public void start() {
        try {
            //绑定监听端口
            serverSocket = new ServerSocket(DEFAULT_PORT);
            System.out.println("聊天室服务器已经成功启动！");

            while (true) {
                Socket socket = serverSocket.accept();

                //为每个socket创建一条单独的线程进行处理
                //new Thread(new ChatHandler(socket,this)).start();
                executorService.execute(new ChatHandler(socket, this));
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            close();
        }
    }

    public static void main(String[] args) {
        ChatServer chatServer = new ChatServer();
        chatServer.start();
    }
}

